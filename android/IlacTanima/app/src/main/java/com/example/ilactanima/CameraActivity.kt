package com.example.ilactanima

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
import com.example.ilactanima.databinding.ActivityCameraBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

class CameraActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "CameraActivity"
        private const val CONF_THRESHOLD = 0.45f
        private const val MAX_CARDS = 5  // Aynı anda kaç ilaç gösterilsin (en güvenliler)
    }

    private lateinit var binding: ActivityCameraBinding

    private var detector: YoloDetector? = null
    private var repo: MedicineRepository? = null

    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private val inferenceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var lastFpsTimestamp = System.currentTimeMillis()
    private var framesSinceLast = 0

    private val infoAdapter = InfoCardAdapter()
    private val labelInfoCache = HashMap<String, MedicineInfo?>()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) startCamera()
        else {
            Toast.makeText(this, R.string.permission_camera_denied, Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCameraBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Status bar boşluğunu üst bar'a uygula
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.topBar) { v, insets ->
            val sb = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.statusBars())
            v.setPadding(v.paddingLeft, v.paddingTop + sb.top, v.paddingRight, v.paddingBottom)
            insets
        }

        binding.btnBack.setOnClickListener { finish() }
        binding.overlay.scaleMode = DetectionOverlay.ScaleMode.FILL_CENTER

        setupRecyclerView()

        // Model + DB yükleme arka planda
        inferenceScope.launch {
            try {
                detector = YoloDetector(applicationContext, confThreshold = CONF_THRESHOLD)
                repo = MedicineRepository(applicationContext)
                Log.d(TAG, "Detector + Repository hazır. ${detector?.labels?.size} sınıf yüklendi.")
            } catch (t: Throwable) {
                Log.e(TAG, "Model/DB başlatılamadı", t)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@CameraActivity,
                        getString(R.string.error_model_load, t.message ?: ""),
                        Toast.LENGTH_LONG).show()
                }
            }
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun setupRecyclerView() {
        binding.infoCardsRv.apply {
            layoutManager = LinearLayoutManager(this@CameraActivity, LinearLayoutManager.HORIZONTAL, false)
            adapter = infoAdapter
            PagerSnapHelper().attachToRecyclerView(this)

            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrollStateChanged(rv: RecyclerView, newState: Int) {
                    if (newState == RecyclerView.SCROLL_STATE_IDLE) updatePageIndicator()
                }
            })
        }
        showEmptyCard()
    }

    private fun showEmptyCard() {
        infoAdapter.submit(emptyList())
        binding.tvPageIndicator.visibility = View.GONE
        // RV boşken bile zemin görünsün diye tek empty kart enjekte et
        // Yerine tvPageIndicator gizli, RV boş. UI'da ekstra "boş" yer olmaması için
        // info yokken kullanıcıya hint vermesini istiyorsak adapter'a placeholder ekleyebiliriz.
    }

    private fun updatePageIndicator() {
        val lm = binding.infoCardsRv.layoutManager as? LinearLayoutManager ?: return
        val pos = lm.findFirstCompletelyVisibleItemPosition()
            .takeIf { it != RecyclerView.NO_POSITION }
            ?: lm.findFirstVisibleItemPosition()
        val total = infoAdapter.itemCount
        if (total <= 1) {
            binding.tvPageIndicator.visibility = View.GONE
        } else {
            binding.tvPageIndicator.visibility = View.VISIBLE
            binding.tvPageIndicator.text = getString(R.string.page_indicator, pos + 1, total)
        }
    }

    private fun startCamera() {
        val previewView = binding.previewView
        previewView.scaleType = PreviewView.ScaleType.FILL_CENTER

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()

                imageAnalysis.setAnalyzer(cameraExecutor) { proxy ->
                    processFrame(proxy)
                }

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalysis
                )
            } catch (e: Exception) {
                Log.e(TAG, "Camera bind hatası", e)
                Toast.makeText(this, "Kamera açılamadı: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun processFrame(proxy: ImageProxy) {
        val det = detector
        if (det == null) {
            proxy.close()
            return
        }

        val bitmap: Bitmap = try {
            proxy.toBitmap()
        } catch (t: Throwable) {
            proxy.close()
            return
        }

        val rotation = proxy.imageInfo.rotationDegrees
        proxy.close()

        val rotated = if (rotation != 0) {
            val m = Matrix().apply { postRotate(rotation.toFloat()) }
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true).also {
                if (it !== bitmap) bitmap.recycle()
            }
        } else bitmap

        val allDetections = try {
            det.detect(rotated)
        } catch (t: Throwable) {
            Log.e(TAG, "Detect hatası", t)
            emptyList()
        }

        val srcW = rotated.width
        val srcH = rotated.height
        rotated.recycle()

        // Sınıf başına en yüksek skorlu olanı al (aynı ilacın 2 kutusu varsa tek kart)
        val uniqueDetections = allDetections
            .asSequence()
            .groupBy { it.label }
            .map { (_, list) -> list.maxBy { it.score } }
            .sortedByDescending { it.score }
            .take(MAX_CARDS)
            .toList()

        // Her etiket için DB sorgusunu cache'le
        val items = uniqueDetections.map { d ->
            val info = labelInfoCache.getOrPut(d.label) {
                try { repo?.findByModelLabel(d.label) } catch (_: Throwable) { null }
            }
            InfoCardAdapter.Item(d, info)
        }

        runOnUiThread {
            binding.overlay.setDetections(allDetections, srcW, srcH)

            val oldLabels = infoAdapter.currentLabels()
            val newLabels = items.map { it.detection.label }

            // Kart set'i değişti mi? Değişmediyse kullanıcının kaydırmasını bozma.
            if (oldLabels != newLabels) {
                infoAdapter.submit(items)
                if (items.isNotEmpty()) {
                    binding.infoCardsRv.scrollToPosition(0)
                }
                updatePageIndicator()
            }

            framesSinceLast++
            val now = System.currentTimeMillis()
            if (now - lastFpsTimestamp >= 1000) {
                val fps = framesSinceLast * 1000f / (now - lastFpsTimestamp)
                binding.tvStats.text = getString(R.string.fps_stats, fps, allDetections.size)
                framesSinceLast = 0
                lastFpsTimestamp = now
            }
        }
    }
}