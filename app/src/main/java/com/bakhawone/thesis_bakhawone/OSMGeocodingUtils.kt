package com.bakhawone.thesis_bakhawone

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL

object OSMGeocodingUtils {
    private const val OSM_REVERSE_GEOCODE_URL = "https://nominatim.openstreetmap.org/reverse"

    /**
     * Reverse geocode coordinates to get a human-readable address using OSM Nominatim API.
     * @param latitude The latitude coordinate
     * @param longitude The longitude coordinate
     * @return A formatted address string, or null if geocoding fails
     */
    suspend fun reverseGeocode(latitude: Double, longitude: Double): String? = withContext(Dispatchers.IO) {
        try {
            val url = "$OSM_REVERSE_GEOCODE_URL?format=json&lat=$latitude&lon=$longitude&zoom=18&addressdetails=1"
            
            val connection = URL(url).openConnection().apply {
                // Set User-Agent as required by OSM Nominatim usage policy
                setRequestProperty("User-Agent", "BakhawOneApp/1.0")
                connectTimeout = 10000
                readTimeout = 10000
            }
            
            val response = connection.getInputStream().bufferedReader().use { it.readText() }
            val json = JSONObject(response)
            
            // Extract address from response
            val address = json.optJSONObject("address")
            if (address != null) {
                // Try to build a meaningful address from available fields
                val displayName = json.optString("display_name", "")
                if (displayName.isNotEmpty()) {
                    return@withContext displayName
                }
                
                // Fallback: build address from components
                val parts = mutableListOf<String>()
                address.optString("road")?.takeIf { it.isNotEmpty() }?.let { parts.add(it) }
                address.optString("suburb")?.takeIf { it.isNotEmpty() }?.let { parts.add(it) }
                address.optString("city")?.takeIf { it.isNotEmpty() }?.let { parts.add(it) }
                address.optString("state")?.takeIf { it.isNotEmpty() }?.let { parts.add(it) }
                address.optString("country")?.takeIf { it.isNotEmpty() }?.let { parts.add(it) }
                
                if (parts.isNotEmpty()) {
                    return@withContext parts.joinToString(", ")
                }
            }
            
            // If no address found, return coordinates as fallback
            "$latitude, $longitude"
        } catch (e: Exception) {
            e.printStackTrace()
            // Return coordinates as fallback if geocoding fails
            "$latitude, $longitude"
        }
    }
}

