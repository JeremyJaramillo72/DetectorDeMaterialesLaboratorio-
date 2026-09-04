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
        "destilacion de nitrogeno y proteinas" to "destilador_proteina",
        "destilación de nitrógeno y proteínas" to "destilador_proteina",
        "destilador de agua continuo metalico" to "destilador_agua",
        "sistema de tratamiento y desionizacion de agua" to "sistema_tratamiento_agua",
        "sistema de tratamiento y desionización de agua" to "sistema_tratamiento_agua"
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
        val eq = if (!equipmentId.isNullOrEmpty()) {
            getEquipmentById(equipmentId) ?: getEquipmentByClass(equipmentId)
        } else {
            null
        }

        if (eq == null) {
            return ChatMessage(
                text = "No encontré la ficha de ese equipo. Vuelve a detectarlo y abre Consultar sobre este equipo.",
                isBot = true
            )
        }

        val q = userQuery.lowercase()
            .replace('á', 'a')
            .replace('é', 'e')
            .replace('í', 'i')
            .replace('ó', 'o')
            .replace('ú', 'u')

        if (isOffTopicEquipmentQueryNormalized(q)) {
            return ChatMessage(
                text = "Solo puedo responder preguntas sobre el **${eq.nombreComun}**.\n\n" +
                    "Pregúntame por prevención, EPP, procedimiento, riesgos, función o prácticas UTEQ de este equipo.",
                isBot = true,
                equipmentId = eq.id
            )
        }

        val text = when {
            q.contains("epp") || q.contains("guante") || q.contains("gafa") || q.contains("mandil") ||
                q.contains("proteccion personal") -> {
                buildString {
                    append("Para operar el **${eq.nombreComun}**, usa este EPP:\n")
                    eq.eppRequerido.forEach { append("• $it\n") }
                    if (eq.eppRequerido.isEmpty()) {
                        append("• Mandil de laboratorio, gafas de seguridad y guantes de nitrilo.")
                    }
                }.trim()
            }

            q.contains("prevencion") || q.contains("precaucion") || q.contains("medida") ||
                q.contains("cuidado") || q.contains("seguridad") || q.contains("riesgo") ||
                q.contains("peligro") || q.contains("evitar") -> {
                buildString {
                    append("Al usar el **${eq.nombreComun}**, ten en cuenta estas medidas de prevención:\n\n")
                    if (eq.riesgosAsociados.isNotEmpty()) {
                        eq.riesgosAsociados.forEach { append("• $it\n") }
                    }
                    if (eq.normasSeguridad.isNotEmpty()) {
                        append("\nTambién sigue estas normas:\n")
                        eq.normasSeguridad.take(4).forEach { append("• $it\n") }
                    }
                    if (eq.eppRequerido.isNotEmpty()) {
                        append("\nEPP recomendado: ")
                        append(eq.eppRequerido.take(3).joinToString("; "))
                        append(".")
                    }
                    if (eq.riesgosAsociados.isEmpty() && eq.normasSeguridad.isEmpty()) {
                        append("• Verifica conexiones y nivel de agua antes de encender.\n")
                        append("• No dejes el equipo sin supervisión.\n")
                        append("• Usa EPP básico de laboratorio.")
                    }
                }.trim()
            }

            q.contains("paso") || q.contains("procedimiento") || q.contains("como usar") ||
                q.contains("como se usa") || q.contains("operar") || q.contains("encender") -> {
                buildString {
                    append("Así se opera el **${eq.nombreComun}**, paso a paso:\n\n")
                    if (eq.procedimientoOperativoEstandar.isNotEmpty()) {
                        eq.procedimientoOperativoEstandar.forEach { append("$it\n") }
                    } else {
                        append("1. Revisa el estado del equipo y el EPP.\n")
                        append("2. Prepara la muestra/ensayo según la práctica UTEQ.\n")
                        append("3. Opera el equipo siguiendo el manual del fabricante.\n")
                        append("4. Apaga y limpia al finalizar.")
                    }
                }.trim()
            }

            q.contains("practica") || q.contains("uteq") || q.contains("guia") -> {
                buildString {
                    append("Prácticas UTEQ relacionadas con el **${eq.nombreComun}**:\n")
                    if (eq.guiasPracticaUteq.isNotEmpty()) {
                        eq.guiasPracticaUteq.forEach { append("• $it\n") }
                    } else {
                        append("• Consulta la guía de prácticas del laboratorio de Bromatología.")
                    }
                }.trim()
            }

            q.contains("componente") || q.contains("parte") || q.contains("estructura") -> {
                buildString {
                    append("Los componentes principales del **${eq.nombreComun}** son:\n")
                    eq.componentesPrincipales.forEach { append("• $it\n") }
                }.trim()
            }

            q.contains("para que") || q.contains("que es") || q.contains("funcion") ||
                q.contains("sirve") || q.contains("uso") -> {
                "El **${eq.nombreComun}** sirve para esto:\n\n${eq.funcionPrincipal}\n\n" +
                    "En resumen: ${eq.principioFuncionamiento.take(220)}"
            }

            else -> {
                "Solo puedo responder preguntas sobre el **${eq.nombreComun}**.\n\n" +
                    "Prueba con: prevención, EPP, procedimiento, riesgos, función o prácticas UTEQ."
            }
        }

        return ChatMessage(
            text = text,
            isBot = true,
            equipmentId = eq.id,
            citations = emptyList(),
            eppRequired = if (q.contains("epp") || q.contains("prevencion") || q.contains("seguridad")) {
                eq.eppRequerido
            } else {
                emptyList()
            },
            risks = if (q.contains("riesgo") || q.contains("prevencion") || q.contains("seguridad")) {
                eq.riesgosAsociados
            } else {
                emptyList()
            }
        )
    }

    fun isOffTopicEquipmentQuery(userQuery: String): Boolean {
        val q = userQuery.lowercase()
            .replace('á', 'a')
            .replace('é', 'e')
            .replace('í', 'i')
            .replace('ó', 'o')
            .replace('ú', 'u')
        return isOffTopicEquipmentQueryNormalized(q)
    }

    private fun isOffTopicEquipmentQueryNormalized(q: String): Boolean {
        val onTopicKeywords = listOf(
            "equipo", "bomba", "vacio", "kjeldahl", "fibra", "destil", "phmetro",
            "estufa", "molino", "refract", "calorimetr", "campana", "cabina", "viscos", "microscop",
            "epp", "guante", "gafa", "mandil", "proteccion", "prevencion", "precaucion",
            "cuidado", "seguridad", "riesgo", "peligro", "evitar", "paso", "procedimiento",
            "operar", "encender", "funcion", "sirve", "principio", "componente",
            "practica", "uteq", "guia", "norma", "bioseguridad", "muestra", "laboratorio",
            "filtracion", "secado", "reactivo", "vapor", "mantenimiento", "limpiar", "apagar",
            "como se usa", "como usar", "para que", "que es", "medida", "precauciones"
        )
        if (onTopicKeywords.any { q.contains(it) }) return false

        val offTopicPatterns = listOf(
            Regex("""\d+\s*[x×*+\-/]\s*\d+"""),
            Regex("""\bcuanto\s+es\b"""),
            Regex("""\bcuanto\s+vale\b"""),
            Regex("""\bsuma\b|\bresta\b|\bmultiplic"""),
            Regex("""\bclima\b|\bchiste\b|\bmusica\b|\bfutbol\b|\breceta\b|\btraduc""")
        )
        if (offTopicPatterns.any { it.containsMatchIn(q) }) return true

        val words = q.split(Regex("\\s+")).filter { it.length > 1 }
        return words.isNotEmpty() && onTopicKeywords.none { q.contains(it) }
    }

    fun generateOfflineGeneralResponse(userQuery: String): ChatMessage {
        val q = userQuery.lowercase()
            .replace('á', 'a')
            .replace('é', 'e')
            .replace('í', 'i')
            .replace('ó', 'o')
            .replace('ú', 'u')

        val matched = findBestMatch(userQuery)
        if (matched != null) {
            return ChatMessage(
                text = "Eso suena a **${matched.nombreComun}**. En modo general no te suelto toda la ficha.\n\n" +
                    "Detectalo con la cámara y entra a **Ficha Técnica → Consultar sobre este equipo** para preguntarme con detalle.",
                isBot = true
            )
        }

        val text = when {
            q.contains("epp") || q.contains("proteccion") || q.contains("guante") ->
                "En el laboratorio de Bromatología UTEQ el EPP básico suele incluir mandil, gafas de seguridad, " +
                    "guantes de nitrilo y zapato cerrado. Si vas a usar un equipo concreto, " +
                    "abre su ficha para ver el EPP específico."

            q.contains("seguridad") || q.contains("bioseguridad") || q.contains("riesgo") || q.contains("prevencion") ->
                "Medidas generales: no comer ni beber en el lab, usar EPP, etiquetar reactivos, " +
                    "conocer salidas de emergencia y nunca operar un equipo sin inducción. " +
                    "Si me dices el equipo, te doy prevenciones puntuales desde su ficha."

            else ->
                "Puedo orientarte de forma general sobre bioseguridad, EPP y prácticas UTEQ.\n\n" +
                    "Si necesitas detalle de un equipo (prevención, pasos, riesgos), detectalo en cámara " +
                    "y usa **Consultar sobre este equipo**."
        }

        return ChatMessage(text = text, isBot = true)
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
