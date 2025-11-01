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
import androidx.compose.ui.unit.sp
import android.graphics.Paint as AndroidPaint

/**
 * OverlayView — Jetpack Compose overlay for showing bounding boxes and labels
 * for objects detected by TrunkDetection.
 *
 * Requires: Detection class from TrunkDetection.kt
 */
@Composable
fun OverlayView(
    detections: List<Detection>,
    imageWidth: Int,
    imageHeight: Int,
    modifier: Modifier = Modifier
) {
    if (detections.isEmpty()) return

    Canvas(modifier = modifier.fillMaxSize()) {
        val scaleX = size.width / imageWidth
        val scaleY = size.height / imageHeight

        drawIntoCanvas { canvas ->
            val textPaint = AndroidPaint().apply {
                color = android.graphics.Color.WHITE
                textSize = 36f
                style = AndroidPaint.Style.FILL
                isAntiAlias = true
            }

            detections.forEach { detection ->
                val box = detection.box
                val left = box.left * scaleX
                val top = box.top * scaleY
                val right = box.right * scaleX
                val bottom = box.bottom * scaleY

                val isRhizo = detection.label.contains("Rhizophora", ignoreCase = true)
                val isDead = detection.label.contains("Dead", ignoreCase = true)
                val isAlive = detection.label.contains("Alive", ignoreCase = true)
                
                // Color coding: Green for Alive Rhizophora, Red for Dead Rhizophora, Yellow for Non-Rhizophora
                val color = when {
                    isRhizo && isAlive -> Color(0xFF4CAF50) // 🟩 Green
                    isRhizo && isDead -> Color(0xFFF44336)  // 🟥 Red
                    else -> Color(0xFFFFEB3B) // 🟨 Yellow (Non-Rhizophora)
                }

                // Draw bounding box
                drawRect(
                    color = color,
                    topLeft = Offset(left, top),
                    size = Size(right - left, bottom - top),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f)
                )

                // Draw label text above the box
                // Format: "Alive Rhizophora (DBH: 12.5 cm)" or "Dead Rhizophora" or show Non-Rhizophora with ⚠
                val labelText = when {
                    isRhizo && isAlive -> {
                        val dbhText = detection.dbhCm?.let { String.format("Alive Rhizophora (DBH: %.1f cm)", it) }
                            ?: "Alive Rhizophora"
                        dbhText
                    }
                    isRhizo && isDead -> "Dead Rhizophora"
                    else -> "⚠ ${detection.label}" // Non-Rhizophora with warning indicator
                }
                canvas.nativeCanvas.drawText(
                    labelText,
                    left,
                    top - 10,
                    textPaint
                )
            }
        }
    }
}
