package com.smartfiles.app.data.duplicates

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest

object Hashing {
    private const val CHUNK = 64 * 1024

    /** Até esse tamanho, o hash rápido já lê o arquivo inteiro. */
    const val QUICK_COVERS_ALL: Long = 2L * CHUNK

    /**
     * Hash rápido: tamanho + primeiros 64 KB + últimos 64 KB.
     * Separa quase todos os "falsos duplicados" sem ler arquivos grandes inteiros.
     */
    fun quick(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        RandomAccessFile(file, "r").use { raf ->
            val len = raf.length()
            md.update(len.toString().toByteArray())
            val buf = ByteArray(CHUNK)

            val head = minOf(CHUNK.toLong(), len).toInt()
            raf.readFully(buf, 0, head)
            md.update(buf, 0, head)

            if (len > CHUNK) {
                val start = maxOf(CHUNK.toLong(), len - CHUNK)
                val tail = (len - start).toInt()
                raf.seek(start)
                raf.readFully(buf, 0, tail)
                md.update(buf, 0, tail)
            }
        }
        return md.digest().toHex()
    }

    /** Hash completo, usado só quando o rápido empata. Pode ser cancelado no meio. */
    suspend fun full(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        val buf = ByteArray(1024 * 1024)
        file.inputStream().use { input ->
            while (true) {
                currentCoroutineContext().ensureActive()
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().toHex()
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
