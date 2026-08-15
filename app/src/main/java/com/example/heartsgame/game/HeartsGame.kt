package com.example.heartsgame.game

import kotlin.random.Random

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

class Player(
    val position: PlayerPosition,
    val isHuman: Boolean = false,
    val personality: AiPersonality = AiPersonality.BALANCED
) {
    var hand = mutableListOf<Card>()
    var score: Int = 0
    var roundScore: Int = 0
    var passedCards: MutableList<Card> = mutableListOf()
    var receivedCards: MutableList<Card> = mutableListOf()
    var hasPassed: Boolean = false
    var hasReceived: Boolean = false

    fun resetForNewRound() {
        hand.clear()
        roundScore = 0
        passedCards.clear()
        receivedCards.clear()
        hasPassed = false
        hasReceived = false
    }

    fun resetForNewGame() {
        score = 0
        resetForNewRound()
    }

    fun addCards(cards: List<Card>) {
        hand.addAll(cards)
        hand.sort()
    }

    fun removeCard(card: Card): Boolean = hand.remove(card)

    fun playableCards(trick: Trick?): List<Card> {
        if (trick == null || trick.cards.isEmpty()) {
            return hand.toList()
        }
        val ledSuit = trick.suitLed!!
        val sameSuit = hand.filter { it.suit == ledSuit }
        return if (sameSuit.isNotEmpty()) sameSuit else hand.toList()
    }
}

data class GameState(
    val players: List<Player>,
    val currentTrick: Trick?,
    val tricksWon: List<Trick>,
    val currentPlayer: PlayerPosition,
    val passDirection: PassDirection,
    val roundNumber: Int,
    val heartsBroken: Boolean,
    val firstTrickOfRound: Boolean
)

