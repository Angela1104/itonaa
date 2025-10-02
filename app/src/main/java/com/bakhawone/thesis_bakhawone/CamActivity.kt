package com.bakhawone.thesis_bakhawone

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.graphics.*
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.min

class CamActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var overlayView: OverlayView
    private lateinit var tvPrediction: TextView
    private lateinit var btnScan: Button
    private lateinit var btnUpload: Button
    private lateinit var imgUploaded: ImageView

    private val TAG = "CamActivity"
    private val IMAGE_SIZE = 640
    private val LABELS_FILE = "labels.txt"
    private val PERMISSION_CODE = 123
    private val IMAGE_PICK_CODE = 1001

    private var interpreter: Interpreter? = null
    private var labels: List<String> = emptyList()
    private var frameCounter = 0

    private val reqExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.cam_activity)

        previewView = findViewById(R.id.previewView)
        overlayView = findViewById(R.id.overlayView)
        tvPrediction = findViewById(R.id.tvPrediction)
        btnScan = findViewById(R.id.btnScan)
        btnUpload = findViewById(R.id.btnUpload)
        imgUploaded = findViewById(R.id.imgUploaded)

        // Load labels
        labels = try {
            loadLabels(LABELS_FILE)
        } catch (e: Exception) {
            listOf("Alive Rhizophora", "Alive Trunk", "Dead Rhizophora", "Dead Trunk")
        }

        // Load model
        try {
            val model = FileUtil.loadMappedFile(this, "best_float32.tflite")
            val options = Interpreter.Options().apply { setNumThreads(4) }
            interpreter = Interpreter(model, options)
            tvPrediction.text = "Model loaded"
        } catch (e: Exception) {
            interpreter = null
            tvPrediction.text = "Model load failed: ${e.message}"
            Log.e(TAG, "Model load error", e)
        }

        // Start camera detection
        btnScan.setOnClickListener {
            imgUploaded.visibility = View.GONE
            previewView.visibility = View.VISIBLE
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                startCamera()
                btnScan.visibility = View.GONE
            } else {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.CAMERA),
                    PERMISSION_CODE
                )
            }
        }

        // Upload image from gallery
        btnUpload.setOnClickListener {
            pickImageFromGallery()
        }

        // Optional: start camera automatically if permission granted
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            previewView.visibility = View.VISIBLE
        }
    }

    // ---- Pick Image ----
    private fun pickImageFromGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(intent, IMAGE_PICK_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == IMAGE_PICK_CODE && resultCode == Activity.RESULT_OK) {
            val imageUri: Uri? = data?.data
            if (imageUri != null) {
                try {
                    val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, imageUri)
                    imgUploaded.setImageBitmap(bitmap)
                    imgUploaded.visibility = View.VISIBLE
                    previewView.visibility = View.GONE

                    // Run detection
                    val dets = detect(bitmap)
                    overlayView.setResults(
                        dets.map { it.box },
                        dets.map { it.label },
                        bitmap.width,
                        bitmap.height,
                        null
                    )
                    tvPrediction.text =
                        if (dets.isNotEmpty()) dets.joinToString { d -> d.label }
                        else "No detection"
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }
    }

    // ---- Detection ----
    private data class Detection(val box: RectF, val label: String, val score: Float)

    private fun detect(bitmap: Bitmap): List<Detection> {
        val lb = letterboxImage(bitmap, IMAGE_SIZE)
        val input = Array(1) { Array(IMAGE_SIZE) { Array(IMAGE_SIZE) { FloatArray(3) } } }

        for (y in 0 until IMAGE_SIZE) {
            for (x in 0 until IMAGE_SIZE) {
                val px = lb.bitmap.getPixel(x, y)
                input[0][y][x][0] = (Color.red(px) / 255f)
                input[0][y][x][1] = (Color.green(px) / 255f)
                input[0][y][x][2] = (Color.blue(px) / 255f)
            }
        }

        val output = Array(1) { Array(8) { FloatArray(8400) } }
        interpreter?.run(input, output)

        val detections = mutableListOf<Detection>()
        val grid = 8400

        for (i in 0 until grid) {
            val x = output[0][0][i]
            val y = output[0][1][i]
            val w = output[0][2][i]
            val h = output[0][3][i]

            var bestClass = -1
            var bestScore = 0f
            val numClasses = labels.size
            for (c in 0 until numClasses) {
                val sc = output[0][c + 4][i]
                if (sc > bestScore) {
                    bestScore = sc
                    bestClass = c
                }
            }

            if (bestScore > 0.10f && bestClass in labels.indices) {
                val left = (x - w / 2) * bitmap.width
                val top = (y - h / 2) * bitmap.height
                val right = (x + w / 2) * bitmap.width
                val bottom = (y + h / 2) * bitmap.height
                if (right > left && bottom > top) {
                    detections.add(
                        Detection(
                            RectF(left, top, right, bottom),
                            "${labels[bestClass]} (${String.format("%.1f", bestScore * 100)}%)",
                            bestScore
                        )
                    )
                }
            }
        }

        lb.bitmap.recycle()
        return detections
    }

    // ---- Letterbox Resize ----
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

    // ---- CameraX Setup ----
    private fun startCamera() {
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
                .also {
                    it.setAnalyzer(reqExecutor) { imageProxy ->
                        frameCounter++
                        if (frameCounter % 3 == 0) {
                            val bmp = imageProxyToBitmap(imageProxy)
                            bmp?.let {
                                val dets = detect(it)
                                runOnUiThread {
                                    overlayView.setResults(
                                        dets.map { d -> d.box },
                                        dets.map { d -> d.label },
                                        it.width,
                                        it.height,
                                        null
                                    )
                                    tvPrediction.text =
                                        if (dets.isNotEmpty()) dets.joinToString { d -> d.label }
                                        else "Scanning..."
                                }
                                it.recycle()
                            }
                        }
                        imageProxy.close()
                    }
                }

            provider.unbindAll()
            provider.bindToLifecycle(this, cameraSelector, preview, analyzer)
        }, ContextCompat.getMainExecutor(this))
    }

    private fun imageProxyToBitmap(image: ImageProxy): Bitmap? {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    private fun loadLabels(filename: String): List<String> {
        val out = mutableListOf<String>()
        assets.open(filename).bufferedReader().useLines { lines -> lines.forEach { out.add(it) } }
        return out
    }

    override fun onDestroy() {
        super.onDestroy()
        reqExecutor.shutdown()
        interpreter?.close()
    }
}
