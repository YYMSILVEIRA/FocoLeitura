package br.com.focoleitura

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File
import kotlin.math.min
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * PDF com texto nativo + OCR no aparelho para páginas digitalizadas.
 * Não requer chave de API, acesso à Internet nem envio do PDF para um serviço remoto.
 * Deve ser chamado exclusivamente fora da thread principal.
 */
object PdfOcr {
    data class Result(val text: String, val totalPages: Int, val ocrPages: Int)
    private const val MAX_FILE_BYTES = 50L * 1024L * 1024L
    private const val MAX_TEXT_CHARS = 4_000_000
    private const val MAX_PAGES = 150

    fun read(context: Context, uri: Uri, progress: (Int, Int) -> Unit): Result {
        val pdf = File.createTempFile("focoleitura_import_", ".pdf", context.cacheDir)
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                pdf.outputStream().use { dest ->
                    val buffer = ByteArray(64 * 1024)
                    var copied = 0L
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        copied += n
                        if (copied > MAX_FILE_BYTES) {
                            throw IllegalArgumentException("O PDF excede 50 MB. Divida o arquivo antes de importar.")
                        }
                        dest.write(buffer, 0, n)
                    }
                }
            } ?: throw IllegalArgumentException("Não foi possível acessar o arquivo. Verifique o cartão SD.")
            var ocr: TextRecognizer? = null
            var renderer: PdfRenderer? = null
            var descriptor: ParcelFileDescriptor? = null
            try {
                PDDocument.load(pdf).use { doc ->
                    val pages = doc.numberOfPages
                    if (pages == 0) throw IllegalArgumentException("PDF vazio.")
                    if (pages > MAX_PAGES) {
                        throw IllegalArgumentException("O PDF possui $pages páginas (máximo: $MAX_PAGES por importação). Divida em partes.")
                    }
                    val out = StringBuilder()
                    var ocrCount = 0
                    val stripper = PDFTextStripper()
                    for (page in 1..pages) {
                        progress(page, pages)
                        stripper.startPage = page
                        stripper.endPage = page
                        var pageText = stripper.getText(doc).trim()
                        if (pageText.isBlank()) {
                            if (renderer == null) {
                                descriptor = ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY)
                                renderer = PdfRenderer(descriptor!!)
                            }
                            if (ocr == null) ocr = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                            pageText = recognizePage(renderer!!, page - 1, ocr!!).trim()
                            if (pageText.isNotBlank()) ocrCount++
                        }
                        if (pageText.isNotEmpty()) {
                            if (out.length + pageText.length + 2 > MAX_TEXT_CHARS) {
                                throw IllegalArgumentException("Texto extraído excede 4 milhões de caracteres.")
                            }
                            if (out.isNotEmpty()) out.append("\n\n")
                            out.append(pageText)
                        }
                    }
                    if (out.isBlank()) {
                        throw IllegalArgumentException("OCR não identificou texto nas páginas. Verifique a nitidez do PDF.")
                    }
                    return Result(out.toString(), pages, ocrCount)
                }
            } finally {
                try { ocr?.close() } catch (_: Exception) {}
                try { renderer?.close() } catch (_: Exception) {}
                try { descriptor?.close() } catch (_: Exception) {}
            }
        } finally {
            pdf.delete()
        }
    }

    private fun recognizePage(renderer: PdfRenderer, index: Int, recognizer: TextRecognizer): String {
        renderer.openPage(index).use { page ->
            val scale = min(2.5f, min(1800f / max(1, page.width), 2400f / max(1, page.height)))
            val width = max(1, (page.width * scale).roundToInt())
            val height = max(1, (page.height * scale).roundToInt())
            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            try {
                bmp.eraseColor(android.graphics.Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                // Tasks.await somente em worker thread: preserva ordem do documento.
                return Tasks.await(recognizer.process(InputImage.fromBitmap(bmp, 0))).text
            } finally {
                bmp.recycle()
            }
        }
    }
}
