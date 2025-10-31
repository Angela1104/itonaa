package com.bakhawone.thesis_bakhawone

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.ar.core.*
import com.google.ar.core.exceptions.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ARActivity : ComponentActivity() {

    private var arSession: Session? = null
    private lateinit var renderer: ARRenderer
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private var deviceStartLat: Double = 0.0
    private var deviceStartLon: Double = 0.0
    private val CAMERA_PERMISSION_CODE = 100

    // made non-private so ARRenderer can access them
    var detectionRunning = false
    var trunkDetector: TrunkDetection? = null

    // ✅ show controls only while boundary is currently visible
    private var boundaryVisible by mutableStateOf(false)
    private lateinit var glSurfaceView: android.opengl.GLSurfaceView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        deviceStartLat = intent.getDoubleExtra("deviceStartLat", 0.0)
        deviceStartLon = intent.getDoubleExtra("deviceStartLon", 0.0)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                CAMERA_PERMISSION_CODE
            )
        } else {
            initARSession()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initARSession()
            } else {
                Toast.makeText(this, "Camera permission required for AR", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    private fun initARSession() {
        try {
            arSession = Session(this)
        } catch (e: UnavailableArcoreNotInstalledException) {
            Toast.makeText(this, "Please install ARCore", Toast.LENGTH_LONG).show()
            finish(); return
        } catch (e: UnavailableDeviceNotCompatibleException) {
            Toast.makeText(this, "This device is not AR compatible", Toast.LENGTH_LONG).show()
            finish(); return
        } catch (e: Exception) {
            finish(); return
        }

        val config = Config(arSession)
        config.updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
        arSession?.configure(config)

        // Pass visibility callback to renderer
        renderer = ARRenderer(arSession!!, deviceStartLat, deviceStartLon, this) { isVisible ->
            boundaryVisible = isVisible
        }

        glSurfaceView = renderer.getGLSurfaceView()

        val root = FrameLayout(this)
        root.addView(glSurfaceView, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))

        val composeOverlay = ComposeView(this).apply {
            setContent { DetectionOverlayUI() }
        }
        root.addView(composeOverlay)

        setContentView(root)
        fetchLatestCenterpoint()
    }

    @Composable
    private fun DetectionOverlayUI() {
        var isDetecting by remember { mutableStateOf(false) }

        val detectionsState = trunkDetector?.detections
        val detections by (detectionsState?.collectAsState(initial = emptyList()) ?: remember { mutableStateOf(emptyList()) })

        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            OverlayView(detections, 640, 640, Modifier.fillMaxSize())

            // Enrich and persist detections when visible and running
            LaunchedEffect(detections, boundaryVisible, isDetecting) {
                if (isDetecting && boundaryVisible && detections.isNotEmpty()) {
                    processAndSaveDetections(detections)
                }
            }

            // ✅ only show buttons while boundary is visible
            if (boundaryVisible) {
                Row(
                    modifier = Modifier
                        .padding(bottom = 40.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Button(
                        onClick = {
                            if (!isDetecting) {
                                startTrunkDetection()
                                isDetecting = true
                            }
                        },
                        enabled = !isDetecting
                    ) { Text("Start Detection") }

                    Button(
                        onClick = {
                            if (isDetecting) {
                                stopTrunkDetection()
                                isDetecting = false
                                goToDashboard()
                            }
                        },
                        enabled = isDetecting
                    ) { Text("End Detection") }
                }
            }
        }
    }

    private fun processAndSaveDetections(detections: List<Detection>) {
        val viewW = glSurfaceView.width.toFloat().takeIf { it > 0 } ?: return
        val viewH = glSurfaceView.height.toFloat().takeIf { it > 0 } ?: return
        val fx = renderer.getFxPixels() ?: return
        val userId = auth.currentUser?.uid ?: return

        detections.forEach { det ->
            val isRhizo = det.label.contains("Rhizophora", ignoreCase = true)
            val isDead = det.label.contains("Dead", ignoreCase = true)
            val isAlive = det.label.contains("Alive", ignoreCase = true)
            if (!isRhizo) return@forEach

            val centerXImg = (det.box.left + det.box.right) / 2f
            val centerYImg = (det.box.top + det.box.bottom) / 2f
            val scaleX = viewW / 640f
            val scaleY = viewH / 640f
            val screenX = centerXImg * scaleX
            val screenY = centerYImg * scaleY

            val wp = renderer.worldPointAndDepthFromScreen(screenX, screenY) ?: return@forEach
            val localVec = wp.first
            val depth = wp.second
            val inBoundary = renderer.isLocalPointInsideBoundary(localVec)
            if (!inBoundary) return@forEach

            val boxWidthPxOnScreen = (det.box.right - det.box.left) * scaleX
            val dbhMeters = if (isAlive) (boxWidthPxOnScreen * depth / fx) else null
            val dbhCm = dbhMeters?.let { (it * 100f).coerceIn(0f, 500f) }

            val trunkId = java.util.UUID.randomUUID().toString()

            val data = hashMapOf(
                "trunk_id" to trunkId,
                "is_in_boundary" to inBoundary,
                "vector" to listOf(localVec[0], localVec[1], localVec[2]),
                "rhizophora" to (if (isAlive) 1 else 0),
                "dbh" to (dbhCm ?: 0f),
                "timestamp" to com.google.firebase.Timestamp.now()
            )

            db.collection("users").document(userId)
                .collection("detections").document(trunkId)
                .set(data)
        }
    }

    private fun startTrunkDetection() {
        if (detectionRunning) return
        detectionRunning = true
        trunkDetector = TrunkDetection(this)
        Toast.makeText(this, "Detection started", Toast.LENGTH_SHORT).show()
    }

    private fun stopTrunkDetection() {
        if (!detectionRunning) return
        detectionRunning = false
        trunkDetector?.close()
        trunkDetector = null
        Toast.makeText(this, "Detection stopped", Toast.LENGTH_SHORT).show()
    }

    private fun goToDashboard() {
        val intent = Intent(this, DashboardActivity::class.java)
        startActivity(intent)
        finish()
    }

    private fun fetchLatestCenterpoint() {
        val userId = auth.currentUser?.uid ?: return
        db.collection("users")
            .document(userId)
            .collection("centerpoints")
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(1)
            .get()
            .addOnSuccessListener { snapshot ->
                if (!snapshot.isEmpty) {
                    val doc = snapshot.documents[0]
                    val lat = doc.getDouble("latitude") ?: 0.0
                    val lon = doc.getDouble("longitude") ?: 0.0
                    renderer.setCenterPoint(lat, lon)
                }
            }
    }

    override fun onResume() {
        super.onResume()
        try {
            arSession?.resume()
            renderer.onResume()
        } catch (e: CameraNotAvailableException) {
            finish()
        }
    }

    override fun onPause() {
        super.onPause()
        renderer.onPause()
        arSession?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        renderer.onPause()
        arSession?.close()
        arSession = null
        trunkDetector?.close()
    }
}
