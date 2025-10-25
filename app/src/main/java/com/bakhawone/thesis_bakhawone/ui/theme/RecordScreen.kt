package com.bakhawone.thesis_bakhawone.ui.theme

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.util.BoundingBox
import androidx.compose.foundation.background
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import androidx.compose.ui.viewinterop.AndroidView
import com.bakhawone.thesis_bakhawone.GeoUtils
import com.bakhawone.thesis_bakhawone.PinnedLocation
import java.text.SimpleDateFormat
import java.util.Date

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
            modifier = Modifier.padding(vertical = 0.dp)
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
                items(pinnedLocations.size) { index ->
                    val location = pinnedLocations[index]
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
                                "Time: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Date(location.timestamp))}",
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
    val context = LocalContext.current
    // Height of your subtab row + padding + divider (adjust if needed)
    val topBoundary = 0.dp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = topBoundary)
            .background(MaterialTheme.colorScheme.background)
    ) {
        AndroidView(
            factory = { ctx ->
                MapView(ctx).apply {
                    // Standard OSM setup
                    setTileSource(TileSourceFactory.MAPNIK)
                    setBuiltInZoomControls(true)
                    setMultiTouchControls(true)

                    // Optional: restrict area (Puerto Princesa)
                    val puertoPrincesaBounds = BoundingBox(10.5, 118.85, 9.6, 117.8)
                    setScrollableAreaLimitDouble(puertoPrincesaBounds)

                    // Default center + zoom
                    controller.setZoom(12.0)
                    controller.setCenter(GeoPoint(9.7439, 118.7357))

                    // Important: clip the view so it doesn't overlap
                    clipToOutline = true
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}
