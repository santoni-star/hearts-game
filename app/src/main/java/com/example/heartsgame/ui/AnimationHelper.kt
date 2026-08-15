package com.example.heartsgame.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.LinearLayout
import com.example.heartsgame.game.Card
import com.example.heartsgame.game.PlayerPosition
import kotlin.math.cos
import kotlin.math.sin

/**
 * Animation utilities for Hearts game.
 * All animations run on UI thread via View.animate() or ObjectAnimator.
 */
object AnimationHelper {

    // Duration constants (tuned for Hearts feel)
    private const val DEAL_DURATION = 300L
    private const val PLAY_DURATION = 400L
    private const val PASS_DURATION = 500L
    private const val COLLECT_DURATION = 600L
    private const val FLIP_DURATION = 250L
    private const val SELECT_BOUNCE_DURATION = 150L
    private const val AI_PULSE_DURATION = 1000L
    private const val SCORE_ANIM_DURATION = 500L
    private const val MOON_CELEBRATION_DURATION = 3000L

    // Use function type instead of interface for simpler lambda usage
    typealias AnimCallback = () -> Unit

    // =========================================================================
    // DEAL ANIMATIONS
    // =========================================================================

    /** Deal a card from deck position to target view */
    fun dealCard(
        cardView: CardView,
        startX: Float, startY: Float,
        endX: Float, endY: Float,
        delay: Long = 0,
        callback: AnimCallback? = null
    ) {
        cardView.animTranslationX = startX - cardView.x
        cardView.animTranslationY = startY - cardView.y
        cardView.animScaleX = 0.3f
        cardView.animScaleY = 0.3f
        cardView.animAlpha = 0f

        cardView.animate()
            .translationX(0f)
            .translationY(0f)
            .scaleX(1f)
            .scaleY(1f)
            .alpha(1f)
            .setDuration(DEAL_DURATION)
            .setStartDelay(delay)
            .setInterpolator(OvershootInterpolator(1.2f))
            .withEndAction { callback?.onEnd() }
            .start()
    }

    /** Deal multiple cards staggered */
    fun dealCards(
        cardViews: List<CardView>,
        startX: Float, startY: Float,
        staggerDelay: Long = 60,
        callback: AnimCallback? = null
    ) {
        if (cardViews.isEmpty()) {
            callback?.onEnd()
            return
        }

        var completed = 0
        val total = cardViews.size

        fun checkComplete() {
            completed++
            if (completed == total) callback?.onEnd()
        }

        cardViews.forEachIndexed { index, cv ->
            val delay = index * staggerDelay
            cv.post {
                val targetX = cv.x
                val targetY = cv.y
                dealCard(cv, startX, startY, targetX, targetY, delay.toLong()) { checkComplete() }
            }
        }
    }

    // =========================================================================
    // PLAY CARD ANIMATIONS
    // =========================================================================

    /** Play card from hand to center play area */
    fun playCard(
        cardView: CardView,
        startX: Float, startY: Float,
        endX: Float, endY: Float,
        rotation: Float = 0f,
        callback: AnimCallback? = null
    ) {
        val startTx = cardView.animTranslationX
        val startTy = cardView.animTranslationY

        cardView.animate()
            .translationX(endX - cardView.x + startTx)
            .translationY(endY - cardView.y + startTy)
            .rotation(rotation)
            .scaleX(0.95f)
            .scaleY(0.95f)
            .setDuration(PLAY_DURATION)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .withEndAction {
                cardView.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(80)
                    .setInterpolator(DecelerateInterpolator())
                    .withEndAction { callback?.onEnd() }
                    .start()
            }
            .start()
    }

    /** Play card with trick order (position in trick) */
    fun playCardToTrick(
        cardView: CardView,
        trickIndex: Int,  // 0-3
        centerX: Float, centerY: Float,
        callback: AnimCallback? = null
    ) {
        // Calculate position based on trick index (N/E/S/W positions around center)
        val radius = 80f
        val angle = (trickIndex * 90f - 90f) * (Math.PI / 180.0).toFloat()
        val targetX = centerX + radius * cos(angle)
        val targetY = centerY + radius * sin(angle)
        val rotation = (trickIndex * 90f) % 360f

        playCard(cardView, cardView.x, cardView.y, targetX, targetY, rotation, callback)
    }

