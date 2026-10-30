package com.smartfiles.app.data.smart

import com.smartfiles.app.data.FileKind
import com.smartfiles.app.data.index.normalizeText
import java.util.Calendar

/** O que o app entendeu de uma frase como "prints do whatsapp do mês passado com pix". */
data class QueryPlan(
    val keywords: List<String> = emptyList(),
    val kind: FileKind? = null,
    val tags: Set<SmartTag> = emptySet(),
    /** Rótulos do ML Kit, em inglês minúsculo (cat, dog, beach...). */
    val labels: Set<String> = emptySet(),
    val since: Long? = null,
    val until: Long? = null,
    val minSize: Long? = null,
    val sortBySize: Boolean = false,
    /** Trecho do caminho (whatsapp, /download/, /dcim/...). */
    val pathHint: String? = null,
    /** Rótulos legíveis do que foi entendido, pra mostrar ao usuário. */
    val understood: List<String> = emptyList(),
)

/**
 * Interpretador de busca em português, por regras, 100% offline.
 * Tira da frase datas, tipos, categorias, origem e tamanho; o que sobra vira palavra-chave
 * (todas precisam aparecer, no nome ou no texto do arquivo, em qualquer ordem).
 */
object QueryParser {

    private val KIND_WORDS = mapOf(
        "foto" to FileKind.IMAGE, "fotos" to FileKind.IMAGE, "imagem" to FileKind.IMAGE,
        "imagens" to FileKind.IMAGE, "figura" to FileKind.IMAGE, "figuras" to FileKind.IMAGE,
        "video" to FileKind.VIDEO, "videos" to FileKind.VIDEO, "filme" to FileKind.VIDEO, "filmes" to FileKind.VIDEO,
        "audio" to FileKind.AUDIO, "audios" to FileKind.AUDIO, "musica" to FileKind.AUDIO,
        "musicas" to FileKind.AUDIO, "gravacao" to FileKind.AUDIO, "gravacoes" to FileKind.AUDIO,
        "pdf" to FileKind.PDF, "pdfs" to FileKind.PDF,
        "apk" to FileKind.APK, "apks" to FileKind.APK, "aplicativo" to FileKind.APK, "aplicativos" to FileKind.APK,
        "zip" to FileKind.ARCHIVE, "rar" to FileKind.ARCHIVE, "compactado" to FileKind.ARCHIVE,
        "compactados" to FileKind.ARCHIVE,
        "pasta" to FileKind.FOLDER, "pastas" to FileKind.FOLDER,
        "planilha" to FileKind.DOCUMENT, "planilhas" to FileKind.DOCUMENT,
    )

    private val TAG_WORDS = mapOf(
        "print" to SmartTag.PRINT, "prints" to SmartTag.PRINT, "screenshot" to SmartTag.PRINT,
        "screenshots" to SmartTag.PRINT, "captura" to SmartTag.PRINT, "capturas" to SmartTag.PRINT,
        "comprovante" to SmartTag.COMPROVANTE, "comprovantes" to SmartTag.COMPROVANTE,
        "pix" to SmartTag.COMPROVANTE, "transferencia" to SmartTag.COMPROVANTE,
        "transferencias" to SmartTag.COMPROVANTE, "recibo" to SmartTag.COMPROVANTE, "recibos" to SmartTag.COMPROVANTE,
        "boleto" to SmartTag.BOLETO, "boletos" to SmartTag.BOLETO, "conta" to SmartTag.BOLETO,
        "contas" to SmartTag.BOLETO, "fatura" to SmartTag.BOLETO, "faturas" to SmartTag.BOLETO,
        "nf" to SmartTag.NOTA_FISCAL, "nfe" to SmartTag.NOTA_FISCAL, "nf-e" to SmartTag.NOTA_FISCAL, "danfe" to SmartTag.NOTA_FISCAL,
        "documento" to SmartTag.DOCUMENTO, "documentos" to SmartTag.DOCUMENTO, "doc" to SmartTag.DOCUMENTO,
        "docs" to SmartTag.DOCUMENTO,
        "selfie" to SmartTag.PESSOAS, "selfies" to SmartTag.PESSOAS, "pessoas" to SmartTag.PESSOAS,
        "animal" to SmartTag.ANIMAIS, "animais" to SmartTag.ANIMAIS, "pet" to SmartTag.ANIMAIS, "pets" to SmartTag.ANIMAIS,
        "comida" to SmartTag.COMIDA, "comidas" to SmartTag.COMIDA, "prato" to SmartTag.COMIDA, "pratos" to SmartTag.COMIDA,
        "paisagem" to SmartTag.PAISAGENS, "paisagens" to SmartTag.PAISAGENS, "natureza" to SmartTag.PAISAGENS,
        "meme" to SmartTag.MEME, "memes" to SmartTag.MEME, "figurinha" to SmartTag.MEME,
    )

