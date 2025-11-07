package com.bakhawone.thesis_bakhawone

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.with
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.bakhawone.thesis_bakhawone.ui.theme.HomeScreen
import com.bakhawone.thesis_bakhawone.ui.theme.ThesisbakhawoneTheme
import com.bakhawone.thesis_bakhawone.ui.theme.PrintScreen
import com.bakhawone.thesis_bakhawone.ui.theme.ProfileScreen
import com.bakhawone.thesis_bakhawone.ui.theme.RecordScreen
import com.bakhawone.thesis_bakhawone.ui.theme.DiagramsScreen
import com.bakhawone.thesis_bakhawone.ui.theme.ReportsScreen
import com.bakhawone.thesis_bakhawone.ui.theme.GISScreen
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Polygon
import kotlin.math.*
import java.util.*

// Data model shared at top-level (kept here so Dashboard and other screens can reference)
data class PinnedLocation(
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long = System.currentTimeMillis()
)

// Utility functions kept here (same as your original)
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
        polygon.fillColor = 0x320064FF.toInt() // Semi-transparent blue using integer color
        polygon.strokeColor = 0xB40000FF.toInt() // Blue border using integer color
        polygon.strokeWidth = 3.0f
        polygon.title = "1000 sqm Area"

        return polygon
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

    override fun onResume() {
        super.onResume()
    }

    override fun onPause() {
        super.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}

@OptIn(ExperimentalAnimationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun DashboardApp() {
    var selectedIndex by remember { mutableIntStateOf(0) }
    var selectedRecordTab by remember { mutableIntStateOf(0) }
    var selectedLocation by remember { mutableStateOf<PinnedLocation?>(null) }
    // mini records panel will expand/collapse instead of navigating or showing popups

    // Store pinned locations at the top level - this will be shared across all screens
    val pinnedLocations = remember { mutableStateListOf<PinnedLocation>() }
    
    // Bottom sheet state for Reports/Diagrams/GIS
    var selectedInsightTab by remember { mutableIntStateOf(0) } // 0=Diagrams, 1=Reports, 2=GIS
    var selectedLocationForInsights by remember { mutableStateOf<PinnedLocation?>(null) }
    
    // Current location button handler from HomeScreen
    var currentLocationClickHandler by remember { mutableStateOf<(() -> Unit)?>(null) }
    
    // Bottom sheet offset for drag-to-expand behavior - always visible in collapsed state
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val navigationBarHeight = 80.dp // Approximate height of navigation bar
    val screenHeight = with(density) { configuration.screenHeightDp.dp.toPx() }
    val navBarHeightPx = with(density) { navigationBarHeight.toPx() }
    val maxSheetHeight = screenHeight - navBarHeightPx
    var sheetOffsetY by remember { mutableFloatStateOf(maxSheetHeight * 0.80f) } // Start in collapsed/peek state (20% visible)
    
    // Smooth animated offset for better drag experience
    val animatedSheetOffsetY = androidx.compose.animation.core.animateFloatAsState(
        targetValue = sheetOffsetY,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessLow
        ),
        label = "sheetOffset"
    )
    
    // Load pinned locations
    val auth = remember { FirebaseAuth.getInstance() }
    val db = remember { FirebaseFirestore.getInstance() }
    LaunchedEffect(Unit) {
        val userId = auth.currentUser?.uid
        if (userId != null) {
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
                            null
                        }
                    }
                    pinnedLocations.clear()
                    pinnedLocations.addAll(locations)
                }
        }
    }

    val items = listOf(
        BottomNavItem("Home", Icons.Filled.Home),
        BottomNavItem("Record", Icons.Filled.List),
        BottomNavItem("Print", Icons.Filled.Print),
        BottomNavItem("Profile", Icons.Filled.Person)
    )

    Scaffold(
        bottomBar = {
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
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
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
                        0 -> HomeScreen(
                            onLocationSelected = { location ->
                                selectedLocation = location
                                selectedRecordTab = 0
                                selectedIndex = 1
                            },
                            onOpenInsights = { location ->
                                selectedLocationForInsights = location
                                selectedInsightTab = 0 // Start with Diagrams
                                // Expand sheet to 50% (half position) to show details
                                sheetOffsetY = maxSheetHeight * 0.5f
                            },
                            onCurrentLocationButtonReady = { handler ->
                                currentLocationClickHandler = handler
                            }
                        )
                        1 -> RecordScreen(
                            selectedTab = selectedRecordTab,
                            pinnedLocations = pinnedLocations,
                            selectedLocation = selectedLocation,
                            onTabSelected = { selectedRecordTab = it },
                            onLocationCleared = { selectedLocation = null }
                        )
                        2 -> PrintScreen()
                        3 -> ProfileScreen()
                    }
                }
            }
            
            // Collapsible bottom sheet for Reports/Diagrams/GIS - only visible on Home screen
            if (selectedIndex == 0) {
                Box(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Bottom sheet content - positioned above navigation bar
                    val sheetHeight = androidx.compose.animation.core.animateDpAsState(
                        targetValue = with(density) { (maxSheetHeight - animatedSheetOffsetY.value).toDp() },
                        label = "sheetHeight"
                    )
                    
                    // Calculate FAB visibility and position
                    val isSheetFullyExpanded = animatedSheetOffsetY.value < maxSheetHeight * 0.1f // Hide when less than 10% offset
                    // Position FAB on top of the sheet - follows the sheet's top edge
                    // FAB should be positioned above the sheet: bottom - navBar - sheetHeight - FAB_height - padding
                    val fabHeight = 56.dp // Standard FAB height
                    val fabPadding = 16.dp
                    val fabOffsetY = -navigationBarHeight - sheetHeight.value - fabHeight - fabPadding
                    
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(sheetHeight.value)
                            .align(Alignment.BottomCenter)
                            .offset(y = -navigationBarHeight) // Position above navigation bar
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragEnd = {
                                        // Snap to nearest position: collapsed (80%), half (50%), or expanded (0%)
                                        val currentPercent = sheetOffsetY / maxSheetHeight
                                        sheetOffsetY = when {
                                            currentPercent > 0.65f -> maxSheetHeight * 0.80f // Collapsed (20% visible)
                                            currentPercent > 0.25f -> maxSheetHeight * 0.5f  // Half (50% visible)
                                            else -> 0f // Expanded (100% visible)
                                        }
                                    }
                                ) { change, dragAmount ->
                                    sheetOffsetY = (sheetOffsetY + dragAmount.y).coerceIn(0f, maxSheetHeight)
                                }
                            },
                        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFF9F9F9)
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize()
                        ) {
                            // Drag handle
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp)
                                    .pointerInput(Unit) {
                                        detectDragGestures(
                                            onDragEnd = {
                                                // Snap to nearest position: collapsed (80%), half (50%), or expanded (0%)
                                                val currentPercent = sheetOffsetY / maxSheetHeight
                                                sheetOffsetY = when {
                                                    currentPercent > 0.65f -> maxSheetHeight * 0.80f // Collapsed (20% visible)
                                                    currentPercent > 0.25f -> maxSheetHeight * 0.5f  // Half (50% visible)
                                                    else -> 0f // Expanded (100% visible)
                                                }
                                            }
                                        ) { change, dragAmount ->
                                            sheetOffsetY = (sheetOffsetY + dragAmount.y).coerceIn(0f, maxSheetHeight)
                                        }
                                    },
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(40.dp)
                                        .height(4.dp)
                                        .background(
                                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                            RoundedCornerShape(2.dp)
                                        )
                                )
                            }
                            
                            HomeInsightsBottomSheet(
                                selectedTab = selectedInsightTab,
                                onTabSelected = { selectedInsightTab = it },
                                pinnedLocations = pinnedLocations,
                                selectedLocation = selectedLocationForInsights
                            )
                        }
                    }
                    
                    // FAB button on top of the sheet - follows the sheet and disappears when fully expanded
                    if (!isSheetFullyExpanded) {
                        androidx.compose.animation.AnimatedVisibility(
                            visible = !isSheetFullyExpanded,
                            enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.slideInVertically(),
                            exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.slideOutVertically()
                        ) {
                            FloatingActionButton(
                                onClick = {
                                    // Expand to full screen
                                    sheetOffsetY = 0f
                                },
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .offset(y = fabOffsetY)
                                    .padding(start = 16.dp),
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.BarChart,
                                    contentDescription = "View Insights"
                                )
                            }
                        }
                    }
                    
                    // Current location FAB button - positioned at the top of the collapsible sheet with a little space
                    // Position: above the sheet's top edge with small padding, adjusts as sheet expands/collapses
                    val currentLocationFabOffsetY = -navigationBarHeight - sheetHeight.value - 8.dp
                    FloatingActionButton(
                        onClick = {
                            currentLocationClickHandler?.invoke()
                        },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .offset(y = currentLocationFabOffsetY)
                            .padding(end = 16.dp),
                        containerColor = MaterialTheme.colorScheme.primary
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MyLocation,
                            contentDescription = "Center on My Location"
                        )
                    }
                }
            }
        }
    }

}

