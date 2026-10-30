package com.smartfiles.app.data.index

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [IndexedFile::class, FileHash::class, FileText::class, FileAnalysis::class],
    version = 4,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun files(): FileDao

    abstract fun hashes(): HashDao

    abstract fun texts(): TextDao

    abstract fun analysis(): AnalysisDao

    companion object {
        /** v2: tabela de cache de hashes. Migração manual pra não perder o índice existente. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `file_hashes` (`path` TEXT NOT NULL, " +
                        "`size` INTEGER NOT NULL, `lastModified` INTEGER NOT NULL, " +
                        "`hash` TEXT NOT NULL, PRIMARY KEY(`path`))"
                )
            }
        }

        /** v3: tabela com o texto lido por OCR. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `file_text` (`path` TEXT NOT NULL, " +
                        "`size` INTEGER NOT NULL, `lastModified` INTEGER NOT NULL, " +
                        "`text` TEXT NOT NULL, `textNorm` TEXT NOT NULL, " +
                        "`processedAt` INTEGER NOT NULL, PRIMARY KEY(`path`))"
                )
            }
        }

        /** v4: categorias automáticas e rótulos de imagem. */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `file_analysis` (`path` TEXT NOT NULL, " +
                        "`size` INTEGER NOT NULL, `lastModified` INTEGER NOT NULL, " +
                        "`tags` TEXT NOT NULL, `labels` TEXT NOT NULL, `version` INTEGER NOT NULL, " +
                        "`processedAt` INTEGER NOT NULL, PRIMARY KEY(`path`))"
                )
            }
        }

        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "smartfiles.db",
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    // O índice é reconstruível a partir do disco, então pode ser recriado em upgrades
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
    }
}
