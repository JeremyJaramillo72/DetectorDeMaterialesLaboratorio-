package com.uteq.software.detector_de_materiales_laboratorio.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import com.uteq.software.detector_de_materiales_laboratorio.model.DetectionResult
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.util.PriorityQueue
import kotlin.math.max
import kotlin.math.min

class YoloDetector(
    private val context: Context,
    private val modelFileName: String = "yolo11_bromatologia.tflite",
    private val fallbackModelName: String = "yolov8_bromatologia.tflite",
    private val labelFileName: String = "labels.txt",
    private val confidenceThreshold: Float = 0.48f, // Umbral equilibrado al 48% para detectar equipos reales
    private val iouThreshold: Float = 0.50f        // Umbral IoU estándar para suprimir solapamientos
) {

    private val TAG = "YoloDetector"
    private var interpreter: Interpreter? = null
    private val labels = ArrayList<String>()
    private val numClasses: Int
        get() = if (labels.isNotEmpty()) labels.size else 8

    val inputSize = 640

    init {
        loadLabels()
        initInterpreter()
    }

    private fun loadLabels() {
        try {
            context.assets.open(labelFileName).use { inputStream ->
                val reader = InputStreamReader(inputStream)
                reader.readLines().forEach { line ->
                    val cleanLine = line.trim()
                    if (cleanLine.isNotEmpty()) {
                        labels.add(cleanLine)
                    }
                }
            }
            Log.d(TAG, "Labels cargados (${labels.size}): $labels")
        } catch (e: Exception) {
            e.printStackTrace()
            labels.addAll(
                listOf(
                    "Analizador de Fibra Cruda y Fracciones", "Bomba de Vacio por Recirculacion de Agua",
                    "Destilador por Arrastre de Vapor tipo Kjeldahl", "Destilador de Agua Continuo Metalico",
                    "Destilacion de Nitrogeno y Proteinas", "Microcospio Trinocular",
                    "Sistema de Tratamiento y Desionizacion deAgua", "Viscosimetro Brookfield Modelo DV-E"
                )
            )
        }
    }

    private fun initInterpreter() {
        val candidates = listOf(modelFileName, fallbackModelName)
        for (candidate in candidates) {
            try {
                val assetFileDescriptor = context.assets.openFd(candidate)
                val fileInputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
                val fileChannel = fileInputStream.channel
                val startOffset = assetFileDescriptor.startOffset
                val declaredLength = assetFileDescriptor.declaredLength
                val modelBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)

                val options = Interpreter.Options().apply {
                    setNumThreads(4)
                }
                val interp = Interpreter(modelBuffer, options)
                interpreter = interp

                val inTensor = interp.getInputTensor(0)
                val outTensor = interp.getOutputTensor(0)
                Log.d(TAG, "✅ Modelo cargado: $candidate | InShape: ${inTensor.shape().contentToString()} | OutShape: ${outTensor.shape().contentToString()}")
                return
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo cargar $candidate: ${e.message}")
            }
        }
        Log.w(TAG, "⚠️ Ningún archivo de modelo encontrado en assets. Se utilizará modo demostración interactiva.")
        interpreter = null
    }

    fun isModelLoaded(): Boolean = interpreter != null
    fun getLoadedModelName(): String? = if (interpreter != null) modelFileName.removeSuffix(".tflite") else null

    fun detect(bitmap: Bitmap): List<DetectionResult> {
        val interp = interpreter ?: return generateDemoDetections(bitmap.width, bitmap.height)

        val inTensor = interp.getInputTensor(0)
        val inShape = inTensor.shape()
        val isNCHW = (inShape.size == 4 && inShape[1] == 3)

        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)
        val inputBuffer = ByteBuffer.allocateDirect(1 * 3 * inputSize * inputSize * 4)
        inputBuffer.order(ByteOrder.nativeOrder())

        val intValues = IntArray(inputSize * inputSize)
        resizedBitmap.getPixels(intValues, 0, inputSize, 0, 0, inputSize, inputSize)

        if (isNCHW) {
            // LiteRT PyTorch NCHW Format: RRR... GGG... BBB...
            for (pixel in intValues) {
                inputBuffer.putFloat(((pixel shr 16) and 0xFF) / 255.0f)
            }
            for (pixel in intValues) {
                inputBuffer.putFloat(((pixel shr 8) and 0xFF) / 255.0f)
            }
            for (pixel in intValues) {
                inputBuffer.putFloat((pixel and 0xFF) / 255.0f)
            }
        } else {
            // Standard NHWC Format: RGB RGB RGB...
            for (pixel in intValues) {
                inputBuffer.putFloat(((pixel shr 16) and 0xFF) / 255.0f)
                inputBuffer.putFloat(((pixel shr 8) and 0xFF) / 255.0f)
                inputBuffer.putFloat((pixel and 0xFF) / 255.0f)
            }
        }

        val outTensor = interp.getOutputTensor(0)
        val shape = outTensor.shape() // Ej: [1, 24, 8400] o [1, 8400, 24]

        val rawDetections = ArrayList<DetectionResult>()
        val imgWidth = bitmap.width.toFloat()
        val imgHeight = bitmap.height.toFloat()

        if (shape.size == 3) {
            val dim1 = shape[1]
            val dim2 = shape[2]

            if (dim1 <= dim2) {
                // Formato [1, 24, 8400]
                val outputArray = Array(1) { Array(dim1) { FloatArray(dim2) } }
                try {
                    inputBuffer.rewind()
                    interp.run(inputBuffer, outputArray)
                } catch (e: Exception) {
                    Log.e(TAG, "Error en inferencia [1, C, N]: ${e.message}")
                    return emptyList()
                }

                val output = outputArray[0]
                val numAnchors = dim2
                val numFeats = dim1

                for (col in 0 until numAnchors) {
                    var maxConfidence = 0.0f
                    var classIdx = -1

                    for (c in 0 until numClasses) {
                        val featIdx = 4 + c
                        if (featIdx < numFeats) {
                            val conf = output[featIdx][col]
                            if (conf > maxConfidence) {
                                maxConfidence = conf
                                classIdx = c
                            }
                        }
                    }

                    if (maxConfidence >= confidenceThreshold && classIdx in 0 until labels.size) {
                        val cxRaw = output[0][col]
                        val cyRaw = output[1][col]
                        val wRaw = output[2][col]
                        val hRaw = output[3][col]

                        val isNormalized = (cxRaw <= 1.05f && cyRaw <= 1.05f && wRaw <= 1.05f && hRaw <= 1.05f)
                        val cx = if (isNormalized) cxRaw * imgWidth else cxRaw * imgWidth / inputSize
                        val cy = if (isNormalized) cyRaw * imgHeight else cyRaw * imgHeight / inputSize
                        val w = if (isNormalized) wRaw * imgWidth else wRaw * imgWidth / inputSize
                        val h = if (isNormalized) hRaw * imgHeight else hRaw * imgHeight / inputSize

                        // Descartar cajas que cubren toda la pantalla (artefactos de monitores o fondo)
                        val areaRatio = (w * h) / (imgWidth * imgHeight)
                        if ((w >= 0.88f * imgWidth && h >= 0.85f * imgHeight) || areaRatio >= 0.78f) {
                            continue
                        }

                        val left = max(0.0f, cx - w / 2.0f)
                        val top = max(0.0f, cy - h / 2.0f)
                        val right = min(imgWidth, cx + w / 2.0f)
                        val bottom = min(imgHeight, cy + h / 2.0f)

                        val label = labels[classIdx]
                        rawDetections.add(
                            DetectionResult(
                                boundingBox = RectF(left, top, right, bottom),
                                label = label,
                                displayName = formatDisplayName(label),
                                confidence = maxConfidence,
                                classIndex = classIdx
                            )
                        )
                    }
                }
            } else {
                // Formato [1, 8400, 24]
                val outputArray = Array(1) { Array(dim1) { FloatArray(dim2) } }
                try {
                    inputBuffer.rewind()
                    interp.run(inputBuffer, outputArray)
                } catch (e: Exception) {
                    Log.e(TAG, "Error en inferencia [1, N, C]: ${e.message}")
                    return emptyList()
                }

                val output = outputArray[0]
                val numAnchors = dim1
                val numFeats = dim2

                for (row in 0 until numAnchors) {
                    var maxConfidence = 0.0f
                    var classIdx = -1

                    for (c in 0 until numClasses) {
                        val featIdx = 4 + c
                        if (featIdx < numFeats) {
                            val conf = output[row][featIdx]
                            if (conf > maxConfidence) {
                                maxConfidence = conf
                                classIdx = c
                            }
                        }
                    }

                    if (maxConfidence >= confidenceThreshold && classIdx in 0 until labels.size) {
                        val cxRaw = output[row][0]
                        val cyRaw = output[row][1]
                        val wRaw = output[row][2]
                        val hRaw = output[row][3]

                        val isNormalized = (cxRaw <= 1.05f && cyRaw <= 1.05f && wRaw <= 1.05f && hRaw <= 1.05f)
                        val cx = if (isNormalized) cxRaw * imgWidth else cxRaw * imgWidth / inputSize
                        val cy = if (isNormalized) cyRaw * imgHeight else cyRaw * imgHeight / inputSize
                        val w = if (isNormalized) wRaw * imgWidth else wRaw * imgWidth / inputSize
                        val h = if (isNormalized) hRaw * imgHeight else hRaw * imgHeight / inputSize

                        // Descartar cajas que cubren toda la pantalla (artefactos de monitores o fondo)
                        val areaRatio = (w * h) / (imgWidth * imgHeight)
                        if ((w >= 0.88f * imgWidth && h >= 0.85f * imgHeight) || areaRatio >= 0.78f) {
                            continue
                        }

                        val left = max(0.0f, cx - w / 2.0f)
                        val top = max(0.0f, cy - h / 2.0f)
                        val right = min(imgWidth, cx + w / 2.0f)
                        val bottom = min(imgHeight, cy + h / 2.0f)

                        val label = labels[classIdx]
                        rawDetections.add(
                            DetectionResult(
                                boundingBox = RectF(left, top, right, bottom),
                                label = label,
                                displayName = formatDisplayName(label),
                                confidence = maxConfidence,
                                classIndex = classIdx
                            )
                        )
                    }
                }
            }
        }

        return applyGlobalNMS(rawDetections)
    }

    /**
     * NMS Global / Agnóstico de Clase:
     * Suprime cajas duplicadas o contenedoras (cuando una caja encierra a otra o tienen IoU >= 0.50),
     * garantizando que solo quede el objeto real enfocado.
     */
    private fun applyGlobalNMS(detections: List<DetectionResult>): List<DetectionResult> {
        if (detections.isEmpty()) return emptyList()

        val pq = PriorityQueue<DetectionResult>(detections.size) { a, b ->
            b.confidence.compareTo(a.confidence)
        }
        pq.addAll(detections)

        val result = ArrayList<DetectionResult>()
        val best = pq.poll() ?: return emptyList()
        result.add(best)

        val minSecondaryConf = max(confidenceThreshold, best.confidence * 0.75f)

        while (pq.isNotEmpty()) {
            val next = pq.poll() ?: break
            if (next.confidence < minSecondaryConf) continue

            val overlaps = result.any { isDuplicateOrContained(it.boundingBox, next.boundingBox) }
            if (!overlaps) {
                result.add(next)
                if (result.size >= 2) break
            }
        }
        return result
    }

    private fun isDuplicateOrContained(a: RectF, b: RectF): Boolean {
        val iou = calculateIoU(a, b)
        if (iou >= iouThreshold) return true

        // Comprobación de contención asimétrica (evita que un marco exterior encierre a un objeto interior)
        val interLeft = max(a.left, b.left)
        val interTop = max(a.top, b.top)
        val interRight = min(a.right, b.right)
        val interBottom = min(a.bottom, b.bottom)
        val interArea = max(0f, interRight - interLeft) * max(0f, interBottom - interTop)

        val areaA = (a.right - a.left) * (a.bottom - a.top)
        val areaB = (b.right - b.left) * (b.bottom - b.top)
        val minArea = min(areaA, areaB)

        return (minArea > 0f && (interArea / minArea) >= 0.65f)
    }

    private fun calculateIoU(a: RectF, b: RectF): Float {
        val intersectionLeft = max(a.left, b.left)
        val intersectionTop = max(a.top, b.top)
        val intersectionRight = min(a.right, b.right)
        val intersectionBottom = min(a.bottom, b.bottom)

        val intersectionArea = max(0f, intersectionRight - intersectionLeft) *
                max(0f, intersectionBottom - intersectionTop)

        val areaA = (a.right - a.left) * (a.bottom - a.top)
        val areaB = (b.right - b.left) * (b.bottom - b.top)
        val unionArea = areaA + areaB - intersectionArea

        return if (unionArea > 0) intersectionArea / unionArea else 0f
    }

    private fun formatDisplayName(rawLabel: String): String {
        return rawLabel.replace("_", " ").replace("MetalicoMetalico", "Metálico").trim()
    }

    private fun generateDemoDetections(width: Int, height: Int): List<DetectionResult> {
        val w = width.toFloat()
        val h = height.toFloat()
        val box = RectF(w * 0.15f, h * 0.20f, w * 0.85f, h * 0.75f)
        return listOf(
            DetectionResult(
                boundingBox = box,
                label = "Destilador por Arrastre de Vapor tipo Kjeldahl",
                displayName = "Destilador por Arrastre de Vapor tipo Kjeldahl",
                confidence = 0.954f,
                classIndex = 4
            )
        )
    }

    fun close() {
        interpreter?.close()
        interpreter = null
    }
}
