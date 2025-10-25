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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val auth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance()

    var mapView by remember { mutableStateOf<MapView?>(null) }
    var locationOverlay by remember { mutableStateOf<MyLocationNewOverlay?>(null) }
    var hasLocationPermission by remember { mutableStateOf(false) }
    var showConfirmDialog by remember { mutableStateOf(false) }
    var currentGeoPoint by remember { mutableStateOf<GeoPoint?>(null) }

    // Permission launcher
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

    // Check permission on load
    LaunchedEffect(Unit) {
        val fineGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        hasLocationPermission = fineGranted || coarseGranted
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Map view
        AndroidView(
            factory = { ctx ->
                MapView(ctx).apply {
                    Configuration.getInstance().load(ctx, ctx.getSharedPreferences("osmdroid", 0))
                    setTileSource(TileSourceFactory.MAPNIK)
                    setBuiltInZoomControls(true)
                    setMultiTouchControls(true)

                    // Limit scroll area (Puerto Princesa)
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

        // Confirmation dialog
        if (showConfirmDialog && currentGeoPoint != null) {
            AlertDialog(
                onDismissRequest = { showConfirmDialog = false },
                title = { Text("Save Centerpoint") },
                text = {
                    Column {
                        Text("Do you want to save your current location as the centerpoint?")
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Lat: ${String.format("%.6f", currentGeoPoint!!.latitude)}\n" +
                                    "Lon: ${String.format("%.6f", currentGeoPoint!!.longitude)}\n" +
                                    "Area: 500 sqm",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val loc = currentGeoPoint!!
                            val userId = auth.currentUser?.uid

                            if (userId != null) {
                                val locationData = hashMapOf(
                                    "name" to "Centerpoint",
                                    "address" to "Saved from current location",
                                    "latitude" to loc.latitude,
                                    "longitude" to loc.longitude,
                                    "timestamp" to System.currentTimeMillis()
                                )

                                db.collection("users")
                                    .document(userId)
                                    .collection("centerpoints")
                                    .add(locationData)
                                    .addOnSuccessListener {
                                        Toast.makeText(
                                            context,
                                            "Centerpoint saved to Firebase!",
                                            Toast.LENGTH_SHORT
                                        ).show()

                                        // Open ARActivity after successful save
                                        val intent = Intent(context, ARActivity::class.java)
                                        context.startActivity(intent)
                                    }
                                    .addOnFailureListener {
                                        Toast.makeText(
                                            context,
                                            "Failed to save centerpoint to Firebase.",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                            } else {
                                Toast.makeText(
                                    context,
                                    "No authenticated user found!",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }

                            showConfirmDialog = false
                        }
                    ) {
                        Text("Yes, Save & Open AR")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showConfirmDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Floating button for current location
        FloatingActionButton(
            onClick = {
                if (!hasLocationPermission) {
                    locationPermissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                    return@FloatingActionButton
                }

                val loc = locationOverlay?.myLocation
                if (loc != null) {
                    val geo = GeoPoint(loc.latitude, loc.longitude)
                    mapView?.controller?.setCenter(geo)
                    mapView?.controller?.setZoom(17.0)
                    currentGeoPoint = geo
                    showConfirmDialog = true
                } else {
                    Toast.makeText(
                        context,
                        "Getting current location... Please wait.",
                        Toast.LENGTH_SHORT
                    ).show()
                    locationOverlay?.enableMyLocation()
                    locationOverlay?.enableFollowLocation()
                }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            containerColor = MaterialTheme.colorScheme.primary
        ) {
            Icon(Icons.Filled.MyLocation, contentDescription = "Center on My Location")
        }
    }
}
