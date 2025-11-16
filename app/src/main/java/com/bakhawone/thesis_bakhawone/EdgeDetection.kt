package com.bakhawone.thesis_bakhawone

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Edge detection utility for trunk DBH measurement.
 * Detects left and right edges of trunk in the top section of bounding box.
 */
object EdgeDetection {
    
    data class EdgeLines(
        val leftEdgeX: Float,    // X coordinate of left edge line
        val rightEdgeX: Float,   // X coordinate of right edge line
        val centerY: Float,      // Y coordinate (center of top section)
        val edgeWidthPx: Float   // Distance between edges in pixels
    )
    
    /**
     * Detect trunk edges in the top half of bounding box using Sobel edge detection.
     * 
     * @param bitmap Original camera image
     * @param box Bounding box of detected trunk
     * @return EdgeLines with left and right edge positions, or null if detection fails
     */
    fun detectTrunkEdges(bitmap: Bitmap, box: android.graphics.RectF): EdgeLines? {
        try {
            // Extract top half of bounding box
            val boxHeight = box.bottom - box.top
            val topHalfBox = android.graphics.RectF(
                box.left.coerceAtLeast(0f),
                box.top.coerceAtLeast(0f),
                box.right.coerceAtMost(bitmap.width.toFloat()),
                (box.top + boxHeight / 2f).coerceAtMost(bitmap.height.toFloat())
            )
            
            // Ensure valid region
            if (topHalfBox.width() <= 0 || topHalfBox.height() <= 0) {
                return null
            }
            
            // Extract ROI (Region of Interest) - top half section
            val roiLeft = topHalfBox.left.toInt()
            val roiTop = topHalfBox.top.toInt()
            val roiWidth = topHalfBox.width().toInt()
            val roiHeight = topHalfBox.height().toInt()
            
            if (roiLeft < 0 || roiTop < 0 || 
                roiLeft + roiWidth > bitmap.width || 
                roiTop + roiHeight > bitmap.height) {
                return null
            }
            
            val roiBitmap = Bitmap.createBitmap(
                bitmap, 
                roiLeft, 
                roiTop, 
                roiWidth, 
                roiHeight
            )
            
            // Convert to grayscale
            val grayBitmap = toGrayscale(roiBitmap)
            
            // Apply Sobel edge detection
            val edgeMap = sobelEdgeDetection(grayBitmap)
            
            // Find leftmost and rightmost strong edges
            val centerY = roiHeight / 2f
            val leftEdge = findLeftmostEdge(edgeMap, centerY.toInt(), roiWidth, roiHeight)
            val rightEdge = findRightmostEdge(edgeMap, centerY.toInt(), roiWidth, roiHeight)
            
            roiBitmap.recycle()
            grayBitmap.recycle()
            
            if (leftEdge == null || rightEdge == null || rightEdge <= leftEdge) {
                return null
            }
            
            // Convert back to original image coordinates
            val leftEdgeX = roiLeft + leftEdge.toFloat()
            val rightEdgeX = roiLeft + rightEdge.toFloat()
            val centerYOriginal = roiTop + centerY
            val edgeWidthPx = rightEdgeX - leftEdgeX
            
            return EdgeLines(
                leftEdgeX = leftEdgeX,
                rightEdgeX = rightEdgeX,
                centerY = centerYOriginal,
                edgeWidthPx = edgeWidthPx
            )
        } catch (e: Exception) {
            android.util.Log.e("EdgeDetection", "Error detecting edges: ${e.message}", e)
            return null
        }
    }
    
    /**
     * Convert bitmap to grayscale.
     */
    private fun toGrayscale(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val grayBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        
        for (i in pixels.indices) {
            val pixel = pixels[i]
            val r = Color.red(pixel)
            val g = Color.green(pixel)
            val b = Color.blue(pixel)
            // Grayscale conversion: Y = 0.299*R + 0.587*G + 0.114*B
            val gray = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
            pixels[i] = Color.rgb(gray, gray, gray)
        }
        
        grayBitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return grayBitmap
    }
    
