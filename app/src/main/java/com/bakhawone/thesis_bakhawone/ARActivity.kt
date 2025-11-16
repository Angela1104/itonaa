package com.bakhawone.thesis_bakhawone

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.view.MotionEvent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.ar.core.*
import com.google.ar.core.exceptions.*
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.mutableStateListOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

class ARActivity : ComponentActivity() {

    private var arSession: Session? = null
    private lateinit var renderer: ARRenderer
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val CAMERA_PERMISSION_CODE = 100
    private var centerpointSet = false
    private var locationManager: LocationManager? = null
    private var pinnedLocationName: String? = null // Store pinned location name for detections
    private var pinnedLocationDocId: String? = null // Store pinned location document ID for explicit connection
    private var pinnedLocationBarangay: String? = null // Store barangay for detections
    private var deviceStartLat: Double = 0.0 // GPS coordinates from HomeScreen
    private var deviceStartLon: Double = 0.0 // GPS coordinates from HomeScreen
    private val savedDetectionIds = mutableSetOf<String>() // Track saved detections to prevent duplicates
    private val savedMarkers = mutableStateListOf<SavedDetectionMarker>()

    // made non-private so ARRenderer can access them
    var detectionRunning = false
    var trunkDetector: TrunkDetection? = null

    data class SavedDetectionMarker(
        val id: String,
        val localPosition: FloatArray,
        val isAlive: Boolean,
        val dbhCm: Float?
    )

    // ✅ show controls only while boundary is currently visible
    private var boundaryVisible by mutableStateOf(false)
    private lateinit var glSurfaceView: android.opengl.GLSurfaceView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize session and Firebase Auth early (like HomeScreen does)
        initializeSessionAndAuth()
        
        // Read intent extras from HomeScreen
        this.deviceStartLat = intent.getDoubleExtra("deviceStartLat", 0.0)
        this.deviceStartLon = intent.getDoubleExtra("deviceStartLon", 0.0)
        val locationNameFromIntent = intent.getStringExtra("locationName")
        
        // Store location name if provided (avoids Firebase query later)
        if (locationNameFromIntent != null && locationNameFromIntent.isNotBlank()) {
            pinnedLocationName = locationNameFromIntent
            Log.d("ARActivity", "Received location: $locationNameFromIntent at ($deviceStartLat, $deviceStartLon)")
        } else {
            Log.d("ARActivity", "Received coordinates: ($deviceStartLat, $deviceStartLon) - no location name")
        }

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
    
