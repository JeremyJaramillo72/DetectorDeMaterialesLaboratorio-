package com.uteq.software.detector_de_materiales_laboratorio.ml

import android.graphics.RectF
import com.uteq.software.detector_de_materiales_laboratorio.model.DetectionResult
import kotlin.math.max
import kotlin.math.min

/**
 * Estabiliza detecciones y exige confirmación multi-frame antes de publicar
 * un equipo, para no etiquetar objetos random como equipos — para varios
 * equipos simultáneos, cada uno con su propio ciclo de vida independiente.
 *
 * Cada "slot" trackeado representa una posición física en la escena, no una
 * clase: el candidato de cada frame se empareja con el slot existente de
 * mayor solapamiento espacial (IoU), no por índice ni por ser el top-1 de
 * confianza global. Así, dos equipos distintos y estables mantienen cada
 * uno su propio slot, su propio hold y su propia histéresis de clase — la
 * misma lógica anti-parpadeo que antes protegía a un solo objeto, ahora
 * aplicada por objeto.
 */
class DetectionTracker(
    private val holdMs: Long = 600L,
    private val switchVotesNeeded: Int = 3,
    private val confirmVotesNeeded: Int = 2,
    private val classSwitchMargin: Float = 0.18f,
    private val boxSmoothAlpha: Float = 0.55f,
    private val minPublishConfidence: Float = 0.60f,
    private val matchIouThreshold: Float = 0.30f
) {
    private class Slot(
        var held: DetectionResult,
        var lastSeenMs: Long,
        var confirmLabel: String,
        var confirmVotes: Int,
        var confirmed: Boolean,
        var pendingLabel: String? = null,
        var pendingVotes: Int = 0
    )

    private val slots = ArrayList<Slot>()

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

        val usedCandidates = BooleanArray(candidates.size)

        // 1) Emparejar cada slot existente con el candidato de mayor solapamiento
        //    espacial este frame (misma posición física, no misma clase).
        for (slot in slots) {
            var bestIdx = -1
            var bestIou = matchIouThreshold
            for (i in candidates.indices) {
                if (usedCandidates[i]) continue
                val iou = calculateIoU(slot.held.boundingBox, candidates[i].boundingBox)
                if (iou >= bestIou) {
                    bestIou = iou
                    bestIdx = i
                }
            }
            if (bestIdx >= 0) {
                usedCandidates[bestIdx] = true
                applyCandidate(slot, candidates[bestIdx], nowMs)
            }
        }

        // 2) Candidatos sin slot emparejado: arrancan su propio ciclo de
        //    confirmación — un equipo nuevo no aparece de golpe en un frame.
        for (i in candidates.indices) {
            if (usedCandidates[i]) continue
            val cand = candidates[i]
            slots.add(
                Slot(
                    held = cand.copy(boundingBox = RectF(cand.boundingBox)),
                    lastSeenMs = nowMs,
                    confirmLabel = cand.label,
                    confirmVotes = 1,
                    confirmed = false
                )
            )
        }

        // 3) Slots no vistos este frame: se sueltan tras holdMs sin señal
        //    (recuadro fantasma corto en vez de parpadeo instantáneo).
        slots.removeAll { nowMs - it.lastSeenMs > holdMs }

        return slots.filter { it.confirmed }.map { it.held }
    }

    fun clear() {
        slots.clear()
    }

    private fun applyCandidate(slot: Slot, candidate: DetectionResult, nowMs: Long) {
        slot.lastSeenMs = nowMs

        val sameClass = slot.held.label.equals(candidate.label, ignoreCase = true)
        if (sameClass) {
            slot.pendingLabel = null
            slot.pendingVotes = 0

            if (slot.confirmLabel.equals(candidate.label, ignoreCase = true)) {
                slot.confirmVotes++
            } else {
                slot.confirmLabel = candidate.label
                slot.confirmVotes = 1
            }
            if (!slot.confirmed) {
                val known = detectionCache?.isKnown(candidate.label) == true
                val votesNeeded = if (known) 2 else confirmVotesNeeded
                if (slot.confirmVotes >= votesNeeded) slot.confirmed = true
            }

            slot.held = DetectionResult(
                boundingBox = smoothBox(slot.held.boundingBox, candidate.boundingBox),
                label = slot.held.label,
                displayName = slot.held.displayName,
                confidence = (slot.held.confidence * 0.3f) + (candidate.confidence * 0.7f),
                classIndex = slot.held.classIndex
            )
            return
        }

        // Clase distinta en la misma posición: exigir margen de confianza claro
        // durante varios frames seguidos antes de aceptar el cambio (anti-parpadeo
        // entre dos clases visualmente parecidas sobre el mismo objeto físico).
        val strongEnough = candidate.confidence >= slot.held.confidence + classSwitchMargin
        if (strongEnough && slot.pendingLabel == candidate.label) {
            slot.pendingVotes++
        } else if (strongEnough) {
            slot.pendingLabel = candidate.label
            slot.pendingVotes = 1
        } else {
            slot.pendingLabel = null
            slot.pendingVotes = 0
        }

        if (slot.pendingVotes >= switchVotesNeeded) {
            slot.pendingLabel = null
            slot.pendingVotes = 0
            slot.held = candidate.copy(boundingBox = RectF(candidate.boundingBox))
            slot.confirmLabel = candidate.label
            slot.confirmVotes = 1
            // slot.confirmed no se reinicia: la posición ya era un equipo
            // confirmado, solo cambió de clase.
        } else {
            slot.held = slot.held.copy(confidence = slot.held.confidence * 0.9f)
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

    private fun calculateIoU(a: RectF, b: RectF): Float {
        val interLeft = max(a.left, b.left)
        val interTop = max(a.top, b.top)
        val interRight = min(a.right, b.right)
        val interBottom = min(a.bottom, b.bottom)
        val inter = max(0f, interRight - interLeft) * max(0f, interBottom - interTop)
        val union = (a.right - a.left) * (a.bottom - a.top) +
            (b.right - b.left) * (b.bottom - b.top) - inter
        return if (union > 0) inter / union else 0f
    }
}
