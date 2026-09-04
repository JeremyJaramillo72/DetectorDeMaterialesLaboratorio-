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
        val scopedEq = if (scopedToEquipment && !equipmentId.isNullOrEmpty()) {
            kbRepo.getEquipmentById(equipmentId) ?: kbRepo.getEquipmentByClass(equipmentId)
        } else {
            null
        }

        // Modo general: resolver equipo por nombre (existe / no registrado)
        var generalMatchedEq: EquipmentData? = null
        if (!scopedToEquipment) {
            when (val resolved = kbRepo.resolveEquipmentFromQuery(userMessage)) {
                is KnowledgeBaseRepository.EquipmentQueryResult.Found -> {
                    generalMatchedEq = resolved.equipment
                }
                is KnowledgeBaseRepository.EquipmentQueryResult.NotRegistered -> {
                    return@withContext ChatMessage(
                        text = "El equipo **\"${resolved.askedName}\"** no está registrado en el sistema del Laboratorio de Bromatología UTEQ.\n\n" +
                            "Solo puedo dar información de equipos que existan en el catálogo. " +
                            "Prueba con un nombre registrado o detectalo con la cámara.",
                        isBot = true
                    )
                }
                KnowledgeBaseRepository.EquipmentQueryResult.GeneralTopic -> Unit
            }
        }

        val eq = scopedEq ?: generalMatchedEq

        // Preguntas fuera de tema en modo equipo específico (ficha)
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
                    equipmentDisplayName = equipmentDisplayName ?: eq?.nombreComun,
                    catalogSummary = if (!scopedToEquipment) kbRepo.buildEquipmentCatalogSummary() else null
                )
                if (!geminiResponse.isNullOrBlank()) {
                    return@withContext ChatMessage(
                        text = geminiResponse,
                        isBot = true,
                        equipmentId = eq?.id,
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
        equipmentDisplayName: String?,
        catalogSummary: String? = null
    ): String? {
        val systemPrompt = if (scopedToEquipment) {
            buildScopedPrompt(userMessage, eq, equipmentDisplayName)
        } else {
            buildGeneralPrompt(userMessage, eq, catalogSummary)
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

            // Evita respuestas cortadas a mitad de frase
            val generationConfig = JsonObject().apply {
                addProperty("temperature", 0.3)
                addProperty("maxOutputTokens", 1024)
            }
            add("generationConfig", generationConfig)
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
            Eres el asistente del Laboratorio de Bromatología UTEQ.
            Responde DIRECTO AL GRANO, en español claro.

            Equipo permitido: "$eqName"
            $contextInfo

            Pregunta: "$userMessage"

            REGLAS:
            1. Contesta solo lo preguntado, completo (nunca cortes a mitad de frase).
            2. Sin relleno, sin ficha técnica completa, sin listar todo.
            3. Función/qué es: 1-3 oraciones cortas y cerradas.
            4. EPP/riesgos/prevención: viñetas concretas.
            5. Procedimiento: solo pasos numerados.
            6. Si la pregunta no es de este equipo, di solo:
               "Solo puedo responder preguntas sobre el $eqName."
            7. Máximo ~80-120 palabras. Termina siempre la última oración.
        """.trimIndent()
    }

    private fun buildGeneralPrompt(
        userMessage: String,
        matchedEquipment: EquipmentData?,
        catalogSummary: String?
    ): String {
        val matchedBlock = if (matchedEquipment != null) {
            """
            EQUIPO IDENTIFICADO EN LA PREGUNTA (usar estos datos):
            - Nombre: ${matchedEquipment.nombreComun}
            - Oficial: ${matchedEquipment.nombreOficial}
            - Fabricante/Modelo: ${matchedEquipment.fabricante} - ${matchedEquipment.modelo}
            - Función: ${matchedEquipment.funcionPrincipal}
            - Principio: ${matchedEquipment.principioFuncionamiento}
            - EPP: ${matchedEquipment.eppRequerido.joinToString(", ")}
            - Riesgos: ${matchedEquipment.riesgosAsociados.joinToString(", ")}
            - Procedimiento: ${matchedEquipment.procedimientoOperativoEstandar.joinToString(" | ")}
            - Prácticas UTEQ: ${matchedEquipment.guiasPracticaUteq.joinToString("; ")}
            """.trimIndent()
        } else {
            "No se identificó un equipo concreto en la pregunta (tema general del laboratorio)."
        }

        return """
            Eres el asistente general del Laboratorio de Bromatología UTEQ.
            Conoces TODOS los equipos registrados del sistema.

            CATÁLOGO DE EQUIPOS REGISTRADOS:
            ${catalogSummary ?: "(catálogo no disponible)"}

            $matchedBlock

            Pregunta: "$userMessage"

            REGLAS:
            1. Responde DIRECTO AL GRANO y completa las oraciones.
            2. Si preguntan por un equipo del catálogo, responde con su información (función, EPP, riesgos, etc. según lo pedido).
            3. Si preguntan por un equipo que NO está en el catálogo, responde exactamente:
               "Ese equipo no está registrado en el sistema del Laboratorio de Bromatología UTEQ."
               No inventes datos de equipos inexistentes.
            4. Si es tema general (bioseguridad/EPP general), responde breve.
            5. Máximo ~100-140 palabras.
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