    /**
     * Apply Sobel edge detection operator.
     */
    private fun sobelEdgeDetection(bitmap: Bitmap): Array<IntArray> {
        val width = bitmap.width
        val height = bitmap.height
        val edgeMap = Array(height) { IntArray(width) }
        
        // Sobel kernels
        val sobelX = arrayOf(
            intArrayOf(-1, 0, 1),
            intArrayOf(-2, 0, 2),
            intArrayOf(-1, 0, 1)
        )
        val sobelY = arrayOf(
            intArrayOf(-1, -2, -1),
            intArrayOf(0, 0, 0),
            intArrayOf(1, 2, 1)
        )
        
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                var gx = 0
                var gy = 0
                
                // Apply Sobel kernels
                for (ky in -1..1) {
                    for (kx in -1..1) {
                        val idx = (y + ky) * width + (x + kx)
                        val gray = Color.red(pixels[idx]) // Already grayscale
                        gx += gray * sobelX[ky + 1][kx + 1]
                        gy += gray * sobelY[ky + 1][kx + 1]
                    }
                }
                
                // Calculate gradient magnitude
                val magnitude = sqrt((gx * gx + gy * gy).toDouble()).toInt()
                edgeMap[y][x] = magnitude.coerceIn(0, 255)
            }
        }
        
        return edgeMap
    }
    
    /**
     * Find leftmost strong vertical edge representing the trunk.
     * Focuses on vertical edges near the bounding box center to avoid background.
     */
    private fun findLeftmostEdge(
        edgeMap: Array<IntArray>, 
        centerY: Int, 
        width: Int, 
        height: Int
    ): Int? {
        if (centerY < 0 || centerY >= height) return null
        
        val threshold = 60 // Edge strength threshold (increased for better trunk detection)
        val searchRange = 8 // Search in ±8 pixels around centerY for vertical continuity
        val centerX = width / 2 // Use center of bounding box as reference
        
        // First, find strong vertical edges (trunk edges are typically vertical)
        // Search from center towards left to find the trunk's left edge
        var bestEdge: Int? = null
        var maxVerticalStrength = 0
        
        // Start searching from center, moving left
        for (x in centerX downTo 0) {
            var verticalStrength = 0
            var edgeCount = 0
            
            // Check vertical continuity - trunk edges should be strong across multiple rows
            for (y in (centerY - searchRange).coerceAtLeast(0) 
                     until (centerY + searchRange).coerceAtMost(height)) {
                if (edgeMap[y][x] > threshold) {
                    verticalStrength += edgeMap[y][x]
                    edgeCount++
                }
            }
            
            // Require at least 3 strong edge points for vertical continuity
            if (edgeCount >= 3 && verticalStrength > maxVerticalStrength) {
                maxVerticalStrength = verticalStrength
                bestEdge = x
            }
            
            // Stop if we found a strong edge and moved far enough from center
            if (bestEdge != null && (centerX - x) > width / 4) {
                break
            }
        }
        
        // If no strong vertical edge found, fallback to simple leftmost edge
        if (bestEdge == null) {
            for (x in 0 until width) {
                for (y in (centerY - searchRange).coerceAtLeast(0) 
                         until (centerY + searchRange).coerceAtMost(height)) {
                    if (edgeMap[y][x] > threshold) {
                        return x
                    }
                }
            }
        }
        
        return bestEdge
    }
    
    /**
     * Find rightmost strong vertical edge representing the trunk.
     * Focuses on vertical edges near the bounding box center to avoid background.
     */
    private fun findRightmostEdge(
        edgeMap: Array<IntArray>, 
        centerY: Int, 
        width: Int, 
        height: Int
    ): Int? {
        if (centerY < 0 || centerY >= height) return null
        
        val threshold = 60 // Edge strength threshold (increased for better trunk detection)
        val searchRange = 8 // Search in ±8 pixels around centerY for vertical continuity
        val centerX = width / 2 // Use center of bounding box as reference
        
        // First, find strong vertical edges (trunk edges are typically vertical)
        // Search from center towards right to find the trunk's right edge
        var bestEdge: Int? = null
        var maxVerticalStrength = 0
        
        // Start searching from center, moving right
        for (x in centerX until width) {
            var verticalStrength = 0
            var edgeCount = 0
            
            // Check vertical continuity - trunk edges should be strong across multiple rows
            for (y in (centerY - searchRange).coerceAtLeast(0) 
                     until (centerY + searchRange).coerceAtMost(height)) {
                if (edgeMap[y][x] > threshold) {
                    verticalStrength += edgeMap[y][x]
                    edgeCount++
                }
            }
            
            // Require at least 3 strong edge points for vertical continuity
            if (edgeCount >= 3 && verticalStrength > maxVerticalStrength) {
                maxVerticalStrength = verticalStrength
                bestEdge = x
            }
            
            // Stop if we found a strong edge and moved far enough from center
            if (bestEdge != null && (x - centerX) > width / 4) {
                break
            }
        }
        
        // If no strong vertical edge found, fallback to simple rightmost edge
        if (bestEdge == null) {
            for (x in width - 1 downTo 0) {
                for (y in (centerY - searchRange).coerceAtLeast(0) 
                         until (centerY + searchRange).coerceAtMost(height)) {
                    if (edgeMap[y][x] > threshold) {
                        return x
                    }
                }
            }
        }
        
        return bestEdge
    }
}

