package com.uteq.software.detector_de_materiales_laboratorio.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import com.uteq.software.detector_de_materiales_laboratorio.R
import com.uteq.software.detector_de_materiales_laboratorio.model.DetectionResult
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/**
 * Capa de anotación sobre el video en vivo.
 *
 * No usa la paleta clara de la app: se dibuja encima de escenas reales
 * impredecibles (equipo blanco sobre pared blanca, o rincones en sombra), así
 * que resuelve su propio contraste con doble trazo — línea de tinta sobre halo
 * blanco — de modo que la marca sobrevive tanto a fondos claros como oscuros.
 *
 * La etiqueta es una pestaña sólida apoyada en el borde superior de la caja:
 * el nombre puede ocupar dos líneas (los nombres de equipo son largos) y la
 * confianza va debajo como lectura, no apretada en la misma línea.
 *
 * Con dos o más equipos en pantalla, el seleccionado se dibuja a contraste
 * total y el resto se atenúa (mismo trazo, menos opacidad) — nunca se ocultan,
 * para que el usuario siga viendo cuántos hay y pueda tocar cualquiera para
 * cambiar la selección. Con 0 o 1 equipo no hay atenuación: el trazo es
 * idéntico al de antes de soportar selección múltiple.
 */
class OverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var results: List<DetectionResult> = emptyList()
    private var imageWidth: Int = 1
    private var imageHeight: Int = 1
    private var scaleFactor: Float = 1.0f
    private var postScaleWidthOffset: Float = 0.0f
    private var postScaleHeightOffset: Float = 0.0f

    private var selectedLabel: String? = null
    var onDetectionSelectedListener: ((DetectionResult) -> Unit)? = null

    /** Atenuación aplicada a un equipo detectado que NO es el seleccionado. */
    private val dimFactor = 0.4f

    private fun dp(value: Float) = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics
    )

    private fun sp(value: Float) = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics
    )

    private val cornerRadius = dp(2f)
    private val labelPaddingH = dp(9f)
    private val labelPaddingV = dp(7f)
    private val lineGap = dp(3f)

    /** Halo blanco: mantiene visible la línea de tinta sobre escenas oscuras. */
    private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(5f)
        color = ContextCompat.getColor(context, R.color.overlay_halo)
    }

    /** Línea de tinta: el trazo real de la anotación. */
    private val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2f)
        color = ContextCompat.getColor(context, R.color.overlay_line)
    }

    private val labelBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.overlay_label_bg)
    }

    private val namePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.white)
        textSize = sp(13f)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }

    private val readoutPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.overlay_readout)
        textSize = sp(12f)
        letterSpacing = 0.06f
        typeface = Typeface.create("sans-serif-condensed-medium", Typeface.NORMAL)
    }

    // Alfa "a contraste total" de cada paint, para poder atenuar y restaurar.
    private val haloAlpha = haloPaint.alpha
    private val boxAlpha = boxPaint.alpha
    private val labelBgAlpha = labelBgPaint.alpha
    private val nameAlpha = namePaint.alpha
    private val readoutAlpha = readoutPaint.alpha

    fun setResults(detectionResults: List<DetectionResult>, imgWidth: Int, imgHeight: Int) {
        results = detectionResults
        imageWidth = imgWidth
        imageHeight = imgHeight
        invalidate()
    }

    /** Marca cuál de los equipos detectados está seleccionado (por label). */
    fun setSelectedLabel(label: String?) {
        if (selectedLabel == label) return
        selectedLabel = label
        invalidate()
    }

    fun clear() {
        results = emptyList()
        selectedLabel = null
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()

        if (viewWidth > 0 && viewHeight > 0 && imageWidth > 0 && imageHeight > 0) {
            val scaleX = viewWidth / imageWidth
            val scaleY = viewHeight / imageHeight
            scaleFactor = max(scaleX, scaleY)

            postScaleWidthOffset = (viewWidth - (imageWidth * scaleFactor)) / 2.0f
            postScaleHeightOffset = (viewHeight - (imageHeight * scaleFactor)) / 2.0f
        }

        // Con un solo equipo (o ninguno) no hay nada que distinguir: todo a
        // contraste total, trazo idéntico al de antes de soportar selección.
        val hasMultiple = results.size > 1

        for (detection in results) {
            val rawBox = detection.boundingBox

            // Mapear coordenadas de la cámara a las dimensiones de pantalla
            val left = max(0f, (rawBox.left * scaleFactor) + postScaleWidthOffset)
            val top = max(0f, (rawBox.top * scaleFactor) + postScaleHeightOffset)
            val right = min(viewWidth, (rawBox.right * scaleFactor) + postScaleWidthOffset)
            val bottom = min(viewHeight, (rawBox.bottom * scaleFactor) + postScaleHeightOffset)
            val mappedBox = RectF(left, top, right, bottom)

            val isDimmed = hasMultiple && !detection.label.equals(selectedLabel, ignoreCase = true)
            applyDim(isDimmed)

            canvas.drawRoundRect(mappedBox, cornerRadius, cornerRadius, haloPaint)
            canvas.drawRoundRect(mappedBox, cornerRadius, cornerRadius, boxPaint)

            drawLabel(canvas, mappedBox, detection, viewWidth)
        }

        applyDim(false)
    }

    private fun applyDim(dimmed: Boolean) {
        val factor = if (dimmed) dimFactor else 1f
        haloPaint.alpha = (haloAlpha * factor).toInt()
        boxPaint.alpha = (boxAlpha * factor).toInt()
        labelBgPaint.alpha = (labelBgAlpha * factor).toInt()
        namePaint.alpha = (nameAlpha * factor).toInt()
        readoutPaint.alpha = (readoutAlpha * factor).toInt()
    }

    private fun drawLabel(
        canvas: Canvas,
        box: RectF,
        detection: DetectionResult,
        viewWidth: Float
    ) {
        val sideMargin = dp(6f)
        val maxTextWidth =
            (min(viewWidth * 0.8f, viewWidth - sideMargin * 2) - labelPaddingH * 2).toInt()
        if (maxTextWidth <= 0) return

        val nameLayout = StaticLayout.Builder
            .obtain(detection.displayName, 0, detection.displayName.length, namePaint, maxTextWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setMaxLines(2)
            .setEllipsize(TextUtils.TruncateAt.END)
            .setIncludePad(false)
            .setLineSpacing(dp(1f), 1f)
            .build()

        val readout = String.format(Locale.getDefault(), "%.1f %%", detection.confidence * 100f)
        val readoutMetrics = readoutPaint.fontMetrics
        val readoutHeight = readoutMetrics.descent - readoutMetrics.ascent

        var widestLine = readoutPaint.measureText(readout)
        for (i in 0 until nameLayout.lineCount) {
            widestLine = max(widestLine, nameLayout.getLineWidth(i))
        }

        val labelWidth = widestLine + labelPaddingH * 2
        val labelHeight = nameLayout.height + lineGap + readoutHeight + labelPaddingV * 2

        // La pestaña se apoya en el borde superior de la caja; si no cabe arriba,
        // se apoya por dentro para no salirse de la pantalla.
        val labelLeft = max(sideMargin, min(box.left, viewWidth - labelWidth - sideMargin))
        val fitsAbove = box.top - labelHeight >= sideMargin
        val labelTop = if (fitsAbove) box.top - labelHeight else box.top + boxPaint.strokeWidth

        canvas.drawRoundRect(
            RectF(labelLeft, labelTop, labelLeft + labelWidth, labelTop + labelHeight),
            cornerRadius,
            cornerRadius,
            labelBgPaint
        )

        canvas.save()
        canvas.translate(labelLeft + labelPaddingH, labelTop + labelPaddingV)
        nameLayout.draw(canvas)
        canvas.restore()

        canvas.drawText(
            readout,
            labelLeft + labelPaddingH,
            labelTop + labelPaddingV + nameLayout.height + lineGap - readoutMetrics.ascent,
            readoutPaint
        )
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            val touchX = event.x
            val touchY = event.y

            for (detection in results) {
                val rawBox = detection.boundingBox
                val left = (rawBox.left * scaleFactor) + postScaleWidthOffset
                val top = (rawBox.top * scaleFactor) + postScaleHeightOffset
                val right = (rawBox.right * scaleFactor) + postScaleWidthOffset
                val bottom = (rawBox.bottom * scaleFactor) + postScaleHeightOffset
                val mappedBox = RectF(left, top, right, bottom)

                if (mappedBox.contains(touchX, touchY) || expand(mappedBox, 48f).contains(touchX, touchY)) {
                    selectedLabel = detection.label
                    invalidate()
                    onDetectionSelectedListener?.invoke(detection)
                    return true
                }
            }
        }
        return super.onTouchEvent(event)
    }

    private fun expand(rect: RectF, pad: Float): RectF {
        return RectF(rect.left - pad, rect.top - pad, rect.right + pad, rect.bottom + pad)
    }
}