/**
 * Bottom sheet content for Reports/Diagrams/GIS insights
 */
@Composable
fun HomeInsightsBottomSheet(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    pinnedLocations: List<PinnedLocation>,
    selectedLocation: PinnedLocation?
) {
    val subTabs: List<Triple<String, ImageVector, ImageVector>> = listOf(
        Triple("Diagrams", Icons.Filled.BarChart, Icons.Outlined.BarChart),
        Triple("Reports", Icons.Filled.Description, Icons.Outlined.Description),
        Triple("GIS Map", Icons.Filled.Map, Icons.Outlined.Map)
    )
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.95f)
    ) {
        // Navigation buttons row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            subTabs.forEachIndexed { index: Int, triple: Triple<String, ImageVector, ImageVector> ->
                val (label: String, filledIcon: ImageVector, outlinedIcon: ImageVector) = triple
                val isSelected = selectedTab == index
                
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClick = { onTabSelected(index) })
                        .padding(8.dp)
                ) {
                    Icon(
                        imageVector = if (isSelected) filledIcon else outlinedIcon,
                        contentDescription = label,
                        tint = if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), // Gray color
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f) // Gray color
                    )
                }
            }
        }
        
        Divider(
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f)
        )
        
        // Content area - scrollable
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            when (selectedTab) {
                0 -> DiagramsScreen(selectedLocation = selectedLocation)
                1 -> ReportsScreen(
                    pinnedLocations = pinnedLocations,
                    selectedLocation = selectedLocation
                )
                2 -> GISScreen(selectedLocation = selectedLocation)
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
        elevation = CardDefaults.cardElevation(10.dp),
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

// (MiniRecordsContainer removed; using inline MiniRecordsPanel navigation)

data class BottomNavItem(
    val title: String,
    val icon: ImageVector
)
