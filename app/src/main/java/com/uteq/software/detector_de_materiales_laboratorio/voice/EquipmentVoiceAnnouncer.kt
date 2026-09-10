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
        if (muted) stop()
    }

    /**
     * Anuncia las características del equipo únicamente cuando el usuario hace clic en la selección.
     * Cancela de inmediato cualquier locución previa (QUEUE_FLUSH).
     */
    fun announceSelectedEquipment(displayName: String, equipment: EquipmentData?) {
        if (!isReady || isMuted) return
        stop()
        val description = equipment?.funcionPrincipal?.let { firstSentence(it) }?.let {
            com.uteq.software.detector_de_materiales_laboratorio.ui.MarkdownText.stripForSpeech(it)
        }
        val price = equipment?.precioAproximado
        val priceSpeech = if (!price.isNullOrBlank()) {
            com.uteq.software.detector_de_materiales_laboratorio.ui.MarkdownText.formatPriceForSpeech(price)
        } else {
            null
        }

        val text = buildString {
            append(displayName)
            if (!description.isNullOrBlank()) {
                append(". $description")
            }
            if (!priceSpeech.isNullOrBlank()) {
                append(". $priceSpeech")
            }
        }
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, displayName)
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
