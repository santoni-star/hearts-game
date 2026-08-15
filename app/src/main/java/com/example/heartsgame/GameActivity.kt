package com.example.heartsgame

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.heartsgame.game.*
import com.example.heartsgame.ui.AnimationHelper
import com.example.heartsgame.ui.CardView
import com.example.heartsgame.ui.ParticleSystem
import com.example.heartsgame.ui.TableBackgroundView
import android.view.Gravity
import android.graphics.Color
import android.graphics.Typeface

class GameActivity : AppCompatActivity() {

    // Game logic
    private lateinit var game: HeartsGame
    private val handler = Handler(Looper.getMainLooper())
    private var aiRunning = false
    private var animating = false
    private var pendingAnimations = 0

    // UI Components
    private lateinit var tableBackground: TableBackgroundView
    private lateinit var particleSystem: ParticleSystem

    // Top bar
    private lateinit var westName: TextView
    private lateinit var westScore: TextView
    private lateinit var northName: TextView
    private lateinit var northScore: TextView
    private lateinit var eastName: TextView
    private lateinit var eastScore: TextView
    private lateinit var roundInfo: TextView
    private lateinit var passInfo: TextView
    private lateinit var deckCount: TextView
    private lateinit var heartsBrokenText: TextView
    private lateinit var newGameButton: Button

    // Center play area
    private lateinit var centerPlayArea: View
    private lateinit var trickCards: LinearLayout
    private lateinit var trickIndicator: TextView
    private lateinit var passOverlay: LinearLayout
    private lateinit var passTitle: TextView
    private lateinit var passDirectionText: TextView
    private lateinit var passInstruction: TextView
    private lateinit var passConfirmButton: Button
    private lateinit var passRevealArea: LinearLayout
    private lateinit var roundEndOverlay: LinearLayout
    private lateinit var roundEndTitle: TextView
    private lateinit var roundScoresContainer: LinearLayout
    private lateinit var nextRoundButton: Button
    private lateinit var gameOverOverlay: LinearLayout
    private lateinit var gameOverTitle: TextView
    private lateinit var gameOverWinner: TextView
    private lateinit var finalScoresContainer: LinearLayout
    private lateinit var playAgainButton: Button
    private lateinit var moonCelebration: FrameLayout

    // Player hands
    private lateinit var westHand: LinearLayout
    private lateinit var eastHand: LinearLayout
    private lateinit var southHand: LinearLayout
    private lateinit var northHand: LinearLayout
    private lateinit var southHandScroll: HorizontalScrollView

    // South (human) UI
    private lateinit var southName: TextView
    private lateinit var southScore: TextView
    private lateinit var playButton: Button
    private lateinit var passButton: Button
    private lateinit var actionButtons: LinearLayout

    // Card views tracking
    private val southCardViews = mutableListOf<CardView>()
    private val trickCardViews = mutableListOf<CardView>()
    private val westCardViews = mutableListOf<CardView>()
    private val eastCardViews = mutableListOf<CardView>()
    private val northCardViews = mutableListOf<CardView>()

