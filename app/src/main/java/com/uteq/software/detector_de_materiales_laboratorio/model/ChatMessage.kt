package com.uteq.software.detector_de_materiales_laboratorio.model

import java.io.Serializable

data class ChatMessage(
    val text: String,
    val isBot: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val equipmentId: String? = null,
    val citations: List<String> = emptyList(),
    val eppRequired: List<String> = emptyList(),
    val risks: List<String> = emptyList()
) : Serializable
