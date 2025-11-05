package com.bakhawone.thesis_bakhawone

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.WriteBatch
import java.text.SimpleDateFormat
import java.util.*
import java.util.Calendar

/**
 * Utility class to generate and save 100 trunk detections for testing
 * 
 * Usage: Call generateTrunkDetections() once to populate Firebase with test data
 */
object GenerateTrunkDetections {
    
    private const val TAG = "GenerateTrunkDetections"
    private const val SESSION_ID = "session_c8a51a15dc00440dba7e74f64c970794"
    private const val PINNED_LOCATION_ID = "WY7ZXSplsgPslKsToM1V"
    private const val PINNED_LOCATION_NAME = "Mangrove Area 1" // Update this with actual name
    
    private val random = Random()
    
    /**
     * Generate and save 100 trunk detections to Firebase
     * 
     * @param pinnedLocationName The name of the pinned location (optional, defaults to above)
     */
    fun generateTrunkDetections(pinnedLocationName: String = PINNED_LOCATION_NAME) {
        val db = FirebaseFirestore.getInstance()
        val batch = db.batch()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        
        val now = Date()
        val baseTimestamp = now.time
        
        // Generate 100 trunk detections
        for (i in 1..100) {
            val trunkId = "trunk_${UUID.randomUUID().toString().replace("-", "").substring(0, 8)}"
            
            // Random position within a 20m x 20m area centered at (0,0,0)
            // Spread trunks in a circular pattern around centerpoint
            val angle = random.nextDouble() * 2 * Math.PI
            val radius = random.nextDouble() * 10.0 // Up to 10 meters from center
            val x = radius * Math.cos(angle)
            val y = radius * Math.sin(angle)
            val z = random.nextDouble() * 0.5 - 0.25 // Small vertical variation
            
            // DBH (Diameter at Breast Height) in cm - realistic mangrove sizes
            // Most mangroves are 5-30 cm DBH, with some larger ones up to 50 cm
            val dbhCm = when {
                random.nextDouble() < 0.7 -> random.nextDouble() * 25.0 + 5.0  // 70% small-medium (5-30cm)
                random.nextDouble() < 0.9 -> random.nextDouble() * 20.0 + 30.0 // 20% medium-large (30-50cm)
                else -> random.nextDouble() * 20.0 + 50.0 // 10% large (50-70cm)
            }
            
            // 80% alive, 20% dead
            val isAlive = if (random.nextDouble() < 0.8) 1 else 0
            
            // All are Rhizophora (based on your detection classes)
            val isRhizophora = 1
            
            // Timestamp - spread over the last hour
            val timestampOffset = (random.nextDouble() * 3600000).toLong() // Random within last hour
            val detectionTime = Date(baseTimestamp - timestampOffset)
            
            val data = hashMapOf<String, Any>(
                "session_id" to SESSION_ID,
                "pinned_location_id" to PINNED_LOCATION_ID,
                "pinned_location" to pinnedLocationName,
                "inside_boundary" to true,
                "vector_position" to listOf(x, y, z),
                "is_rhizophora" to isRhizophora,
                "is_alive" to isAlive,
                "dbh_cm" to dbhCm,
                "timestamp" to dateFormat.format(detectionTime),
                "timestamp_firestore" to com.google.firebase.Timestamp(detectionTime)
            )
            
            val docRef = db.collection("trunk_detections").document(trunkId)
            batch.set(docRef, data)
            
            if (i % 50 == 0) {
                Log.d(TAG, "Generated $i/100 trunk detections...")
            }
        }
        
        // Commit batch write
        batch.commit()
            .addOnSuccessListener {
                Log.d(TAG, "Successfully generated and saved 100 trunk detections!")
                Log.d(TAG, "Session ID: $SESSION_ID")
                Log.d(TAG, "Pinned Location ID: $PINNED_LOCATION_ID")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to generate trunk detections", e)
            }
    }
    
