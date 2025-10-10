package com.bakhawone.thesis_bakhawone

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.bakhawone.thesis_bakhawone.ui.theme.ThesisbakhawoneTheme
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import androidx.compose.ui.graphics.vector.ImageVector
import org.osmdroid.util.BoundingBox
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.Marker
import androidx.compose.material3.OutlinedTextField
import org.osmdroid.views.overlay.Polygon
import android.graphics.Color
import kotlin.math.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

// Data class to store pinned locations with name/address
data class PinnedLocation(
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long = System.currentTimeMillis()
)

// Storage utility class
class LocationStorage(private val context: android.content.Context) {
    private val sharedPreferences = context.getSharedPreferences("pinned_locations", android.content.Context.MODE_PRIVATE)
    private val gson = Gson()
    private val key = "pinned_locations_list"

    fun saveLocations(locations: List<PinnedLocation>) {
        val jsonString = gson.toJson(locations)
        sharedPreferences.edit().putString(key, jsonString).apply()
    }

    fun loadLocations(): List<PinnedLocation> {
        val jsonString = sharedPreferences.getString(key, null)
        return if (jsonString != null) {
            val type = object : TypeToken<List<PinnedLocation>>() {}.type
            gson.fromJson(jsonString, type) ?: emptyList()
        } else {
            emptyList()
        }
    }

    fun clearLocations() {
        sharedPreferences.edit().remove(key).apply()
    }
}

class DashboardActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // OSMDroid config
        Configuration.getInstance().load(applicationContext, getPreferences(MODE_PRIVATE))

        setContent {
            ThesisbakhawoneTheme {
                DashboardApp()
            }
        }
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun DashboardApp() {
    var selectedIndex by remember { mutableIntStateOf(0) }
    var selectedRecordTab by remember { mutableIntStateOf(0) }
    val context = LocalContext.current

    // Store pinned locations at the top level with persistent storage
    val pinnedLocations = remember {
        mutableStateListOf<PinnedLocation>().apply {
            // Load initial locations from storage
            val storage = LocationStorage(context)
            addAll(storage.loadLocations())
        }
    }

    // Save locations whenever they change
    LaunchedEffect(pinnedLocations.size) {
        val storage = LocationStorage(context)
        storage.saveLocations(pinnedLocations)
    }

    val items = listOf(
        BottomNavItem("Home", Icons.Filled.Home),
        BottomNavItem("Record", Icons.AutoMirrored.Filled.List),
        BottomNavItem("Print", Icons.Filled.Print),
        BottomNavItem("Profile", Icons.Filled.Person)
    )

    Scaffold(
        bottomBar = {
            Column {
                if (selectedIndex == 0) {
                    MiniRecordsPanel(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .height(80.dp),
                        onQuickTabClick = { tabIndex ->
                            selectedRecordTab = tabIndex
                            selectedIndex = 1
                        }
                    )
                    Spacer(modifier = Modifier.height(15.dp))
                }

                NavigationBar(containerColor = MaterialTheme.colorScheme.background) {
                    items.forEachIndexed { index, item ->
                        NavigationBarItem(
                            icon = { Icon(item.icon, contentDescription = item.title) },
                            label = { Text(item.title) },
                            selected = selectedIndex == index,
                            onClick = { selectedIndex = index },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.background,
                                selectedTextColor = MaterialTheme.colorScheme.outline,
                                indicatorColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.secondary,
                                unselectedTextColor = MaterialTheme.colorScheme.secondary
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            AnimatedContent(
                targetState = selectedIndex,
                transitionSpec = {
                    if (targetState == 1 && initialState == 0) {
                        slideInVertically { height -> height } + fadeIn() with
                                slideOutVertically { height -> -height } + fadeOut()
                    } else if (targetState == 0 && initialState == 1) {
                        slideInVertically { height -> -height } + fadeIn() with
                                slideOutVertically { height -> height } + fadeOut()
                    } else {
                        fadeIn(tween(200)) with fadeOut(tween(200))
                    }
                },
                label = "ScreenTransition"
            ) { screenIndex ->
                when (screenIndex) {
                    0 -> HomeScreen(pinnedLocations = pinnedLocations)
                    1 -> RecordScreen(selectedRecordTab, pinnedLocations) { selectedRecordTab = it }
                    2 -> PrintScreen()
                    3 -> ProfileScreen()
                }
            }
        }
    }
}

@Composable
fun MiniRecordsPanel(
    modifier: Modifier = Modifier,
    onQuickTabClick: (Int) -> Unit
) {
    val subTabs = listOf(
        "Diagrams" to Icons.Default.BarChart,
        "Reports" to Icons.Default.Description,
        "GIS Map" to Icons.Default.Map
    )

    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            subTabs.forEachIndexed { index, pair ->
                val (label, icon) = pair
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onQuickTabClick(index) }
                        .padding(4.dp)
                ) {
                    Icon(
                        icon,
                        contentDescription = label,
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

data class BottomNavItem(
    val title: String,
    val icon: ImageVector
)

// Utility functions for geographic calculations
object GeoUtils {
    private const val EARTH_RADIUS_METERS = 6371000.0

    /**
     * Calculate the radius needed for a circle to have the specified area
     * Area = π * r², so r = √(Area / π)
     */
    fun calculateRadiusForArea(areaSqm: Double): Double {
        return sqrt(areaSqm / Math.PI)
    }

    /**
     * Calculate destination point given start point, distance and bearing
     */
    fun calculateDestinationPoint(start: GeoPoint, distance: Double, bearing: Double): GeoPoint {
        val lat1 = Math.toRadians(start.latitude)
        val lon1 = Math.toRadians(start.longitude)
        val angularDistance = distance / EARTH_RADIUS_METERS
        val bearingRad = Math.toRadians(bearing)

        val lat2 = asin(sin(lat1) * cos(angularDistance) +
                cos(lat1) * sin(angularDistance) * cos(bearingRad))

        val lon2 = lon1 + atan2(sin(bearingRad) * sin(angularDistance) * cos(lat1),
            cos(angularDistance) - sin(lat1) * sin(lat2))

        return GeoPoint(Math.toDegrees(lat2), Math.toDegrees(lon2))
    }

    /**
     * Create a circle polygon with specified center, radius and number of points
     */
    fun createCirclePolygon(center: GeoPoint, radiusMeters: Double, points: Int = 36): Polygon {
        val circlePoints = ArrayList<GeoPoint>()

        for (i in 0 until points) {
            val bearing = (360.0 * i) / points
            val point = calculateDestinationPoint(center, radiusMeters, bearing)
            circlePoints.add(point)
        }

        // Close the circle
        circlePoints.add(circlePoints[0])

        val polygon = Polygon()
        polygon.points = circlePoints
        polygon.fillColor = Color.argb(50, 0, 100, 255) // Semi-transparent blue
        polygon.strokeColor = Color.argb(180, 0, 0, 255) // Blue border
        polygon.strokeWidth = 3.0f
        polygon.title = "500 sqm Area"

        return polygon
    }
}

@Composable
fun HomeScreen(pinnedLocations: MutableList<PinnedLocation>) {
    val context = LocalContext.current
    var mapView by remember { mutableStateOf<MapView?>(null) }
    var locationOverlay by remember { mutableStateOf<MyLocationNewOverlay?>(null) }

    // State for location input dialog
    var showLocationDialog by remember { mutableStateOf(false) }
    var tempGeoPoint by remember { mutableStateOf<GeoPoint?>(null) }
    var locationName by remember { mutableStateOf("") }
    var locationAddress by remember { mutableStateOf("") }

    // Location permission state
    var hasLocationPermission by remember { mutableStateOf(false) }

    // Permission launcher
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseLocationGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false

        hasLocationPermission = fineLocationGranted || coarseLocationGranted

        if (hasLocationPermission) {
            locationOverlay?.enableMyLocation()
            locationOverlay?.enableFollowLocation()
            android.widget.Toast.makeText(context, "Location permission granted", android.widget.Toast.LENGTH_SHORT).show()
        } else {
            android.widget.Toast.makeText(context, "Location permission denied", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    // Check permission on startup
    LaunchedEffect(Unit) {
        val fineLocationPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarseLocationPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        hasLocationPermission = fineLocationPermission || coarseLocationPermission
    }

    // Function to handle location pinning
    fun handleLocationPinning(geoPoint: GeoPoint) {
        tempGeoPoint = geoPoint
        showLocationDialog = true
    }

    // Function to add marker and circle to map
    fun addLocationToMap(location: PinnedLocation) {
        mapView?.let { map ->
            // Add marker
            val marker = Marker(map)
            marker.position = GeoPoint(location.latitude, location.longitude)
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            marker.title = location.name
            marker.snippet = if (location.address.isNotBlank()) {
                "Address: ${location.address}\nLat: ${String.format("%.6f", location.latitude)}\nLon: ${String.format("%.6f", location.longitude)}"
            } else {
                "Lat: ${String.format("%.6f", location.latitude)}\nLon: ${String.format("%.6f", location.longitude)}"
            }
            marker.icon = context.getDrawable(android.R.drawable.ic_menu_mylocation)

            // Add 500 sqm circle
            val radius = GeoUtils.calculateRadiusForArea(500.0) // 500 sqm area
            val circle = GeoUtils.createCirclePolygon(
                center = GeoPoint(location.latitude, location.longitude),
                radiusMeters = radius
            )
            circle.title = "${location.name} - 500 sqm Area"

            map.overlays.add(marker)
            map.overlays.add(circle)
            map.invalidate()
        }
    }

    // Load existing pinned locations when map is ready
    LaunchedEffect(mapView, pinnedLocations.size) {
        mapView?.let { map ->
            // Clear existing overlays (except location overlay)
            val overlaysToKeep = map.overlays.filter { it is MyLocationNewOverlay }
            map.overlays.clear()
            map.overlays.addAll(overlaysToKeep)

            // Add all pinned locations with their circles
            pinnedLocations.forEach { location ->
                addLocationToMap(location)
            }
            map.invalidate()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                MapView(ctx).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setBuiltInZoomControls(true)
                    setMultiTouchControls(true)

                    // Restrict to Puerto Princesa
                    val puertoPrincesaBounds = BoundingBox(10.5, 118.85, 9.6, 117.8)
                    setScrollableAreaLimitDouble(puertoPrincesaBounds)
                    controller.setZoom(12.0)
                    controller.setCenter(GeoPoint(9.7439, 118.7357))

                    // User location overlay
                    val newLocationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(ctx), this)
                    if (hasLocationPermission) {
                        newLocationOverlay.enableMyLocation()
                        newLocationOverlay.enableFollowLocation()
                    }
                    overlays.add(newLocationOverlay)

                    locationOverlay = newLocationOverlay

                    // Double tap listener for pinning locations
                    val gestureDetector = android.view.GestureDetector(ctx, object : android.view.GestureDetector.SimpleOnGestureListener() {
                        override fun onDoubleTap(e: android.view.MotionEvent): Boolean {
                            val projection = this@apply.projection
                            val geoPoint = projection.fromPixels(e.x.toInt(), e.y.toInt()) as GeoPoint

                            // Use Handler to trigger from main thread
                            android.os.Handler(ctx.mainLooper).post {
                                handleLocationPinning(geoPoint)
                            }
                            return true
                        }
                    })

                    setOnTouchListener { _, event ->
                        gestureDetector.onTouchEvent(event)
                        false
                    }

                    // Update map center when first location is available
                    newLocationOverlay.runOnFirstFix {
                        newLocationOverlay.myLocation?.let { loc ->
                            controller.setCenter(GeoPoint(loc.latitude, loc.longitude))
                            postInvalidate()
                        }
                    }

                    mapView = this
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Location Input Dialog
        if (showLocationDialog && tempGeoPoint != null) {
            AlertDialog(
                onDismissRequest = {
                    showLocationDialog = false
                    locationName = ""
                    locationAddress = ""
                    tempGeoPoint = null
                },
                title = { Text("Add Location Details") },
                text = {
                    Column {
                        OutlinedTextField(
                            value = locationName,
                            onValueChange = { locationName = it },
                            label = { Text("Location Name *") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                            singleLine = true,
                            isError = locationName.isBlank()
                        )

                        OutlinedTextField(
                            value = locationAddress,
                            onValueChange = { locationAddress = it },
                            label = { Text("Address") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp),
                            singleLine = true
                        )

                        tempGeoPoint?.let { geoPoint ->
                            Text(
                                "Coordinates: ${String.format("%.6f", geoPoint.latitude)}, ${String.format("%.6f", geoPoint.longitude)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary
                            )

                            // Show calculated radius info
                            val radius = GeoUtils.calculateRadiusForArea(500.0)
                            Text(
                                "500 sqm area radius: ${String.format("%.1f", radius)} meters",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (locationName.isNotBlank() && tempGeoPoint != null) {
                                // Create new location
                                val newLocation = PinnedLocation(
                                    name = locationName,
                                    address = locationAddress,
                                    latitude = tempGeoPoint!!.latitude,
                                    longitude = tempGeoPoint!!.longitude
                                )

                                // Add to shared list
                                pinnedLocations.add(newLocation)

                                // Add marker and circle to map
                                addLocationToMap(newLocation)

                                // Show success message
                                android.widget.Toast.makeText(
                                    context,
                                    "Location '$locationName' pinned with 500 sqm area!",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()

                                // Reset dialog state
                                showLocationDialog = false
                                locationName = ""
                                locationAddress = ""
                                tempGeoPoint = null

                                // --- Launch CameraActivity immediately ---
                                val intent = android.content.Intent(context, CameraActivity::class.java)
                                context.startActivity(intent)
                            } else {
                                android.widget.Toast.makeText(
                                    context,
                                    "Please enter a location name",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        enabled = locationName.isNotBlank()
                    ) {
                        Text("Save Location")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showLocationDialog = false
                            locationName = ""
                            locationAddress = ""
                            tempGeoPoint = null
                        }
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Floating action button to show current location
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

                mapView?.let { map ->
                    val currentLocation = locationOverlay?.myLocation

                    if (currentLocation != null) {
                        map.controller.setCenter(GeoPoint(currentLocation.latitude, currentLocation.longitude))
                        map.controller.setZoom(16.0)
                    } else {
                        locationOverlay?.enableMyLocation()
                        locationOverlay?.enableFollowLocation()

                        android.widget.Toast.makeText(
                            context,
                            "Waiting for location... Make sure location is enabled",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()

                        map.handler.postDelayed({
                            locationOverlay?.myLocation?.let { loc ->
                                map.controller.setCenter(GeoPoint(loc.latitude, loc.longitude))
                                map.controller.setZoom(16.0)
                            }
                        }, 1000)
                    }
                }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            containerColor = MaterialTheme.colorScheme.primary
        ) {
            Icon(Icons.Filled.MyLocation, "Current Location")
        }

        // Permission request dialog
        if (!hasLocationPermission) {
            AlertDialog(
                onDismissRequest = { /* Don't allow dismiss without action */ },
                title = { Text("Location Permission Required") },
                text = { Text("This app needs location access to show your current position on the map and enable location pinning features.") },
                confirmButton = {
                    Button(
                        onClick = {
                            locationPermissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        }
                    ) {
                        Text("Grant Permission")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { /* User denied permission */ }
                    ) {
                        Text("Deny")
                    }
                }
            )
        }
    }
}

@Composable
fun RecordScreen(
    selectedTab: Int,
    pinnedLocations: List<PinnedLocation>,
    onTabSelected: (Int) -> Unit
) {
    val subTabs = listOf(
        "Diagrams" to Icons.Default.BarChart,
        "Reports" to Icons.Default.Description,
        "GIS Map" to Icons.Default.Map
    )

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            subTabs.forEachIndexed { index, pair ->
                val (label, icon) = pair
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clickable { onTabSelected(index) }
                        .padding(8.dp)
                ) {
                    Icon(
                        icon,
                        contentDescription = label,
                        tint = if (selectedTab == index) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(32.dp)
                    )
                    Text(
                        label,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (selectedTab == index) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }

        Divider(
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f),
            modifier = Modifier.padding(vertical = 8.dp)
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
        ) {
            when (selectedTab) {
                0 -> DiagramsScreen()
                1 -> ReportsScreen(pinnedLocations = pinnedLocations)
                2 -> GISScreen()
            }
        }
    }
}

@Composable
fun DiagramsScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text("Diagrams content")
    }
}

@Composable
fun ReportsScreen(pinnedLocations: List<PinnedLocation>) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            "Pinned Locations Report",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Debug: Show current count
        Text(
            "Total locations: ${pinnedLocations.size}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        if (pinnedLocations.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("No locations pinned yet. Go to Home screen and double-tap on the map to pin locations.")
            }
        } else {
            LazyColumn {
                items(pinnedLocations) { location ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        elevation = CardDefaults.cardElevation(2.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                location.name,
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )

                            if (location.address.isNotBlank()) {
                                Text(
                                    "Address: ${location.address}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                            }

                            Text(
                                "Latitude: ${String.format("%.6f", location.latitude)}",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(bottom = 2.dp)
                            )

                            Text(
                                "Longitude: ${String.format("%.6f", location.longitude)}",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )

                            // Show area information
                            val radius = GeoUtils.calculateRadiusForArea(500.0)
                            Text(
                                "Area: 500 sqm (Radius: ${String.format("%.1f", radius)}m)",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )

                            Text(
                                "Time: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(java.util.Date(location.timestamp))}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GISScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text("GIS Map content")
    }
}

@Composable
fun PrintScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text("Print content")
    }
}

@Composable
fun ProfileScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text("Profile content")
    }
}