    private fun initializeSessionAndAuth() {
        // Ensure Firebase Auth available
        if (auth.currentUser == null) {
            auth.signInAnonymously()
                .addOnSuccessListener {
                    Log.d("ARActivity", "Anonymous Firebase Auth successful")
                }
                .addOnFailureListener { e ->
                    Log.e("ARActivity", "Anonymous sign-in failed", e)
                    // Don't show toast here - user is already in AR mode
                }
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
        renderer = ARRenderer(arSession!!, this) { isVisible ->
            boundaryVisible = isVisible
            centerpointSet = isVisible // Update centerpoint state when boundary becomes visible
        }

        glSurfaceView = renderer.getGLSurfaceView()
        
        // Add double-tap listener for centerpoint selection
        var lastTapTime = 0L
        glSurfaceView.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastTapTime < 300) {
                    // Double tap detected
                    if (!centerpointSet) {
                        val hitPose = renderer.hitTestPlane(event.x, event.y)
                        if (hitPose != null) {
                            val success = renderer.setCenterpointFromHit(hitPose)
                            if (success) {
                                centerpointSet = true
                                // Save centerpoint to Firebase
                                saveCenterpointToFirebase(hitPose)
                                Toast.makeText(this, "Centerpoint set! Boundary will appear.", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(this, "Failed to set centerpoint", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Toast.makeText(this, "Tap on a detected surface (gray mesh)", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(this, "Centerpoint already set. Only one allowed per session.", Toast.LENGTH_SHORT).show()
                    }
                    lastTapTime = 0
                    true
                } else {
                    lastTapTime = currentTime
                    false
                }
            } else {
                false
            }
        }

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
    }

    @Composable
    private fun DetectionOverlayUI() {
        var isDetecting by remember { mutableStateOf(false) }
        var showHelp by remember { mutableStateOf(false) }
        val helpColor = Color(0xFF607048)

        val detectionsState = trunkDetector?.detections
        val rawDetections by (detectionsState?.collectAsState(initial = emptyList()) ?: remember { mutableStateOf(emptyList()) })

        // Enrich detections with DBH and boundary info for visualization
        val enrichedDetections = remember(rawDetections, boundaryVisible) {
            if (rawDetections.isEmpty() || !renderer.isCenterpointSet()) {
                rawDetections
            } else {
                val viewW = glSurfaceView.width.toFloat().takeIf { it > 0 } ?: 1f
                val viewH = glSurfaceView.height.toFloat().takeIf { it > 0 } ?: 1f
                
                rawDetections.map { det ->
                    try {
                        val centerXImg = (det.box.left + det.box.right) / 2f
                        val centerYImg = (det.box.top + det.box.bottom) / 2f
                        val scaleX = viewW / 640f
                        val scaleY = viewH / 640f
                        val screenX = centerXImg * scaleX
                        val screenY = centerYImg * scaleY

                        val wp = renderer.worldPointAndDepthFromScreen(screenX, screenY)
                        if (wp != null && wp.first.size >= 3) {
                            val localVec = wp.first
                            val depth = wp.second
                            val inBoundary = renderer.isLocalPointInsideBoundary(localVec)
                            
                            // Parse label: format is "Alive Rhizophora (85.3%)" or "Dead Rhizophora (90.1%)"
                            val isRhizo = det.label.contains("Rhizophora", ignoreCase = true)
                            val isAlive = det.label.contains("Alive", ignoreCase = true)
                            val isDead = det.label.contains("Dead", ignoreCase = true)
                            
                            val fx = renderer.getFxPixels() ?: return@map det
                            
                            // Try edge detection for visualization (only for alive Rhizophora inside boundary)
                            var dbhCm: Float? = null
                            var leftEdgeX: Float? = null
                            var rightEdgeX: Float? = null
                            var edgeCenterY: Float? = null
                            
                            if (isRhizo && isAlive && inBoundary) {
                                // Try to get frame for edge detection
                                val frame = renderer.getLastFrame()
                                if (frame != null) {
                                    try {
                                        val cameraImage = frame.acquireCameraImage()
                                        val bitmap = trunkDetector?.yuvToRgbBitmap(cameraImage)
                                        cameraImage.close()
                                        
                                        if (bitmap != null) {
                                            val edgeLines = EdgeDetection.detectTrunkEdges(bitmap, det.box)
                                            if (edgeLines != null) {
                                                leftEdgeX = edgeLines.leftEdgeX
                                                rightEdgeX = edgeLines.rightEdgeX
                                                edgeCenterY = edgeLines.centerY
                                                
                                                // Calculate DBH using edge distance
                                                val edgeCenterScreenX = edgeLines.leftEdgeX * scaleX
                                                val edgeCenterScreenY = edgeLines.centerY * scaleY
                                                val edgeWp = renderer.worldPointAndDepthFromScreen(edgeCenterScreenX, edgeCenterScreenY)
                                                val edgeDepth = edgeWp?.second ?: depth
                                                
                                                val dbhMeters = (edgeLines.edgeWidthPx * scaleX * edgeDepth / fx)
                                                dbhCm = (dbhMeters * 100f).coerceIn(0f, 100f)
                                            } else {
                                                // Fallback to original method
                                                val boxWidthPxOnScreen = (det.box.right - det.box.left) * scaleX
                                                val dbhMeters = (boxWidthPxOnScreen * depth / fx)
                                                dbhCm = (dbhMeters * 100f).coerceIn(0f, 100f)
                                            }
                                            bitmap.recycle()
                                        } else {
                                            // Fallback to original method
                                            val boxWidthPxOnScreen = (det.box.right - det.box.left) * scaleX
                                            val dbhMeters = (boxWidthPxOnScreen * depth / fx)
                                            dbhCm = (dbhMeters * 100f).coerceIn(0f, 100f)
                                        }
                                    } catch (e: Exception) {
                                        // Fallback to original method on error
                                        val boxWidthPxOnScreen = (det.box.right - det.box.left) * scaleX
                                        val dbhMeters = (boxWidthPxOnScreen * depth / fx)
                                        dbhCm = (dbhMeters * 100f).coerceIn(0f, 100f)
                                    }
                                } else {
                                    // Fallback to original method if frame not available
                                    val boxWidthPxOnScreen = (det.box.right - det.box.left) * scaleX
                                    val dbhMeters = (boxWidthPxOnScreen * depth / fx)
                                    dbhCm = (dbhMeters * 100f).coerceIn(0f, 100f)
                                }
                            }
                            
                            det.copy(
                                isRhizophora = isRhizo,
                                isAlive = if (isRhizo) isAlive else null, // Only set isAlive for Rhizophora
                                dbhCm = dbhCm,
                                isInBoundary = inBoundary,
                                leftEdgeX = leftEdgeX,
                                rightEdgeX = rightEdgeX,
                                edgeCenterY = edgeCenterY
                            )
                        } else {
                            // No world point available - still mark Rhizophora status for overlay
                            val isRhizo = det.label.contains("Rhizophora", ignoreCase = true)
                            val isAlive = if (isRhizo) det.label.contains("Alive", ignoreCase = true) else null
                            det.copy(
                                isRhizophora = isRhizo,
                                isAlive = isAlive,
                                isInBoundary = null
                            )
                        }
                    } catch (e: Exception) {
                        det
                    }
                }
            }
        }

        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            var markerOverlays by remember { mutableStateOf<List<OverlayMarker>>(emptyList()) }

            LaunchedEffect(savedMarkers.size, boundaryVisible) {
                if (!boundaryVisible) {
                    markerOverlays = emptyList()
                    return@LaunchedEffect
                }
                while (isActive) {
                    val projected = savedMarkers.mapNotNull { marker ->
                        val screen = renderer.projectLocalPointToScreen(marker.localPosition)
                        screen?.let { (sx, sy) ->
                            OverlayMarker(
                                screenX = sx,
                                screenY = sy,
                                isAlive = marker.isAlive,
                                dbhCm = marker.dbhCm
                            )
                        }
                    }
                    markerOverlays = projected
                    delay(33)
                }
            }

            OverlayView(enrichedDetections, markerOverlays, 640, 640, Modifier.fillMaxSize())

            // Top-left help button
            Box(modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)) {
                Button(
                    onClick = { showHelp = true },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)),
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier
                        .size(40.dp)
                        .align(Alignment.TopStart)
                ) {
                    Text("?", style = MaterialTheme.typography.titleMedium, color = helpColor)
                }
            }

            // Enrich and persist detections when detection is running, boundary is visible, and we have detections
            // Process detections as they come in (real-time saving)
            LaunchedEffect(rawDetections, boundaryVisible, isDetecting) {
                if (isDetecting && boundaryVisible && rawDetections.isNotEmpty()) {
                    // Process and save detections to Firebase (only Rhizophora inside boundary)
                    processAndSaveDetections(rawDetections)
                }
            }

            // Center guide removed per request

            // ✅ Show "Start Detection" button only after boundary is visible
            if (boundaryVisible) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Button(
                        onClick = {
                            if (!isDetecting) {
                                startTrunkDetection()
                                isDetecting = true
                            }
                        },
                        enabled = !isDetecting,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier
                            .fillMaxWidth(0.8f)
                            .padding(bottom = 8.dp)
                    ) { 
                        Text("Start Detection") 
                    }

                    if (isDetecting) {
                        Button(
                            onClick = {
                                stopTrunkDetection()
                                isDetecting = false
                                goToDashboard()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.fillMaxWidth(0.8f)
                        ) { 
                            Text("End Detection") 
                        }
                    }
                }
            }

