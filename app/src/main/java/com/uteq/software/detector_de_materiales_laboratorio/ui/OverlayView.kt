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

    // Paleta de colores Neón de Grado Científico para visión computacional
    private val CLASS_COLORS = intArrayOf(
        Color.parseColor("#00E676"), // 0: Verde Neón / Esmeralda
        Color.parseColor("#00E5FF"), // 1: Cyan Eléctrico
        Color.parseColor("#FF9100"), // 2: Ámbar Neón
        Color.parseColor("#A855F7"), // 3: Púrpura Neón
        Color.parseColor("#FFD600"), // 4: Amarillo Neón
        Color.parseColor("#F43F5E"), // 5: Coral Rosa Neón
        Color.parseColor("#38BDF8"), // 6: Azul Hielo
        Color.parseColor("#2DD4BF")  // 7: Turquesa Menta
    )

    // Bounding Box con trazo limpio
    private val boxPaint = Paint().apply {
        strokeWidth = 3.5f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    // Corchetes tácticos de mira en las 4 esquinas (efecto AR de laboratorio)
    private val cornerPaint = Paint().apply {
        strokeWidth = 6.5f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        isAntiAlias = true
    }

    // Fondo del badge de etiqueta tipo cápsula esmerilada
    private val badgeBgPaint = Paint().apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#E6090E18")
        isAntiAlias = true
    }

    // Borde brillante de la cápsula de etiqueta
    private val badgeBorderPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.8f
        isAntiAlias = true
    }

    // Fondo del porcentaje de confianza (píldora sólida)
    private val confPillPaint = Paint().apply {
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    // Texto del nombre de clase
    private val labelTextPaint = Paint().apply {
        color = Color.WHITE
        textSize = 34f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        isAntiAlias = true
    }

    // Texto del porcentaje (dentro de la píldora)
    private val confTextPaint = Paint().apply {
        color = Color.parseColor("#070A11")
        textSize = 26f
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

            // Mapear coordenadas de la cámara a las dimensiones de pantalla
            val left = max(0f, (rawBox.left * scaleFactor) + postScaleWidthOffset)
            val top = max(0f, (rawBox.top * scaleFactor) + postScaleHeightOffset)
            val right = min(viewWidth, (rawBox.right * scaleFactor) + postScaleWidthOffset)
            val bottom = min(viewHeight, (rawBox.bottom * scaleFactor) + postScaleHeightOffset)
            val mappedBox = RectF(left, top, right, bottom)

            val colorIdx = (detection.classIndex).coerceAtLeast(0) % CLASS_COLORS.size
            val color = CLASS_COLORS[colorIdx]

            // 1. Dibujar el recuadro delimitador con bordes suaves redondeados
            boxPaint.color = color
            boxPaint.alpha = 210
            canvas.drawRoundRect(mappedBox, 14f, 14f, boxPaint)

            // 2. Dibujar los corchetes tácticos de mira en las 4 esquinas
            cornerPaint.color = color
            val cornerLen = min(32f, min(mappedBox.width() * 0.25f, mappedBox.height() * 0.25f))

            // Esquina superior izquierda
            canvas.drawLine(mappedBox.left, mappedBox.top + cornerLen, mappedBox.left, mappedBox.top, cornerPaint)
            canvas.drawLine(mappedBox.left, mappedBox.top, mappedBox.left + cornerLen, mappedBox.top, cornerPaint)

            // Esquina superior derecha
            canvas.drawLine(mappedBox.right - cornerLen, mappedBox.top, mappedBox.right, mappedBox.top, cornerPaint)
            canvas.drawLine(mappedBox.right, mappedBox.top, mappedBox.right, mappedBox.top + cornerLen, cornerPaint)

            // Esquina inferior izquierda
            canvas.drawLine(mappedBox.left, mappedBox.bottom - cornerLen, mappedBox.left, mappedBox.bottom, cornerPaint)
            canvas.drawLine(mappedBox.left, mappedBox.bottom, mappedBox.left + cornerLen, mappedBox.bottom, cornerPaint)

            // Esquina inferior derecha
            canvas.drawLine(mappedBox.right - cornerLen, mappedBox.bottom, mappedBox.right, mappedBox.bottom, cornerPaint)
            canvas.drawLine(mappedBox.right, mappedBox.bottom, mappedBox.right, mappedBox.bottom - cornerLen, cornerPaint)

            // 3. Preparar textos de etiqueta y porcentaje
            val confVal = detection.confidence * 100f
            val confStr = if (confVal >= 99.95f) "100%" else String.format(Locale.US, "%.1f%%", confVal).replace(".0%", "%")

            var displayName = detection.displayName
            val maxAllowedWidth = viewWidth * 0.85f

            val fontMetrics = labelTextPaint.fontMetrics
            val textHeight = fontMetrics.descent - fontMetrics.ascent
            val confMetrics = confTextPaint.fontMetrics
            val confTextW = confTextPaint.measureText(confStr)
            val confPillW = confTextW + 18f
            val confPillH = textHeight * 0.78f

            val paddingH = 14f
            val paddingV = 8f
            val gap = 10f

            // Recortar con elipsis si el nombre es muy largo
            while (displayName.length > 8 &&
                (paddingH * 2 + labelTextPaint.measureText("$displayName…") + gap + confPillW > maxAllowedWidth)) {
                displayName = displayName.dropLast(1).trimEnd()
            }
            val finalName = if (displayName != detection.displayName) "$displayName…" else displayName
            val nameTextW = labelTextPaint.measureText(finalName)

            val badgeWidth = paddingH + nameTextW + gap + confPillW + paddingH
            val badgeHeight = textHeight + (paddingV * 2)

            // 4. Posicionar la cápsula de etiqueta flotante encima de la caja
            var badgeTop = mappedBox.top - badgeHeight - 6f
            var badgeBottom = mappedBox.top - 6f

            if (badgeTop < 10f) {
                // Si choca con el tope, dibujarla dentro de la caja en la parte superior
                badgeTop = mappedBox.top + 8f
                badgeBottom = badgeTop + badgeHeight
            }

            val badgeLeft = max(8f, min(mappedBox.left, viewWidth - badgeWidth - 8f))
            val badgeRight = badgeLeft + badgeWidth
            val badgeRect = RectF(badgeLeft, badgeTop, badgeRight, badgeBottom)

            // 5. Dibujar fondo de cápsula y borde neón sutil
            canvas.drawRoundRect(badgeRect, 12f, 12f, badgeBgPaint)
            badgeBorderPaint.color = color
            badgeBorderPaint.alpha = 180
            canvas.drawRoundRect(badgeRect, 12f, 12f, badgeBorderPaint)

            // 6. Dibujar texto del nombre de equipo en blanco
            val textY = badgeTop + paddingV - fontMetrics.ascent
            canvas.drawText(finalName, badgeLeft + paddingH, textY, labelTextPaint)

            // 7. Dibujar píldora de porcentaje con color de clase y texto oscuro
            val pillLeft = badgeLeft + paddingH + nameTextW + gap
            val pillTop = badgeTop + (badgeHeight - confPillH) / 2f
            val pillRight = pillLeft + confPillW
            val pillBottom = pillTop + confPillH
            val pillRect = RectF(pillLeft, pillTop, pillRight, pillBottom)

            confPillPaint.color = color
            canvas.drawRoundRect(pillRect, 8f, 8f, confPillPaint)

            val confTextX = pillLeft + 9f
            val confTextY = pillTop + (confPillH - (confMetrics.descent - confMetrics.ascent)) / 2f - confMetrics.ascent
            canvas.drawText(confStr, confTextX, confTextY, confTextPaint)
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
