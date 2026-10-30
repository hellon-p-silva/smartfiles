package com.smartfiles.app.data.whatsapp

import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Categorias de mídia do WhatsApp, pelo nome da pasta.
 * Ex.: "WhatsApp Images" e "WhatsApp Business Images" -> IMAGES.
 */
enum class WaCategory(val label: String, val folder: String, val visual: Boolean) {
    IMAGES("Fotos", "Images", true),
    VIDEOS("Vídeos", "Video", true),
    VOICE_NOTES("Áudios de voz", "Voice Notes", false),
    AUDIO("Músicas e áudios", "Audio", false),
    DOCUMENTS("Documentos", "Documents", false),
    STICKERS("Figurinhas", "Stickers", true),
    GIFS("GIFs", "Animated Gifs", true),
    VIDEO_NOTES("Recados de vídeo", "Video Notes", true),
    PROFILE_PHOTOS("Fotos de perfil", "Profile Photos", true);

    companion object {
        fun fromFolder(name: String): WaCategory? {
            val suffix = name.removePrefix("WhatsApp Business ").removePrefix("WhatsApp ")
            return entries.firstOrNull { it.folder.equals(suffix, ignoreCase = true) }
        }
    }
}

data class WaFile(
    val path: String,
    val name: String,
    val size: Long,
    val lastModified: Long,
    val category: WaCategory,
    /** Está numa pasta "Sent": é cópia de algo que você mesmo enviou. */
    val sent: Boolean,
    val business: Boolean,
)

/**
 * Lê as pastas de mídia do WhatsApp e do WhatsApp Business direto do disco
 * (não depende do índice, então o resultado está sempre atualizado).
 */
class WhatsAppScanner(root: File = Environment.getExternalStorageDirectory()) {

    private val mediaRoots = listOf(
        // Android 11+
        "Android/media/com.whatsapp/WhatsApp/Media",
        "Android/media/com.whatsapp.w4b/WhatsApp Business/Media",
        // Instalações antigas que ainda usam a raiz do armazenamento
        "WhatsApp/Media",
        "WhatsApp Business/Media",
    ).map { File(root, it) }

    fun isInstalled(): Boolean = mediaRoots.any { it.isDirectory }

    suspend fun scan(): List<WaFile> = withContext(Dispatchers.IO) {
        val out = mutableListOf<WaFile>()
        for (mediaRoot in mediaRoots) {
            if (!mediaRoot.isDirectory) continue
            val business = mediaRoot.path.contains("w4b") || mediaRoot.path.contains("WhatsApp Business")

            for (dir in mediaRoot.listFiles().orEmpty()) {
                if (!dir.isDirectory) continue
                val category = WaCategory.fromFolder(dir.name) ?: continue

                dir.walkTopDown()
                    .onEnter { it == dir || !it.name.startsWith(".") }
                    .filter { it.isFile && !it.name.startsWith(".") } // ignora .nomedia
                    .forEach { f ->
                        ensureActive()
                        out += WaFile(
                            path = f.absolutePath,
                            name = f.name,
                            size = f.length(),
                            lastModified = f.lastModified(),
                            category = category,
                            sent = f.absolutePath.contains("/Sent/", ignoreCase = true),
                            business = business,
                        )
                    }
            }
        }
        out
    }
}
