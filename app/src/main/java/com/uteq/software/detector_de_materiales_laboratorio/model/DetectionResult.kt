package com.uteq.software.detector_de_materiales_laboratorio.model

import android.graphics.RectF

data class DetectionResult(
    val boundingBox: RectF,
    val label: String,
    val displayName: String,
    val confidence: Float,
    val classIndex: Int
)
