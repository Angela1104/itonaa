package com.bakhawone.thesis_bakhawone.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.Icons.Default
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.util.BoundingBox
import org.osmdroid.views.overlay.Polygon
import androidx.compose.foundation.background
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import androidx.compose.ui.viewinterop.AndroidView
import com.bakhawone.thesis_bakhawone.GeoUtils
import com.bakhawone.thesis_bakhawone.PinnedLocation
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.*
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Calendar
import java.util.TimeZone

@Composable
fun RecordScreen(
    selectedTab: Int,
    pinnedLocations: List<PinnedLocation>,
    selectedLocation: PinnedLocation? = null,
    onTabSelected: (Int) -> Unit,
    onLocationCleared: () -> Unit = {}
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
fun DiagramsScreen(selectedLocation: PinnedLocation? = null) {
    val context = LocalContext.current
    val db = remember { FirebaseFirestore.getInstance() }
    val userId = remember { FirebaseAuth.getInstance().currentUser?.uid ?: "" }
    
    var monthDataList by remember { mutableStateOf<List<MonthData>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var pinnedLocationDocId by remember { mutableStateOf<String?>(null) }
    var selectedYear by remember { mutableStateOf<Int?>(null) }
    
    // Find pinned_location_id from Firebase and load data grouped by month
    LaunchedEffect(selectedLocation) {
        if (selectedLocation != null && userId.isNotEmpty()) {
            isLoading = true
            try {
                // Query to find the pinned location document ID
                val snapshot = db.collection("users").document(userId)
                    .collection("pinned_locations")
                    .whereEqualTo("name", selectedLocation.name)
                    .whereEqualTo("latitude", selectedLocation.latitude)
                    .whereEqualTo("longitude", selectedLocation.longitude)
                    .limit(1)
                    .get()
                    .await()
                
                if (!snapshot.isEmpty) {
                    pinnedLocationDocId = snapshot.documents[0].id
                    
                    // Query trunk detections for this location
                    val trunkSnapshot = db.collection("trunk_detections")
                        .whereEqualTo("pinned_location_id", pinnedLocationDocId)
                        .whereEqualTo("is_rhizophora", 1)
                        .get()
                        .await()
                    
                    // Map to store month data: "2024-1" -> MonthData
                    val monthMap = mutableMapOf<String, MonthData>()
                    val monthNames = arrayOf(
                        "January", "February", "March", "April", "May", "June",
                        "July", "August", "September", "October", "November", "December"
                    )
                    
                    trunkSnapshot.documents.forEach { doc ->
                        val isAlive = doc.getLong("is_alive") ?: 0L
                        val timestamp = doc.getTimestamp("timestamp_firestore")?.toDate()
                        
                        if (timestamp != null) {
                            val calendar = Calendar.getInstance(TimeZone.getTimeZone("GMT+08:00")).apply { time = timestamp }
                            val year = calendar.get(Calendar.YEAR)
                            val month = calendar.get(Calendar.MONTH)
                            
                            // Create unique key for year-month
                            val key = "$year-$month"
                            
                            // Get or create MonthData for this month
                            val monthData = monthMap.getOrPut(key) {
                                MonthData(
                                    monthName = monthNames[month],
                                    year = year,
                                    month = month,
                                    aliveCount = 0,
                                    deadCount = 0
                                )
                            }
                            
                            // Update counts
                            if (isAlive == 1L) {
                                monthData.aliveCount++
                            } else {
                                monthData.deadCount++
                            }
                        }
                    }
                    
                    // Convert map to sorted list (by year, then month)
                    val allMonths = monthMap.values.sortedWith(
                        compareBy<MonthData> { it.year }.thenBy { it.month }
                    )
                    monthDataList = allMonths
                    
                    // Set default selected year to the most recent year if not set
                    if (selectedYear == null && allMonths.isNotEmpty()) {
                        selectedYear = allMonths.maxOfOrNull { it.year }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("DiagramsScreen", "Error loading trunk data", e)
            } finally {
                isLoading = false
            }
        } else {
            monthDataList = emptyList()
            pinnedLocationDocId = null
        }
    }
    
    if (selectedLocation != null) {
        // Get available years from monthDataList
        val availableYears = monthDataList.map { it.year }.distinct().sortedDescending()
        // Filter monthDataList by selected year
        val filteredMonthDataList = if (selectedYear != null) {
            monthDataList.filter { it.year == selectedYear }
        } else {
            monthDataList
        }
        
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.padding(32.dp))
                    Text(
                        "Loading trunk data...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                } else {
                    if (monthDataList.isNotEmpty()) {
                        // Location and filter container (above trends card)
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp),
                            elevation = CardDefaults.cardElevation(4.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                // Location info on left side (65% of width)
                                Row(
                                    modifier = Modifier.weight(0.65f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Start
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.LocationOn,
                                        contentDescription = "Location",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        selectedLocation!!.name,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                
                                // Year filter button on right side (35% of width)
                                if (availableYears.isNotEmpty()) {
                                    Box(
                                        modifier = Modifier.weight(0.35f),
                                        contentAlignment = Alignment.CenterEnd
                                    ) {
                                        var expanded by remember { mutableStateOf(false) }
                                        
                                        Button(
                                            onClick = { expanded = !expanded },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = Color(0xFFA5D6A7) // Matcha green
                                            ),
                                            shape = RoundedCornerShape(20.dp),
                                            modifier = Modifier
                                                .height(36.dp)
                                                .width(100.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text(
                                                    selectedYear?.toString() ?: "All",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = FontWeight.Medium,
                                                    color = Color(0xFF1B5E20), // Dark green text
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                Icon(
                                                    imageVector = if (expanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                                                    contentDescription = if (expanded) "Collapse" else "Expand",
                                                    tint = Color(0xFF1B5E20),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                        
                                        DropdownMenu(
                                            expanded = expanded,
                                            onDismissRequest = { expanded = false },
                                            modifier = Modifier.width(120.dp)
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text("All") },
                                                onClick = {
                                                    selectedYear = null
                                                    expanded = false
                                                }
                                            )
                                            availableYears.forEach { year ->
                                                DropdownMenuItem(
                                                    text = { Text(year.toString()) },
                                                    onClick = {
                                                        selectedYear = year
                                                        expanded = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        
                        // Trend Chart - Shows alive and dead counts over time
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 24.dp),
                            elevation = CardDefaults.cardElevation(4.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    "Trends Over Time",
                                    style = MaterialTheme.typography.titleLarge,
                                    modifier = Modifier.padding(bottom = 16.dp)
                                )
                                
                                // Trend Line Chart
                                TrendChart(
                                    monthDataList = filteredMonthDataList,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(300.dp)
                                        .padding(16.dp)
                                )
                                
                                Spacer(modifier = Modifier.height(16.dp))
                                
                                // Legend for trend chart
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    LegendItem(
                                        color = Color(0xFF4CAF50), // Green
                                        label = "Alive",
                                        count = filteredMonthDataList.sumOf { it.aliveCount }
                                    )
                                    LegendItem(
                                        color = Color(0xFFF44336), // Red
                                        label = "Dead",
                                        count = filteredMonthDataList.sumOf { it.deadCount }
                                    )
                                }
                            }
                        }
                        
                        // Generate pie chart for each month found in Firebase (filtered by year)
                        filteredMonthDataList.forEachIndexed { index, monthData ->
                            val monthTotal = monthData.aliveCount + monthData.deadCount
                            
                            if (monthTotal > 0) {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = if (index < filteredMonthDataList.size - 1) 16.dp else 24.dp),
                                    elevation = CardDefaults.cardElevation(4.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(24.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            "${monthData.monthName} ${monthData.year}",
                                            style = MaterialTheme.typography.titleLarge,
                                            modifier = Modifier.padding(bottom = 8.dp)
                                        )
                                        Text(
                                            "Alive vs Dead Rhizophora",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.secondary,
                                            modifier = Modifier.padding(bottom = 16.dp)
                                        )
                                        
                                        // Pie Chart
                                        PieChart(
                                            aliveCount = monthData.aliveCount,
                                            deadCount = monthData.deadCount,
                                            modifier = Modifier
                                                .size(250.dp)
                                                .padding(16.dp)
                                        )
                                        
                                        Spacer(modifier = Modifier.height(16.dp))
                                        
                                        // Legend
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceEvenly
                                        ) {
                                            LegendItem(
                                                color = Color(0xFF4CAF50), // Green
                                                label = "Alive",
                                                count = monthData.aliveCount
                                            )
                                            LegendItem(
                                                color = Color(0xFFF44336), // Red
                                                label = "Dead",
                                                count = monthData.deadCount
                                            )
                                        }
                                        
                                        Spacer(modifier = Modifier.height(8.dp))
                                        
                                        // Total count
                                        Text(
                                            "Total: $monthTotal trunks",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "No trunk detections found",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.padding(bottom = 16.dp)
                                )
                                Text(
                                    "Start detection in AR to generate data",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                    }
                }
            }
        }
    } else {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
            Text("Select a location from the map to view records")
        }
    }
}

@Composable
fun PieChart(
    aliveCount: Int,
    deadCount: Int,
    modifier: Modifier = Modifier
) {
    val total = aliveCount + deadCount
    val aliveAngle = if (total > 0) (aliveCount.toFloat() / total.toFloat()) * 360f else 0f
    val deadAngle = if (total > 0) (deadCount.toFloat() / total.toFloat()) * 360f else 0f
    
    val aliveColor = Color(0xFF4CAF50) // Green
    val deadColor = Color(0xFFF44336) // Red
    
    Canvas(modifier = modifier) {
        val canvasSize = size.minDimension
        val radius = canvasSize / 2f
        val center = Offset(size.width / 2f, size.height / 2f)
        val topLeft = Offset(center.x - radius, center.y - radius)
        val chartSize = Size(radius * 2f, radius * 2f)
        
        var startAngle = -90f // Start from top
        
        // Draw alive slice (green)
        if (aliveAngle > 0f) {
            drawArc(
                color = aliveColor,
                startAngle = startAngle,
                sweepAngle = aliveAngle,
                useCenter = true,
                topLeft = topLeft,
                size = chartSize
            )
            startAngle += aliveAngle
        }
        
        // Draw dead slice (red)
        if (deadAngle > 0f) {
            drawArc(
                color = deadColor,
                startAngle = startAngle,
                sweepAngle = deadAngle,
                useCenter = true,
                topLeft = topLeft,
                size = chartSize
            )
        }
    }
}

// Data class for month data (shared between DiagramsScreen and TrendChart)
data class MonthData(
    val monthName: String,
    val year: Int,
    val month: Int,
    var aliveCount: Int = 0,
    var deadCount: Int = 0
)

@Composable
fun TrendChart(
    monthDataList: List<MonthData>,
    modifier: Modifier = Modifier
) {
    if (monthDataList.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text("No data available")
        }
        return
    }
    
    val maxCount = monthDataList.maxOfOrNull { 
        maxOf(it.aliveCount, it.deadCount) 
    } ?: 100
    
    // Add padding for Y-axis labels
    val yAxisWidth = 50f
    val xAxisHeight = 60f
    val chartPadding = 20f
    
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val chartWidth = width - yAxisWidth - chartPadding
        val chartHeight = height - xAxisHeight - chartPadding
        val chartStartX = yAxisWidth + chartPadding
        val chartStartY = chartPadding
        val chartEndX = width - chartPadding
        val chartEndY = height - xAxisHeight
        
        val aliveColor = Color(0xFF4CAF50) // Green
        val deadColor = Color(0xFFF44336) // Red
        val gridColor = Color.Gray.copy(alpha = 0.3f)
        val axisColor = Color.Gray
        
        // Draw grid lines and Y-axis labels
        val ySteps = 5
        for (i in 0..ySteps) {
            val y = chartStartY + (chartHeight * i / ySteps)
            val value = maxCount - (maxCount * i / ySteps)
            
            // Draw grid line
            drawLine(
                color = gridColor,
                start = Offset(chartStartX, y),
                end = Offset(chartEndX, y),
                strokeWidth = 1f
            )
            
            // Draw Y-axis label
            drawIntoCanvas { canvas ->
                val paint = android.graphics.Paint().apply {
                    color = android.graphics.Color.GRAY
                    textSize = 28f
                    textAlign = android.graphics.Paint.Align.RIGHT
                    isAntiAlias = true
                }
                canvas.nativeCanvas.drawText("${value.toInt()}", chartStartX - 10, y + 10, paint)
            }
        }
        
        // Draw X-axis and labels
        val monthCount = monthDataList.size
        monthDataList.forEachIndexed { index, monthData ->
            val x = chartStartX + (chartWidth * index / (monthCount - 1).coerceAtLeast(1))
            
            // Draw X-axis label (abbreviated month name)
            val monthLabel = monthData.monthName.take(3) // "Jan", "Feb", etc.
            drawIntoCanvas { canvas ->
                val paint = android.graphics.Paint().apply {
                    color = android.graphics.Color.GRAY
                    textSize = 24f
                    textAlign = android.graphics.Paint.Align.CENTER
                    isAntiAlias = true
                }
                canvas.nativeCanvas.drawText(monthLabel, x, chartEndY + 40, paint)
            }
        }
        
        // Draw axis lines
        drawLine(
            color = axisColor,
            start = Offset(chartStartX, chartEndY),
            end = Offset(chartEndX, chartEndY),
            strokeWidth = 2f
        )
        drawLine(
            color = axisColor,
            start = Offset(chartStartX, chartStartY),
            end = Offset(chartStartX, chartEndY),
            strokeWidth = 2f
        )
        
        // Draw trend lines
        if (monthCount > 1) {
            // Draw Alive trend line (green)
            val alivePoints = monthDataList.mapIndexed { index, monthData ->
                val x = chartStartX + (chartWidth * index / (monthCount - 1))
                val y = chartEndY - (chartHeight * monthData.aliveCount / maxCount)
                Offset(x, y)
            }
            for (i in 0 until alivePoints.size - 1) {
                drawLine(
                    color = aliveColor,
                    start = alivePoints[i],
                    end = alivePoints[i + 1],
                    strokeWidth = 3f
                )
            }
            // Draw points for alive line
            alivePoints.forEach { point ->
                drawCircle(
                    color = aliveColor,
                    radius = 6f,
                    center = point
                )
            }
            
            // Draw Dead trend line (red)
            val deadPoints = monthDataList.mapIndexed { index, monthData ->
                val x = chartStartX + (chartWidth * index / (monthCount - 1))
                val y = chartEndY - (chartHeight * monthData.deadCount / maxCount)
                Offset(x, y)
            }
            for (i in 0 until deadPoints.size - 1) {
                drawLine(
                    color = deadColor,
                    start = deadPoints[i],
                    end = deadPoints[i + 1],
                    strokeWidth = 3f
                )
            }
            // Draw points for dead line
            deadPoints.forEach { point ->
                drawCircle(
                    color = deadColor,
                    radius = 6f,
                    center = point
                )
            }
        } else {
            // Single point - draw as bar or point
            val monthData = monthDataList[0]
            val x = chartStartX + (chartWidth / 2f)
            val aliveY = chartEndY - (chartHeight * monthData.aliveCount / maxCount)
            val deadY = chartEndY - (chartHeight * monthData.deadCount / maxCount)
            
            drawLine(
                color = aliveColor,
                start = Offset(x, chartEndY),
                end = Offset(x, aliveY),
                strokeWidth = 20f
            )
            drawLine(
                color = deadColor,
                start = Offset(x, chartEndY),
                end = Offset(x, deadY),
                strokeWidth = 20f
            )
        }
    }
}

@Composable
fun LegendItem(
    color: Color,
    label: String,
    count: Int
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .background(color, MaterialTheme.shapes.small)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                "$count trunks",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
        }
    }
}

// Data class for trunk detection in reports
data class TrunkReportData(
    val trunkId: String,
    val status: String, // "Alive" or "Dead"
    val dbhCm: Double,
    val basalArea: Double // calculated: 0.00007854 * DBH
)

@Composable
fun ReportsScreen(
    pinnedLocations: List<PinnedLocation>,
    selectedLocation: PinnedLocation? = null
) {
    val context = LocalContext.current
    val db = remember { FirebaseFirestore.getInstance() }
    val userId = remember { FirebaseAuth.getInstance().currentUser?.uid ?: "" }
    
    // Month names helper to map to indices
    val monthNames = remember {
        arrayOf(
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December"
        )
    }
    
    var monthTrunkMap by remember { mutableStateOf<Map<String, List<TrunkReportData>>>(emptyMap()) }
    var isLoading by remember { mutableStateOf(false) }
    var pinnedLocationDocId by remember { mutableStateOf<String?>(null) }
    var expandedMonths by remember { mutableStateOf<Set<String>>(emptySet()) } // Track which months are expanded
    var lastTapTime by remember { mutableStateOf(0L) }
    var lastTappedMonth by remember { mutableStateOf<String?>(null) }
    
    // Load trunk data grouped by month
    LaunchedEffect(selectedLocation) {
        if (selectedLocation != null && userId.isNotEmpty()) {
            isLoading = true
            try {
                // Query to find the pinned location document ID
                val snapshot = db.collection("users").document(userId)
                    .collection("pinned_locations")
                    .whereEqualTo("name", selectedLocation.name)
                    .whereEqualTo("latitude", selectedLocation.latitude)
                    .whereEqualTo("longitude", selectedLocation.longitude)
                    .limit(1)
                    .get()
                    .await()
                
                if (!snapshot.isEmpty) {
                    pinnedLocationDocId = snapshot.documents[0].id
                    
                    // Query trunk detections for this location
                    val trunkSnapshot = db.collection("trunk_detections")
                        .whereEqualTo("pinned_location_id", pinnedLocationDocId)
                        .whereEqualTo("is_rhizophora", 1)
                        .get()
                        .await()
                    
                    // Group trunk detections by month
                    val map = mutableMapOf<String, MutableList<TrunkReportData>>()
                    
                    trunkSnapshot.documents.forEach { doc ->
                        val timestamp = doc.getTimestamp("timestamp_firestore")?.toDate()
                        
                        if (timestamp != null) {
                            val calendar = Calendar.getInstance(TimeZone.getTimeZone("GMT+08:00")).apply { time = timestamp }
                            val year = calendar.get(Calendar.YEAR)
                            val month = calendar.get(Calendar.MONTH)
                            val monthKey = "$year-${monthNames[month]}"
                            
                            val trunkId = doc.id
                            val isAlive = doc.getLong("is_alive") ?: 0L
                            val status = if (isAlive == 1L) "Alive" else "Dead"
                            val dbhCm = doc.getDouble("dbh_cm") ?: 0.0
                            
                            // Calculate basal area: 0.00007854 * DBH
                            val basalArea = 0.00007854 * dbhCm
                            
                            val trunkData = TrunkReportData(
                                trunkId = trunkId,
                                status = status,
                                dbhCm = dbhCm,
                                basalArea = basalArea
                            )
                            
                            map.getOrPut(monthKey) { mutableListOf() }.add(trunkData)
                        }
                    }
                    
                    monthTrunkMap = map
                }
            } catch (e: Exception) {
                android.util.Log.e("ReportsScreen", "Error loading trunk data", e)
            } finally {
                isLoading = false
            }
        } else {
            monthTrunkMap = emptyMap()
            pinnedLocationDocId = null
        }
    }

    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val cardTopPositions = remember { mutableStateMapOf<String, Float>() }

    if (selectedLocation == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("Select a location from the map to view records")
        }
    } else if (isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                Text(
                    "Loading reports...",
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
    } else if (monthTrunkMap.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("No trunk detections found for this location")
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp)
        ) {
            // Display report for each month
            run {
                val sortedEntries = monthTrunkMap.entries.sortedWith(
                    compareByDescending<Map.Entry<String, List<TrunkReportData>>> {
                        val parts = it.key.split("-")
                        val yr = parts.getOrNull(0)?.toIntOrNull() ?: 0
                        val mName = parts.getOrNull(1) ?: ""
                        val mIdx = monthNames.indexOf(mName).coerceAtLeast(0)
                        yr * 100 + mIdx
                    }
                )
                sortedEntries.forEach { (monthKey, trunks) ->
                    val (year, monthName) = monthKey.split("-")
                    val totalBasalArea = trunks.sumOf { it.basalArea }
                    val isExpanded = expandedMonths.contains(monthKey)
                    val maxDisplay = 5
                    val displayedTrunks = if (isExpanded) trunks else trunks.take(maxDisplay)
                    val hasMore = trunks.size > maxDisplay
                    
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 24.dp)
                                .onGloballyPositioned { coords ->
                                    // Track the top Y position of this month card within the scrollable Column
                                    cardTopPositions[monthKey] = coords.positionInParent().y
                                }
                                .clickable {
                                    val currentTime = System.currentTimeMillis()
                                    // Check if this is a double-tap (within 300ms and same month)
                                    if (currentTime - lastTapTime < 300 && lastTappedMonth == monthKey) {
                                        // Double-tap detected - toggle expanded state
                                        val wasExpanded = isExpanded
                                        val beforeCardTop = cardTopPositions[monthKey] ?: 0f
                                        val beforeScroll = scrollState.value.toFloat()
                                        expandedMonths = if (isExpanded) {
                                            expandedMonths - monthKey
                                        } else {
                                            expandedMonths + monthKey
                                        }
                                        lastTapTime = 0
                                        lastTappedMonth = null
                                        
                                        // If collapsing, maintain scroll position to keep this report visible
                                        if (wasExpanded) {
                                            scope.launch {
                                                delay(150) // Wait for UI update and layout pass
                                                val afterCardTop = cardTopPositions[monthKey] ?: beforeCardTop
                                                val deltaTop = afterCardTop - beforeCardTop
                                                val targetScroll = (beforeScroll + deltaTop).coerceAtLeast(0f)
                                                scrollState.animateScrollTo(targetScroll.toInt())
                                            }
                                        }
                                    } else {
                                        // Single tap - record for potential double-tap
                                        lastTapTime = currentTime
                                        lastTappedMonth = monthKey
                                    }
                                },
                            elevation = CardDefaults.cardElevation(4.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp)
                            ) {
                            // Date
                                Text(
                                    "Date: $monthName $year",
                                    style = MaterialTheme.typography.titleLarge,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )

                                Spacer(modifier = Modifier.height(8.dp))
                                
                            // Divider
                            Divider(
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                            
                            // Table Header
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.Start
                            ) {
                                Text(
                                    "Trunk ID",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f),
                                    textAlign = TextAlign.Start
                                )
                                Text(
                                    "Status",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f),
                                    textAlign = TextAlign.Start
                                )
                                Text(
                                    "DBH(cm)",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f),
                                    textAlign = TextAlign.Start
                                )
                                Text(
                                    "Basal Area(m²)",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f),
                                    textAlign = TextAlign.Start
                                )
                            }
                            
                            // Divider
                            Divider(
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )

                            // Table Rows (limited to 5 or all if expanded)
                            displayedTrunks.forEachIndexed { index, trunk ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.Start
                                ) {
                                    Text(
                                        trunk.trunkId.take(12), // Show first 12 chars of ID
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.weight(1f),
                                        textAlign = TextAlign.Start,
                                        fontSize = 12.sp
                                    )
                                    Text(
                                        trunk.status,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.weight(1f),
                                        textAlign = TextAlign.Start,
                                        fontSize = 12.sp,
                                        color = if (trunk.status == "Alive") Color(0xFF4CAF50) else Color(0xFFF44336)
                                    )
                                    Text(
                                        String.format("%.1f", trunk.dbhCm),
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.weight(1f),
                                        textAlign = TextAlign.Start,
                                        fontSize = 12.sp
                                    )
                                    Text(
                                        String.format("%.6f", trunk.basalArea),
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.weight(1f),
                                        textAlign = TextAlign.Start,
                                        fontSize = 12.sp
                                    )
                                }
                                
                                if (index < displayedTrunks.size - 1) {
                                    Divider(
                                        modifier = Modifier.padding(vertical = 4.dp),
                                        thickness = 0.5.dp
                                    )
                                }
                            }
                            
                            // Divider
                            Divider(
                                modifier = Modifier.padding(vertical = 8.dp)
                                )

                            // Basal Area per Acre with Expand/Collapse Icon
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                Text(
                                        "Basal Area per Acre: ${String.format("%.6f", totalBasalArea)}",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                
                                // Expand/Collapse Icon (only show if there are more than 5 trunks)
                                if (hasMore) {
                                    IconButton(
                                        onClick = {
                                            val wasExpanded = isExpanded
                                            val beforeCardTop = cardTopPositions[monthKey] ?: 0f
                                            val beforeScroll = scrollState.value.toFloat()
                                            expandedMonths = if (isExpanded) {
                                                expandedMonths - monthKey
                                            } else {
                                                expandedMonths + monthKey
                                            }
                                            
                                            // If collapsing, maintain scroll position to keep this report visible
                                            if (wasExpanded) {
                                                scope.launch {
                                                    delay(150) // Wait for UI update and layout pass
                                                    val afterCardTop = cardTopPositions[monthKey] ?: beforeCardTop
                                                    val deltaTop = afterCardTop - beforeCardTop
                                                    val targetScroll = (beforeScroll + deltaTop).coerceAtLeast(0f)
                                                    scrollState.animateScrollTo(targetScroll.toInt())
                                                }
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                            contentDescription = if (isExpanded) "Collapse" else "Expand",
                                            tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
        }
    }

// Data for GIS rendering
data class LocationWithStatus(
    val location: PinnedLocation,
    val pinnedLocationDocId: String,
    val alivePercentage: Double,
    val circleColor: Int
)

@Composable
fun GISScreen(selectedLocation: PinnedLocation? = null) {
    val db = remember { FirebaseFirestore.getInstance() }
    val userId = remember { FirebaseAuth.getInstance().currentUser?.uid ?: "" }

    var locationsWithStatus by remember { mutableStateOf<List<LocationWithStatus>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    LaunchedEffect(userId) {
        if (userId.isEmpty()) {
            locationsWithStatus = emptyList()
            return@LaunchedEffect
        }
        isLoading = true
        try {
            val pinnedLocationsSnapshot = db.collectionGroup("pinned_locations")
                .get()
                .await()

            val result = mutableListOf<LocationWithStatus>()
            for (pinnedDoc in pinnedLocationsSnapshot.documents) {
                val loc = PinnedLocation(
                    name = pinnedDoc.getString("name") ?: "Unknown",
                    address = pinnedDoc.getString("address") ?: "",
                    latitude = pinnedDoc.getDouble("latitude") ?: 0.0,
                    longitude = pinnedDoc.getDouble("longitude") ?: 0.0,
                    timestamp = pinnedDoc.getTimestamp("timestamp")?.toDate()?.time ?: System.currentTimeMillis()
                )
                // Skip invalid coordinates
                if (loc.latitude == 0.0 && loc.longitude == 0.0) continue

                val pinnedLocationDocId = pinnedDoc.id

                // Filter detections for this location and current user
                val trunks = db.collection("trunk_detections")
                    .whereEqualTo("pinned_location_id", pinnedLocationDocId)
                    .whereEqualTo("user_id", userId)
                    .whereEqualTo("is_rhizophora", 1)
                    .get()
                    .await()

                if (trunks.isEmpty) continue

                // Most recent month window (UTC+8)
                var latestTs = Long.MIN_VALUE
                trunks.documents.forEach { d ->
                    val ts = d.getTimestamp("timestamp_firestore")?.toDate()?.time
                    if (ts != null && ts > latestTs) latestTs = ts
                }
                if (latestTs == Long.MIN_VALUE) continue

                val tz = TimeZone.getTimeZone("GMT+08:00")
                val calLatest = Calendar.getInstance(tz).apply { timeInMillis = latestTs }
                val latestYear = calLatest.get(Calendar.YEAR)
                val latestMonth = calLatest.get(Calendar.MONTH)

                val calStart = Calendar.getInstance(tz).apply {
                    set(Calendar.YEAR, latestYear)
                    set(Calendar.MONTH, latestMonth)
                    set(Calendar.DAY_OF_MONTH, 1)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val calEnd = Calendar.getInstance(tz).apply {
                    set(Calendar.YEAR, latestYear)
                    set(Calendar.MONTH, latestMonth)
                    set(Calendar.DAY_OF_MONTH, 1)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                    add(Calendar.MONTH, 1)
                }

                var alive = 0
                var total = 0
                trunks.documents.forEach { d ->
                    val ts = d.getTimestamp("timestamp_firestore")?.toDate()?.time
                    if (ts != null && ts >= calStart.timeInMillis && ts < calEnd.timeInMillis) {
                        total += 1
                        val isAlive = d.getLong("is_alive") ?: 0L
                        if (isAlive == 1L) alive += 1
                    }
                }

                val pct = if (total > 0) (alive.toDouble() / total.toDouble()) * 100.0 else 0.0
                val color = when {
                    pct >= 80.0 -> 0x804CAF50.toInt() // Green
                    pct >= 51.0 -> 0x8098FB98.toInt() // Pale Green
                    pct >= 41.0 -> 0x80FFEB3B.toInt() // Yellow
                    else -> 0x80F44336.toInt()       // Red
                }

                result.add(
                    LocationWithStatus(
                        location = loc,
                        pinnedLocationDocId = pinnedLocationDocId,
                        alivePercentage = pct,
                        circleColor = color
                    )
                )
            }

            locationsWithStatus = result
        } finally {
            isLoading = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        AndroidView(
            factory = { ctx ->
                MapView(ctx).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setBuiltInZoomControls(true)
                    setMultiTouchControls(true)
                    val puertoPrincesaBounds = BoundingBox(10.5, 118.85, 9.6, 117.8)
                    setScrollableAreaLimitDouble(puertoPrincesaBounds)
                    controller.setZoom(12.0)
                    controller.setCenter(GeoPoint(9.7439, 118.7357))
                    clipToOutline = true
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { view ->
                // Clear previous polygons
                view.overlays.removeAll { it is org.osmdroid.views.overlay.Polygon }

                // Draw circles
                locationsWithStatus.forEach { ls ->
                    val center = GeoPoint(ls.location.latitude, ls.location.longitude)
                    val radius = GeoUtils.calculateRadiusForArea(1000.0)
                    val circle = GeoUtils.createCirclePolygon(center, radius, 36)
                    circle.fillColor = ls.circleColor
                    circle.strokeColor = (ls.circleColor and 0x00FFFFFF) or 0xFF000000.toInt()
                    circle.strokeWidth = 3f
                    circle.title = "${ls.location.name} (${String.format("%.1f", ls.alivePercentage)}% alive)"
                    view.overlays.add(circle)
                }

                // Center to selected location if provided
                selectedLocation?.let { sel ->
                    val match = locationsWithStatus.find { it.location.name == sel.name }
                    if (match != null) {
                        view.controller.setZoom(17.0)
                        view.controller.setCenter(GeoPoint(match.location.latitude, match.location.longitude))
                    }
                }

                view.invalidate()
            },
            onRelease = { view ->
                view.overlays.clear()
            }
        )

        // Legend
        Card(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Status:", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                LegendItem("≥ 80%", Color(0xFF4CAF50))
                LegendItem("51-79%", Color(0xFF98FB98))
                LegendItem("41-50%", Color(0xFFFFEB3B))
                LegendItem("≤ 40%", Color(0xFFF44336))
            }
        }

        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                Card { Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp).padding(end = 8.dp))
                    Text("Loading locations...")
                }}
            }
        }
    }
}

@Composable
private fun LegendItem(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .background(color, MaterialTheme.shapes.small)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

