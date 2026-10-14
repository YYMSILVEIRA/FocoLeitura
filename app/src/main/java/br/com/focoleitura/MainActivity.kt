package br.com.focoleitura

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.ValueCallback
import androidx.webkit.WebViewAssetLoader
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

/** Empacotamento Android do motor RSVP da versão anterior, com TTS NATIVO.
 *  A UI e o armazenamento IndexedDB são locais; não há conta/servidor.
 *  MinSdk 26 por permitir TextToSpeech.onRangeStart. */
class MainActivity : Activity(), TextToSpeech.OnInitListener {
    private lateinit var webView: WebView
    private var tts: TextToSpeech? = null
    @Volatile private var ttsReady = false
    private var chooser: ValueCallback<Array<Uri>>? = null
    private val fileChooserRequest = 1031
    private val backupSaveRequest = 1032
    private val nativeOpenRequest = 1033
    private var pendingBackupJson: String? = null
    private val importSerial = AtomicInteger(0)
    private var readerResumed = false
    private var pendingImport: Pair<Int, String>? = null
    @Volatile private var pendingNativeText: Pair<Int, String>? = null
    private val baseUrl = "https://appassets.androidplatform.net/assets/index.html"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(11, 19, 33)
        window.navigationBarColor = android.graphics.Color.rgb(11, 19, 33)
        webView = WebView(this)
        webView.layoutParams = ViewGroup.LayoutParams(-1, -1)
        setContentView(webView)
        val assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.allowFileAccess = false
        webView.settings.allowContentAccess = true
        webView.settings.setSupportZoom(false)
        webView.addJavascriptInterface(ReaderTtsBridge(), "AndroidTTS")
        webView.addJavascriptInterface(ReaderFileBridge(), "AndroidFiles")
        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? =
                request?.url?.let { assetLoader.shouldInterceptRequest(it) }
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val uri = request?.url ?: return false
                return if (uri.host == "appassets.androidplatform.net") false else true
            }
        }
        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView?, filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                chooser?.onReceiveValue(null)
                chooser = filePathCallback
                return try {
                    openDocumentPicker(fileChooserRequest)
                    true
                } catch (e: Exception) {
                    chooser?.onReceiveValue(null)
                    chooser = null
                    dispatchImportError(importSerial.get(), "Falha ao abrir seletor: " + (e.message ?: "erro"))
                    true
                }
            }
        }
        PDFBoxResourceLoader.init(applicationContext)
        tts = TextToSpeech(this, this)
        webView.loadUrl(baseUrl)
    }

    @Deprecated("File picker activity result support")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == backupSaveRequest) {
            val payload = pendingBackupJson
            pendingBackupJson = null
            if (resultCode == RESULT_OK && data?.data != null && payload != null) {
                val uri = data.data!!
                Thread {
                    try {
                        contentResolver.openOutputStream(uri, "w")?.bufferedWriter(Charsets.UTF_8)?.use { it.write(payload) }
                            ?: throw IllegalStateException("Destino não permitiu gravação")
                        val message = JSONObject.quote("Cópia de segurança exportada com sucesso.")
                        runOnUiThread { webView.evaluateJavascript("window.FocoNativeExportResult?.($message)", null) }
                    } catch (e: Exception) {
                        val message = JSONObject.quote("Falha ao salvar backup: " + e.message)
                        runOnUiThread { webView.evaluateJavascript("window.FocoNativeExportResult?.($message)", null) }
                    }
                }.start()
            }
            return
        }
        if (requestCode != nativeOpenRequest && requestCode != fileChooserRequest) return
        if (requestCode == fileChooserRequest) {
            val current = chooser
            chooser = null
            current?.onReceiveValue(null)
        }
        val uri = if (resultCode == RESULT_OK) data?.data else null
        if (uri != null) importNativeDocument(uri)
    }

    private fun openDocumentPicker(requestCode: Int) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            // Alguns cartões SD declaram PDFs e textos como application/octet-stream.
            type = "*/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        @Suppress("DEPRECATION")
        startActivityForResult(intent, requestCode)
    }

    private fun dispatchImport(serial: Int, script: String) {
        runOnUiThread {
            if (isFinishing || isDestroyed || serial != importSerial.get()) return@runOnUiThread
            pendingImport = serial to script
            flushImport()
        }
    }

    private fun dispatchImportError(serial: Int, message: String) {
        dispatchImport(serial, "window.FocoNativeImportError?.(" + JSONObject.quote(message) + ")")
    }

    private fun flushImport() {
        if (!readerResumed || !::webView.isInitialized) return
        val next = pendingImport ?: return
        pendingImport = null
        if (next.first == importSerial.get()) webView.evaluateJavascript(next.second, null)
    }

    private fun importNativeDocument(uri: Uri) {
        val serial = importSerial.incrementAndGet()
        pendingImport = null
        pendingNativeText = null
        Thread {
            try {
                var filename = "Documento"
                contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) filename = cursor.getString(0) ?: filename
                }
                val mime = contentResolver.getType(uri).orEmpty()
                val pdf = mime.equals("application/pdf", true) || filename.endsWith(".pdf", true)
                val textFile = mime.startsWith("text/", true) ||
                    Regex("(?i).*\\.(txt|md|markdown|html|htm)$").matches(filename)
                if (!pdf && !textFile) throw IllegalArgumentException("Formato não suportado. Use PDF, TXT, MD ou HTML.")
                var ocrPages = 0
                var totalPages = 0
                val content = if (pdf) {
                    val extracted = PdfOcr.read(
                        applicationContext, uri,
                        progress = { page, total ->
                            runOnUiThread {
                                if (!isFinishing && readerResumed && serial == importSerial.get())
                                    webView.evaluateJavascript("window.FocoNativeImportProgress?.($page,$total)", null)
                            }
                        },
                        shouldCancel = { serial != importSerial.get() || Thread.currentThread().isInterrupted }
                    )
                    ocrPages = extracted.ocrPages
                    totalPages = extracted.totalPages
                    extracted.text
                } else {
                    contentResolver.openInputStream(uri)?.use { input ->
                        val output = java.io.ByteArrayOutputStream()
                        val chunk = ByteArray(8192)
                        while (true) {
                            val n = input.read(chunk)
                            if (n < 0) break
                            if (output.size() + n > 4_000_000)
                                throw IllegalArgumentException("Texto acima de 4 MB.")
                            output.write(chunk, 0, n)
                        }
                        String(output.toByteArray(), Charsets.UTF_8)
                    } ?: throw IllegalArgumentException("Não foi possível abrir o texto.")
                }
                if (content.isBlank()) throw IllegalArgumentException("Arquivo sem texto legível; PDFs digitalizados precisam de OCR.")
                if (content.length > 4_000_000) throw IllegalArgumentException("Documento muito extenso (limite 4 milhões de caracteres).")
                if (serial != importSerial.get()) return@Thread
                pendingNativeText = serial to content
                val title = JSONObject.quote(if (pdf) filename.replace(Regex("(?i)\\.pdf$"), "") else filename)
                // A mensagem enviada à WebView contém apenas metadados; texto por blocos.
                dispatchImport(serial, "window.FocoNativeImportReady?.($serial,$title,$pdf,$ocrPages,$totalPages,${content.length})")
            } catch (e: Exception) {
                dispatchImportError(serial, "Erro ao abrir documento: " + (e.message ?: "arquivo inválido"))
            }
        }.start()
    }

    override fun onInit(status: Int) {
        ttsReady = status == TextToSpeech.SUCCESS
        if (!ttsReady) return
        tts?.language = Locale("pt", "BR")
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = report("start", utteranceId)
            override fun onDone(utteranceId: String?) = report("done", utteranceId)
            @Deprecated("Use onError(id, errorCode)")
            override fun onError(utteranceId: String?) = report("error", utteranceId)
            override fun onError(utteranceId: String?, errorCode: Int) = report("error", utteranceId)
            override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
                report("range", utteranceId, start, end)
            }
        })
        runOnUiThread { webView.evaluateJavascript("window.FocoNativeTTSReady?.()", null) }
    }

    private fun report(event: String, id: String?, start: Int = -1, end: Int = -1) {
        if (id == null) return
        val json = JSONObject().put("type", event).put("id", id)
        if (start >= 0) json.put("start", start)
        if (end >= 0) json.put("end", end)
        runOnUiThread { if (!isFinishing) webView.evaluateJavascript("window.FocoNativeTTS?.(${json})", null) }
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        webView.evaluateJavascript("window.FocoHandleBack?.() === true") { handled ->
            if (handled != "true") finish()
        }
    }

    private inner class ReaderFileBridge {
        @JavascriptInterface
        fun importChunk(serial: Int, offset: Int, maxLength: Int): String {
            if (serial != importSerial.get()) return ""
            val pending = pendingNativeText ?: return ""
            if (pending.first != serial || offset < 0 || offset >= pending.second.length) return ""
            val size = maxLength.coerceIn(1, 16_384)
            return pending.second.substring(offset, (offset.toLong() + size).coerceAtMost(pending.second.length.toLong()).toInt())
        }

        @JavascriptInterface
        fun releaseImport(serial: Int): Boolean {
            if (serial != importSerial.get() || pendingNativeText?.first != serial) return false
            pendingNativeText = null
            return true
        }

        @JavascriptInterface
        fun cancelImport() {
            importSerial.incrementAndGet()
            pendingNativeText = null
            runOnUiThread { pendingImport = null }
        }

        @JavascriptInterface
        fun openDocument() {
            runOnUiThread {
                try {
                    openDocumentPicker(nativeOpenRequest)
                } catch (e: Exception) {
                    val message = JSONObject.quote("Não foi possível abrir o seletor Android: " + e.message)
                    webView.evaluateJavascript("window.FocoNativeImportError?.($message)", null)
                }
            }
        }

        @JavascriptInterface
        fun exportJson(json: String, name: String) {
            if (json.length > 20_000_000) return
            runOnUiThread {
                pendingBackupJson = json
                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/json"
                    putExtra(Intent.EXTRA_TITLE, name.take(120))
                }
                @Suppress("DEPRECATION")
                startActivityForResult(intent, backupSaveRequest)
            }
        }
    }

    private inner class ReaderTtsBridge {
        @JavascriptInterface
        fun speak(text: String, id: String, rate: Double, pitch: Double, volume: Double, voiceId: String): Boolean {
            val synth = tts ?: return false
            if (!ttsReady || text.isBlank()) return false
            try {
                val available = synth.voices?.firstOrNull { it.name == voiceId }
                if (available != null) synth.voice = available else synth.language = Locale("pt", "BR")
                synth.setSpeechRate(rate.toFloat().coerceIn(0.5f, 4f))
                synth.setPitch(pitch.toFloat().coerceIn(0.6f, 1.6f))
                val args = Bundle()
                args.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume.toFloat().coerceIn(0f, 1f))
                return synth.speak(text, TextToSpeech.QUEUE_FLUSH, args, id) == TextToSpeech.SUCCESS
            } catch (_: Exception) { return false }
        }
        @JavascriptInterface fun stop() { tts?.stop() }
        @JavascriptInterface fun voices(): String {
            val arr = JSONArray()
            if (!ttsReady) return arr.toString()
            tts?.voices?.filterNotNull()?.sortedWith(compareByDescending<Voice>{ it.locale.language == "pt" }.thenBy { it.name })?.forEach {
                arr.put(JSONObject().put("id", it.name).put("name", it.name).put("lang", it.locale.toLanguageTag())
                    .put("network", it.isNetworkConnectionRequired))
            }
            return arr.toString()
        }
    }

    override fun onPause() {
        readerResumed = false
        webView.evaluateJavascript("window.FocoPauseForBackground?.()", null)
        tts?.stop()
        super.onPause()
        webView.onPause()
    }
    override fun onResume() {
        super.onResume()
        webView.onResume()
        readerResumed = true
        flushImport()
    }
    override fun onDestroy() {
        chooser?.onReceiveValue(null);chooser=null
        importSerial.incrementAndGet()
        pendingNativeText = null
        tts?.stop();tts?.shutdown();tts=null
        webView.destroy()
        super.onDestroy()
    }
}
