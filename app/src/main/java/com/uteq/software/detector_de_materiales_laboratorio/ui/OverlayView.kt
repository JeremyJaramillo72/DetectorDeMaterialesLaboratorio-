package com.uteq.software.detector_de_materiales_laboratorio.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.uteq.software.detector_de_materiales_laboratorio.model.DetectionResult
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

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

    private var selectedDetection: DetectionResult? = null
    var onDetectionSelectedListener: ((DetectionResult) -> Unit)? = null

    // Paleta de colores estándar YOLO por clase (26 colores para 26 clases)
    private val CLASS_COLORS = intArrayOf(
        Color.parseColor("#00E676"), // 0: Verde brillante
        Color.parseColor("#00E5FF"), // 1: Cyan / Aqua
        Color.parseColor("#FF6D00"), // 2: Naranja vivo
        Color.parseColor("#7C4DFF"), // 3: Púrpura / Índigo
        Color.parseColor("#FFD600"), // 4: Amarillo brillante
        Color.parseColor("#FF1744"), // 5: Rojo intenso
        Color.parseColor("#00B0FF"), // 6: Azul claro
        Color.parseColor("#F50057"), // 7: Rosa profundo
        Color.parseColor("#651FFF"), // 8: Violeta eléctrico
        Color.parseColor("#1DE9B6"), // 9: Turquesa
        Color.parseColor("#FF9100"), // 10: Ámbar
        Color.parseColor("#00E676"), // 11: Verde esmeralda
        Color.parseColor("#00B0FF"), // 12: Azul cielo
        Color.parseColor("#E040FB"), // 13: Magenta neón
        Color.parseColor("#76FF03"), // 14: Verde lima
        Color.parseColor("#FF5252"), // 15: Coral
        Color.parseColor("#40C4FF"), // 16: Celeste
        Color.parseColor("#FFD740"), // 17: Dorado
        Color.parseColor("#B388FF"), // 18: Lavanda
        Color.parseColor("#00E5FF"), // 19: Cyan brillante
        Color.parseColor("#FF6E40"), // 20: Naranja profundo
        Color.parseColor("#69F0AE"), // 21: Verde menta
        Color.parseColor("#EA80FC"), // 22: Orquídea
        Color.parseColor("#18FFFF"), // 23: Aqua brillante
        Color.parseColor("#FFAB40"), // 24: Ámbar claro
        Color.parseColor("#448AFF")  // 25: Azul índigo
    )

    private val boxPaint = Paint().apply {
        strokeWidth = 7f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    private val labelBgPaint = Paint().apply {
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 38f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        isAntiAlias = true
    }

    fun setResults(detectionResults: List<DetectionResult>, imgWidth: Int, imgHeight: Int) {
        results = detectionResults
        imageWidth = imgWidth
        imageHeight = imgHeight
        invalidate()
    }

    fun clear() {
        results = emptyList()
        selectedDetection = null
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

        for (detection in results) {
            val rawBox = detection.boundingBox

            // Mapear coordenadas a la pantalla
            val left = max(0f, (rawBox.left * scaleFactor) + postScaleWidthOffset)
            val top = max(0f, (rawBox.top * scaleFactor) + postScaleHeightOffset)
            val right = min(viewWidth, (rawBox.right * scaleFactor) + postScaleWidthOffset)
            val bottom = min(viewHeight, (rawBox.bottom * scaleFactor) + postScaleHeightOffset)
            val mappedBox = RectF(left, top, right, bottom)

            // Obtener color asignado a la clase
            val colorIdx = (detection.classIndex).coerceAtLeast(0) % CLASS_COLORS.size
            val color = CLASS_COLORS[colorIdx]

            // 1. Dibujar el recuadro delimitador sólido estilo YOLO
            boxPaint.color = color
            canvas.drawRect(mappedBox, boxPaint)

            // 2. Formatear texto de etiqueta con porcentaje (ej: "Ohaus pr224 85%" o "Dosi-Fiber 96.4%")
            val confVal = detection.confidence * 100f
            val confStr = if (confVal >= 99.95f) "100%" else String.format(Locale.US, "%.1f%%", confVal).replace(".0%", "%")

            // Truncar nombres largos con elipsis para que quepan en pantalla
            val maxLabelWidth = viewWidth * 0.85f // Máximo 85% del ancho de pantalla
            val confTextWidth = textPaint.measureText(" $confStr")
            var displayName = detection.displayName
            var fullLabelText = "$displayName $confStr"
            var textWidth = textPaint.measureText(fullLabelText)
            val paddingH = 16f

            if (textWidth + (paddingH * 2) > maxLabelWidth) {
                // Recortar el nombre progresivamente hasta que quepa
                while (displayName.length > 10 && textPaint.measureText("$displayName… $confStr") + (paddingH * 2) > maxLabelWidth) {
                    displayName = displayName.dropLast(1).trimEnd()
                }
                fullLabelText = "$displayName… $confStr"
                textWidth = textPaint.measureText(fullLabelText)
            }

            val labelText = fullLabelText
            val paddingV = 10f
            val fontMetrics = textPaint.fontMetrics
            val textHeight = fontMetrics.descent - fontMetrics.ascent
            val labelHeight = textHeight + (paddingV * 2)
            val labelWidth = textWidth + (paddingH * 2)

            // 3. Posicionar el encabezado sólido encima de la caja (o dentro si top < labelHeight)
            var labelTop = mappedBox.top - labelHeight
            var labelBottom = mappedBox.top

            if (labelTop < 0) {
                labelTop = mappedBox.top
                labelBottom = mappedBox.top + labelHeight
            }

            val labelLeft = mappedBox.left
            val labelRight = min(viewWidth, labelLeft + labelWidth)
            val labelRect = RectF(labelLeft, labelTop, labelRight, labelBottom)

            // 4. Dibujar fondo sólido del mismo color del recuadro
            labelBgPaint.color = color
            canvas.drawRect(labelRect, labelBgPaint)

            // 5. Dibujar texto en blanco centrado verticalmente en la etiqueta
            val textY = labelTop + paddingV - fontMetrics.ascent
            canvas.save()
            canvas.clipRect(labelRect)
            canvas.drawText(labelText, labelLeft + paddingH, textY, textPaint)
            canvas.restore()
        }
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

                if (mappedBox.contains(touchX, touchY)) {
                    selectedDetection = detection
                    invalidate()
                    onDetectionSelectedListener?.invoke(detection)
                    return true
                }
            }
        }
        return super.onTouchEvent(event)
    }
}
