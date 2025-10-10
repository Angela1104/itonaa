package com.bakhawone.thesis_bakhawone

import android.Manifest
import android.graphics.*
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import org.tensorflow.lite.support.common.FileUtil
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.min

// Add this missing import
import org.tensorflow.lite.Interpreter

// Add the missing Detection data class at top level
data class Detection(val box: RectF, val label: String, val score: Float)

// Add the missing LetterboxResult data class
private data class LetterboxResult(val bitmap: Bitmap, val scale: Float, val dx: Int, val dy: Int)

class CameraActivity : ComponentActivity() {

    private val TAG = "CameraActivity"
    private val IMAGE_SIZE = 640
    private val LABELS_FILE = "labels.txt"
    private val CONFIDENCE_THRESHOLD = 0.5f

    private var interpreter: Interpreter? = null
    private var labels: List<String> = emptyList()
    private val reqExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Load labels
        labels = try {
            FileUtil.loadLabels(this, LABELS_FILE)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading labels, using default", e)
            listOf("Alive Rhizophora", "Alive Trunk", "Dead Rhizophora", "Dead Trunk")
        }

        // Load TFLite model
        try {
            val model = FileUtil.loadMappedFile(this, "best_float32.tflite")
            val options = Interpreter.Options().apply {
                setNumThreads(4)
            }
            interpreter = Interpreter(model, options)
            Log.d(TAG, "Model loaded successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Model load failed: ${e.message}", e)
            interpreter = null
        }

        setContent {
            CameraScreen(interpreter = interpreter, labels = labels)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        reqExecutor.shutdown()
        interpreter?.close()
        Log.d(TAG, "CameraActivity destroyed")
    }

