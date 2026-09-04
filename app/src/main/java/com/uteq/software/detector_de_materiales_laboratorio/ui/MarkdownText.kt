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
