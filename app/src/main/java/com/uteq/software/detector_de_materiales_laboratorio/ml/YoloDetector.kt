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
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.PriorityQueue
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

class YoloDetector(
    private val context: Context,
    private val modelFileName: String = "yolo11_bromatologia.tflite",
    private val fallbackModelName: String = "yolov8_bromatologia.tflite",
    private val labelFileName: String = "labels.txt",
    private val confidenceThreshold: Float = 0.65f,
    private val classMargin: Float = 0.18f,
    private val iouThreshold: Float = 0.50f
) {

    private val TAG = "YoloDetector"
    private var interpreter: Interpreter? = null
    private var loadedModelName: String? = null
    private val labels = ArrayList<String>()
    private val numClasses: Int
        get() = if (labels.isNotEmpty()) labels.size else 8

    /** Caché opcional: equipos ya vistos usan umbral más bajo */
    var detectionCache: EquipmentDetectionCache? = null

    val inputSize = 640

    private var inputBuffer: ByteBuffer? = null
    private var pixelBuffer: IntArray? = null
    private var outputArray: Array<Array<FloatArray>>? = null
    private var isNCHW = false
    private var transposedOutput = false
    private var outputDim1 = 0
    private var outputDim2 = 0

    init {
        loadLabels()
        initInterpreter()
    }

    private fun loadLabels() {
        try {
            context.assets.open(labelFileName).use { inputStream ->
                InputStreamReader(inputStream).readLines().forEach { line ->
                    val cleanLine = line.trim()
                    if (cleanLine.isNotEmpty()) labels.add(cleanLine)
                }
            }
            Log.d(TAG, "Labels cargados (${labels.size}): $labels")
        } catch (e: Exception) {
            e.printStackTrace()
            labels.addAll(
                listOf(
                    "Analizador de Fibra Cruda y Fracciones",
                    "Bomba de Vacio por Recirculacion de Agua",
                    "Destilador por Arrastre de Vapor tipo Kjeldahl",
                    "Destilador de Agua Continuo Metalico",
                    "Destilacion de Nitrogeno y Proteinas",
                    "Microcospio Trinocular",
                    "Sistema de Tratamiento y Desionizacion deAgua",
                    "Viscosimetro Brookfield Modelo DV-E"
                )
            )
        }
    }

    private fun initInterpreter() {
        for (candidate in listOf(modelFileName, fallbackModelName)) {
            try {
                val modelBuffer = loadModelFile(candidate)
                val options = Interpreter.Options().apply {
                    setNumThreads(3)
                }
                val interp = Interpreter(modelBuffer, options)
                interpreter = interp
                loadedModelName = candidate

                val inShape = interp.getInputTensor(0).shape()
                isNCHW = inShape.size == 4 && inShape[1] == 3

                val outShape = interp.getOutputTensor(0).shape()
                if (outShape.size == 3) {
                    outputDim1 = outShape[1]
                    outputDim2 = outShape[2]
                    transposedOutput = outputDim1 <= outputDim2
                    outputArray = Array(1) { Array(outputDim1) { FloatArray(outputDim2) } }
                }

                inputBuffer = ByteBuffer.allocateDirect(1 * 3 * inputSize * inputSize * 4).apply {
                    order(ByteOrder.nativeOrder())
                }
                pixelBuffer = IntArray(inputSize * inputSize)

                Log.d(
                    TAG,
                    "Modelo cargado: $candidate | In: ${inShape.contentToString()} | Out: ${outShape.contentToString()}"
                )
                return
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo cargar $candidate: ${e.message}")
            }
        }
        Log.w(TAG, "Ningún modelo .tflite encontrado en assets")
        interpreter = null
        loadedModelName = null
    }

    private fun loadModelFile(fileName: String): MappedByteBuffer {
        val afd = context.assets.openFd(fileName)
        FileInputStream(afd.fileDescriptor).use { fis ->
            return fis.channel.map(FileChannel.MapMode.READ_ONLY, afd.startOffset, afd.declaredLength)
        }
    }

    fun isModelLoaded(): Boolean = interpreter != null

    fun getLoadedModelName(): String? = loadedModelName

    fun detect(bitmap: Bitmap): List<DetectionResult> {
        val interp = interpreter ?: return emptyList()
        val buffer = inputBuffer ?: return emptyList()
        val pixels = pixelBuffer ?: return emptyList()
        val output = outputArray ?: return emptyList()

        val resized = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)
        try {
            resized.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)
            buffer.rewind()

            if (isNCHW) {
                for (pixel in pixels) buffer.putFloat(((pixel shr 16) and 0xFF) / 255.0f)
                for (pixel in pixels) buffer.putFloat(((pixel shr 8) and 0xFF) / 255.0f)
                for (pixel in pixels) buffer.putFloat((pixel and 0xFF) / 255.0f)
            } else {
                for (pixel in pixels) {
                    buffer.putFloat(((pixel shr 16) and 0xFF) / 255.0f)
                    buffer.putFloat(((pixel shr 8) and 0xFF) / 255.0f)
                    buffer.putFloat((pixel and 0xFF) / 255.0f)
                }
            }

            buffer.rewind()
            try {
                interp.run(buffer, output)
            } catch (e: Exception) {
                Log.e(TAG, "Error en inferencia: ${e.message}")
                return emptyList()
            }

            val raw = ArrayList<DetectionResult>(16)
            val imgW = bitmap.width.toFloat()
            val imgH = bitmap.height.toFloat()
            val data = output[0]

            if (transposedOutput) {
                parseTransposed(data, outputDim2, outputDim1, imgW, imgH, raw)
            } else {
                parseRows(data, outputDim1, outputDim2, imgW, imgH, raw)
            }

            return applyGlobalNms(raw)
        } finally {
            if (resized !== bitmap) resized.recycle()
        }
    }

    private fun parseTransposed(
        output: Array<FloatArray>,
        numAnchors: Int,
        numFeats: Int,
        imgW: Float,
        imgH: Float,
        out: ArrayList<DetectionResult>
    ) {
        for (col in 0 until numAnchors) {
            var best = 0f
            var second = 0f
            var classIdx = -1
            for (c in 0 until numClasses) {
                val featIdx = 4 + c
                if (featIdx >= numFeats) break
                val conf = asProb(output[featIdx][col])
                if (conf > best) {
                    second = best
                    best = conf
                    classIdx = c
                } else if (conf > second) {
                    second = conf
                }
            }
            if (classIdx < 0 || classIdx >= labels.size) continue
            val threshold = detectionCache?.confidenceThresholdFor(classIdx, confidenceThreshold)
                ?: confidenceThreshold
            if (best < threshold) continue
            // Exigir margen claro entre 1.ª y 2.ª clase (anti falso positivo)
            if (best - second < classMargin) continue

            val box = mapBox(output[0][col], output[1][col], output[2][col], output[3][col], imgW, imgH)
                ?: continue
            out.add(
                DetectionResult(
                    boundingBox = box,
                    label = labels[classIdx],
                    displayName = formatDisplayName(labels[classIdx]),
                    confidence = best,
                    classIndex = classIdx
                )
            )
        }
    }

    private fun parseRows(
        output: Array<FloatArray>,
        numAnchors: Int,
        numFeats: Int,
        imgW: Float,
        imgH: Float,
        out: ArrayList<DetectionResult>
    ) {
        for (row in 0 until numAnchors) {
            var best = 0f
            var second = 0f
            var classIdx = -1
            for (c in 0 until numClasses) {
                val featIdx = 4 + c
                if (featIdx >= numFeats) break
                val conf = asProb(output[row][featIdx])
                if (conf > best) {
                    second = best
                    best = conf
                    classIdx = c
                } else if (conf > second) {
                    second = conf
                }
            }
            if (classIdx < 0 || classIdx >= labels.size) continue
            val threshold = detectionCache?.confidenceThresholdFor(classIdx, confidenceThreshold)
                ?: confidenceThreshold
            if (best < threshold) continue
            if (best - second < classMargin) continue

            val box = mapBox(output[row][0], output[row][1], output[row][2], output[row][3], imgW, imgH)
                ?: continue
            out.add(
                DetectionResult(
                    boundingBox = box,
                    label = labels[classIdx],
                    displayName = formatDisplayName(labels[classIdx]),
                    confidence = best,
                    classIndex = classIdx
                )
            )
        }
    }

    /** Ultralytics TFLite puede devolver logits o probabilidades. */
    private fun asProb(x: Float): Float {
        return if (x in 0f..1f) x else (1f / (1f + exp(-x)))
    }

    private fun mapBox(
        cxRaw: Float, cyRaw: Float, wRaw: Float, hRaw: Float,
        imgW: Float, imgH: Float
    ): RectF? {
        val normalized = cxRaw <= 1.05f && cyRaw <= 1.05f && wRaw <= 1.05f && hRaw <= 1.05f
        val cx = if (normalized) cxRaw * imgW else cxRaw * imgW / inputSize
        val cy = if (normalized) cyRaw * imgH else cyRaw * imgH / inputSize
        val w = if (normalized) wRaw * imgW else wRaw * imgW / inputSize
        val h = if (normalized) hRaw * imgH else hRaw * imgH / inputSize

        val areaRatio = (w * h) / (imgW * imgH)
        if ((w >= 0.90f * imgW && h >= 0.88f * imgH) || areaRatio >= 0.82f) return null
        if (w < 12f || h < 12f) return null

        return RectF(
            max(0f, cx - w / 2f),
            max(0f, cy - h / 2f),
            min(imgW, cx + w / 2f),
            min(imgH, cy + h / 2f)
        )
    }

    private fun applyGlobalNms(detections: List<DetectionResult>): List<DetectionResult> {
        if (detections.isEmpty()) return emptyList()

        val pq = PriorityQueue<DetectionResult>(detections.size) { a, b ->
            b.confidence.compareTo(a.confidence)
        }
        pq.addAll(detections)

        val result = ArrayList<DetectionResult>(2)
        while (pq.isNotEmpty() && result.size < 1) {
            val best = pq.poll() ?: break
            val overlaps = result.any { isDuplicateOrContained(it.boundingBox, best.boundingBox) }
            if (!overlaps) result.add(best)
        }
        return result
    }

    private fun isDuplicateOrContained(a: RectF, b: RectF): Boolean {
        if (calculateIoU(a, b) >= iouThreshold) return true
        val interLeft = max(a.left, b.left)
        val interTop = max(a.top, b.top)
        val interRight = min(a.right, b.right)
        val interBottom = min(a.bottom, b.bottom)
        val interArea = max(0f, interRight - interLeft) * max(0f, interBottom - interTop)
        val minArea = min(
            (a.right - a.left) * (a.bottom - a.top),
            (b.right - b.left) * (b.bottom - b.top)
        )
        return minArea > 0f && interArea / minArea >= 0.65f
    }

    private fun calculateIoU(a: RectF, b: RectF): Float {
        val interLeft = max(a.left, b.left)
        val interTop = max(a.top, b.top)
        val interRight = min(a.right, b.right)
        val interBottom = min(a.bottom, b.bottom)
        val inter = max(0f, interRight - interLeft) * max(0f, interBottom - interTop)
        val union = (a.right - a.left) * (a.bottom - a.top) +
            (b.right - b.left) * (b.bottom - b.top) - inter
        return if (union > 0) inter / union else 0f
    }

    private fun formatDisplayName(rawLabel: String): String {
        return rawLabel
            .replace("Microcospio", "Microscopio")
            .replace("deAgua", "de Agua")
            .replace("MetalicoMetalico", "Metálico")
            .trim()
    }

    fun close() {
        interpreter?.close()
        interpreter = null
        inputBuffer = null
        pixelBuffer = null
        outputArray = null
    }
}
