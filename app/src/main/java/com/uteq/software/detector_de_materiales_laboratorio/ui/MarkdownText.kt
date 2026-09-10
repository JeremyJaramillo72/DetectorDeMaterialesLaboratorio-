package com.uteq.software.detector_de_materiales_laboratorio.ui

import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.BulletSpan
import android.text.style.StyleSpan

/**
 * Convierte el markdown ligero que devuelve Gemini (**negrita**, viñetas "- "/"• ")
 * en un Spannable legible. Es solo formato de presentación del texto ya recibido:
 * no toca la respuesta ni la lógica de red/RAG.
 */
object MarkdownText {

    private val boldRegex = Regex("\\*\\*(.+?)\\*\\*")
    private val bulletPrefixRegex = Regex("^[•\\-]\\s+")

    private val emojiRegex = Regex("""[\uD83C-\uDBFF\uDC00-\uDFFF\u2600-\u27BF]""")

    /**
     * Convierte un número entero a palabras en español para reproducción de voz (TTS),
     * garantizando que el motor de voz pronuncie correctamente los millares y unidades
     * sin interpretar signos como comas decimales.
     */
    fun numberToSpanishWords(n: Long): String {
        if (n == 0L) return "cero"
        if (n < 0L) return "menos " + numberToSpanishWords(-n)

        val units = arrayOf(
            "", "un", "dos", "tres", "cuatro", "cinco", "seis", "siete", "ocho", "nueve",
            "diez", "once", "doce", "trece", "catorce", "quince", "dieciséis", "diecisiete",
            "dieciocho", "diecinueve", "veinte", "veintiún", "veintidós", "veintitrés",
            "veinticuatro", "veinticinco", "veintiséis", "veintisiete", "veintiocho", "veintinueve"
        )
        val tens = arrayOf(
            "", "", "", "treinta", "cuarenta", "cincuenta", "sesenta", "setenta", "ochenta", "noventa"
        )
        val hundreds = arrayOf(
            "", "ciento", "doscientos", "trescientos", "cuatrocientos", "quinientos",
            "seiscientos", "setecientos", "ochocientos", "novecientos"
        )

        fun convertUnderThousand(num: Int): String {
            if (num == 0) return ""
            if (num == 100) return "cien"
            val parts = mutableListOf<String>()
            var rem = num
            if (rem >= 100) {
                parts.add(hundreds[rem / 100])
                rem %= 100
            }
            if (rem in 1..29) {
                parts.add(units[rem])
            } else if (rem >= 30) {
                val t = tens[rem / 10]
                val u = rem % 10
                if (u > 0) {
                    parts.add("$t y ${units[u]}")
                } else {
                    parts.add(t)
                }
            }
            return parts.joinToString(" ").trim()
        }

        if (n < 1000L) {
            return convertUnderThousand(n.toInt())
        }

        if (n < 1_000_000L) {
            val thousands = (n / 1000L).toInt()
            val remainder = (n % 1000L).toInt()
            val tStr = if (thousands == 1) "mil" else "${convertUnderThousand(thousands)} mil"
            return if (remainder > 0) {
                "$tStr ${convertUnderThousand(remainder)}".trim()
            } else {
                tStr
            }
        }

        if (n < 2_000_000L) {
            val remainder = n % 1_000_000L
            return if (remainder > 0L) {
                "un millón ${numberToSpanishWords(remainder)}".trim()
            } else {
                "un millón"
            }
        }

        val millions = (n / 1_000_000L).toInt()
        val remainder = n % 1_000_000L
        val mStr = "${convertUnderThousand(millions)} millones"
        return if (remainder > 0L) {
            "$mStr ${numberToSpanishWords(remainder)}".trim()
        } else {
            mStr
        }
    }

