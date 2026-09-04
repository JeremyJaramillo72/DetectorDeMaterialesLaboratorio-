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

            // Solo llena el cuadro de texto; el usuario envía cuando quiera
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

        if (scopedToEquipment && !currentEquipmentName.isNullOrEmpty()) {
            binding.tvActiveEquipment.text = "Equipo activo: $currentEquipmentName"
            binding.layoutSelectedChip.visibility = View.VISIBLE
            binding.etChatMessage.hint = "Escribe o dicta sobre este equipo..."
        } else {
            binding.tvActiveEquipment.text = "Modo general • Laboratorio de Bromatología UTEQ"
            binding.layoutSelectedChip.visibility = View.VISIBLE
            binding.etChatMessage.hint = "Escribe o dicta tu consulta..."
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
            "Listo. Solo respondo sobre **$eqName**.\n\n" +
                "Pregunta directo (o usa el micrófono): función, EPP, riesgos, procedimiento."
        } else {
            "Hola. Soy el asistente general del lab UTEQ.\n\n" +
                "Conozco todos los equipos registrados. Pregunta por nombre (ej. microscopio trinocular) " +
                "o usa el micrófono. Si el equipo no está en el sistema, te lo diré."
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
        chatAdapter.addMessage(ChatMessage(text = text, isBot = false))
        binding.rvChatMessages.smoothScrollToPosition(chatAdapter.itemCount - 1)

        lifecycleScope.launch {
            val equipmentIdForRequest = if (scopedToEquipment) currentEquipmentId else null
            val responseMsg = ragApiClient.sendMessage(
                userMessage = text,
                equipmentId = equipmentIdForRequest,
                scopedToEquipment = scopedToEquipment,
                equipmentDisplayName = if (scopedToEquipment) currentEquipmentName else null
            )
            chatAdapter.addMessage(responseMsg)
            binding.rvChatMessages.smoothScrollToPosition(chatAdapter.itemCount - 1)
        }
    }
}
