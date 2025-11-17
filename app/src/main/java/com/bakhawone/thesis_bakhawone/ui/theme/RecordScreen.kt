package com.bakhawone.thesis_bakhawone.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.Icons.Default
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.FilterChip
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
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import androidx.compose.ui.viewinterop.AndroidView
import com.bakhawone.thesis_bakhawone.GeoUtils
import com.bakhawone.thesis_bakhawone.PinnedLocation
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
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
    BarangayTrendsScreen()
}

@Composable
fun DiagramsScreen(selectedLocation: PinnedLocation? = null) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isTablet = minOf(configuration.screenWidthDp, configuration.screenHeightDp) >= 600
    val db = remember { FirebaseFirestore.getInstance() }
    val userId = remember { FirebaseAuth.getInstance().currentUser?.uid ?: "" }
    
    var monthDataList by remember { mutableStateOf<List<MonthData>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var pinnedLocationDocId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(selectedLocation) {
        if (selectedLocation != null && userId.isNotEmpty()) {
            isLoading = true
            try {
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
                    val trunkSnapshot = db.collection("trunk_detections")
                        .whereEqualTo("pinned_location_id", pinnedLocationDocId)
                        .whereEqualTo("is_rhizophora", 1)
                        .get()
                        .await()
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
                            val key = "$year-$month"
                            val monthData = monthMap.getOrPut(key) {
                                MonthData(
                                    monthName = monthNames[month],
                                    year = year,
                                    month = month,
                                    aliveCount = 0,
                                    deadCount = 0
                                )
                            }
                            if (isAlive == 1L) {
                                monthData.aliveCount++
                            } else {
                                monthData.deadCount++
                            }
                        }
                    }
                    val allMonths = monthMap.values.sortedWith(
                        compareBy<MonthData> { it.year }.thenBy { it.month }
                    )
                    monthDataList = allMonths
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
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = if (isTablet) 24.dp else 16.dp, vertical = 16.dp),
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
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        monthDataList.forEachIndexed { index, monthData ->
                            val monthTotal = monthData.aliveCount + monthData.deadCount
                            
                            if (monthTotal > 0) {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = if (index < monthDataList.size - 1) 16.dp else 24.dp),
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
                                        PieChart(
                                            aliveCount = monthData.aliveCount,
                                            deadCount = monthData.deadCount,
                                            modifier = Modifier
                                                .size(if (isTablet) 300.dp else 250.dp)
                                                .padding(16.dp)
                                        )
                                        
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceEvenly
                                        ) {
                                            LegendItem(
                                                color = Color(0xFF4CAF50),
                                                label = "Alive",
                                                count = monthData.aliveCount
                                            )
                                            LegendItem(
                                                color = Color(0xFFF44336),
                                                label = "Dead",
                                                count = monthData.deadCount
                                            )
                                        }
                                        
                                        Spacer(modifier = Modifier.height(8.dp))
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
    
    val aliveColor = Color(0xFF4CAF50)
    val deadColor = Color(0xFFF44336)
    
    Canvas(modifier = modifier) {
        val canvasSize = size.minDimension
        val radius = canvasSize / 2f
        val center = Offset(size.width / 2f, size.height / 2f)
        val topLeft = Offset(center.x - radius, center.y - radius)
        val chartSize = Size(radius * 2f, radius * 2f)
        
        var startAngle = -90f
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
        
        val aliveColor = Color(0xFF4CAF50)
        val deadColor = Color(0xFFF44336)
        val gridColor = Color.Gray.copy(alpha = 0.3f)
        val axisColor = Color.Gray
        val ySteps = 5
        for (i in 0..ySteps) {
            val y = chartStartY + (chartHeight * i / ySteps)
            val value = maxCount - (maxCount * i / ySteps)
            drawLine(
                color = gridColor,
                start = Offset(chartStartX, y),
                end = Offset(chartEndX, y),
                strokeWidth = 1f
            )
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
        val monthCount = monthDataList.size
        monthDataList.forEachIndexed { index, monthData ->
            val x = chartStartX + (chartWidth * index / (monthCount - 1).coerceAtLeast(1))
            val monthLabel = monthData.monthName.take(3)
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
        if (monthCount > 1) {
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
            alivePoints.forEach { point ->
                drawCircle(
                    color = aliveColor,
                    radius = 6f,
                    center = point
                )
            }
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
            deadPoints.forEach { point ->
                drawCircle(
                    color = deadColor,
                    radius = 6f,
                    center = point
                )
            }
        } else {
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
data class TrunkReportData(
    val trunkId: String,
    val status: String,
    val dbhCm: Double,
    val basalArea: Double
)

@Composable
fun ReportsScreen(
    pinnedLocations: List<PinnedLocation>,
    selectedLocation: PinnedLocation? = null
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isTablet = minOf(configuration.screenWidthDp, configuration.screenHeightDp) >= 600
    val db = remember { FirebaseFirestore.getInstance() }
    val userId = remember { FirebaseAuth.getInstance().currentUser?.uid ?: "" }
    val monthNames = remember {
        arrayOf(
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December"
        )
    }
    
    var monthTrunkMap by remember { mutableStateOf<Map<String, List<TrunkReportData>>>(emptyMap()) }
    var isLoading by remember { mutableStateOf(false) }
    var pinnedLocationDocId by remember { mutableStateOf<String?>(null) }
    var expandedMonths by remember { mutableStateOf<Set<String>>(emptySet()) }
    var lastTapTime by remember { mutableStateOf(0L) }
    var lastTappedMonth by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(selectedLocation) {
        if (selectedLocation != null && userId.isNotEmpty()) {
            isLoading = true
            try {
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
                    val trunkSnapshot = db.collection("trunk_detections")
                        .whereEqualTo("pinned_location_id", pinnedLocationDocId)
                        .whereEqualTo("is_rhizophora", 1)
                        .get()
                        .await()
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
                            // Basal Area = π * (DBH/200)² = π * DBH² / 40000 (DBH in cm, result in m²)
                            val basalArea = Math.PI * (dbhCm / 200.0) * (dbhCm / 200.0)
                            
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
                .padding(horizontal = if (isTablet) 24.dp else 16.dp, vertical = 16.dp)
        ) {
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
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
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
                                    cardTopPositions[monthKey] = coords.positionInParent().y
                                }
                                .clickable {
                                    val currentTime = System.currentTimeMillis()
                                    if (currentTime - lastTapTime < 300 && lastTappedMonth == monthKey) {
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
                                        if (wasExpanded) {
                                            scope.launch {
                                                delay(150)
                                                val afterCardTop = cardTopPositions[monthKey] ?: beforeCardTop
                                                val deltaTop = afterCardTop - beforeCardTop
                                                val targetScroll = (beforeScroll + deltaTop).coerceAtLeast(0f)
                                                scrollState.animateScrollTo(targetScroll.toInt())
                                            }
                                        }
                                    } else {
                                        lastTapTime = currentTime
                                        lastTappedMonth = monthKey
                                    }
                                },
                            elevation = CardDefaults.cardElevation(4.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Text(
                                    "Date: $monthName $year",
                                    style = MaterialTheme.typography.titleLarge,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )

                                Spacer(modifier = Modifier.height(8.dp))
                            Divider(
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
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
                            Divider(
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                            displayedTrunks.forEachIndexed { index, trunk ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.Start
                                ) {
                                    Text(
                                        trunk.trunkId.take(if (isTablet) 16 else 12),
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.weight(1f),
                                        textAlign = TextAlign.Start,
                                        fontSize = if (isTablet) 13.sp else 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        trunk.status,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.weight(1f),
                                        textAlign = TextAlign.Start,
                                        fontSize = if (isTablet) 13.sp else 12.sp,
                                        color = if (trunk.status == "Alive") Color(0xFF4CAF50) else Color(0xFFF44336),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        String.format("%.1f", trunk.dbhCm),
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.weight(1f),
                                        textAlign = TextAlign.Start,
                                        fontSize = if (isTablet) 13.sp else 12.sp
                                    )
                                    Text(
                                        String.format("%.3f", trunk.basalArea),
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.weight(1f),
                                        textAlign = TextAlign.Start,
                                        fontSize = if (isTablet) 13.sp else 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                
                                if (index < displayedTrunks.size - 1) {
                                    Divider(
                                        modifier = Modifier.padding(vertical = 4.dp),
                                        thickness = 0.5.dp
                                    )
                                }
                            }
                            Divider(
                                modifier = Modifier.padding(vertical = 8.dp)
                                )
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp)
                                ) {
                                    // Basal Area per Acre
                                    Text(
                                        "Basal Area per Acre: ${String.format("%.3f", totalBasalArea)}",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    // Density Estimation (alive trees per 1000 sqm)
                                    // Density = Total number of alive trees / 1000 sqm
                                    val aliveTreesCount = trunks.count { it.status == "Alive" }
                                    val areaInSqm = 1000.0
                                    val densityPer1000Sqm = aliveTreesCount / areaInSqm
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        "Density: ${String.format("%.4f", densityPer1000Sqm)} trees per 1000 sqm",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
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
                                            if (wasExpanded) {
                                                scope.launch {
                                                    delay(150)
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
data class LocationWithStatus(
    val location: PinnedLocation,
    val pinnedLocationDocId: String,
    val alivePercentage: Double,
    val circleColor: Int
)

@Composable
fun GISScreen(selectedLocation: PinnedLocation? = null) {
    val configuration = LocalConfiguration.current
    val isTablet = minOf(configuration.screenWidthDp, configuration.screenHeightDp) >= 600
    val db = remember { FirebaseFirestore.getInstance() }

    var locationsWithStatus by remember { mutableStateOf<List<LocationWithStatus>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
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
                if (loc.latitude == 0.0 && loc.longitude == 0.0) continue

                val pinnedLocationDocId = pinnedDoc.id
                val trunks = db.collection("trunk_detections")
                    .whereEqualTo("pinned_location_id", pinnedLocationDocId)
                    .whereEqualTo("is_rhizophora", 1)
                    .get()
                    .await()

                if (trunks.isEmpty) continue
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
                    pct >= 80.0 -> 0x804CAF50.toInt()
                    pct >= 51.0 -> 0x8098FB98.toInt()
                    pct >= 41.0 -> 0x80FFEB3B.toInt()
                    else -> 0x80F44336.toInt()
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
                view.overlays.removeAll { it is org.osmdroid.views.overlay.Polygon }
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
        Card(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = if (isTablet) 12.dp else 8.dp, top = if (isTablet) 12.dp else 8.dp)
                .widthIn(max = if (isTablet) 350.dp else 280.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
            )
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = if (isTablet) 12.dp else 8.dp, vertical = if (isTablet) 8.dp else 6.dp)
            ) {
                Text(
                    "Status:", 
                    style = MaterialTheme.typography.titleSmall, 
                    fontWeight = FontWeight.Bold,
                    fontSize = if (isTablet) 16.sp else 14.sp
                )
                Spacer(modifier = Modifier.height(if (isTablet) 6.dp else 4.dp))

                LegendLine(color = Color(0xFF4CAF50), text = "≥ 80.0% = (Very Healthy)")
                LegendLine(color = Color(0xFF98FB98), text = "≥ 51.0% and < 80.0% = (Healthy)")
                LegendLine(color = Color(0xFFFFEB3B), text = "≥ 41.0% and < 51.0% = (Degraded)")
                LegendLine(color = Color(0xFFF44336), text = "< 41.0% = (Very Degraded)")
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

@Composable
private fun LegendLine(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
        Box(
            modifier = Modifier
                .size(14.dp)
                .background(color, MaterialTheme.shapes.small)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodySmall)
    }
}
data class BarangayMonthData(
    val barangay: String,
    val monthKey: String,
    val monthName: String,
    val aliveCount: Int,
    val deadCount: Int
)
data class BarangaySummary(
    val barangay: String,
    val totalTrunks: Int,
    val aliveCount: Int,
    val deadCount: Int,
    val healthPercentage: Double,
    val status: String,
    val trend: String,
    val density: Double = 0.0
)
@Composable
fun SummaryStatsCards(
    totalTrunks: Int,
    overallHealthPercentage: Double,
    barangayCount: Int,
    trendIndicator: String,
    isTablet: Boolean = false,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val isSmallScreen = minOf(configuration.screenWidthDp, configuration.screenHeightDp) < 400
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp
    
    if (isSmallScreen && !isLandscape) {
        // Stack cards vertically on small portrait screens
        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SummaryCard(
                    title = "Total Trunks",
                    value = totalTrunks.toString(),
                    icon = Icons.Filled.Park,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                SummaryCard(
                    title = "Health %",
                    value = String.format("%.1f%%", overallHealthPercentage),
                    icon = Icons.Filled.Favorite,
                    color = when {
                        overallHealthPercentage >= 80.0 -> Color(0xFF4CAF50)
                        overallHealthPercentage >= 51.0 -> Color(0xFF98FB98)
                        overallHealthPercentage >= 41.0 -> Color(0xFFFFEB3B)
                        else -> Color(0xFFF44336)
                    },
                    modifier = Modifier.weight(1f)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SummaryCard(
                    title = "Barangays",
                    value = barangayCount.toString(),
                    icon = Icons.Filled.LocationCity,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.weight(1f)
                )
                SummaryCard(
                    title = "Trend",
                    value = when (trendIndicator) {
                        "improving" -> "↑"
                        "declining" -> "↓"
                        else -> "→"
                    },
                    icon = when (trendIndicator) {
                        "improving" -> Icons.Filled.TrendingUp
                        "declining" -> Icons.Filled.TrendingDown
                        else -> Icons.Filled.TrendingFlat
                    },
                    color = when (trendIndicator) {
                        "improving" -> Color(0xFF4CAF50)
                        "declining" -> Color(0xFFF44336)
                        else -> Color(0xFF9E9E9E)
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    } else {
        Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(if (isTablet) 16.dp else 12.dp)
        ) {
        SummaryCard(
            title = "Total Trunks",
            value = totalTrunks.toString(),
            icon = Icons.Filled.Park,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f)
        )
        SummaryCard(
            title = "Health %",
            value = String.format("%.1f%%", overallHealthPercentage),
            icon = Icons.Filled.Favorite,
            color = when {
                overallHealthPercentage >= 80.0 -> Color(0xFF4CAF50)
                overallHealthPercentage >= 51.0 -> Color(0xFF98FB98)
                overallHealthPercentage >= 41.0 -> Color(0xFFFFEB3B)
                else -> Color(0xFFF44336)
            },
            modifier = Modifier.weight(1f)
        )
        SummaryCard(
            title = "Barangays",
            value = barangayCount.toString(),
            icon = Icons.Filled.LocationCity,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.weight(1f)
        )
        SummaryCard(
            title = "Trend",
            value = when (trendIndicator) {
                "improving" -> "↑"
                "declining" -> "↓"
                else -> "→"
            },
            icon = when (trendIndicator) {
                "improving" -> Icons.Filled.TrendingUp
                "declining" -> Icons.Filled.TrendingDown
                else -> Icons.Filled.TrendingFlat
            },
            color = when (trendIndicator) {
                "improving" -> Color(0xFF4CAF50)
                "declining" -> Color(0xFFF44336)
                else -> Color(0xFF9E9E9E)
            },
            modifier = Modifier.weight(1f)
        )
        }
    }
}

@Composable
fun SummaryCard(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val isTablet = minOf(configuration.screenWidthDp, configuration.screenHeightDp) >= 600
    
    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(if (isTablet) 20.dp else 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = color,
                modifier = Modifier.size(if (isTablet) 40.dp else 32.dp)
            )
            Spacer(modifier = Modifier.height(if (isTablet) 12.dp else 8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = color,
                fontSize = if (isTablet) 24.sp else 20.sp
            )
            Spacer(modifier = Modifier.height(if (isTablet) 6.dp else 4.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = if (isTablet) 13.sp else 12.sp
            )
        }
    }
}
@Composable
fun DensityTrendChart(
    barangayMonthData: Map<String, List<BarangayMonthData>>,
    selectedBarangays: Set<String>,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val isTablet = minOf(configuration.screenWidthDp, configuration.screenHeightDp) >= 600
    val isSmallScreen = minOf(configuration.screenWidthDp, configuration.screenHeightDp) < 400
    if (barangayMonthData.isEmpty()) {
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center
        ) {
            Text("No data available", color = MaterialTheme.colorScheme.secondary)
        }
        return
    }
    
    if (selectedBarangays.isEmpty()) {
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center
        ) {
            Text("Select barangays to visualize", color = MaterialTheme.colorScheme.secondary)
        }
        return
    }
    
    val barangaysToShow = selectedBarangays
    val allMonthKeys = barangaysToShow
        .flatMap { barangay -> barangayMonthData[barangay]?.map { it.monthKey } ?: emptyList() }
        .distinct()
        .sorted()
    
    if (allMonthKeys.isEmpty()) {
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center
        ) {
            Text("No data available", color = MaterialTheme.colorScheme.secondary)
        }
        return
    }
    
    // Calculate density for each barangay/month (alive trees per 1000 sqm)
    val areaInSqm = 1000.0
    val maxDensity = barangaysToShow
        .flatMap { barangay -> 
            barangayMonthData[barangay]?.map { it.aliveCount / areaInSqm } ?: emptyList() 
        }
        .maxOrNull() ?: 1.0
    
    val yAxisWidth = if (isSmallScreen) 45f else if (isTablet) 70f else 60f
    val xAxisHeight = if (isSmallScreen) 70f else if (isTablet) 90f else 80f // Increased to accommodate year labels
    val chartPadding = if (isSmallScreen) 15f else if (isTablet) 25f else 20f
    val labelTextSize = if (isSmallScreen) 18f else if (isTablet) 28f else 24f
    val axisTextSize = if (isSmallScreen) 20f else if (isTablet) 32f else 28f
    val yearTextSize = if (isSmallScreen) 16f else if (isTablet) 22f else 20f
    
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val chartWidth = width - yAxisWidth - chartPadding
        val chartHeight = height - xAxisHeight - chartPadding
        val chartStartX = yAxisWidth + chartPadding
        val chartStartY = chartPadding
        val chartEndX = width - chartPadding
        val chartEndY = height - xAxisHeight
        
        val gridColor = Color.Gray.copy(alpha = 0.3f)
        val axisColor = Color.Gray
        val ySteps = 5
        for (i in 0..ySteps) {
            val y = chartStartY + (chartHeight * i / ySteps)
            val value = maxDensity - (maxDensity * i / ySteps)
            
            drawLine(
                color = gridColor,
                start = Offset(chartStartX, y),
                end = Offset(chartEndX, y),
                strokeWidth = 1f
            )
            
            drawIntoCanvas { canvas ->
                val paint = android.graphics.Paint().apply {
                    color = android.graphics.Color.GRAY
                    textSize = axisTextSize
                    textAlign = android.graphics.Paint.Align.RIGHT
                    isAntiAlias = true
                }
                canvas.nativeCanvas.drawText(
                    String.format("%.3f", value),
                    chartStartX - 10,
                    y + 10,
                    paint
                )
            }
        }
        val monthCount = allMonthKeys.size
        // Group months by year
        val monthsByYear = allMonthKeys.groupBy { monthKey ->
            val parts = monthKey.split("-")
            parts.getOrNull(0)?.toIntOrNull() ?: 0
        }
        
        // Draw month labels
        allMonthKeys.forEachIndexed { index, monthKey ->
            val x = chartStartX + (chartWidth * index / (monthCount - 1).coerceAtLeast(1))
            
            val parts = monthKey.split("-")
            val monthIndex = parts.getOrNull(1)?.toIntOrNull()?.minus(1) ?: 0
            val monthNames = arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
            val monthLabel = if (monthIndex in 0..11) monthNames[monthIndex] else monthKey
            
            drawIntoCanvas { canvas ->
                val paint = android.graphics.Paint().apply {
                    color = android.graphics.Color.GRAY
                    textSize = labelTextSize
                    textAlign = android.graphics.Paint.Align.CENTER
                    isAntiAlias = true
                }
                val labelY = if (isSmallScreen) chartEndY + 35 else chartEndY + 40
                canvas.nativeCanvas.drawText(monthLabel, x, labelY, paint)
            }
        }
        
        // Draw year labels below month labels, centered under each year's month range
        monthsByYear.forEach { (year, yearMonths) ->
            val yearMonthIndices = yearMonths.map { allMonthKeys.indexOf(it) }.filter { it >= 0 }
            if (yearMonthIndices.isNotEmpty()) {
                val firstIndex = yearMonthIndices.minOrNull() ?: 0
                val lastIndex = yearMonthIndices.maxOrNull() ?: 0
                val firstX = chartStartX + (chartWidth * firstIndex / (monthCount - 1).coerceAtLeast(1))
                val lastX = chartStartX + (chartWidth * lastIndex / (monthCount - 1).coerceAtLeast(1))
                val yearCenterX = (firstX + lastX) / 2f
                
                drawIntoCanvas { canvas ->
                    val paint = android.graphics.Paint().apply {
                        color = android.graphics.Color.GRAY
                        textSize = yearTextSize
                        textAlign = android.graphics.Paint.Align.CENTER
                        isAntiAlias = true
                        typeface = android.graphics.Typeface.DEFAULT_BOLD
                    }
                    val yearLabelY = if (isSmallScreen) chartEndY + 55 else chartEndY + 60
                    canvas.nativeCanvas.drawText(year.toString(), yearCenterX, yearLabelY, paint)
                }
            }
        }
        
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
        val colors = listOf(
            Color(0xFF2196F3), Color(0xFF9C27B0), Color(0xFFFF9800),
            Color(0xFF00BCD4), Color(0xFF8BC34A), Color(0xFFE91E63),
            Color(0xFF3F51B5), Color(0xFF009688), Color(0xFFFFC107)
        )
        
        barangaysToShow.forEachIndexed { barangayIndex, barangay ->
            val color = colors[barangayIndex % colors.size]
            val data = barangayMonthData[barangay] ?: return@forEachIndexed
            val points = allMonthKeys.mapIndexed { monthIndex, monthKey ->
                val monthData = data.find { it.monthKey == monthKey }
                val density = (monthData?.aliveCount ?: 0) / areaInSqm
                val x = chartStartX + (chartWidth * monthIndex / (monthCount - 1).coerceAtLeast(1))
                val y = chartEndY - (chartHeight * density.toFloat() / maxDensity.toFloat())
                Offset(x, y)
            }
            if (points.size > 1) {
                // Create smooth curve using cubic bezier
                val path = Path().apply {
                    moveTo(points[0].x, points[0].y)
                    
                    for (i in 0 until points.size - 1) {
                        val p0 = if (i > 0) points[i - 1] else points[i]
                        val p1 = points[i]
                        val p2 = points[i + 1]
                        val p3 = if (i < points.size - 2) points[i + 2] else points[i + 1]
                        
                        // Calculate control points for smooth curve
                        val cp1x = p1.x + (p2.x - p0.x) / 6f
                        val cp1y = p1.y + (p2.y - p0.y) / 6f
                        val cp2x = p2.x - (p3.x - p1.x) / 6f
                        val cp2y = p2.y - (p3.y - p1.y) / 6f
                        
                        cubicTo(
                            cp1x, cp1y,
                            cp2x, cp2y,
                            p2.x, p2.y
                        )
                    }
                }
                
                // Draw smooth curve
                drawPath(
                    path = path,
                    color = color,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = 3f,
                        cap = androidx.compose.ui.graphics.StrokeCap.Round,
                        join = androidx.compose.ui.graphics.StrokeJoin.Round
                    )
                )
            } else if (points.size == 1) {
                // Single point - draw as a circle
                drawCircle(
                    color = color,
                    radius = 5f,
                    center = points[0]
                )
            }
            // Draw data points
            points.forEach { point ->
                drawCircle(
                    color = color,
                    radius = 5f,
                    center = point
                )
            }
        }
    }
}

@Composable
fun MultiBarangayTrendChart(
    barangayMonthData: Map<String, List<BarangayMonthData>>,
    selectedBarangays: Set<String>,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val isTablet = minOf(configuration.screenWidthDp, configuration.screenHeightDp) >= 600
    val isSmallScreen = minOf(configuration.screenWidthDp, configuration.screenHeightDp) < 400
    if (barangayMonthData.isEmpty()) {
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center
        ) {
            Text("No data available", color = MaterialTheme.colorScheme.secondary)
        }
        return
    }
    
    if (selectedBarangays.isEmpty()) {
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center
        ) {
            Text("Select barangays to visualize", color = MaterialTheme.colorScheme.secondary)
        }
        return
    }
    
    val barangaysToShow = selectedBarangays
    val allMonthKeys = barangaysToShow
        .flatMap { barangay -> barangayMonthData[barangay]?.map { it.monthKey } ?: emptyList() }
        .distinct()
        .sorted()
    
    if (allMonthKeys.isEmpty()) {
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center
        ) {
            Text("No data available", color = MaterialTheme.colorScheme.secondary)
        }
        return
    }
    val maxCount = barangaysToShow
        .flatMap { barangay -> barangayMonthData[barangay]?.map { it.aliveCount + it.deadCount } ?: emptyList() }
        .maxOrNull() ?: 100
    
    val yAxisWidth = if (isSmallScreen) 45f else if (isTablet) 70f else 50f
    val xAxisHeight = if (isSmallScreen) 70f else if (isTablet) 90f else 80f // Increased to accommodate year labels
    val chartPadding = if (isSmallScreen) 15f else if (isTablet) 25f else 20f
    val labelTextSize = if (isSmallScreen) 18f else if (isTablet) 28f else 24f
    val axisTextSize = if (isSmallScreen) 20f else if (isTablet) 32f else 28f
    val yearTextSize = if (isSmallScreen) 16f else if (isTablet) 22f else 20f
    
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val chartWidth = width - yAxisWidth - chartPadding
        val chartHeight = height - xAxisHeight - chartPadding
        val chartStartX = yAxisWidth + chartPadding
        val chartStartY = chartPadding
        val chartEndX = width - chartPadding
        val chartEndY = height - xAxisHeight
        
        val gridColor = Color.Gray.copy(alpha = 0.3f)
        val axisColor = Color.Gray
        val ySteps = 5
        for (i in 0..ySteps) {
            val y = chartStartY + (chartHeight * i / ySteps)
            val value = maxCount - (maxCount * i / ySteps)
            
            drawLine(
                color = gridColor,
                start = Offset(chartStartX, y),
                end = Offset(chartEndX, y),
                strokeWidth = 1f
            )
            
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
        val monthCount = allMonthKeys.size
        // Group months by year
        val monthsByYear = allMonthKeys.groupBy { monthKey ->
            val parts = monthKey.split("-")
            parts.getOrNull(0)?.toIntOrNull() ?: 0
        }
        
        // Draw month labels
        allMonthKeys.forEachIndexed { index, monthKey ->
            val x = chartStartX + (chartWidth * index / (monthCount - 1).coerceAtLeast(1))
            
            val parts = monthKey.split("-")
            val monthIndex = parts.getOrNull(1)?.toIntOrNull()?.minus(1) ?: 0
            val monthNames = arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
            val monthLabel = if (monthIndex in 0..11) monthNames[monthIndex] else monthKey
            
            drawIntoCanvas { canvas ->
                val paint = android.graphics.Paint().apply {
                    color = android.graphics.Color.GRAY
                    textSize = labelTextSize
                    textAlign = android.graphics.Paint.Align.CENTER
                    isAntiAlias = true
                }
                val labelY = if (isSmallScreen) chartEndY + 35 else chartEndY + 40
                canvas.nativeCanvas.drawText(monthLabel, x, labelY, paint)
            }
        }
        
        // Draw year labels below month labels, centered under each year's month range
        monthsByYear.forEach { (year, yearMonths) ->
            val yearMonthIndices = yearMonths.map { allMonthKeys.indexOf(it) }.filter { it >= 0 }
            if (yearMonthIndices.isNotEmpty()) {
                val firstIndex = yearMonthIndices.minOrNull() ?: 0
                val lastIndex = yearMonthIndices.maxOrNull() ?: 0
                val firstX = chartStartX + (chartWidth * firstIndex / (monthCount - 1).coerceAtLeast(1))
                val lastX = chartStartX + (chartWidth * lastIndex / (monthCount - 1).coerceAtLeast(1))
                val yearCenterX = (firstX + lastX) / 2f
                
                drawIntoCanvas { canvas ->
                    val paint = android.graphics.Paint().apply {
                        color = android.graphics.Color.GRAY
                        textSize = yearTextSize
                        textAlign = android.graphics.Paint.Align.CENTER
                        isAntiAlias = true
                        typeface = android.graphics.Typeface.DEFAULT_BOLD
                    }
                    val yearLabelY = if (isSmallScreen) chartEndY + 55 else chartEndY + 60
                    canvas.nativeCanvas.drawText(year.toString(), yearCenterX, yearLabelY, paint)
                }
            }
        }
        
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
        val colors = listOf(
            Color(0xFF2196F3), Color(0xFF9C27B0), Color(0xFFFF9800),
            Color(0xFF00BCD4), Color(0xFF8BC34A), Color(0xFFE91E63),
            Color(0xFF3F51B5), Color(0xFF009688), Color(0xFFFFC107)
        )
        
        barangaysToShow.forEachIndexed { barangayIndex, barangay ->
            val color = colors[barangayIndex % colors.size]
            val data = barangayMonthData[barangay] ?: return@forEachIndexed
            val points = allMonthKeys.mapIndexed { monthIndex, monthKey ->
                val monthData = data.find { it.monthKey == monthKey }
                val total = (monthData?.aliveCount ?: 0) + (monthData?.deadCount ?: 0)
                val x = chartStartX + (chartWidth * monthIndex / (monthCount - 1).coerceAtLeast(1))
                val y = chartEndY - (chartHeight * total / maxCount)
                Offset(x, y)
            }
            if (points.size > 1) {
                // Create smooth curve using cubic bezier
                val path = Path().apply {
                    moveTo(points[0].x, points[0].y)
                    
                    for (i in 0 until points.size - 1) {
                        val p0 = if (i > 0) points[i - 1] else points[i]
                        val p1 = points[i]
                        val p2 = points[i + 1]
                        val p3 = if (i < points.size - 2) points[i + 2] else points[i + 1]
                        
                        // Calculate control points for smooth curve
                        val cp1x = p1.x + (p2.x - p0.x) / 6f
                        val cp1y = p1.y + (p2.y - p0.y) / 6f
                        val cp2x = p2.x - (p3.x - p1.x) / 6f
                        val cp2y = p2.y - (p3.y - p1.y) / 6f
                        
                        cubicTo(
                            cp1x, cp1y,
                            cp2x, cp2y,
                            p2.x, p2.y
                        )
                    }
                }
                
                // Draw smooth curve
                drawPath(
                    path = path,
                    color = color,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = 3f,
                        cap = androidx.compose.ui.graphics.StrokeCap.Round,
                        join = androidx.compose.ui.graphics.StrokeJoin.Round
                    )
                )
            } else if (points.size == 1) {
                // Single point - draw as a circle
                drawCircle(
                    color = color,
                    radius = 5f,
                    center = points[0]
                )
            }
            // Draw data points
            points.forEach { point ->
                drawCircle(
                    color = color,
                    radius = 5f,
                    center = point
                )
            }
        }
    }
}

@Composable
fun QuickStatsTable(
    barangaySummaries: List<BarangaySummary>,
    selectedBarangays: Set<String>,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val isTablet = minOf(configuration.screenWidthDp, configuration.screenHeightDp) >= 600
    val isSmallScreen = minOf(configuration.screenWidthDp, configuration.screenHeightDp) < 400
    
    val filteredSummaries = if (selectedBarangays.isEmpty()) {
        barangaySummaries
    } else {
        barangaySummaries.filter { it.barangay in selectedBarangays }
    }
    
    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(modifier = Modifier.padding(if (isSmallScreen) 12.dp else if (isTablet) 20.dp else 16.dp)) {
            Text(
                text = "Barangay Statistics",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                fontSize = if (isSmallScreen) 18.sp else if (isTablet) 22.sp else 20.sp,
                modifier = Modifier.padding(bottom = if (isSmallScreen) 12.dp else 16.dp)
            )
            
            if (filteredSummaries.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (selectedBarangays.isEmpty()) {
                            "No barangay data available"
                        } else {
                            "No data for selected barangay(s)"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = if (isSmallScreen) 6.dp else 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Barangay",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        fontSize = if (isSmallScreen) 11.sp else if (isTablet) 14.sp else 12.sp,
                        modifier = Modifier.weight(2f)
                    )
                    Text(
                        "Total",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        fontSize = if (isSmallScreen) 11.sp else if (isTablet) 14.sp else 12.sp,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center
                    )
                    Text(
                        "% Alive",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        fontSize = if (isSmallScreen) 11.sp else if (isTablet) 14.sp else 12.sp,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center
                    )
                    Text(
                        "Density",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        fontSize = if (isSmallScreen) 11.sp else if (isTablet) 14.sp else 12.sp,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center
                    )
                    Text(
                        "Status",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        fontSize = if (isSmallScreen) 11.sp else if (isTablet) 14.sp else 12.sp,
                        modifier = Modifier.weight(1.5f),
                        textAlign = TextAlign.Center
                    )
                    Text(
                        "Trend",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        fontSize = if (isSmallScreen) 11.sp else if (isTablet) 14.sp else 12.sp,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center
                    )
                }
                
                Divider()
                
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    filteredSummaries.forEachIndexed { index, summary ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = if (isSmallScreen) 6.dp else 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                summary.barangay,
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = if (isSmallScreen) 10.sp else if (isTablet) 13.sp else 12.sp,
                                modifier = Modifier.weight(2f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                summary.totalTrunks.toString(),
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = if (isSmallScreen) 10.sp else if (isTablet) 13.sp else 12.sp,
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Center
                            )
                            Text(
                                String.format("%.1f%%", summary.healthPercentage),
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = if (isSmallScreen) 10.sp else if (isTablet) 13.sp else 12.sp,
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Center
                            )
                            Text(
                                String.format("%.4f", summary.density),
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = if (isSmallScreen) 10.sp else if (isTablet) 13.sp else 12.sp,
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                summary.status,
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = if (isSmallScreen) 10.sp else if (isTablet) 13.sp else 12.sp,
                                color = when (summary.status) {
                                    "Very Healthy" -> Color(0xFF4CAF50)
                                    "Healthy" -> Color(0xFF98FB98)
                                    "Degraded" -> Color(0xFFFFEB3B)
                                    else -> Color(0xFFF44336)
                                },
                                modifier = Modifier.weight(1.5f),
                                textAlign = TextAlign.Center
                            )
                            Icon(
                                imageVector = when (summary.trend) {
                                    "improving" -> Icons.Filled.TrendingUp
                                    "declining" -> Icons.Filled.TrendingDown
                                    else -> Icons.Filled.TrendingFlat
                                },
                                contentDescription = summary.trend,
                                tint = when (summary.trend) {
                                    "improving" -> Color(0xFF4CAF50)
                                    "declining" -> Color(0xFFF44336)
                                    else -> Color(0xFF9E9E9E)
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .size(if (isSmallScreen) 16.dp else if (isTablet) 24.dp else 20.dp)
                            )
                        }
                        if (index < filteredSummaries.size - 1) {
                            Divider(thickness = 0.5.dp)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BarangayTrendsScreen() {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isTablet = minOf(configuration.screenWidthDp, configuration.screenHeightDp) >= 600
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp
    val db = remember { FirebaseFirestore.getInstance() }
    
    var barangayMonthData by remember { mutableStateOf<Map<String, List<BarangayMonthData>>>(emptyMap()) }
    var availableBarangays by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedBarangays by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isLoading by remember { mutableStateOf(false) }
    var barangaySummaries by remember { mutableStateOf<List<BarangaySummary>>(emptyList()) }
    
    val monthNames = remember {
        arrayOf(
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December"
        )
    }
    LaunchedEffect(Unit) {
        isLoading = true
        try {
            val locationMap = mutableMapOf<String, Pair<String?, String>>()
            try {
                val allLocations = db.collectionGroup("pinned_locations").get().await()
                allLocations.documents.forEach { locationDoc ->
                    val locationId = locationDoc.id
                    val barangay = locationDoc.getString("barangay")
                    val address = locationDoc.getString("address") ?: ""
                    locationMap[locationId] = Pair(barangay, address)
                }
            } catch (e: Exception) {
                android.util.Log.e("BarangayTrendsScreen", "Error loading locations", e)
            }
            val trunkSnapshot = db.collection("trunk_detections")
                .whereEqualTo("is_rhizophora", 1)
                .get()
                .await()
            val barangayDataMap = mutableMapOf<String, MutableMap<String, Pair<Int, Int>>>()
            trunkSnapshot.documents.forEach { doc ->
                val barangay = doc.getString("barangay")
                val isAlive = doc.getLong("is_alive") ?: 0
                val timestamp = doc.getTimestamp("timestamp_firestore")
                val pinnedLocationId = doc.getString("pinned_location_id")
                if (timestamp == null) return@forEach
                var finalBarangay = barangay
                var isPuertoPrincesa = false
                
                if (finalBarangay == null && pinnedLocationId != null) {
                    val locationInfo = locationMap[pinnedLocationId]
                    if (locationInfo != null) {
                        val (locationBarangay, address) = locationInfo
                        finalBarangay = locationBarangay
                        if (finalBarangay == null) {
                            finalBarangay = extractBarangayFromAddress(address)
                        }
                        isPuertoPrincesa = address.contains("Puerto Princesa", ignoreCase = true) ||
                                         address.contains("Puerto Princesa City", ignoreCase = true)
                    }
                } else if (pinnedLocationId != null) {
                    val locationInfo = locationMap[pinnedLocationId]
                    if (locationInfo != null) {
                        val address = locationInfo.second
                        isPuertoPrincesa = address.contains("Puerto Princesa", ignoreCase = true) ||
                                         address.contains("Puerto Princesa City", ignoreCase = true)
                    } else {
                        isPuertoPrincesa = true
                    }
                } else {
                    isPuertoPrincesa = true
                }
                
                if (!isPuertoPrincesa || finalBarangay == null) {
                    return@forEach
                }
                val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
                calendar.time = timestamp.toDate()
                val year = calendar.get(Calendar.YEAR)
                val month = calendar.get(Calendar.MONTH)
                val monthKey = "$year-${String.format("%02d", month + 1)}"
                val dataMap = barangayDataMap.getOrPut(finalBarangay) { mutableMapOf() }
                val (alive, dead) = dataMap.getOrPut(monthKey) { Pair(0, 0) }
                dataMap[monthKey] = if (isAlive == 1L) {
                    Pair(alive + 1, dead)
                } else {
                    Pair(alive, dead + 1)
                }
            }
            val result = barangayDataMap.mapValues { (barangay, monthMap) ->
                monthMap.map { (monthKey, counts) ->
                    val parts = monthKey.split("-")
                    val year = parts[0].toIntOrNull() ?: 2024
                    val monthIndex = parts.getOrNull(1)?.toIntOrNull()?.minus(1) ?: 0
                    val monthName = if (monthIndex in 0..11) {
                        "${monthNames[monthIndex]} $year"
                    } else {
                        monthKey
                    }
                    BarangayMonthData(
                        barangay = barangay,
                        monthKey = monthKey,
                        monthName = monthName,
                        aliveCount = counts.first,
                        deadCount = counts.second
                    )
                }.sortedBy { it.monthKey }
            }
            
            barangayMonthData = result
            availableBarangays = result.keys.sorted()
            val summaries = result.map { (barangay, monthDataList) ->
                val totalAlive = monthDataList.sumOf { it.aliveCount }
                val totalDead = monthDataList.sumOf { it.deadCount }
                val totalTrunks = totalAlive + totalDead
                val healthPercentage = if (totalTrunks > 0) {
                    (totalAlive.toDouble() / totalTrunks.toDouble()) * 100.0
                } else {
                    0.0
                }
                // Density = Total number of alive trees / 1000 sqm
                val areaInSqm = 1000.0
                val density = totalAlive / areaInSqm
                val status = when {
                    healthPercentage >= 80.0 -> "Very Healthy"
                    healthPercentage >= 51.0 -> "Healthy"
                    healthPercentage >= 41.0 -> "Degraded"
                    else -> "Very Degraded"
                }
                val sortedData = monthDataList.sortedBy { it.monthKey }
                val trend = if (sortedData.size >= 2) {
                    val midPoint = sortedData.size / 2
                    val firstHalf = sortedData.take(midPoint)
                    val secondHalf = sortedData.drop(midPoint)
                    
                    val firstHalfAlive = firstHalf.sumOf { it.aliveCount }
                    val firstHalfTotal = firstHalf.sumOf { it.aliveCount + it.deadCount }
                    val secondHalfAlive = secondHalf.sumOf { it.aliveCount }
                    val secondHalfTotal = secondHalf.sumOf { it.aliveCount + it.deadCount }
                    
                    val firstHalfPct = if (firstHalfTotal > 0) (firstHalfAlive.toDouble() / firstHalfTotal) * 100.0 else 0.0
                    val secondHalfPct = if (secondHalfTotal > 0) (secondHalfAlive.toDouble() / secondHalfTotal) * 100.0 else 0.0
                    
                    when {
                        secondHalfPct > firstHalfPct + 2.0 -> "improving"
                        secondHalfPct < firstHalfPct - 2.0 -> "declining"
                        else -> "stable"
                    }
                } else {
                    "stable"
                }
                
                BarangaySummary(
                    barangay = barangay,
                    totalTrunks = totalTrunks,
                    aliveCount = totalAlive,
                    deadCount = totalDead,
                    healthPercentage = healthPercentage,
                    status = status,
                    trend = trend,
                    density = density
                )
            }.sortedByDescending { it.healthPercentage }
            
            barangaySummaries = summaries
        } catch (e: Exception) {
            android.util.Log.e("BarangayTrendsScreen", "Error loading data", e)
        } finally {
            isLoading = false
        }
    }
    val totalTrunks = barangaySummaries.sumOf { it.totalTrunks }
    val totalAlive = barangaySummaries.sumOf { it.aliveCount }
    val overallHealthPercentage = if (totalTrunks > 0) {
        (totalAlive.toDouble() / totalTrunks.toDouble()) * 100.0
    } else {
        0.0
    }
    val overallTrend = if (barangaySummaries.isNotEmpty()) {
        val improving = barangaySummaries.count { it.trend == "improving" }
        val declining = barangaySummaries.count { it.trend == "declining" }
        when {
            improving > declining -> "improving"
            declining > improving -> "declining"
            else -> "stable"
        }
    } else {
        "stable"
    }
    
    if (isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                Text("Loading trends...", color = MaterialTheme.colorScheme.secondary)
            }
        }
    } else if (availableBarangays.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "No detected trees found in Puerto Princesa City",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.secondary,
                textAlign = TextAlign.Center
            )
        }
    } else {
        val isSmallScreen = minOf(configuration.screenWidthDp, configuration.screenHeightDp) < 400
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(
                    horizontal = if (isSmallScreen) 12.dp else if (isTablet) 24.dp else 16.dp,
                    vertical = if (isSmallScreen) 12.dp else 16.dp
                )
        ) {
            Text(
                text = "Barangay Trends Dashboard",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                fontSize = if (isSmallScreen) 18.sp else if (isTablet) 24.sp else 20.sp,
                modifier = Modifier.padding(bottom = if (isSmallScreen) 6.dp else 8.dp)
            )
            Text(
                text = "Puerto Princesa City",
                style = MaterialTheme.typography.bodyMedium,
                fontSize = if (isSmallScreen) 12.sp else if (isTablet) 15.sp else 14.sp,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(bottom = if (isSmallScreen) 12.dp else 16.dp)
            )
            SummaryStatsCards(
                totalTrunks = totalTrunks,
                overallHealthPercentage = overallHealthPercentage,
                barangayCount = availableBarangays.size,
                trendIndicator = overallTrend,
                isTablet = isTablet,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                val isSmallScreen = minOf(configuration.screenWidthDp, configuration.screenHeightDp) < 400
                Column(modifier = Modifier.padding(if (isSmallScreen) 12.dp else if (isTablet) 20.dp else 16.dp)) {
                    Text(
                        text = "Alive Trunks Trend",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        fontSize = if (isSmallScreen) 14.sp else if (isTablet) 18.sp else 16.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = if (isSmallScreen) 6.dp else 8.dp)
                    )
                    Text(
                        text = "Shows the total number of alive rhizophora trees over time",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = if (isSmallScreen) 10.sp else if (isTablet) 13.sp else 12.sp,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(bottom = if (isSmallScreen) 12.dp else 16.dp)
                    )
                    MultiBarangayTrendChart(
                        barangayMonthData = barangayMonthData,
                        selectedBarangays = selectedBarangays,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(if (isSmallScreen) 250.dp else if (isTablet) 400.dp else 300.dp)
                    )
                }
            }
            
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                val isSmallScreen = minOf(configuration.screenWidthDp, configuration.screenHeightDp) < 400
                Column(modifier = Modifier.padding(if (isSmallScreen) 12.dp else if (isTablet) 20.dp else 16.dp)) {
                    Text(
                        text = "Density Trend (Trees per 1000 sqm)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        fontSize = if (isSmallScreen) 14.sp else if (isTablet) 18.sp else 16.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = if (isSmallScreen) 6.dp else 8.dp)
                    )
                    Text(
                        text = "Shows the density of alive rhizophora trees over time",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = if (isSmallScreen) 10.sp else if (isTablet) 13.sp else 12.sp,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(bottom = if (isSmallScreen) 12.dp else 16.dp)
                    )
                    DensityTrendChart(
                        barangayMonthData = barangayMonthData,
                        selectedBarangays = selectedBarangays,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(if (isSmallScreen) 250.dp else if (isTablet) 400.dp else 300.dp)
                    )
                }
            }
            
            // Barangay Selection Card - Below both charts
            if (availableBarangays.isNotEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    elevation = CardDefaults.cardElevation(4.dp)
                ) {
                    val isSmallScreen = minOf(configuration.screenWidthDp, configuration.screenHeightDp) < 400
                    Column(modifier = Modifier.padding(if (isSmallScreen) 12.dp else if (isTablet) 20.dp else 16.dp)) {
                        Text(
                            text = "Select Barangays",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            fontSize = if (isSmallScreen) 14.sp else if (isTablet) 18.sp else 16.sp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = if (isSmallScreen) 8.dp else 12.dp)
                        )
                        
                        val barangayColors = listOf(
                            Color(0xFF2196F3), Color(0xFF9C27B0), Color(0xFFFF9800),
                            Color(0xFF00BCD4), Color(0xFF8BC34A), Color(0xFFE91E63),
                            Color(0xFF3F51B5), Color(0xFF009688), Color(0xFFFFC107)
                        )
                        
                        // Calculate chips per row based on screen size
                        val chipsPerRow = if (isSmallScreen) 2 else if (isTablet) 5 else 4
                        val chunkedBarangays = availableBarangays.chunked(chipsPerRow)
                        
                        // Use Column with Rows for natural wrapping without scroll
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(if (isSmallScreen) 6.dp else 8.dp)
                        ) {
                            chunkedBarangays.forEach { row ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(if (isSmallScreen) 6.dp else 8.dp)
                                ) {
                                    row.forEach { barangay ->
                                        val barangayIndex = availableBarangays.indexOf(barangay)
                                        val barangayColor = barangayColors[barangayIndex % barangayColors.size]
                                        
                                        FilterChip(
                                            selected = selectedBarangays.contains(barangay),
                                            onClick = {
                                                selectedBarangays = if (selectedBarangays.contains(barangay)) {
                                                    selectedBarangays - barangay
                                                } else {
                                                    if (selectedBarangays.size < 10) {
                                                        selectedBarangays + barangay
                                                    } else {
                                                        selectedBarangays
                                                    }
                                                }
                                            },
                                            label = { 
                                                Text(
                                                    barangay, 
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontSize = if (isSmallScreen) 10.sp else if (isTablet) 13.sp else 12.sp,
                                                    color = barangayColor,
                                                    fontWeight = if (selectedBarangays.contains(barangay)) FontWeight.Bold else FontWeight.Normal,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                ) 
                                            },
                                            modifier = Modifier.weight(1f),
                                            enabled = selectedBarangays.contains(barangay) || selectedBarangays.size < 10
                                        )
                                    }
                                    // Fill remaining space in row
                                    repeat(chipsPerRow - row.size) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                        
                        if (selectedBarangays.size >= 10) {
                            Text(
                                text = "Maximum 10 barangays can be selected for comparison",
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = if (isSmallScreen) 10.sp else 12.sp,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(top = if (isSmallScreen) 8.dp else 12.dp)
                            )
                        }
                    }
                }
            }
            
            QuickStatsTable(
                barangaySummaries = barangaySummaries,
                selectedBarangays = selectedBarangays,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

private fun extractBarangayFromAddress(address: String): String? {
    val patterns = listOf(
        Regex("Barangay\\s+([^,]+)", RegexOption.IGNORE_CASE),
        Regex("Brgy\\.?\\s+([^,]+)", RegexOption.IGNORE_CASE),
        Regex("Brgy\\s+([^,]+)", RegexOption.IGNORE_CASE)
    )
    
    for (pattern in patterns) {
        val match = pattern.find(address)
        if (match != null) {
            return match.groupValues[1].trim()
        }
    }
    
    return null
}

