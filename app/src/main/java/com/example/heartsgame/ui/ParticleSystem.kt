package com.example.heartsgame.ui

import android.graphics.*
import android.view.View
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.cos
import kotlin.math.sin

class ParticleSystem(private val view: View) {

    private val particles = CopyOnWriteArrayList<Particle>()
    private val random = java.util.Random()
    private var running = false
    private var animationCallback: (() -> Unit)? = null

    data class Particle(
        var x: Float,
        var y: Float,
        var vx: Float,
        var vy: Float,
        var life: Float,
        var maxLife: Float,
        var size: Float,
        var color: Int,
        var type: ParticleType,
        var rotation: Float = 0f,
        var rotationSpeed: Float = 0f
    ) {
        var alpha: Float = 1f
            get() = life / maxLife
    }

    enum class ParticleType {
        HEART, SPARKLE, STAR, MOON, CONFETTI, QUEEN_SPADES
    }

    fun start() {
        running = true
        animate()
    }

    fun stop() {
        running = false
        particles.clear()
    }

    fun burstHearts(centerX: Float, centerY: Float, count: Int = 8) {
        repeat(count) {
            val angle = random.nextFloat() * 360f
            val speed = 50f + random.nextFloat() * 100f
            val rad = Math.toRadians(angle.toDouble()).toFloat()
            val vx = cos(rad) * speed
            val vy = -sin(rad) * speed - 50f
            
            particles.add(Particle(
                x = centerX,
                y = centerY,
                vx = vx,
                vy = vy,
                life = 1.5f + random.nextFloat() * 1f,
                maxLife = 1.5f + random.nextFloat() * 1f,
                size = 16f + random.nextFloat() * 12f,
                color = Color.parseColor("#CC0000"),
                type = ParticleType.HEART,
                rotation = random.nextFloat() * 360f,
                rotationSpeed = (random.nextFloat() - 0.5f) * 180f
            ))
        }
        if (!running) start()
    }

    fun burstQueenSpades(centerX: Float, centerY: Float) {
        repeat(12) {
            val angle = random.nextFloat() * 360f
            val speed = 80f + random.nextFloat() * 120f
            val rad = Math.toRadians(angle.toDouble()).toFloat()
            val vx = cos(rad) * speed
            val vy = -sin(rad) * speed - 30f
            
            particles.add(Particle(
                x = centerX,
                y = centerY,
                vx = vx,
                vy = vy,
                life = 1f + random.nextFloat() * 0.5f,
                maxLife = 1f + random.nextFloat() * 0.5f,
                size = 20f + random.nextFloat() * 10f,
                color = Color.parseColor("#FFD700"),
                type = ParticleType.QUEEN_SPADES,
                rotation = random.nextFloat() * 360f,
                rotationSpeed = (random.nextFloat() - 0.5f) * 360f
            ))
        }
        if (!running) start()
    }

    fun celebrateMoonShoot(winnerX: Float, winnerY: Float, screenWidth: Int, screenHeight: Int) {
        repeat(30) {
            val angle = random.nextFloat() * 360f
            val speed = 100f + random.nextFloat() * 200f
            val rad = Math.toRadians(angle.toDouble()).toFloat()
            val vx = cos(rad) * speed
            val vy = -sin(rad) * speed - 100f
            
            val colors = arrayOf(
                Color.parseColor("#FFD700"),
                Color.parseColor("#FFA500"),
                Color.parseColor("#FF6B6B"),
                Color.parseColor("#4ECDC4"),
                Color.parseColor("#FF6B35")
            )
            
            particles.add(Particle(
                x = winnerX,
                y = winnerY,
                vx = vx,
                vy = vy,
                life = 2.5f + random.nextFloat() * 1.5f,
                maxLife = 2.5f + random.nextFloat() * 1.5f,
                size = 24f + random.nextFloat() * 16f,
                color = colors[random.nextInt(colors.size)],
                type = when (random.nextInt(4)) {
                    0 -> ParticleType.STAR
                    1 -> ParticleType.MOON
                    2 -> ParticleType.SPARKLE
                    else -> ParticleType.CONFETTI
                },
                rotation = random.nextFloat() * 360f,
                rotationSpeed = (random.nextFloat() - 0.5f) * 720f
            ))
        }
        
        repeat(50) {
            val x = random.nextFloat() * screenWidth
            val y = -50f - random.nextFloat() * 100f
            val vx = (random.nextFloat() - 0.5f) * 100f
            val vy = 150f + random.nextFloat() * 200f
            
            particles.add(Particle(
                x = x,
                y = y,
                vx = vx,
                vy = vy,
                life = 3f + random.nextFloat() * 2f,
                maxLife = 3f + random.nextFloat() * 2f,
                size = 12f + random.nextFloat() * 8f,
                color = arrayOf(
                    Color.parseColor("#FFD700"),
                    Color.parseColor("#FF6B6B"),
                    Color.parseColor("#4ECDC4"),
                    Color.parseColor("#FFA500"),
                    Color.parseColor("#FFFFFF")
                )[random.nextInt(5)],
                type = ParticleType.CONFETTI,
                rotation = random.nextFloat() * 360f,
                rotationSpeed = (random.nextFloat() - 0.5f) * 360f
            ))
        }
        
        if (!running) start()
    }

