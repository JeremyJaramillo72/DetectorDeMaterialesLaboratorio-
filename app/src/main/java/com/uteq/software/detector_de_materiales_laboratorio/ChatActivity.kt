package com.uteq.software.detector_de_materiales_laboratorio

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.uteq.software.detector_de_materiales_laboratorio.data.KnowledgeBaseRepository
import com.uteq.software.detector_de_materiales_laboratorio.databinding.ActivityChatBinding
import com.uteq.software.detector_de_materiales_laboratorio.model.ChatMessage
import com.uteq.software.detector_de_materiales_laboratorio.network.RagApiClient
import com.uteq.software.detector_de_materiales_laboratorio.ui.ChatAdapter
import kotlinx.coroutines.launch

class ChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatBinding
    private val chatAdapter = ChatAdapter()
    private lateinit var ragApiClient: RagApiClient
    private lateinit var kbRepository: KnowledgeBaseRepository

    private var currentEquipmentId: String? = null
    private var currentEquipmentName: String? = null
    private var scopedToEquipment: Boolean = false
    private var baseInputBottomPadding = 0
    private var baseHeaderTopPadding = 0

    companion object {
        const val EXTRA_EQUIPMENT_ID = "extra_equipment_id"
        const val EXTRA_EQUIPMENT_CLASS = "extra_equipment_class"
        const val EXTRA_EQUIPMENT_NAME = "extra_equipment_name"
        const val EXTRA_SCOPED_TO_EQUIPMENT = "extra_scoped_to_equipment"
    }

    /**
     * "Hablar con el asistente" abre una pantalla completamente aparte
     * (VoiceConversationActivity) — no es un modo dentro de este chat. Se le
     * pasa el historial actual como contexto inicial, y al volver se anexan
     * los turnos nuevos que se hablaron allá, para que sea UNA sola
     * conversación aunque haya cambiado de modalidad a mitad de camino.
     */
    private val voiceConversationLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            @Suppress("DEPRECATION", "UNCHECKED_CAST")
            val newTurns = result.data?.getSerializableExtra(VoiceConversationActivity.EXTRA_NEW_TURNS)
                as? ArrayList<ChatMessage>
            newTurns?.forEach { chatAdapter.addMessage(it) }
            if (!newTurns.isNullOrEmpty()) {
                binding.rvChatMessages.smoothScrollToPosition(chatAdapter.itemCount - 1)
            }
        }

    private val voicePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                startVoiceDictation()
            } else {
                Toast.makeText(
                    this,
                    "Se necesita permiso de micrófono para dictar el mensaje.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    private val voiceInputLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != RESULT_OK) return@registerForActivityResult
            val spoken = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                ?.trim()
                .orEmpty()

            if (spoken.isEmpty()) {
                Toast.makeText(this, "No se pudo transcribir. Intenta de nuevo.", Toast.LENGTH_SHORT)
                    .show()
                return@registerForActivityResult
            }

            // Solo llena el cuadro de texto; el usuario envía cuando quiera.
            // Este micrófono es para dictar en el chat tradicional — la
            // conversación hablada continua vive en VoiceConversationActivity.
            val current = binding.etChatMessage.text?.toString().orEmpty().trim()
            val merged = if (current.isEmpty()) spoken else "$current $spoken"
            binding.etChatMessage.setText(merged)
            binding.etChatMessage.setSelection(merged.length)
            Toast.makeText(this, "Texto dictado listo. Revisa y envía.", Toast.LENGTH_SHORT).show()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        baseInputBottomPadding = binding.layoutInputDock.paddingBottom
        baseHeaderTopPadding = binding.layoutChatHeader.paddingTop
        setupKeyboardInsets()

        ragApiClient = RagApiClient(this)
        kbRepository = KnowledgeBaseRepository.getInstance(this)

        scopedToEquipment = intent.getBooleanExtra(EXTRA_SCOPED_TO_EQUIPMENT, false)
        currentEquipmentId = intent.getStringExtra(EXTRA_EQUIPMENT_ID)
            ?: intent.getStringExtra(EXTRA_EQUIPMENT_CLASS)
        currentEquipmentName = intent.getStringExtra(EXTRA_EQUIPMENT_NAME)

        if (!currentEquipmentId.isNullOrBlank() || !currentEquipmentName.isNullOrBlank()) {
            scopedToEquipment = true
            resolveEquipmentIdentity()
        } else {
            scopedToEquipment = false
            currentEquipmentId = null
            currentEquipmentName = null
        }

        setupToolbar()
        setupRecyclerView()
        setupChips()
        setupListeners()
        sendInitialWelcomeMessage()
    }

    /**
     * Entrada a la conversación por voz: una pantalla completamente aparte,
     * no un modo dentro de este chat (ver [voiceConversationLauncher]).
     */
    private fun openVoiceConversation() {
        val intent = Intent(this, VoiceConversationActivity::class.java).apply {
            putExtra(EXTRA_SCOPED_TO_EQUIPMENT, scopedToEquipment)
            putExtra(EXTRA_EQUIPMENT_ID, currentEquipmentId)
            putExtra(EXTRA_EQUIPMENT_NAME, currentEquipmentName)
            putExtra(VoiceConversationActivity.EXTRA_HISTORY, ArrayList(chatAdapter.getMessages()))
        }
        voiceConversationLauncher.launch(intent)
    }

    private fun resolveEquipmentIdentity() {
        val eq = currentEquipmentId?.let {
            kbRepository.getEquipmentById(it) ?: kbRepository.getEquipmentByClass(it)
        } ?: currentEquipmentName?.let { name ->
            kbRepository.getAllEquipments().firstOrNull {
                it.nombreComun.equals(name, ignoreCase = true) ||
                    it.nombreOficial.equals(name, ignoreCase = true)
            }
        }

        if (eq != null) {
            currentEquipmentId = eq.id
            currentEquipmentName = eq.nombreComun
        }
    }

    private fun setupKeyboardInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())

            binding.layoutChatHeader.updatePadding(top = baseHeaderTopPadding + systemBars.top)
            binding.layoutInputDock.updatePadding(
                bottom = baseInputBottomPadding + maxOf(ime.bottom, systemBars.bottom)
            )

            if (ime.bottom > 0 && chatAdapter.itemCount > 0) {
                binding.rvChatMessages.post {
                    binding.rvChatMessages.scrollToPosition(chatAdapter.itemCount - 1)
                }
            }
            insets
        }
        ViewCompat.requestApplyInsets(binding.root)
    }

    private fun setupToolbar() {
        binding.btnBack.setOnClickListener { finish() }
        binding.btnTalkToAssistant.setOnClickListener { openVoiceConversation() }

        if (scopedToEquipment && !currentEquipmentName.isNullOrEmpty()) {
            binding.tvActiveEquipment.text = currentEquipmentName
            binding.etChatMessage.hint = getString(R.string.chat_hint_scoped)
        } else {
            binding.tvActiveEquipment.text = getString(R.string.context_general)
            binding.etChatMessage.hint = getString(R.string.chat_hint_general)
        }
    }

    private fun setupRecyclerView() {
        binding.rvChatMessages.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }
        binding.rvChatMessages.adapter = chatAdapter
    }

    private fun setupChips() {
        if (scopedToEquipment) {
            binding.chipPPE.setOnClickListener {
                sendMessage("¿Qué Elementos de Protección Personal (EPP) necesito para operar este equipo?")
            }
            binding.chipProcedure.setOnClickListener {
                sendMessage("¿Cuál es el procedimiento operativo estándar paso a paso de este equipo?")
            }
            binding.chipPractices.setOnClickListener {
                sendMessage("¿Qué prácticas académicas de la UTEQ utilizan específicamente este equipo?")
            }
            binding.chipRisks.setOnClickListener {
                sendMessage("¿Cuáles son los riesgos asociados y normas de bioseguridad de este equipo?")
            }
        } else {
            binding.chipPPE.text = "EPP general de laboratorio"
            binding.chipProcedure.text = "Normas de bioseguridad"
            binding.chipPractices.text = "Prácticas UTEQ"
            binding.chipRisks.text = "Consultas generales"

            binding.chipPPE.setOnClickListener {
                sendMessage("¿Cuáles son los EPP generales obligatorios en el Laboratorio de Bromatología UTEQ?")
            }
            binding.chipProcedure.setOnClickListener {
                sendMessage("¿Cuáles son las normas generales de bioseguridad del laboratorio de Bromatología?")
            }
            binding.chipPractices.setOnClickListener {
                sendMessage("¿Qué prácticas académicas se realizan en el Laboratorio de Bromatología UTEQ?")
            }
            binding.chipRisks.setOnClickListener {
                sendMessage("Dame una orientación general sobre seguridad y buenas prácticas en el laboratorio.")
            }
        }
    }

    private fun setupListeners() {
        binding.btnSendMessage.setOnClickListener {
            val text = binding.etChatMessage.text.toString().trim()
            if (text.isNotEmpty()) {
                sendMessage(text)
                binding.etChatMessage.text.clear()
            }
        }

        binding.btnVoiceInput.setOnClickListener {
            requestMicAndDictate()
        }

        binding.etChatMessage.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                val text = binding.etChatMessage.text.toString().trim()
                if (text.isNotEmpty()) {
                    sendMessage(text)
                    binding.etChatMessage.text.clear()
                }
                true
            } else {
                false
            }
        }

        binding.etChatMessage.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus && chatAdapter.itemCount > 0) {
                binding.rvChatMessages.postDelayed({
                    binding.rvChatMessages.smoothScrollToPosition(chatAdapter.itemCount - 1)
                }, 200)
            }
        }
    }

    private fun requestMicAndDictate() {
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (granted) {
            startVoiceDictation()
        } else {
            voicePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startVoiceDictation() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Habla tu pregunta…")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        try {
            voiceInputLauncher.launch(intent)
        } catch (_: Exception) {
            Toast.makeText(
                this,
                "Este dispositivo no tiene reconocimiento de voz disponible.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun sendInitialWelcomeMessage() {
        val welcomeText = if (scopedToEquipment) {
            val eqName = currentEquipmentName ?: "este equipo"
            "Listo. Estoy enfocado en el **$eqName**, pero también puedo buscar en internet información de reactivos, normas o temas complementarios.\n\n" +
                "Pregunta directo o usa el botón de voz."
        } else {
            "¡Hola! Soy tu asistente del Laboratorio de Bromatología UTEQ.\n\n" +
                "Conozco los equipos y protocolos de la sede, y puedo buscar rápidamente en internet cualquier duda científica, reactivo o procedimiento que necesites."
        }
        chatAdapter.addMessage(
            ChatMessage(
                text = welcomeText,
                isBot = true,
                equipmentId = if (scopedToEquipment) currentEquipmentId else null
            )
        )
    }

    private fun sendMessage(text: String) {
        val historySnapshot = chatAdapter.getMessages()
        chatAdapter.addMessage(ChatMessage(text = text, isBot = false))
        binding.rvChatMessages.smoothScrollToPosition(chatAdapter.itemCount - 1)
        binding.tvProcessing.visibility = View.VISIBLE

        lifecycleScope.launch {
            val equipmentIdForRequest = if (scopedToEquipment) currentEquipmentId else null
            val responseMsg = ragApiClient.sendMessage(
                userMessage = text,
                equipmentId = equipmentIdForRequest,
                scopedToEquipment = scopedToEquipment,
                equipmentDisplayName = if (scopedToEquipment) currentEquipmentName else null,
                history = historySnapshot
            )
            binding.tvProcessing.visibility = View.GONE
            chatAdapter.addMessage(responseMsg)
            binding.rvChatMessages.smoothScrollToPosition(chatAdapter.itemCount - 1)
        }
    }
}
