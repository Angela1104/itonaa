package com.bakhawone.thesis_bakhawone.ui.theme

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.Icons.Default
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import android.content.Context
import android.content.Intent
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.widget.Toast
import android.graphics.Paint
import android.graphics.Typeface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.remember

// Data classes for print structure
data class LocationPrintData(
    val locationName: String,
    val pinnedLocationDocId: String,
    val months: Map<String, Map<String, List<TrunkPrintData>>> // monthKey -> dateString -> list of trunks
)

data class TrunkPrintData(
    val trunkId: String,
    val status: String,
    val dbhCm: Double,
    val basalArea: Double
)

@Composable
fun PrintScreen() {
    val context = LocalContext.current
    val db = remember { FirebaseFirestore.getInstance() }
    val userId = remember { FirebaseAuth.getInstance().currentUser?.uid ?: "" }
    
    var selectedYear by remember { mutableStateOf<Int?>(null) }
    var expanded by remember { mutableStateOf(false) }
    var locationDataList by remember { mutableStateOf<List<LocationPrintData>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var isLoadingInitial by remember { mutableStateOf(true) }
    var availableYears by remember { mutableStateOf<List<Int>>(emptyList()) }
    var hasData by remember { mutableStateOf(false) }
    
    val dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault())
    val monthNames = arrayOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December"
    )
    
    // Load available years on initial load and set most recent year
    LaunchedEffect(Unit) {
        if (userId.isNotEmpty()) {
            isLoadingInitial = true
            try {
                val trunkSnapshot = db.collection("trunk_detections")
                    .whereEqualTo("user_id", userId)
                    .whereEqualTo("is_rhizophora", 1)
                    .get()
                    .await()
                
                if (trunkSnapshot.documents.isEmpty()) {
                    // No data found
                    hasData = false
                    availableYears = emptyList()
                } else {
                    // Data found, extract years
                    hasData = true
                    val yearsSet = mutableSetOf<Int>()
                    trunkSnapshot.documents.forEach { doc ->
                        val timestamp = doc.getTimestamp("timestamp_firestore")?.toDate()
                        if (timestamp != null) {
                            val calendar = Calendar.getInstance().apply { time = timestamp }
                            yearsSet.add(calendar.get(Calendar.YEAR))
                        }
                    }
                    availableYears = yearsSet.sortedDescending()
                    
                    // Set the most recent year as default
                    if (availableYears.isNotEmpty() && selectedYear == null) {
                        selectedYear = availableYears[0]
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("PrintScreen", "Error loading years", e)
                hasData = false
            } finally {
                isLoadingInitial = false
            }
        } else {
            isLoadingInitial = false
            hasData = false
        }
    }
    
    // Load data when year is selected
    LaunchedEffect(selectedYear) {
        if (selectedYear != null) {
            isLoading = true
            try {
                // Get all trunk detections for the selected year (user-specific only)
                val trunkSnapshot = db.collection("trunk_detections")
                    .whereEqualTo("user_id", userId)
                    .whereEqualTo("is_rhizophora", 1)
                    .get()
                    .await()
                
                val locationDataMap = mutableMapOf<String, LocationPrintData>()
                
                // Group by location, then by month, then by date
                trunkSnapshot.documents.forEach { doc ->
                    val timestamp = doc.getTimestamp("timestamp_firestore")?.toDate()
                    val pinnedLocationId = doc.getString("pinned_location_id") ?: return@forEach
                    val locationName = doc.getString("pinned_location") ?: return@forEach
                    
                    if (timestamp != null) {
                        val calendar = Calendar.getInstance().apply { time = timestamp }
                        val year = calendar.get(Calendar.YEAR)
                        val month = calendar.get(Calendar.MONTH)
                        
                        // Filter by selected year
                        if (year == selectedYear) {
                            val monthKey = "${monthNames[month]} $year"
                            val dateString = dateFormat.format(timestamp)
                            
                            // Get trunk data
                            val trunkId = doc.id
                            val isAlive = doc.getLong("is_alive") ?: 0L
                            val status = if (isAlive == 1L) "Alive" else "Dead"
                            val dbhCm = doc.getDouble("dbh_cm") ?: 0.0
                            val basalArea = 0.00007854 * dbhCm
                            
                            val trunkData = TrunkPrintData(
                                trunkId = trunkId,
                                status = status,
                                dbhCm = dbhCm,
                                basalArea = basalArea
                            )
                            
                            // Get or create location data
                            val locationData = locationDataMap.getOrPut(pinnedLocationId) {
                                LocationPrintData(
                                    locationName = locationName,
                                    pinnedLocationDocId = pinnedLocationId,
                                    months = mutableMapOf()
                                )
                            }
                            
                            // Get or create month data
                            val monthDates = (locationData.months as MutableMap).getOrPut(monthKey) {
                                mutableMapOf<String, MutableList<TrunkPrintData>>()
                            }
                            
                            // Get or create date data (survey)
                            val dateTrunks = (monthDates as MutableMap).getOrPut(dateString) {
                                mutableListOf()
                            }
                            
                            // Add trunk data
                            (dateTrunks as MutableList).add(trunkData)
                        }
                    }
                }
                
                locationDataList = locationDataMap.values.toList()
                
            } catch (e: Exception) {
                android.util.Log.e("PrintScreen", "Error loading print data", e)
            } finally {
                isLoading = false
            }
        } else {
            locationDataList = emptyList()
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Container with header
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(4.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                // Header with Year Filter
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Print Records",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    
                    // Year filter button (matcha green)
                    if (availableYears.isNotEmpty()) {
                        Box {
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
                                        selectedYear?.toString() ?: "",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium,
                                        color = Color(0xFF1B5E20), // Dark green text
                                        maxLines = 1,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Icon(
                                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
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
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Content area - Show locations with months and dates
                if (isLoadingInitial) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else if (!hasData) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Print,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.secondary
                            )
                            Text(
                                text = "No data to print",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Text(
                                text = "Start detecting trunks in AR to generate print records",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f)
                            )
                        }
                    }
                } else if (isLoading) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else if (locationDataList.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No records found for $selectedYear",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        locationDataList.forEach { locationData ->
                            // Location container
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                elevation = CardDefaults.cardElevation(2.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp)
                                ) {
                                    Text(
                                        text = locationData.locationName,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(bottom = 12.dp)
                                    )
                                    
                                    // Months for this location
                                    locationData.months.toSortedMap().forEach { (monthKey, dateMap) ->
                                        // Month separator
                                        Divider(
                                            modifier = Modifier.padding(vertical = 8.dp)
                                        )
                                        
                                        Text(
                                            text = monthKey,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier.padding(vertical = 8.dp)
                                        )
                                        
                                        // Dates (surveys) with print buttons
                                        dateMap.toSortedMap().forEach { (dateString, trunks) ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 4.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = dateString,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                
                                                // Print button with dropdown
                                                PrintFileDropdown(
                                                    context = context,
                                                    locationName = locationData.locationName,
                                                    date = dateString,
                                                    trunks = trunks,
                                                    monthKey = monthKey
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
    }
}

@Composable
fun PrintFileDropdown(
    context: Context,
    locationName: String,
    date: String,
    trunks: List<TrunkPrintData>,
    monthKey: String
) {
    var expanded by remember { mutableStateOf(false) }
    
    // Launcher for Excel file save (CSV format - Excel can open CSV files)
    val excelLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        if (uri != null) {
            exportToExcel(context, locationName, date, trunks, uri)
        }
    }
    
    // Launcher for PDF file save
    val pdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri: Uri? ->
        if (uri != null) {
            exportToPdf(context, locationName, date, trunks, uri)
        }
    }
    
    Box {
        Button(
            onClick = { expanded = !expanded },
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier.height(36.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Print,
                    contentDescription = "Print",
                    modifier = Modifier.size(18.dp)
                )
                Text("Print")
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("Excel (.csv)") },
                onClick = {
                    expanded = false
                    val fileName = "${locationName.replace(" ", "_")}_${date.replace(" ", "_").replace(",", "")}.csv"
                    excelLauncher.launch(fileName)
                }
            )
            DropdownMenuItem(
                text = { Text("PDF") },
                onClick = {
                    expanded = false
                    val fileName = "${locationName.replace(" ", "_")}_${date.replace(" ", "_").replace(",", "")}.pdf"
                    pdfLauncher.launch(fileName)
                }
            )
        }
    }
}

fun exportToExcel(context: Context, locationName: String, date: String, trunks: List<TrunkPrintData>, uri: Uri) {
    try {
        // Calculate total basal area per acre
        val totalBasalArea = trunks.sumOf { it.basalArea }
        
        // Create CSV content (can be opened in Excel)
        val csvContent = StringBuilder()
        
        // Header rows - Location and Date
        csvContent.append("Location: $locationName\n")
        csvContent.append("Date: $date\n")
        csvContent.append("\n") // Empty row for spacing
        
        // Table header row
        csvContent.append("Trunk ID,Status,DBH(cm),Basal Area(m²)\n")
        
        // Data rows
        trunks.forEach { trunk ->
            csvContent.append("${trunk.trunkId},${trunk.status},${String.format("%.1f", trunk.dbhCm)},${String.format("%.6f", trunk.basalArea)}\n")
        }
        
        // Total row
        csvContent.append("\nTotal Basal Area per Acre,${String.format("%.6f", totalBasalArea)}\n")
        
        // Write to URI using content resolver with UTF-8 encoding
        context.contentResolver.openOutputStream(uri)?.use { outputStream ->
            outputStream.write(csvContent.toString().toByteArray(Charsets.UTF_8))
        }
        
        Toast.makeText(
            context,
            "Excel file saved successfully",
            Toast.LENGTH_LONG
        ).show()
        
        android.util.Log.d("PrintScreen", "Excel exported to: $uri")
    } catch (e: Exception) {
        android.util.Log.e("PrintScreen", "Error exporting to Excel", e)
        Toast.makeText(context, "Error exporting to Excel: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

fun exportToPdf(context: Context, locationName: String, date: String, trunks: List<TrunkPrintData>, uri: Uri) {
    try {
        // Calculate total basal area per acre
        val totalBasalArea = trunks.sumOf { it.basalArea }
        
        // Create PDF document
        val pdfDocument = PdfDocument()
        
        // Page dimensions (A4 size in points: 595 x 842)
        val pageWidth = 595
        val pageHeight = 842
        val margin = 50
        val contentWidth = pageWidth - (2 * margin)
        val contentHeight = pageHeight - (2 * margin)
        
        // Create a page
        var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
        var page = pdfDocument.startPage(pageInfo)
        var canvas = page.canvas
        
        // Set up paint objects
        val titlePaint = Paint().apply {
            color = android.graphics.Color.BLACK
            textSize = 18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        
        val headerPaint = Paint().apply {
            color = android.graphics.Color.BLACK
            textSize = 14f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        
        val textPaint = Paint().apply {
            color = android.graphics.Color.BLACK
            textSize = 12f
            isAntiAlias = true
        }
        
        var yPos = margin.toFloat() + 30f
        
        // Header - Location
        val locationText = "Location: $locationName"
        canvas.drawText(locationText, margin.toFloat(), yPos, titlePaint)
        yPos += 30f
        
        // Header - Date (below Location)
        val dateText = "Date: $date"
        canvas.drawText(dateText, margin.toFloat(), yPos, titlePaint)
        yPos += 40f
        
        // Draw a line separator
        canvas.drawLine(margin.toFloat(), yPos, (pageWidth - margin).toFloat(), yPos, textPaint)
        yPos += 20f
        
        // Table headers
        val headerY = yPos
        canvas.drawText("Trunk ID", margin.toFloat(), headerY, headerPaint)
        canvas.drawText("Status", margin + 150f, headerY, headerPaint)
        canvas.drawText("DBH(cm)", margin + 250f, headerY, headerPaint)
        canvas.drawText("Basal Area(m²)", margin + 350f, headerY, headerPaint)
        
        yPos += 30f
        
        // Draw line under headers
        canvas.drawLine(margin.toFloat(), yPos - 10f, (pageWidth - margin).toFloat(), yPos - 10f, textPaint)
        yPos += 10f
        
        // Data rows
        trunks.forEach { trunk ->
            // Check if we need a new page
            if (yPos > (pageHeight - margin - 50)) {
                pdfDocument.finishPage(page)
                val newPageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pdfDocument.pages.size + 1).create()
                val newPage = pdfDocument.startPage(newPageInfo)
                val newCanvas = newPage.canvas
                yPos = margin.toFloat() + 30f
                
                // Redraw headers on new page
                newCanvas.drawText("Trunk ID", margin.toFloat(), yPos, headerPaint)
                newCanvas.drawText("Status", margin + 150f, yPos, headerPaint)
                newCanvas.drawText("DBH(cm)", margin + 250f, yPos, headerPaint)
                newCanvas.drawText("Basal Area(m²)", margin + 350f, yPos, headerPaint)
                yPos += 30f
                newCanvas.drawLine(margin.toFloat(), yPos - 10f, (pageWidth - margin).toFloat(), yPos - 10f, textPaint)
                yPos += 10f
                
                // Draw trunk data
                newCanvas.drawText(trunk.trunkId.take(12), margin.toFloat(), yPos, textPaint)
                newCanvas.drawText(trunk.status, margin + 150f, yPos, textPaint)
                newCanvas.drawText(String.format("%.1f", trunk.dbhCm), margin + 250f, yPos, textPaint)
                newCanvas.drawText(String.format("%.6f", trunk.basalArea), margin + 350f, yPos, textPaint)
                yPos += 20f
                
                canvas = newCanvas
                page = newPage
            } else {
                canvas.drawText(trunk.trunkId.take(12), margin.toFloat(), yPos, textPaint)
                canvas.drawText(trunk.status, margin + 150f, yPos, textPaint)
                canvas.drawText(String.format("%.1f", trunk.dbhCm), margin + 250f, yPos, textPaint)
                canvas.drawText(String.format("%.6f", trunk.basalArea), margin + 350f, yPos, textPaint)
                yPos += 20f
            }
        }
        
        yPos += 10f
        
        // Draw line before total
        canvas.drawLine(margin.toFloat(), yPos, (pageWidth - margin).toFloat(), yPos, textPaint)
        yPos += 20f
        
        // Total Basal Area per Acre
        val totalText = "Total Basal Area per Acre: ${String.format("%.6f", totalBasalArea)}"
        canvas.drawText(totalText, margin.toFloat(), yPos, headerPaint)
        
        // Finish the page
        pdfDocument.finishPage(page)
        
        // Write PDF to URI
        context.contentResolver.openOutputStream(uri)?.use { outputStream ->
            pdfDocument.writeTo(outputStream)
        }
        
        pdfDocument.close()
        
        Toast.makeText(
            context,
            "PDF file saved successfully",
            Toast.LENGTH_LONG
        ).show()
        
        android.util.Log.d("PrintScreen", "PDF exported to: $uri")
    } catch (e: Exception) {
        android.util.Log.e("PrintScreen", "Error exporting to PDF", e)
        Toast.makeText(context, "Error exporting to PDF: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}
