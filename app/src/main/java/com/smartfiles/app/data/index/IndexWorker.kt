package com.smartfiles.app.data.index

import android.content.Context
import android.os.Environment
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.smartfiles.app.data.ocr.OcrWorker
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

class IndexWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!Environment.isExternalStorageManager()) return Result.success()

        val indexer = FileIndexer(AppDatabase.get(applicationContext).files())
        return try {
            indexer.fullScan { count -> setProgress(workDataOf(KEY_COUNT to count)) }
            prefs(applicationContext).edit().putLong(KEY_LAST_SCAN, System.currentTimeMillis()).apply()
            // Índice atualizado: agora lê o texto das imagens/PDFs novos
            OcrWorker.enqueue(applicationContext)
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val KEY_COUNT = "count"
        const val UNIQUE_NOW = "index-now"
        private const val UNIQUE_PERIODIC = "index-periodic"
        private const val KEY_LAST_SCAN = "last_scan"
        private val MIN_INTERVAL_ON_OPEN = TimeUnit.HOURS.toMillis(1)

        private fun prefs(context: Context) =
            context.getSharedPreferences("index", Context.MODE_PRIVATE)

        /**
         * Chamado ao abrir o app com permissão:
         * - varre agora se a última varredura tiver mais de 1h
         * - agenda varreduras a cada 12h, só carregando e com bateria ok
         */
        fun schedule(context: Context) {
            val wm = WorkManager.getInstance(context)
            val last = prefs(context).getLong(KEY_LAST_SCAN, 0L)
            if (System.currentTimeMillis() - last > MIN_INTERVAL_ON_OPEN) {
                wm.enqueueUniqueWork(
                    UNIQUE_NOW,
                    ExistingWorkPolicy.KEEP,
                    OneTimeWorkRequestBuilder<IndexWorker>().build(),
                )
            }
            val periodic = PeriodicWorkRequestBuilder<IndexWorker>(12, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiresCharging(true)
                        .setRequiresBatteryNotLow(true)
                        .build()
                )
                .build()
            wm.enqueueUniquePeriodicWork(UNIQUE_PERIODIC, ExistingPeriodicWorkPolicy.KEEP, periodic)

            // Retoma o OCR se ele tiver sido interrompido antes de terminar
            OcrWorker.enqueue(context)
        }

        /** Botão "reindexar" da busca. */
        fun reindexNow(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_NOW,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<IndexWorker>().build(),
            )
        }
    }
}
