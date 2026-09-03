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

    // Mapa de traducción: nombre Roboflow (labels.txt) → id interno del JSON
    private val roboflowToId = mapOf(
        "agitador de tubos - mezclador vortex" to "agitador_vortex",
        "agua destilada desmineralizada" to "bidon_agua_destilada",
        "analizador de fibra cruda y fracciones" to "analizador_fibra",
        "balanza analitica de la serie ohaus pioneer" to "balanza_ohaus_pioneer",
        "bateria de desionizacion y tratamiento de agua" to "sistema_tratamiento_agua",
        "bomba calorimetrica de oxigeno" to "calorimetro_bomba",
        "bomba de vacio de membrana - diafragma portatil" to "bomba_vacio_membrana",
        "bomba de vacio por recirculacion de agua" to "bomba_vacio_recirculacion",
        "cabina de flujo laminar" to "cabina_flujo_laminar_uvp",
        "campana de extraccion de gases de laboratorio" to "campana_extraccion_gases",
        "destilacion por arrastre de vapor" to "destilador_kjeldahl",
        "destilador por arrastre de vapor tipo kjeldahl" to "destilador_kjeldahl",
        "destilador_de_agua_continuo_metalico" to "destilador_agua",
        "estufa - horno universal de secado" to "estufa_secado_memmert",
        "extractor de laboratorio automatico para analisis de grasas y aceites" to "extractor_soxhlet",
        "molino ciclonico de muestras bromatologicas" to "molino_ciclonico_foss",
        "mufla electrica de laboratorio" to "mufla_electrica",
        "placa calefactora con agitador magneticometalico" to "placa_calefactora_heidolph",
        "potenciometro - ph-metro digital de mesa" to "phmetro_ohaus",
        "refractometro digital abbe de mesa" to "refractometro_atago",
        "sistema de tratamiento y desionizacion deagua" to "sistema_tratamiento_agua",
        "soporte giratorio para pipetas de vidrio" to "gradilla_pipetas",
        "stufa de laboratorio de conveccion" to "estufa_secado_memmert",
        "termometro digital parr model 6775" to "calorimetro_bomba",
        "unidad de destilacion kjeldahl" to "destilador_kjeldahl",
        "viscosimetro rotacional digital brookfield" to "viscosimetro_brookfield",
        "viscosímetro brookfield modelo dv-e" to "viscosimetro_brookfield",
        "viscosimetro brookfield modelo dv-e" to "viscosimetro_brookfield",
        "microscopio trinocular" to "microscopio_trinocular",
        "microcospio trinocular" to "microscopio_trinocular",
        "destilador de proteina" to "destilador_proteina",
        "destilador de proteína" to "destilador_proteina",
        "destilador de agua continuo metalico" to "destilador_agua"
    )

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
        // Primero intentar búsqueda directa
        equipmentMap[key]?.let { return it }
        // Luego usar el mapa de traducción Roboflow → id interno
        roboflowToId[key]?.let { mappedId ->
            equipmentMap[mappedId]?.let { return it }
        }
        return null
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
