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
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            Log.d(TAG, "✅ Loaded ${labels.size} labels: ${labels.joinToString(", ")}")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading labels, using default list", e)
            labels = listOf(
                "Alive Rhizophora",
                "Dead Rhizophora"
            )
        }

        if (labels.isEmpty()) {
            Log.e(TAG, "❌ No labels available, cannot run detection")
            return
        }

        try {
            val model = FileUtil.loadMappedFile(context, modelFile)
            val options = Interpreter.Options().apply { 
                setNumThreads(2)
                setUseXNNPACK(true) // Enable XNNPACK for better performance
            }
            val newInterpreter = Interpreter(model, options)
            interpreter = newInterpreter
            
            // Get input and output tensor info for validation
            val inputTensor = newInterpreter.getInputTensor(0)
            val outputTensor = newInterpreter.getOutputTensor(0)
            Log.d(TAG, "✅ Model loaded successfully")
            Log.d(TAG, "   Input shape: ${inputTensor.shape().contentToString()}")
            Log.d(TAG, "   Output shape: ${outputTensor.shape().contentToString()}")
            Log.d(TAG, "   Expected output dim: ${labels.size + 4} (4 bbox + ${labels.size} classes)")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Model load failed: ${e.message}", e)
            e.printStackTrace()
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
        if (labels.isEmpty()) {
            Log.w(TAG, "No labels loaded, skipping detection")
            return emptyList()
        }

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

            // Output tensor: YOLO-style [1, num_classes+4, 8400]
            // Format: [x, y, w, h, class1_score, class2_score, ...]
            val numClasses = labels.size
            val outputDim = numClasses + 4
            val output = Array(1) { Array(outputDim) { FloatArray(8400) } }
            
            synchronized(interpreter) {
                interpreter.run(inputBuffer, output)
            }

            val detections = mutableListOf<Detection>()
            val scale = processed.scale
            val dx = processed.dx
            val dy = processed.dy
            
            for (i in 0 until 8400) {
                // Model outputs normalized coordinates (0-1) relative to 640x640 letterboxed image
                val xNorm = output[0][0][i]
                val yNorm = output[0][1][i]
                val wNorm = output[0][2][i]
                val hNorm = output[0][3][i]

                // Find best class
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
                    // Convert from normalized coordinates (0-1) in letterboxed space to pixel coordinates
                    // Letterboxed coordinates (0-640)
                    val xLetterbox = xNorm * imageSize
                    val yLetterbox = yNorm * imageSize
                    val wLetterbox = wNorm * imageSize
                    val hLetterbox = hNorm * imageSize
                    
                    // Convert from letterboxed space to original image space
                    // Remove padding offset and scale back
                    val xOriginal = (xLetterbox - dx) / scale
                    val yOriginal = (yLetterbox - dy) / scale
                    val wOriginal = wLetterbox / scale
                    val hOriginal = hLetterbox / scale
                    
                    // Calculate bounding box in original image coordinates
                    val left = xOriginal - wOriginal / 2f
                    val top = yOriginal - hOriginal / 2f
                    val right = xOriginal + wOriginal / 2f
                    val bottom = yOriginal + hOriginal / 2f

                    // Validate bounding box
                    if (right > left && bottom > top &&
                        left >= -bitmap.width * 0.1f && top >= -bitmap.height * 0.1f &&
                        right <= bitmap.width * 1.1f && bottom <= bitmap.height * 1.1f
                    ) {
                        // Clamp to image bounds
                        val clampedLeft = left.coerceIn(0f, bitmap.width.toFloat())
                        val clampedTop = top.coerceIn(0f, bitmap.height.toFloat())
                        val clampedRight = right.coerceIn(0f, bitmap.width.toFloat())
                        val clampedBottom = bottom.coerceIn(0f, bitmap.height.toFloat())
                        
                        if (clampedRight > clampedLeft && clampedBottom > clampedTop) {
                            val label = labels[bestClass]
                            detections.add(
                                Detection(
                                    box = RectF(clampedLeft, clampedTop, clampedRight, clampedBottom),
                                    label = "$label (${String.format("%.1f", bestScore * 100)}%)",
                                    score = bestScore
                                )
                            )
                        }
                    }
                }
            }

            processed.bitmap.recycle()
            if (detections.isNotEmpty()) {
                Log.d(TAG, "Detected ${detections.size} objects")
            }
            detections
        } catch (e: Exception) {
            Log.e(TAG, "Detection error: ${e.message}", e)
            e.printStackTrace()
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
