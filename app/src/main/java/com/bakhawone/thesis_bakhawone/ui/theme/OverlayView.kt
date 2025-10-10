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

    // Color mapping for different labels
    private var aliveRhizophoraColor: Int = Color.GREEN
    private var aliveTrunkColor: Int = Color.GREEN
    private var deadRhizophoraColor: Int = Color.RED
    private var deadTrunkColor: Int = Color.RED

    private val boxPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        isAntiAlias = true
    }

    private val fillPaint = Paint().apply {
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val textBgPaint = Paint().apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#CC000000") // Semi-transparent black background
        isAntiAlias = true
    }

    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 36f
        typeface = Typeface.DEFAULT_BOLD
        isAntiAlias = true
    }

    fun setLabelColors(
        aliveRhizophoraColor: Int = Color.GREEN,
        aliveTrunkColor: Int = Color.GREEN,
        deadRhizophoraColor: Int = Color.RED,
        deadTrunkColor: Int = Color.RED
    ) {
        this.aliveRhizophoraColor = aliveRhizophoraColor
        this.aliveTrunkColor = aliveTrunkColor
        this.deadRhizophoraColor = deadRhizophoraColor
        this.deadTrunkColor = deadTrunkColor
    }

    fun setResults(
        boxes: List<RectF>,
        labels: List<String>,
        imgWidth: Int,
        imgHeight: Int
    ) {
        this.boxes = boxes
        this.labels = labels
        this.imageWidth = imgWidth
        this.imageHeight = imgHeight
        invalidate() // Request redraw
    }

    fun clear() {
        boxes = emptyList()
        labels = emptyList()
        invalidate()
    }

    private fun getColorForLabel(label: String): Int {
        return when {
            label.contains("Alive Rhizophora", ignoreCase = true) -> aliveRhizophoraColor
            label.contains("Alive Trunk", ignoreCase = true) -> aliveTrunkColor
            label.contains("Dead Rhizophora", ignoreCase = true) -> deadRhizophoraColor
            label.contains("Dead Trunk", ignoreCase = true) -> deadTrunkColor
            else -> Color.YELLOW // Default color for unknown labels
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (boxes.isEmpty()) return

        // Calculate scaling factors
        val scaleX = width.toFloat() / imageWidth
        val scaleY = height.toFloat() / imageHeight

        for (i in boxes.indices) {
            if (i >= labels.size) break

            val box = boxes[i]
            val label = labels[i]

            // Get color based on label
            val color = getColorForLabel(label)
            boxPaint.color = color
            fillPaint.color = Color.argb(30, Color.red(color), Color.green(color), Color.blue(color))

            // Scale coordinates to view size
            val left = box.left * scaleX
            val top = box.top * scaleY
            val right = box.right * scaleX
            val bottom = box.bottom * scaleY

            // Only draw if box is within visible bounds
            if (right > 0 && left < width && bottom > 0 && top < height) {
                val scaledBox = RectF(left, top, right, bottom)

                // Draw semi-transparent fill
                canvas.drawRect(scaledBox, fillPaint)

                // Draw bounding box
                canvas.drawRect(scaledBox, boxPaint)

                // Draw label with background for better readability
                drawLabel(canvas, label, scaledBox)
            }
        }
    }

    private fun drawLabel(canvas: Canvas, label: String, box: RectF) {
        val textWidth = textPaint.measureText(label)
        val textHeight = textPaint.descent() - textPaint.ascent()

        // Calculate text position (above the box, but ensure it doesn't go off screen)
        val textLeft = box.left.coerceAtLeast(0f)
        val textTop = (box.top - 10f).coerceAtLeast(textHeight + 5f)

        // Draw text background with rounded corners
        val backgroundRect = RectF(
            textLeft - 5f,
            textTop - textHeight - 10f,
            textLeft + textWidth + 15f,
            textTop
        )

        // Draw rounded rectangle background
        canvas.drawRoundRect(backgroundRect, 8f, 8f, textBgPaint)

        // Draw text
        canvas.drawText(label, textLeft + 5f, textTop - 15f, textPaint)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // Adjust text size based on screen size for better readability
        textPaint.textSize = (w * 0.03f).coerceAtLeast(24f).coerceAtMost(48f)
    }
}