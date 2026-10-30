package com.smartfiles.app.data.ocr

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.media.ExifInterface
import android.os.ParcelFileDescriptor
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * OCR 100% no aparelho (ML Kit, modelo latino embutido). Nada sai do celular.
 * Imagens são reduzidas antes de processar pra economizar memória e bateria.
 */
class OcrEngine {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val labeler = ImageLabeling.getClient(
        ImageLabelerOptions.Builder().setConfidenceThreshold(LABEL_CONFIDENCE).build()
    )

    /** Resultado da análise de uma imagem. `text` = null quando o OCR não foi pedido. */
    data class ImageResult(val text: String?, val labels: Set<String>)

    /**
     * Decodifica a imagem uma vez só e roda OCR (se `withText`) e rótulos em cima do mesmo bitmap.
     * Rótulos vêm em inglês e minúsculos: "cat", "food", "beach"...
     */
    suspend fun analyzeImage(file: File, withText: Boolean): ImageResult {
        val bitmap = decodeSampled(file, MAX_SIDE) ?: return ImageResult(if (withText) "" else null, emptySet())
        return try {
            val image = InputImage.fromBitmap(bitmap, rotationOf(file))
            val text = if (withText) recognizer.process(image).await().text else null
            val labels = labeler.process(image).await().map { it.text.lowercase() }.toSet()
            ImageResult(text, labels)
        } finally {
            bitmap.recycle()
        }
    }

    /** Renderiza as primeiras páginas do PDF e lê o texto de cada uma. */
    suspend fun readPdf(file: File, maxPages: Int = MAX_PDF_PAGES): String {
        val out = StringBuilder()
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                val pages = minOf(renderer.pageCount, maxPages)
                for (i in 0 until pages) {
                    val page = renderer.openPage(i)
                    val bitmap = try {
                        val scale = PDF_RENDER_WIDTH.toFloat() / page.width
                        val w = PDF_RENDER_WIDTH
                        val h = (page.height * scale).toInt().coerceAtLeast(1)
                        Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also {
                            it.eraseColor(Color.WHITE) // PDF tem fundo transparente
                            page.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        }
                    } finally {
                        page.close()
                    }
                    try {
                        out.append(recognize(bitmap, 0)).append(' ')
                    } finally {
                        bitmap.recycle()
                    }
                }
            }
        }
        return out.toString()
    }

    fun close() {
        recognizer.close()
        labeler.close()
    }

    private suspend fun recognize(bitmap: Bitmap, rotation: Int): String {
        val result = recognizer.process(InputImage.fromBitmap(bitmap, rotation)).await()
        return result.text
    }

    private fun decodeSampled(file: File, maxSide: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeFile(file.path, opts)
    }

    private fun rotationOf(file: File): Int = runCatching {
        when (ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
    }.getOrDefault(0)

    companion object {
        private const val MAX_SIDE = 2048
        private const val PDF_RENDER_WIDTH = 1600
        private const val MAX_PDF_PAGES = 3
        private const val LABEL_CONFIDENCE = 0.7f

        /** Deixa o texto numa linha só, sem espaços repetidos, e com tamanho limitado. */
        fun clean(text: String): String = text.replace(WHITESPACE, " ").trim().take(10_000)

        private val WHITESPACE = "\\s+".toRegex()
    }
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
    addOnCanceledListener { cont.cancel() }
}
