package com.example.heartsgame.game

import kotlin.random.Random

class Deck {
    private val cards = mutableListOf<Card>()

    init {
        reset()
    }

    /** Reset and shuffle the deck */
    fun reset() {
        cards.clear()
        cards.addAll(Card.ALL_CARDS)
        shuffle()
    }

    /** Fisher-Yates shuffle */
    fun shuffle() {
        for (i in cards.size - 1 downTo 1) {
            val j = Random.nextInt(i + 1)
            val temp = cards[i]
            cards[i] = cards[j]
            cards[j] = temp
        }
    }

    /** Draw a single card from top of deck */
    fun draw(): Card? = if (cards.isEmpty()) null else cards.removeAt(cards.lastIndex)

    /** Draw multiple cards */
    fun drawMultiple(count: Int): List<Card> {
        val drawn = mutableListOf<Card>()
        repeat(count) {
            draw()?.let { drawn.add(it) }
        }
        return drawn
    }

    /** Deal 13 cards to each of 4 players */
    fun deal(): List<List<Card>> {
        val hands = List(4) { mutableListOf<Card>() }
        var playerIndex = 0
        while (!cards.isEmpty()) {
            hands[playerIndex].add(draw()!!)
            playerIndex = (playerIndex + 1) % 4
        }
        // Sort each hand
        hands.forEach { it.sort() }
        return hands
    }

    val size: Int get() = cards.size
    val isEmpty: Boolean get() = cards.isEmpty()
    val remaining: List<Card> get() = cards.toList()
}