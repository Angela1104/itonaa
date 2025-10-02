package com.bakhawone.thesis_bakhawone

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

class OverlayView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    private var boxes: List<RectF> = emptyList()
    private var labels: List<String> = emptyList()
    private var imageWidth: Int = 1
    private var imageHeight: Int = 1
    private var displayRect: RectF? = null

    // Different colors for better visual distinction
    private val colors = listOf(
        Color.RED,
        Color.GREEN,
        Color.BLUE,
        Color.YELLOW
    )

    private val boxPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    private val textBgPaint = Paint().apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#CC000000") // Semi-transparent black background
    }

    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 36f   // ✅ updated from 40f to 36f
        typeface = Typeface.DEFAULT_BOLD
        isAntiAlias = true
    }

    fun setResults(
        boxes: List<RectF>,
        labels: List<String>,
        imgWidth: Int,
        imgHeight: Int,
        displayRect: RectF?
    ) {
        this.boxes = boxes
        this.labels = labels
        this.imageWidth = imgWidth
        this.imageHeight = imgHeight
        this.displayRect = displayRect
        invalidate() // Request redraw
    }

    fun clear() {
        boxes = emptyList()
        labels = emptyList()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (boxes.isEmpty()) return

        // Simplified scaling - use full view size
        val scaleX = width.toFloat() / imageWidth
        val scaleY = height.toFloat() / imageHeight
        val dx = 0f
        val dy = 0f

        for (i in boxes.indices) {
            val box = boxes[i]
            val label = labels.getOrNull(i) ?: "Unknown"

            // Use different color for each detection
            boxPaint.color = colors[i % colors.size]

            // Scale coordinates to view size
            val left = box.left * scaleX + dx
            val top = box.top * scaleY + dy
            val right = box.right * scaleX + dx
            val bottom = box.bottom * scaleY + dy

            // Only draw if box is within visible bounds
            if (right > 0 && left < width && bottom > 0 && top < height) {
                // Draw bounding box
                canvas.drawRect(left, top, right, bottom, boxPaint)

                // Draw label with background for better readability
                val textWidth = textPaint.measureText(label)
                val textHeight = textPaint.descent() - textPaint.ascent()

                // Ensure text doesn't go off screen
                val textLeft = left.coerceAtLeast(0f)
                val textTop = (top - 10f).coerceAtLeast(textHeight + 5f)

                // Draw text background
                canvas.drawRect(
                    textLeft,
                    textTop - textHeight - 5f,
                    textLeft + textWidth + 20f,
                    textTop,
                    textBgPaint
                )

                // Draw text
                canvas.drawText(label, textLeft + 10f, textTop - 10f, textPaint)
            }
        }
    }
}