    /** Palavra em português -> rótulo do ML Kit. */
    private val LABEL_WORDS = mapOf(
        "gato" to "cat", "gatos" to "cat", "gata" to "cat", "gatinho" to "cat",
        "cachorro" to "dog", "cachorros" to "dog", "cao" to "dog", "caes" to "dog", "cachorrinho" to "dog",
        "dog" to "dog", "passaro" to "bird", "passaros" to "bird", "ave" to "bird", "aves" to "bird",
        "praia" to "beach", "praias" to "beach", "ceu" to "sky", "montanha" to "mountain", "montanhas" to "mountain",
        "flor" to "flower", "flores" to "flower", "planta" to "plant", "plantas" to "plant",
        "carro" to "car", "carros" to "car", "pizza" to "pizza", "bolo" to "cake", "bolos" to "cake",
        "por do sol" to "sunset", "lago" to "lake", "cachoeira" to "waterfall", "mar" to "sea",
    )

    private val STOPWORDS = setOf(
        "a", "o", "as", "os", "um", "uma", "uns", "umas", "de", "do", "da", "dos", "das", "com", "sem",
        "que", "e", "em", "no", "na", "nos", "nas", "para", "pra", "pro", "por", "pelo", "pela", "sobre",
        "meu", "meus", "minha", "minhas", "esse", "essa", "esses", "essas", "este", "esta", "aquele",
        "aquela", "aqueles", "aquelas", "tem", "tenho", "tinha", "foi", "foram", "todos", "todas", "tudo",
        "arquivo", "arquivos", "quero", "queria", "achar", "acha", "encontrar", "encontra", "mostrar", "mostra",
        "me", "ver", "onde", "esta", "estao", "recebi", "recebido", "recebidos", "recebidas", "enviei",
        "enviado", "enviados", "tirei", "salvei", "salvo", "salvos", "baixado", "aquilo", "isso", "qual", "quais",
    )

    private val MONTH_LABELS = listOf(
        "Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho",
        "Julho", "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro",
    )

    private val MONTHS = listOf(
        "janeiro", "fevereiro", "marco", "abril", "maio", "junho",
        "julho", "agosto", "setembro", "outubro", "novembro", "dezembro",
    )

