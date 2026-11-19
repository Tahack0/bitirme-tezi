package com.example.ilactanima

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import androidx.core.graphics.toColorInt

/**
 * Kamera/video önizlemesi üzerine YOLO tespit kutularını çizer.
 *
 * Detection.x1..y2 alanları kaynak görüntü uzayında 0..1 normalize halinde gelir.
 * Bu View kaynak görüntünün önizlemeye nasıl yerleştiğini (FILL_CENTER vs FIT_CENTER)
 * bilerek koordinatları view piksellerine çevirir.
 *
 * NOT: Bilerek hardware layer kullanmıyoruz — emülatör GPU/EGL bug'larından kaçınmak için.
 */
class DetectionOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    enum class ScaleMode { FILL_CENTER, FIT_CENTER }

    var scaleMode: ScaleMode = ScaleMode.FILL_CENTER

    private var detections: List<Detection> = emptyList()
    private var sourceWidth: Int = 0
    private var sourceHeight: Int = 0

    private val rect = RectF()

    private val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
        color = "#10B981".toColorInt()
    }

    private val labelBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = "#CC10B981".toColorInt()
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 36f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    fun setDetections(detections: List<Detection>, sourceWidth: Int, sourceHeight: Int) {
        this.detections = detections
        this.sourceWidth = sourceWidth
        this.sourceHeight = sourceHeight
        postInvalidate()
    }

    fun clear() {
        this.detections = emptyList()
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (detections.isEmpty() || (sourceWidth == 0) || (sourceHeight == 0)) return

        val viewW = width.toFloat()
        val viewH = height.toFloat()
        val srcW = sourceWidth.toFloat()
        val srcH = sourceHeight.toFloat()

        val scale: Float
        val offsetX: Float
        val offsetY: Float
        when (scaleMode) {
            ScaleMode.FILL_CENTER -> {
                scale = maxOf(viewW / srcW, viewH / srcH)
                offsetX = (viewW - srcW * scale) / 2f
                offsetY = (viewH - srcH * scale) / 2f
            }
            ScaleMode.FIT_CENTER -> {
                scale = minOf(viewW / srcW, viewH / srcH)
                offsetX = (viewW - srcW * scale) / 2f
                offsetY = (viewH - srcH * scale) / 2f
            }
        }

        for (d in detections) {
            val left = d.x1 * srcW * scale + offsetX
            val top = d.y1 * srcH * scale + offsetY
            val right = d.x2 * srcW * scale + offsetX
            val bottom = d.y2 * srcH * scale + offsetY

            rect.set(left, top, right, bottom)
            canvas.drawRoundRect(rect, 12f, 12f, boxPaint)

            val labelText = "${d.label}  ${(d.score * 100).toInt()}%"
            val textWidth = textPaint.measureText(labelText)
            val textHeight = textPaint.fontMetrics.run { descent - ascent }
            val padH = 12f
            val padV = 6f
            val labelTop = (top - textHeight - padV * 2).coerceAtLeast(0f)
            val labelLeft = left.coerceAtLeast(0f)
            val labelRight = labelLeft + textWidth + padH * 2

            canvas.drawRoundRect(
                labelLeft, labelTop,
                labelRight, labelTop + textHeight + padV * 2,
                8f, 8f, labelBgPaint
            )
            canvas.drawText(
                labelText,
                labelLeft + padH,
                labelTop + textHeight + padV - textPaint.fontMetrics.descent,
                textPaint
            )
        }
    }
}
