package com.uteq.software.detector_de_materiales_laboratorio

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.uteq.software.detector_de_materiales_laboratorio.data.KnowledgeBaseRepository
import com.uteq.software.detector_de_materiales_laboratorio.databinding.ActivityMainBinding
import com.uteq.software.detector_de_materiales_laboratorio.ml.YoloDetector
import com.uteq.software.detector_de_materiales_laboratorio.model.DetectionResult
import com.uteq.software.detector_de_materiales_laboratorio.ui.EquipmentBottomSheetDialog
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var yoloDetector: YoloDetector
    private lateinit var kbRepository: KnowledgeBaseRepository

    private var latestDetections: List<DetectionResult> = emptyList()

    private val cameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                startCamera()
            } else {
                Toast.makeText(
                    this,
                    R.string.camera_permission_required,
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        cameraExecutor = Executors.newSingleThreadExecutor()
        yoloDetector = YoloDetector(this)
        kbRepository = KnowledgeBaseRepository.getInstance(this)

        setupListeners()

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun setupListeners() {
        binding.overlayView.onDetectionSelectedListener = { detection ->
            showEquipmentDetails(detection.label)
        }

        binding.btnQuickDetails.setOnClickListener {
            val firstDetection = latestDetections.firstOrNull()
            if (firstDetection != null) {
                showEquipmentDetails(firstDetection.label)
            } else {
                // Si no hay equipo en foco, mostrar el primero del catálogo
                val firstEquipment = kbRepository.getAllEquipments().firstOrNull()
                if (firstEquipment != null) {
                    val dialog = EquipmentBottomSheetDialog.newInstance(firstEquipment)
                    dialog.show(supportFragmentManager, "EquipmentDetail")
                } else {
                    Toast.makeText(this, "Apunta la cámara a un equipo del laboratorio", Toast.LENGTH_SHORT).show()
                }
            }
        }

        binding.btnOpenChat.setOnClickListener {
            val firstDetection = latestDetections.firstOrNull()
            val intent = Intent(this, ChatActivity::class.java).apply {
                if (firstDetection != null) {
                    val eq = kbRepository.getEquipmentByClass(firstDetection.label)
                    putExtra(ChatActivity.EXTRA_EQUIPMENT_ID, eq?.id ?: firstDetection.label)
                    putExtra(ChatActivity.EXTRA_EQUIPMENT_NAME, firstDetection.displayName)
                }
            }
            startActivity(intent)
        }
    }

    private fun showEquipmentDetails(yoloClass: String) {
        val eq = kbRepository.getEquipmentByClass(yoloClass) ?: kbRepository.getEquipmentById(yoloClass)
        if (eq != null) {
            val dialog = EquipmentBottomSheetDialog.newInstance(eq)
            dialog.show(supportFragmentManager, "EquipmentDetail")
        } else {
            Toast.makeText(this, "Equipo detectado: $yoloClass", Toast.LENGTH_SHORT).show()
        }
    }

    private fun allPermissionsGranted() = ContextCompat.checkSelfPermission(
        baseContext, Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
                }

            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor) { imageProxy ->
                        processImageProxy(imageProxy)
                    }
                }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageAnalyzer
                )
            } catch (exc: Exception) {
                exc.printStackTrace()
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun processImageProxy(imageProxy: ImageProxy) {
        val bitmap = imageProxyToBitmap(imageProxy)
        imageProxy.close()

        if (bitmap != null) {
            val detections = yoloDetector.detect(bitmap)
            latestDetections = detections

            runOnUiThread {
                binding.overlayView.setResults(detections, bitmap.width, bitmap.height)
                updateHUD(detections)
            }
        }
    }

    private fun updateHUD(detections: List<DetectionResult>) {
        if (detections.isEmpty()) {
            binding.tvStatus.text = "Escaneando laboratorio en tiempo real…"
        } else {
            val names = detections.joinToString(", ") { "${it.displayName} (${(it.confidence * 100).toInt()}%)" }
            binding.tvStatus.text = "🎯 Detectado: $names"
        }
    }

    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
        val bitmap = imageProxy.toBitmap()
        val rotationDegrees = imageProxy.imageInfo.rotationDegrees

        return if (rotationDegrees != 0) {
            val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } else {
            bitmap
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        yoloDetector.close()
    }
}
