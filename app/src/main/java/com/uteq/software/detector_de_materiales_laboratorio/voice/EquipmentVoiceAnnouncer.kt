package com.uteq.software.detector_de_materiales_laboratorio.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import com.uteq.software.detector_de_materiales_laboratorio.model.DetectionResult
import com.uteq.software.detector_de_materiales_laboratorio.model.EquipmentData
import java.util.Locale

/**
 * Anuncia por voz cada equipo detectado, una sola vez por aparición.
 *
 * Usa la cola nativa de [TextToSpeech] (QUEUE_ADD): si aparecen varios
 * equipos a la vez, Android reproduce sus anuncios uno tras otro sin
 * solaparse — no hace falta una cola propia. Esta clase solo decide QUÉ
 * encolar y CUÁNDO (una vez por equipo mientras siga en escena); el
 * debounce de detecciones inestables ya lo resuelve [DetectionTracker]
 * antes de que la lista llegue aquí, así que no se repite esa lógica.
 *
 * Si un equipo desaparece de escena y vuelve a aparecer más tarde, se
 * anuncia de nuevo — es señal de que el usuario movió la cámara a propósito.
 *
 * Falla en silencio si el motor TTS no está disponible en el dispositivo:
 * el resto de la app (detección, selección, ficha técnica) sigue funcionando
 * exactamente igual, sin voz.
 */
class EquipmentVoiceAnnouncer(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private var tts: TextToSpeech? = null
    private var isReady = false
    private val announcedLabels = HashSet<String>()

    var isMuted: Boolean = prefs.getBoolean(KEY_MUTED, false)
        private set

    init {
        tts = TextToSpeech(appContext) { status ->
            isReady = status == TextToSpeech.SUCCESS
            if (isReady) {
                tts?.language = Locale.forLanguageTag("es-ES")
            }
        }
    }

    fun setMuted(muted: Boolean) {
        isMuted = muted
        prefs.edit().putBoolean(KEY_MUTED, muted).apply()
        if (muted) tts?.stop()
    }

    /**
     * Anuncia cada equipo nuevo de [detections]; ignora los que ya se
     * anunciaron mientras sigan en escena. Debe recibir la lista ESTABLE
     * (post [DetectionTracker]), no las detecciones crudas del modelo.
     */
    fun onDetections(detections: List<DetectionResult>, equipmentOf: (String) -> EquipmentData?) {
        val currentLabels = detections.mapTo(HashSet()) { it.label }

        if (!isReady || isMuted) {
            // No se habla nada, pero se sincroniza el set para no volcar de
            // golpe todos los anuncios pendientes al desmutear.
            announcedLabels.clear()
            announcedLabels.addAll(currentLabels)
            return
        }

        detections.forEach { detection ->
            if (announcedLabels.add(detection.label)) {
                speak(detection, equipmentOf(detection.label))
            }
        }
        announcedLabels.retainAll(currentLabels)
    }

    private fun speak(detection: DetectionResult, equipment: EquipmentData?) {
        val description = equipment?.funcionPrincipal?.let { firstSentence(it) }
        val text = if (!description.isNullOrBlank()) {
            "Detectado: ${detection.displayName}. $description"
        } else {
            "Detectado: ${detection.displayName}."
        }
        tts?.speak(text, TextToSpeech.QUEUE_ADD, null, detection.label)
    }

    private fun firstSentence(text: String, maxLen: Int = 160): String {
        val cut = text.indexOf(". ")
        val sentence = if (cut in 1 until maxLen) text.substring(0, cut + 1) else text
        return if (sentence.length > maxLen) sentence.take(maxLen).trimEnd() + "…" else sentence
    }

    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }

    companion object {
        private const val PREFS_NAME = "voice_announcer_prefs"
        private const val KEY_MUTED = "muted"
    }
}
