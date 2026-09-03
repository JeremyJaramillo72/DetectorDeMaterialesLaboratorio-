package com.uteq.software.detector_de_materiales_laboratorio.network

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
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

    // Configuración oficial de Google Gemini 2.5 Flash (leída de local.properties para seguridad)
    private val geminiApiKey = com.uteq.software.detector_de_materiales_laboratorio.BuildConfig.GEMINI_API_KEY
    private val geminiModel = "gemini-2.5-flash"
    private val geminiApiUrl: String
        get() = "https://generativelanguage.googleapis.com/v1beta/models/$geminiModel:generateContent?key=$geminiApiKey"

    // Dirección del backend local (opcional)
    var serverBaseUrl: String = "http://10.0.2.2:8000"

    suspend fun sendMessage(userMessage: String, equipmentId: String?): ChatMessage = withContext(Dispatchers.IO) {
        val kbRepo = KnowledgeBaseRepository.getInstance(context)
        val eq = if (!equipmentId.isNullOrEmpty()) {
            kbRepo.getEquipmentById(equipmentId) ?: kbRepo.getEquipmentByClass(equipmentId)
        } else {
            kbRepo.getAllEquipments().firstOrNull()
        }

        // 1. Intento Directo con Google Gemini 2.5 Flash (RAG en la nube sin necesidad de PC)
        if (geminiApiKey.isNotBlank()) {
            try {
                val geminiResponse = callGeminiDirectly(userMessage, eq)
                if (!geminiResponse.isNullOrBlank()) {
                    return@withContext ChatMessage(
                        text = geminiResponse,
                        isBot = true,
                        equipmentId = eq?.id ?: equipmentId,
                        citations = eq?.fuentesReferencias ?: emptyList(),
                        eppRequired = eq?.eppRequerido ?: emptyList(),
                        risks = eq?.riesgosAsociados ?: emptyList()
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Fallo al consultar Gemini directamente: ${e.message}. Probando servidor local...")
            }
        }

        // 2. Intento con Backend Python Local (si está corriendo)
        try {
            val payload = mapOf(
                "message" to userMessage,
                "equipment_id" to equipmentId
            )
            val requestBody = gson.toJson(payload).toRequestBody(jsonMediaType)
            val request = Request.Builder()
                .url("$serverBaseUrl/api/chat")
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyString = response.body?.string() ?: ""
                    val apiResponse = gson.fromJson(bodyString, RagChatApiResponse::class.java)
                    return@withContext ChatMessage(
                        text = apiResponse.response,
                        isBot = true,
                        equipmentId = apiResponse.equipmentId ?: equipmentId,
                        citations = apiResponse.citations ?: emptyList(),
                        eppRequired = apiResponse.eppRequired ?: emptyList(),
                        risks = apiResponse.risks ?: emptyList()
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Backend local no disponible: ${e.message}. Activando RAG Offline local...")
        }

        // 3. Fallback Offline Local Garantizado (100% sin internet)
        return@withContext kbRepo.generateOfflineRagResponse(userMessage, equipmentId)
    }

    private fun callGeminiDirectly(userMessage: String, eq: EquipmentData?): String? {
        val contextInfo = if (eq != null) {
            """
            DATOS TÉCNICOS DEL EQUIPO:
            - Nombre Oficial: ${eq.nombreOficial} (${eq.nombreComun})
            - Fabricante y Modelo: ${eq.fabricante} - ${eq.modelo}
            - Ubicación en el Laboratorio: ${eq.ubicacion}
            - Función Principal: ${eq.funcionPrincipal}
            - Principio de Funcionamiento: ${eq.principioFuncionamiento}
            - Componentes: ${eq.componentesPrincipales.joinToString(", ")}
            - Procedimiento Operativo: ${eq.procedimientoOperativoEstandar.joinToString(" | ")}
            - EPP Obligatorio: ${eq.eppRequerido.joinToString(", ")}
            - Riesgos y Normas de Bioseguridad: ${eq.riesgosAsociados.joinToString(", ")}
            - Prácticas UTEQ: ${eq.guiasPracticaUteq.joinToString("; ")}
            - Normas Oficiales: ${eq.fuentesReferencias.joinToString("; ")}
            """.trimIndent()
        } else {
            "Laboratorio de Bromatología - UTEQ (Información General de Alimentos y Seguridad)."
        }

        val systemPrompt = """
            Eres el Asistente Experto en Bromatología y Bioseguridad del Laboratorio de la Universidad Técnica Estatal de Quevedo (UTEQ).
            Responde la siguiente consulta basándote en la información técnica oficial del equipo:
            
            $contextInfo
            
            PREGUNTA DEL ESTUDIANTE:
            $userMessage
            
            INSTRUCCIONES DE RESPUESTA:
            1. Sé claro, profesional y académicamente riguroso.
            2. Si preguntan por procedimiento, explica los pasos en orden.
            3. Si preguntan por seguridad o riesgos, resalta el EPP obligatorio y precauciones críticas.
            4. Menciona las normas técnicas oficiales (AOAC, INEN, ISO) relevantes.
        """.trimIndent()

        val jsonPayload = JsonObject().apply {
            val contentsArray = com.google.gson.JsonArray()
            val contentObj = JsonObject()
            val partsArray = com.google.gson.JsonArray()
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
                    val candidate = candidates[0].asJsonObject
                    val content = candidate.getAsJsonObject("content")
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

    private data class RagChatApiResponse(
        val response: String,
        val equipmentId: String?,
        val citations: List<String>?,
        val eppRequired: List<String>?,
        val risks: List<String>?
    )
}
