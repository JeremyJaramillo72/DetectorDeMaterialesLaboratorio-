package com.uteq.software.detector_de_materiales_laboratorio.network

import android.content.Context
import com.google.gson.Gson
import com.uteq.software.detector_de_materiales_laboratorio.data.KnowledgeBaseRepository
import com.uteq.software.detector_de_materiales_laboratorio.model.ChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class RagApiClient(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    // Dirección del backend local (10.0.2.2 para emulador Android)
    var serverBaseUrl: String = "http://10.0.2.2:8000"

    suspend fun sendMessage(userMessage: String, equipmentId: String?): ChatMessage = withContext(Dispatchers.IO) {
        val payload = mapOf(
            "message" to userMessage,
            "equipment_id" to equipmentId
        )
        val requestBody = gson.toJson(payload).toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url("$serverBaseUrl/api/chat")
            .post(requestBody)
            .build()

        try {
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
            println("⚠️ Error conectando al servidor backend RAG: ${e.message}. Usando motor RAG local...")
        }

        // Fallback RAG Offline garantizado
        return@withContext KnowledgeBaseRepository.getInstance(context)
            .generateOfflineRagResponse(userMessage, equipmentId)
    }

    private data class RagChatApiResponse(
        val response: String,
        val equipmentId: String?,
        val citations: List<String>?,
        val eppRequired: List<String>?,
        val risks: List<String>?
    )
}
