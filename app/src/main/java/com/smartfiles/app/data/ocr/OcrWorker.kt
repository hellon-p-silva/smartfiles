package com.smartfiles.app.data.ocr

import android.content.Context
import android.os.Environment
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.smartfiles.app.data.FileKind
import com.smartfiles.app.data.index.AppDatabase
import com.smartfiles.app.data.index.FileAnalysis
import com.smartfiles.app.data.index.FileText
import com.smartfiles.app.data.index.IndexedFile
import com.smartfiles.app.data.index.normalizeText
import com.smartfiles.app.data.smart.Categorizer
import com.smartfiles.app.data.smart.SmartTag
import java.io.File

/**
 * Analisa imagens e PDFs aos poucos, dos mais recentes pros mais antigos:
 * 1. texto (OCR), reaproveitando o que já foi lido se o arquivo não mudou;
 * 2. rótulos de imagem (gato, comida, praia...);
 * 3. categorias automáticas (prints, comprovantes, boletos...).
 * Se o sistema interromper, o que já foi feito fica salvo e a próxima execução continua.
 */
class OcrWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!Environment.isExternalStorageManager()) return Result.success()

        val db = AppDatabase.get(applicationContext)
        val texts = db.texts()
        val analysis = db.analysis()
        val engine = OcrEngine()
        val version = FileAnalysis.CURRENT_VERSION

        try {
            val total = texts.pendingCount(MIN_SIZE, MAX_SIZE, version)
            var done = 0
            setProgress(workDataOf(KEY_DONE to 0, KEY_TOTAL to total))

            while (!isStopped) {
                val batch = texts.pending(MIN_SIZE, MAX_SIZE, version, BATCH)
                if (batch.isEmpty()) break

                for (item in batch) {
                    if (isStopped) break
                    process(item, engine, db)
                    done++
                    setProgress(workDataOf(KEY_DONE to done, KEY_TOTAL to maxOf(total, done)))
                }
            }
            texts.deleteOrphans()
            analysis.deleteOrphans()
        } finally {
            engine.close()
        }
        return Result.success()
    }

    private suspend fun process(item: IndexedFile, engine: OcrEngine, db: AppDatabase) {
        val file = File(item.path)
        val kind = runCatching { FileKind.valueOf(item.kind) }.getOrDefault(FileKind.OTHER)

        // Texto já lido e ainda válido? Reaproveita (economiza o OCR de quem veio da etapa 5)
        val cached = db.texts().get(item.path)
            ?.takeIf { it.size == item.size && it.lastModified == item.lastModified }

        var text = cached?.text
        var labels = emptySet<String>()

        runCatching {
            if (kind == FileKind.PDF) {
                if (text == null) text = OcrEngine.clean(engine.readPdf(file))
            } else {
                val result = engine.analyzeImage(file, withText = text == null)
                if (text == null) text = OcrEngine.clean(result.text.orEmpty())
                labels = result.labels
            }
        }
        val finalText = text.orEmpty()
        val textNorm = normalizeText(finalText)
        val now = System.currentTimeMillis()

        // Grava mesmo vazio, pra não tentar de novo o mesmo arquivo
        if (cached == null) {
            db.texts().upsert(FileText(item.path, item.size, item.lastModified, finalText, textNorm, now))
        }
        val tags = Categorizer.tags(item.path, kind, textNorm, labels)
        db.analysis().upsert(
            FileAnalysis(
                path = item.path,
                size = item.size,
                lastModified = item.lastModified,
                tags = SmartTag.encode(tags),
                labels = if (labels.isEmpty()) "" else labels.joinToString(",", ",", ","),
                version = FileAnalysis.CURRENT_VERSION,
                processedAt = now,
            )
        )
    }

    companion object {
        const val UNIQUE = "ocr"
        const val KEY_DONE = "done"
        const val KEY_TOTAL = "total"

        private const val BATCH = 25
        private const val MIN_SIZE = 20L * 1024           // ignora ícones e miniaturas
        private const val MAX_SIZE = 25L * 1024 * 1024    // ignora imagens/PDFs gigantes

        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<OcrWorker>()
                .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE, ExistingWorkPolicy.KEEP, request)
        }
    }
}
