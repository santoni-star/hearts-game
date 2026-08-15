package com.example.heartsgame.game

enum class Suit(val symbol: String, val color: Int, val order: Int) {
    CLUBS("♣", 0xFF000000, 0),
    DIAMONDS("♦", 0xFFCC0000, 1),
    SPADES("♠", 0xFF000000, 2),
    HEARTS("♥", 0xFFCC0000, 3)
}

enum class Rank(val value: Int, val display: String, val order: Int) {
    TWO(2, "2", 0), THREE(3, "3", 1), FOUR(4, "4", 2), FIVE(5, "5", 3),
    SIX(6, "6", 4), SEVEN(7, "7", 5), EIGHT(8, "8", 6), NINE(9, "9", 7),
    TEN(10, "10", 7), JACK(11, "J", 8), QUEEN(12, "Q", 9), KING(13, "K", 10), ACE(14, "A", 11)
}

data class Card(
    val suit: Suit,
    val rank: Rank
) : Comparable<Card> {
    val points: Int
        get() = when {
            suit == Suit.HEARTS -> 1
            this == Card(Suit.SPADES, Rank.QUEEN) -> 13
            else -> 0
        }

    val id: String = "${suit.name}_${rank.name}"

    override fun compareTo(other: Card): Int {
        if (suit != other.suit) return suit.order - other.suit.order
        return rank.order - other.rank.order
    }

    override fun toString(): String = "${rank.display}${suit.symbol}"

    companion object {
        val ALL_CARDS: List<Card> = Suit.values().flatMap { suit ->
            Rank.values().map { rank -> Card(suit, rank) }
        }.sorted()

        fun fromId(id: String): Card? = ALL_CARDS.firstOrNull { it.id == id }
    }
}

enum class PlayerPosition(val index: Int, val label: String) {
    SOUTH(0, "You"),
    WEST(1, "West"),
    NORTH(2, "North"),
    EAST(3, "East")
}

enum class PassDirection(val label: String) {
    LEFT("Left"),
    RIGHT("Right"),
    ACROSS("Across"),
    NONE("No Pass")
}

enum class GamePhase {
    PASSING,
    PASS_REVEAL,
    PLAYING,
    TRICK_END,
    ROUND_END,
    GAME_OVER
}

data class Trick(
    val leader: PlayerPosition,
    val cards: MutableList<PlayedCard> = mutableListOf(),
    var heartsBroken: Boolean = false
) {
    val suitLed: Suit? get() = cards.firstOrNull()?.card.suit
    val isComplete: Boolean get() = cards.size == 4
    val currentPlayer: PlayerPosition get() = PlayerPosition.values()[(leader.index + cards.size) % 4]

    val winner: PlayerPosition?
        get() {
            if (!isComplete) return null
            val ledSuit = suitLed!!
            return cards.maxByOrNull { it.card.points }?.player
                ?: cards.filter { it.card.suit == ledSuit }.maxByOrNull { it.card.rank.value }?.player
                ?: leader
        }

    val totalPoints: Int = cards.sumOf { it.card.points }
}

data class PlayedCard(
    val player: PlayerPosition,
    val card: Card
)

enum class AiPersonality(val label: String) {
    BALANCED("Balanced"),
    AGGRESSIVE("Aggressive"),
    DEFENSIVE("Defensive")
}