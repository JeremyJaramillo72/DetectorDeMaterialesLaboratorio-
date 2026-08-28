package com.uteq.software.detector_de_materiales_laboratorio

import android.os.Bundle
import android.view.inputmethod.EditorInfo
import androidx.appcompat.app.AppCompatActivity
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

    companion object {
        const val EXTRA_EQUIPMENT_ID = "extra_equipment_id"
        const val EXTRA_EQUIPMENT_NAME = "extra_equipment_name"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ragApiClient = RagApiClient(this)
        kbRepository = KnowledgeBaseRepository.getInstance(this)

        currentEquipmentId = intent.getStringExtra(EXTRA_EQUIPMENT_ID)
        currentEquipmentName = intent.getStringExtra(EXTRA_EQUIPMENT_NAME)

        setupToolbar()
        setupRecyclerView()
        setupChips()
        setupListeners()

        // Mensaje inicial de bienvenida
        sendInitialWelcomeMessage()
    }

    private fun setupToolbar() {
        binding.toolbarChat.setNavigationOnClickListener {
            finish()
        }

        if (!currentEquipmentName.isNullOrEmpty()) {
            binding.tvActiveEquipment.text = "🔬 Equipo Activo: $currentEquipmentName"
        } else {
            binding.tvActiveEquipment.text = "🔬 Laboratorio de Bromatología UTEQ (General)"
        }
    }

    private fun setupRecyclerView() {
        val layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }
        binding.rvChatMessages.layoutManager = layoutManager
        binding.rvChatMessages.adapter = chatAdapter
    }

    private fun setupChips() {
        binding.chipPPE.setOnClickListener {
            sendMessage("¿Qué Elementos de Protección Personal (EPP) necesito para operar este equipo?")
        }
        binding.chipProcedure.setOnClickListener {
            sendMessage("¿Cuál es el procedimiento operativo estándar paso a paso?")
        }
        binding.chipPractices.setOnClickListener {
            sendMessage("¿Qué prácticas académicas de la UTEQ utilizan este equipo?")
        }
        binding.chipRisks.setOnClickListener {
            sendMessage("¿Cuáles son los riesgos asociados y normas de bioseguridad?")
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
    }

    private fun sendInitialWelcomeMessage() {
        val eqName = currentEquipmentName ?: "los equipos del Laboratorio de Bromatología"
        val welcomeText = "👋 ¡Hola! Soy el **Asistente Inteligente de Bromatología UTEQ**.\n\nEstoy conectado con la base de manuales, normas de seguridad y guías oficiales de práctica sobre **$eqName**.\n\n¿En qué te puedo ayudar hoy? Puedes seleccionar una de las opciones rápidas arriba o escribir tu consulta."
        chatAdapter.addMessage(ChatMessage(text = welcomeText, isBot = true, equipmentId = currentEquipmentId))
    }

    private fun sendMessage(text: String) {
        // Añadir mensaje del usuario
        val userMsg = ChatMessage(text = text, isBot = false)
        chatAdapter.addMessage(userMsg)
        binding.rvChatMessages.smoothScrollToPosition(chatAdapter.itemCount - 1)

        // Consultar al backend RAG
        lifecycleScope.launch {
            val responseMsg = ragApiClient.sendMessage(text, currentEquipmentId)
            chatAdapter.addMessage(responseMsg)
            binding.rvChatMessages.smoothScrollToPosition(chatAdapter.itemCount - 1)
        }
    }
}
