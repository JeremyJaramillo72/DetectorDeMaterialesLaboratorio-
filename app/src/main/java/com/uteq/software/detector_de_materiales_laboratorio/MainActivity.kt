package com.uteq.software.detector_de_materiales_laboratorio

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.util.Size
import android.widget.LinearLayout
import android.widget.TextView
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
import java.util.Locale
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
    /** Label del equipo elegido por el usuario cuando hay varios a la vez. */
    private var selectedLabel: String? = null
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

        cameraExecutor = Executors.newSingleThreadExecutor()
        detectionCache = EquipmentDetectionCache(this)
        yoloDetector = YoloDetector(this).also { it.detectionCache = detectionCache }
        detectionTracker.detectionCache = detectionCache
        kbRepository = KnowledgeBaseRepository.getInstance(this)
        warmEquipmentInfoCache()

        setupUI()
        updateHUD(emptyList())

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissions()
        }
    }

    private fun warmEquipmentInfoCache() {
        // Precarga fichas en memoria para apertura instantánea
        kbRepository.getAllEquipments().forEach { eq ->
            detectionCache.putEquipmentInfo(eq.id, eq)
        }
    }

    private fun setupUI() {
        // Tocar un recuadro sobre la cámara selecciona ese equipo — igual que
        // tocar su tarjeta en la franja. Ya no abre la ficha directamente: con
        // varios equipos a la vez, seleccionar y consultar son pasos distintos.
        binding.overlayView.onDetectionSelectedListener = { detection ->
            selectEquipment(detection.label)
        }

        binding.btnOpenChat.setOnClickListener {
            // Asistente IA general: sin equipo forzado
            startActivity(Intent(this, ChatActivity::class.java))
        }

        binding.btnQuickDetails.setOnClickListener {
            val label = selectedLabel
            if (label != null) {
                showEquipmentDetails(label)
            } else {
                Toast.makeText(
                    this,
                    getString(R.string.aim_at_equipment),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    /** Cambia el equipo seleccionado y refresca la UI con lo último detectado,
     *  sin esperar al próximo frame de inferencia (toque instantáneo). */
    private fun selectEquipment(label: String) {
        selectedLabel = label
        refreshSelection(latestDetections)
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

            // Aprender solo detecciones muy seguras (evita cachear falsos positivos).
            // Se recorren TODAS las detecciones estables del frame, no solo la
            // primera — cada equipo visible aprende de forma independiente.
            stable.forEach { det ->
                if (det.confidence >= EquipmentDetectionCache.MIN_LEARN_CONFIDENCE) {
                    val eq = detectionCache.getEquipmentInfo(det.label)
                        ?: kbRepository.getEquipmentByClass(det.label)
                        ?: kbRepository.getEquipmentById(det.label)
                    detectionCache.remember(det, eq)
                }
            }

            val frameWidth = bitmap.width
            val frameHeight = bitmap.height

            runOnUiThread {
                if (!isFinishing && !isDestroyed) {
                    binding.overlayView.setResults(stable, frameWidth, frameHeight)
                    updateHUD(stable)
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

    /**
     * El panel de lectura siempre describe al equipo SELECCIONADO, no al de
     * mayor confianza — con un solo equipo detectado ambos coinciden, así que
     * el flujo de un solo equipo no cambia. Altura fija en todos los estados:
     * nunca se oculta ni se agrega una fila, solo cambian texto y color.
     */
    private fun updateHUD(detections: List<DetectionResult>) {
        if (!yoloDetector.isModelLoaded()) {
            selectedLabel = null
            showReading(getString(R.string.model_missing), isAlert = true)
            updateEquipmentStrip(emptyList())
            binding.overlayView.setSelectedLabel(null)
            return
        }
        refreshSelection(detections)
    }

    private fun refreshSelection(detections: List<DetectionResult>) {
        if (detections.isEmpty()) {
            selectedLabel = null
            showReading(getString(R.string.aim_at_equipment))
            updateEquipmentStrip(detections)
            binding.overlayView.setSelectedLabel(null)
            return
        }

        // Si lo seleccionado ya no está en escena (o no había selección), se
        // adopta automáticamente el de mayor confianza — mismo comportamiento
        // de siempre cuando solo hay un equipo, ahora explícito para varios.
        val selected = detections.find { it.label.equals(selectedLabel, ignoreCase = true) }
            ?: detections.maxByOrNull { it.confidence }!!.also { selectedLabel = it.label }

        showReading(selected.displayName, confidence = selected.confidence)
        updateEquipmentStrip(detections)
        binding.overlayView.setSelectedLabel(selectedLabel)
    }

    /**
     * Franja de tarjetas: una por equipo detectado, para responder de un
     * vistazo cuántos hay, cuál es cada uno y cuál está seleccionado. Altura
     * de la franja fija (@dimen/lab_strip_height en el layout); esta función
     * solo reutiliza/crea/quita tarjetas dentro de esa fila.
     */
    private fun updateEquipmentStrip(detections: List<DetectionResult>) {
        val container = binding.stripEquipmentContainer
        val padH = resources.getDimensionPixelSize(R.dimen.lab_space_3)
        val padV = resources.getDimensionPixelSize(R.dimen.lab_space_2)
        val cardMaxWidth = resources.getDimensionPixelSize(R.dimen.lab_card_max_width)
        val cardMargin = resources.getDimensionPixelSize(R.dimen.lab_space_2)

        val remaining = HashMap<String, TextView>()
        for (i in 0 until container.childCount) {
            val child = container.getChildAt(i) as TextView
            remaining[child.tag as String] = child
        }

        detections.forEachIndexed { index, detection ->
            val isSelected = detection.label.equals(selectedLabel, ignoreCase = true)

            val card = remaining.remove(detection.label) ?: TextView(this).also { tv ->
                tv.tag = detection.label
                tv.maxLines = 1
                tv.ellipsize = TextUtils.TruncateAt.END
                tv.maxWidth = cardMaxWidth
                tv.setPadding(padH, padV, padH, padV)
                tv.setTextAppearance(R.style.TextAppearance_Lab_Chip)
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = cardMargin }
                container.addView(tv, params)
            }

            card.text = detection.displayName
            card.setOnClickListener { selectEquipment(detection.label) }
            card.setBackgroundResource(
                if (isSelected) R.drawable.bg_card_selected else R.drawable.bg_chip_outline
            )
            card.setTextColor(
                ContextCompat.getColor(this, if (isSelected) R.color.white else R.color.ink)
            )

            if (container.indexOfChild(card) != index) {
                container.removeView(card)
                container.addView(card, index)
            }
        }

        remaining.values.forEach { container.removeView(it) }
    }

    private fun showReading(
        text: String,
        confidence: Float? = null,
        isAlert: Boolean = false
    ) {
        val hasEquipment = confidence != null

        binding.btnQuickDetails.isEnabled = hasEquipment
        binding.labelEquipment.text = getString(
            if (hasEquipment) R.string.field_equipment else R.string.field_status
        )

        binding.tvEquipmentName.text = text
        binding.tvEquipmentName.setTextColor(
            ContextCompat.getColor(
                this,
                when {
                    isAlert -> R.color.alert
                    hasEquipment -> R.color.ink
                    else -> R.color.ink_soft
                }
            )
        )

        binding.tvConfidence.text = confidence?.let {
            String.format(Locale.getDefault(), "%.1f %%", it * 100f)
        } ?: getString(R.string.confidence_placeholder)
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
