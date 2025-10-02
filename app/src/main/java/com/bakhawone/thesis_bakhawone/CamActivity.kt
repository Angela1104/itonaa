package com.bakhawone.thesis_bakhawone

import android.Manifest
import android.graphics.*
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.tensorflow.lite.Interpreter 
import org.tensorflow.lite.support.common.FileUtil
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.min

class CamActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var overlayView: OverlayView
    private lateinit var tvPrediction: TextView
    private lateinit var btnScan: Button

    private val TAG = "CamActivity"
    private val IMAGE_SIZE = 640
    private val LABELS_FILE = "labels.txt"

    private var interpreterBest: Interpreter? = null
    private var labels: List<String> = emptyList()

    private val reqExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.cam_activity)

        previewView = findViewById(R.id.previewView)
        overlayView = findViewById(R.id.overlayView)
        tvPrediction = findViewById(R.id.tvPrediction)
        btnScan = findViewById(R.id.btnScan)

        btnScan.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 123)
            } else {
                startCamera()
            }
        }

        // Load labels.txt from assets
        try {
            labels = loadLabels(LABELS_FILE)
        } catch (e: Exception) {
            labels = emptyList()
            Log.w(TAG, "labels.txt load failed: ${e.message}")
        }

        // Load model from ml/
        try {
            interpreterBest = Interpreter(FileUtil.loadMappedFile(this, "best_float32.tflite"))
            tvPrediction.text = "Model loaded"
        } catch (e: Exception) {
            interpreterBest = null
            tvPrediction.text = "Model load failed"
            Log.e(TAG, "Model load error", e)
        }
    }

    private data class LetterboxResult(val bitmap: Bitmap, val scale: Float, val dx: Int, val dy: Int)
    private fun letterboxImage(src: Bitmap, targetSize: Int): LetterboxResult {
        val scale = min(targetSize.toFloat() / src.width, targetSize.toFloat() / src.height)
        val newW = (src.width * scale).toInt()
        val newH = (src.height * scale).toInt()
        val resized = Bitmap.createScaledBitmap(src, newW, newH, true)
        val output = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(Color.BLACK)
        val dx = (targetSize - newW) / 2
        val dy = (targetSize - newH) / 2
        canvas.drawBitmap(resized, dx.toFloat(), dy.toFloat(), null)
        return LetterboxResult(output, scale, dx, dy)
    }

    private data class RawDet(val box: RectF, val cls: Int, val score: Float)

    // ---------- DETECTION ----------
    private fun detect(bitmap: Bitmap): Pair<List<RectF>, List<String>> {
        val dets = detectWithInterpreter(bitmap, interpreterBest)
        val boxes = dets.first.map { it.box }
        val labelsOut = dets.first.map { idx ->
            if (idx.cls < labels.size) labels[idx.cls] else "Class ${idx.cls}"
        }
        return Pair(boxes, labelsOut)
    }

    private fun detectWithInterpreter(bitmap: Bitmap, interp: Interpreter?): Pair<List<RawDet>, List<String>> {
        if (interp == null) return Pair(emptyList(), emptyList())

        val lb = letterboxImage(bitmap, IMAGE_SIZE)
        val inpBmp = lb.bitmap

        val input = Array(1) { Array(IMAGE_SIZE) { Array(IMAGE_SIZE) { FloatArray(3) } } }
        for (y in 0 until IMAGE_SIZE) {
            for (x in 0 until IMAGE_SIZE) {
                val px = inpBmp.getPixel(x, y)
                input[0][y][x][0] = (px shr 16 and 0xFF) / 255f
                input[0][y][x][1] = (px shr 8 and 0xFF) / 255f
                input[0][y][x][2] = (px and 0xFF) / 255f
            }
        }

        val output = Array(1) { Array(20) { FloatArray(8400) } }
        interp.run(input, output)

        val numClasses = 20 - 4
        val rawDetections = ArrayList<RawDet>()

        for (i in 0 until 8400) {
            val cx = output[0][0][i] * IMAGE_SIZE
            val cy = output[0][1][i] * IMAGE_SIZE
            val w = output[0][2][i] * IMAGE_SIZE
            val h = output[0][3][i] * IMAGE_SIZE

            val scores = FloatArray(numClasses) { c -> output[0][4 + c][i] }
            val maxIdx = scores.indices.maxByOrNull { scores[it] } ?: -1
            val score = if (maxIdx >= 0) scores[maxIdx] else 0f

            if (score > 0.5f && maxIdx >= 0) {
                val bx1 = cx - w / 2f
                val by1 = cy - h / 2f
                val bx2 = cx + w / 2f
                val by2 = cy + h / 2f

                val left = max(0f, (bx1 - lb.dx) / lb.scale)
                val top = max(0f, (by1 - lb.dy) / lb.scale)
                val right = min(bitmap.width.toFloat(), (bx2 - lb.dx) / lb.scale)
                val bottom = min(bitmap.height.toFloat(), (by2 - lb.dy) / lb.scale)

                if ((right - left) > 5 && (bottom - top) > 5) {
                    rawDetections.add(RawDet(RectF(left, top, right, bottom), maxIdx, score))
                }
            }
        }
        return Pair(rawDetections, emptyList())
    }

    // ---------- CAMERA ----------
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val provider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }

            val analyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(reqExecutor) { imageProxy ->
                        val bmp = imageProxyToBitmap(imageProxy)
                        if (bmp != null) {
                            val (boxes, labelsOut) = detect(bmp)
                            runOnUiThread {
                                overlayView.setResults(boxes, labelsOut, bmp.width, bmp.height, null)
                                if (labelsOut.isNotEmpty()) {
                                    tvPrediction.setTextColor(Color.YELLOW)
                                    tvPrediction.text = labelsOut.joinToString()
                                } else {
                                    tvPrediction.text = "No detection"
                                    tvPrediction.setTextColor(Color.RED)
                                }
                            }
                        }
                        imageProxy.close()
                    }
                }

            try {
                provider.unbindAll()
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analyzer)
            } catch (e: Exception) {
                Log.e(TAG, "bind camera failed", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun imageProxyToBitmap(image: ImageProxy): Bitmap? {
        return try {
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

            val yuv = android.graphics.YuvImage(nv21, android.graphics.ImageFormat.NV21, image.width, image.height, null)
            val baos = ByteArrayOutputStream()
            yuv.compressToJpeg(Rect(0, 0, image.width, image.height), 90, baos)
            val bytes = baos.toByteArray()
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Exception) {
            Log.e(TAG, "imageProxyToBitmap error", e)
            null
        }
    }

    // ---------- LABELS ----------
    private fun loadLabels(filename: String): List<String> {
        val out = mutableListOf<String>()
        assets.open(filename).bufferedReader().useLines { lines -> lines.forEach { out.add(it.trim()) } }
        return out
    }

    override fun onDestroy() {
        super.onDestroy()
        reqExecutor.shutdown()
        interpreterBest?.close()
    }
}
