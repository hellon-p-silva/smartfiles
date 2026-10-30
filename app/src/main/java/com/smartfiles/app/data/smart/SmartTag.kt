package com.smartfiles.app.data.smart

import com.smartfiles.app.data.FileKind

/** Categorias automáticas. `key` é o que fica gravado no banco (",print,comprovante,"). */
enum class SmartTag(val key: String, val label: String) {
    PRINT("print", "Prints"),
    COMPROVANTE("comprovante", "Comprovantes"),
    BOLETO("boleto", "Boletos e contas"),
    NOTA_FISCAL("nota", "Notas fiscais"),
    DOCUMENTO("documento", "Documentos"),
    PESSOAS("pessoas", "Pessoas"),
    ANIMAIS("animais", "Animais"),
    COMIDA("comida", "Comida"),
    PAISAGENS("paisagens", "Paisagens"),
    MEME("meme", "Memes");

    companion object {
        fun fromKey(key: String): SmartTag? = entries.firstOrNull { it.key == key }

        /** ",a,b," -> conjunto de tags. */
        fun parse(stored: String?): Set<SmartTag> =
            stored.orEmpty().split(',').mapNotNull { fromKey(it) }.toSet()

        fun encode(tags: Collection<SmartTag>): String =
            if (tags.isEmpty()) "" else tags.joinToString(",", prefix = ",", postfix = ",") { it.key }
    }
}

/**
 * Regras que transformam (caminho, texto do OCR, rótulos de imagem) em categorias.
 * São heurísticas simples e explicáveis; dá pra ajustar as listas de palavras com o uso real.
 */
object Categorizer {

    private val RECEIPT = listOf(
        "comprovante", "pix", "transferencia realizada", "pagamento realizado",
        "pagamento efetuado", "valor pago", "id da transacao", "autenticacao",
    )
    private val BILL = listOf("boleto", "linha digitavel", "codigo de barras", "beneficiario", "fatura")
    private val INVOICE = listOf("nota fiscal", "nf-e", "nfc-e", "danfe", "chave de acesso", "cupom fiscal")

    private val PEOPLE = setOf("selfie", "smile", "beard")
    private val ANIMALS = setOf("dog", "cat", "bird", "pet", "horse", "fish", "butterfly", "insect")
    private val FOOD = setOf(
        "food", "pizza", "cake", "bread", "fruit", "vegetable", "dessert", "cuisine",
        "hamburger", "cookie", "sushi", "pasta", "salad",
    )
    private val LANDSCAPE = setOf(
        "sky", "beach", "mountain", "lake", "sunset", "flower", "plant", "waterfall",
        "river", "sea", "cloud", "garden", "forest",
    )
    private val CARTOON = setOf("comics", "cartoon", "meme")

    fun tags(path: String, kind: FileKind, textNorm: String, labels: Set<String>): Set<SmartTag> {
        val out = mutableSetOf<SmartTag>()
        val p = path.lowercase()
        val name = p.substringAfterLast('/')

        val isPrint = "/screenshots/" in p || name.startsWith("screenshot") ||
            name.startsWith("captura") || name.startsWith("screen_")
        if (isPrint) out += SmartTag.PRINT

        if (RECEIPT.any { it in textNorm }) out += SmartTag.COMPROVANTE
        if (BILL.any { it in textNorm } || ("vencimento" in textNorm && "valor" in textNorm)) out += SmartTag.BOLETO
        if (INVOICE.any { it in textNorm }) out += SmartTag.NOTA_FISCAL

        val isPaperPhoto = kind == FileKind.IMAGE && !isPrint && (textNorm.length >= 200 || "paper" in labels)
        if ((kind == FileKind.PDF && textNorm.isNotBlank()) || isPaperPhoto) out += SmartTag.DOCUMENTO

        if (kind == FileKind.IMAGE) {
            if (labels.any { it in PEOPLE }) out += SmartTag.PESSOAS
            if (labels.any { it in ANIMALS }) out += SmartTag.ANIMAIS
            if (labels.any { it in FOOD }) out += SmartTag.COMIDA
            if (labels.any { it in LANDSCAPE }) out += SmartTag.PAISAGENS

            // Meme: imagem recebida no WhatsApp, com pouco texto por cima, que não é print nem documento financeiro
            val financial = SmartTag.COMPROVANTE in out || SmartTag.BOLETO in out || SmartTag.NOTA_FISCAL in out
            val shortText = textNorm.length in 3..160
            if (labels.any { it in CARTOON } || ("whatsapp" in p && !isPrint && !financial && shortText)) {
                out += SmartTag.MEME
            }
        }
        return out
    }
}
