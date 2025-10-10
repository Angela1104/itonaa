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
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import java.nio.ByteBuffer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.min

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
            listOf("Alive Rhizophora", "Alive Trunk", "Dead Rhizophora", "Dead Trunk")
        }

        // Load TFLite model
        try {
            val model = FileUtil.loadMappedFile(this, "best_float32.tflite")
            val options = Interpreter.Options().apply { setNumThreads(4) }
            interpreter = Interpreter(model, options)
            Log.d(TAG, "Model loaded")
        } catch (e: Exception) {
            Log.e(TAG, "Model load failed", e)
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
    }

    @Composable
    fun CameraScreen(interpreter: Interpreter?, labels: List<String>) {
        val context = LocalContext.current
        var previewView by remember { mutableStateOf<PreviewView?>(null) }
        var overlayView by remember { mutableStateOf<OverlayView?>(null) }
        var predictionText by remember { mutableStateOf("Starting detection...") }
        var isDetecting by remember { mutableStateOf(true) }

        val cameraPermissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) {
                previewView?.let {
                    overlayView?.let { ov ->
                        startCamera(it, ov, interpreter, labels) { text, detections ->
                            predictionText = text
                        }
                    }
                }
            } else {
                predictionText = "Camera permission denied"
            }
        }

        LaunchedEffect(Unit) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                == android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                previewView?.let {
                    overlayView?.let { ov ->
                        startCamera(it, ov, interpreter, labels) { text, detections ->
                            predictionText = text
                        }
                    }
                }
            } else {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            // Camera Preview
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).also { pv -> previewView = pv }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Overlay View for bounding boxes
            AndroidView(
                factory = { ctx ->
                    OverlayView(ctx, null).also { ov ->
                        overlayView = ov
                        // Set the label colors
                        ov.setLabelColors(
                            aliveRhizophoraColor = Color.GREEN,
                            aliveTrunkColor = Color.GREEN,
                            deadRhizophoraColor = Color.RED,
                            deadTrunkColor = Color.RED
                        )
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

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
                        previewView?.let {
                            overlayView?.let { ov ->
                                startCamera(it, ov, interpreter, labels) { text, detections ->
                                    predictionText = text
                                }
                            }
                        }
                    },
                    enabled = !isDetecting
                ) {
                    Text("Start Detection")
                }

                Button(
                    onClick = {
                        isDetecting = false
                        overlayView?.clear()
                        predictionText = "Detection paused"
                    },
                    enabled = isDetecting
                ) {
                    Text("Pause Detection")
                }
            }
        }
    }

    private fun startCamera(
        previewView: PreviewView,
        overlayView: OverlayView,
        interpreter: Interpreter?,
        labels: List<String>,
        onPrediction: (String, List<Detection>) -> Unit
    ) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val provider = cameraProviderFuture.get()
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val analyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { analysis ->
                    analysis.setAnalyzer(reqExecutor) { imageProxy ->
                        val bitmap = yuvToRgb(imageProxy)
                        bitmap?.let {
                            val detections = detect(it, interpreter, labels)

                            // Update overlay with detections
                            overlayView.setResults(
                                boxes = detections.map { it.box },
                                labels = detections.map { it.label },
                                imgWidth = bitmap.width,
                                imgHeight = bitmap.height
                            )

                            // Update prediction text
                            val detectionText = if (detections.isNotEmpty()) {
                                "Detected: ${detections.size} trunks"
                            } else {
                                "Scanning for trunks..."
                            }
                            onPrediction(detectionText, detections)

                            it.recycle()
                        }
                        imageProxy.close()
                    }
                }

            provider.unbindAll()
            provider.bindToLifecycle(this, cameraSelector, preview, analyzer)
        }, ContextCompat.getMainExecutor(this))
    }

    @OptIn(ExperimentalGetImage::class)
    private fun yuvToRgb(image: ImageProxy): Bitmap? {
        val yuv = image.image ?: return null
        val yBuffer = yuv.planes[0].buffer
        val uBuffer = yuv.planes[1].buffer
        val vBuffer = yuv.planes[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)
        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
        val out = java.io.ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 100, out)
        val bytes = out.toByteArray()
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    private data class Detection(val box: RectF, val label: String, val score: Float)

    private fun detect(bitmap: Bitmap, interpreter: Interpreter?, labels: List<String>): List<Detection> {
        if (interpreter == null) return emptyList()

        // Letterbox + normalize
        val lb = letterboxImage(bitmap, IMAGE_SIZE)
        val input = Array(1) { Array(IMAGE_SIZE) { Array(IMAGE_SIZE) { FloatArray(3) } } }

        for (y in 0 until IMAGE_SIZE) {
            for (x in 0 until IMAGE_SIZE) {
                val px = lb.bitmap.getPixel(x, y)
                input[0][y][x][0] = Color.red(px) / 255f
                input[0][y][x][1] = Color.green(px) / 255f
                input[0][y][x][2] = Color.blue(px) / 255f
            }
        }

        // Adjust output shape as per your model (this might need adjustment based on your model)
        val output = Array(1) { Array(8) { FloatArray(8400) } }
        interpreter.run(input, output)

        val detections = mutableListOf<Detection>()
        val grid = 8400

        for (i in 0 until grid) {
            val x = output[0][0][i]
            val y = output[0][1][i]
            val w = output[0][2][i]
            val h = output[0][3][i]

            var bestClass = -1
            var bestScore = 0f

            for (c in labels.indices) {
                val sc = output[0][c + 4][i]
                if (sc > bestScore) {
                    bestScore = sc
                    bestClass = c
                }
            }

            if (bestScore > CONFIDENCE_THRESHOLD && bestClass in labels.indices) {
                val left = (x - w / 2) * bitmap.width
                val top = (y - h / 2) * bitmap.height
                val right = (x + w / 2) * bitmap.width
                val bottom = (y + h / 2) * bitmap.height

                if (right > left && bottom > top && left >= 0 && top >= 0 && right <= bitmap.width && bottom <= bitmap.height) {
                    val labelName = labels[bestClass]
                    detections.add(
                        Detection(
                            RectF(left, top, right, bottom),
                            "$labelName (${String.format("%.1f", bestScore * 100)}%)",
                            bestScore
                        )
                    )
                }
            }
        }

        lb.bitmap.recycle()
        return detections
    }

    private data class LetterboxResult(val bitmap: Bitmap, val scale: Float, val dx: Int, val dy: Int)

    private fun letterboxImage(src: Bitmap, target: Int): LetterboxResult {
        val scale = min(target.toFloat() / src.width, target.toFloat() / src.height)
        val newW = (src.width * scale).toInt()
        val newH = (src.height * scale).toInt()
        val resized = Bitmap.createScaledBitmap(src, newW, newH, true)
        val output = Bitmap.createBitmap(target, target, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(Color.BLACK)
        val dx = (target - newW) / 2
        val dy = (target - newH) / 2
        canvas.drawBitmap(resized, dx.toFloat(), dy.toFloat(), null)
        resized.recycle()
        return LetterboxResult(output, scale, dx, dy)
    }
}