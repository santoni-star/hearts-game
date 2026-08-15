package com.example.heartsgame.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

/**
 * Felt table background with subtle texture and player position markers
 */
class TableBackgroundView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val feltPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1A6B1A")
        strokeWidth = 1f
        style = Paint.Style.STROKE
    }
    private val positionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#33FFFFFF")
        textSize = 14f
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }
    private val centerCirclePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1AFFFFFF")
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    private var noiseBitmap: Bitmap? = null

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        generateNoiseTexture(w, h)
    }

    private fun generateNoiseTexture(w: Int, h: Int) {
        // Create a subtle noise texture for felt effect
        noiseBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(noiseBitmap!!)
        val noisePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        
        // Base felt color
        canvas.drawColor(Color.parseColor("#1B5E20"))
        
        // Add subtle noise
        val random = java.util.Random(42) // Fixed seed for consistency
        for (i in 0 until (w * h / 50)) { // Sparse noise
            val x = random.nextInt(w)
            val y = random.nextInt(h)
            val alpha = random.nextInt(15) + 5 // 5-20 alpha
            noisePaint.color = Color.argb(alpha, 255, 255, 255)
            canvas.drawPoint(x.toFloat(), y.toFloat(), noisePaint)
        }
        
        // Add some darker noise
        for (i in 0 until (w * h / 80)) {
            val x = random.nextInt(w)
            val y = random.nextInt(h)
            val alpha = random.nextInt(10) + 5
            noisePaint.color = Color.argb(alpha, 0, 0, 0)
            canvas.drawPoint(x.toFloat(), y.toFloat(), noisePaint)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val w = width.toFloat()
        val h = height.toFloat()
        val centerX = w / 2f
        val centerY = h / 2f

        // Draw noise texture
        noiseBitmap?.let { bitmap ->
            canvas.drawBitmap(bitmap, 0f, 0f, feltPaint)
        } ?: run {
            // Fallback solid color
            canvas.drawColor(Color.parseColor("#1B5E20"))
        }

        // Center circle (play area indicator)
        val circleRadius = minOf(w, h) * 0.18f
        canvas.drawCircle(centerX, centerY, circleRadius, centerCirclePaint)
        
        // Inner circle
        centerCirclePaint.strokeWidth = 1f
        centerCirclePaint.color = Color.parseColor("#10FFFFFF")
        canvas.drawCircle(centerX, centerY, circleRadius * 0.7f, centerCirclePaint)

        // Player position labels (subtle)
        val labelRadius = minOf(w, h) * 0.35f
        
        // North (top)
        canvas.drawText("NORTH", centerX, centerY - labelRadius + 20f, positionPaint)
        // South (bottom)
        canvas.drawText("SOUTH (YOU)", centerX, centerY + labelRadius + 30f, positionPaint)
        // West (left)
        canvas.save()
        canvas.rotate(-90f, centerX, centerY)
        canvas.drawText("WEST", centerX - labelRadius + 20f, centerY, positionPaint)
        canvas.restore()
        // East (right)
        canvas.save()
        canvas.rotate(90f, centerX, centerY)
        canvas.drawText("EAST", centerX + labelRadius - 20f, centerY, positionPaint)
        canvas.restore()

        // Subtle quadrant lines
        linePaint.color = Color.parseColor("#1A6B1A")
        linePaint.strokeWidth = 1f
        
        // Cross lines through center
        canvas.drawLine(0f, centerY, w, centerY, linePaint)
        canvas.drawLine(centerX, 0f, centerX, h, linePaint)
    }
}