class HeartsGame(
    val onGameStateChanged: (HeartsGame) -> Unit = {},
    val onAnimationTrigger: (String, Any?) -> Unit = {}
) {
    val players = mutableListOf<Player>()
    val deck = Deck()
    var currentTrick: Trick? = null
    val tricksWon = mutableListOf<Trick>()
    var phase = GamePhase.PASSING
    var passDirection = PassDirection.LEFT
    var roundNumber = 0
    var heartsBroken = false
    var firstTrickOfRound = true
    var gameOver = false
    var winner: PlayerPosition? = null

    private var aiDelayMs = 800L

    init {
        players.add(Player(PlayerPosition.SOUTH, isHuman = true))
        players.add(Player(PlayerPosition.WEST, personality = AiPersonality.DEFENSIVE))
        players.add(Player(PlayerPosition.NORTH, personality = AiPersonality.AGGRESSIVE))
        players.add(Player(PlayerPosition.EAST, personality = AiPersonality.BALANCED))
    }

    fun startGame() {
        players.forEach { it.resetForNewGame() }
        roundNumber = 0
        gameOver = false
        winner = null
        startNewRound()
    }

    fun startNewRound() {
        roundNumber++
        deck.reset()
        players.forEach { it.resetForNewRound() }
        
        val hands = deck.deal()
        players.forEachIndexed { idx, player ->
            player.addCards(hands[idx])
        }

        passDirection = when (roundNumber % 4) {
            1 -> PassDirection.LEFT
            2 -> PassDirection.RIGHT
            3 -> PassDirection.ACROSS
            0 -> PassDirection.NONE
            else -> PassDirection.LEFT
        }

        if (passDirection != PassDirection.NONE) {
            phase = GamePhase.PASSING
        } else {
            phase = GamePhase.PLAYING
            firstTrickOfRound = true
            heartsBroken = false
            val twoClubs = Card(Suit.CLUBS, Rank.TWO)
            val starter = players.firstOrNull { twoClubs in it.hand }?.position ?: PlayerPosition.SOUTH
            currentTrick = Trick(starter)
        }
        notifyChange()
    }

    fun humanPassSelected(cards: List<Card>): Boolean {
        if (phase != GamePhase.PASSING) return false
        val human = players[0]
        if (cards.size != 3) return false
        if (cards.any { it !in human.hand }) return false

        human.passedCards.addAll(cards)
        cards.forEach { human.removeCard(it) }
        human.hasPassed = true

        processAiPasses()

        if (players.all { it.hasPassed }) {
            phase = GamePhase.PASS_REVEAL
            distributePassedCards()
        }
        notifyChange()
        return true
    }

    private fun processAiPasses() {
        players.filter { !it.isHuman }.forEach { ai ->
            val toPass = ai.personality.choosePass(ai.hand, passDirection, players, ai.position)
            ai.passedCards.addAll(toPass)
            toPass.forEach { ai.removeCard(it) }
            ai.hasPassed = true
        }
    }

    private fun distributePassedCards() {
        players.forEachIndexed { idx, player ->
            val recipientIdx = when (passDirection) {
                PassDirection.LEFT -> (idx + 1) % 4
                PassDirection.RIGHT -> (idx + 3) % 4
                PassDirection.ACROSS -> (idx + 2) % 4
                PassDirection.NONE -> idx
            }
            val recipient = players[recipientIdx]
            recipient.receivedCards.addAll(player.passedCards)
            recipient.hasReceived = true
        }

        onAnimationTrigger("passCards", null)
    }

    fun finishPassReveal() {
        players.forEach { player ->
            player.addCards(player.receivedCards)
            player.receivedCards.clear()
        }
        phase = GamePhase.PLAYING
        firstTrickOfRound = true
        heartsBroken = false

        val twoClubs = Card(Suit.CLUBS, Rank.TWO)
        val starter = players.firstOrNull { twoClubs in it.hand }?.position ?: PlayerPosition.SOUTH
        currentTrick = Trick(starter)
        notifyChange()

        if (starter != PlayerPosition.SOUTH) {
            scheduleAiTurn()
        }
    }

    fun humanPlayCard(card: Card): Boolean {
        if (phase != GamePhase.PLAYING) return false
        val human = players[0]
        if (currentTrick == null) return false
        if (currentTrick!!.currentPlayer != PlayerPosition.SOUTH) return false
        if (card !in human.hand) return false

        val playable = human.playableCards(currentTrick)
        if (card !in playable) return false

        if (firstTrickOfRound) {
            val twoClubs = Card(Suit.CLUBS, Rank.TWO)
            if (card != twoClubs) return false
            if (card.suit == Suit.HEARTS || card == Card(Suit.SPADES, Rank.QUEEN)) return false
        } else if (currentTrick!!.cards.isEmpty()) {
            if (card.suit == Suit.HEARTS && !heartsBroken) return false
        }

        playCard(PlayerPosition.SOUTH, card)
        return true
    }

    private fun playCard(position: PlayerPosition, card: Card) {
        val player = players[position.index]
        player.removeCard(card)
        currentTrick!!.cards.add(PlayedCard(position, card))

        if (card.suit == Suit.HEARTS) {
            heartsBroken = true
            onAnimationTrigger("heartBurst", position)
        }
        if (card == Card(Suit.SPADES, Rank.QUEEN)) {
            onAnimationTrigger("queenSpadesGlow", position)
        }

        onAnimationTrigger("playCard", mapOf("player" to position, "card" to card))

        if (currentTrick!!.isComplete) {
            phase = GamePhase.TRICK_END
            val trickWinner = currentTrick!!.winner!!
            val points = currentTrick!!.totalPoints
            players[trickWinner.index].roundScore += points
            tricksWon.add(currentTrick!!)

            onAnimationTrigger("collectTrick", mapOf("winner" to trickWinner, "points" to points))

            if (players[trickWinner.index].roundScore == 26) {
                onAnimationTrigger("moonCelebration", trickWinner)
            }

            scheduleTrickEnd(trickWinner)
        } else {
            val nextPlayer = currentTrick!!.currentPlayer
            if (nextPlayer != PlayerPosition.SOUTH) {
                scheduleAiTurn()
            }
            notifyChange()
        }
    }

    private fun scheduleAiTurn() {}

    private fun scheduleTrickEnd(winner: PlayerPosition) {}

    fun finishTrickEnd() {
        val lastTrick = tricksWon.last()
        val winner = lastTrick.winner!!
        
        if (tricksWon.size == 13) {
            finishRound()
        } else {
            firstTrickOfRound = false
            currentTrick = Trick(winner)
            notifyChange()
            
            if (winner != PlayerPosition.SOUTH) {
                scheduleAiTurn()
            }
        }
    }

    private fun finishRound() {
        phase = GamePhase.ROUND_END
        
        val moonShooter = players.firstOrNull { it.roundScore == 26 }
        if (moonShooter != null) {
            players.forEach { p ->
                if (p != moonShooter) {
                    p.score += 26
                }
            }
        } else {
            players.forEach { it.score += it.roundScore }
        }

        val gameEnded = players.any { it.score >= 100 }
        if (gameEnded) {
            gameOver = true
            phase = GamePhase.GAME_OVER
            winner = players.minByOrNull { it.score }?.position
        }

        notifyChange()
    }

    fun startNextRound() {
        if (gameOver) return
        startNewRound()
    }

    fun getGameState(): GameState = GameState(
        players = players.toList(),
        currentTrick = currentTrick,
        tricksWon = tricksWon.toList(),
        currentPlayer = currentTrick?.currentPlayer ?: players[0].position,
        passDirection = passDirection,
        roundNumber = roundNumber,
        heartsBroken = heartsBroken,
        firstTrickOfRound = firstTrickOfRound
    )

    fun executeAiTurn(): AiAction? {
        if (phase != GamePhase.PLAYING) return null
        if (currentTrick == null) return null
        
        val currentPlayer = currentTrick!!.currentPlayer
        if (currentPlayer == PlayerPosition.SOUTH) return null
        
        val aiPlayer = players[currentPlayer.index]
        val gameState = getGameState()
        val card = aiPlayer.personality.choosePlay(aiPlayer.hand, currentTrick!!, gameState, aiPlayer.position)
        
        if (card != null) {
            playCard(currentPlayer, card)
            return AiAction.PLAYED(card, currentPlayer)
        }
        
        return AiAction.NOTHING
    }

    private fun notifyChange() {
        onGameStateChanged(this)
    }
}

