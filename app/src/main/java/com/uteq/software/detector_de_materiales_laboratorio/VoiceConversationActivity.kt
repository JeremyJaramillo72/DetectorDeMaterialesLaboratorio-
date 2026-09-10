package com.uteq.software.detector_de_materiales_laboratorio

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.View
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.uteq.software.detector_de_materiales_laboratorio.data.KnowledgeBaseRepository
import com.uteq.software.detector_de_materiales_laboratorio.databinding.ActivityVoiceConversationBinding
import com.uteq.software.detector_de_materiales_laboratorio.model.ChatMessage
import com.uteq.software.detector_de_materiales_laboratorio.network.RagApiClient
import com.uteq.software.detector_de_materiales_laboratorio.ui.MarkdownText
import com.uteq.software.detector_de_materiales_laboratorio.ui.VoiceOrbView
import com.uteq.software.detector_de_materiales_laboratorio.voice.ChatVoiceController
import kotlinx.coroutines.launch

/**
 * Conversación por voz continua — pantalla propia, no un modo del chat de
 * texto (ver ChatActivity). El ciclo escuchar → procesar → responder →
 * volver a escuchar es automático: el usuario no toca nada entre turnos.
 *
 * Reutiliza [RagApiClient] (misma lógica de respuesta que el chat de texto,
 * ahora con [historial][conversationHistory] para resolver referencias como
 * "¿y para qué sirve?") y [ChatVoiceController] (ya existía para hablar
 * respuestas; aquí además dispara el siguiente ciclo de escucha al terminar).
 *
 * Lo que SÍ es nuevo: [SpeechRecognizer] en vivo (no el diálogo del sistema
 * que usa el chat de texto para dictar) — es lo único que permite
 * reiniciarse solo tras cada turno sin que el usuario vuelva a tocar nada.
 */
class VoiceConversationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVoiceConversationBinding
    private lateinit var ragApiClient: RagApiClient
    private lateinit var kbRepository: KnowledgeBaseRepository
    private lateinit var ttsController: ChatVoiceController
    private var speechRecognizer: SpeechRecognizer? = null

    private var scopedToEquipment = false
    private var currentEquipmentId: String? = null
    private var currentEquipmentName: String? = null

    /** Historial completo (lo que llegó de ChatActivity + lo nuevo de esta sesión) — se le pasa al RAG como contexto. */
    private val conversationHistory = mutableListOf<ChatMessage>()

    /** Solo lo nuevo de ESTA sesión — se devuelve a ChatActivity al salir. */
    private val newTurns = mutableListOf<ChatMessage>()

    private var currentState = VoiceState.LISTENING
    private var isFinishingConversation = false
    private var hasStartedOnce = false

    private enum class VoiceState {
        LISTENING, PROCESSING, SPEAKING, PERMISSION_NEEDED, UNAVAILABLE, ERROR_NETWORK, ERROR_GENERIC
    }

    companion object {
        const val EXTRA_HISTORY = "extra_voice_history"
        const val EXTRA_NEW_TURNS = "extra_voice_new_turns"
    }

    private val micPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) initRecognizer() else setState(VoiceState.PERMISSION_NEEDED)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVoiceConversationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ragApiClient = RagApiClient(this)
        kbRepository = KnowledgeBaseRepository.getInstance(this)
        ttsController = ChatVoiceController(this)

        scopedToEquipment = intent.getBooleanExtra(ChatActivity.EXTRA_SCOPED_TO_EQUIPMENT, false)
        currentEquipmentId = intent.getStringExtra(ChatActivity.EXTRA_EQUIPMENT_ID)
        currentEquipmentName = intent.getStringExtra(ChatActivity.EXTRA_EQUIPMENT_NAME)

        @Suppress("DEPRECATION", "UNCHECKED_CAST")
        val incomingHistory = intent.getSerializableExtra(EXTRA_HISTORY) as? ArrayList<ChatMessage>
        if (incomingHistory != null) conversationHistory.addAll(incomingHistory)

        setupContextLabel()
        setupControls()

        ttsController.onSpeakingStateChanged = { speaking ->
            if (!speaking && currentState == VoiceState.SPEAKING && !isFinishingConversation) {
                startListening()
            }
        }

        onBackPressedDispatcher.addCallback(this) { finishConversation() }

        checkPermissionAndStart()
    }

    private fun setupContextLabel() {
        if (scopedToEquipment && !currentEquipmentName.isNullOrBlank()) {
            binding.layoutVoiceContext.visibility = View.VISIBLE
            binding.tvVoiceContextName.text = currentEquipmentName
        }
    }

    private fun setupControls() {
        binding.btnEndConversation.setOnClickListener { finishConversation() }
        binding.voiceOrb.setOnClickListener { onOrbTapped() }
        binding.btnVoiceAction.setOnClickListener {
            if (currentState == VoiceState.PERMISSION_NEEDED) {
                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    /**
     * Único punto de interacción manual: mientras responde, tocar el anillo
     * la interrumpe y pasa a escuchar de inmediato — el equivalente a
     * interrumpir a alguien para hablar. En error/permiso, reintenta.
     */
    private fun onOrbTapped() {
        when (currentState) {
            VoiceState.SPEAKING -> {
                ttsController.stop()
                startListening()
            }
            VoiceState.UNAVAILABLE, VoiceState.ERROR_NETWORK, VoiceState.ERROR_GENERIC -> initRecognizer()
            VoiceState.PERMISSION_NEEDED -> micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            VoiceState.LISTENING, VoiceState.PROCESSING -> Unit
        }
    }

    private fun checkPermissionAndStart() {
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (granted) {
            initRecognizer()
        } else {
            setState(VoiceState.PERMISSION_NEEDED)
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun initRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            setState(VoiceState.UNAVAILABLE)
            return
        }
        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(recognitionListener)
        }
        startListening()
    }

    /**
     * Nunca debe llamarse mientras [ttsController] está hablando — el
     * micrófono no debe estar abierto captando la propia voz del asistente.
     * El reinicio automático tras cada turno pasa siempre por aquí.
     */
    private fun startListening() {
        if (isFinishingConversation || ttsController.isSpeaking) return
        val recognizer = speechRecognizer ?: return

        binding.tvVoiceTranscript.text = ""
        val recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
        }
        setState(VoiceState.LISTENING)
        recognizer.startListening(recognizerIntent)
    }

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) {
            binding.voiceOrb.setAudioLevel(rmsdB)
        }
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit

        override fun onPartialResults(partialResults: Bundle?) {
            val partial = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
            if (!partial.isNullOrBlank()) binding.tvVoiceTranscript.text = partial
        }

        override fun onResults(results: Bundle?) {
            val text = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                ?.trim()
            if (!text.isNullOrEmpty()) {
                handleUserUtterance(text)
            } else {
                startListening()
            }
        }

        override fun onError(error: Int) = handleRecognizerError(error)
    }

    private fun handleRecognizerError(error: Int) {
        if (isFinishingConversation) return
        when (error) {
            // Silencio o habla no entendida: no es un fallo real de conversación,
            // simplemente se sigue escuchando — como cuando alguien no dice nada.
            SpeechRecognizer.ERROR_NO_MATCH,
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> startListening()

            SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
                binding.root.postDelayed({ if (!isFinishingConversation) startListening() }, 300)

            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> setState(VoiceState.PERMISSION_NEEDED)

            SpeechRecognizer.ERROR_NETWORK,
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> setState(VoiceState.ERROR_NETWORK)

            else -> setState(VoiceState.ERROR_GENERIC)
        }
    }

    private fun handleUserUtterance(text: String) {
        setState(VoiceState.PROCESSING)
        binding.tvVoiceTranscript.text = text

        val userMsg = ChatMessage(text = text, isBot = false)
        val historyForRequest = conversationHistory.toList()
        conversationHistory.add(userMsg)
        newTurns.add(userMsg)

        lifecycleScope.launch {
            val response = ragApiClient.sendMessage(
                userMessage = text,
                equipmentId = if (scopedToEquipment) currentEquipmentId else null,
                scopedToEquipment = scopedToEquipment,
                equipmentDisplayName = if (scopedToEquipment) currentEquipmentName else null,
                history = historyForRequest
            )
            if (isFinishingConversation) return@launch

            conversationHistory.add(response)
            newTurns.add(response)
            speakReply(response.text)
        }
    }

    private fun speakReply(text: String) {
        setState(VoiceState.SPEAKING)
        binding.tvVoiceTranscript.text = ""
        ttsController.speak(MarkdownText.stripForSpeech(text))
    }

    private fun setState(state: VoiceState) {
        currentState = state
        binding.btnVoiceAction.visibility = View.GONE
        binding.tvVoiceTranscript.visibility = View.VISIBLE

        when (state) {
            VoiceState.LISTENING -> {
                binding.voiceOrb.setMode(VoiceOrbView.Mode.LISTENING)
                binding.tvVoiceState.text = getString(R.string.voice_state_listening)
            }
            VoiceState.PROCESSING -> {
                binding.voiceOrb.setMode(VoiceOrbView.Mode.PROCESSING)
                binding.tvVoiceState.text = getString(R.string.voice_state_processing)
            }
            VoiceState.SPEAKING -> {
                binding.voiceOrb.setMode(VoiceOrbView.Mode.SPEAKING)
                binding.tvVoiceState.text = getString(R.string.voice_state_speaking_hint)
                binding.tvVoiceTranscript.visibility = View.GONE
            }
            VoiceState.PERMISSION_NEEDED -> {
                binding.voiceOrb.setMode(VoiceOrbView.Mode.IDLE)
                binding.tvVoiceState.text = getString(R.string.voice_state_permission_needed)
                binding.tvVoiceTranscript.visibility = View.GONE
                binding.btnVoiceAction.text = getString(R.string.voice_grant_permission)
                binding.btnVoiceAction.visibility = View.VISIBLE
            }
            VoiceState.UNAVAILABLE -> {
                binding.voiceOrb.setMode(VoiceOrbView.Mode.IDLE)
                binding.tvVoiceState.text = getString(R.string.voice_state_unavailable)
                binding.tvVoiceTranscript.visibility = View.GONE
            }
            VoiceState.ERROR_NETWORK -> {
                binding.voiceOrb.setMode(VoiceOrbView.Mode.IDLE)
                binding.tvVoiceState.text = getString(R.string.voice_state_error_network)
                binding.tvVoiceTranscript.visibility = View.GONE
            }
            VoiceState.ERROR_GENERIC -> {
                binding.voiceOrb.setMode(VoiceOrbView.Mode.IDLE)
                binding.tvVoiceState.text = getString(R.string.voice_state_error_generic)
                binding.tvVoiceTranscript.visibility = View.GONE
            }
        }
    }

    private fun finishConversation() {
        if (isFinishingConversation) return
        isFinishingConversation = true
        speechRecognizer?.stopListening()
        speechRecognizer?.cancel()
        ttsController.stop()

        setResult(RESULT_OK, Intent().putExtra(EXTRA_NEW_TURNS, ArrayList(newTurns)))
        finish()
    }

    override fun onStart() {
        super.onStart()
        // No reinicia en el primerísimo onStart (ya arrancó desde onCreate);
        // sí lo hace al volver de segundo plano, para que la conversación
        // siga donde quedó en vez de dejar el micrófono apagado en silencio.
        if (hasStartedOnce && !isFinishingConversation &&
            currentState != VoiceState.PERMISSION_NEEDED &&
            currentState != VoiceState.UNAVAILABLE
        ) {
            startListening()
        }
        hasStartedOnce = true
    }

    override fun onStop() {
        super.onStop()
        // Nunca debe seguir escuchando ni hablando en segundo plano.
        speechRecognizer?.stopListening()
        ttsController.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer?.destroy()
        ttsController.shutdown()
    }
}
