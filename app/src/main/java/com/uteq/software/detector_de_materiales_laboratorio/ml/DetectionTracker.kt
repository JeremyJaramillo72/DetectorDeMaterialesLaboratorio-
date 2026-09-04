package com.uteq.software.detector_de_materiales_laboratorio.ml

import android.graphics.RectF
import com.uteq.software.detector_de_materiales_laboratorio.model.DetectionResult
import kotlin.math.max

/**
 * Estabiliza detecciones y exige confirmación multi-frame
 * para no etiquetar objetos random como equipos.
 */
class DetectionTracker(
    private val holdMs: Long = 600L,
    private val switchVotesNeeded: Int = 3,
    private val confirmVotesNeeded: Int = 2,
    private val classSwitchMargin: Float = 0.18f,
    private val boxSmoothAlpha: Float = 0.55f,
    private val minPublishConfidence: Float = 0.60f
) {
    private var held: DetectionResult? = null
    private var lastSeenMs: Long = 0L
    private var pendingLabel: String? = null
    private var pendingVotes: Int = 0
    private var confirmLabel: String? = null
    private var confirmVotes: Int = 0

    var detectionCache: EquipmentDetectionCache? = null

    fun update(
        incoming: List<DetectionResult>,
        frameWidth: Int,
        frameHeight: Int,
        nowMs: Long = System.currentTimeMillis()
    ): List<DetectionResult> {
        // Solo candidatos con confianza real (nada de inflar)
        val candidates = (detectionCache?.boostIfCached(incoming) ?: incoming)
            .filter { it.confidence >= minPublishConfidence }

        val best = candidates.maxByOrNull { it.confidence }

        if (best == null) {
            // Sin detección válida: soltar rápido el recuadro fantasma
            val current = held ?: return emptyList()
            return if (nowMs - lastSeenMs <= holdMs) {
                listOf(current)
            } else {
                clearSessionHold()
                emptyList()
            }
        }

        // Exigir 2 frames seguidos de la misma clase antes de mostrar (anti falso positivo)
        if (confirmLabel.equals(best.label, ignoreCase = true)) {
            confirmVotes++
        } else {
            confirmLabel = best.label
            confirmVotes = 1
        }

        val known = detectionCache?.isKnown(best.label) == true
        val votesNeeded = if (known) 2 else confirmVotesNeeded
        if (confirmVotes < votesNeeded && held?.label?.equals(best.label, true) != true) {
            // Aún no confirmado: si había algo distinto, mantener hold corto; si no, vacío
            val current = held ?: return emptyList()
            return if (nowMs - lastSeenMs <= holdMs) listOf(current) else emptyList()
        }

        lastSeenMs = nowMs
        held = mergeWithHeld(best)
        return listOf(held!!)
    }

    fun clear() {
        clearSessionHold()
    }

    private fun clearSessionHold() {
        held = null
        pendingLabel = null
        pendingVotes = 0
        confirmLabel = null
        confirmVotes = 0
    }

    private fun mergeWithHeld(incoming: DetectionResult): DetectionResult {
        val previous = held
        if (previous == null) {
            pendingLabel = null
            pendingVotes = 0
            return incoming.copy(boundingBox = RectF(incoming.boundingBox))
        }

        val sameClass = previous.label.equals(incoming.label, ignoreCase = true)
        if (sameClass) {
            pendingLabel = null
            pendingVotes = 0
            return DetectionResult(
                boundingBox = smoothBox(previous.boundingBox, incoming.boundingBox),
                label = previous.label,
                displayName = previous.displayName,
                confidence = (previous.confidence * 0.3f) + (incoming.confidence * 0.7f),
                classIndex = previous.classIndex
            )
        }

        val strongEnough = incoming.confidence >= previous.confidence + classSwitchMargin
        if (strongEnough && pendingLabel == incoming.label) {
            pendingVotes++
        } else if (strongEnough) {
            pendingLabel = incoming.label
            pendingVotes = 1
        } else {
            pendingLabel = null
            pendingVotes = 0
        }

        return if (pendingVotes >= switchVotesNeeded) {
            pendingLabel = null
            pendingVotes = 0
            incoming.copy(boundingBox = RectF(incoming.boundingBox))
        } else {
            DetectionResult(
                boundingBox = previous.boundingBox,
                label = previous.label,
                displayName = previous.displayName,
                confidence = previous.confidence * 0.9f,
                classIndex = previous.classIndex
            )
        }
    }

    private fun smoothBox(prev: RectF, next: RectF, alpha: Float = boxSmoothAlpha): RectF {
        val a = alpha
        return RectF(
            prev.left * (1f - a) + next.left * a,
            prev.top * (1f - a) + next.top * a,
            prev.right * (1f - a) + next.right * a,
            prev.bottom * (1f - a) + next.bottom * a
        )
    }
}
