package com.uteq.software.detector_de_materiales_laboratorio.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import com.uteq.software.detector_de_materiales_laboratorio.R
import com.uteq.software.detector_de_materiales_laboratorio.model.DetectionResult
import kotlin.math.max

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

    private val boxPaint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.box_emerald)
        strokeWidth = 8f
        style = Paint.Style.STROKE
        isAntiAlias = true
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val selectedBoxPaint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.box_selected)
        strokeWidth = 12f
        style = Paint.Style.STROKE
        isAntiAlias = true
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val cornerPaint = Paint().apply {
        color = Color.WHITE
        strokeWidth = 14f
        style = Paint.Style.STROKE
        isAntiAlias = true
        strokeCap = Paint.Cap.SQUARE
    }

    private val textBackgroundPaint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.box_text_bg)
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 42f
        isFakeBoldText = true
        isAntiAlias = true
    }

    private val confidenceBadgePaint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.uteq_accent)
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val confidenceTextPaint = Paint().apply {
        color = Color.WHITE
        textSize = 34f
        isFakeBoldText = true
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

            val left = (rawBox.left * scaleFactor) + postScaleWidthOffset
            val top = (rawBox.top * scaleFactor) + postScaleHeightOffset
            val right = (rawBox.right * scaleFactor) + postScaleWidthOffset
            val bottom = (rawBox.bottom * scaleFactor) + postScaleHeightOffset
            val mappedBox = RectF(left, top, right, bottom)

            val isSelected = selectedDetection == detection
            val currentBoxPaint = if (isSelected) selectedBoxPaint else boxPaint

            // 1. Dibujar Bounding Box
            canvas.drawRoundRect(mappedBox, 16f, 16f, currentBoxPaint)

            // 2. Dibujar esquinas reforzadas estilo HUD futurista
            val cornerLength = 36f
            // Superior Izquierda
            canvas.drawLine(mappedBox.left, mappedBox.top, mappedBox.left + cornerLength, mappedBox.top, cornerPaint)
            canvas.drawLine(mappedBox.left, mappedBox.top, mappedBox.left, mappedBox.top + cornerLength, cornerPaint)
            // Superior Derecha
            canvas.drawLine(mappedBox.right - cornerLength, mappedBox.top, mappedBox.right, mappedBox.top, cornerPaint)
            canvas.drawLine(mappedBox.right, mappedBox.top, mappedBox.right, mappedBox.top + cornerLength, cornerPaint)
            // Inferior Izquierda
            canvas.drawLine(mappedBox.left, mappedBox.bottom, mappedBox.left + cornerLength, mappedBox.bottom, cornerPaint)
            canvas.drawLine(mappedBox.left, mappedBox.bottom - cornerLength, mappedBox.left, mappedBox.bottom, cornerPaint)
            // Inferior Derecha
            canvas.drawLine(mappedBox.right - cornerLength, mappedBox.bottom, mappedBox.right, mappedBox.bottom, cornerPaint)
            canvas.drawLine(mappedBox.right, mappedBox.bottom - cornerLength, mappedBox.right, mappedBox.bottom, cornerPaint)

            // 3. Etiqueta con Nombre del Equipo y Confianza %
            val labelText = detection.displayName
            val confidenceText = "${(detection.confidence * 100).toInt()}%"

            val textWidth = textPaint.measureText(labelText)
            val confWidth = confidenceTextPaint.measureText(confidenceText)
            val tagHeight = 70f
            val padding = 20f

            val tagTop = max(10f, mappedBox.top - tagHeight - 12f)
            val tagBottom = tagTop + tagHeight
            val tagLeft = mappedBox.left
            val tagRight = tagLeft + textWidth + confWidth + (padding * 3)

            val tagRect = RectF(tagLeft, tagTop, tagRight, tagBottom)
            canvas.drawRoundRect(tagRect, 12f, 12f, textBackgroundPaint)

            val textY = tagTop + (tagHeight / 2f) - ((textPaint.descent() + textPaint.ascent()) / 2f)
            canvas.drawText(labelText, tagLeft + padding, textY, textPaint)

            // Badge de Confianza
            val badgeLeft = tagLeft + textWidth + (padding * 1.5f)
            val badgeRight = tagRight - padding / 2f
            val badgeRect = RectF(badgeLeft, tagTop + 10f, badgeRight, tagBottom - 10f)
            canvas.drawRoundRect(badgeRect, 8f, 8f, confidenceBadgePaint)

            val confY = badgeRect.centerY() - ((confidenceTextPaint.descent() + confidenceTextPaint.ascent()) / 2f)
            val confX = badgeRect.left + (badgeRect.width() - confWidth) / 2f
            canvas.drawText(confidenceText, confX, confY, confidenceTextPaint)
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
