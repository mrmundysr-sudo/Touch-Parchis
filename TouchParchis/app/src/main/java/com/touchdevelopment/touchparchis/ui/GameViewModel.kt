package com.touchdevelopment.touchparchis.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.touchdevelopment.touchparchis.R
import com.touchdevelopment.touchparchis.ai.RulesAI
import com.touchdevelopment.touchparchis.data.BoardCoords
import com.touchdevelopment.touchparchis.engine.GameEngine
import com.touchdevelopment.touchparchis.engine.GamePhase
import com.touchdevelopment.touchparchis.engine.MoveAction
import com.touchdevelopment.touchparchis.engine.ParchisColor
import com.touchdevelopment.touchparchis.engine.ReelSlot
import com.touchdevelopment.touchparchis.engine.RandomSource
import com.touchdevelopment.touchparchis.engine.RouteIndex
import com.touchdevelopment.touchparchis.engine.RulesData

/** A message to show, as a string resource plus an optional color argument. */
data class UiMessage(val resId: Int, val color: ParchisColor? = null)

enum class Screen { SPLASH, GAMEPLAY, WIN, LOSS }

/** Reel phase for the vertical scroll animation. */
enum class ReelPhase { IDLE, SPINNING, SETTLING }

/** One reel's display state. */
data class ReelViewState(
    val value: Int = 0,
    val phase: ReelPhase = ReelPhase.IDLE,
    val dimmed: Boolean = false,
    val highlighted: Boolean = false
)

/** A short-lived pawn animation overlay. */
sealed class Anim {
    abstract val color: ParchisColor
    abstract val pawnId: Int
    abstract val fromCx: Float
    abstract val fromCy: Float
    abstract val toCx: Float
    abstract val toCy: Float
    abstract val startMs: Long
    abstract val durationMs: Long

    val key: Pair<ParchisColor, Int> get() = color to pawnId

    data class Move(
        override val color: ParchisColor, override val pawnId: Int,
        override val fromCx: Float, override val fromCy: Float,
        override val toCx: Float, override val toCy: Float,
        override val startMs: Long, override val durationMs: Long
    ) : Anim()

    data class Capture(
        override val color: ParchisColor, override val pawnId: Int,
        override val fromCx: Float, override val fromCy: Float,
        override val toCx: Float, override val toCy: Float,
        override val startMs: Long, override val durationMs: Long
    ) : Anim()
}

/** The human's selected pawn and the legal ways to play it. */
data class PawnChoices(
    val color: ParchisColor,
    val pawnId: Int,
    /** destination route index -> combined (both reels) */
    val destinations: Map<Int, Boolean>,
    val canUseR1: Boolean,
    val canUseR2: Boolean
)

/**
 * Owns all UI state and drives the engine through a single frame tick.
 *
 * The UI never re-implements a rule: it asks [humanChoices] what is legal and calls
 * [humanSelectPawn] / [humanPlay] to act. Positions come from [PawnPositioner].
 */
