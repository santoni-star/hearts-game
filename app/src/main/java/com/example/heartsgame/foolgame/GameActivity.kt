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

class GameActivity : AppCompatActivity() {

    private lateinit var game: FoolGame
    private lateinit var playerHandLayout: LinearLayout
    private lateinit var tableLayout: LinearLayout
    private lateinit var aiHandLayout: LinearLayout
    private lateinit var trumpText: TextView
    private lateinit var statusText: TextView
    private lateinit var deckCountText: TextView
    private lateinit var playButton: Button
    private lateinit var passButton: Button
    private lateinit var takeButton: Button
    private lateinit var infoText: TextView

    private val cardViews = mutableListOf<CardView>()
    private val handler = Handler(Looper.getMainLooper())
    private var aiRunning = false
    private var animating = false
    private var pendingAnimations = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fool)

        playerHandLayout = findViewById(R.id.player_hand)
        tableLayout = findViewById(R.id.table_area)
        aiHandLayout = findViewById(R.id.ai_hand)
        trumpText = findViewById(R.id.trump_text)
        statusText = findViewById(R.id.status_text)
        deckCountText = findViewById(R.id.deck_count)
        playButton = findViewById(R.id.play_button)
        passButton = findViewById(R.id.pass_button)
        takeButton = findViewById(R.id.take_button)
        infoText = findViewById(R.id.info_text)

        game = FoolGame()
        game.startGame()

        playButton.setOnClickListener { onPlay() }
        passButton.setOnClickListener { onPass() }
        takeButton.setOnClickListener { onTake() }
        findViewById<Button>(R.id.new_game_button).setOnClickListener { newGame() }

        // Initial deal animation
        dealInitialCards()
    }

    private fun dealInitialCards() {
        // Deck position (center top-ish)
        val deckX = playerHandLayout.width / 2f
        val deckY = 100f * resources.displayMetrics.density

        // Player gets 6 cards, AI gets 6 cards - alternate
        var delay = 0L
        val playerCards = game.playerHand.sortedBy { it.rank.value }
        val aiCards = game.aiHand

        for (i in 0 until 6) {
            // Player card
            if (i < playerCards.size) {
                val cv = CardView(this)
                cv.card = playerCards[i]
                cv.isFaceUp = true
                cv.layoutParams = LinearLayout.LayoutParams(0, 200, 1f).apply { setMargins(3, 4, 3, 4) }
                cv.maxCardWidth = (80 * resources.displayMetrics.density).toInt()
                playerHandLayout.addView(cv)
                cardViews.add(cv)

                // Schedule animation after layout
                cv.post {
                    val targetX = cv.x
                    val targetY = cv.y
                    AnimationHelper.dealCard(cv, deckX, deckY, targetX, targetY, delay) { endAnim() }
                }
                delay += 80
            }

            // AI card (face down)
            if (i < aiCards.size) {
                val cv = CardView(this).apply {
                    isFaceUp = false
                    layoutParams = LinearLayout.LayoutParams(0, 200, 1f).apply { setMargins(2, 4, 2, 4) }
                }
                aiHandLayout.addView(cv)

                cv.post {
                    val targetX = cv.x
                    val targetY = cv.y
                    AnimationHelper.dealCard(cv, deckX, deckY, targetX, targetY, delay) { endAnim() }
                }
                delay += 80
            }
        }

        startAnim() // Will be ended by the last callback
        // After all dealt, update UI fully
        handler.postDelayed({ updateUI() }, delay + 500)
    }

    // --- Player action handlers ---

    private fun onPlay() {
        if (aiRunning || animating) return
        when {
            // Attacking (first card or throw)
            game.phase == GamePhase.ATTACK_TURN && game.attacker == AttackRole.PLAYER_ATTACKER -> {
                if (game.selectedCard == null) return
                val selectedCard = game.selectedCard!!
                val cardView = cardViews.find { it.card == selectedCard } ?: return
                val tableX = tableLayout.x + tableLayout.width / 2f
                val tableY = tableLayout.y + tableLayout.height / 2f

                startAnim()
                AnimationHelper.playCard(cardView, cardView.x, cardView.y, tableX, tableY) {
                    if (game.playerPlaySelected()) {
                        endAnim()
                        updateUI()
                        if (game.isGameOver) {
                            showResult()
                        } else {
                            // Must be DEFENSE_TURN now — AI defends
                            AnimationHelper.startAiPulse(aiHandLayout)
                            handler.postDelayed({ runAi() }, 500)
                        }
                    } else {
                        endAnim()
                        setButtonsEnabled(true)
                    }
                }
            }

            // Defending (beating a card)
            game.phase == GamePhase.DEFENSE_TURN && game.attacker == AttackRole.AI_ATTACKER -> {
                if (game.selectedCard == null) return
                val selectedCard = game.selectedCard!!
                val cardView = cardViews.find { it.card == selectedCard } ?: return
                val attackPair = tableLayout.getChildAt(tableLayout.childCount - 1) as? android.widget.LinearLayout
                val attackView = attackPair?.getChildAt(0) as? CardView ?: return

                startAnim()
                AnimationHelper.beatCard(cardView, attackView) {
                    if (game.playerDefend(selectedCard)) {
                        endAnim()
                        updateUI()
                        if (game.isGameOver) {
                            showResult()
                        } else if (game.phase == GamePhase.ATTACK_TURN && game.attacker == AttackRole.PLAYER_ATTACKER) {
                            // Player defended successfully → player becomes attacker
                            setButtonsEnabled(true)
                        } else if (game.phase == GamePhase.ATTACK_TURN && game.attacker == AttackRole.AI_ATTACKER) {
                            // AI can throw more — run AI
                            AnimationHelper.startAiPulse(aiHandLayout)
                            handler.postDelayed({ runAi() }, 500)
                        } else if (game.phase == GamePhase.DEFENSE_TURN) {
                            AnimationHelper.startAiPulse(aiHandLayout)
                            handler.postDelayed({ runAi() }, 500)
                        }
                    } else {
                        endAnim()
                        setButtonsEnabled(true)
                    }
                }
            }
        }
    }

    private fun onPass() {
        if (aiRunning || animating) return
        if (game.phase == GamePhase.ATTACK_TURN && game.attacker == AttackRole.PLAYER_ATTACKER) {
            setButtonsEnabled(false)
            game.playerPassThrow()
            updateUI()
            if (game.isGameOver) {
                showResult()
            } else {
                // Roles switched — should be AI_ATTACKER now
                AnimationHelper.startAiPulse(aiHandLayout)
                handler.postDelayed({ runAi() }, 500)
            }
        }
    }

    private fun onTake() {
        if (aiRunning || animating) return
        if (game.phase == GamePhase.DEFENSE_TURN && game.attacker == AttackRole.AI_ATTACKER) {
            setButtonsEnabled(false)
            
            // Animate taking cards
            val tableCardViews = mutableListOf<CardView>()
            for (i in 0 until tableLayout.childCount) {
                val pair = tableLayout.getChildAt(i) as? android.widget.LinearLayout
                pair?.let {
                    val atk = it.getChildAt(0) as? CardView
                    val def = it.getChildAt(1) as? CardView
                    atk?.let { tableCardViews.add(it) }
                    def?.let { tableCardViews.add(it) }
                }
            }
            
            val targetX = playerHandLayout.x + playerHandLayout.width / 2f
            val targetY = playerHandLayout.y + playerHandLayout.height / 2f
            
            startAnim()
            AnimationHelper.takeCards(tableCardViews, targetX, targetY, 120) {
                game.playerTakeCards()
                endAnim()
                updateUI()
                if (game.isGameOver) {
                    showResult()
                } else {
                    // Same attacker (AI) will attack again — run AI
                    AnimationHelper.startAiPulse(aiHandLayout)
                    handler.postDelayed({ runAi() }, 500)
                }
            }
        }
    }

    // --- AI ---

    private fun runAi() {
        if (game.isGameOver || aiRunning) return
        aiRunning = true
        processAiStep()
    }

    private fun processAiStep() {
        if (game.isGameOver) {
            AnimationHelper.stopAiPulse(aiHandLayout)
            updateUI(); showResult(); aiRunning = false; return
        }

        val shouldAct = game.phase == GamePhase.ATTACK_TURN && game.attacker == AttackRole.AI_ATTACKER ||
                        game.phase == GamePhase.DEFENSE_TURN && game.attacker == AttackRole.PLAYER_ATTACKER

        if (!shouldAct) {
            // Player's turn — stop AI and show controls
            AnimationHelper.stopAiPulse(aiHandLayout)
            updateUI()
            aiRunning = false
            return
        }

        val action = game.executeAiTurn()
        
        // Animate AI action based on type
        when (action) {
            is AiAction.ATTACKED -> {
                statusText.text = "AI attacks with ${action.card}"
                animateAiAttack(action.card)
            }
            is AiAction.DEFENDED -> {
                statusText.text = "AI defends with ${action.card}"
                animateAiDefend(action.card)
            }
            is AiAction.THROWN -> {
                statusText.text = "AI throws ${action.card}"
                animateAiThrow(action.card)
            }
            is AiAction.THROW_PASS -> {
                statusText.text = "AI passes"
                animateAiPass()
            }
            is AiAction.TOOK_CARDS -> {
                statusText.text = "AI takes cards!"
                animateAiTake()
            }
            is AiAction.NOTHING -> {
                // Shouldn't happen, but fallback
                updateUI()
                handler.postDelayed({ processAiStep() }, 900)
            }
        }
    }

    // --- UI update ---

    private fun updateUI() {
        deckCountText.text = "Deck: ${game.deck.size}"
        trumpText.text = "${game.trump.symbol} ${game.trump.name.lowercase()}"

        updatePlayerHand()
        updateTable()
        updateAiHand()
        updateStatusAndButtons()
    }

    private fun updatePlayerHand() {
        playerHandLayout.removeAllViews()
        cardViews.clear()
        game.clearSelection()

        for (card in game.playerHand.sortedBy { it.rank.value }) {
            val cv = CardView(this)
            cv.card = card
            cv.isFaceUp = true
            cv.isPlayable = game.isPlayable(card)

            if (game.phase == GamePhase.DEFENSE_TURN && game.attacker == AttackRole.AI_ATTACKER) {
                val last = game.tableCards.lastOrNull { it.second == null }?.first
                if (last != null && game.canBeat(card, last)) cv.canBeat = true
            }

            cv.layoutParams = LinearLayout.LayoutParams(0, 200, 1f).apply {
                setMargins(3, 4, 3, 4)
            }
            cv.maxCardWidth = (80 * resources.displayMetrics.density).toInt()

            cv.setOnClickListener {
                if (!aiRunning && (
                        game.phase == GamePhase.ATTACK_TURN && game.attacker == AttackRole.PLAYER_ATTACKER ||
                        game.phase == GamePhase.DEFENSE_TURN && game.attacker == AttackRole.AI_ATTACKER
                )) {
                    game.selectCard(card)
                    updateCardSelections()
                    updateButtons()
                }
            }

            playerHandLayout.addView(cv)
            cardViews.add(cv)
        }
        updateCardSelections()
    }

    private fun updateCardSelections() {
        val sel = game.selectedCard
        for (cv in cardViews) {
            val wasSelected = cv.cardSelected
            cv.cardSelected = cv.card == sel
            if (cv.cardSelected != wasSelected) {
                AnimationHelper.selectBounce(cv, cv.cardSelected)
            }
            cv.postInvalidate()
        }
    }

    private fun updateTable() {
        tableLayout.removeAllViews()
        for ((attack, defense) in game.tableCards) {
            val pair = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            val atk = CardView(this).apply {
                card = attack; isFaceUp = true
                layoutParams = LinearLayout.LayoutParams(130, 180).apply { setMargins(3, 4, 3, 4) }
            }
            pair.addView(atk)
            if (defense != null) {
                val def = CardView(this).apply {
                    card = defense; isFaceUp = true
                    layoutParams = LinearLayout.LayoutParams(130, 180).apply { setMargins(3, 4, 3, 4) }
                }
                pair.addView(def)
            } else {
                val placeholder = CardView(this).apply {
                    isFaceUp = false
                    layoutParams = LinearLayout.LayoutParams(130, 180).apply { setMargins(3, 4, 3, 4) }
                }
                pair.addView(placeholder)
            }
            tableLayout.addView(pair)
        }
    }

    private fun updateAiHand() {
        aiHandLayout.removeAllViews()
        for (i in game.aiHand.indices) {
            val cv = CardView(this).apply {
                isFaceUp = false
                layoutParams = LinearLayout.LayoutParams(0, 200, 1f).apply { setMargins(2, 4, 2, 4) }
            }
            aiHandLayout.addView(cv)
        }
    }

    private fun updateStatusAndButtons() {
        if (game.isGameOver) {
            statusText.text = when (game.result) {
                GameResult.PLAYER_WINS -> "You win! "
                GameResult.PLAYER_LOSES -> "You lose! "
                null -> "Draw!"
            }
            playButton.visibility = View.GONE
            passButton.visibility = View.GONE
            takeButton.visibility = View.GONE
            infoText.text = "Tap New to play again"
            return
        }

        when {
            // Player attacks (first card or throw)
            game.phase == GamePhase.ATTACK_TURN && game.attacker == AttackRole.PLAYER_ATTACKER -> {
                if (game.isFirstAttack) {
                    statusText.text = "Your attack \u2191 (any card)"
                    playButton.text = "Attack"
                    passButton.visibility = View.GONE
                    infoText.text = "Choose a card and Attack"
                } else {
                    statusText.text = "Your throw \u2191 (matching rank)"
                    playButton.text = "Throw"
                    passButton.visibility = View.VISIBLE
                    infoText.text = "Throw a matching card or Pass"
                }
                playButton.visibility = View.VISIBLE
                takeButton.visibility = View.GONE
            }

            // Player defends
            game.phase == GamePhase.DEFENSE_TURN && game.attacker == AttackRole.AI_ATTACKER -> {
                statusText.text = "Defend! Beat the AI's card"
                playButton.text = "Beat"
                playButton.visibility = View.VISIBLE
                passButton.visibility = View.GONE
                takeButton.visibility = View.VISIBLE
                infoText.text = "Beat the card or Take"
            }

            // AI's turn
            else -> {
                statusText.text = "AI thinking..."
                playButton.visibility = View.GONE
                passButton.visibility = View.GONE
                takeButton.visibility = View.GONE
                infoText.text = ""
            }
        }
        updateButtons()
    }

    private fun updateButtons() {
        val canPlay = when {
            game.phase == GamePhase.ATTACK_TURN && game.attacker == AttackRole.PLAYER_ATTACKER ->
                game.selectedCard != null && game.isPlayable(game.selectedCard!!)
            game.phase == GamePhase.DEFENSE_TURN && game.attacker == AttackRole.AI_ATTACKER -> {
                game.selectedCard != null && (game.tableCards.lastOrNull { it.second == null }?.first?.let {
                    game.canBeat(game.selectedCard!!, it)
                } ?: false)
            }
            else -> false
        }
        playButton.isEnabled = canPlay && !aiRunning
        passButton.isEnabled = game.isThrowingPhase && !aiRunning && !game.isFirstAttack
        takeButton.isEnabled = game.phase == GamePhase.DEFENSE_TURN && game.attacker == AttackRole.AI_ATTACKER && !aiRunning
    }

    private fun setButtonsEnabled(enabled: Boolean) {
        playButton.isEnabled = enabled && game.selectedCard != null
        passButton.isEnabled = game.isThrowingPhase && enabled && !game.isFirstAttack
        takeButton.isEnabled = game.phase == GamePhase.DEFENSE_TURN && game.attacker == AttackRole.AI_ATTACKER && enabled
    }

    // --- Dialogs ---

    private fun showResult() {
        val msg = when (game.result) {
            GameResult.PLAYER_WINS -> "You won! AI is the fool \uD83D\uDE06"
            GameResult.PLAYER_LOSES -> "You lost! You are the fool \uD83D\uDE22"
            null -> "Draw!"
        }
        AlertDialog.Builder(this)
            .setTitle("Game Over")
            .setMessage(msg)
            .setPositiveButton("New Game") { _, _ -> game.startGame(); updateUI() }
            .setNegativeButton("Exit") { _, _ -> finish() }
            .setCancelable(false)
            .show()
    }

    private fun newGame() {
        AlertDialog.Builder(this)
            .setTitle("New Game")
            .setMessage("Start a new game?")
            .setPositiveButton("Yes") { _, _ -> game.startGame(); updateUI() }
            .setNegativeButton("No", null)
            .show()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        AnimationHelper.stopAiPulse(aiHandLayout)
        super.onDestroy()
    }

    // --- Animation management ---

    private fun startAnim() {
        animating = true
        pendingAnimations++
        setButtonsEnabled(false)
    }

    private fun endAnim() {
        pendingAnimations--
        if (pendingAnimations <= 0) {
            animating = false
            pendingAnimations = 0
            updateButtons()
        }
    }

    private fun waitForAnimations(callback: () -> Unit) {
        if (!animating) {
            callback()
        } else {
            handler.postDelayed({ waitForAnimations(callback) }, 50)
        }
    }

    // --- Animation callbacks ---

    private fun animateAiAttack(card: com.example.heartsgame.game.Card) {
        // Find the AI card view that matches (it's face down, so we animate the first AI card)
        val aiCardView = aiHandLayout.getChildAt(0) as? CardView ?: run { updateUI(); nextAiStep(); return }
        val tableX = tableLayout.x + tableLayout.width / 2f
        val tableY = tableLayout.y + tableLayout.height / 2f

        startAnim()
        AnimationHelper.playCard(aiCardView, aiCardView.x, aiCardView.y, tableX, tableY) {
            endAnim()
            updateUI()
            nextAiStep()
        }
    }

    private fun animateAiDefend(card: com.example.heartsgame.game.Card) {
        val aiCardView = aiHandLayout.getChildAt(0) as? CardView ?: run { updateUI(); nextAiStep(); return }
        val attackPair = tableLayout.getChildAt(tableLayout.childCount - 1) as? android.widget.LinearLayout
        val attackView = attackPair?.getChildAt(0) as? CardView ?: run { updateUI(); nextAiStep(); return }

        startAnim()
        AnimationHelper.beatCard(aiCardView, attackView) {
            endAnim()
            updateUI()
            nextAiStep()
        }
    }

    private fun animateAiThrow(card: com.example.heartsgame.game.Card) {
        val aiCardView = aiHandLayout.getChildAt(0) as? CardView ?: run { updateUI(); nextAiStep(); return }
        val tableX = tableLayout.x + tableLayout.width / 2f
        val tableY = tableLayout.y + tableLayout.height / 2f

        startAnim()
        AnimationHelper.playCard(aiCardView, aiCardView.x, aiCardView.y, tableX, tableY) {
            endAnim()
            updateUI()
            nextAiStep()
        }
    }

    private fun animateAiPass() {
        updateUI()
        nextAiStep()
    }

    private fun animateAiTake() {
        val tableCardViews = mutableListOf<CardView>()
        for (i in 0 until tableLayout.childCount) {
            val pair = tableLayout.getChildAt(i) as? android.widget.LinearLayout
            pair?.let {
                val atk = it.getChildAt(0) as? CardView
                val def = it.getChildAt(1) as? CardView
                atk?.let { tableCardViews.add(it) }
                def?.let { tableCardViews.add(it) }
            }
        }

        val targetX = aiHandLayout.x + aiHandLayout.width / 2f
        val targetY = aiHandLayout.y + aiHandLayout.height / 2f

        startAnim()
        AnimationHelper.takeCards(tableCardViews, targetX, targetY, 120) {
            endAnim()
            updateUI()
            nextAiStep()
        }
    }

    private fun nextAiStep() {
        if (game.isGameOver) {
            runOnUiThread { updateUI(); showResult(); aiRunning = false }
            return
        }
        handler.postDelayed({ processAiStep() }, 900)
    }
}
