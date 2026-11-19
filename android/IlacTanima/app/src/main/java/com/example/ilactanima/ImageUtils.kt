package com.example.ilactanima

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import androidx.core.graphics.createBitmap

/**
 * Görüntü ön işleme yardımcıları.
 */
object ImageUtils {

    /** Letterbox sonrası orijinal koordinatlara dönmek için meta veri. */
    data class LetterboxInfo(
        val scale: Float,
        val padX: Float,
        val padY: Float,
        val srcWidth: Int,
        val srcHeight: Int,
    )

    /**
     * Bitmap'i hedef kare boyutuna letterbox ile yerleştirir.
     * YOLO'nun eğitim sırasında kullandığı format.
     */
    fun letterbox(
        src: Bitmap,
        targetSize: Int = 640,
        fillColor: Int = Color.rgb(114, 114, 114)
    ): Pair<Bitmap, LetterboxInfo> {
        val srcW = src.width
        val srcH = src.height
        val scale = minOf(
            targetSize.toFloat() / srcW,
            targetSize.toFloat() / srcH
        )
        val newW = (srcW * scale).toInt()
        val newH = (srcH * scale).toInt()
        val padX = (targetSize - newW) / 2f
        val padY = (targetSize - newH) / 2f

        val out = createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(fillColor)
        val dst = Rect(padX.toInt(), padY.toInt(), padX.toInt() + newW, padY.toInt() + newH)
        canvas.drawBitmap(src, null, dst, Paint(Paint.FILTER_BITMAP_FLAG))

        return out to LetterboxInfo(
            scale = scale,
            padX = padX,
            padY = padY,
            srcWidth = srcW,
            srcHeight = srcH
        )
    }

    /** Bitmap'i verilen açıda döndür (0/90/180/270). */
    fun rotate(src: Bitmap, degrees: Int): Bitmap {
        if (degrees == 0) return src
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
    }

    /** 640 uzayındaki normalize koordinatı orijinal görüntü uzayına çevir. */
    fun undoLetterboxNormalized(
        nx: Float, ny: Float,
        info: LetterboxInfo,
        targetSize: Int = 640
    ): Pair<Float, Float> {
        val px = nx * targetSize
        val py = ny * targetSize
        val realX = (px - info.padX) / info.scale
        val realY = (py - info.padY) / info.scale
        val outX = (realX / info.srcWidth).coerceIn(0f, 1f)
        val outY = (realY / info.srcHeight).coerceIn(0f, 1f)
        return outX to outY
    }
}
