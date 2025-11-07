package com.bakhawone.thesis_bakhawone

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import android.graphics.Paint as AndroidPaint
import android.graphics.Typeface

/**
 * OverlayView — Jetpack Compose overlay for showing bounding boxes and labels
 * for objects detected by TrunkDetection.
 *
 * Displays only Rhizophora detections with status and DBH information to help users
 * avoid duplicate entries, and renders persistent markers for saved trunks.
 */
data class OverlayMarker(
    val screenX: Float,
    val screenY: Float,
    val isAlive: Boolean,
    val dbhCm: Float?
)

@Composable
fun OverlayView(
    detections: List<Detection>,
    savedMarkers: List<OverlayMarker>,
    imageWidth: Int,
    imageHeight: Int,
    modifier: Modifier = Modifier
) {
    val rhizophoraDetections = detections.filter { detection ->
        val isRhizo = detection.isRhizophora || detection.label.contains("Rhizophora", ignoreCase = true)
        val insideBoundary = detection.isInBoundary == true
        isRhizo && insideBoundary
    }

    if (rhizophoraDetections.isEmpty() && savedMarkers.isEmpty()) return

    Canvas(modifier = modifier.fillMaxSize()) {
        val scaleX = size.width / imageWidth
        val scaleY = size.height / imageHeight

        drawIntoCanvas { canvas ->
            val backgroundPaint = AndroidPaint().apply {
                color = android.graphics.Color.argb(200, 0, 0, 0)
                style = AndroidPaint.Style.FILL
                isAntiAlias = true
            }

            val statusTextPaint = AndroidPaint().apply {
                color = android.graphics.Color.WHITE
                textSize = 42f
                style = AndroidPaint.Style.FILL
                isAntiAlias = true
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }

            val dbhTextPaint = AndroidPaint().apply {
                color = android.graphics.Color.WHITE
                textSize = 36f
                style = AndroidPaint.Style.FILL
                isAntiAlias = true
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            }

            rhizophoraDetections.forEach { detection ->
                val box = detection.box
                val left = box.left * scaleX
                val top = box.top * scaleY
                val right = box.right * scaleX
                val bottom = box.bottom * scaleY

                // Determine alive/dead status: prefer enriched data, fallback to label parsing
                // Label format from TrunkDetection: "Alive Rhizophora (85.3%)" or "Dead Rhizophora (90.1%)"
                val isAlive = detection.isAlive
                    ?: detection.label.contains("Alive", ignoreCase = true)
                val isDead = detection.label.contains("Dead", ignoreCase = true) && !isAlive

                val boxColor = when {
                    isAlive -> Color(0xFF4CAF50) // Green for alive
                    isDead -> Color(0xFFF44336)  // Red for dead
                    else -> Color(0xFF4CAF50)    // Default to green (shouldn't happen for Rhizophora)
                }

                drawRect(
                    color = boxColor,
                    topLeft = Offset(left, top),
                    size = Size(right - left, bottom - top),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 5f)
                )

                val statusText = if (isAlive) "Alive Rhizophora" else "Dead Rhizophora"
                val dbhText = if (isAlive && detection.dbhCm != null) {
                    String.format("DBH: %.1f cm", detection.dbhCm)
                } else null

                val statusWidth = statusTextPaint.measureText(statusText)
                val dbhWidth = dbhText?.let { dbhTextPaint.measureText(it) } ?: 0f
                val maxWidth = maxOf(statusWidth, dbhWidth)
                val padding = 12f
                val labelHeight = if (dbhText != null) 90f else 50f
                val labelTop = (top - labelHeight - 10f).coerceAtLeast(0f)
                val labelLeft = (left - padding).coerceAtLeast(0f)

                drawRect(
                    color = Color(0xCC000000),
                    topLeft = Offset(labelLeft, labelTop),
                    size = Size(maxWidth + padding * 2, labelHeight)
                )

                canvas.nativeCanvas.drawText(
                    statusText,
                    labelLeft + padding,
                    labelTop + if (dbhText != null) 38f else 45f,
                    statusTextPaint
                )

                if (dbhText != null) {
                    canvas.nativeCanvas.drawText(
                        dbhText,
                        labelLeft + padding,
                        labelTop + 78f,
                        dbhTextPaint
                    )
                }
            }

            // Draw persistent markers for saved detections
            savedMarkers.forEach { marker ->
                val color = if (marker.isAlive) Color(0xFF4CAF50) else Color(0xFFF44336)
                val center = Offset(marker.screenX, marker.screenY)

                drawCircle(
                    color = color.copy(alpha = 0.85f),
                    radius = 16f,
                    center = center
                )
                drawCircle(
                    color = Color.White.copy(alpha = 0.9f),
                    radius = 6f,
                    center = center
                )

                val statusText = if (marker.isAlive) "Alive Rhizophora" else "Dead Rhizophora"
                val dbhText = marker.dbhCm?.let { String.format("DBH: %.1f cm", it) }
                val padding = 12f
                val labelHeight = if (dbhText != null) 80f else 48f
                val labelTop = (marker.screenY - labelHeight - 20f).coerceAtLeast(0f)
                val labelLeft = (marker.screenX - 80f)
                val textWidth = statusTextPaint.measureText(statusText)
                val dbhWidth = dbhText?.let { dbhTextPaint.measureText(it) } ?: 0f
                val contentWidth = maxOf(textWidth, dbhWidth) + padding * 2

                drawRect(
                    color = Color(0xCC000000),
                    topLeft = Offset(labelLeft, labelTop),
                    size = Size(contentWidth, labelHeight)
                )

                canvas.nativeCanvas.drawText(
                    statusText,
                    labelLeft + padding,
                    labelTop + if (dbhText != null) 34f else 38f,
                    statusTextPaint
                )

                if (dbhText != null) {
                    canvas.nativeCanvas.drawText(
                        dbhText,
                        labelLeft + padding,
                        labelTop + 68f,
                        dbhTextPaint
                    )
                }
            }
        }
    }
}
