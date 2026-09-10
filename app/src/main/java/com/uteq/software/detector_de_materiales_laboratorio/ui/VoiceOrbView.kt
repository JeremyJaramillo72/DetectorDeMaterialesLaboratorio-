package com.uteq.software.detector_de_materiales_laboratorio.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.content.ContextCompat
import com.uteq.software.detector_de_materiales_laboratorio.R
import kotlin.math.min
import kotlin.math.sin

/**
 * Representación visual del asistente en la conversación por voz: un anillo
 * de contorno, no un ícono ni un avatar — mismo lenguaje "solo contorno, sin
 * relleno" que ya usan las tarjetas y chips del resto de la app. El estado
 * se comunica con color y movimiento, no con texto encima:
 *
 * - ESCUCHANDO: el anillo reacciona en vivo al volumen de la voz del usuario
 *   (ver [setAudioLevel], alimentado por `onRmsChanged` del reconocedor).
 * - PROCESANDO: un arco gira sobre el anillo, como un progreso indeterminado.
 * - RESPONDIENDO: el anillo respira en @color/accent — el mismo acento que
 *   ya marca "estado activo real" en el resto del sistema.
 * - INACTIVO/ERROR: anillo estático y tenue.
 *
 * Un único ValueAnimator en bucle conduce las tres animaciones (respiración,
 * rotación, suavizado del nivel de audio) — no hay Lottie ni GIFs, es Canvas
 * puro, igual que OverlayView.
 */
class VoiceOrbView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class Mode { IDLE, LISTENING, PROCESSING, SPEAKING }

    private var mode = Mode.IDLE
    private var targetLevel = 0f
    private var displayLevel = 0f

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val colorInk = ContextCompat.getColor(context, R.color.ink)
    private val colorInkSoft = ContextCompat.getColor(context, R.color.ink_soft)
    private val colorInkFaint = ContextCompat.getColor(context, R.color.ink_faint)
    private val colorAccent = ContextCompat.getColor(context, R.color.accent)

    private val driver = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 16
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            displayLevel += (targetLevel - displayLevel) * 0.18f
            invalidate()
        }
    }

    fun setMode(newMode: Mode) {
        if (mode == newMode) return
        mode = newMode
        if (newMode != Mode.LISTENING) targetLevel = 0f
        invalidate()
    }

    /** Nivel de audio en vivo del usuario, 0..1. Suavizado internamente. */
    fun setAudioLevel(rmsDb: Float) {
        targetLevel = ((rmsDb - 1f) / 9f).coerceIn(0f, 1f)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        driver.start()
    }

    override fun onDetachedFromWindow() {
        driver.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val baseRadius = min(width, height) * 0.28f
        val now = System.currentTimeMillis()

        when (mode) {
            Mode.IDLE -> {
                val breathe = 0.75f + 0.25f * ((sin(now / 900.0) + 1) / 2).toFloat()
                ringPaint.color = colorInkFaint
                ringPaint.alpha = (breathe * 255).toInt()
                ringPaint.strokeWidth = dp(2f)
                canvas.drawCircle(cx, cy, baseRadius, ringPaint)
            }

            Mode.LISTENING -> {
                ringPaint.color = colorInk
                ringPaint.alpha = 255
                ringPaint.strokeWidth = dp(2.5f)
                canvas.drawCircle(cx, cy, baseRadius + displayLevel * dp(22f), ringPaint)
            }

            Mode.PROCESSING -> {
                ringPaint.color = colorInkFaint
                ringPaint.alpha = 255
                ringPaint.strokeWidth = dp(2f)
                canvas.drawCircle(cx, cy, baseRadius, ringPaint)

                val sweepAngle = 70f
                val startAngle = (now % 1400L) / 1400f * 360f
                arcPaint.color = colorInkSoft
                arcPaint.strokeWidth = dp(2.5f)
                val rect = android.graphics.RectF(
                    cx - baseRadius, cy - baseRadius, cx + baseRadius, cy + baseRadius
                )
                canvas.drawArc(rect, startAngle, sweepAngle, false, arcPaint)
            }

            Mode.SPEAKING -> {
                val breathe = 0.85f + 0.15f * ((sin(now / 260.0) + 1) / 2).toFloat()
                ringPaint.color = colorAccent
                ringPaint.alpha = 255
                ringPaint.strokeWidth = dp(3f) * breathe
                canvas.drawCircle(cx, cy, baseRadius + dp(4f) * breathe, ringPaint)
            }
        }
    }

    private fun dp(value: Float) = value * resources.displayMetrics.density
}
