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
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.bakhawone.thesis_bakhawone.R
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import com.bakhawone.thesis_bakhawone.ARActivity
import com.bakhawone.thesis_bakhawone.OSMGeocodingUtils
import com.bakhawone.thesis_bakhawone.PinnedLocation
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.Timestamp
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenInsights: ((PinnedLocation?) -> Unit)? = null,
    onCurrentLocationButtonReady: ((() -> Unit) -> Unit)? = null
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp
    val isTablet = minOf(configuration.screenWidthDp, configuration.screenHeightDp) >= 600
    val auth = remember { FirebaseAuth.getInstance() }
    val db = remember { FirebaseFirestore.getInstance() }
    val scope = rememberCoroutineScope()

    var mapView by remember { mutableStateOf<MapView?>(null) }
    var locationOverlay by remember { mutableStateOf<MyLocationNewOverlay?>(null) }
    var hasLocationPermission by remember { mutableStateOf(false) }
    var showNameInputDialog by remember { mutableStateOf(false) }
    var currentGeoPoint by remember { mutableStateOf<GeoPoint?>(null) }
        var geocodedAddress by remember { mutableStateOf<String?>(null) }
        var geocodeResult by remember { mutableStateOf<com.bakhawone.thesis_bakhawone.OSMGeocodingUtils.GeocodeResult?>(null) }
    var locationName by remember { mutableStateOf("") }
    var isGeocoding by remember { mutableStateOf(false) }
    var userId by remember { mutableStateOf<String?>(null) }
    
    val pinnedLocations = remember { mutableStateListOf<PinnedLocation>() }

    LaunchedEffect(Unit) {
        userId = auth.currentUser?.uid
        if (userId == null) {
            Toast.makeText(context, "Please sign in to continue", Toast.LENGTH_LONG).show()
        }
        
        val fineGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        hasLocationPermission = fineGranted || coarseGranted
        
        val uid = userId
        if (uid != null) {
            loadPinnedLocations(context, db, uid) { locations ->
                pinnedLocations.clear()
                pinnedLocations.addAll(locations)
            }
        }
    }
    
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
                    overlays.add(overlay)
                    locationOverlay = overlay
                    mapView = this
                    
                    if (hasLocationPermission) {
                        overlay.enableMyLocation()
                        overlay.enableFollowLocation()
                    }
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { view ->
                updatePinnedLocationMarkers(view, pinnedLocations) { location ->
                    onOpenInsights?.invoke(location)
                }
            }
        )
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(horizontal = if (isTablet) 24.dp else 16.dp, vertical = if (isLandscape) 8.dp else 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Image(
                painter = painterResource(id = R.drawable.bakhawone),
                contentDescription = "App Logo",
                modifier = Modifier
                    .size(if (isTablet) 48.dp else 40.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
            
            Spacer(modifier = Modifier.width(if (isTablet) 16.dp else 12.dp))
            
            Text(
                text = "BakhawOne",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                fontSize = if (isTablet) 24.sp else 20.sp
            )
        }

        if (showNameInputDialog && currentGeoPoint != null) {
            AlertDialog(
                onDismissRequest = { 
                    showNameInputDialog = false
                    currentGeoPoint = null
                    geocodedAddress = null
                    geocodeResult = null
                    locationName = ""
                },
                title = { 
                    Text(
                        "Enter Location Name",
                        style = MaterialTheme.typography.titleLarge
                    ) 
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = if (isTablet) 500.dp else screenWidth * 0.9f)
                    ) {
                        Text(
                            "Please enter a name for this location:",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = locationName,
                            onValueChange = { 
                                if (it.length <= 200) {
                                    locationName = it
                                }
                            },
                            label = { Text("Location Name") },
                            placeholder = { Text(geocodedAddress ?: "Enter name") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            supportingText = {
                                Text(
                                    text = "${locationName.length}/200",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (locationName.length > 200) MaterialTheme.colorScheme.error 
                                            else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            isError = locationName.length > 200
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Address: ${geocodedAddress ?: "Unknown"}\n" +
                                    "Coordinates: ${String.format("%.6f", currentGeoPoint!!.latitude)}, ${String.format("%.6f", currentGeoPoint!!.longitude)}",
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
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
                            
                            val trimmedName = locationName.trim()
                            if (trimmedName.length > 200) {
                                Toast.makeText(context, "Location name must be 200 characters or less", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            
                            val loc = currentGeoPoint!!
                            val name = trimmedName.take(200)
                            val address = geocodedAddress ?: "${loc.latitude}, ${loc.longitude}"
                            val barangay = geocodeResult?.barangay
                            val uid = userId ?: auth.currentUser?.uid
                            if (uid == null) {
                                Toast.makeText(context, "Please sign in to save locations", Toast.LENGTH_LONG).show()
                                return@Button
                            }
                            val now = Date()
                            val locationData = hashMapOf<String, Any>(
                                "name" to name,
                                "latitude" to loc.latitude,
                                "longitude" to loc.longitude,
                                "address" to address,
                                "timestamp" to Timestamp.now(),
                                "timestamp_iso" to SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                                    timeZone = TimeZone.getTimeZone("UTC")
                                }.format(now)
                            )
                            if (barangay != null) {
                                locationData["barangay"] = barangay
                            }
                            
                            db.collection("users").document(uid)
                                .collection("pinned_locations")
                                .add(locationData)
                                .addOnSuccessListener { documentReference ->
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
                                    geocodeResult = null
                                    locationName = ""
                                    Toast.makeText(context, "Location saved successfully", Toast.LENGTH_SHORT).show()
                                    
                                    val activity = context as? android.app.Activity
                                    activity?.let {
                                        val intent = Intent(it, ARActivity::class.java).apply {
                                            putExtra("deviceStartLat", loc.latitude)
                                            putExtra("deviceStartLon", loc.longitude)
                                            putExtra("locationName", name)
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
                            geocodeResult = null
                            locationName = ""
                        }) { Text("Cancel") }
                }
            )
        }


        if (isGeocoding) {
            AlertDialog(
                onDismissRequest = { 
                    isGeocoding = false
                    geocodeResult = null
                    geocodedAddress = currentGeoPoint?.let { "${it.latitude}, ${it.longitude}" }
                    showNameInputDialog = true
                },
                title = { 
                    Text(
                        "Getting Address",
                        style = MaterialTheme.typography.titleLarge
                    ) 
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = if (isTablet) 400.dp else screenWidth * 0.8f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "Looking up address for your location...",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                    }
                },
                confirmButton = { },
                dismissButton = {
                    TextButton(onClick = {
                        isGeocoding = false
                        geocodeResult = null
                        geocodedAddress = currentGeoPoint?.let { "${it.latitude}, ${it.longitude}" }
                        showNameInputDialog = true
                    }) {
                        Text("Cancel")
                    }
                }
            )
        }

        LaunchedEffect(mapView, locationOverlay, hasLocationPermission) {
            val handler: () -> Unit = handler@{
                if (!hasLocationPermission) {
                    locationPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                    return@handler
                }
                val loc = locationOverlay?.myLocation
                if (loc != null) {
                    val geo = GeoPoint(loc.latitude, loc.longitude)
                    mapView?.controller?.setCenter(geo)
                    mapView?.controller?.setZoom(17.0)
                    currentGeoPoint = geo
                    
                    isGeocoding = true
                    scope.launch {
                        try {
                            val result = OSMGeocodingUtils.reverseGeocodeWithBarangay(geo.latitude, geo.longitude)
                            geocodeResult = result
                            geocodedAddress = result?.address
                            locationName = result?.address?.take(200) ?: ""
                            isGeocoding = false
                            showNameInputDialog = true
                        } catch (e: Exception) {
                            isGeocoding = false
                            geocodeResult = null
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
            }
            onCurrentLocationButtonReady?.invoke(handler)
        }
    }
}

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

private fun createRedPinIcon(context: android.content.Context): Bitmap {
    val size = 100
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    
    val paint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }
    
    val redColor = android.graphics.Color.parseColor("#EA4335")
    
    val centerX = size / 2f
    val centerY = size / 2f - 5f
    val circleRadius = size * 0.2f
    
    paint.color = redColor
    canvas.drawCircle(centerX, centerY, circleRadius, paint)
    
    val path = Path()
    val bottomY = size * 0.95f
    val pinWidth = size * 0.25f
    
    path.moveTo(centerX, centerY + circleRadius)
    path.lineTo(centerX - pinWidth, bottomY)
    path.lineTo(centerX, bottomY - 3f)
    path.lineTo(centerX + pinWidth, bottomY)
    path.close()
    
    canvas.drawPath(path, paint)
    
    paint.color = android.graphics.Color.WHITE
    val highlightRadius = circleRadius * 0.4f
    canvas.drawCircle(centerX - circleRadius * 0.3f, centerY - circleRadius * 0.3f, highlightRadius, paint)
    
    return bitmap
}

private fun updatePinnedLocationMarkers(
    mapView: MapView?,
    pinnedLocations: List<PinnedLocation>,
    onMarkerClicked: ((PinnedLocation) -> Unit)? = null
) {
    if (mapView == null) return
    
    val overlaysToRemove = mutableListOf<org.osmdroid.views.overlay.Overlay>()
    mapView.overlays.forEach { overlay ->
        if (overlay is Marker && overlay.title?.startsWith("📍 ") == true) {
            overlaysToRemove.add(overlay)
        }
    }
    overlaysToRemove.forEach { mapView.overlays.remove(it) }
    
    val context = mapView.context
    val redPinIcon = createRedPinIcon(context)
    
    pinnedLocations.forEach { location ->
        val marker = Marker(mapView).apply {
            position = GeoPoint(location.latitude, location.longitude)
            title = "📍 ${location.name}"
            snippet = if (location.address.isNotBlank()) location.address else "${location.latitude}, ${location.longitude}"
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            isDraggable = false
            
            setIcon(android.graphics.drawable.BitmapDrawable(context.resources, redPinIcon))
            
            setOnMarkerClickListener { clickedMarker, mapView ->
                onMarkerClicked?.invoke(location)
                true
            }
        }
        mapView.overlays.add(marker)
    }
    
    mapView.invalidate()
}
