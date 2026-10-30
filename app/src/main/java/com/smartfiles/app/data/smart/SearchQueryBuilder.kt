package com.smartfiles.app.data.smart

import androidx.sqlite.db.SimpleSQLiteQuery
import com.smartfiles.app.data.FileKind
import com.smartfiles.app.data.index.likeEscape

/**
 * Monta o SQL da busca a partir do QueryPlan + filtros manuais da tela (chips).
 * Os chips têm prioridade sobre o que foi entendido da frase.
 */
object SearchQueryBuilder {

    fun build(
        plan: QueryPlan,
        chipKind: FileKind? = null,
        chipSince: Long? = null,
        chipTag: SmartTag? = null,
        limit: Int = 300,
    ): SimpleSQLiteQuery {
        val where = mutableListOf<String>()
        val args = mutableListOf<Any>()

        // Cada palavra precisa aparecer no nome OU no texto lido por OCR (em qualquer ordem)
        for (word in plan.keywords) {
            val like = "%${likeEscape(word)}%"
            where += "(f.nameNorm LIKE ? ESCAPE '\\' OR t.textNorm LIKE ? ESCAPE '\\')"
            args += like; args += like
        }

        val kind = chipKind ?: plan.kind
        if (kind != null) {
            where += "f.kind = ?"
            args += kind.name
        }

        val since = chipSince ?: plan.since
        if (since != null && since > 0) {
            where += "f.lastModified >= ?"
            args += since
        }
        if (chipSince == null && plan.until != null) {
            where += "f.lastModified < ?"
            args += plan.until
        }

        // Categorias: todas as pedidas precisam estar presentes
        val tags = plan.tags + listOfNotNull(chipTag)
        for (tag in tags) {
            if (tag == SmartTag.DOCUMENTO) {
                // "documentos" também inclui Word/Excel/etc., que não passam pela análise
                where += "(a.tags LIKE ? OR f.kind = 'DOCUMENT')"
            } else {
                where += "a.tags LIKE ?"
            }
            args += "%,${tag.key},%"
        }

        // Rótulos de imagem: basta um deles ("gato ou cachorro" vira cat OR dog)
        if (plan.labels.isNotEmpty()) {
            where += plan.labels.joinToString(" OR ", "(", ")") { "a.labels LIKE ?" }
            plan.labels.forEach { args += "%,$it,%" }
        }

        plan.minSize?.let {
            where += "f.size >= ?"
            args += it
        }
        plan.pathHint?.let {
            where += "f.path LIKE ? ESCAPE '\\'"
            args += "%${likeEscape(it)}%"
        }

        // Sem palavra digitada, não mostra pastas (exceto se pediu pastas)
        if (plan.keywords.isEmpty() && kind != FileKind.FOLDER) where += "f.isDirectory = 0"

        val order = mutableListOf<String>()
        plan.keywords.firstOrNull()?.let { first ->
            val e = likeEscape(first)
            order += "CASE WHEN f.nameNorm LIKE ? ESCAPE '\\' THEN 0 " +
                "WHEN f.nameNorm LIKE ? ESCAPE '\\' THEN 1 ELSE 2 END"
            args += "$e%"; args += "%$e%"
        }
        if (plan.sortBySize) order += "f.size DESC"
        order += "f.lastModified DESC"

        val sql = buildString {
            append("SELECT f.*, t.text AS ocrText, a.tags AS tags FROM files f ")
            append("LEFT JOIN file_text t ON t.path = f.path ")
            append("LEFT JOIN file_analysis a ON a.path = f.path ")
            if (where.isNotEmpty()) append("WHERE ").append(where.joinToString(" AND ")).append(' ')
            append("ORDER BY ").append(order.joinToString(", ")).append(' ')
            append("LIMIT ").append(limit)
        }
        return SimpleSQLiteQuery(sql, args.toTypedArray())
    }
}
