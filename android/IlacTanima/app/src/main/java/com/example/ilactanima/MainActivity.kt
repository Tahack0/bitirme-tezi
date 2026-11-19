package com.example.ilactanima

import android.content.Intent
import android.os.Bundle
import android.view.animation.DecelerateInterpolator
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.ilactanima.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Defansif: hata olursa app çakılmasın
        try {
            binding = ActivityMainBinding.inflate(layoutInflater)
            setContentView(binding.root)
            setupInsets()
            setupListeners()
            runEntryAnimations()
        } catch (t: Throwable) {
            android.util.Log.e("MainActivity", "onCreate ÇAKILDI", t)
            Toast.makeText(this, "HATA: ${t.javaClass.simpleName}: ${t.message}", Toast.LENGTH_LONG).show()
            // finish() kaldırıldı — hatayı görmek için açık kalsın
        }
    }

    private fun setupInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val sb = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(sb.left, sb.top, sb.right, sb.bottom)
            insets
        }
    }

    private fun setupListeners() {
        binding.btnCamera.setOnClickListener {
            startActivity(Intent(this, CameraActivity::class.java))
        }
        binding.btnGallery.setOnClickListener {
            startActivity(Intent(this, GalleryActivity::class.java))
        }
    }

    /** Yumuşak fade + slide-up animasyonu, sade ve GPU-light. */
    private fun runEntryAnimations() {
        listOf(
            binding.tvTitle,
            binding.tvSubtitle,
            binding.btnCamera,
            binding.btnGallery
        ).forEachIndexed { i, view ->
            view.alpha = 0f
            view.translationY = 32f
            view.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(60L * i)
                .setDuration(360)
                .setInterpolator(DecelerateInterpolator(1.4f))
                .start()
        }
    }
}
