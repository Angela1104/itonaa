package com.bakhawone.thesis_bakhawone.ui.theme

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.bakhawone.thesis_bakhawone.ARActivity
import com.bakhawone.thesis_bakhawone.OSMGeocodingUtils
import com.bakhawone.thesis_bakhawone.SessionManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.Timestamp
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HomeScreen() {
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
    var sessionId by remember { mutableStateOf<String?>(null) }

    // Get session ID on launch
    LaunchedEffect(Unit) {
        sessionId = SessionManager.getOrCreateSessionId(context)
        
        if (auth.currentUser == null) {
            auth.signInAnonymously()
                .addOnSuccessListener {
                    Toast.makeText(context, "Anonymous session started", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener {
                    Toast.makeText(context, "Anonymous sign-in failed: ${it.message}", Toast.LENGTH_LONG).show()
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

    LaunchedEffect(Unit) {
        val fineGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        hasLocationPermission = fineGranted || coarseGranted
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
                    if (hasLocationPermission) {
                        overlay.enableMyLocation()
                        overlay.enableFollowLocation()
                    }
                    overlays.add(overlay)
                    locationOverlay = overlay
                    mapView = this
                }
            },
            modifier = Modifier.fillMaxSize()
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
                            val sId = sessionId ?: SessionManager.getOrCreateSessionId(context)
                            
                            // Save pinned location to Firebase under /devices/{session_id}/pinned_locations/
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
                            
                            db.collection("devices").document(sId)
                                .collection("pinned_locations")
                                .add(locationData)
                                .addOnSuccessListener { documentReference ->
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
                                        }
                                        it.startActivity(intent)
                                    }
                                }
                                .addOnFailureListener { e ->
                                    Toast.makeText(context, "Failed to save location: ${e.message}", Toast.LENGTH_LONG).show()
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

        // Geocoding progress dialog
        if (isGeocoding) {
            AlertDialog(
                onDismissRequest = { },
                title = { Text("Getting Address") },
                text = {
                    Column {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                        Spacer(Modifier.height(16.dp))
                        Text("Looking up address for your location...")
                    }
                },
                confirmButton = { }
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