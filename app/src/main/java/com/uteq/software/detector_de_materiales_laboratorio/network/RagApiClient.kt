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
        equipmentDisplayName: String? = null,
        history: List<ChatMessage> = emptyList()
    ): ChatMessage = withContext(Dispatchers.IO) {
        val kbRepo = KnowledgeBaseRepository.getInstance(context)
        val scopedEq = if (scopedToEquipment && !equipmentId.isNullOrEmpty()) {
            kbRepo.getEquipmentById(equipmentId) ?: kbRepo.getEquipmentByClass(equipmentId)
        } else {
            null
        }

        // Modo general: resolver equipo por nombre si coincide con el catálogo
        var generalMatchedEq: EquipmentData? = null
        if (!scopedToEquipment) {
            when (val resolved = kbRepo.resolveEquipmentFromQuery(userMessage)) {
                is KnowledgeBaseRepository.EquipmentQueryResult.Found -> {
                    generalMatchedEq = resolved.equipment
                }
                is KnowledgeBaseRepository.EquipmentQueryResult.NotRegistered -> {
                    // No bloqueamos: permitimos que Gemini busque en internet
                    generalMatchedEq = null
                }
                KnowledgeBaseRepository.EquipmentQueryResult.GeneralTopic -> {
                    generalMatchedEq = history.asReversed()
                        .filter { !it.isBot }
                        .firstNotNullOfOrNull { kbRepo.findBestMatch(it.text) }
                }
            }
        }

        val eq = scopedEq ?: generalMatchedEq

        if (geminiApiKey.isNotBlank()) {
            try {
                val geminiResult = callGeminiDirectly(
                    userMessage = userMessage,
                    eq = eq,
                    scopedToEquipment = scopedToEquipment,
                    equipmentDisplayName = equipmentDisplayName ?: eq?.nombreComun,
                    catalogSummary = if (!scopedToEquipment) kbRepo.buildEquipmentCatalogSummary() else null,
                    history = history
                )
                if (geminiResult != null && geminiResult.text.isNotBlank()) {
                    val combinedCitations = mutableListOf<String>()
                    eq?.fuentesReferencias?.let { combinedCitations.addAll(it) }
                    combinedCitations.addAll(geminiResult.webCitations)

                    return@withContext ChatMessage(
                        text = geminiResult.text,
                        isBot = true,
                        equipmentId = eq?.id,
                        citations = combinedCitations.distinct(),
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

    data class GeminiResponseResult(
        val text: String,
        val webCitations: List<String> = emptyList()
    )

    private fun callGeminiDirectly(
        userMessage: String,
        eq: EquipmentData?,
        scopedToEquipment: Boolean,
        equipmentDisplayName: String?,
        catalogSummary: String? = null,
        history: List<ChatMessage> = emptyList()
    ): GeminiResponseResult? {
        val systemPrompt = if (scopedToEquipment) {
            buildScopedPrompt(userMessage, eq, equipmentDisplayName, history)
        } else {
            buildGeneralPrompt(userMessage, eq, catalogSummary, history)
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

            // Activación de Búsqueda Rápida en Google (Google Search Grounding)
            val toolsArray = JsonArray()
            val toolObj = JsonObject()
            toolObj.add("google_search", JsonObject())
            toolsArray.add(toolObj)
            add("tools", toolsArray)

            // Evita respuestas cortadas a mitad de frase
            val generationConfig = JsonObject().apply {
                addProperty("temperature", 0.4)
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
                    val candidateObj = candidates[0].asJsonObject
                    val content = candidateObj.getAsJsonObject("content")
                    val parts = content?.getAsJsonArray("parts")
                    val textBuilder = StringBuilder()
                    if (parts != null) {
                        for (i in 0 until parts.size()) {
                            val partText = parts[i].asJsonObject.get("text")?.asString
                            if (!partText.isNullOrBlank()) {
                                textBuilder.append(partText)
                            }
                        }
                    }

                    val finalAnswer = textBuilder.toString().trim()
                    if (finalAnswer.isBlank()) return null

                    // Extraer fuentes web si la búsqueda en Google aportó citas
                    val webCitations = mutableListOf<String>()
                    val groundingMetadata = candidateObj.getAsJsonObject("groundingMetadata")
                    if (groundingMetadata != null && groundingMetadata.has("groundingChunks")) {
                        val chunks = groundingMetadata.getAsJsonArray("groundingChunks")
                        if (chunks != null) {
                            for (elem in chunks) {
                                val web = elem.asJsonObject.getAsJsonObject("web")
                                if (web != null) {
                                    val title = web.get("title")?.asString.orEmpty()
                                    val uri = web.get("uri")?.asString.orEmpty()
                                    if (uri.isNotBlank()) {
                                        if (title.isNotBlank()) {
                                            webCitations.add("$title ($uri)")
                                        } else {
                                            webCitations.add(uri)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    return GeminiResponseResult(
                        text = finalAnswer,
                        webCitations = webCitations.distinct()
                    )
                }
            } else {
                Log.e(TAG, "Gemini API Error: HTTP ${response.code}")
            }
        }
        return null
    }

    /**
     * Últimos turnos de la conversación, para que el modelo resuelva
     * referencias como "¿y para qué sirve?" sin que el usuario repita el
     * nombre del equipo. Se limita a los últimos 6 mensajes (3 intercambios)
     * para no inflar el prompt. Vacío si no hay historial (primer mensaje).
     */
    private fun formatHistory(history: List<ChatMessage>): String {
        if (history.isEmpty()) return ""
        val recent = history.takeLast(6)
        val turns = recent.joinToString("\n") { msg ->
            "${if (msg.isBot) "Asistente" else "Usuario"}: ${msg.text}"
        }
        return """

            CONVERSACIÓN RECIENTE (para entender referencias como "eso", "y para qué sirve", etc.):
            $turns
        """.trimIndent()
    }

    private fun buildScopedPrompt(
        userMessage: String,
        eq: EquipmentData?,
        equipmentDisplayName: String?,
        history: List<ChatMessage> = emptyList()
    ): String {
        val eqName = eq?.nombreComun ?: equipmentDisplayName ?: "el equipo enfocado"
        val contextInfo = if (eq != null) {
            """
            CONTEXTO INSTITUCIONAL UTEQ (equipo enfocado en pantalla):
            Equipo: ${eq.nombreOficial} (${eq.nombreComun})
            Fabricante/Modelo: ${eq.fabricante} - ${eq.modelo}
            Precio Comercial Estimado: ${eq.precioAproximado ?: "No especificado"}
            Función: ${eq.funcionPrincipal}
            Principio: ${eq.principioFuncionamiento}
            Componentes: ${eq.componentesPrincipales.joinToString(", ")}
            Procedimiento Oficial UTEQ: ${eq.procedimientoOperativoEstandar.joinToString(" | ")}
            EPP Obligatorio: ${eq.eppRequerido.joinToString(", ")}
            Riesgos/Prevención: ${eq.riesgosAsociados.joinToString(", ")}
            Normas de Seguridad: ${eq.normasSeguridad.joinToString(", ")}
            Prácticas UTEQ: ${eq.guiasPracticaUteq.joinToString("; ")}
            Fuentes Oficiales: ${eq.fuentesReferencias.joinToString("; ")}
            """.trimIndent()
        } else {
            "Equipo enfocado: $eqName."
        }

        return """
            Eres el Asistente Experto en Bromatología y Ciencias de Laboratorio de la Universidad Técnica Estatal de Quevedo (UTEQ).
            Responde en español claro, profesional y directo al grano.

            $contextInfo
            ${formatHistory(history)}

            Pregunta del usuario: "$userMessage"

            DIRECTRICES CLAVE:
            1. Si la pregunta es sobre el equipo enfocado ($eqName), prioriza siempre la información técnica, EPP, riesgos y procedimiento oficial de la UTEQ provisto arriba.
            2. REGLA ESTRICTA DE PRECIO: Si preguntan por el precio o costo, toma EXACTAMENTE el valor de la ficha técnica provisto en "Precio Comercial Estimado". Exprésalo de forma precisa con la fórmula "cuesta [monto] dólares..." (por ejemplo: "cuesta 800 dólares, con un rango estimado de 650 a 950 dólares"). NUNCA uses emojis (como 💰 ni ningún otro) ni signos como $, asteriscos o paréntesis para el precio, ya que la respuesta se reproduce por voz.
            3. Si la pregunta involucra reactivos, cálculos químicos, normas internacionales, fundamentos científicos que no consten en la ficha local o temas complementarios, UTILIZA LA BÚSQUEDA EN INTERNET (Google Search) para fundamentar y responder con precisión.
            4. Resuelve referencias relativas ("¿cómo se usa eso?", "¿qué reactivos necesita?") usando la CONVERSACIÓN RECIENTE.
            5. Estilo adecuado para chat y para voz (TTS): alrededor de 70 a 130 palabras, oraciones completas, sin emojis ni signos confusos ni enlaces URL en el texto hablado.
            6. Nunca te niegues a responder consultas científicas, técnicas o de laboratorio.
        """.trimIndent()
    }

    private fun buildGeneralPrompt(
        userMessage: String,
        matchedEquipment: EquipmentData?,
        catalogSummary: String?,
        history: List<ChatMessage> = emptyList()
    ): String {
        val matchedBlock = if (matchedEquipment != null) {
            """
            EQUIPO IDENTIFICADO EN EL CATÁLOGO LOCAL UTEQ:
            - Nombre: ${matchedEquipment.nombreComun}
            - Oficial: ${matchedEquipment.nombreOficial}
            - Fabricante/Modelo: ${matchedEquipment.fabricante} - ${matchedEquipment.modelo}
            - Precio Comercial Estimado: ${matchedEquipment.precioAproximado ?: "No especificado"}
            - Función: ${matchedEquipment.funcionPrincipal}
            - Principio: ${matchedEquipment.principioFuncionamiento}
            - EPP: ${matchedEquipment.eppRequerido.joinToString(", ")}
            - Riesgos: ${matchedEquipment.riesgosAsociados.joinToString(", ")}
            - Procedimiento: ${matchedEquipment.procedimientoOperativoEstandar.joinToString(" | ")}
            - Prácticas UTEQ: ${matchedEquipment.guiasPracticaUteq.joinToString("; ")}
            """.trimIndent()
        } else {
            "La consulta no refiere a un equipo específico del catálogo local (o es una consulta sobre reactivos, química, procedimientos o equipos generales)."
        }

        return """
            Eres el Asistente Inteligente del Laboratorio de Bromatología y Ciencias de la Universidad Técnica Estatal de Quevedo (UTEQ).
            Responde en español con rigor académico, amabilidad y claridad.

            CATÁLOGO DE EQUIPOS REGISTRADOS EN BROMATOLOGÍA UTEQ:
            ${catalogSummary ?: "(catálogo no disponible)"}

            $matchedBlock
            ${formatHistory(history)}

            Pregunta del usuario: "$userMessage"

            DIRECTRICES CLAVE:
            1. Si la pregunta se refiere a un equipo del inventario local de Bromatología UTEQ, prioriza los datos institucionales provistos arriba.
            2. REGLA ESTRICTA DE PRECIO: Si preguntan por el precio o costo de un equipo registrado, toma EXACTAMENTE el valor de la ficha técnica provisto en "Precio Comercial Estimado". Di con exactitud "cuesta [monto] dólares..." (por ejemplo: "cuesta 800 dólares, con un rango estimado de 650 a 950 dólares"). NUNCA uses emojis (como 💰 ni ningún otro) ni signos como $, asteriscos o paréntesis para el precio, para que el motor de voz lo pronuncie perfecto.
            3. Si la pregunta es sobre un equipo que NO está registrado en la UTEQ, técnicas químicas, cálculo de concentraciones, normas (AOAC, Codex, ISO), reactivos o cualquier duda científica abierta, UTILIZA LA BÚSQUEDA EN INTERNET (Google Search) para brindar una respuesta rigurosa y actualizada. Si es un equipo no disponible físicamente en la sede de Bromatología UTEQ, menciónalo con naturalidad y explica su funcionamiento y uso en laboratorio.
            4. Resuelve pronombres y referencias ("eso", "¿y cuánto cuesta?") con la CONVERSACIÓN RECIENTE.
            5. Estilo adecuado para chat y voz: conciso, profesional y fluido (aprox. 80-130 palabras, oraciones completas, sin emojis ni URLs crudas dentro del texto).
            6. No limites al estudiante; responde cualquier consulta de ciencias, laboratorio o bromatología con la mayor utilidad posible.
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