    // =========================================================================
    // PASS CARD ANIMATIONS
    // =========================================================================

    /** Pass card from player hand to target player */
    fun passCard(
        cardView: CardView,
        startX: Float, startY: Float,
        endX: Float, endY: Float,
        delay: Long = 0,
        flip: Boolean = true,
        callback: AnimCallback? = null
    ) {
        if (flip) {
            // Flip down, move, flip up
            cardView.animate()
                .scaleX(0f)
                .setDuration(FLIP_DURATION / 2)
                .setStartDelay(delay)
                .withEndAction {
                    cardView.isFaceUp = false
                    cardView.postInvalidate()
                    
                    // Move while face down
                    cardView.animTranslationX = startX - cardView.x
                    cardView.animTranslationY = startY - cardView.y
                    
                    cardView.animate()
                        .translationX(endX - cardView.x)
                        .translationY(endY - cardView.y)
                        .setDuration(PASS_DURATION)
                        .setInterpolator(AccelerateDecelerateInterpolator())
                        .withEndAction {
                            cardView.animate()
                                .scaleX(1f)
                                .setDuration(FLIP_DURATION / 2)
                                .withEndAction {
                                    cardView.isFaceUp = true
                                    cardView.postInvalidate()
                                    callback?.onEnd()
                                }
                                .start()
                        }
                        .start()
                }
                .start()
        } else {
            // Just move
            cardView.animTranslationX = startX - cardView.x
            cardView.animTranslationY = startY - cardView.y
            cardView.animate()
                .translationX(endX - cardView.x)
                .translationY(endY - cardView.y)
                .setDuration(PASS_DURATION)
                .setStartDelay(delay)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .withEndAction { callback?.onEnd() }
                .start()
        }
    }

    /** Pass multiple cards staggered */
    fun passCards(
        cardViews: List<CardView>,
        startX: Float, startY: Float,
        targetPositions: List<Pair<Float, Float>>,
        staggerDelay: Long = 150,
        callback: AnimCallback? = null
    ) {
        if (cardViews.isEmpty() || targetPositions.isEmpty()) {
            callback?.onEnd()
            return
        }

        var completed = 0
        val total = minOf(cardViews.size, targetPositions.size)

        fun checkComplete() {
            completed++
            if (completed == total) callback?.onEnd()
        }

        cardViews.forEachIndexed { index, cv ->
            if (index >= targetPositions.size) return@forEachIndexed
            val (endX, endY) = targetPositions[index]
            val delay = index * staggerDelay
            cv.post {
                passCard(cv, startX, startY, endX, endY, delay.toLong(), true) { checkComplete() }
            }
        }
    }

    // =========================================================================
    // TRICK COLLECTION ANIMATIONS
    // =========================================================================

    /** Collect trick cards to winner's position */
    fun collectTrick(
        cardViews: List<CardView>,
        winnerPosition: PlayerPosition,
        winnerView: View,  // The player's hand area or score area
        callback: AnimCallback? = null
    ) {
        if (cardViews.isEmpty()) {
            callback?.onEnd()
            return
        }

        val targetX = winnerView.x + winnerView.width / 2f
        val targetY = winnerView.y + winnerView.height / 2f

        var completed = 0
        val total = cardViews.size

        fun checkComplete() {
            completed++
            if (completed == total) callback?.onEnd()
        }

        cardViews.forEachIndexed { index, cv ->
            val delay = index * 80L
            cv.animate()
                .translationX(targetX - cv.x)
                .translationY(targetY - cv.y)
                .scaleX(0.3f)
                .scaleY(0.3f)
                .rotation(360f)
                .alpha(0.3f)
                .setDuration(COLLECT_DURATION)
                .setStartDelay(delay)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .withEndAction { checkComplete() }
                .start()
        }
    }

    // =========================================================================
    // SELECTION & FEEDBACK ANIMATIONS
    // =========================================================================

    /** Selection bounce feedback */
    fun selectBounce(cardView: CardView, selected: Boolean) {
        cardView.animate().cancel()
        if (selected) {
            cardView.animate()
                .translationY(cardView.animTranslationY - 30f)
                .scaleX(1.1f)
                .scaleY(1.1f)
                .setDuration(SELECT_BOUNCE_DURATION)
                .setInterpolator(OvershootInterpolator(1.5f))
                .start()
        } else {
            cardView.animate()
                .translationY(cardView.animTranslationY)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(SELECT_BOUNCE_DURATION)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }
    }

