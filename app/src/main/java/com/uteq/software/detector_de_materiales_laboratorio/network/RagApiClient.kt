package com.uteq.software.detector_de_materiales_laboratorio.network

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.uteq.software.detector_de_materiales_laboratorio.BuildConfig
import com.uteq.software.detector_de_materiales_laboratorio.data.KnowledgeBaseRepository
import com.uteq.software.detector_de_materiales_laboratorio.model.ChatMessage
import com.uteq.software.detector_de_materiales_laboratorio.model.EquipmentData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class RagApiClient(private val context: Context) {

    private val TAG = "RagApiClient"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val geminiApiKey = BuildConfig.GEMINI_API_KEY
    private val geminiModel = "gemini-2.5-flash"
    private val geminiApiUrl: String
        get() = "https://generativelanguage.googleapis.com/v1beta/models/$geminiModel:generateContent?key=$geminiApiKey"

    var serverBaseUrl: String = "http://10.0.2.2:8000"

    suspend fun sendMessage(
        userMessage: String,
        equipmentId: String?,
        scopedToEquipment: Boolean = !equipmentId.isNullOrBlank(),
        equipmentDisplayName: String? = null
    ): ChatMessage = withContext(Dispatchers.IO) {
        val kbRepo = KnowledgeBaseRepository.getInstance(context)
        val eq = if (scopedToEquipment && !equipmentId.isNullOrEmpty()) {
            kbRepo.getEquipmentById(equipmentId) ?: kbRepo.getEquipmentByClass(equipmentId)
        } else {
            null
        }

        // Preguntas fuera de tema en modo equipo: rechazar antes de llamar a Gemini
        if (scopedToEquipment) {
            val eqName = eq?.nombreComun ?: equipmentDisplayName ?: "el equipo detectado"
            if (kbRepo.isOffTopicEquipmentQuery(userMessage)) {
                return@withContext ChatMessage(
                    text = "Solo puedo responder preguntas sobre el **$eqName**.\n\n" +
                        "Pregúntame por prevención, EPP, procedimiento, riesgos, función o prácticas UTEQ de este equipo.",
                    isBot = true,
                    equipmentId = eq?.id ?: equipmentId
                )
            }
        }

        if (geminiApiKey.isNotBlank()) {
            try {
                val geminiResponse = callGeminiDirectly(
                    userMessage = userMessage,
                    eq = eq,
                    scopedToEquipment = scopedToEquipment,
                    equipmentDisplayName = equipmentDisplayName ?: eq?.nombreComun
                )
                if (!geminiResponse.isNullOrBlank()) {
                    return@withContext ChatMessage(
                        text = geminiResponse,
                        isBot = true,
                        equipmentId = if (scopedToEquipment) eq?.id ?: equipmentId else null,
                        citations = eq?.fuentesReferencias ?: emptyList(),
                        eppRequired = eq?.eppRequerido ?: emptyList(),
                        risks = eq?.riesgosAsociados ?: emptyList()
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Fallo Gemini: ${e.message}. Probando servidor local...")
            }
        }

        try {
            val payload = mapOf(
                "message" to userMessage,
                "equipment_id" to if (scopedToEquipment) equipmentId else null,
                "scoped" to scopedToEquipment
            )
            val requestBody = gson.toJson(payload).toRequestBody(jsonMediaType)
            val request = Request.Builder()
                .url("$serverBaseUrl/api/chat")
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyString = response.body?.string().orEmpty()
                    val apiResponse = gson.fromJson(bodyString, RagChatApiResponse::class.java)
                    return@withContext ChatMessage(
                        text = apiResponse.response,
                        isBot = true,
                        equipmentId = if (scopedToEquipment) {
                            apiResponse.equipmentId ?: equipmentId
                        } else {
                            null
                        },
                        citations = apiResponse.citations ?: emptyList(),
                        eppRequired = apiResponse.eppRequired ?: emptyList(),
                        risks = apiResponse.risks ?: emptyList()
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Backend local no disponible: ${e.message}")
        }

        return@withContext if (scopedToEquipment) {
            kbRepo.generateOfflineRagResponse(userMessage, equipmentId)
        } else {
            kbRepo.generateOfflineGeneralResponse(userMessage)
        }
    }

    private fun callGeminiDirectly(
        userMessage: String,
        eq: EquipmentData?,
        scopedToEquipment: Boolean,
        equipmentDisplayName: String?
    ): String? {
        val systemPrompt = if (scopedToEquipment) {
            buildScopedPrompt(userMessage, eq, equipmentDisplayName)
        } else {
            buildGeneralPrompt(userMessage)
        }

        val jsonPayload = JsonObject().apply {
            val contentsArray = JsonArray()
            val contentObj = JsonObject()
            val partsArray = JsonArray()
            val partObj = JsonObject()
            partObj.addProperty("text", systemPrompt)
            partsArray.add(partObj)
            contentObj.add("parts", partsArray)
            contentsArray.add(contentObj)
            add("contents", contentsArray)
        }

        val body = jsonPayload.toString().toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url(geminiApiUrl)
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                val jsonString = response.body?.string() ?: return null
                val rootJson = gson.fromJson(jsonString, JsonObject::class.java)
                val candidates = rootJson.getAsJsonArray("candidates")
                if (candidates != null && candidates.size() > 0) {
                    val content = candidates[0].asJsonObject.getAsJsonObject("content")
                    val parts = content?.getAsJsonArray("parts")
                    if (parts != null && parts.size() > 0) {
                        return parts[0].asJsonObject.get("text")?.asString
                    }
                }
            } else {
                Log.e(TAG, "Gemini API Error: HTTP ${response.code}")
            }
        }
        return null
    }

    private fun buildScopedPrompt(
        userMessage: String,
        eq: EquipmentData?,
        equipmentDisplayName: String?
    ): String {
        val eqName = eq?.nombreComun ?: equipmentDisplayName ?: "el equipo detectado"
        val contextInfo = if (eq != null) {
            """
            CONTEXTO INTERNO (usa solo lo necesario para responder; NO lo copies completo):
            Equipo: ${eq.nombreOficial} (${eq.nombreComun})
            Fabricante/Modelo: ${eq.fabricante} - ${eq.modelo}
            Función: ${eq.funcionPrincipal}
            Principio: ${eq.principioFuncionamiento}
            Componentes: ${eq.componentesPrincipales.joinToString(", ")}
            Procedimiento: ${eq.procedimientoOperativoEstandar.joinToString(" | ")}
            EPP: ${eq.eppRequerido.joinToString(", ")}
            Riesgos/prevención: ${eq.riesgosAsociados.joinToString(", ")}
            Normas de seguridad: ${eq.normasSeguridad.joinToString(", ")}
            Prácticas UTEQ: ${eq.guiasPracticaUteq.joinToString("; ")}
            Fuentes: ${eq.fuentesReferencias.joinToString("; ")}
            """.trimIndent()
        } else {
            "Equipo objetivo: $eqName."
        }

        return """
            Eres un asistente de chat del Laboratorio de Bromatología UTEQ.
            Habla como una IA conversacional (tipo WhatsApp/ChatGPT): natural, breve y útil.

            Estás ayudando SOLO con: "$eqName".

            $contextInfo

            Pregunta del estudiante:
            "$userMessage"

            REGLAS DE RESPUESTA (obligatorias):
            1. Contesta DIRECTAMENTE la pregunta. Nada más.
            2. NO lances la ficha técnica completa.
            3. NO listes fabricante, función, principio, prácticas y fuentes si no te lo pidieron.
            4. Si preguntan prevención/seguridad/EPP/riesgos: responde con medidas concretas en viñetas cortas.
            5. Si preguntan procedimiento: solo los pasos.
            6. Si preguntan qué es/para qué sirve: 2-4 oraciones máximo.
            7. Tono cercano y claro para estudiantes. Español neutro.
            8. Si la pregunta NO es sobre este equipo (mates, chistes, clima, cultura general, otro aparato, etc.),
               responde EXACTAMENTE en este estilo:
               "Solo puedo responder preguntas sobre el $eqName. Pregúntame por prevención, EPP, procedimiento, riesgos, función o prácticas UTEQ de este equipo."
               No resuelvas la pregunta fuera de tema.
            9. Máximo ~120-180 palabras, salvo que pidan el procedimiento completo.
        """.trimIndent()
    }

    private fun buildGeneralPrompt(userMessage: String): String {
        return """
            Eres un asistente de chat del Laboratorio de Bromatología UTEQ.
            Habla como una IA normal: conversacional, breve y directa.

            Pregunta:
            "$userMessage"

            REGLAS:
            1. Responde solo lo preguntado (bioseguridad general, EPP, prácticas, orientación).
            2. No sueltes un manual completo.
            3. Si piden un equipo concreto, indícales detectarlo en cámara y abrir "Consultar sobre este equipo".
            4. Máximo ~120-180 palabras. Español claro.
        """.trimIndent()
    }

    private data class RagChatApiResponse(
        val response: String,
        val equipmentId: String?,
        val citations: List<String>?,
        val eppRequired: List<String>?,
        val risks: List<String>?
    )
}