    /**
     * Generate trunk detections in smaller batches to avoid Firebase limits
     * Generates 400 trunks total: 100 from January 2024, 100 from September 2024,
     * 100 from May 2025, and 100 from June 2025
     * (Firebase batch limit is 500 operations, but we'll use 50 per batch)
     */
    fun generateTrunkDetectionsInBatches(pinnedLocationName: String = PINNED_LOCATION_NAME) {
        val db = FirebaseFirestore.getInstance()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        
        val batchSize = 50
        val trunksPerMonth = 100
        val totalTrunks = 400 // 4 months × 100 trunks each
        var generatedCount = 0
        
        // Define same-day dates for each month (all on 15th)
        val january2024SameDay = Calendar.getInstance().apply {
            set(2024, Calendar.JANUARY, 15, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.time
        
        val september2024SameDay = Calendar.getInstance().apply {
            set(2024, Calendar.SEPTEMBER, 15, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.time
        
        val may2025SameDay = Calendar.getInstance().apply {
            set(2025, Calendar.MAY, 15, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.time
        
        val june2025SameDay = Calendar.getInstance().apply {
            set(2025, Calendar.JUNE, 15, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.time
        
        fun getMonthName(monthStart: Date): String {
            val cal = Calendar.getInstance().apply { time = monthStart }
            val year = cal.get(Calendar.YEAR)
            val month = cal.get(Calendar.MONTH)
            val monthNames = arrayOf(
                "January", "February", "March", "April", "May", "June",
                "July", "August", "September", "October", "November", "December"
            )
            return "${monthNames[month]} $year"
        }
        
        fun createBatch(
            startIndex: Int, 
            endIndex: Int, 
            batch: WriteBatch, 
            monthStart: Date,
            onComplete: () -> Unit
        ) {
            for (i in startIndex until endIndex) {
                val trunkId = "trunk_${UUID.randomUUID().toString().replace("-", "").substring(0, 8)}"
                
                // Random position within a 20m x 20m area
                val angle = random.nextDouble() * 2 * Math.PI
                val radius = random.nextDouble() * 10.0
                val x = radius * Math.cos(angle)
                val y = radius * Math.sin(angle)
                val z = random.nextDouble() * 0.5 - 0.25
                
                // DBH - realistic mangrove sizes
                val dbhCm = when {
                    random.nextDouble() < 0.7 -> random.nextDouble() * 25.0 + 5.0
                    random.nextDouble() < 0.9 -> random.nextDouble() * 20.0 + 30.0
                    else -> random.nextDouble() * 20.0 + 50.0
                }
                
                // 80% alive, 20% dead
                val isAlive = if (random.nextDouble() < 0.8) 1 else 0
                
                // Use the same day for all detections in this month
                // monthStart already represents a specific day, so use it directly
                val detectionTime = monthStart
                
                val data = hashMapOf<String, Any>(
                    "session_id" to SESSION_ID,
                    "pinned_location_id" to PINNED_LOCATION_ID,
                    "pinned_location" to pinnedLocationName,
                    "inside_boundary" to true,
                    "vector_position" to listOf(x, y, z),
                    "is_rhizophora" to 1,
                    "is_alive" to isAlive,
                    "dbh_cm" to dbhCm,
                    "timestamp" to dateFormat.format(detectionTime),
                    "timestamp_firestore" to com.google.firebase.Timestamp(detectionTime)
                )
                
                val docRef = db.collection("trunk_detections").document(trunkId)
                batch.set(docRef, data)
                generatedCount++
            }
            
            batch.commit()
                .addOnSuccessListener {
                    val month = getMonthName(monthStart)
                    Log.d(TAG, "Batch saved: $generatedCount/$totalTrunks trunk detections ($month)")
                    onComplete()
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to save batch", e)
                }
        }
        
        // Generate January 2024 batches (100 trunks) - all on the same day (15th)
        val janBatch1 = db.batch()
        createBatch(0, batchSize, janBatch1, january2024SameDay) {
            val janBatch2 = db.batch()
            createBatch(batchSize, trunksPerMonth, janBatch2, january2024SameDay) {
                // January complete, now generate September 2024 batches (100 trunks) - all on the same day (15th)
                val sepBatch1 = db.batch()
                val sepStartIndex = trunksPerMonth
                createBatch(sepStartIndex, sepStartIndex + batchSize, sepBatch1, september2024SameDay) {
                    val sepBatch2 = db.batch()
                    createBatch(sepStartIndex + batchSize, sepStartIndex + trunksPerMonth, sepBatch2, september2024SameDay) {
                        // September complete, now generate May 2025 batches (100 trunks) - all on the same day (15th)
                        val mayBatch1 = db.batch()
                        val mayStartIndex = trunksPerMonth * 2
                        createBatch(mayStartIndex, mayStartIndex + batchSize, mayBatch1, may2025SameDay) {
                            val mayBatch2 = db.batch()
                            createBatch(mayStartIndex + batchSize, mayStartIndex + trunksPerMonth, mayBatch2, may2025SameDay) {
                                // May complete, now generate June 2025 batches (100 trunks) - all on the same day (15th)
                                val juneBatch1 = db.batch()
                                val juneStartIndex = trunksPerMonth * 3
                                createBatch(juneStartIndex, juneStartIndex + batchSize, juneBatch1, june2025SameDay) {
                                    val juneBatch2 = db.batch()
                                    createBatch(juneStartIndex + batchSize, juneStartIndex + trunksPerMonth, juneBatch2, june2025SameDay) {
                                        Log.d(TAG, "✅ Successfully generated all $totalTrunks trunk detections!")
                                        Log.d(TAG, "  - 100 trunks from January 15, 2024")
                                        Log.d(TAG, "  - 100 trunks from September 15, 2024")
                                        Log.d(TAG, "  - 100 trunks from May 15, 2025")
                                        Log.d(TAG, "  - 100 trunks from June 15, 2025")
                                        Log.d(TAG, "Session ID: $SESSION_ID")
                                        Log.d(TAG, "Pinned Location ID: $PINNED_LOCATION_ID")
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