    fun parse(raw: String, now: Calendar = Calendar.getInstance()): QueryPlan {
        var q = " " + normalizeText(raw).replace(Regex("[^a-z0-9\\s-]"), " ").replace(Regex("\\s+"), " ") + " "
        val understood = mutableListOf<String>()
        var since: Long? = null
        var until: Long? = null
        var minSize: Long? = null
        var sortBySize = false
        var pathHint: String? = null
        val tags = linkedSetOf<SmartTag>()
        val labels = linkedSetOf<String>()
        var kind: FileKind? = null

        /** Procura o padrão; se achar, remove da frase e devolve o match. */
        fun take(pattern: String): MatchResult? {
            val m = Regex(pattern).find(q) ?: return null
            q = q.substring(0, m.range.first) + " " + q.substring(m.range.last + 1)
            return m
        }

        fun setRange(from: Calendar, to: Calendar?, label: String) {
            if (since != null) return // só a primeira data da frase vale
            since = from.timeInMillis
            until = to?.timeInMillis
            understood += label
        }

        // ---------- Datas ----------
        take("\\bultim[oa]s?\\s+(\\d+)\\s+(dias?|semanas?|mes|meses|anos?)\\b")?.let { m ->
            val n = m.groupValues[1].toInt()
            val unit = m.groupValues[2]
            val from = now.copy().startOfDay()
            when {
                unit.startsWith("dia") -> from.add(Calendar.DAY_OF_YEAR, -n)
                unit.startsWith("semana") -> from.add(Calendar.DAY_OF_YEAR, -7 * n)
                unit.startsWith("mes") -> from.add(Calendar.MONTH, -n)
                else -> from.add(Calendar.YEAR, -n)
            }
            setRange(from, null, "Últimos $n $unit")
        }
        take("\\bultima semana\\b")?.let {
            setRange(now.copy().startOfDay().apply { add(Calendar.DAY_OF_YEAR, -7) }, null, "Últimos 7 dias")
        }
        take("\\bsemana passada\\b")?.let {
            val start = now.copy().startOfWeek()
            setRange(start.copy().apply { add(Calendar.DAY_OF_YEAR, -7) }, start, "Semana passada")
        }
        take("\\b(n?est|n?ess)a semana\\b")?.let { setRange(now.copy().startOfWeek(), null, "Esta semana") }
        take("\\b(mes passado|ultimo mes)\\b")?.let {
            val start = now.copy().startOfMonth()
            setRange(start.copy().apply { add(Calendar.MONTH, -1) }, start, "Mês passado")
        }
        take("\\b(n?est|n?ess)e mes\\b")?.let { setRange(now.copy().startOfMonth(), null, "Este mês") }
        take("\\bano passado\\b")?.let {
            val start = now.copy().startOfYear()
            setRange(start.copy().apply { add(Calendar.YEAR, -1) }, start, "Ano passado")
        }
        take("\\b(n?est|n?ess)e ano\\b")?.let { setRange(now.copy().startOfYear(), null, "Este ano") }
        take("\\banteontem\\b")?.let {
            val d = now.copy().startOfDay().apply { add(Calendar.DAY_OF_YEAR, -2) }
            setRange(d, d.copy().apply { add(Calendar.DAY_OF_YEAR, 1) }, "Anteontem")
        }
        take("\\bontem\\b")?.let {
            val d = now.copy().startOfDay().apply { add(Calendar.DAY_OF_YEAR, -1) }
            setRange(d, now.copy().startOfDay(), "Ontem")
        }
        take("\\bhoje\\b")?.let { setRange(now.copy().startOfDay(), null, "Hoje") }

        take("\\b(${MONTHS.joinToString("|")})(?:\\s+(?:de\\s+)?(\\d{4}))?\\b")?.let { m ->
            val month = MONTHS.indexOf(m.groupValues[1])
            val year = m.groupValues[2].toIntOrNull()
                ?: now.get(Calendar.YEAR).let { y -> if (month > now.get(Calendar.MONTH)) y - 1 else y }
            val start = (now.clone() as Calendar).apply {
                clear(); set(year, month, 1)
            }
            setRange(start, start.copy().apply { add(Calendar.MONTH, 1) }, "${MONTH_LABELS[month]} de $year")
        }
        take("\\b(?:de|em) ((?:19|20)\\d{2})\\b")?.let { m ->
            val year = m.groupValues[1].toInt()
            val start = (now.clone() as Calendar).apply { clear(); set(year, 0, 1) }
            setRange(start, start.copy().apply { add(Calendar.YEAR, 1) }, "Em $year")
        }

        // ---------- Tamanho ----------
        take("\\b(?:maior(?:es)?|acima|mais)\\s+(?:que|de|do que)\\s+(\\d+)\\s*(kb|mb|gb)\\b")?.let { m ->
            val n = m.groupValues[1].toLong()
            val mult = when (m.groupValues[2]) { "kb" -> 1024L; "mb" -> 1024L * 1024; else -> 1024L * 1024 * 1024 }
            minSize = n * mult
            understood.add("Maior que $n ${m.groupValues[2].uppercase()}")
        }
        take("\\b(grandes?|pesad[oa]s?|maiores|enormes?)\\b")?.let {
            sortBySize = true
            if (minSize == null) minSize = 5L * 1024 * 1024
            understood.add("Grandes")
        }

        // ---------- Expressões de mais de uma palavra ----------
        take("\\bnotas? fisca(l|is)\\b|\\bcupom fiscal\\b")?.let { tags.add(SmartTag.NOTA_FISCAL) }
        take("\\bcapturas? de tela\\b")?.let { tags.add(SmartTag.PRINT) }
        take("\\bpor do sol\\b")?.let { labels.add("sunset"); understood.add("Pôr do sol") }

        // ---------- Origem ----------
        take("\\b(whatsapp|zap|zapzap|wpp|whats)\\b")?.let { pathHint = "whatsapp"; understood.add("Do WhatsApp") }
            ?: take("\\btelegram\\b")?.let { pathHint = "telegram"; understood.add("Do Telegram") }
            ?: take("\\binstagram\\b")?.let { pathHint = "instagram"; understood.add("Do Instagram") }
            ?: take("\\b(downloads?|baixad[oa]s|baixei)\\b")?.let { pathHint = "/download/"; understood.add("Em Downloads") }
            ?: take("\\b(camera|galeria)\\b")?.let { pathHint = "/dcim/"; understood.add("Da câmera") }

        // ---------- Palavras soltas ----------
        val keywords = mutableListOf<String>()
        for (token in q.split(' ').filter { it.isNotBlank() }) {
            val kindWord = KIND_WORDS[token]
            val tagWord = TAG_WORDS[token]
            val labelWord = LABEL_WORDS[token]
            when {
                kindWord != null -> if (kind == null) kind = kindWord
                tagWord != null -> tags.add(tagWord)
                labelWord != null -> {
                    if (labels.add(labelWord)) understood.add(token.replaceFirstChar { it.uppercase() })
                }
                token !in STOPWORDS && token.length >= 2 -> keywords.add(token)
            }
        }
        // Rótulo de imagem implica foto
        if (labels.isNotEmpty() && kind == null) kind = FileKind.IMAGE

        kind?.let { understood.add(0, kindLabel(it)) }
        tags.forEach { understood += it.label }
        keywords.forEach { understood.add("“$it”") }

        return QueryPlan(
            keywords = keywords.distinct(),
            kind = kind,
            tags = tags,
            labels = labels,
            since = since,
            until = until,
            minSize = minSize,
            sortBySize = sortBySize,
            pathHint = pathHint,
            understood = understood.distinct(),
        )
    }

    private fun kindLabel(kind: FileKind) = when (kind) {
        FileKind.FOLDER -> "Pastas"
        FileKind.IMAGE -> "Imagens"
        FileKind.VIDEO -> "Vídeos"
        FileKind.AUDIO -> "Áudios"
        FileKind.PDF -> "PDFs"
        FileKind.DOCUMENT -> "Documentos do Office"
        FileKind.ARCHIVE -> "Compactados"
        FileKind.APK -> "APKs"
        FileKind.OTHER -> "Outros"
    }

    private fun Calendar.copy(): Calendar = clone() as Calendar

    private fun Calendar.startOfDay(): Calendar = apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }

    private fun Calendar.startOfWeek(): Calendar = startOfDay().apply {
        val diff = (get(Calendar.DAY_OF_WEEK) - firstDayOfWeek + 7) % 7
        add(Calendar.DAY_OF_YEAR, -diff)
    }

    private fun Calendar.startOfMonth(): Calendar = startOfDay().apply { set(Calendar.DAY_OF_MONTH, 1) }

    private fun Calendar.startOfYear(): Calendar = startOfDay().apply { set(Calendar.DAY_OF_YEAR, 1) }
}
