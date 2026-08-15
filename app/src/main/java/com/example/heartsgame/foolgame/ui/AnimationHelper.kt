package com.example.heartsgame.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import kotlin.coroutines.experimental.channels.produce
import kotlin.coroutines.experimental.startCoroutine

/**
 * Animation utilities for the Fool game.
 * All animations run on UI thread via View.animate() or ObjectAnimator.
 */
object AnimationHelper {

    private const val DEAL_DURATION = 400L
    private const val PLAY_DURATION = 500L
    private const val BEAT_DURATION = 400L
    private const val TAKE_DURATION = 600L
    private const val SELECT_BOUNCE_DURATION = 200L
    private const val AI_PULSE_DURATION = 1000L
    private const val CARD_FLIP_DURATION = 300L

    /** Interface for animation callbacks */
    interface AnimCallback {
        fun onEnd()
    }

    /** Deal a card from deck position to target view */
    fun dealCard(
        cardView: CardView,
        startX: Float, startY: Float,
        endX: Float, endY: Float,
        delay: Long = 0,
        callback: AnimCallback? = null
    ) {
        cardView.translationX = startX - cardView.x
        cardView.translationY = startY - cardView.y
        cardView.scaleX = 0.3f
        cardView.scaleY = 0.3f
        cardView.alpha = 0f

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

    /** Play card from hand to table */
    fun playCard(
        cardView: CardView,
        startX: Float, startY: Float,
        endX: Float, endY: Float,
        rotation: Float = 0f,
        callback: AnimCallback? = null
    ) {
        val startTranslationX = cardView.translationX
        val startTranslationY = cardView.translationY

        cardView.animate()
            .translationX(endX - cardView.x + startTranslationX)
            .translationY(endY - cardView.y + startTranslationY)
            .rotation(rotation)
            .scaleX(0.9f)
            .scaleY(0.9f)
            .setDuration(PLAY_DURATION)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .withEndAction {
                cardView.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(100)
                    .setInterpolator(DecelerateInterpolator())
                    .withEndAction { callback?.onEnd() }
                    .start()
            }
            .start()
    }

    /** Beat/defend card - slide over attack card */
    fun beatCard(
        defenseView: CardView,
        attackView: CardView,
        callback: AnimCallback? = null
    ) {
        val targetX = attackView.x + attackView.translationX
        val targetY = attackView.y + attackView.translationY - attackView.height * 0.6f

        defenseView.animate()
            .translationX(targetX - defenseView.x)
            .translationY(targetY - defenseView.y)
            .rotation(0f)
            .scaleX(0.85f)
            .scaleY(0.85f)
            .setDuration(BEAT_DURATION)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .withEndAction {
                defenseView.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(80)
                    .withEndAction { callback?.onEnd() }
                    .start()
            }
            .start()
    }

    /** Take cards - sweep from table to hand */
    fun takeCards(
        cardViews: List<CardView>,
        targetX: Float, targetY: Float,
        staggerDelay: Long = 80,
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
            cv.animate()
                .translationX(targetX - cv.x)
                .translationY(targetY - cv.y)
                .scaleX(0.4f)
                .scaleY(0.4f)
                .rotation(0f)
                .alpha(0.3f)
                .setDuration(TAKE_DURATION)
                .setStartDelay(delay)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .withEndAction { checkComplete() }
                .start()
        }
    }

    /** Selection bounce feedback */
    fun selectBounce(cardView: CardView, selected: Boolean) {
        cardView.animate().cancel()
        if (selected) {
            cardView.animate()
                .translationY(cardView.translationY - 30f)
                .scaleX(1.1f)
                .scaleY(1.1f)
                .setDuration(SELECT_BOUNCE_DURATION)
                .setInterpolator(OvershootInterpolator(1.5f))
                .start()
        } else {
            cardView.animate()
                .translationY(cardView.translationY)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(SELECT_BOUNCE_DURATION)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }
    }

    /** AI thinking pulse on AI hand */
    fun startAiPulse(aiHandLayout: android.widget.LinearLayout) {
        aiHandLayout.tag = "pulsing"
        pulseOnce(aiHandLayout)
    }

    private fun pulseOnce(layout: android.widget.LinearLayout) {
        if (layout.tag != "pulsing") return
        layout.animate()
            .alpha(0.5f)
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

    fun stopAiPulse(aiHandLayout: android.widget.LinearLayout) {
        aiHandLayout.tag = null
        aiHandLayout.animate().cancel()
        aiHandLayout.alpha = 1f
    }

    /** Flip card face down/up */
    fun flipCard(cardView: CardView, faceUp: Boolean, callback: AnimCallback? = null) {
        val targetScaleX = if (faceUp) 1f else -1f
        cardView.animate()
            .scaleX(0f)
            .setDuration(CARD_FLIP_DURATION / 2)
            .withEndAction {
                cardView.isFaceUp = faceUp
                cardView.postInvalidate()
                cardView.animate()
                    .scaleX(targetScaleX)
                    .setDuration(CARD_FLIP_DURATION / 2)
                    .withEndAction { callback?.onEnd() }
                    .start()
            }
            .start()
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

    /** Slide new card into hand (when drawing) */
    fun slideIntoHand(cardView: CardView, fromX: Float, fromY: Float, callback: AnimCallback? = null) {
        cardView.translationX = fromX - cardView.x
        cardView.translationY = fromY - cardView.y
        cardView.alpha = 0f
        cardView.scaleX = 0.5f
        cardView.scaleY = 0.5f

        cardView.animate()
            .translationX(0f)
            .translationY(0f)
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(300)
            .setInterpolator(OvershootInterpolator(1.1f))
            .withEndAction { callback?.onEnd() }
            .start()
    }

    /** Discard animation - shrink and fade */
    fun discardCard(cardView: CardView, callback: AnimCallback? = null) {
        cardView.animate()
            .scaleX(0f)
            .scaleY(0f)
            .alpha(0f)
            .rotation(360f)
            .setDuration(300)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .withEndAction { callback?.onEnd() }
            .start()
    }
}