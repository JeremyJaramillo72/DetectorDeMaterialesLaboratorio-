package com.uteq.software.detector_de_materiales_laboratorio.data

import android.content.Context
import com.google.gson.Gson
import com.uteq.software.detector_de_materiales_laboratorio.model.ChatMessage
import com.uteq.software.detector_de_materiales_laboratorio.model.EquipmentData
import com.uteq.software.detector_de_materiales_laboratorio.model.KnowledgeBaseRoot
import java.io.InputStreamReader

class KnowledgeBaseRepository private constructor(private val context: Context) {

    private var knowledgeBaseRoot: KnowledgeBaseRoot? = null
    private val equipmentMap = HashMap<String, EquipmentData>()

    init {
        loadKnowledgeBase()
    }

    private fun loadKnowledgeBase() {
        try {
            context.assets.open("manuales_bromatologia_uteq.json").use { inputStream ->
                val reader = InputStreamReader(inputStream, "UTF-8")
                knowledgeBaseRoot = Gson().fromJson(reader, KnowledgeBaseRoot::class.java)
                knowledgeBaseRoot?.equipos?.forEach { eq ->
                    equipmentMap[eq.id.lowercase()] = eq
                    equipmentMap[eq.claseYolo.lowercase()] = eq
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getAllEquipments(): List<EquipmentData> {
        return knowledgeBaseRoot?.equipos ?: emptyList()
    }

    fun getEquipmentByClass(yoloClass: String): EquipmentData? {
        val key = yoloClass.lowercase().trim()
        return equipmentMap[key]
    }

    fun getEquipmentById(id: String): EquipmentData? {
        return equipmentMap[id.lowercase().trim()]
    }

    fun generateOfflineRagResponse(userQuery: String, equipmentId: String?): ChatMessage {
        val targetEquipment = if (!equipmentId.isNullOrEmpty()) {
            getEquipmentById(equipmentId) ?: getEquipmentByClass(equipmentId)
        } else {
            findBestMatch(userQuery)
        } ?: getAllEquipments().firstOrNull()

        if (targetEquipment == null) {
            return ChatMessage(
                text = "No se encontró información técnica para este equipo en la base de datos de Bromatología.",
                isBot = true
            )
        }

        val q = userQuery.lowercase()
        val sb = StringBuilder()

        sb.append("🔬 **${targetEquipment.nombreOficial}**\n")
        sb.append("🏭 Fabricante: ${targetEquipment.fabricante} (${targetEquipment.modelo})\n\n")

        val epp = targetEquipment.eppRequerido
        val risks = targetEquipment.riesgosAsociados
        val citations = targetEquipment.fuentesReferencias

        when {
            q.contains("epp") || q.contains("seguridad") || q.contains("proteccion") || q.contains("riesgo") || q.contains("peligro") -> {
                sb.append("🦺 **Elementos de Protección Personal (EPP) Obligatorios:**\n")
                epp.forEach { sb.append("• $it\n") }
                sb.append("\n⚠️ **Riesgos Asociados y Bioseguridad:**\n")
                risks.forEach { sb.append("• $it\n") }
                if (targetEquipment.normasSeguridad.isNotEmpty()) {
                    sb.append("\n📌 **Normas de Operación Segura:**\n")
                    targetEquipment.normasSeguridad.forEach { sb.append("• $it\n") }
                }
            }
            q.contains("paso") || q.contains("procedimiento") || q.contains("como usar") || q.contains("operar") || q.contains("practica") || q.contains("ensayo") -> {
                sb.append("📋 **Procedimiento Operativo Paso a Paso:**\n")
                targetEquipment.procedimientoOperativoEstandar.forEach { sb.append("$it\n") }
                if (targetEquipment.guiasPracticaUteq.isNotEmpty()) {
                    sb.append("\n🧪 **Guías de Práctica UTEQ:**\n")
                    targetEquipment.guiasPracticaUteq.forEach { sb.append("• $it\n") }
                }
            }
            q.contains("componente") || q.contains("parte") || q.contains("estructura") -> {
                sb.append("⚙️ **Componentes Principales del Equipo:**\n")
                targetEquipment.componentesPrincipales.forEach { sb.append("• $it\n") }
            }
            else -> {
                sb.append("📌 **Función en Bromatología:**\n${targetEquipment.funcionPrincipal}\n\n")
                sb.append("🔬 **Principio de Funcionamiento:**\n${targetEquipment.principioFuncionamiento}\n\n")
                if (targetEquipment.guiasPracticaUteq.isNotEmpty()) {
                    sb.append("🧪 **Prácticas UTEQ Relacionadas:**\n")
                    targetEquipment.guiasPracticaUteq.forEach { sb.append("• $it\n") }
                }
            }
        }

        if (citations.isNotEmpty()) {
            sb.append("\n📚 **Fuentes Oficiales Consultadas:**\n")
            citations.forEach { sb.append("• $it\n") }
        }

        return ChatMessage(
            text = sb.toString().trim(),
            isBot = true,
            equipmentId = targetEquipment.id,
            citations = citations,
            eppRequired = epp,
            risks = risks
        )
    }

    private fun findBestMatch(query: String): EquipmentData? {
        val q = query.lowercase()
        var bestMatch: EquipmentData? = null
        var maxScore = 0

        getAllEquipments().forEach { eq ->
            var score = 0
            if (q.contains(eq.id.lowercase())) score += 5
            if (q.contains(eq.claseYolo.lowercase())) score += 5
            if (q.contains(eq.nombreComun.lowercase())) score += 4
            if (q.contains(eq.fabricante.lowercase())) score += 2
            if (q.contains(eq.modelo.lowercase())) score += 2
            if (score > maxScore) {
                maxScore = score
                bestMatch = eq
            }
        }
        return bestMatch
    }

    companion object {
        @Volatile
        private var INSTANCE: KnowledgeBaseRepository? = null

        fun getInstance(context: Context): KnowledgeBaseRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: KnowledgeBaseRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
