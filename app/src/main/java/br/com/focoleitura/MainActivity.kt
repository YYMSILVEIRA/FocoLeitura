package br.com.focoleitura

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
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
                    @Suppress("DEPRECATION")
                    startActivityForResult(fileChooserParams?.createIntent() ?: Intent(Intent.ACTION_OPEN_DOCUMENT)
                        .apply { type = "*/*"; addCategory(Intent.CATEGORY_OPENABLE) }, fileChooserRequest)
                    true
                } catch (_: ActivityNotFoundException) {
                    chooser?.onReceiveValue(null); chooser = null; false
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
        if (requestCode == nativeOpenRequest) {
            if (resultCode == RESULT_OK && data?.data != null) importNativeDocument(data.data!!)
            return
        }
        if (requestCode != fileChooserRequest) return
        val uri = if (resultCode == RESULT_OK) data?.data else null
        var extensionIsPdf = false
        if (uri != null) contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) extensionIsPdf = (cursor.getString(0) ?: "").lowercase(Locale.ROOT).endsWith(".pdf")
        }
        val pdf = uri != null && (contentResolver.getType(uri) == "application/pdf" || extensionIsPdf)
        if (pdf && uri != null) {
            chooser?.onReceiveValue(null)
            chooser = null
            extractPdfOffline(uri)
        } else {
            chooser?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data))
            chooser = null
        }
    }

    private fun importNativeDocument(uri: Uri) {
        Thread {
            try {
                var filename = "Documento"
                contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) filename = cursor.getString(0) ?: filename
                }
                val mime = contentResolver.getType(uri).orEmpty()
                if (mime == "application/pdf" || filename.lowercase(Locale.ROOT).endsWith(".pdf")) {
                    extractPdfOffline(uri)
                    return@Thread
                }
                if (!Regex("(?i).*\\.(txt|md|markdown|html|htm)$").matches(filename) && !mime.startsWith("text/")) {
                    throw IllegalArgumentException("Formato não suportado. Selecione PDF, TXT, MD ou HTML.")
                }
                val bytes = contentResolver.openInputStream(uri)?.use { input -> input.readBytes() }
                    ?: throw IllegalArgumentException("Não foi possível abrir arquivo")
                if (bytes.size > 4_000_000) throw IllegalArgumentException("Texto maior que 4 MB; divida o arquivo.")
                val safeTitle = JSONObject.quote(filename)
                val safeText = JSONObject.quote(String(bytes, Charsets.UTF_8))
                runOnUiThread { webView.evaluateJavascript("window.FocoLoadNativeText?.($safeTitle,$safeText,'texto-nativo')", null) }
            } catch (e: Exception) {
                val message = JSONObject.quote("Erro ao abrir documento: " + (e.message ?: "Arquivo inválido"))
                runOnUiThread { webView.evaluateJavascript("window.FocoNativeImportError?.($message)", null) }
            }
        }.start()
    }

    private fun extractPdfOffline(uri: Uri) {
        Thread {
            try {
                var title = "Documento PDF"
                contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) title = cursor.getString(0) ?: title
                }
                val text = contentResolver.openInputStream(uri)?.use { input ->
                    PDDocument.load(input).use { pdf -> PDFTextStripper().getText(pdf) }
                } ?: throw IllegalArgumentException("Não foi possível abrir o PDF.")
                if (text.isBlank()) throw IllegalArgumentException("PDF sem texto selecionável. OCR offline ainda não está disponível.")
                if (text.length > 4_000_000) throw IllegalArgumentException("Documento muito extenso para importação de uma só vez (limite: 4 milhões de caracteres).")
                val safeTitle = JSONObject.quote(title.removeSuffix(".pdf"))
                val safeText = JSONObject.quote(text)
                runOnUiThread { webView.evaluateJavascript("window.FocoLoadNativePdf?.($safeTitle,$safeText)", null) }
            } catch (e: Exception) {
                val msg = JSONObject.quote("Erro ao extrair PDF localmente: " + (e.message ?: "PDF inválido"))
                runOnUiThread { webView.evaluateJavascript("window.FocoNativeImportError?.($msg)", null) }
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
        fun openDocument() {
            runOnUiThread {
                try {
                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "*/*"
                        putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/pdf", "text/plain", "text/html", "text/markdown", "application/octet-stream"))
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    @Suppress("DEPRECATION")
                    startActivityForResult(intent, nativeOpenRequest)
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
                synth.setSpeechRate(rate.toFloat().coerceIn(0.5f, 2f))
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
        webView.evaluateJavascript("window.FocoPauseForBackground?.()", null)
        tts?.stop()
        super.onPause()
        webView.onPause()
    }
    override fun onResume() { super.onResume(); webView.onResume() }
    override fun onDestroy() {
        chooser?.onReceiveValue(null);chooser=null
        tts?.stop();tts?.shutdown();tts=null
        webView.destroy()
        super.onDestroy()
    }
}