    /**
     * Parsea un valor monetario distinguiendo rigurosamente cuándo la coma representa
     * miles (ej: 7,800 -> 7800) y cuándo representa centavos decimales (ej: 15,50 -> 15 dólares con 50 centavos).
     * Devuelve Pair(parteEntera, centavos).
     */
    fun parsePriceValue(raw: String): Pair<Long, Int> {
        val clean = raw.trim()
            .replace("$", "")
            .replace("USD", "", ignoreCase = true)
            .replace("dólares", "", ignoreCase = true)
            .replace("dolares", "", ignoreCase = true)
            .trim()

        if (clean.isBlank()) return Pair(0L, 0)

        // Caso 1: Contiene tanto coma como punto
        if (clean.contains(",") && clean.contains(".")) {
            val commaIdx = clean.indexOf(",")
            val dotIdx = clean.indexOf(".")
            return if (commaIdx < dotIdx) {
                // Formato 1,250.75 -> coma es miles, punto es centavos
                val parts = clean.split(".")
                val integ = parts[0].replace(",", "").toLongOrNull() ?: 0L
                val centsRaw = parts.getOrNull(1)?.take(2)?.padEnd(2, '0') ?: "0"
                val cents = centsRaw.toIntOrNull() ?: 0
                Pair(integ, cents)
            } else {
                // Formato 1.250,75 -> punto es miles, coma es centavos
                val parts = clean.split(",")
                val integ = parts[0].replace(".", "").toLongOrNull() ?: 0L
                val centsRaw = parts.getOrNull(1)?.take(2)?.padEnd(2, '0') ?: "0"
                val cents = centsRaw.toIntOrNull() ?: 0
                Pair(integ, cents)
            }
        }

        // Caso 2: Contiene solo coma
        if (clean.contains(",")) {
            val parts = clean.split(",")
            if (parts.size == 2) {
                if (parts[1].length == 3) {
                    // 7,800 -> Miles (la coma es separador de miles en el catálogo UTEQ)
                    val integ = (parts[0] + parts[1]).toLongOrNull() ?: 0L
                    return Pair(integ, 0)
                } else if (parts[1].length in 1..2) {
                    // 15,50 o 15,5 -> Centavos (la coma es separador decimal)
                    val integ = parts[0].toLongOrNull() ?: 0L
                    val centsRaw = parts[1].take(2).padEnd(2, '0')
                    val cents = centsRaw.toIntOrNull() ?: 0
                    return Pair(integ, cents)
                }
            }
            val integ = clean.replace(",", "").toLongOrNull() ?: 0L
            return Pair(integ, 0)
        }

        // Caso 3: Contiene solo punto
        if (clean.contains(".")) {
            val parts = clean.split(".")
            if (parts.size == 2) {
                if (parts[1].length == 3 && parts[0].length in 1..3) {
                    // 7.800 -> Miles
                    val integ = (parts[0] + parts[1]).toLongOrNull() ?: 0L
                    return Pair(integ, 0)
                } else if (parts[1].length in 1..2) {
                    // 15.50 o 15.5 -> Centavos
                    val integ = parts[0].toLongOrNull() ?: 0L
                    val centsRaw = parts[1].take(2).padEnd(2, '0')
                    val cents = centsRaw.toIntOrNull() ?: 0
                    return Pair(integ, cents)
                }
            }
            val integ = clean.replace(".", "").toLongOrNull() ?: 0L
            return Pair(integ, 0)
        }

        // Caso 4: Entero puro sin signos
        val digits = clean.filter { it.isDigit() }
        val integ = digits.toLongOrNull() ?: 0L
        return Pair(integ, 0)
    }

    /**
     * Convierte un monto numérico a frase en palabras:
     * ej. "7,800" -> "siete mil ochocientos dólares"
     * ej. "15,50" -> "quince dólares con cincuenta centavos"
     */
    fun formatPricePhrase(raw: String): String {
        val (integ, cents) = parsePriceValue(raw)
        val words = numberToSpanishWords(integ)
        val currency = if (integ == 1L) "dólar" else "dólares"
        return if (cents > 0) {
            val centsWords = numberToSpanishWords(cents.toLong())
            val centLabel = if (cents == 1) "centavo" else "centavos"
            "$words $currency con $centsWords $centLabel"
        } else {
            "$words $currency"
        }
    }

    /**
     * Formatea el precio exactamente como consta en la ficha técnica para que sea
     * pronunciado por voz de forma precisa, diciendo "cuesta [monto] dólares..."
     * sin emojis ni signos como $, asteriscos o paréntesis, y con miles convertidos en palabras.
     */
    fun formatPriceForSpeech(rawPrice: String?): String {
        if (rawPrice.isNullOrBlank() || rawPrice.contains("no especificado", ignoreCase = true)) {
            return "precio no especificado en la ficha técnica"
        }

        // Detectar patrón con rango: "$X USD (Rango estimado: $Y - $Z USD)"
        val rangeRegex = Regex("""(?i)\$?\s*([\d,.]+)\s*USD?\s*\(rango\s*estimado:?\s*\$?\s*([\d,.]+)\s*[-–—]\s*\$?\s*([\d,.]+)\s*USD?\)""")
        val match = rangeRegex.find(rawPrice.trim())
        if (match != null) {
            val baseStr = match.groupValues[1]
            val minStr = match.groupValues[2]
            val maxStr = match.groupValues[3]

            val basePhrase = formatPricePhrase(baseStr)
            val (minInteg, minCents) = parsePriceValue(minStr)
            val (maxInteg, maxCents) = parsePriceValue(maxStr)

            val rangePhrase = if (minCents == 0 && maxCents == 0) {
                "con un rango estimado de ${numberToSpanishWords(minInteg)} a ${numberToSpanishWords(maxInteg)} dólares"
            } else {
                "con un rango estimado de ${formatPricePhrase(minStr)} a ${formatPricePhrase(maxStr)}"
            }
            return "cuesta $basePhrase, $rangePhrase"
        }

        // Detectar patrón simple: "$X USD"
        val simpleRegex = Regex("""(?i)\$?\s*([\d,.]+)\s*USD?""")
        val matchSimple = simpleRegex.find(rawPrice.trim())
        if (matchSimple != null) {
            val basePhrase = formatPricePhrase(matchSimple.groupValues[1])
            return "cuesta $basePhrase"
        }

        return "cuesta ${formatPricePhrase(rawPrice)}"
    }