    // Pass phase state
    private var selectedPassCards = mutableListOf<Card>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hearts)

        initViews()
        setupGame()
        setupListeners()
        startNewGame()
    }

    private fun initViews() {
        // Table background
        tableBackground = findViewById(R.id.table_background)
        particleSystem = ParticleSystem(tableBackground)

        // Top bar
        westName = findViewById(R.id.west_name)
        westScore = findViewById(R.id.west_score)
        northName = findViewById(R.id.north_name)
        northScore = findViewById(R.id.north_score)
        eastName = findViewById(R.id.east_name)
        eastScore = findViewById(R.id.east_score)
        roundInfo = findViewById(R.id.round_info)
        passInfo = findViewById(R.id.pass_info)
        deckCount = findViewById(R.id.deck_count)
        heartsBrokenText = findViewById(R.id.hearts_broken)
        newGameButton = findViewById(R.id.new_game_button)

        // Center
        centerPlayArea = findViewById(R.id.center_play_area)
        trickCards = findViewById(R.id.trick_cards)
        trickIndicator = findViewById(R.id.trick_indicator)
        passOverlay = findViewById(R.id.pass_overlay)
        passTitle = findViewById(R.id.pass_title)
        passDirectionText = findViewById(R.id.pass_direction_text)
        passInstruction = findViewById(R.id.pass_instruction)
        passConfirmButton = findViewById(R.id.pass_confirm_button)
        passRevealArea = findViewById(R.id.pass_reveal_area)
        roundEndOverlay = findViewById(R.id.round_end_overlay)
        roundEndTitle = findViewById(R.id.round_end_title)
        roundScoresContainer = findViewById(R.id.round_scores_container)
        nextRoundButton = findViewById(R.id.next_round_button)
        gameOverOverlay = findViewById(R.id.game_over_overlay)
        gameOverTitle = findViewById(R.id.game_over_title)
        gameOverWinner = findViewById(R.id.game_over_winner)
        finalScoresContainer = findViewById(R.id.final_scores_container)
        playAgainButton = findViewById(R.id.play_again_button)
        moonCelebration = findViewById(R.id.moon_celebration)

        // Player hands
        westHand = findViewById(R.id.west_hand)
        eastHand = findViewById(R.id.east_hand)
        southHand = findViewById(R.id.south_hand)
        northHand = findViewById(R.id.north_hand)
        southHandScroll = findViewById(R.id.south_hand_scroll)

        // South UI
        southName = findViewById(R.id.south_name)
        southScore = findViewById(R.id.south_score)
        playButton = findViewById(R.id.play_button)
        passButton = findViewById(R.id.pass_button)
        actionButtons = findViewById(R.id.action_buttons)
    }

    private fun setupGame() {
        game = HeartsGame(
            onGameStateChanged = { gameState ->
                runOnUiThread { updateUI(gameState) }
            },
            onAnimationTrigger = { trigger, data ->
                runOnUiThread { handleAnimationTrigger(trigger, data) }
            }
        )
    }

    private fun setupListeners() {
        newGameButton.setOnClickListener { confirmNewGame() }
        playButton.setOnClickListener { onPlayCard() }
        passButton.setOnClickListener { onPass() }
        passConfirmButton.setOnClickListener { onConfirmPass() }
        nextRoundButton.setOnClickListener { onNextRound() }
        playAgainButton.setOnClickListener { startNewGame() }
    }

    private fun startNewGame() {
        hideAllOverlays()
        game.startGame()
        dealInitialCards()
    }

    private fun confirmNewGame() {
        AlertDialog.Builder(this)
            .setTitle("New Game")
            .setMessage("Start a new game? Current progress will be lost.")
            .setPositiveButton("Yes") { _, _ -> startNewGame() }
            .setNegativeButton("No", null)
            .show()
    }

    // =========================================================================
    // CARD DEALING
    // =========================================================================

    private fun dealInitialCards() {
        val deckX = centerPlayArea.x + centerPlayArea.width / 2f
        val deckY = centerPlayArea.y + centerPlayArea.height / 2f

        startAnim()

        // Deal to all 4 players in rotation
        val allCardViews = mutableListOf<CardView>()
        var delay = 0L

        for (round in 0 until 13) {
            for (pos in 0 until 4) {
                val player = game.players[pos]
                val card = player.hand[round]
                
                val cv = CardView(this).apply {
                    setCard(card, faceUp = (pos == 0)) // Only human sees face up
                    layoutParams = getLayoutParamsForPosition(pos)
                    maxCardWidth = (80 * resources.displayMetrics.density).toInt()
                }

                val targetLayout = getHandLayoutForPosition(pos)
                targetLayout.addView(cv)
                
                // Store reference
                when (pos) {
                    0 -> southCardViews.add(cv)
                    1 -> westCardViews.add(cv)
                    2 -> northCardViews.add(cv)
                    3 -> eastCardViews.add(cv)
                }
                allCardViews.add(cv)

                // Animate deal
                cv.post {
                    val targetX = cv.x
                    val targetY = cv.y
                    AnimationHelper.dealCard(cv, deckX, deckY, targetX, targetY, delay) { endAnim() }
                }
                delay += 40
            }
        }

        // After deal, update UI and handle pass phase or first trick
        handler.postDelayed({
            updateUI(game)
            if (game.phase == GamePhase.PASSING) {
                showPassPhase()
            } else {
                showPlayPhase()
            }
        }, delay + 500)
    }

    private fun getLayoutParamsForPosition(pos: Int): LinearLayout.LayoutParams {
        return when (pos) {
            0 -> LinearLayout.LayoutParams(0, 200, 1f).apply { setMargins(3, 4, 3, 4) } // South - horizontal
            1, 3 -> LinearLayout.LayoutParams(130, 180).apply { setMargins(2, 2, 2, 2) } // West/East - vertical
            else -> LinearLayout.LayoutParams(130, 180).apply { setMargins(2, 2, 2, 2) } // North
        }
    }

    private fun getHandLayoutForPosition(position: PlayerPosition): LinearLayout {
        return when (position) {
            PlayerPosition.SOUTH -> southHand
            PlayerPosition.WEST -> westHand
            PlayerPosition.NORTH -> northHand
            PlayerPosition.EAST -> eastHand
        }
    }

    // =========================================================================
    // PASS PHASE
    // =========================================================================

    private fun showPassPhase() {
        hideAllOverlays()
        passOverlay.visibility = View.VISIBLE
        
        val human = game.players[0]
        passDirectionText.text = when (game.passDirection) {
            PassDirection.LEFT -> "Pass 3 cards LEFT"
            PassDirection.RIGHT -> "Pass 3 cards RIGHT"
            PassDirection.ACROSS -> "Pass 3 cards ACROSS"
            else -> "No passing this round"
        }
        
        passConfirmButton.isEnabled = false
        selectedPassCards.clear()
        updateSouthHandForPassing()
    }

    private fun updateSouthHandForPassing() {
        southHand.removeAllViews()
        southCardViews.clear()
        
        val human = game.players[0]
        for (card in human.hand.sortedBy { it.compareTo(Card(Suit.CLUBS, Rank.TWO)) }) {
            val cv = CardView(this).apply {
                setCard(card, faceUp = true)
                isPlayable = true // All cards playable during pass
                layoutParams = LinearLayout.LayoutParams(0, 200, 1f).apply { setMargins(3, 4, 3, 4) }
                maxCardWidth = (80 * resources.displayMetrics.density).toInt()
            }
            
            cv.setOnClickListener {
                if (!animating) {
                    togglePassSelection(card, cv)
                }
            }
            
            southHand.addView(cv)
            southCardViews.add(cv)
        }
    }

    private fun togglePassSelection(card: Card, cardView: CardView) {
        if (card in selectedPassCards) {
            selectedPassCards.remove(card)
            cardView.cardSelected = false
        } else if (selectedPassCards.size < 3) {
            selectedPassCards.add(card)
            cardView.cardSelected = true
        } else {
            // Already 3 selected, shake
            AnimationHelper.shakeView(cardView)
            return
        }
        AnimationHelper.selectBounce(cardView, cardView.cardSelected)
        passConfirmButton.isEnabled = selectedPassCards.size == 3
    }

    private fun onConfirmPass() {
        if (selectedPassCards.size != 3) return
        
        passOverlay.visibility = View.GONE
        val success = game.humanPassSelected(selectedPassCards.toList())
        if (success) {
            selectedPassCards.clear()
        }
    }

    // Called from game after AI passes
    private fun showPassReveal() {
        passOverlay.visibility = View.GONE
        passRevealArea.visibility = View.VISIBLE
        passRevealArea.removeAllViews()
        
        // Create animated pass cards
        val centerX = centerPlayArea.x + centerPlayArea.width / 2f
        val centerY = centerPlayArea.y + centerPlayArea.height / 2f
        
        // Animate cards passing between players
        animatePassCards()
    }

    private fun animatePassCards() {
        // For each player, animate their passed cards to recipient
        val playerPositions = arrayOf(
            Pair(southHand, PlayerPosition.SOUTH),
            Pair(westHand, PlayerPosition.WEST),
            Pair(northHand, PlayerPosition.NORTH),
            Pair(eastHand, PlayerPosition.EAST)
        )
        
        var totalAnimations = 0
        var completedAnimations = 0
        
        fun checkAllDone() {
            completedAnimations++
            if (completedAnimations >= totalAnimations) {
                // All pass animations done
                handler.postDelayed({
                    passRevealArea.visibility = View.GONE
                    game.finishPassReveal()
                }, 300)
            }
        }
        
        for (i in 0 until 4) {
            val player = game.players[i]
            if (player.passedCards.isEmpty()) continue
            
            val recipientIdx = when (game.passDirection) {
                PassDirection.LEFT -> (i + 1) % 4
                PassDirection.RIGHT -> (i + 3) % 4
                PassDirection.ACROSS -> (i + 2) % 4
                else -> i
            }
            val recipient = game.players[recipientIdx]
            
            val fromLayout = playerPositions[i].first
            val toLayout = playerPositions[recipientIdx].first
            
            val fromX = fromLayout.x + fromLayout.width / 2f
            val fromY = fromLayout.y + fromLayout.height / 2f
            val toX = toLayout.x + toLayout.width / 2f
            val toY = toLayout.y + toLayout.height / 2f
            
            // Create temporary card views for animation
            val animCardViews = mutableListOf<CardView>()
            for (card in player.passedCards) {
                val cv = CardView(this).apply {
                    setCard(card, faceUp = false)
                    layoutParams = LinearLayout.LayoutParams(130, 180)
                    x = fromX - 65f
                    y = fromY - 90f
                }
                passRevealArea.addView(cv)
                animCardViews.add(cv)
            }
            
            totalAnimations += animCardViews.size
            
            // Animate
            AnimationHelper.passCards(
                animCardViews,
                fromX, fromY,
                animCardViews.map { Pair(toX, toY) },
                150
            ) { checkAllDone() }
        }
        
        if (totalAnimations == 0) {
            // No cards to pass (shouldn't happen)
            game.finishPassReveal()
        }
    }

    // =========================================================================
    // PLAY PHASE
    // =========================================================================

    private fun showPlayPhase() {
        hideAllOverlays()
        actionButtons.visibility = View.VISIBLE
        updateUI(game)
    }

    private fun onPlayCard() {
        if (aiRunning || animating) return
        
        val human = game.players[0]
        val selectedCard = human.hand.firstOrNull { card ->
            southCardViews.firstOrNull { it.card == card }?.cardSelected == true
        }
        
        if (selectedCard == null) return
        
        val cardView = southCardViews.first { it.card == selectedCard }
        val success = game.humanPlayCard(selectedCard)
        
        if (success) {
            // Animate card to center
            val centerX = centerPlayArea.x + centerPlayArea.width / 2f
            val centerY = centerPlayArea.y + centerPlayArea.height / 2f
            val trickIndex = game.currentTrick?.cards.size ?: 0
            
            startAnim()
            AnimationHelper.playCardToTrick(cardView, trickIndex, centerX, centerY) { endAnim() }
            
            // Remove from hand immediately
            southHand.removeView(cardView)
            southCardViews.remove(cardView)
        }
    }

    private fun onPass() {
        // In Hearts, "pass" during play means you can't follow suit and must discard
        // But actually in Hearts you MUST follow suit if possible
        // This button might be for a different purpose or not needed
    }

    // =========================================================================
    // AI TURNS
    // =========================================================================

    private fun runAi() {
        if (game.isGameOver || aiRunning) return
        aiRunning = true
        processAiStep()
    }

    private fun processAiStep() {
        if (game.gameOver) {
            aiRunning = false
            updateUI(game)
            return
        }
        
        // Check if it's AI's turn
        val currentPlayer = game.currentTrick?.currentPlayer
        if (currentPlayer == null || currentPlayer == PlayerPosition.SOUTH) {
            // Human's turn
            aiRunning = false
            updateUI(game)
            return
        }
        
        // Show AI thinking
        showAiThinking(currentPlayer)
        
        handler.postDelayed({
            val action = game.executeAiTurn()
            if (action != null) {
                handleAiAction(action)
            } else {
                aiRunning = false
                updateUI(game)
            }
        }, 800)
    }

    private fun showAiThinking(position: PlayerPosition) {
        val layout = when (position) {
            PlayerPosition.WEST -> westHand
            PlayerPosition.NORTH -> northHand
            PlayerPosition.EAST -> eastHand
            else -> return
        }
        AnimationHelper.startAiPulse(layout)
        trickIndicator.text = "${getPlayerName(position)} thinking..."
        trickIndicator.visibility = View.VISIBLE
    }

    private fun handleAiAction(action: AiAction) {
        when (action) {
            is AiAction.PLAYED -> {
                val aiPlayer = game.players[action.player.index]
                val card = action.card
                
                // Find the card view in AI's hand
                val cardViews = when (action.player) {
                    PlayerPosition.WEST -> westCardViews
                    PlayerPosition.NORTH -> northCardViews
                    PlayerPosition.EAST -> eastCardViews
                    else -> mutableListOf<CardView>()
                }
                
                val cardView = cardViews.firstOrNull { it.card == card }
                if (cardView != null) {
                    val centerX = centerPlayArea.x + centerPlayArea.width / 2f
                    val centerY = centerPlayArea.y + centerPlayArea.height / 2f
                    val trickIndex = game.currentTrick?.cards.size!! - 1
                    
                    startAnim()
                    AnimationHelper.playCardToTrick(cardView, trickIndex, centerX, centerY) { 
                        endAnim()
                        // Remove from AI hand
                        when (action.player) {
                            PlayerPosition.WEST -> westHand.removeView(cardView)
                            PlayerPosition.NORTH -> northHand.removeView(cardView)
                            PlayerPosition.EAST -> eastHand.removeView(cardView)
                        }
                        cardViews.remove(cardView)
                        // Add to trick
                        cardView.isInTrick = true
                        cardView.trickOrder = trickIndex + 1
                        trickCards.addView(cardView)
                        trickCardViews.add(cardView)
                    }
                }
            }
        }
    }

    // =========================================================================
    // TRICK END ANIMATION
    // =========================================================================

    private fun animateTrickCollection(winner: PlayerPosition) {
        val winnerLayout = when (winner) {
            PlayerPosition.SOUTH -> southHand
            PlayerPosition.WEST -> westHand
            PlayerPosition.NORTH -> northHand
            PlayerPosition.EAST -> eastHand
        }
        
        startAnim()
        AnimationHelper.collectTrick(
            trickCardViews.toList(),
            winner,
            winnerLayout
        ) { 
            endAnim()
            // Clear trick cards
            trickCardViews.forEach { trickCards.removeView(it) }
            trickCardViews.clear()
            
            // Continue game
            game.finishTrickEnd()
        }
    }

    // =========================================================================
    // ROUND END
    // =========================================================================

    private fun showRoundEnd() {
        hideAllOverlays()
        roundEndOverlay.visibility = View.VISIBLE
        
        roundScoresContainer.removeAllViews()
        
        // Show scores for this round
        for (player in game.players) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            val nameTv = TextView(this).apply {
                text = getPlayerName(player.position)
                textColor = Color.WHITE
                textSize = 16f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val scoreTv = TextView(this).apply {
                text = if (player.roundScore == 26 && game.players.any { it.roundScore == 26 && it != player }) {
                    "SHOT THE MOON! (+26 to others)"
                } else {
                    "${player.roundScore} points"
                }
                textColor = if (player.roundScore == 26) Color.parseColor("#FFD700") else Color.WHITE
                textSize = 16f
                textStyle = if (player.roundScore == 26) Typeface.BOLD else Typeface.NORMAL
                gravity = Gravity.END
            }
            row.addView(nameTv)
            row.addView(scoreTv)
            roundScoresContainer.addView(row)
        }
        
        // Show total scores
        val totalRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, 16, 0, 0) }
        val totalLabel = TextView(this).apply { text = "TOTAL:"; textColor = Color.parseColor("#FFD700"); textSize = 18f; textStyle = Typeface.BOLD; layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) }
        totalRow.addView(totalLabel)
        
        for (player in game.players) {
            val tv = TextView(this).apply {
                text = "${player.score}"
                textColor = if (player == game.players.minByOrNull { it.score }) Color.parseColor("#4CAF50") else Color.WHITE
                textSize = 18f
                textStyle = Typeface.BOLD
                gravity = Gravity.END
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            totalRow.addView(tv)
        }
        roundScoresContainer.addView(totalRow)
    }

    private fun onNextRound() {
        roundEndOverlay.visibility = View.GONE
        game.startNextRound()
        dealInitialCards()
    }

    // =========================================================================
    // GAME OVER
    // =========================================================================

    private fun showGameOver() {
        hideAllOverlays()
        gameOverOverlay.visibility = View.VISIBLE
        
        val winner = game.winner ?: PlayerPosition.SOUTH
        gameOverWinner.text = "Winner: ${getPlayerName(winner)} with ${game.players[winner.index].score} points!"
        gameOverWinner.setTextColor(if (winner == PlayerPosition.SOUTH) Color.parseColor("#4CAF50") else Color.WHITE)
        
        finalScoresContainer.removeAllViews()
        
        // Sort by score
        val sorted = game.players.sortedBy { it.score }
        for ((rank, player) in sorted.withIndex()) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            val rankTv = TextView(this).apply { text = "${rank + 1}."; textColor = Color.WHITE; textSize = 18f; textStyle = Typeface.BOLD; layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) }
            val nameTv = TextView(this).apply { text = getPlayerName(player.position); textColor = Color.WHITE; textSize = 18f; layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 2f) }
            val scoreTv = TextView(this).apply { text = "${player.score}"; textColor = if (rank == 0) Color.parseColor("#4CAF50") else Color.WHITE; textSize = 18f; textStyle = Typeface.BOLD; gravity = Gravity.END }
            row.addView(rankTv)
            row.addView(nameTv)
            row.addView(scoreTv)
            finalScoresContainer.addView(row)
        }
    }

    // =========================================================================
    // MOON CELEBRATION
    // =========================================================================

    private fun showMoonCelebration(winner: PlayerPosition) {
        moonCelebration.visibility = View.VISIBLE
        
        val winnerLayout = when (winner) {
            PlayerPosition.SOUTH -> southHand
            PlayerPosition.WEST -> westHand
            PlayerPosition.NORTH -> northHand
            PlayerPosition.EAST -> eastHand
        }
        
        val centerX = winnerLayout.x + winnerLayout.width / 2f
        val centerY = winnerLayout.y + winnerLayout.height / 2f
        
        AnimationHelper.triggerMoonCelebration(
            particleSystem,
            centerX, centerY,
            width, height
        ) {
            moonCelebration.visibility = View.GONE
        }
    }

    // =========================================================================
    // ANIMATION TRIGGERS
    // =========================================================================

    private fun handleAnimationTrigger(trigger: String, data: Any?) {
        when (trigger) {
            "playCard" -> {
                // Handled in onPlayCard and handleAiAction
            }
            "passCards" -> {
                showPassReveal()
            }
            "collectTrick" -> {
                val map = data as Map<*, *>
                val winner = map["winner"] as PlayerPosition
                animateTrickCollection(winner)
            }
            "heartBurst" -> {
                val position = data as PlayerPosition
                val layout = getHandLayoutForPosition(position.index)
                val centerX = layout.x + layout.width / 2f
                val centerY = layout.y + layout.height / 2f
                particleSystem.burstHearts(centerX, centerY)
            }
            "queenSpadesGlow" -> {
                val position = data as PlayerPosition
                val layout = getHandLayoutForPosition(position.index)
                val centerX = layout.x + layout.width / 2f
                val centerY = layout.y + layout.height / 2f
                particleSystem.burstQueenSpades(centerX, centerY)
            }
            "moonCelebration" -> {
                val winner = data as PlayerPosition
                showMoonCelebration(winner)
            }
            "roundEnd" -> {
                showRoundEnd()
            }
            "gameOver" -> {
                showGameOver()
            }
        }
    }

    // =========================================================================
    // UI UPDATE
    // =========================================================================

    private fun updateUI(gameState: HeartsGame) {
        // Update scores
        westScore.text = gameState.players[1].score.toString()
        northScore.text = gameState.players[2].score.toString()
        eastScore.text = gameState.players[3].score.toString()
        southScore.text = "Total: ${gameState.players[0].score} | Round: ${gameState.players[0].roundScore}"

        // Round info
        roundInfo.text = "Round ${gameState.roundNumber}"

        // Pass direction
        if (gameState.phase == GamePhase.PASSING || gameState.phase == GamePhase.PASS_REVEAL) {
            passInfo.text = when (gameState.passDirection) {
                PassDirection.LEFT -> "Pass LEFT"
                PassDirection.RIGHT -> "Pass RIGHT"
                PassDirection.ACROSS -> "Pass ACROSS"
                else -> "No Pass"
            }
            passInfo.visibility = View.VISIBLE
        } else {
            passInfo.visibility = View.GONE
        }

        // Hearts broken
        heartsBrokenText.text = if (gameState.heartsBroken) "♥ HEARTS BROKEN!" else ""
        heartsBrokenText.visibility = if (gameState.heartsBroken) View.VISIBLE else View.GONE

        // Deck count
        deckCount.text = "Deck: ${gameState.deck.size}"

        // Update south hand (human)
        if (gameState.phase == GamePhase.PLAYING) {
            updateSouthHandForPlay(gameState)
        }

        // Update AI hands (face down, just count)
        updateAiHands(gameState)

        // Update trick cards in center
        updateTrickDisplay(gameState)

        // Update buttons and indicator
        updateActionButtons(gameState)
    }

    private fun updateSouthHandForPlay(gameState: HeartsGame) {
        val human = gameState.players[0]
        
        // Only recreate if needed (to preserve animations)
        if (southCardViews.size != human.hand.size) {
            southHand.removeAllViews()
            southCardViews.clear()
            
            for (card in human.hand.sortedBy { it.compareTo(Card(Suit.CLUBS, Rank.TWO)) }) {
                val cv = CardView(this).apply {
                    setCard(card, faceUp = true)
                    layoutParams = LinearLayout.LayoutParams(0, 200, 1f).apply { setMargins(3, 4, 3, 4) }
                    maxCardWidth = (80 * resources.displayMetrics.density).toInt()
                }
                
                // Determine if playable
                val playable = human.playableCards(gameState.currentTrick)
                cv.isPlayable = card in playable
                
                // Highlight beatable cards when defending
                if (gameState.currentTrick != null && gameState.currentTrick!!.phase == GamePhase.PLAYING) {
                    val lastCard = gameState.currentTrick!!.cards.lastOrNull()?.card
                    if (lastCard != null && gameState.canBeat(card, lastCard)) {
                        cv.canBeat = true
                    }
                }
                
                cv.setOnClickListener {
                    if (!aiRunning && !animating) {
                        selectCard(card, cv, gameState)
                    }
                }
                
                southHand.addView(cv)
                southCardViews.add(cv)
            }
        }
    }

    private fun selectCard(card: Card, cardView: CardView, gameState: HeartsGame) {
        // Clear previous selection
        southCardViews.forEach { cv ->
            if (cv.cardSelected) {
                cv.cardSelected = false
                AnimationHelper.selectBounce(cv, false)
            }
        }
        
        // Check if playable
        val human = gameState.players[0]
        val playable = human.playableCards(gameState.currentTrick)
        
        if (card in playable) {
            cardView.cardSelected = true
            AnimationHelper.selectBounce(cardView, true)
            playButton.isEnabled = true
            playButton.text = "PLAY ${card}"
        } else {
            AnimationHelper.shakeView(cardView)
            playButton.isEnabled = false
            playButton.text = "PLAY"
        }
    }

    private fun updateAiHands(gameState: HeartsGame) {
        // West
        updateAiHand(westHand, westCardViews, gameState.players[1].hand.size)
        // North
        updateAiHand(northHand, northCardViews, gameState.players[2].hand.size)
        // East
        updateAiHand(eastHand, eastCardViews, gameState.players[3].hand.size)
    }

    private fun updateAiHand(layout: LinearLayout, cardViews: MutableList<CardView>, targetCount: Int) {
        if (cardViews.size == targetCount) return
        
        layout.removeAllViews()
        cardViews.clear()
        
        for (i in 0 until targetCount) {
            val cv = CardView(this).apply {
                setCard(null, faceUp = false)
                layoutParams = LinearLayout.LayoutParams(130, 180).apply { setMargins(2, 2, 2, 2) }
            }
            layout.addView(cv)
            cardViews.add(cv)
        }
    }

    private fun updateTrickDisplay(gameState: HeartsGame) {
        // Trick cards are added via animations, just update indicator
        if (gameState.currentTrick != null) {
            val trick = gameState.currentTrick!!
            val currentPlayer = trick.currentPlayer
            
            if (currentPlayer == PlayerPosition.SOUTH) {
                trickIndicator.text = "Your turn - ${getPlayHint(gameState)}"
                trickIndicator.visibility = View.VISIBLE
            } else {
                trickIndicator.text = "${getPlayerName(currentPlayer)}'s turn"
                trickIndicator.visibility = View.VISIBLE
            }
        } else {
            trickIndicator.visibility = View.GONE
        }
    }

    private fun getPlayHint(gameState: HeartsGame): String {
        val human = gameState.players[0]
        val trick = gameState.currentTrick!!
        
        if (trick.cards.isEmpty()) {
            if (gameState.firstTrickOfRound) return "Must play 2♣"
            if (!gameState.heartsBroken) return "Choose a card (no hearts)"
            return "Choose any card"
        } else {
            val ledSuit = trick.suitLed!!
            val sameSuit = human.hand.filter { it.suit == ledSuit }
            if (sameSuit.isNotEmpty()) return "Must follow ${ledSuit.symbol}"
            return "Void in ${ledSuit.symbol} - play any card"
        }
    }

    private fun updateActionButtons(gameState: HeartsGame) {
        val human = gameState.players[0]
        val hasSelection = southCardViews.any { it.cardSelected }
        
        if (gameState.phase == GamePhase.PLAYING && gameState.currentTrick?.currentPlayer == PlayerPosition.SOUTH) {
            actionButtons.visibility = View.VISIBLE
            playButton.isEnabled = hasSelection
            playButton.text = if (hasSelection) {
                val selectedCard = southCardViews.first { it.cardSelected }.card
                "PLAY $selectedCard"
            } else "PLAY"
            passButton.visibility = View.GONE
        } else {
            actionButtons.visibility = View.GONE
        }
    }

    // =========================================================================
    // HELPERS
    // =========================================================================

    private fun getPlayerName(position: PlayerPosition): String {
        return when (position) {
            PlayerPosition.SOUTH -> "You"
            PlayerPosition.WEST -> "West"
            PlayerPosition.NORTH -> "North"
            PlayerPosition.EAST -> "East"
        }
    }

    private fun hideAllOverlays() {
        passOverlay.visibility = View.GONE
        passRevealArea.visibility = View.GONE
        roundEndOverlay.visibility = View.GONE
        gameOverOverlay.visibility = View.GONE
        moonCelebration.visibility = View.GONE
        trickIndicator.visibility = View.GONE
    }

    private fun startAnim() {
        animating = true
        pendingAnimations++
        actionButtons.visibility = View.GONE
    }

    private fun endAnim() {
        pendingAnimations--
        if (pendingAnimations <= 0) {
            animating = false
            pendingAnimations = 0
            updateActionButtons(game)
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        particleSystem.stop()
        super.onDestroy()
    }
}