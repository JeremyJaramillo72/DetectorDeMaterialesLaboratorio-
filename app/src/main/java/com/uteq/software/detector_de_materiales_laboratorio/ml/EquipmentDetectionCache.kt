package com.uteq.software.detector_de_materiales_laboratorio.ml

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.uteq.software.detector_de_materiales_laboratorio.model.DetectionResult
import com.uteq.software.detector_de_materiales_laboratorio.model.EquipmentData

/**
 * Caché de equipos YA confirmados con alta confianza.
 * Solo acelera un poco la re-detección; NUNCA inventa detecciones de objetos random.
 */
class EquipmentDetectionCache(context: Context) {

    data class CachedEquipment(
        val label: String,
        val displayName: String,
        val classIndex: Int,
        val equipmentId: String? = null,
        val hitCount: Int = 1,
        val lastConfidence: Float = 0f,
        val lastSeenMs: Long = System.currentTimeMillis()
    )

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    private val memory = LinkedHashMap<String, CachedEquipment>(16, 0.75f, true)
    private val equipmentInfoCache = HashMap<String, EquipmentData>()

    init {
        loadFromDisk()
    }

    fun isKnown(label: String): Boolean = memory.containsKey(normalize(label))

    fun isKnownClass(classIndex: Int): Boolean =
        memory.values.any { it.classIndex == classIndex }

    fun getCachedClassIndices(): Set<Int> =
        memory.values.map { it.classIndex }.toSet()

    fun get(label: String): CachedEquipment? = memory[normalize(label)]

    fun getEquipmentInfo(key: String): EquipmentData? =
        equipmentInfoCache[normalize(key)]

    fun putEquipmentInfo(key: String, data: EquipmentData) {
        equipmentInfoCache[normalize(key)] = data
        equipmentInfoCache[normalize(data.id)] = data
        equipmentInfoCache[normalize(data.claseYolo)] = data
        equipmentInfoCache[normalize(data.nombreComun)] = data
    }

    fun remember(detection: DetectionResult, equipment: EquipmentData? = null) {
        // Solo aprende detecciones muy seguras (evita cachear falsos positivos)
        if (detection.confidence < MIN_LEARN_CONFIDENCE) return

        val key = normalize(detection.label)
        val previous = memory[key]
        memory[key] = CachedEquipment(
            label = detection.label,
            displayName = detection.displayName,
            classIndex = detection.classIndex,
            equipmentId = equipment?.id ?: previous?.equipmentId,
            hitCount = (previous?.hitCount ?: 0) + 1,
            lastConfidence = detection.confidence,
            lastSeenMs = System.currentTimeMillis()
        )
        equipment?.let { putEquipmentInfo(detection.label, it) }
        persistToDisk()
    }

    /**
     * Solo mejora el displayName. NO infla la confianza (eso causaba falsos positivos).
     */
    fun boostIfCached(raw: List<DetectionResult>): List<DetectionResult> {
        if (raw.isEmpty() || memory.isEmpty()) return raw
        return raw.map { det ->
            val cached = memory[normalize(det.label)]
            if (cached != null && det.confidence >= CACHED_ACCEPT_THRESHOLD) {
                det.copy(displayName = cached.displayName.ifBlank { det.displayName })
            } else {
                det
            }
        }.sortedByDescending { it.confidence }
    }

    fun findBestCachedCandidate(raw: List<DetectionResult>): DetectionResult? {
        if (raw.isEmpty()) return null
        return raw
            .filter { isKnown(it.label) && it.confidence >= CACHED_ACCEPT_THRESHOLD }
            .maxByOrNull { it.confidence }
    }

    fun confidenceThresholdFor(classIndex: Int, defaultThreshold: Float): Float {
        // Re-detección: umbral apenas más bajo, nunca permisivo
        return if (isKnownClass(classIndex)) {
            maxOf(CACHED_ACCEPT_THRESHOLD, defaultThreshold - 0.08f)
        } else {
            defaultThreshold
        }
    }

    fun clear() {
        memory.clear()
        equipmentInfoCache.clear()
        prefs.edit().remove(KEY_CACHE).apply()
    }

    private fun loadFromDisk() {
        val json = prefs.getString(KEY_CACHE, null) ?: return
        try {
            val type = object : TypeToken<List<CachedEquipment>>() {}.type
            val list: List<CachedEquipment> = gson.fromJson(json, type) ?: return
            // Descarta entradas viejas aprendidas con umbral bajo (falsos positivos)
            list.filter { it.lastConfidence >= MIN_LEARN_CONFIDENCE }
                .forEach { memory[normalize(it.label)] = it }
        } catch (_: Exception) {
            prefs.edit().remove(KEY_CACHE).apply()
        }
    }

    private fun persistToDisk() {
        prefs.edit().putString(KEY_CACHE, gson.toJson(memory.values.toList())).apply()
    }

    private fun normalize(value: String): String = value.lowercase().trim()

    companion object {
        // v2 invalida caché corrupta de umbrales bajos anteriores
        private const val PREFS_NAME = "equipment_detection_cache_v2"
        private const val KEY_CACHE = "known_equipments_v2"
        /** Solo se guarda en caché si fue una detección fuerte */
        const val MIN_LEARN_CONFIDENCE = 0.72f
        /** Re-detección: sigue exigiendo confianza alta */
        const val CACHED_ACCEPT_THRESHOLD = 0.58f
    }
}
