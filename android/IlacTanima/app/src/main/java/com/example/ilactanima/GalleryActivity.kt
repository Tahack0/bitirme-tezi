package com.example.ilactanima

import android.graphics.SurfaceTexture
import androidx.core.graphics.createBitmap
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
import com.example.ilactanima.databinding.ActivityGalleryBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GalleryActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "GalleryActivity"
        private const val CONF_THRESHOLD = 0.45f
        private const val ANALYSIS_INTERVAL_MS = 200L
        private const val SEEKBAR_UPDATE_INTERVAL_MS = 200L
        private const val MAX_CARDS = 5
    }

    private lateinit var binding: ActivityGalleryBinding

    private var detector: YoloDetector? = null
    private var repo: MedicineRepository? = null

    private var mediaPlayer: MediaPlayer? = null
    private var pendingUri: Uri? = null
    private var pendingSurface: Surface? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private val inferenceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var inferenceJob: Job? = null

    private var isUserSeeking = false

    private val infoAdapter = InfoCardAdapter()
    private val labelInfoCache = HashMap<String, MedicineInfo?>()

    private val pickVideo = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) loadVideo(uri)
    }

    private val analysisRunnable = object : Runnable {
        override fun run() {
            analyzeCurrentFrame()
            mainHandler.postDelayed(this, ANALYSIS_INTERVAL_MS)
        }
    }

    private val seekUpdateRunnable = object : Runnable {
        override fun run() {
            val mp = mediaPlayer
            if (mp != null && !isUserSeeking && mp.isPlaying) {
                binding.seekBar.progress = mp.currentPosition
            }
            mainHandler.postDelayed(this, SEEKBAR_UPDATE_INTERVAL_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGalleryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.topBar) { v, insets ->
            val sb = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.statusBars())
            v.setPadding(v.paddingLeft, v.paddingTop + sb.top, v.paddingRight, v.paddingBottom)
            insets
        }

        binding.btnBack.setOnClickListener { finish() }
        binding.btnPickEmpty.setOnClickListener { launchVideoPicker() }
        binding.btnPick.setOnClickListener { launchVideoPicker() }
        binding.btnPlayPause.setOnClickListener { togglePlayPause() }

        binding.overlay.scaleMode = DetectionOverlay.ScaleMode.FIT_CENTER

        setupRecyclerView()

        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) mediaPlayer?.seekTo(progress)
            }
            override fun onStartTrackingTouch(sb: SeekBar?) { isUserSeeking = true }
            override fun onStopTrackingTouch(sb: SeekBar?) { isUserSeeking = false }
        })

        binding.textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(s: SurfaceTexture, w: Int, h: Int) {
                pendingSurface = Surface(s)
                pendingUri?.let { startPlayback(it) }
            }
            override fun onSurfaceTextureSizeChanged(s: SurfaceTexture, w: Int, h: Int) = Unit
            override fun onSurfaceTextureDestroyed(s: SurfaceTexture): Boolean {
                pendingSurface?.release()
                pendingSurface = null
                return true
            }
            override fun onSurfaceTextureUpdated(s: SurfaceTexture) = Unit
        }

        inferenceScope.launch {
            try {
                detector = YoloDetector(applicationContext, confThreshold = CONF_THRESHOLD)
                repo = MedicineRepository(applicationContext)
            } catch (t: Throwable) {
                Log.e(TAG, "Model/DB başlatılamadı", t)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@GalleryActivity,
                        getString(R.string.error_model_load, t.message ?: ""),
                        Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun setupRecyclerView() {
        binding.infoCardsRv.apply {
            layoutManager = LinearLayoutManager(this@GalleryActivity, LinearLayoutManager.HORIZONTAL, false)
            adapter = infoAdapter
            PagerSnapHelper().attachToRecyclerView(this)

            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrollStateChanged(rv: RecyclerView, newState: Int) {
                    if (newState == RecyclerView.SCROLL_STATE_IDLE) updatePageIndicator()
                }
            })
        }
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

    private fun launchVideoPicker() {
        pickVideo.launch(
            PickVisualMediaRequest(
                ActivityResultContracts.PickVisualMedia.VideoOnly
            )
        )
    }

    private fun loadVideo(uri: Uri) {
        binding.emptyState.visibility = View.GONE
        binding.controlBar.visibility = View.VISIBLE
        binding.infoCardsRv.visibility = View.VISIBLE

        pendingUri = uri
        releaseMediaPlayer()

        if (pendingSurface == null) return
        startPlayback(uri)
    }

    private fun startPlayback(uri: Uri) {
        val surface = pendingSurface ?: return
        val mp = MediaPlayer()
        try {
            mp.setDataSource(this, uri)
            mp.setSurface(surface)
            mp.setOnVideoSizeChangedListener { _, width, height ->
                fitTextureToVideo(width, height)
            }
            mp.setOnPreparedListener {
                binding.seekBar.max = mp.duration
                mp.isLooping = false
                mp.start()
                binding.btnPlayPause.text = getString(R.string.btn_pause)
                mainHandler.removeCallbacks(analysisRunnable)
                mainHandler.post(analysisRunnable)
                mainHandler.removeCallbacks(seekUpdateRunnable)
                mainHandler.post(seekUpdateRunnable)
            }
            mp.setOnCompletionListener {
                binding.btnPlayPause.text = getString(R.string.btn_play)
            }
            mp.setOnErrorListener { _, what, extra ->
                Log.e(TAG, "MediaPlayer error what=$what extra=$extra")
                Toast.makeText(this, R.string.error_video_open, Toast.LENGTH_LONG).show()
                true
            }
            mp.prepareAsync()
            mediaPlayer = mp
        } catch (t: Throwable) {
            Log.e(TAG, "Video açılamadı", t)
            Toast.makeText(this, R.string.error_video_open, Toast.LENGTH_LONG).show()
        }
    }

    private fun fitTextureToVideo(videoW: Int, videoH: Int) {
        if (videoW <= 0 || videoH <= 0) return
        val container = binding.videoContainer
        container.post {
            val cw = container.width
            val ch = container.height
            if (cw == 0 || ch == 0) return@post
            val scale = minOf(cw.toFloat() / videoW, ch.toFloat() / videoH)
            val newW = (videoW * scale).toInt()
            val newH = (videoH * scale).toInt()
            val lp = binding.textureView.layoutParams as ViewGroup.LayoutParams
            lp.width = newW
            lp.height = newH
            binding.textureView.layoutParams = lp

            val olp = binding.overlay.layoutParams
            olp.width = newW
            olp.height = newH
            binding.overlay.layoutParams = olp
        }
    }

    private fun togglePlayPause() {
        val mp = mediaPlayer ?: return
        if (mp.isPlaying) {
            mp.pause()
            binding.btnPlayPause.text = getString(R.string.btn_play)
        } else {
            mp.start()
            binding.btnPlayPause.text = getString(R.string.btn_pause)
        }
    }

    private fun analyzeCurrentFrame() {
        val det = detector ?: return
        val tex = binding.textureView
        if (!tex.isAvailable || tex.width == 0 || tex.height == 0) return

        val maxSide = 640
        val scale = minOf(
            maxSide.toFloat() / tex.width,
            maxSide.toFloat() / tex.height,
            1f
        )
        val bw = (tex.width * scale).toInt().coerceAtLeast(1)
        val bh = (tex.height * scale).toInt().coerceAtLeast(1)
        val frame = tex.getBitmap(bw, bh) ?: return

        if (inferenceJob?.isActive == true) {
            frame.recycle()
            return
        }

        inferenceJob = inferenceScope.launch {
            val allDetections = try {
                det.detect(frame)
            } catch (t: Throwable) {
                Log.e(TAG, "Detect hatası", t)
                emptyList()
            }
            val srcW = frame.width
            val srcH = frame.height
            frame.recycle()

            val uniqueDetections = allDetections
                .groupBy { it.label }
                .map { (_, list) -> list.maxBy { it.score } }
                .sortedByDescending { it.score }
                .take(MAX_CARDS)

            val items = uniqueDetections.map { d ->
                val info = labelInfoCache.getOrPut(d.label) {
                    try { repo?.findByModelLabel(d.label) } catch (_: Throwable) { null }
                }
                InfoCardAdapter.Item(d, info)
            }

            withContext(Dispatchers.Main) {
                binding.overlay.setDetections(allDetections, srcW, srcH)
                val oldLabels = infoAdapter.currentLabels()
                val newLabels = items.map { it.detection.label }
                if (oldLabels != newLabels) {
                    infoAdapter.submit(items)
                    if (items.isNotEmpty()) {
                        binding.infoCardsRv.scrollToPosition(0)
                    }
                    updatePageIndicator()
                }
                binding.tvStats.text = getString(R.string.stats_boxes, allDetections.size)
            }
        }
    }

    private fun releaseMediaPlayer() {
        mainHandler.removeCallbacks(analysisRunnable)
        mainHandler.removeCallbacks(seekUpdateRunnable)
        mediaPlayer?.apply {
            try { if (isPlaying) stop() } catch (_: Throwable) {}
            release()
        }
        mediaPlayer = null
    }

    override fun onPause() {
        super.onPause()
        if (mediaPlayer?.isPlaying == true) {
            mediaPlayer?.pause()
            binding.btnPlayPause.text = getString(R.string.btn_play)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mainHandler.removeCallbacks(analysisRunnable)
        mainHandler.removeCallbacks(seekUpdateRunnable)
        releaseMediaPlayer()
        pendingSurface?.release()
        pendingSurface = null
        inferenceScope.cancel()
        detector?.close()
        repo?.close()
    }
}