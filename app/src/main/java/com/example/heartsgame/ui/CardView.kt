package com.example.heartsgame.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import com.example.heartsgame.game.Card
import com.example.heartsgame.game.Suit

class CardView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var card: Card? = null
    var isFaceUp: Boolean = true
    var cardSelected: Boolean = false
    var isPlayable: Boolean = false
    var canBeat: Boolean = false
    var isInTrick: Boolean = false
    var trickOrder: Int = 0
    var maxCardWidth: Int = 0

    // Animation properties
    var animScaleX: Float = 1f
    var animScaleY: Float = 1f
    var animRotation: Float = 0f
    var animAlpha: Float = 1f
    var animTranslationX: Float = 0f
    var animTranslationY: Float = 0f

    private var cardWidth: Int = 0
    private var cardHeight: Int = 0
    private val cornerRadius = 12f

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
        color = Color.parseColor("#CCCCCC")
    }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#33000000")
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 28f
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.LEFT
    }
    private val smallTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 18f
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.LEFT
    }
    private val bigSuitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 48f
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }
    private val selectedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#CC4CAF50")
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val playableGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val backPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val backPatternPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#223333AA")
        strokeWidth = 1.5f
        style = Paint.Style.STROKE
    }
    private val backCenterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#885555DD")
        textSize = 28f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }

    private var cardGradient: LinearGradient? = null

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val maxW = (90 * resources.displayMetrics.density).toInt()
        val finalW = if (w > maxW) maxW else w
        val h = (finalW * 160f / 110f).toInt()
        setMeasuredDimension(finalW, h)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        cardWidth = w
        cardHeight = h
        cardGradient = LinearGradient(
            0f, 0f, 0f, h.toFloat(),
            intArrayOf(
                Color.parseColor("#FFFFFF"),
                Color.parseColor("#F0F0F0"),
                Color.parseColor("#E8E8E8"),
                Color.parseColor("#FFFFFF")
            ),
            floatArrayOf(0f, 0.3f, 0.7f, 1f),
            Shader.TileMode.CLAMP
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val w = width.toFloat()
        val h = height.toFloat()
        val r = cornerRadius

        canvas.save()
        canvas.translate(animTranslationX, animTranslationY)
        canvas.scale(animScaleX, animScaleY, w / 2f, h / 2f)
        canvas.rotate(animRotation, w / 2f, h / 2f)
        canvas.clipRect(-w, -h, w * 2, h * 2)

        if (isFaceUp && !isInTrick && animAlpha > 0.5f) {
            val shadowOffset = (4f * resources.displayMetrics.density)
            shadowPaint.alpha = (50 * animAlpha).toInt()
            canvas.drawRoundRect(
                shadowOffset, shadowOffset, w - shadowOffset, h - shadowOffset,
                r, r, shadowPaint
            )
        }

        if (isPlayable && !cardSelected) {
            playableGlowPaint.color = Color.parseColor("#3300AA00")
            canvas.drawRoundRect(2f, 2f, w - 2f, h - 2f, r, r, playableGlowPaint)
        } else if (canBeat) {
            playableGlowPaint.color = Color.parseColor("#4400CC00")
            canvas.drawRoundRect(2f, 2f, w - 2f, h - 2f, r, r, playableGlowPaint)
        }

        if (isFaceUp && card != null) {
            drawCardFace(canvas, card!!, w, h, r)
        } else {
            drawCardBack(canvas, w, h, r)
        }

        if (cardSelected) {
            selectedPaint.alpha = (204 * animAlpha).toInt()
            canvas.drawRoundRect(2f, 2f, w - 2f, h - 2f, r, r, selectedPaint)
        }

        if (isInTrick && trickOrder > 0) {
            drawTrickOrderBadge(canvas, w, h, r)
        }

        canvas.restore()
    }

    private fun drawCardFace(canvas: Canvas, c: Card, w: Float, h: Float, r: Float) {
        bgPaint.shader = cardGradient
        canvas.drawRoundRect(1f, 1f, w - 1f, h - 1f, r, r, bgPaint)
        bgPaint.shader = null

        borderPaint.alpha = 200
        canvas.drawRoundRect(1f, 1f, w - 1f, h - 1f, r, r, borderPaint)

        val isRed = c.suit == Suit.HEARTS || c.suit == Suit.DIAMONDS
        val textColor = if (isRed) Color.parseColor("#CC0000") else Color.parseColor("#000000")
        
        textPaint.color = textColor
        smallTextPaint.color = textColor
        bigSuitPaint.color = textColor

        val rankStr = c.rank.display
        val suitSym = c.suit.symbol

        val margin = 8f
        canvas.drawText(rankStr, margin, 24f, smallTextPaint)
        canvas.drawText(suitSym, margin, 42f, smallTextPaint)

        canvas.save()
        canvas.rotate(180f, w / 2f, h / 2f)
        canvas.drawText(rankStr, margin, 24f, smallTextPaint)
        canvas.drawText(suitSym, margin, 42f, smallTextPaint)
        canvas.restore()

        canvas.drawText(suitSym, w / 2f, h / 2f + 16f, bigSuitPaint)

        if (c.suit == Suit.SPADES && c.rank == Rank.QUEEN) {
            val crownPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#FFD700")
                textSize = 16f
                textAlign = Paint.Align.CENTER
                typeface = Typeface.DEFAULT_BOLD
            }
            canvas.drawText("♛", w / 2f, h * 0.75f, crownPaint)
        }
    }

    private fun drawCardBack(canvas: Canvas, w: Float, h: Float, r: Float) {
        val backGradient = LinearGradient(
            0f, 0f, 0f, h,
            intArrayOf(
                Color.parseColor("#191970"),
                Color.parseColor("#2A2A8A"),
                Color.parseColor("#1A1A6A"),
                Color.parseColor("#0D0D4D")
            ),
            floatArrayOf(0f, 0.3f, 0.7f, 1f),
            Shader.TileMode.CLAMP
        )
        backPaint.shader = backGradient
        canvas.drawRoundRect(1f, 1f, w - 1f, h - 1f, r, r, backPaint)
        backPaint.shader = null

        backPaint.color = Color.parseColor("#4444CC")
        backPaint.style = Paint.Style.STROKE
        backPaint.strokeWidth = 2f
        canvas.drawRoundRect(3f, 3f, w - 3f, h - 3f, r, r, backPaint)

        backPatternPaint.color = Color.parseColor("#223333AA")
        backPatternPaint.strokeWidth = 1.5f
        val step = 16f
        for (i in -h.toInt() until w.toInt() step step.toInt()) {
            canvas.drawLine(i.toFloat(), 0f, (i - h).toFloat(), h, backPatternPaint)
            canvas.drawLine(i.toFloat(), 0f, (i + h).toFloat(), h, backPatternPaint)
        }

        backPaint.color = Color.parseColor("#445555DD")
        backPaint.style = Paint.Style.STROKE
        backPaint.strokeWidth = 2f
        canvas.drawOval(w * 0.2f, h * 0.25f, w * 0.8f, h * 0.75f, backPaint)

        backCenterPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("♠♥", w / 2f, h / 2f + 10f, backCenterPaint)
    }

    private fun drawTrickOrderBadge(canvas: Canvas, w: Float, h: Float, r: Float) {
        val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FFD700")
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
        val bgBadgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#CC000000")
        }
        
        val badgeSize = 20f
        val x = w - badgeSize - 4f
        val y = 4f
        
        canvas.drawCircle(x + badgeSize / 2f, y + badgeSize / 2f, badgeSize / 2f, bgBadgePaint)
        canvas.drawText(trickOrder.toString(), x + badgeSize / 2f, y + badgeSize / 2f + 5f, badgePaint)
    }

    fun resetAnimation() {
        animScaleX = 1f
        animScaleY = 1f
        animRotation = 0f
        animAlpha = 1f
        animTranslationX = 0f
        animTranslationY = 0f
    }

    fun setCard(card: Card?, faceUp: Boolean = true) {
        this.card = card
        this.isFaceUp = faceUp
        resetAnimation()
        postInvalidate()
    }
}