    @OptIn(ExperimentalGetImage::class) // ADD THIS TO COMPOSABLE FUNCTION
    @Composable
    fun CameraScreen(interpreter: Interpreter?, labels: List<String>) {
        val context = LocalContext.current
        var previewView by remember { mutableStateOf<PreviewView?>(null) }
        var overlayView by remember { mutableStateOf<BoundingBoxOverlay?>(null) }
        var predictionText by remember { mutableStateOf("Starting camera...") }
        var isDetecting by remember { mutableStateOf(true) }
        var hasPermission by remember { mutableStateOf(false) }

        val cameraPermissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { granted ->
            hasPermission = granted
            if (granted) {
                predictionText = "Camera permission granted"
                previewView?.let { pv ->
                    overlayView?.let { ov ->
                        startCamera(pv, ov, interpreter, labels) { text, detections ->
                            predictionText = text
                        }
                    }
                }
            } else {
                predictionText = "Camera permission denied"
            }
        }

        // Check permission and initialize camera
        LaunchedEffect(Unit) {
            val hasCameraPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED

            hasPermission = hasCameraPermission

            if (hasCameraPermission) {
                predictionText = "Camera ready - detecting objects..."
            } else {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            if (hasPermission) {
                // Camera Preview
                AndroidView(
                    factory = { ctx ->
                        PreviewView(ctx).also { pv ->
                            previewView = pv
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Bounding Box Overlay
                AndroidView(
                    factory = { ctx ->
                        BoundingBoxOverlay(ctx).also { ov ->
                            overlayView = ov
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Detection status text
            Text(
                text = predictionText,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(16.dp),
                color = ComposeColor.White,
                style = MaterialTheme.typography.bodyMedium
            )

            // Control buttons
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = {
                        isDetecting = true
                        predictionText = "Detection started"
                        previewView?.let { pv ->
                            overlayView?.let { ov ->
                                startCamera(pv, ov, interpreter, labels) { text, detections ->
                                    predictionText = text
                                }
                            }
                        }
                    },
                    enabled = !isDetecting && hasPermission
                ) {
                    Text("Start Detection")
                }

                Button(
                    onClick = {
                        isDetecting = false
                        overlayView?.clearDetections()
                        predictionText = "Detection paused"
                    },
                    enabled = isDetecting && hasPermission
                ) {
                    Text("Pause Detection")
                }

                // Back button
                Button(
                    onClick = {
                        finish()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Text("Back")
                }
            }

            // Model loading status
            if (interpreter == null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text(
                            "Model not loaded - check if best_float32.tflite exists in assets",
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalGetImage::class)
    private fun startCamera(
        previewView: PreviewView,
        overlayView: BoundingBoxOverlay,
        interpreter: Interpreter?,
        labels: List<String>,
        onPrediction: (String, List<Detection>) -> Unit
    ) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            try {
                val provider = cameraProviderFuture.get()
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                val preview = Preview.Builder()
                    .build()
                    .also { preview ->
                        preview.setSurfaceProvider(previewView.surfaceProvider)
                    }

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { analysis ->
                        analysis.setAnalyzer(reqExecutor) { imageProxy ->
                            try {
                                val bitmap = imageProxy.toBitmap()
                                bitmap?.let { bmp ->
                                    val detections = detect(bmp, interpreter, labels)

                                    // Update overlay
                                    overlayView.setDetections(detections, bmp.width, bmp.height)

                                    // Update prediction text
                                    val detectionText = when {
                                        interpreter == null -> "Model not loaded"
                                        detections.isNotEmpty() -> "Detected: ${detections.size} objects"
                                        else -> "Scanning... No objects detected"
                                    }
                                    onPrediction(detectionText, detections)

                                    bmp.recycle()
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Analysis error: ${e.message}")
                            } finally {
                                imageProxy.close()
                            }
                        }
                    }

                // Bind use cases
                provider.unbindAll()
                provider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    imageAnalysis
                )

                Log.d(TAG, "Camera started successfully")

            } catch (e: Exception) {
                Log.e(TAG, "Camera start failed: ${e.message}", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    @OptIn(ExperimentalGetImage::class)
    private fun ImageProxy.toBitmap(): Bitmap? {
        val image = this.image ?: return null
        val planes = image.planes
        val yBuffer = planes[0].buffer
        val uBuffer = planes[1].buffer
        val vBuffer = planes[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)
        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        val yuvImage = YuvImage(nv21, ImageFormat.NV21, this.width, this.height, null)
        val out = java.io.ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, this.width, this.height), 80, out)
        val imageBytes = out.toByteArray()
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    }

    private fun detect(bitmap: Bitmap, interpreter: Interpreter?, labels: List<String>): List<Detection> {
        if (interpreter == null) return emptyList()

        return try {
            // Preprocess image
            val processed = letterboxImage(bitmap, IMAGE_SIZE)
            val input = Array(1) { Array(IMAGE_SIZE) { Array(IMAGE_SIZE) { FloatArray(3) } } }

            // Normalize pixel values
            for (y in 0 until IMAGE_SIZE) {
                for (x in 0 until IMAGE_SIZE) {
                    val pixel = processed.bitmap.getPixel(x, y)
                    input[0][y][x][0] = Color.red(pixel) / 255.0f
                    input[0][y][x][1] = Color.green(pixel) / 255.0f
                    input[0][y][x][2] = Color.blue(pixel) / 255.0f
                }
            }

            // Run inference - adjust output shape based on your model
            val output = Array(1) { Array(8) { FloatArray(8400) } }
            interpreter.run(input, output)

            // Process detections
            val detections = mutableListOf<Detection>()

            for (i in 0 until 8400) { // Adjust based on your model output
                val x = output[0][0][i]
                val y = output[0][1][i]
                val w = output[0][2][i]
                val h = output[0][3][i]

                var bestClass = -1
                var bestScore = 0f

                // Find best class
                for (c in labels.indices) {
                    val score = output[0][c + 4][i]
                    if (score > bestScore) {
                        bestScore = score
                        bestClass = c
                    }
                }

                // Filter by confidence threshold
                if (bestScore > CONFIDENCE_THRESHOLD && bestClass in labels.indices) {
                    // Convert normalized coordinates to pixel coordinates
                    val left = (x - w / 2) * bitmap.width
                    val top = (y - h / 2) * bitmap.height
                    val right = (x + w / 2) * bitmap.width
                    val bottom = (y + h / 2) * bitmap.height

                    // Validate bounds
                    if (right > left && bottom > top &&
                        left >= 0 && top >= 0 &&
                        right <= bitmap.width && bottom <= bitmap.height) {

                        val labelName = labels[bestClass]
                        detections.add(
                            Detection(
                                box = RectF(left, top, right, bottom),
                                label = "$labelName (${String.format("%.1f", bestScore * 100)}%)",
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
}

// Simple BoundingBoxOverlay view (replace your OverlayView)
class BoundingBoxOverlay(context: android.content.Context) : android.view.View(context) {
    private val detections = mutableListOf<Detection>()
    private var imgWidth = 1
    private var imgHeight = 1

    private val paint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        isAntiAlias = true
    }

    private val textPaint = Paint().apply {
        style = Paint.Style.FILL
        textSize = 32f
        color = Color.WHITE
        isAntiAlias = true
    }

    fun setDetections(newDetections: List<Detection>, width: Int, height: Int) {
        detections.clear()
        detections.addAll(newDetections)
        imgWidth = width
        imgHeight = height
        postInvalidate()
    }

    fun clearDetections() {
        detections.clear()
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val scaleX = width.toFloat() / imgWidth
        val scaleY = height.toFloat() / imgHeight

        for (detection in detections) {
            // Set color based on label
            paint.color = when {
                detection.label.contains("Alive") -> Color.GREEN
                detection.label.contains("Dead") -> Color.RED
                else -> Color.YELLOW
            }

            // Draw bounding box
            val rect = RectF(
                detection.box.left * scaleX,
                detection.box.top * scaleY,
                detection.box.right * scaleX,
                detection.box.bottom * scaleY
            )
            canvas.drawRect(rect, paint)

            // Draw label
            canvas.drawText(
                detection.label,
                rect.left,
                rect.top - 10,
                textPaint
            )
        }
    }
}