    fun trickCollected(fromX: Float, fromY: Float, toX: Float, toY: Float, cardCount: Int) {
        repeat(cardCount * 2) {
            val progress = random.nextFloat()
            val x = fromX + (toX - fromX) * progress + (random.nextFloat() - 0.5f) * 60f
            val y = fromY + (toY - fromY) * progress + (random.nextFloat() - 0.5f) * 60f
            
            particles.add(Particle(
                x = x,
                y = y,
                vx = (random.nextFloat() - 0.5f) * 80f,
                vy = (random.nextFloat() - 0.5f) * 80f - 50f,
                life = 0.8f + random.nextFloat() * 0.4f,
                maxLife = 0.8f + random.nextFloat() * 0.4f,
                size = 10f + random.nextFloat() * 6f,
                color = Color.parseColor("#FFD700"),
                type = ParticleType.SPARKLE,
                rotation = random.nextFloat() * 360f,
                rotationSpeed = (random.nextFloat() - 0.5f) * 540f
            ))
        }
        if (!running) start()
    }

    fun passCards(fromX: Float, fromY: Float, toX: Float, toY: Float, count: Int = 3) {
        repeat(count) {
            val delay = it * 0.15f
            particles.add(Particle(
                x = fromX,
                y = fromY,
                vx = (toX - fromX) / 0.8f + (random.nextFloat() - 0.5f) * 40f,
                vy = (toY - fromY) / 0.8f + (random.nextFloat() - 0.5f) * 40f - 30f,
                life = 0.8f + delay,
                maxLife = 0.8f + delay,
                size = 16f,
                color = Color.parseColor("#FFD700"),
                type = ParticleType.SPARKLE,
                rotation = random.nextFloat() * 360f,
                rotationSpeed = (random.nextFloat() - 0.5f) * 360f
            ))
        }
        if (!running) start()
    }

    private fun animate() {
        if (!running) return
        
        val dt = 0.016f
        
        val toRemove = mutableListOf<Particle>()
        particles.forEach { p ->
            p.x += p.vx * dt
            p.y += p.vy * dt
            p.vy += 200f * dt
            p.life -= dt
            p.rotation += p.rotationSpeed * dt
            
            p.vx *= 0.99f
            p.vy *= 0.99f
            
            if (p.life <= 0f) {
                toRemove.add(p)
            }
        }
        particles.removeAll(toRemove)
        
        view.postInvalidate()
        
        if (particles.isNotEmpty()) {
            view.postDelayed({ animate() }, 16)
        } else {
            running = false
            animationCallback?.invoke()
        }
    }

    fun draw(canvas: Canvas) {
        particles.forEach { p ->
            drawParticle(canvas, p)
        }
    }

    private fun drawParticle(canvas: Canvas, p: Particle) {
        canvas.save()
        canvas.translate(p.x, p.y)
        canvas.rotate(p.rotation)
        
        val alpha = (255 * p.alpha).toInt()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(alpha, Color.red(p.color), Color.green(p.color), Color.blue(p.color))
            textSize = p.size
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        
        when (p.type) {
            ParticleType.HEART -> canvas.drawText("♥", 0f, p.size / 3f, paint)
            ParticleType.SPARKLE -> {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 2f
                val s = p.size / 2f
                canvas.drawLine(-s, 0f, s, 0f, paint)
                canvas.drawLine(0f, -s, 0f, s, paint)
                canvas.drawLine(-s * 0.7f, -s * 0.7f, s * 0.7f, s * 0.7f, paint)
                canvas.drawLine(-s * 0.7f, s * 0.7f, s * 0.7f, -s * 0.7f, paint)
            }
            ParticleType.STAR -> canvas.drawText("★", 0f, p.size / 3f, paint)
            ParticleType.MOON -> canvas.drawText("☽", 0f, p.size / 3f, paint)
            ParticleType.CONFETTI -> {
                paint.style = Paint.Style.FILL
                val half = p.size / 2f
                canvas.drawRect(-half, -half * 0.4f, half, half * 0.4f, paint)
            }
            ParticleType.QUEEN_SPADES -> canvas.drawText("♠", 0f, p.size / 3f, paint)
        }
        
        canvas.restore()
    }

    fun setOnComplete(callback: () -> Unit) {
        animationCallback = callback
    }

    fun isRunning(): Boolean = running
}