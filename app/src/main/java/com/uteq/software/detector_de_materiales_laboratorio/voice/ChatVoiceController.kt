package com.uteq.software.detector_de_materiales_laboratorio.voice

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/**
 * Controla la voz del asistente en el chat: lee en voz alta las respuestas
 * en modo voz, y expone si está hablando para que la UI refleje el estado.
 *
 * A diferencia de [EquipmentVoiceAnnouncer] (que encola con QUEUE_ADD porque
 * varios equipos pueden coexistir), aquí se usa QUEUE_FLUSH: en una
 * conversación, una pregunta nueva del usuario tiene prioridad sobre
 * terminar de leer la respuesta anterior — no tiene sentido "encolar" turnos
 * de diálogo como si fueran anuncios independientes.
 *
 * Falla en silencio si el motor TTS no está disponible: el chat de texto
 * sigue funcionando exactamente igual, sin voz de salida.
 */
class ChatVoiceController(context: Context) {

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())

    private var tts: TextToSpeech? = null
    private var isReady = false

    var isSpeaking = false
        private set

    /** true si el motor TTS pudo inicializarse en este dispositivo. */
    val isAvailable: Boolean get() = isReady

    var onSpeakingStateChanged: ((Boolean) -> Unit)? = null

    init {
        tts = TextToSpeech(appContext) { status ->
            isReady = status == TextToSpeech.SUCCESS
            val engine = tts
            if (isReady && engine != null) {
                engine.language = Locale.forLanguageTag("es-ES")
                engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) = setSpeaking(true)
                    override fun onDone(utteranceId: String?) = setSpeaking(false)
                    @Deprecated("Deprecated in Java", ReplaceWith(""))
                    override fun onError(utteranceId: String?) = setSpeaking(false)
                })
            }
        }
    }

    private fun setSpeaking(speaking: Boolean) {
        mainHandler.post {
            isSpeaking = speaking
            onSpeakingStateChanged?.invoke(speaking)
        }
    }

    fun speak(text: String) {
        if (!isReady || text.isBlank()) return
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "chat_reply")
    }

    fun stop() {
        tts?.stop()
        setSpeaking(false)
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
