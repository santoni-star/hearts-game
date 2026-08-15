package com.example.heartsgame.game

import kotlin.random.Random

class Deck {
    private val cards = mutableListOf<Card>()

    init {
        reset()
    }

    fun reset() {
        cards.clear()
        cards.addAll(Card.ALL_CARDS)
        shuffle()
    }

    fun shuffle() {
        for (i in cards.size - 1 downTo 1) {
            val j = Random.nextInt(i + 1)
            val temp = cards[i]
            cards[i] = cards[j]
            cards[j] = temp
        }
    }

    fun draw(): Card? = if (cards.isEmpty()) null else cards.removeAt(cards.lastIndex)

    fun drawMultiple(count: Int): List<Card> {
        val drawn = mutableListOf<Card>()
        repeat(count) {
            draw()?.let { drawn.add(it) }
        }
        return drawn
    }

    fun deal(): List<List<Card>> {
        val hands = List(4) { mutableListOf<Card>() }
        var playerIndex = 0
        while (!cards.isEmpty()) {
            hands[playerIndex].add(draw()!!)
            playerIndex = (playerIndex + 1) % 4
        }
        hands.forEach { it.sort() }
        return hands
    }

    val size: Int get() = cards.size
    val isEmpty: Boolean get() = cards.isEmpty()
    val remaining: List<Card> get() = cards.toList()
}