            // Help overlay modal
            if (showHelp) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "How to use AR Detection",
                                style = MaterialTheme.typography.titleLarge,
                                color = helpColor
                            )
                            Spacer(Modifier.height(8.dp))
                            Text("1. Move your phone to scan the surface until a gray mesh appears.", style = MaterialTheme.typography.bodyMedium, color = helpColor)
                            Spacer(Modifier.height(4.dp))
                            Text("2. Double-tap on the mesh to set the centerpoint (boundary will appear).", style = MaterialTheme.typography.bodyMedium, color = helpColor)
                            Spacer(Modifier.height(4.dp))
                            Text("3. Align the target trunk at the center of the screen.", style = MaterialTheme.typography.bodyMedium, color = helpColor)
                            Spacer(Modifier.height(4.dp))
                            Text("4. Tap 'Start Detection' to begin saving detections.", style = MaterialTheme.typography.bodyMedium, color = helpColor)
                            Spacer(Modifier.height(4.dp))
                            Text("5. Only Rhizophora inside the boundary will be saved.", style = MaterialTheme.typography.bodyMedium, color = helpColor)
                            Spacer(Modifier.height(12.dp))
                            Row(
                                horizontalArrangement = Arrangement.End,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                TextButton(onClick = { showHelp = false }) {
                                    Text("Close", color = helpColor)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun processAndSaveDetections(detections: List<Detection>) {
        try {
            val viewW = glSurfaceView.width.toFloat().takeIf { it > 0 } ?: return
            val viewH = glSurfaceView.height.toFloat().takeIf { it > 0 } ?: return
            val fx = renderer.getFxPixels() ?: return
            val userId = auth.currentUser?.uid ?: return

            // Ensure we have a valid anchor/centerpoint
            if (!renderer.isCenterpointSet()) {
                Log.w("ARActivity", "Cannot process detections: centerpoint not set")
                return
            }

            if (detections.isEmpty()) return

            // Calculate screen center coordinates
            val screenCenterX = viewW / 2f
            val screenCenterY = viewH / 2f

            // Find the detection closest to the center of the camera frame
            val scaleX = viewW / 640f
            val scaleY = viewH / 640f
            
            var closestDetection: Detection? = null
            var minDistance = Float.MAX_VALUE

            // Step 1: Find the trunk closest to center
            for (det in detections) {
                // Calculate detection center in screen coordinates
                val centerXImg = (det.box.left + det.box.right) / 2f
                val centerYImg = (det.box.top + det.box.bottom) / 2f
                val screenX = centerXImg * scaleX
                val screenY = centerYImg * scaleY

                // Calculate distance from detection center to screen center
                val dx = screenX - screenCenterX
                val dy = screenY - screenCenterY
                val distance = kotlin.math.sqrt(dx * dx + dy * dy)

                // Keep track of closest detection
                if (distance < minDistance) {
                    minDistance = distance
                    closestDetection = det
                }
            }

            // Step 2: Process only the closest detection (center of frame)
            val det = closestDetection ?: return
            
            try {
                // Step 1: Species Identification - Determine if Rhizophora
                // Label format: "Alive Rhizophora (85.3%)" or "Dead Rhizophora (90.1%)"
                val isRhizo = det.label.contains("Rhizophora", ignoreCase = true)
                // Step 2: Status Classification - Alive or Dead
                val isAlive = det.label.contains("Alive", ignoreCase = true)
                val isDead = det.label.contains("Dead", ignoreCase = true)
                
                // Calculate screen position for boundary check (recalculate for processing)
                val centerXImg = (det.box.left + det.box.right) / 2f
                val centerYImg = (det.box.top + det.box.bottom) / 2f
                val screenX = centerXImg * scaleX
                val screenY = centerYImg * scaleY

                val wp = renderer.worldPointAndDepthFromScreen(screenX, screenY)
                if (wp == null) {
                    // Can't determine position - skip
                    return
                }
                
                val localVec = wp.first
                val depth = wp.second
                
                // Validate localVec has 3 elements
                if (localVec.size < 3) {
                    Log.w("ARActivity", "Invalid localVec size: ${localVec.size}")
                    return
                }
                
                // Step 3: Boundary Check - Only process Rhizophora inside boundary
                val insideBoundary = renderer.isLocalPointInsideBoundary(localVec)
                
                // Only save Rhizophora trunks that are inside the boundary
                if (!isRhizo || !insideBoundary) {
                    return
                }

                // Create a unique ID for this detection based on position (to prevent duplicates)
                // Round position to ~10cm precision to avoid duplicate saves for same trunk
                val posKey = "${String.format("%.2f", localVec[0])}_${String.format("%.2f", localVec[1])}_${String.format("%.2f", localVec[2])}"
                if (savedDetectionIds.contains(posKey)) {
                    // Already saved this detection - skip
                    return
                }

                // Step 4: DBH Measurement using Edge Detection (only for alive trunks)
                var dbhCmForFirestore = 0f
                var leftEdgeX: Float? = null
                var rightEdgeX: Float? = null
                var edgeCenterY: Float? = null

                if (isAlive) {
                    // Get current camera frame bitmap for edge detection
                    val frame = renderer.getLastFrame()
                    if (frame != null) {
                        try {
                            val cameraImage = frame.acquireCameraImage()
                            val bitmap = trunkDetector?.yuvToRgbBitmap(cameraImage)
                            cameraImage.close()
                            
                            if (bitmap != null) {
                                // Detect edges in top half of bounding box
                                val edgeLines = EdgeDetection.detectTrunkEdges(bitmap, det.box)
                                
                                if (edgeLines != null) {
                                    leftEdgeX = edgeLines.leftEdgeX
                                    rightEdgeX = edgeLines.rightEdgeX
                                    edgeCenterY = edgeLines.centerY
                                    
                                    // Calculate DBH using edge distance and depth
                                    // Get depth at edge center point
                                    val edgeCenterScreenX = edgeLines.leftEdgeX * scaleX
                                    val edgeCenterScreenY = edgeLines.centerY * scaleY
                                    val edgeWp = renderer.worldPointAndDepthFromScreen(edgeCenterScreenX, edgeCenterScreenY)
                                    
                                    if (edgeWp != null) {
                                        val edgeDepth = edgeWp.second
                                        val fx = renderer.getFxPixels() ?: fx
                                        
                                        // Convert pixel width to real-world distance
                                        val dbhMeters = (edgeLines.edgeWidthPx * scaleX * edgeDepth / fx)
                                        dbhCmForFirestore = (dbhMeters * 100f).coerceIn(0f, 100f)
                                    } else {
                                        // Fallback: use original depth if edge depth unavailable
                                        val dbhMeters = (edgeLines.edgeWidthPx * scaleX * depth / fx)
                                        dbhCmForFirestore = (dbhMeters * 100f).coerceIn(0f, 100f)
                                    }
                                } else {
                                    // Fallback to original method if edge detection fails
                                    val boxWidthPxOnScreen = (det.box.right - det.box.left) * scaleX
                                    val dbhMeters = (boxWidthPxOnScreen * depth / fx)
                                    dbhCmForFirestore = (dbhMeters * 100f).coerceIn(0f, 100f)
                                }
                                
                                bitmap.recycle()
                            } else {
                                // Fallback if bitmap conversion fails
                                val boxWidthPxOnScreen = (det.box.right - det.box.left) * scaleX
                                val dbhMeters = (boxWidthPxOnScreen * depth / fx)
                                dbhCmForFirestore = (dbhMeters * 100f).coerceIn(0f, 100f)
                            }
                        } catch (e: Exception) {
                            Log.e("ARActivity", "Edge detection failed, using fallback", e)
                            // Fallback to original method
                            val boxWidthPxOnScreen = (det.box.right - det.box.left) * scaleX
                            val dbhMeters = (boxWidthPxOnScreen * depth / fx)
                            dbhCmForFirestore = (dbhMeters * 100f).coerceIn(0f, 100f)
                        }
                    } else {
                        // Fallback if frame not available
                        val boxWidthPxOnScreen = (det.box.right - det.box.left) * scaleX
                        val dbhMeters = (boxWidthPxOnScreen * depth / fx)
                        dbhCmForFirestore = (dbhMeters * 100f).coerceIn(0f, 100f)
                    }
                }

                // Step 5: Data Saving - Store in Firebase with proper structure
                val trunkId = "trunk_${java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 8)}"
                val now = Date()
                
                // Validate centerpoint connection exists before saving
                if (pinnedLocationDocId == null) {
                    Log.w("ARActivity", "Cannot save detection: centerpoint document ID not available")
                    return
                }
                
                val data = hashMapOf<String, Any>(
                    "user_id" to userId,
                    "pinned_location_id" to pinnedLocationDocId!!, // Explicit connection to centerpoint document
                    "pinned_location" to (pinnedLocationName ?: "Unknown Location"), // Name for readability
                    "inside_boundary" to true, // Always true since we filter before saving
                    "vector_position" to listOf(localVec[0], localVec[1], localVec[2]), // Relative to centerpoint (0,0,0)
                    "is_rhizophora" to 1,
                    "is_alive" to (if (isAlive) 1 else 0),
                    "dbh_cm" to dbhCmForFirestore,
                    "timestamp" to SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                        timeZone = TimeZone.getTimeZone("UTC")
                    }.format(now),
                    "timestamp_firestore" to com.google.firebase.Timestamp.now()
                )
                // Add barangay if available
                if (pinnedLocationBarangay != null) {
                    data["barangay"] = pinnedLocationBarangay!!
                }

                // Save to /trunk_detections/ collection
                db.collection("trunk_detections")
                    .document(trunkId)
                    .set(data)
                    .addOnSuccessListener {
                        savedDetectionIds.add(posKey) // Mark as saved
                        if (savedMarkers.none { it.id == posKey }) {
                            savedMarkers.add(
                                SavedDetectionMarker(
                                    id = posKey,
                                    localPosition = floatArrayOf(localVec[0], localVec[1], localVec[2]),
                                    isAlive = isAlive,
                                    dbhCm = if (dbhCmForFirestore > 0f) dbhCmForFirestore else null
                                )
                            )
                        }
                        Log.d("ARActivity", "Trunk detection saved (center of frame): $trunkId")
                    }
                    .addOnFailureListener { e ->
                        Log.e("ARActivity", "Failed to save detection", e)
                    }
            } catch (e: Exception) {
                Log.e("ARActivity", "Error processing detection", e)
            }
        } catch (e: Exception) {
            Log.e("ARActivity", "Error in processAndSaveDetections", e)
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
        
        // Step 1: AR scanning stops
        detectionRunning = false
        
        // Step 2: Process any final detections before stopping
        // (Note: Most detections are saved in real-time via LaunchedEffect,
        // but we ensure final batch is processed)
        val finalDetections = trunkDetector?.detections?.value ?: emptyList()
        if (finalDetections.isNotEmpty() && boundaryVisible) {
            processAndSaveDetections(finalDetections)
        }
        
        // Step 3: Stop the detector
        trunkDetector?.close()
        trunkDetector = null
        
        Toast.makeText(this, "Detection stopped", Toast.LENGTH_SHORT).show()
        
        // Step 4: Navigate back to HomeScreen
        goToDashboard()
    }

    private fun goToDashboard() {
        // Navigate back to HomeScreen (DashboardActivity)
        val intent = Intent(this, DashboardActivity::class.java)
        
        // Pass location information so DashboardActivity can show the detected trunk data
        if (pinnedLocationName != null) {
            intent.putExtra("showDetectedTrunkData", true)
            intent.putExtra("locationName", pinnedLocationName)
            intent.putExtra("locationLat", deviceStartLat)
            intent.putExtra("locationLon", deviceStartLon)
            if (pinnedLocationDocId != null) {
                intent.putExtra("locationDocId", pinnedLocationDocId)
            }
        }
        
        startActivity(intent)
        finish()
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

    private fun saveCenterpointToFirebase(hitPose: com.google.ar.core.Pose) {
        val userId = auth.currentUser?.uid ?: return
        
        // Get device's current GPS location
        getCurrentLocationForCenterpoint { lat, lon ->
            // Find the most recent pinned location for this session to link the centerpoint to it
            db.collection("users").document(userId)
                .collection("pinned_locations")
                .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(1)
                .get()
                .addOnSuccessListener { snapshot ->
                    val now = Date()
                    val centerpointData = hashMapOf<String, Any>(
                        "ar_pose_x" to hitPose.tx(),
                        "ar_pose_y" to hitPose.ty(),
                        "ar_pose_z" to hitPose.tz(),
                        "centerpoint_timestamp" to Timestamp.now(),
                        "centerpoint_timestamp_iso" to SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                            timeZone = TimeZone.getTimeZone("UTC")
                        }.format(now),
                        "centerpoint_device_lat" to lat,
                        "centerpoint_device_lon" to lon
                    )
                    
                    if (!snapshot.isEmpty) {
                        // Update the most recent pinned location with centerpoint data
                        val pinnedLocationDoc = snapshot.documents[0]
                        pinnedLocationName = pinnedLocationDoc.getString("name") // Store name for detections
                        pinnedLocationDocId = pinnedLocationDoc.id // Store document ID for explicit connection
                        pinnedLocationBarangay = pinnedLocationDoc.getString("barangay") // Store barangay for detections
                        pinnedLocationDoc.reference
                            .update(centerpointData)
                            .addOnSuccessListener {
                                Log.d("ARActivity", "Centerpoint linked to pinned location: ${pinnedLocationDoc.id}")
                            }
                            .addOnFailureListener { e ->
                                Log.e("ARActivity", "Failed to link centerpoint to pinned location", e)
                                Toast.makeText(this@ARActivity, "Failed to save centerpoint", Toast.LENGTH_SHORT).show()
                            }
                    } else {
                        // No pinned location found - create a new entry with both pinned and centerpoint data
                        val combinedData = hashMapOf<String, Any>(
                            "name" to "AR Centerpoint (No Map Pin)",
                            "latitude" to lat,
                            "longitude" to lon,
                            "address" to "Set via AR",
                            "timestamp" to Timestamp.now(),
                            "timestamp_iso" to SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                                timeZone = TimeZone.getTimeZone("UTC")
                            }.format(now)
                        )
                        combinedData.putAll(centerpointData)
                        
                        db.collection("users").document(userId)
                            .collection("pinned_locations")
                            .add(combinedData)
                            .addOnSuccessListener { documentReference ->
                                pinnedLocationDocId = documentReference.id // Store document ID for explicit connection
                                Log.d("ARActivity", "Centerpoint saved as new entry: ${documentReference.id}")
                            }
                            .addOnFailureListener { e ->
                                Log.e("ARActivity", "Failed to save centerpoint", e)
                                Toast.makeText(this@ARActivity, "Failed to save centerpoint", Toast.LENGTH_SHORT).show()
                            }
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("ARActivity", "Failed to query pinned locations", e)
                    Toast.makeText(this, "Failed to link centerpoint", Toast.LENGTH_SHORT).show()
                }
        }
    }
    
    private fun getCurrentLocationForCenterpoint(callback: (Double, Double) -> Unit) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // No location permission - use default coordinates (0, 0) or skip GPS
            callback(0.0, 0.0)
            return
        }
        
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        
        val locationListener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                callback(location.latitude, location.longitude)
                locationManager?.removeUpdates(this)
            }
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        }
        
        try {
            // Try to get last known location first (faster)
            val lastKnownLocation = locationManager?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: locationManager?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            
            if (lastKnownLocation != null) {
                callback(lastKnownLocation.latitude, lastKnownLocation.longitude)
            } else {
                // Request a fresh location update
                locationManager?.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    0L,
                    0f,
                    locationListener
                )
                locationManager?.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    0L,
                    0f,
                    locationListener
                )
                // Timeout after 3 seconds - use default coordinates
                Handler(Looper.getMainLooper()).postDelayed({
                    callback(0.0, 0.0)
                    locationManager?.removeUpdates(locationListener)
                }, 3000)
            }
        } catch (e: SecurityException) {
            Log.e("ARActivity", "Location permission error", e)
            callback(0.0, 0.0)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        locationManager = null
        renderer.onPause()
        arSession?.close()
        arSession = null
        trunkDetector?.close()
    }
}