class GameViewModel(
    val rules: RulesData,
    val coords: BoardCoords,
    private val rng: RandomSource,
    private val clock: () -> Long = { System.nanoTime() / 1_000_000L }
) {
    var screen by mutableStateOf(Screen.SPLASH)
        private set

    var selectedOpponents by mutableStateOf(0)
        private set

    /** True after PLAY is tapped with no opponent selected; drives the prompt. */
    var showChoosePrompt by mutableStateOf(false)
        private set

    var splashPulse by mutableStateOf(false)
        private set

    var engine: GameEngine? = null
        private set

    var reel1 by mutableStateOf(ReelViewState())
        private set
    var reel2 by mutableStateOf(ReelViewState())
        private set

    var message by mutableStateOf<UiMessage?>(null)
        private set

    var openingTotals by mutableStateOf<Map<ParchisColor, Int>>(emptyMap())
        private set

    /** Most recent two-number roll retained for every player display. */
    var lastRolls by mutableStateOf<Map<ParchisColor, Pair<Int, Int>>>(emptyMap())
        private set

    var animations by mutableStateOf<List<Anim>>(emptyList())
        private set

    var choices by mutableStateOf<PawnChoices?>(null)
        private set

    var resultAtMs by mutableStateOf<Long?>(null)
        private set

    private val ai = RulesAI(rng)

    /** Human cannot spin while this is true (animation, AI turn, unresolved roll). */
    private var spinLocked = true

    /** Earliest time the AI may act again. */
    private var aiActAtMs = 0L

    private var nextAiDelay = 700L

    private companion object {
        const val REEL_DECEL_MS = 450L
        const val REEL_BOUNCE_MS = 150L
    }

    // Reel animation timeline.
    private var reelStartAt = 0L
    private var reel1StopAt = 0L
    private var reel2StopAt = 0L
    private var reelSpinnerColor = ParchisColor.RED
    var doublesPulseAtMs by mutableStateOf(0L)
        private set
    private var reelsActive = false

    // Opening-spin sequencing.
    private var openingInProgress = false
    private var openingSettling = false
    private var openingStartPendingAt = 0L

    val humanColor: ParchisColor get() = rules.humanColor
    val activeSeats: List<ParchisColor> get() = engine?.activeSeats ?: emptyList()

    // ---- Splash -------------------------------------------------------------

    fun selectOpponents(n: Int) {
        selectedOpponents = n
        showChoosePrompt = false
        splashPulse = false
    }

    fun play() {
        if (selectedOpponents !in 1..3) {
            showChoosePrompt = true
            splashPulse = true
            return
        }
        startGame(selectedOpponents)
    }

    /** Called by the UI once the splash prompt pulse animation has finished. */
    fun clearSplashPulse() {
        splashPulse = false
    }

    private fun startGame(opponents: Int) {
        engine = GameEngine(rules, rng, rules.activeSeats(opponents))
        openingTotals = emptyMap()
        lastRolls = emptyMap()
        animations = emptyList()
        choices = null
        reel1 = ReelViewState()
        reel2 = ReelViewState()
        resultAtMs = null
        openingInProgress = true
        openingSettling = false
        openingStartPendingAt = 0
        reelsActive = false
        screen = Screen.GAMEPLAY
        message = UiMessage(R.string.opening_spin_caption)
        spinLocked = true
        aiActAtMs = clock() + 500
    }

    private fun scheduleResult() {
        resultAtMs = clock() + 1000
        message = null
    }

    // ---- Frame tick ---------------------------------------------------------

    /** Advances all timers, reel animation, and AI behavior. Call once per frame. */
    fun tick(now: Long) {
        tickReels(now)

        if (animations.isNotEmpty()) {
            animations = animations.filter { now < it.startMs + it.durationMs }
        }

        val at = resultAtMs
        if (at != null && now >= at) {
            val winner = engine?.winner
            if (winner != null) {
                resultAtMs = null
                screen = if (winner == humanColor) Screen.WIN else Screen.LOSS
            }
        }

        val e = engine ?: return

        if (openingInProgress) {
            if (openingSettling) return
            if (openingStartPendingAt != 0L) {
                if (now >= openingStartPendingAt) {
                    openingStartPendingAt = 0
                    if (e.phase == GamePhase.OPENING_SPIN) {
                        if (e.nextOpeningSpinner() == humanColor) beginOpeningHuman()
                    } else {
                        openingInProgress = false
                        beginNormalTurns()
                    }
                }
                return
            }
            val next = e.nextOpeningSpinner()
            if (next == humanColor) {
                if (spinLocked) beginOpeningHuman()
            } else if (now >= aiActAtMs) {
                performOpeningSpin()
                aiActAtMs = now + 750
            }
            return
        }

        if (e.phase == GamePhase.GAME_OVER) return
        if (e.currentColor == humanColor) return

        when (e.phase) {
            GamePhase.AWAIT_SPIN -> if (now >= aiActAtMs) {
                doSpin()
                // Leave time for the reels to settle and the player to read the result.
                aiActAtMs = now + 2200L
                nextAiDelay = 900 + (rng.nextFloat() * 500).toLong()
            }
            GamePhase.AWAIT_MOVE -> if (now >= aiActAtMs) {
                aiStep()
                aiActAtMs = now + 800
            }
            else -> {}
        }
    }

    private fun performOpeningSpin() {
        val e = engine ?: return
        val spinner = e.nextOpeningSpinner()
        val res = e.openingSpin()
        lastRolls = lastRolls + (spinner to (res.r1 to res.r2))
        openingTotals = e.openingTotals.toMap()
        startReelAnimation(res.r1, res.r2, spinner)
        openingSettling = true
        if (res.starterDecided) message = UiMessage(R.string.starts, res.color)
    }

    private fun beginOpeningHuman() {
        message = UiMessage(R.string.your_turn_spin)
        spinLocked = false
    }

    /** Begins normal turns after the opening spin decided the starter. */
    private fun beginNormalTurns() {
        val e = engine ?: return
        choices = null
        reel1 = ReelViewState(value = e.reel1)
        reel2 = ReelViewState(value = e.reel2)
        message = turnMessage(e.currentColor, R.string.your_turn_spin)
        spinLocked = e.currentColor != humanColor
        aiActAtMs = clock() + nextAiDelay
    }

    // ---- Spin ---------------------------------------------------------------

    /** True when the human may press SPIN (their opening spin, or a normal turn spin). */
    fun isSpinEnabled(): Boolean {
        val e = engine ?: return false
        if (screen != Screen.GAMEPLAY || openingSettling || reelsActive || spinLocked) return false
        if (openingInProgress) return e.phase == GamePhase.OPENING_SPIN && e.nextOpeningSpinner() == humanColor
        return e.phase == GamePhase.AWAIT_SPIN && e.currentColor == humanColor
    }

    fun humanSpin() {
        if (!isSpinEnabled()) return
        if (openingInProgress) {
            spinLocked = true
            performOpeningSpin()
        } else {
            doSpin()
        }
    }

    private fun doSpin() {
        val e = engine ?: return
        val result = e.spin()
        lastRolls = lastRolls + (e.currentColor to (result.r1 to result.r2))
        startReelAnimation(result.r1, result.r2, e.currentColor)
        choices = null
        spinLocked = true
        message = turnMessage(e.currentColor, R.string.your_turn_choose)
    }

    /** Sets predetermined outcomes first, then animates only their visual reel strips. */
    private fun startReelAnimation(r1: Int, r2: Int, spinner: ParchisColor) {
        val now = clock()
        reelStartAt = now
        reelSpinnerColor = spinner
        val jitter = Math.floorMod(now, 201L) - 100L
        reel1StopAt = now + 1200L + jitter
        reel2StopAt = now + 1900L + jitter
        reelsActive = true
        reel1 = ReelViewState(value = r1, phase = ReelPhase.SPINNING)
        reel2 = ReelViewState(value = r2, phase = ReelPhase.SPINNING)
    }

    private fun tickReels(now: Long) {
        if (!reelsActive) return

        if (reel1.phase == ReelPhase.SPINNING && now >= reel1StopAt - REEL_DECEL_MS) {
            reel1 = reel1.copy(phase = ReelPhase.SETTLING)
        } else if (reel1.phase == ReelPhase.SETTLING && now >= reel1StopAt + REEL_BOUNCE_MS) {
            reel1 = reel1.copy(phase = ReelPhase.IDLE)
        }

        if (reel2.phase == ReelPhase.SPINNING && now >= reel2StopAt - REEL_DECEL_MS) {
            reel2 = reel2.copy(phase = ReelPhase.SETTLING)
        } else if (reel2.phase == ReelPhase.SETTLING && now >= reel2StopAt + REEL_BOUNCE_MS) {
            reel2 = reel2.copy(phase = ReelPhase.IDLE)
        }

        if (reel1.phase == ReelPhase.IDLE && reel2.phase == ReelPhase.IDLE) {
            reelsActive = false
            onReelsSettled()
        }
    }

    private fun onReelsSettled() {
        val e = engine ?: return
        if (reelSpinnerColor == humanColor && reel1.value in 1..6 && reel1.value == reel2.value) {
            doublesPulseAtMs = clock()
        }
        if (openingInProgress) {
            openingSettling = false
            openingStartPendingAt = clock() +
                if (e.phase == GamePhase.AWAIT_SPIN) 900 else 350
            spinLocked = true
            return
        }
        if (e.phase == GamePhase.AWAIT_SPIN) {
            // The turn was lost during the spin.
            if (e.currentColor == humanColor) message = UiMessage(R.string.no_moves)
            e.endTurn()
            afterTurnChange()
        } else {
            spinLocked = false
            if (e.legalActions().isEmpty()) {
                if (e.currentColor == humanColor) message = UiMessage(R.string.no_moves)
                e.endTurn()
                afterTurnChange()
            } else if (e.currentColor == humanColor) {
                message = UiMessage(R.string.your_turn_choose)
                maybeAutoPlay()
            }
        }
    }

    private fun afterTurnChange() {
        val e = engine ?: return
        choices = null
        reel1 = ReelViewState()
        reel2 = ReelViewState()
        message = turnMessage(e.currentColor, R.string.your_turn_spin)
        spinLocked = e.currentColor != humanColor
        aiActAtMs = clock() + nextAiDelay
    }

    private fun maybeAutoPlay() {
        val e = engine ?: return
        if (e.currentColor != humanColor) return
        val actions = e.legalActions()
        if (actions.size == 1) applyMove(actions.first())
    }

    // ---- Human interaction --------------------------------------------------

    fun humanSelectPawn(color: ParchisColor, pawnId: Int) {
        val e = engine ?: return
        if (e.currentColor != humanColor || e.phase != GamePhase.AWAIT_MOVE) return
        val actions = actionsFor(color, pawnId)
        if (actions.isEmpty()) return
        if (actions.size == 1) {
            applyMove(actions.first())
        } else {
            choices = PawnChoices(
                color = color,
                pawnId = pawnId,
                destinations = actions.associate { e.indexAfter(it) to (it.slots.size == 2) },
                canUseR1 = actions.any { it.slots == setOf(ReelSlot.R1) },
                canUseR2 = actions.any { it.slots == setOf(ReelSlot.R2) }
            )
            refreshReels()
        }
    }

    fun clearChoices() {
        if (choices != null) {
            choices = null
            refreshReels()
        }
    }

    fun actionsFor(color: ParchisColor, pawnId: Int): List<MoveAction> {
        val e = engine ?: return emptyList()
        if (color != humanColor) return emptyList()
        return e.legalActions().filter { it.color == color && it.pawnId == pawnId }
    }

    fun isPawnTappable(color: ParchisColor, pawnId: Int): Boolean =
        actionsFor(color, pawnId).isNotEmpty()

    /** True when the pawn has a legal move and should be drawn with a highlight. */
    fun isPawnHighlighted(color: ParchisColor, pawnId: Int): Boolean {
        val e = engine ?: return false
        if (e.currentColor != humanColor || e.phase != GamePhase.AWAIT_MOVE) return false
        return isPawnTappable(color, pawnId)
    }

    fun humanPlayReel(slot: ReelSlot) {
        val c = choices ?: return
        val action = actionsFor(c.color, c.pawnId).firstOrNull { it.slots == setOf(slot) } ?: return
        applyMove(action)
    }

    fun humanPlayDestination(index: Int) {
        val c = choices ?: return
        val action = actionsFor(c.color, c.pawnId).firstOrNull { engine!!.indexAfter(it) == index } ?: return
        applyMove(action)
    }

    fun humanEnter() {
        val e = engine ?: return
        if (e.currentColor != humanColor || e.phase != GamePhase.AWAIT_MOVE) return
        val enter = e.legalActions().filterIsInstance<MoveAction.Enter>().firstOrNull() ?: return
        applyMove(enter)
    }

    fun humanHasActions(): Boolean {
        val e = engine ?: return false
        return e.currentColor == humanColor && e.phase == GamePhase.AWAIT_MOVE && e.legalActions().isNotEmpty()
    }

    // ---- Apply --------------------------------------------------------------

    private fun applyMove(action: MoveAction) {
        val e = engine ?: return
        val mover = e.pawns.getValue(action.color).first { it.id == action.pawnId }
        val from = anchorFor(e, action.color, action.pawnId, mover.index)
        val result = e.applyMove(action)
        val to = anchorFor(e, action.color, action.pawnId, mover.index)

        val now = clock()
        val anims = mutableListOf<Anim>()
        anims += Anim.Move(action.color, action.pawnId, from.first, from.second, to.first, to.second, now, 280)
        if (result.capturedColor != null && result.capturedPawnId != null) {
            val capColor = result.capturedColor
            val capId = result.capturedPawnId
            val yard = anchorFor(e, capColor, capId, RouteIndex.YARD)
            anims += Anim.Capture(capColor, capId, to.first, to.second, yard.first, yard.second, now, 420)
            message = UiMessage(R.string.captured)
        }
        animations = anims
        choices = null

        when {
            result.gameOver -> {
                refreshReels()
                scheduleResult()
            }
            result.extraSpin -> {
                e.grantExtraSpin()
                refreshReels()
                spinLocked = e.currentColor != humanColor
                message = turnMessage(e.currentColor, R.string.doubles_spin_again)
                if (e.currentColor != humanColor) aiActAtMs = now + nextAiDelay
            }
            e.legalActions().isEmpty() -> {
                e.endTurn()
                afterTurnChange()
            }
            else -> {
                refreshReels()
                if (e.currentColor == humanColor) {
                    message = UiMessage(R.string.your_turn_choose)
                    maybeAutoPlay()
                } else {
                    message = UiMessage(R.string.thinking, e.currentColor)
                    aiActAtMs = now + 350
                }
            }
        }
    }

    private fun aiStep() {
        val e = engine ?: return
        if (e.phase != GamePhase.AWAIT_MOVE || e.currentColor == humanColor) return
        val action = ai.chooseMove(e) ?: run {
            e.endTurn(); afterTurnChange(); return
        }
        applyMove(action)
    }

    private fun refreshReels() {
        val e = engine
        if (e == null || e.phase != GamePhase.AWAIT_MOVE) {
            reel1 = ReelViewState(value = e?.reel1 ?: 0)
            reel2 = ReelViewState(value = e?.reel2 ?: 0)
            return
        }
        val c = choices
        reel1 = ReelViewState(
            value = e.reel1,
            dimmed = e.isSlotUsed(ReelSlot.R1),
            highlighted = c != null && c.canUseR1
        )
        reel2 = ReelViewState(
            value = e.reel2,
            dimmed = e.isSlotUsed(ReelSlot.R2),
            highlighted = c != null && c.canUseR2
        )
    }

    // ---- Reset --------------------------------------------------------------

    fun reset() {
        engine = null
        selectedOpponents = 0
        choices = null
        openingTotals = emptyMap()
        animations = emptyList()
        reel1 = ReelViewState()
        reel2 = ReelViewState()
        message = null
        showChoosePrompt = false
        splashPulse = false
        resultAtMs = null
        doublesPulseAtMs = 0L
        spinLocked = true
        reelsActive = false
        openingInProgress = false
        openingSettling = false
        openingStartPendingAt = 0
        screen = Screen.SPLASH
    }

    /** Elapsed time since the reels started, and whether they are animating. */
    fun reelAnimationElapsed(now: Long): Long = if (reelsActive) now - reelStartAt else -1L

    fun reelStopDurationMs(slot: ReelSlot): Long =
        (if (slot == ReelSlot.R1) reel1StopAt else reel2StopAt) - reelStartAt

    fun reelStartSeed(): Int = ((reelStartAt xor (reelStartAt ushr 32)).toInt() and 0x7fffffff)

    /** Time spent in the individual reel's settling phase, or -1 while it is not settling. */
    fun reelSettleElapsed(now: Long, slot: ReelSlot): Long {
        val state = if (slot == ReelSlot.R1) reel1 else reel2
        if (state.phase != ReelPhase.SETTLING) return -1L
        val stopAt = if (slot == ReelSlot.R1) reel1StopAt else reel2StopAt
        return (now - (stopAt - 450L)).coerceAtLeast(0L)
    }

    /** Message for the active player: the human's prompt, or "X is thinking…". */
    private fun turnMessage(color: ParchisColor, humanRes: Int): UiMessage =
        if (color == humanColor) UiMessage(humanRes) else UiMessage(R.string.thinking, color)

    /** Normalized center for a pawn, resolving its current yard slot when in the yard. */
    private fun anchorFor(e: GameEngine, color: ParchisColor, pawnId: Int, index: Int): Pair<Float, Float> {
        val slot = if (index == RouteIndex.YARD) PawnPositioner.yardSlot(e.pawns, color, pawnId) else -1
        return PawnPositioner.anchorPoint(coords.gameplay, rules, color, pawnId, PawnPositioner.Anchor.AtIndex(index), slot)
    }

    fun colorNameRes(color: ParchisColor): Int = when (color) {
        ParchisColor.RED -> R.string.color_red
        ParchisColor.YELLOW -> R.string.color_yellow
        ParchisColor.GREEN -> R.string.color_green
        ParchisColor.BLUE -> R.string.color_blue
    }
}