    /** Shake animation for invalid action */
    fun shakeView(view: View, callback: AnimCallback? = null) {
        val originalX = view.translationX
        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 300
            addUpdateListener { animation ->
                val fraction = animation.animatedValue as Float
                val shake = (Math.sin(fraction * 10 * Math.PI).toFloat() * 15f * (1f - fraction))
                view.translationX = originalX + shake
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    view.translationX = originalX
                    callback?.onEnd()
                }
            })
            start()
        }
    }

    /** Pulse animation for AI thinking */
    fun startAiPulse(layout: LinearLayout) {
        layout.tag = "pulsing"
        pulseOnce(layout)
    }

    private fun pulseOnce(layout: LinearLayout) {
        if (layout.tag != "pulsing") return
        layout.animate()
            .alpha(0.6f)
            .setDuration(AI_PULSE_DURATION / 2)
            .withEndAction {
                if (layout.tag == "pulsing") {
                    layout.animate()
                        .alpha(1f)
                        .setDuration(AI_PULSE_DURATION / 2)
                        .withEndAction { pulseOnce(layout) }
                        .start()
                }
            }
            .start()
    }

    fun stopAiPulse(layout: LinearLayout) {
        layout.tag = null
        layout.animate().cancel()
        layout.alpha = 1f
    }

    // =========================================================================
    // CARD FLIP ANIMATIONS
    // =========================================================================

    /** Flip card face down/up */
    fun flipCard(cardView: CardView, faceUp: Boolean, callback: AnimCallback? = null) {
        val targetScaleX = if (faceUp) 1f else -1f
        cardView.animate()
            .scaleX(0f)
            .setDuration(FLIP_DURATION / 2)
            .withEndAction {
                cardView.isFaceUp = faceUp
                cardView.postInvalidate()
                cardView.animate()
                    .scaleX(targetScaleX)
                    .setDuration(FLIP_DURATION / 2)
                    .withEndAction { callback?.onEnd() }
                    .start()
            }
            .start()
    }

    // =========================================================================
    // SCORE ANIMATIONS
    // =========================================================================

    /** Animate score counter */
    fun animateScore(textView: android.widget.TextView, from: Int, to: Int, callback: AnimCallback? = null) {
        ValueAnimator.ofInt(from, to).apply {
            duration = SCORE_ANIM_DURATION
            setInterpolator(DecelerateInterpolator())
            addUpdateListener { animation ->
                val value = animation.animatedValue as Int
                textView.text = value.toString()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    callback?.onEnd()
                }
            })
            start()
        }
    }

    // =========================================================================
    // MOON CELEBRATION
    // =========================================================================

    /** Trigger moon celebration (handled by ParticleSystem) */
    fun triggerMoonCelebration(
        particleSystem: ParticleSystem,
        winnerX: Float, winnerY: Float,
        screenWidth: Int, screenHeight: Int,
        callback: AnimCallback? = null
    ) {
        particleSystem.celebrateMoonShoot(winnerX, winnerY, screenWidth, screenHeight)
        particleSystem.setOnComplete { callback?.onEnd() }
    }

    // =========================================================================
    // UTILITY
    // =========================================================================

    /** Cancel all animations on a view */
    fun cancelAll(view: View) {
        view.animate().cancel()
    }

    /** Calculate position for player hand area */
    fun getPlayerHandCenter(handLayout: LinearLayout, position: PlayerPosition): Pair<Float, Float> {
        return Pair(
            handLayout.x + handLayout.width / 2f,
            handLayout.y + handLayout.height / 2f
        )
    }

    /** Calculate center play area position */
    fun getTrickPosition(centerView: View, trickIndex: Int): Pair<Float, Float> {
        val radius = 100f
        val angle = (trickIndex * 90f - 90f) * (Math.PI / 180.0).toFloat()
        val centerX = centerView.x + centerView.width / 2f
        val centerY = centerView.y + centerView.height / 2f
        return Pair(
            centerX + radius * cos(angle),
            centerY + radius * sin(angle)
        )
    }
}