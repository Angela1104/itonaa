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
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.bakhawone.thesis_bakhawone.ui.theme.HomeScreen
import com.bakhawone.thesis_bakhawone.ui.theme.ThesisbakhawoneTheme
import androidx.compose.ui.graphics.vector.ImageVector
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

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun DashboardApp() {
    var selectedIndex by remember { mutableIntStateOf(0) }
    var selectedRecordTab by remember { mutableIntStateOf(0) }
    var selectedLocation by remember { mutableStateOf<PinnedLocation?>(null) }
    // mini records panel will expand/collapse instead of navigating or showing popups

    // Store pinned locations at the top level - this will be shared across all screens
    val pinnedLocations = remember { mutableStateListOf<PinnedLocation>() }

    val items = listOf(
        BottomNavItem("Home", Icons.Filled.Home),
        BottomNavItem("Record", Icons.Filled.List),
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
                    0 -> HomeScreen(
                        onLocationSelected = { location ->
                            selectedLocation = location
                            selectedRecordTab = 0
                            selectedIndex = 1
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

data class BottomNavItem(
    val title: String,
    val icon: ImageVector
)

// (MiniRecordsContainer removed; using inline MiniRecordsPanel navigation)
