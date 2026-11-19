package com.example.ilactanima

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * YOLOv11n TFLite modelini yükler ve çalıştırır.
 *
 * Model: best_float32.tflite (assets/model/)
 *   Giriş : [1, 640, 640, 3] float32, RGB, 0..1 normalize, NHWC
 *   Çıkış : [1, 300, 6] float32 — NMS modele gömülü
 *           Her satır: [x1, y1, x2, y2, score, class_id]
 *           Koordinatlar 640×640 letterbox uzayında 0..1 normalize.
 *
 * NMS gömülü olduğu için Kotlin'de ek post-process gerekmiyor,
 * sadece confidence eşiğine göre filtreliyoruz.
 */
class YoloDetector(
    context: Context,
    private val confThreshold: Float = 0.45f,
    numThreads: Int = 4,
) {

    companion object {
        private const val INPUT_SIZE = 640
        private const val NUM_DETECTIONS = 300
        private const val NUM_VALUES_PER_DETECTION = 6
        private const val MODEL_PATH = "model/best_float32.tflite"
        private const val LABELS_PATH = "labels/labels.txt"
    }

    private val interpreter: Interpreter
    val labels: List<String>

    private val inputBuffer: ByteBuffer = ByteBuffer
        .allocateDirect(1 * INPUT_SIZE * INPUT_SIZE * 3 * 4)
        .order(ByteOrder.nativeOrder())

    private val outputArray: Array<Array<FloatArray>> =
        Array(1) { Array(NUM_DETECTIONS) { FloatArray(NUM_VALUES_PER_DETECTION) } }

    private val intPixels = IntArray(INPUT_SIZE * INPUT_SIZE)

    init {
        val opts = Interpreter.Options().apply { setNumThreads(numThreads) }
        interpreter = Interpreter(loadModelFile(context), opts)
        labels = context.assets.open(LABELS_PATH).bufferedReader().useLines { lines ->
            lines.map { it.trim() }
                .filter { it.isNotEmpty() }
                .toList()
        }
    }

    private fun loadModelFile(context: Context): MappedByteBuffer {
        val afd = context.assets.openFd(MODEL_PATH)
        FileInputStream(afd.fileDescriptor).use { fis ->
            return fis.channel.map(
                FileChannel.MapMode.READ_ONLY,
                afd.startOffset,
                afd.declaredLength
            )
        }
    }

    fun detect(bitmap: Bitmap): List<Detection> {
        val (lb, info) = ImageUtils.letterbox(bitmap, INPUT_SIZE)

        bitmapToInputBuffer(lb)

        interpreter.run(inputBuffer, outputArray)

        val detections = ArrayList<Detection>()
        for (i in 0 until NUM_DETECTIONS) {
            val row = outputArray[0][i]
            val score = row[4]
            if (score < confThreshold) continue

            val classId = row[5].toInt()
            if ((classId < 0) || (classId >= labels.size)) continue

            val (x1, y1) = ImageUtils.undoLetterboxNormalized(row[0], row[1], info, INPUT_SIZE)
            val (x2, y2) = ImageUtils.undoLetterboxNormalized(row[2], row[3], info, INPUT_SIZE)

            detections.add(
                Detection(
                    x1 = minOf(x1, x2),
                    y1 = minOf(y1, y2),
                    x2 = maxOf(x1, x2),
                    y2 = maxOf(y1, y2),
                    score = score,
                    classId = classId,
                    label = labels[classId]
                )
            )
        }
        if (lb !== bitmap) lb.recycle()
        return detections
    }

    private fun bitmapToInputBuffer(bitmap: Bitmap) {
        inputBuffer.rewind()
        bitmap.getPixels(intPixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)
        for (p in intPixels) {
            val r = ((p shr 16) and 0xFF) / 255f
            val g = ((p shr 8) and 0xFF) / 255f
            val b = (p and 0xFF) / 255f
            inputBuffer.putFloat(r)
            inputBuffer.putFloat(g)
            inputBuffer.putFloat(b)
        }
    }

    fun close() {
        interpreter.close()
    }
}
