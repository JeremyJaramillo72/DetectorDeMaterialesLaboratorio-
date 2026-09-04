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
                q.contains("sirve") || q.contains("uso") || q.contains("principio") -> {
                // Respuesta completa y directa (sin cortar a mitad de frase)
                "El **${eq.nombreComun}** sirve para: ${eq.funcionPrincipal.trim()}"
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

        when (val resolved = resolveEquipmentFromQuery(userQuery)) {
            is EquipmentQueryResult.Found -> {
                val eq = resolved.equipment
                val detail = buildDirectEquipmentAnswer(q, eq)
                return ChatMessage(
                    text = detail,
                    isBot = true,
                    equipmentId = eq.id,
                    citations = eq.fuentesReferencias,
                    eppRequired = eq.eppRequerido,
                    risks = eq.riesgosAsociados
                )
            }
            is EquipmentQueryResult.NotRegistered -> {
                return ChatMessage(
                    text = "El equipo **\"${resolved.askedName}\"** no está registrado en el sistema del Laboratorio de Bromatología UTEQ.\n\n" +
                        "Equipos disponibles (ejemplos):\n" +
                        listRegisteredEquipmentPreview() +
                        "\nSi buscas uno de esos, escribe su nombre exacto o detectalo con la cámara.",
                    isBot = true
                )
            }
            EquipmentQueryResult.GeneralTopic -> Unit
        }

        val text = when {
            q.contains("epp") || q.contains("proteccion") || q.contains("guante") ->
                "EPP general del laboratorio: mandil, gafas de seguridad, guantes de nitrilo y zapato cerrado. " +
                    "Si me nombras un equipo registrado, te doy el EPP específico."

            q.contains("seguridad") || q.contains("bioseguridad") || q.contains("riesgo") || q.contains("prevencion") ->
                "Bioseguridad general: no comer/beber en el lab, usar EPP, etiquetar reactivos, " +
                    "conocer salidas de emergencia y no operar equipos sin inducción. " +
                    "Si preguntas por un equipo registrado, te doy riesgos concretos."

            q.contains("lista") || q.contains("que equipos") || q.contains("equipos hay") || q.contains("catalogo") ->
                "Equipos registrados en el sistema:\n${listRegisteredEquipmentPreview(limit = 30)}"

            else ->
                "Puedo ayudarte con bioseguridad general o con cualquier equipo registrado del laboratorio.\n\n" +
                    "Ejemplos: ${listRegisteredEquipmentPreview(limit = 6)}\n\n" +
                    "Pregunta por nombre (ej. \"función del microscopio trinocular\")."
        }

        return ChatMessage(text = text, isBot = true)
    }

    sealed class EquipmentQueryResult {
        data class Found(val equipment: EquipmentData) : EquipmentQueryResult()
        data class NotRegistered(val askedName: String) : EquipmentQueryResult()
        data object GeneralTopic : EquipmentQueryResult()
    }

    /**
     * Resuelve si la consulta habla de un equipo del catálogo, de uno desconocido, o es tema general.
     */
    fun resolveEquipmentFromQuery(query: String): EquipmentQueryResult {
        val matched = findBestMatch(query)
        if (matched != null) return EquipmentQueryResult.Found(matched)

        val askedName = extractPossibleEquipmentName(query)
        if (!askedName.isNullOrBlank() && looksLikeEquipmentRequest(query)) {
            return EquipmentQueryResult.NotRegistered(askedName)
        }

        // Pide info de un "equipo X" pero no matcheó ninguno
        if (looksLikeEquipmentRequest(query) && extractPossibleEquipmentName(query) != null) {
            return EquipmentQueryResult.NotRegistered(extractPossibleEquipmentName(query)!!)
        }

        return EquipmentQueryResult.GeneralTopic
    }

    fun buildEquipmentCatalogSummary(limitPerField: Int = 120): String {
        return getAllEquipments().joinToString("\n") { eq ->
            "- ${eq.nombreComun} | id=${eq.id} | función=${eq.funcionPrincipal.take(limitPerField)}"
        }
    }

    fun listRegisteredEquipmentNames(): List<String> =
        getAllEquipments().map { it.nombreComun }.sorted()

    private fun listRegisteredEquipmentPreview(limit: Int = 10): String {
        val names = listRegisteredEquipmentNames()
        val shown = names.take(limit).joinToString("\n") { "• $it" }
        return if (names.size > limit) {
            "$shown\n• … y ${names.size - limit} más"
        } else {
            shown
        }
    }

    private fun buildDirectEquipmentAnswer(q: String, eq: EquipmentData): String {
        return when {
            q.contains("epp") || q.contains("guante") || q.contains("proteccion") -> {
                buildString {
                    append("EPP para **${eq.nombreComun}**:\n")
                    eq.eppRequerido.forEach { append("• $it\n") }
                    if (eq.eppRequerido.isEmpty()) append("• Mandil, gafas y guantes de nitrilo.")
                }.trim()
            }
            q.contains("riesgo") || q.contains("prevencion") || q.contains("seguridad") || q.contains("cuidado") -> {
                buildString {
                    append("Riesgos / prevención de **${eq.nombreComun}**:\n")
                    eq.riesgosAsociados.forEach { append("• $it\n") }
                    if (eq.normasSeguridad.isNotEmpty()) {
                        append("\nNormas:\n")
                        eq.normasSeguridad.take(4).forEach { append("• $it\n") }
                    }
                }.trim()
            }
            q.contains("paso") || q.contains("procedimiento") || q.contains("como usar") || q.contains("operar") -> {
                buildString {
                    append("Procedimiento de **${eq.nombreComun}**:\n")
                    eq.procedimientoOperativoEstandar.forEach { append("$it\n") }
                }.trim()
            }
            q.contains("practica") || q.contains("uteq") -> {
                buildString {
                    append("Prácticas UTEQ de **${eq.nombreComun}**:\n")
                    eq.guiasPracticaUteq.forEach { append("• $it\n") }
                }.trim()
            }
            else -> {
                "**${eq.nombreComun}** (${eq.fabricante} • ${eq.modelo})\n\n" +
                    "Función: ${eq.funcionPrincipal}\n\n" +
                    "Ubicación: ${eq.ubicacion}"
            }
        }
    }

    private fun looksLikeEquipmentRequest(query: String): Boolean {
        val q = normalizeText(query)
        val triggers = listOf(
            "equipo", "informacion", "info", "sobre el", "sobre la", "del ", "de la ",
            "funcion", "ficha", "dame", "necesito", "que es", "como funciona",
            "microscop", "bomba", "destil", "estufa", "molino", "balanza", "phmetro",
            "ph-metro", "viscos", "mufla", "campana", "cabina", "refract", "agitador",
            "analizador", "extractor", "placa", "gradilla", "piseta", "cilindro", "bidon"
        )
        return triggers.any { q.contains(it) }
    }

    private fun extractPossibleEquipmentName(query: String): String? {
        val cleaned = query.trim()
        val patterns = listOf(
            Regex("""(?i)(?:informaci[oó]n|info|datos|ficha|funci[oó]n|riesgos?|epp|procedimiento)\s+(?:sobre|de|del|de la)\s+(?:el |la |los |las )?(.+)$"""),
            Regex("""(?i)sobre\s+(?:el |la |los |las )?(.+)$"""),
            Regex("""(?i)(?:del|de la)\s+(.+)$"""),
            Regex("""(?i)equipo\s+(.+)$"""),
            Regex("""(?i)necesito.*?sobre\s+(?:el |la )?(.+)$""")
        )
        for (pattern in patterns) {
            val match = pattern.find(cleaned)?.groupValues?.getOrNull(1)?.trim()
            if (!match.isNullOrBlank() && match.length in 3..80) {
                return match.trimEnd('.', '?', '!')
            }
        }
        return null
    }

    private fun normalizeText(value: String): String {
        return value.lowercase()
            .replace('á', 'a')
            .replace('é', 'e')
            .replace('í', 'i')
            .replace('ó', 'o')
            .replace('ú', 'u')
            .replace('ü', 'u')
            .replace('ñ', 'n')
    }

    fun findBestMatch(query: String): EquipmentData? {
        val q = normalizeText(query)
        val queryWords = q.split(Regex("[^a-z0-9]+")).filter { it.length >= 3 }
        var bestMatch: EquipmentData? = null
        var maxScore = 0

        getAllEquipments().forEach { eq ->
            var score = 0
            val name = normalizeText(eq.nombreComun)
            val official = normalizeText(eq.nombreOficial)
            val idWords = normalizeText(eq.id.replace('_', ' '))
            val yolo = normalizeText(eq.claseYolo.replace('_', ' '))

            if (q.contains(name)) score += 12
            if (q.contains(official)) score += 10
            if (q.contains(idWords)) score += 8
            if (q.contains(yolo)) score += 8
            if (q.contains(normalizeText(eq.fabricante)) && eq.fabricante.length > 3) score += 2
            if (q.contains(normalizeText(eq.modelo)) && eq.modelo.length > 2) score += 2

            val nameWords = name.split(Regex("[^a-z0-9]+")).filter { it.length >= 3 }
            val overlap = nameWords.count { nw -> queryWords.any { qw -> qw.contains(nw) || nw.contains(qw) } }
            score += overlap * 3

            // Bonus si aparecen 2+ palabras clave del nombre
            if (overlap >= 2) score += 4

            if (score > maxScore) {
                maxScore = score
                bestMatch = eq
            }
        }

        // Umbral mínimo para evitar falsos positivos
        return if (maxScore >= 4) bestMatch else null
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
