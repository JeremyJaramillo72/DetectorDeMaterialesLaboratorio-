package com.uteq.software.detector_de_materiales_laboratorio

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Outline
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.uteq.software.detector_de_materiales_laboratorio.data.KnowledgeBaseRepository
import com.uteq.software.detector_de_materiales_laboratorio.databinding.ActivityMainBinding
import com.uteq.software.detector_de_materiales_laboratorio.ml.DetectionTracker
import com.uteq.software.detector_de_materiales_laboratorio.ml.EquipmentDetectionCache
import com.uteq.software.detector_de_materiales_laboratorio.ml.YoloDetector
import com.uteq.software.detector_de_materiales_laboratorio.model.DetectionResult
import com.uteq.software.detector_de_materiales_laboratorio.ui.EquipmentBottomSheetDialog
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : AppCompatActivity() {

    private val TAG = "MainActivity"
    private lateinit var binding: ActivityMainBinding
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var yoloDetector: YoloDetector
    private lateinit var kbRepository: KnowledgeBaseRepository
    private lateinit var detectionCache: EquipmentDetectionCache
    private val detectionTracker = DetectionTracker(
        holdMs = 600L,
        switchVotesNeeded = 3,
        confirmVotesNeeded = 2,
        classSwitchMargin = 0.18f,
        minPublishConfidence = 0.60f
    )

    private var latestDetections: List<DetectionResult> = emptyList()
    private val isProcessing = AtomicBoolean(false)
    private var imageAnalyzer: ImageAnalysis? = null
    private var lastInferenceTimeMs = 0L

    private val activityResultLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            var permissionGranted = true
            permissions.entries.forEach {
                if (it.key in REQUIRED_PERMISSIONS && !it.value) {
                    permissionGranted = false
                }
            }
            if (!permissionGranted) {
                Toast.makeText(
                    this,
                    "Permiso de cámara requerido para escanear equipos del laboratorio.",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                startCamera()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupCameraFrameClip()

        cameraExecutor = Executors.newSingleThreadExecutor()
        detectionCache = EquipmentDetectionCache(this)
        yoloDetector = YoloDetector(this).also { it.detectionCache = detectionCache }
        detectionTracker.detectionCache = detectionCache
        kbRepository = KnowledgeBaseRepository.getInstance(this)
        warmEquipmentInfoCache()

        setupUI()
        updateModelBadge()

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissions()
        }
    }

    private fun setupCameraFrameClip() {
        binding.cameraFrame.post {
            val radius = 18f * resources.displayMetrics.density
            binding.cameraFrame.outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, radius)
                }
            }
            binding.cameraFrame.clipToOutline = true
        }
    }

    private fun warmEquipmentInfoCache() {
        // Precarga fichas en memoria para apertura instantánea
        kbRepository.getAllEquipments().forEach { eq ->
            detectionCache.putEquipmentInfo(eq.id, eq)
        }
    }

    private fun setupUI() {
        binding.overlayView.onDetectionSelectedListener = { detection ->
            showEquipmentDetails(detection.label)
        }

        binding.btnOpenChat.setOnClickListener {
            // Asistente IA general: sin equipo forzado
            startActivity(Intent(this, ChatActivity::class.java))
        }

        binding.btnQuickDetails.setOnClickListener {
            val topDetection = latestDetections.maxByOrNull { it.confidence }
            if (topDetection != null) {
                showEquipmentDetails(topDetection.label)
            } else {
                Toast.makeText(
                    this,
                    "Apunta la cámara a un equipo dentro del recuadro verde.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun updateModelBadge() {
        if (yoloDetector.isModelLoaded()) {
            val modelName = yoloDetector.getLoadedModelName() ?: "YOLO"
            binding.tvModelBadge.text = "$modelName • Activo"
            binding.tvModelBadge.setTextColor(ContextCompat.getColor(this, R.color.uteq_accent))
        } else {
            binding.tvModelBadge.text = "Sin modelo .tflite"
            binding.tvModelBadge.setTextColor(ContextCompat.getColor(this, R.color.badge_epp))
        }
    }

    private fun showEquipmentDetails(yoloClass: String) {
        val eq = detectionCache.getEquipmentInfo(yoloClass)
            ?: kbRepository.getEquipmentByClass(yoloClass)
            ?: kbRepository.getEquipmentById(yoloClass)
        if (eq != null) {
            detectionCache.putEquipmentInfo(yoloClass, eq)
            val dialog = EquipmentBottomSheetDialog.newInstance(eq)
            dialog.show(supportFragmentManager, "EquipmentDetail")
        } else {
            Toast.makeText(this, "Equipo detectado: $yoloClass", Toast.LENGTH_SHORT).show()
        }
    }

    private fun allPermissionsGranted() = ContextCompat.checkSelfPermission(
        baseContext, Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED

    private fun requestPermissions() {
        activityResultLauncher.launch(REQUIRED_PERMISSIONS)
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
                }

            val analysisResolution = ResolutionSelector.Builder()
                .setResolutionStrategy(
                    ResolutionStrategy(
                        Size(ANALYSIS_WIDTH, ANALYSIS_HEIGHT),
                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                    )
                )
                .build()

            val analyzer = ImageAnalysis.Builder()
                .setResolutionSelector(analysisResolution)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor) { imageProxy ->
                        processImageProxy(imageProxy)
                    }
                }

            imageAnalyzer = analyzer

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analyzer
                )
            } catch (exc: Exception) {
                Log.e(TAG, "Fallo al vincular la cámara", exc)
                runOnUiThread {
                    Toast.makeText(this, "No se pudo iniciar la cámara: ${exc.message}", Toast.LENGTH_LONG)
                        .show()
                }
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun processImageProxy(imageProxy: ImageProxy) {
        val now = System.currentTimeMillis()
        val interval = if (detectionCache.getCachedClassIndices().isNotEmpty()) {
            CACHED_INFERENCE_INTERVAL_MS
        } else {
            INFERENCE_INTERVAL_MS
        }
        if (now - lastInferenceTimeMs < interval) {
            imageProxy.close()
            return
        }

        if (!isProcessing.compareAndSet(false, true)) {
            imageProxy.close()
            return
        }

        var rawBitmap: Bitmap? = null
        var bitmap: Bitmap? = null

        try {
            rawBitmap = imageProxy.toBitmap()
            bitmap = rotateBitmapIfNeeded(rawBitmap, imageProxy.imageInfo.rotationDegrees)

            val rawDetections = yoloDetector.detect(bitmap)
            val stable = detectionTracker.update(
                incoming = rawDetections,
                frameWidth = bitmap.width,
                frameHeight = bitmap.height,
                nowMs = now
            )
            latestDetections = stable
            lastInferenceTimeMs = now

            // Aprender solo detecciones muy seguras (evita cachear falsos positivos)
            stable.firstOrNull()?.let { det ->
                if (det.confidence >= EquipmentDetectionCache.MIN_LEARN_CONFIDENCE) {
                    val eq = detectionCache.getEquipmentInfo(det.label)
                        ?: kbRepository.getEquipmentByClass(det.label)
                        ?: kbRepository.getEquipmentById(det.label)
                    detectionCache.remember(det, eq)
                }
            }

            val frameWidth = bitmap.width
            val frameHeight = bitmap.height
            val fromCache = stable.firstOrNull()?.let { detectionCache.isKnown(it.label) } == true

            runOnUiThread {
                if (!isFinishing && !isDestroyed) {
                    binding.overlayView.setResults(stable, frameWidth, frameHeight)
                    updateHUD(stable, fromCache)
                }
            }
        } catch (oom: OutOfMemoryError) {
            Log.e(TAG, "Memoria agotada procesando frame", oom)
            System.gc()
        } catch (e: Exception) {
            Log.e(TAG, "Error en processImageProxy: ${e.message}", e)
        } finally {
            if (bitmap != null && bitmap !== rawBitmap) bitmap.recycle()
            rawBitmap?.recycle()
            isProcessing.set(false)
            imageProxy.close()
        }
    }

    private fun updateHUD(detections: List<DetectionResult>, fromCache: Boolean = false) {
        when {
            !yoloDetector.isModelLoaded() -> {
                binding.tvStatus.text = "Falta el modelo: copia yolo11_bromatologia.tflite a assets/"
                binding.tvDockEquipmentName.text = "Modelo YOLO no cargado"
                binding.tvDockConfidence.text = "Sin modelo"
                binding.tvDockConfidence.setBackgroundResource(R.drawable.bg_chip_epp)
                binding.tvDockConfidence.setTextColor(ContextCompat.getColor(this, R.color.badge_epp))
            }
            detections.isEmpty() -> {
                binding.tvStatus.text = "Enfoca un equipo de laboratorio dentro del recuadro"
                binding.tvDockEquipmentName.text = "Escaneando área de laboratorio..."
                binding.tvDockConfidence.text = "En espera"
                binding.tvDockConfidence.setBackgroundResource(R.drawable.bg_badge_live)
                binding.tvDockConfidence.setTextColor(ContextCompat.getColor(this, R.color.neon_cyan))
            }
            else -> {
                val top = detections.maxByOrNull { it.confidence } ?: return
                val confPercent = (top.confidence * 100).toInt()
                binding.tvStatus.text = if (fromCache) {
                    "Detectado (caché) • Toca el recuadro para ver detalles"
                } else {
                    "Equipo detectado • Toca el recuadro para ver detalles"
                }
                binding.tvDockEquipmentName.text = top.displayName
                binding.tvDockConfidence.text = if (fromCache) {
                    "$confPercent% • cache"
                } else {
                    "$confPercent% match"
                }
                binding.tvDockConfidence.setBackgroundResource(R.drawable.bg_badge_model)
                binding.tvDockConfidence.setTextColor(ContextCompat.getColor(this, R.color.neon_emerald))
            }
        }
    }

    private fun rotateBitmapIfNeeded(bitmap: Bitmap, rotationDegrees: Int): Bitmap {
        if (rotationDegrees == 0) return bitmap
        val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    override fun onStop() {
        super.onStop()
        imageAnalyzer?.clearAnalyzer()
    }

    override fun onStart() {
        super.onStart()
        if (allPermissionsGranted() && imageAnalyzer != null) {
            imageAnalyzer?.setAnalyzer(cameraExecutor) { imageProxy ->
                processImageProxy(imageProxy)
            }
        }
    }

    override fun onDestroy() {
        imageAnalyzer?.clearAnalyzer()
        imageAnalyzer = null
        detectionTracker.clear()
        super.onDestroy()
        cameraExecutor.shutdown()
        yoloDetector.close()
    }

    companion object {
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
        private const val ANALYSIS_WIDTH = 640
        private const val ANALYSIS_HEIGHT = 480
        /** ~6–7 FPS primera detección */
        private const val INFERENCE_INTERVAL_MS = 150L
        /** ~10 FPS cuando ya hay equipos en caché */
        private const val CACHED_INFERENCE_INTERVAL_MS = 100L
    }
}
