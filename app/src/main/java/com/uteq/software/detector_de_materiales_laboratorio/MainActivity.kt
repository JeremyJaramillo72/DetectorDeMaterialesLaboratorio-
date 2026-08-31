package com.uteq.software.detector_de_materiales_laboratorio

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Bundle
import android.util.Log
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

    private val TAG = "MainActivity"
    private lateinit var binding: ActivityMainBinding
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var yoloDetector: YoloDetector
    private lateinit var kbRepository: KnowledgeBaseRepository

    private var latestDetections: List<DetectionResult> = emptyList()

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
        yoloDetector = YoloDetector(this)
        kbRepository = KnowledgeBaseRepository.getInstance(this)

        setupUI()

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissions()
        }
    }

    private fun setupUI() {
        binding.overlayView.onDetectionSelectedListener = { detection ->
            showEquipmentDetails(detection.label)
        }

        binding.btnOpenChat.setOnClickListener {
            val topDetection = latestDetections.maxByOrNull { it.confidence }
            val intent = Intent(this, ChatActivity::class.java).apply {
                if (topDetection != null) {
                    putExtra(ChatActivity.EXTRA_EQUIPMENT_ID, topDetection.label)
                    putExtra(ChatActivity.EXTRA_EQUIPMENT_NAME, topDetection.displayName)
                }
            }
            startActivity(intent)
        }

        binding.btnQuickDetails.setOnClickListener {
            val topDetection = latestDetections.maxByOrNull { it.confidence }
            if (topDetection != null) {
                showEquipmentDetails(topDetection.label)
            } else {
                Toast.makeText(this, "Apunta la cámara a un equipo para ver su ficha técnica.", Toast.LENGTH_SHORT).show()
            }
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

            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
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
                Log.e(TAG, "Fallo al vincular la cámara con el ciclo de vida", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun processImageProxy(imageProxy: ImageProxy) {
        try {
            val bitmap = imageProxyToBitmap(imageProxy)
            if (bitmap != null) {
                val detections = yoloDetector.detect(bitmap)
                latestDetections = detections

                runOnUiThread {
                    binding.overlayView.setResults(detections, bitmap.width, bitmap.height)
                    updateHUD(detections)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error en processImageProxy: ${e.message}", e)
        } finally {
            imageProxy.close()
        }
    }

    private fun updateHUD(detections: List<DetectionResult>) {
        if (detections.isEmpty()) {
            binding.tvStatus.text = "Apunta la cámara a un equipo o toca el recuadro para ver detalles"
        } else {
            val names = detections.joinToString(", ") { "${it.displayName} (${(it.confidence * 100).toInt()}%)" }
            binding.tvStatus.text = "🎯 Detectado: $names"
        }
    }

    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
        return try {
            val bitmap = imageProxy.toBitmap()
            val rotationDegrees = imageProxy.imageInfo.rotationDegrees

            if (rotationDegrees != 0) {
                val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            } else {
                bitmap
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error convirtiendo ImageProxy a Bitmap: ${e.message}", e)
            null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        yoloDetector.close()
    }

    companion object {
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
}
