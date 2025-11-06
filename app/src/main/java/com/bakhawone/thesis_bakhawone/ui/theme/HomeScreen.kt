package com.bakhawone.thesis_bakhawone.ui.theme

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.BitmapDrawable
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.bakhawone.thesis_bakhawone.ARActivity
import com.bakhawone.thesis_bakhawone.OSMGeocodingUtils
import com.bakhawone.thesis_bakhawone.PinnedLocation
// SessionManager removed: switch to user-based storage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.Timestamp
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HomeScreen(
    onLocationSelected: ((PinnedLocation) -> Unit)? = null
) {
    val context = LocalContext.current
    val auth = remember { FirebaseAuth.getInstance() }
    val db = remember { FirebaseFirestore.getInstance() }
    val scope = rememberCoroutineScope()

    var mapView by remember { mutableStateOf<MapView?>(null) }
    var locationOverlay by remember { mutableStateOf<MyLocationNewOverlay?>(null) }
    var hasLocationPermission by remember { mutableStateOf(false) }
    var showNameInputDialog by remember { mutableStateOf(false) }
    var currentGeoPoint by remember { mutableStateOf<GeoPoint?>(null) }
    var geocodedAddress by remember { mutableStateOf<String?>(null) }
    var locationName by remember { mutableStateOf("") }
    var isGeocoding by remember { mutableStateOf(false) }
    var userId by remember { mutableStateOf<String?>(null) }
    
    // Store pinned locations to display on map
    val pinnedLocations = remember { mutableStateListOf<PinnedLocation>() }
    
    // Dialog state for location tap action
    var clickedLocation by remember { mutableStateOf<PinnedLocation?>(null) }
    var showLocationActionDialog by remember { mutableStateOf(false) }

    // Initialize user and check permissions on launch
    LaunchedEffect(Unit) {
        userId = auth.currentUser?.uid
        if (userId == null) {
            Toast.makeText(context, "Please sign in to continue", Toast.LENGTH_LONG).show()
        }
        
        // Check initial permission state
        val fineGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        hasLocationPermission = fineGranted || coarseGranted
        
        // Load pinned locations from Firebase to display on map
        val uid = userId
        if (uid != null) {
            loadPinnedLocations(context, db, uid) { locations ->
                pinnedLocations.clear()
                pinnedLocations.addAll(locations)
            }
        }
    }
    
    // Reload pinned locations when user ID changes or becomes available
    LaunchedEffect(userId) {
        if (userId != null) {
            loadPinnedLocations(context, db, userId!!) { locations ->
                pinnedLocations.clear()
                pinnedLocations.addAll(locations)
            }
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasLocationPermission = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (hasLocationPermission) {
            locationOverlay?.enableMyLocation()
            locationOverlay?.enableFollowLocation()
            Toast.makeText(context, "Location permission granted", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Location permission denied", Toast.LENGTH_SHORT).show()
        }
    }
    
    // Update location overlay when permission state changes
    LaunchedEffect(hasLocationPermission) {
        if (hasLocationPermission) {
            locationOverlay?.enableMyLocation()
            locationOverlay?.enableFollowLocation()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                MapView(ctx).apply {
                    Configuration.getInstance().load(ctx, ctx.getSharedPreferences("osmdroid", 0))
                    setTileSource(TileSourceFactory.MAPNIK)
                    setBuiltInZoomControls(true)
                    setMultiTouchControls(true)
                    val bounds = BoundingBox(10.5, 118.85, 9.6, 117.8)
                    setScrollableAreaLimitDouble(bounds)
                    controller.setZoom(12.0)
                    controller.setCenter(GeoPoint(9.7439, 118.7357))

                    val overlay = MyLocationNewOverlay(GpsMyLocationProvider(ctx), this)
                    // Location will be enabled when hasLocationPermission state updates
                    overlays.add(overlay)
                    locationOverlay = overlay
                    mapView = this
                    
                    // Enable location if permission already granted
                    if (hasLocationPermission) {
                        overlay.enableMyLocation()
                        overlay.enableFollowLocation()
                    }
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { view ->
                // Update markers when pinned locations change
                updatePinnedLocationMarkers(view, pinnedLocations) { location ->
                    // When marker is clicked, show action dialog
                    clickedLocation = location
                    showLocationActionDialog = true
                }
            }
        )

        // Name input dialog - shown after geocoding is complete
        if (showNameInputDialog && currentGeoPoint != null) {
            AlertDialog(
                onDismissRequest = { 
                    showNameInputDialog = false
                    currentGeoPoint = null
                    geocodedAddress = null
                    locationName = ""
                },
                title = { Text("Enter Location Name") },
                text = {
                    Column {
                        Text("Please enter a name for this location:")
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = locationName,
                            onValueChange = { locationName = it },
                            label = { Text("Location Name") },
                            placeholder = { Text(geocodedAddress ?: "Enter name") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Address: ${geocodedAddress ?: "Unknown"}\n" +
                                    "Coordinates: ${String.format("%.6f", currentGeoPoint!!.latitude)}, ${String.format("%.6f", currentGeoPoint!!.longitude)}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (locationName.isBlank()) {
                                Toast.makeText(context, "Please enter a location name", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            
                            val loc = currentGeoPoint!!
                            val name = locationName.trim()
                            val address = geocodedAddress ?: "${loc.latitude}, ${loc.longitude}"
                            // Determine current user ID
                            val uid = userId ?: auth.currentUser?.uid
                            if (uid == null) {
                                Toast.makeText(context, "Please sign in to save locations", Toast.LENGTH_LONG).show()
                                return@Button
                            }
                            // Save pinned location to Firebase under /users/{uid}/pinned_locations/
                            val now = Date()
                            val locationData = hashMapOf(
                                "name" to name,
                                "latitude" to loc.latitude,
                                "longitude" to loc.longitude,
                                "address" to address,
                                "timestamp" to Timestamp.now(), // Firestore Timestamp for querying
                                "timestamp_iso" to SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                                    timeZone = TimeZone.getTimeZone("UTC")
                                }.format(now) // ISO string for human readability
                            )
                            
                            db.collection("users").document(uid)
                                .collection("pinned_locations")
                                .add(locationData)
                                .addOnSuccessListener { documentReference ->
                                    // Add to local list to update map immediately
                                    val newPinnedLocation = PinnedLocation(
                                        name = name,
                                        address = address,
                                        latitude = loc.latitude,
                                        longitude = loc.longitude,
                                        timestamp = System.currentTimeMillis()
                                    )
                                    pinnedLocations.add(newPinnedLocation)
                                    
                                    showNameInputDialog = false
                                    currentGeoPoint = null
                                    geocodedAddress = null
                                    locationName = ""
                                    Toast.makeText(context, "Location saved successfully", Toast.LENGTH_SHORT).show()
                                    
                                    // Transition to AR Activity
                                    val activity = context as? android.app.Activity
                                    activity?.let {
                                        val intent = Intent(it, ARActivity::class.java).apply {
                                            putExtra("deviceStartLat", loc.latitude)
                                            putExtra("deviceStartLon", loc.longitude)
                                            putExtra("locationName", name) // Pass location name for easier access
                                        }
                                        it.startActivity(intent)
                                    }
                                }
                                .addOnFailureListener { e ->
                                    Toast.makeText(context, "Failed to save location. Please try again.", Toast.LENGTH_LONG).show()
                                    android.util.Log.e("HomeScreen", "Failed to save location", e)
                                }
                        },
                        enabled = locationName.isNotBlank()
                    ) { Text("Save & Open AR") }
                },
                dismissButton = {
                    TextButton(onClick = { 
                        showNameInputDialog = false
                        currentGeoPoint = null
                        geocodedAddress = null
                        locationName = ""
                    }) { Text("Cancel") }
                }
            )
        }

        // Location action dialog (Detect Again or View Records)
        if (showLocationActionDialog && clickedLocation != null) {
            AlertDialog(
                onDismissRequest = { 
                    showLocationActionDialog = false
                    clickedLocation = null
                },
                title = null,
                text = {
                    if (clickedLocation!!.address.isNotBlank()) {
                        Text(
                            clickedLocation!!.address,
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp,
                            maxLines = 5,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                // Detect Again - Launch AR Activity
                                val location = clickedLocation!!
                                val intent = Intent(context, ARActivity::class.java).apply {
                                    putExtra("deviceStartLat", location.latitude)
                                    putExtra("deviceStartLon", location.longitude)
                                    putExtra("locationName", location.name)
                                }
                                context.startActivity(intent)
                                showLocationActionDialog = false
                                clickedLocation = null
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Detect Again", fontSize = 12.sp)
                        }
                        Button(
                            onClick = {
                                // View Records - Navigate to RecordScreen
                                onLocationSelected?.invoke(clickedLocation!!)
                                showLocationActionDialog = false
                                clickedLocation = null
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("View Records", fontSize = 12.sp)
                        }
                    }
                }
            )
        }

        // Geocoding progress dialog
        if (isGeocoding) {
            AlertDialog(
                onDismissRequest = { 
                    // Allow cancellation by stopping geocoding
                    isGeocoding = false
                    geocodedAddress = currentGeoPoint?.let { "${it.latitude}, ${it.longitude}" }
                    showNameInputDialog = true
                },
                title = { Text("Getting Address") },
                text = {
                    Column {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                        Spacer(Modifier.height(16.dp))
                        Text("Looking up address for your location...")
                    }
                },
                confirmButton = { },
                dismissButton = {
                    TextButton(onClick = {
                        isGeocoding = false
                        geocodedAddress = currentGeoPoint?.let { "${it.latitude}, ${it.longitude}" }
                        showNameInputDialog = true
                    }) {
                        Text("Cancel")
                    }
                }
            )
        }

        FloatingActionButton(
            onClick = {
                if (!hasLocationPermission) {
                    locationPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                    return@FloatingActionButton
                }
                val loc = locationOverlay?.myLocation
                if (loc != null) {
                    val geo = GeoPoint(loc.latitude, loc.longitude)
                    mapView?.controller?.setCenter(geo)
                    mapView?.controller?.setZoom(17.0)
                    currentGeoPoint = geo
                    
                    // Start reverse geocoding
                    isGeocoding = true
                    scope.launch {
                        try {
                            val address = OSMGeocodingUtils.reverseGeocode(geo.latitude, geo.longitude)
                            geocodedAddress = address
                            // Pre-fill location name with address if available
                            locationName = address?.take(50) ?: ""
                            isGeocoding = false
                            showNameInputDialog = true
                        } catch (e: Exception) {
                            isGeocoding = false
                            geocodedAddress = "${geo.latitude}, ${geo.longitude}"
                            showNameInputDialog = true
                            Toast.makeText(context, "Could not fetch address, using coordinates", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    Toast.makeText(context, "Getting current location...", Toast.LENGTH_SHORT).show()
                    locationOverlay?.enableMyLocation()
                    locationOverlay?.enableFollowLocation()
                }
            },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            containerColor = MaterialTheme.colorScheme.primary
        ) {
            Icon(Icons.Filled.MyLocation, contentDescription = "Center on My Location")
        }
    }
}

/**
 * Load pinned locations from Firebase and call callback with the list
 */
private fun loadPinnedLocations(
    context: android.content.Context,
    db: FirebaseFirestore,
    userId: String,
    callback: (List<PinnedLocation>) -> Unit
) {
    db.collection("users").document(userId)
        .collection("pinned_locations")
        .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
        .get()
        .addOnSuccessListener { snapshot ->
            val locations = snapshot.documents.mapNotNull { doc ->
                try {
                    val timestamp = doc.getTimestamp("timestamp")
                    PinnedLocation(
                        name = doc.getString("name") ?: "Unknown",
                        address = doc.getString("address") ?: "",
                        latitude = doc.getDouble("latitude") ?: 0.0,
                        longitude = doc.getDouble("longitude") ?: 0.0,
                        timestamp = timestamp?.toDate()?.time ?: System.currentTimeMillis()
                    )
                } catch (e: Exception) {
                    android.util.Log.e("HomeScreen", "Error parsing pinned location: ${doc.id}", e)
                    null
                }
            }
            callback(locations)
            android.util.Log.d("HomeScreen", "Loaded ${locations.size} pinned locations")
        }
        .addOnFailureListener { e ->
            android.util.Log.e("HomeScreen", "Failed to load pinned locations", e)
            callback(emptyList())
        }
}

/**
 * Create a red pin icon bitmap similar to Google Maps
 */
private fun createRedPinIcon(context: android.content.Context): Bitmap {
    val size = 100 // Size of the pin icon
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    
    val paint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }
    
    // Red color for the pin (Google Maps red: #EA4335)
    val redColor = android.graphics.Color.parseColor("#EA4335")
    
    // Draw pin shape: circle on top + teardrop/triangle on bottom
    val centerX = size / 2f
    val centerY = size / 2f - 5f // Slightly above center
    val circleRadius = size * 0.2f
    
    // Draw the circle (pin head)
    paint.color = redColor
    canvas.drawCircle(centerX, centerY, circleRadius, paint)
    
    // Draw the teardrop shape (pin body)
    val path = Path()
    val bottomY = size * 0.95f
    val pinWidth = size * 0.25f
    
    path.moveTo(centerX, centerY + circleRadius) // Start from bottom of circle
    path.lineTo(centerX - pinWidth, bottomY) // Left side of teardrop
    path.lineTo(centerX, bottomY - 3f) // Bottom point (slightly rounded)
    path.lineTo(centerX + pinWidth, bottomY) // Right side of teardrop
    path.close()
    
    canvas.drawPath(path, paint)
    
    // Add a white circle in the center for the pin highlight
    paint.color = android.graphics.Color.WHITE
    val highlightRadius = circleRadius * 0.4f
    canvas.drawCircle(centerX - circleRadius * 0.3f, centerY - circleRadius * 0.3f, highlightRadius, paint)
    
    return bitmap
}

/**
 * Update markers on map for pinned locations
 */
private fun updatePinnedLocationMarkers(
    mapView: MapView?,
    pinnedLocations: List<PinnedLocation>,
    onMarkerClicked: ((PinnedLocation) -> Unit)? = null
) {
    if (mapView == null) return
    
    // Remove all existing pinned location markers (keep my location overlay)
    val overlaysToRemove = mutableListOf<org.osmdroid.views.overlay.Overlay>()
    mapView.overlays.forEach { overlay ->
        if (overlay is Marker && overlay.title?.startsWith("📍 ") == true) {
            overlaysToRemove.add(overlay)
        }
    }
    overlaysToRemove.forEach { mapView.overlays.remove(it) }
    
    // Create red pin icon once (reuse for all markers)
    val context = mapView.context
    val redPinIcon = createRedPinIcon(context)
    
    // Add markers for each pinned location
    pinnedLocations.forEach { location ->
        val marker = Marker(mapView).apply {
            position = GeoPoint(location.latitude, location.longitude)
            title = "📍 ${location.name}"
            snippet = if (location.address.isNotBlank()) location.address else "${location.latitude}, ${location.longitude}"
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            isDraggable = false
            
            // Set red pin icon
            setIcon(android.graphics.drawable.BitmapDrawable(context.resources, redPinIcon))
            
            // Handle marker click to show action dialog
            setOnMarkerClickListener { clickedMarker, mapView ->
                onMarkerClicked?.invoke(location)
                true // Return true to indicate we handled the click
            }
        }
        mapView.overlays.add(marker)
    }
    
    // Refresh map to show markers
    mapView.invalidate()
}