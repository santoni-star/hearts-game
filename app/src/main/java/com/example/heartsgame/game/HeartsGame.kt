package com.example.heartsgame.game

import kotlin.random.Random
import kotlin.math.maxOf

/** Represents a player in the game */
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

    /** Cards that can be legally played given the current trick */
    fun playableCards(trick: Trick?): List<Card> {
        if (trick == null || trick.cards.isEmpty()) {
            // Leading - can play any card except restrictions on first trick
            return hand.toList()
        }
        val ledSuit = trick.suitLed!!
        val sameSuit = hand.filter { it.suit == ledSuit }
        return if (sameSuit.isNotEmpty()) sameSuit else hand.toList()
    }
}

/** Game state snapshot for AI decisions */
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

/** Main Hearts game controller */
class HeartsGame(
    val onGameStateChanged: (HeartsGame) -> Unit = {},
    val onAnimationTrigger: (String, Any?) -> Unit = {}
) {

    // Players in position order: SOUTH(0/human), WEST(1), NORTH(2), EAST(3)
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

    // AI delay for visible turns
    private var aiDelayMs = 800L

    init {
        // Create 4 players: South=Human, others=AI with different personalities
        players.add(Player(PlayerPosition.SOUTH, isHuman = true))
        players.add(Player(PlayerPosition.WEST, personality = AiPersonality.DEFENSIVE))
        players.add(Player(PlayerPosition.NORTH, personality = AiPersonality.AGGRESSIVE))
        players.add(Player(PlayerPosition.EAST, personality = AiPersonality.BALANCED))
    }

    /** Start a new game */
    fun startGame() {
        players.forEach { it.resetForNewGame() }
        roundNumber = 0
        gameOver = false
        winner = null
        startNewRound()
    }

    /** Start a new round */
    fun startNewRound() {
        roundNumber++
        deck.reset()
        players.forEach { it.resetForNewRound() }
        
        // Deal cards
        val hands = deck.deal()
        players.forEachIndexed { idx, player ->
            player.addCards(hands[idx])
        }

        // Determine pass direction for this round
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
            // Find player with 2 of Clubs - they lead first trick
            val twoClubs = Card(Suit.CLUBS, Rank.TWO)
            val starter = players.firstOrNull { twoClubs in it.hand }?.position ?: PlayerPosition.SOUTH
            currentTrick = Trick(starter)
        }
        notifyChange()
    }

    /** Called when human player selects cards to pass */
    fun humanPassSelected(cards: List<Card>): Boolean {
        if (phase != GamePhase.PASSING) return false
        val human = players[0]
        if (cards.size != 3) return false
        if (cards.any { it !in human.hand }) return false

        human.passedCards.addAll(cards)
        cards.forEach { human.removeCard(it) }
        human.hasPassed = true

        // Trigger AI passes
        processAiPasses()

        // All passed - move to reveal phase
        if (players.all { it.hasPassed }) {
            phase = GamePhase.PASS_REVEAL
            distributePassedCards()
        }
        notifyChange()
        return true
    }

    /** AI chooses 3 cards to pass */
    private fun processAiPasses() {
        players.filter { !it.isHuman }.forEach { ai ->
            val toPass = ai.personality.strategy.choosePass(ai.hand, passDirection, players, ai.position)
            ai.passedCards.addAll(toPass)
            toPass.forEach { ai.removeCard(it) }
            ai.hasPassed = true
        }
    }

    /** Distribute passed cards to recipients */
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

        // Animate card passing
        onAnimationTrigger("passCards", null)

        // After reveal delay, add cards to hands and start play
        // This will be triggered by animation callback in Activity
    }

    /** Called after pass reveal animation completes */
    fun finishPassReveal() {
        players.forEach { player ->
            player.addCards(player.receivedCards)
            player.receivedCards.clear()
        }
        phase = GamePhase.PLAYING
        firstTrickOfRound = true
        heartsBroken = false

        // Find player with 2 of Clubs - they lead first trick
        val twoClubs = Card(Suit.CLUBS, Rank.TWO)
        val starter = players.firstOrNull { twoClubs in it.hand }?.position ?: PlayerPosition.SOUTH
        currentTrick = Trick(starter)
        notifyChange()

        // If AI leads, start AI turn
        if (starter != PlayerPosition.SOUTH) {
            scheduleAiTurn()
        }
    }

    /** Human plays a card */
    fun humanPlayCard(card: Card): Boolean {
        if (phase != GamePhase.PLAYING) return false
        val human = players[0]
        if (currentTrick == null) return false
        if (currentTrick!!.currentPlayer != PlayerPosition.SOUTH) return false
        if (card !in human.hand) return false

        // Check legal play
        val playable = human.playableCards(currentTrick)
        if (card !in playable) return false

        // First trick restrictions
        if (firstTrickOfRound) {
            val twoClubs = Card(Suit.CLUBS, Rank.TWO)
            if (card != twoClubs) return false // Must lead 2♣
            if (card.suit == Suit.HEARTS || card == Card(Suit.SPADES, Rank.QUEEN)) return false
        } else if (currentTrick!!.cards.isEmpty()) {
            // Leading subsequent tricks
            if (card.suit == Suit.HEARTS && !heartsBroken) return false // Can't lead hearts until broken
        }

        playCard(PlayerPosition.SOUTH, card)
        return true
    }

    /** Core card play logic */
    private fun playCard(position: PlayerPosition, card: Card) {
        val player = players[position.index]
        player.removeCard(card)
        currentTrick!!.cards.add(PlayedCard(position, card))

        // Track hearts broken
        if (card.suit == Suit.HEARTS) {
            heartsBroken = true
            onAnimationTrigger("heartBurst", position)
        }
        if (card == Card(Suit.SPADES, Rank.QUEEN)) {
            onAnimationTrigger("queenSpadesGlow", position)
        }

        // Trigger play animation
        onAnimationTrigger("playCard", mapOf("player" to position, "card" to card))

        // Check if trick complete
        if (currentTrick!!.isComplete) {
            phase = GamePhase.TRICK_END
            val trickWinner = currentTrick!!.winner!!
            val points = currentTrick!!.totalPoints
            players[trickWinner.index].roundScore += points
            tricksWon.add(currentTrick!!)

            onAnimationTrigger("collectTrick", mapOf("winner" to trickWinner, "points" to points))

            // Check for shoot the moon
            if (players[trickWinner.index].roundScore == 26) {
                // Winner shot the moon!
                onAnimationTrigger("moonCelebration", trickWinner)
            }

            // Schedule next trick or round end
            scheduleTrickEnd(trickWinner)
        } else {
            // Next player's turn
            val nextPlayer = currentTrick!!.currentPlayer
            if (nextPlayer != PlayerPosition.SOUTH) {
                scheduleAiTurn()
            }
            notifyChange()
        }
    }

    /** Schedule AI turn with delay */
    private fun scheduleAiTurn() {
        // In real implementation, this would use a Handler
        // For now, we'll expose a method for Activity to call
    }

    /** Schedule trick end processing */
    private fun scheduleTrickEnd(winner: PlayerPosition) {
        // Process after animation
    }

    /** Called after trick collection animation completes */
    fun finishTrickEnd() {
        val lastTrick = tricksWon.last()
        val winner = lastTrick.winner!!
        
        // Check if round over (all 13 tricks played)
        if (tricksWon.size == 13) {
            finishRound()
        } else {
            // Winner leads next trick
            firstTrickOfRound = false
            currentTrick = Trick(winner)
            notifyChange()
            
            if (winner != PlayerPosition.SOUTH) {
                scheduleAiTurn()
            }
        }
    }

    /** Finish round, calculate scores */
    private fun finishRound() {
        phase = GamePhase.ROUND_END
        
        // Check for shoot the moon
        val moonShooter = players.firstOrNull { it.roundScore == 26 }
        if (moonShooter != null) {
            // Shooter gets 0, everyone else gets +26
            players.forEach { p ->
                if (p != moonShooter) {
                    p.score += 26
                }
            }
        } else {
            // Normal scoring
            players.forEach { it.score += it.roundScore }
        }

        // Check game over (any player >= 100)
        val gameEnded = players.any { it.score >= 100 }
        if (gameEnded) {
            gameOver = true
            phase = GamePhase.GAME_OVER
            winner = players.minByOrNull { it.score }?.position
        }

        notifyChange()
    }

    /** Called after round end animation/UI */
    fun startNextRound() {
        if (gameOver) return
        startNewRound()
    }

    /** Get current game state for AI */
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

    /** Execute AI turn */
    fun executeAiTurn(): AiAction? {
        if (phase != GamePhase.PLAYING) return null
        if (currentTrick == null) return null
        
        val currentPlayer = currentTrick!!.currentPlayer
        if (currentPlayer == PlayerPosition.SOUTH) return null // Human's turn
        
        val aiPlayer = players[currentPlayer.index]
        val gameState = getGameState()
        val card = aiPlayer.personality.strategy.choosePlay(aiPlayer.hand, currentTrick!!, gameState, aiPlayer.position)
        
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

/** AI Actions */
sealed class AiAction {
    data class PLAYED(val card: Card, val player: PlayerPosition) : AiAction()
    object NOTHING : AiAction()
}

/** AI Strategy Interface */
interface AiStrategy {
    /** Choose 3 cards to pass */
    fun choosePass(hand: List<Card>, direction: PassDirection, allPlayers: List<Player>, myPosition: PlayerPosition): List<Card>
    
    /** Choose a card to play */
    fun choosePlay(hand: List<Card>, trick: Trick, gameState: GameState, myPosition: PlayerPosition): Card?
}

/** Balanced AI - solid all-around play */
class BalancedAi : AiStrategy {
    override fun choosePass(hand: List<Card>, direction: PassDirection, allPlayers: List<Player>, myPosition: PlayerPosition): List<Card> {
        // Pass high hearts, Q♠, high spades, keep low cards
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
            // Leading: prefer low non-hearts, avoid leading hearts unless broken
            val nonHearts = playable.filter { it.suit != Suit.HEARTS }.sortedBy { it.rank.value }
            if (nonHearts.isNotEmpty() && (!gameState.heartsBroken || playable.any { it.suit != Suit.HEARTS })) {
                return nonHearts.first()
            }
            // Must lead hearts
            return playable.filter { it.suit == Suit.HEARTS }.minByOrNull { it.rank.value }
        } else {
            // Following suit
            val sameSuit = playable.filter { it.suit == ledSuit }
            if (sameSuit.isNotEmpty()) {
                // Try to win with lowest winning card, or dump lowest
                val winningCards = sameSuit.filter { it.rank.value > trick.cards.maxByOrNull { it.card.rank.value }?.card.rank.value ?: 0 }
                return if (winningCards.isNotEmpty()) winningCards.minByOrNull { it.rank.value } else sameSuit.minByOrNull { it.rank.value }
            } else {
                // Void in suit - dump highest penalty card
                val penalties = playable.filter { it.points > 0 }.sortedByDescending { it.points }.thenByDescending { it.rank.value }
                if (penalties.isNotEmpty()) return penalties.first()
                // No penalties - dump highest card
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

/** Aggressive AI - tries to shoot the moon */
class AggressiveAi : AiStrategy {
    private val balanced = BalancedAi()

    override fun choosePass(hand: List<Card>, direction: PassDirection, allPlayers: List<Player>, myPosition: PlayerPosition): List<Card> {
        // Keep high hearts for moon attempt, pass low cards and off-suit high cards
        val hearts = hand.filter { it.suit == Suit.HEARTS }.sortedByDescending { it.rank.value }
        val qSpades = hand.firstOrNull { it == Card(Suit.SPADES, Rank.QUEEN) }
        
        // If we have many hearts, keep them for moon shot
        if (hearts.size >= 5) {
            // Pass non-hearts, keep hearts
            val nonHearts = hand.filter { it.suit != Suit.HEARTS }.sortedByDescending { 
                if (it == Card(Suit.SPADES, Rank.QUEEN)) 1000 else it.rank.value 
            }
            return nonHearts.take(3)
        }
        return balanced.choosePass(hand, direction, allPlayers, myPosition)
    }

    override fun choosePlay(hand: List<Card>, trick: Trick, gameState: GameState, myPosition: PlayerPosition): Card? {
        // Check if we're in position to shoot moon
        val myPlayer = gameState.players[myPosition.index]
        val heartsHeld = myPlayer.hand.count { it.suit == Suit.HEARTS }
        val qSpadesHeld = myPlayer.hand.any { it == Card(Suit.SPADES, Rank.QUEEN) }
        val pointsSoFar = myPlayer.roundScore
        
        // If close to moon, play aggressively to capture points
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
            // Lead hearts if we have them to try to capture
            val hearts = playable.filter { it.suit == Suit.HEARTS }.sortedByDescending { it.rank.value }
            if (hearts.isNotEmpty()) return hearts.first()
            return playable.maxByOrNull { it.rank.value }
        } else {
            val sameSuit = playable.filter { it.suit == ledSuit }
            if (sameSuit.isNotEmpty()) {
                // Try to win the trick
                val winningCards = sameSuit.filter { it.rank.value > trick.cards.maxByOrNull { it.card.rank.value }?.card.rank.value ?: 0 }
                return if (winningCards.isNotEmpty()) winningCards.maxByOrNull { it.rank.value } else sameSuit.maxByOrNull { it.rank.value }
            } else {
                // Void - play highest heart or Q♠ if we have them
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

/** Defensive AI - avoids points at all costs */
class DefensiveAi : AiStrategy {
    private val balanced = BalancedAi()

    override fun choosePass(hand: List<Card>, direction: PassDirection, allPlayers: List<Player>, myPosition: PlayerPosition): List<Card> {
        // Aggressively pass ALL penalty cards
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
            // Lead lowest non-penalty card
            val safe = playable.filter { it.points == 0 }.sortedBy { it.rank.value }
            return safe.firstOrNull() ?: playable.minByOrNull { it.rank.value }
        } else {
            val sameSuit = playable.filter { it.suit == ledSuit }
            if (sameSuit.isNotEmpty()) {
                // Play lowest card that won't win the trick (avoid taking points)
                val currentHighest = trick.cards.maxByOrNull { it.card.rank.value }?.card.rank.value ?: 0
                val losingCards = sameSuit.filter { it.rank.value < currentHighest }.sortedByDescending { it.rank.value }
                if (losingCards.isNotEmpty()) return losingCards.first()
                // Must win - play lowest
                return sameSuit.minByOrNull { it.rank.value }
            } else {
                // Void - dump highest penalty card
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

/** Extension to get AI strategy from personality */
val AiPersonality.strategy: AiStrategy
    get() = when (this) {
        AiPersonality.BALANCED -> BalancedAi()
        AiPersonality.AGGRESSIVE -> AggressiveAi()
        AiPersonality.DEFENSIVE -> DefensiveAi()
    }