sealed class AiAction {
    data class PLAYED(val card: Card, val player: PlayerPosition) : AiAction()
    object NOTHING : AiAction()
}

interface AiStrategy {
    fun choosePass(hand: List<Card>, direction: PassDirection, allPlayers: List<Player>, myPosition: PlayerPosition): List<Card>
    fun choosePlay(hand: List<Card>, trick: Trick, gameState: GameState, myPosition: PlayerPosition): Card?
}

class BalancedAi : AiStrategy {
    override fun choosePass(hand: List<Card>, direction: PassDirection, allPlayers: List<Player>, myPosition: PlayerPosition): List<Card> {
        val sorted = hand.sortedByDescending { 
            when {
                it == Card(Suit.SPADES, Rank.QUEEN) -> 1000
                it.suit == Suit.HEARTS -> it.rank.value + 100
                it.suit == Suit.SPADES -> it.rank.value + 50
                else -> it.rank.value
            }
        }
        return sorted.take(3)
    }

    override fun choosePlay(hand: List<Card>, trick: Trick, gameState: GameState, myPosition: PlayerPosition): Card? {
        val playable = hand.filter { it in getPlayableCards(hand, trick) }
        if (playable.isEmpty()) return null

        val ledSuit = trick.suitLed
        val isLeading = trick.cards.isEmpty()

        if (isLeading) {
            val nonHearts = playable.filter { it.suit != Suit.HEARTS }.sortedBy { it.rank.value }
            if (nonHearts.isNotEmpty() && (!gameState.heartsBroken || playable.any { it.suit != Suit.HEARTS })) {
                return nonHearts.first()
            }
            return playable.filter { it.suit == Suit.HEARTS }.minByOrNull { it.rank.value }
        } else {
            val sameSuit = playable.filter { it.suit == ledSuit }
            if (sameSuit.isNotEmpty()) {
                val winningCards = sameSuit.filter { it.rank.value > trick.cards.maxByOrNull { it.card.rank.value }?.card.rank.value ?: 0 }
                return if (winningCards.isNotEmpty()) winningCards.minByOrNull { it.rank.value } else sameSuit.minByOrNull { it.rank.value }
            } else {
                val penalties = playable.filter { it.points > 0 }.sortedByDescending { it.points }.thenByDescending { it.rank.value }
                if (penalties.isNotEmpty()) return penalties.first()
                return playable.maxByOrNull { it.rank.value }
            }
        }
    }

    private fun getPlayableCards(hand: List<Card>, trick: Trick): List<Card> {
        if (trick.cards.isEmpty()) return hand
        val ledSuit = trick.suitLed!!
        val sameSuit = hand.filter { it.suit == ledSuit }
        return if (sameSuit.isNotEmpty()) sameSuit else hand
    }
}

class AggressiveAi : AiStrategy {
    private val balanced = BalancedAi()

    override fun choosePass(hand: List<Card>, direction: PassDirection, allPlayers: List<Player>, myPosition: PlayerPosition): List<Card> {
        val hearts = hand.filter { it.suit == Suit.HEARTS }.sortedByDescending { it.rank.value }
        val qSpades = hand.firstOrNull { it == Card(Suit.SPADES, Rank.QUEEN) }
        
        if (hearts.size >= 5) {
            val nonHearts = hand.filter { it.suit != Suit.HEARTS }.sortedByDescending { 
                if (it == Card(Suit.SPADES, Rank.QUEEN)) 1000 else it.rank.value 
            }
            return nonHearts.take(3)
        }
        return balanced.choosePass(hand, direction, allPlayers, myPosition)
    }

