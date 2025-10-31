package com.bakhawone.thesis_bakhawone

import android.content.Context
import android.graphics.*
import android.media.Image
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.min
import java.util.concurrent.atomic.AtomicBoolean
import android.os.SystemClock

/**
 * Data model for detected objects.
 */
data class Detection(
    val box: RectF,
    val label: String,
    val score: Float,
    // Optional enrichment fields computed at runtime (AR + business logic)
    val isRhizophora: Boolean = false,
    val isAlive: Boolean? = null,
    val dbhCm: Float? = null,
    val isInBoundary: Boolean? = null
)

/**
 * Helper class for managing letterboxed image preprocessing.
 */
private data class LetterboxResult(
    val bitmap: Bitmap,
    val scale: Float,
    val dx: Int,
    val dy: Int
)

/**
 * TrunkDetection — real-time TFLite detector for trunk recognition.
 * Uses coroutine-based async inference and exposes results as a Flow.
 */
class TrunkDetection(
    private val context: Context,
    private val imageSize: Int = 640,
    private val confidenceThreshold: Float = 0.5f,
    private val labelsFile: String = "labels.txt",
    private val modelFile: String = "best_float32.tflite"
) {
    private val TAG = "TrunkDetection"

    private var interpreter: Interpreter? = null
    private var labels: List<String> = emptyList()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val busy = AtomicBoolean(false)
    private var lastProcessUptimeMs: Long = 0L
    private val minIntervalMs: Long = 150

    // Live detections accessible to Compose UI
    private val _detections = MutableStateFlow<List<Detection>>(emptyList())
    val detections: StateFlow<List<Detection>> get() = _detections

    init {
        loadModelAndLabels()
    }

    /**
     * Load TFLite model and label list from assets.
     */
    private fun loadModelAndLabels() {
        try {
            labels = FileUtil.loadLabels(context, labelsFile)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading labels, using default list", e)
            labels = listOf(
                "Alive Rhizophora",
                "Alive Trunk",
                "Dead Rhizophora",
                "Dead Trunk"
            )
        }

        try {
            val model = FileUtil.loadMappedFile(context, modelFile)
            val options = Interpreter.Options().apply { setNumThreads(2) }
            interpreter = Interpreter(model, options)
            Log.d(TAG, "✅ Model loaded successfully")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Model load failed: ${e.message}", e)
        }
    }

    /**
     * Perform async detection on a Bitmap.
     */
    fun detectAsync(bitmap: Bitmap) {
        val now = SystemClock.uptimeMillis()
        if (now - lastProcessUptimeMs < minIntervalMs) return
        if (!busy.compareAndSet(false, true)) return
        lastProcessUptimeMs = now
        scope.launch {
            try {
                val result = detect(bitmap)
                _detections.emit(result)
            } finally {
                busy.set(false)
            }
        }
    }

    /**
     * Analyze ARCore camera frame image (YUV_420_888 → Bitmap → detect).
     */
    fun analyzeImage(image: Image) {
        val now = SystemClock.uptimeMillis()
        if (now - lastProcessUptimeMs < minIntervalMs || busy.get()) {
            try { image.close() } catch (_: Exception) {}
            return
        }
        val bitmap = yuvToRgb(image)
        try { image.close() } catch (_: Exception) {}
        detectAsync(bitmap)
    }

    /**
     * Convert YUV Image to Bitmap (ARCore-compatible).
     */
    private fun yuvToRgb(image: Image): Bitmap {
        val yBuffer = image.planes[0].buffer
        val uBuffer = image.planes[1].buffer
        val vBuffer = image.planes[2].buffer
        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)
        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
        val out = java.io.ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 65, out)
        val bytes = out.toByteArray()
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    /**
     * Run inference synchronously on a Bitmap.
     */
    private fun detect(bitmap: Bitmap): List<Detection> {
        val interpreter = interpreter ?: return emptyList()

        return try {
            val processed = letterboxImage(bitmap, imageSize)

            // Prepare input tensor
            val inputBuffer = ByteBuffer.allocateDirect(1 * imageSize * imageSize * 3 * 4)
            inputBuffer.order(ByteOrder.nativeOrder())
            for (y in 0 until imageSize) {
                for (x in 0 until imageSize) {
                    val pixel = processed.bitmap.getPixel(x, y)
                    inputBuffer.putFloat(Color.red(pixel) / 255.0f)
                    inputBuffer.putFloat(Color.green(pixel) / 255.0f)
                    inputBuffer.putFloat(Color.blue(pixel) / 255.0f)
                }
            }

            // Output tensor: YOLO-style [1, 8, 8400]
            val output = Array(1) { Array(8) { FloatArray(8400) } }
            synchronized(interpreter) {
                interpreter.run(inputBuffer, output)
            }

            val detections = mutableListOf<Detection>()
            for (i in 0 until 8400) {
                val x = output[0][0][i]
                val y = output[0][1][i]
                val w = output[0][2][i]
                val h = output[0][3][i]

                var bestClass = -1
                var bestScore = 0f
                for (c in labels.indices) {
                    val score = output[0][c + 4][i]
                    if (score > bestScore) {
                        bestScore = score
                        bestClass = c
                    }
                }

                if (bestScore > confidenceThreshold && bestClass in labels.indices) {
                    val left = (x - w / 2) * bitmap.width
                    val top = (y - h / 2) * bitmap.height
                    val right = (x + w / 2) * bitmap.width
                    val bottom = (y + h / 2) * bitmap.height

                    if (right > left && bottom > top &&
                        left >= 0 && top >= 0 &&
                        right <= bitmap.width && bottom <= bitmap.height
                    ) {
                        val label = labels[bestClass]
                        detections.add(
                            Detection(
                                box = RectF(left, top, right, bottom),
                                label = "$label (${String.format("%.1f", bestScore * 100)}%)",
                                score = bestScore
                            )
                        )
                    }
                }
            }

            processed.bitmap.recycle()
            detections
        } catch (e: Exception) {
            Log.e(TAG, "Detection error: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Resize and pad image to model input size.
     */
    private fun letterboxImage(src: Bitmap, targetSize: Int): LetterboxResult {
        val scale = min(targetSize.toFloat() / src.width, targetSize.toFloat() / src.height)
        val newWidth = (src.width * scale).toInt()
        val newHeight = (src.height * scale).toInt()

        val resized = Bitmap.createScaledBitmap(src, newWidth, newHeight, true)
        val output = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(Color.BLACK)

        val dx = (targetSize - newWidth) / 2
        val dy = (targetSize - newHeight) / 2
        canvas.drawBitmap(resized, dx.toFloat(), dy.toFloat(), null)

        resized.recycle()
        return LetterboxResult(output, scale, dx, dy)
    }

    /**
     * Close resources.
     */
    fun close() {
        interpreter?.close()
        interpreter = null
        scope.cancel()
    }

    fun canAcceptFrame(): Boolean {
        val now = SystemClock.uptimeMillis()
        return (now - lastProcessUptimeMs >= minIntervalMs) && !busy.get()
    }
}
