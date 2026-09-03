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
    private val confidenceThreshold: Float = 0.35f, // Umbral calibrado al 35% para máxima respuesta y precisión
    private val iouThreshold: Float = 0.40f        // Umbral IoU para suprimir cajas duplicadas
) {

    private val TAG = "YoloDetector"
    private var interpreter: Interpreter? = null
    private val labels = ArrayList<String>()
    private val numClasses: Int
        get() = if (labels.isNotEmpty()) labels.size else 26

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
                    "Agitador de Tubos - Mezclador Vortex", "Agua Destilada Desmineralizada", "Analizador de Fibra Cruda y Fracciones",
                    "Balanza Analitica de la Serie Ohaus Pioneer", "Bateria de Desionizacion y Tratamiento de Agua", "Bomba Calorimetrica de Oxigeno",
                    "Bomba de Vacio de Membrana - Diafragma Portatil", "Bomba de Vacio por Recirculacion de Agua", "Cabina de Flujo Laminar",
                    "Campana de Extraccion de Gases de Laboratorio", "Destilacion por Arrastre de Vapor", "Destilador por Arrastre de Vapor tipo Kjeldahl",
                    "Destilador de Agua Continuo Metalico", "Estufa - Horno Universal de Secado", "Extractor de Laboratorio Automatico para Analisis de Grasas y Aceites",
                    "Molino Ciclonico de Muestras Bromatologicas", "Mufla Electrica de Laboratorio", "Placa Calefactora con Agitador MagneticoMetalico",
                    "Potenciometro - pH-metro Digital de Mesa", "Refractometro Digital Abbe de Mesa", "Sistema de Tratamiento y Desionizacion deAgua",
                    "Soporte Giratorio para Pipetas de Vidrio", "Stufa de Laboratorio de Conveccion", "Termometro Digital Parr Model 6775",
                    "Unidad de Destilacion Kjeldahl", "Viscosimetro Rotacional Digital Brookfield"
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
     * Si 2 clases diferentes predicen cajas sobre el mismo objeto (IoU >= 0.40),
     * se conserva ÚNICAMENTE la que tiene mayor confianza, eliminando falsos positivos cruzados.
     */
    private fun applyGlobalNMS(detections: List<DetectionResult>): List<DetectionResult> {
        if (detections.isEmpty()) return emptyList()

        val result = ArrayList<DetectionResult>()
        val pq = PriorityQueue<DetectionResult>(detections.size) { a, b ->
            b.confidence.compareTo(a.confidence)
        }
        pq.addAll(detections)

        while (pq.isNotEmpty()) {
            val best = pq.poll() ?: break
            result.add(best)

            val it = pq.iterator()
            while (it.hasNext()) {
                val next = it.next()
                if (calculateIoU(best.boundingBox, next.boundingBox) >= iouThreshold) {
                    it.remove()
                }
            }
        }
        return result
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
                label = "destilador_kjeldahl",
                displayName = "Unidad Kjeldahl Selecta",
                confidence = 0.954f,
                classIndex = 0
            )
        )
    }

    fun close() {
        interpreter?.close()
        interpreter = null
    }
}