    /**
     * Versión hablable del texto: sin emojis, sin `**`, sin signos que el TTS
     * pronuncia de forma literal (como $, asteriscos o viñetas), y convirtiendo
     * precios y millares en palabras para que la coma nunca sea leída como decimal.
     */
    fun stripForSpeech(raw: String): String {
        // 1. Quitar emojis
        var text = emojiRegex.replace(raw, "")

        // 2. Reemplazar rangos de precio completos en texto libre
        val rangeRegex = Regex("""(?i)\$?\s*([\d,.]+)\s*USD?\s*\(rango\s*estimado:?\s*\$?\s*([\d,.]+)\s*[-–—]\s*\$?\s*([\d,.]+)\s*USD?\)""")
        text = rangeRegex.replace(text) { m ->
            val basePhrase = formatPricePhrase(m.groupValues[1])
            val (minInteg, minCents) = parsePriceValue(m.groupValues[2])
            val (maxInteg, maxCents) = parsePriceValue(m.groupValues[3])
            val rangePhrase = if (minCents == 0 && maxCents == 0) {
                "con un rango estimado de ${numberToSpanishWords(minInteg)} a ${numberToSpanishWords(maxInteg)} dólares"
            } else {
                "con un rango estimado de ${formatPricePhrase(m.groupValues[2])} a ${formatPricePhrase(m.groupValues[3])}"
            }
            "$basePhrase, $rangePhrase"
        }

        // 3. Reemplazar valores con signo de dólar ($X o $X USD)
        text = Regex("""(?i)\$\s*([\d,.]+)\s*(?:USD)?""").replace(text) { m ->
            formatPricePhrase(m.groupValues[1])
        }

        // 4. Reemplazar expresiones con USD o dólares al final (ej. "7,800 USD", "15,50 dólares")
        text = Regex("""(?i)\b([\d,.]+)\s*(?:USD|dólares|dolares)\b""").replace(text) { m ->
            formatPricePhrase(m.groupValues[1])
        }

        // 5. Normalizar cualquier coma de miles en números genéricos restantes (ej. "12,000 rpm" -> "12000 rpm")
        // para que el TTS no pronuncie la coma como decimal
        while (true) {
            val updated = Regex("""\b(\d{1,3}),(\d{3})\b""").replace(text, "$1$2")
            if (updated == text) break
            text = updated
        }

        // 6. Quitar markdown negritas
        text = boldRegex.replace(text) { it.groupValues[1] }

        // 7. Quitar signos que el TTS pronuncia de forma literal o errónea
        text = text
            .replace("*", "")
            .replace("#", "")
            .replace("`", "")
            .replace("~", "")
            .replace("_", " ")
            .replace("(", " ")
            .replace(")", " ")
            .replace(":", " ")
            .replace(Regex("""[-–—]"""), " ")

        return text.split("\n")
            .map { it.replaceFirst(bulletPrefixRegex, "").trim() }
            .filter { it.isNotEmpty() }
            .joinToString(". ")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    fun format(raw: String, bulletGapPx: Int = 18): CharSequence {
        val builder = SpannableStringBuilder()

        raw.split("\n").forEachIndexed { index, line ->
            if (index > 0) builder.append("\n")

            val isBullet = bulletPrefixRegex.containsMatchIn(line)
            val content = if (isBullet) line.replaceFirst(bulletPrefixRegex, "") else line

            val lineStart = builder.length
            appendWithBoldSpans(builder, content)

            if (isBullet) {
                builder.setSpan(
                    BulletSpan(bulletGapPx),
                    lineStart,
                    builder.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }

        return builder
    }

    private fun appendWithBoldSpans(builder: SpannableStringBuilder, line: String) {
        var lastIndex = 0
        for (match in boldRegex.findAll(line)) {
            if (match.range.first > lastIndex) {
                builder.append(line.substring(lastIndex, match.range.first))
            }
            val boldStart = builder.length
            builder.append(match.groupValues[1])
            builder.setSpan(
                StyleSpan(android.graphics.Typeface.BOLD),
                boldStart,
                builder.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            lastIndex = match.range.last + 1
        }
        if (lastIndex < line.length) {
            builder.append(line.substring(lastIndex))
        }
    }
}