    override fun choosePlay(hand: List<Card>, trick: Trick, gameState: GameState, myPosition: PlayerPosition): Card? {
        val myPlayer = gameState.players[myPosition.index]
        val heartsHeld = myPlayer.hand.count { it.suit == Suit.HEARTS }
        val qSpadesHeld = myPlayer.hand.any { it == Card(Suit.SPADES, Rank.QUEEN) }
        val pointsSoFar = myPlayer.roundScore
        
        if (pointsSoFar + heartsHeld + (if (qSpadesHeld) 13 else 0) >= 20) {
            return playForMoon(hand, trick, gameState)
        }
        
        return balanced.choosePlay(hand, trick, gameState, myPosition)
    }

    private fun playForMoon(hand: List<Card>, trick: Trick, gameState: GameState): Card? {
        val playable = hand.filter { it in getPlayableCards(hand, trick) }
        if (playable.isEmpty()) return null

        val ledSuit = trick.suitLed
        val isLeading = trick.cards.isEmpty()

        if (isLeading) {
            val hearts = playable.filter { it.suit == Suit.HEARTS }.sortedByDescending { it.rank.value }
            if (hearts.isNotEmpty()) return hearts.first()
            return playable.maxByOrNull { it.rank.value }
        } else {
            val sameSuit = playable.filter { it.suit == ledSuit }
            if (sameSuit.isNotEmpty()) {
                val winningCards = sameSuit.filter { it.rank.value > trick.cards.maxByOrNull { it.card.rank.value }?.card.rank.value ?: 0 }
                return if (winningCards.isNotEmpty()) winningCards.maxByOrNull { it.rank.value } else sameSuit.maxByOrNull { it.rank.value }
            } else {
                val penalties = playable.filter { it.points > 0 }.sortedByDescending { it.points }.thenByDescending { it.rank.value }
                return penalties.firstOrNull() ?: playable.maxByOrNull { it.rank.value }
            }
        }
    }

    private fun getPlayableCards(hand: List<Card>, trick: Trick): List<Card> {
        if (trick.cards.isEmpty()) return hand
        val ledSuit = trick.suitLed!!
        val sameSuit = hand.filter { it.suit == ledSuit }
        return if (sameSuit.isNotEmpty()) sameSuit else hand
    }
}

class DefensiveAi : AiStrategy {
    private val balanced = BalancedAi()

    override fun choosePass(hand: List<Card>, direction: PassDirection, allPlayers: List<Player>, myPosition: PlayerPosition): List<Card> {
        val penalties = hand.filter { it.points > 0 }.sortedByDescending { it.points }.thenByDescending { it.rank.value }
        if (penalties.size >= 3) return penalties.take(3)
        
        val remaining = hand.filter { it.points == 0 }.sortedByDescending { it.rank.value }
        return (penalties + remaining).take(3)
    }

    override fun choosePlay(hand: List<Card>, trick: Trick, gameState: GameState, myPosition: PlayerPosition): Card? {
        val playable = hand.filter { it in getPlayableCards(hand, trick) }
        if (playable.isEmpty()) return null

        val ledSuit = trick.suitLed
        val isLeading = trick.cards.isEmpty()

        if (isLeading) {
            val safe = playable.filter { it.points == 0 }.sortedBy { it.rank.value }
            return safe.firstOrNull() ?: playable.minByOrNull { it.rank.value }
        } else {
            val sameSuit = playable.filter { it.suit == ledSuit }
            if (sameSuit.isNotEmpty()) {
                val currentHighest = trick.cards.maxByOrNull { it.card.rank.value }?.card.rank.value ?: 0
                val losingCards = sameSuit.filter { it.rank.value < currentHighest }.sortedByDescending { it.rank.value }
                if (losingCards.isNotEmpty()) return losingCards.first()
                return sameSuit.minByOrNull { it.rank.value }
            } else {
                val penalties = playable.filter { it.points > 0 }.sortedByDescending { it.points }.thenByDescending { it.rank.value }
                return penalties.firstOrNull() ?: playable.maxByOrNull { it.rank.value }
            }
        }
    }

    private fun getPlayableCards(hand: List<Card>, trick: Trick): List<Card> {
        if (trick.cards.isEmpty()) return hand
        val ledSuit = trick.suitLed!!
        val sameSuit = hand.filter { it.suit == ledSuit }
        return if (sameSuit.isNotEmpty()) sameSuit else hand
    }
}

val AiPersonality.strategy: AiStrategy
    get() = when (this) {
        AiPersonality.BALANCED -> BalancedAi()
        AiPersonality.AGGRESSIVE -> AggressiveAi()
        AiPersonality.DEFENSIVE -> DefensiveAi()
    }
}