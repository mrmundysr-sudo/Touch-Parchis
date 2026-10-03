package com.touchdevelopment.touchparchis.engine

/**
 * Pure Kotlin game engine for Touch Parchís V1. No Android imports.
 *
 * The engine owns all rules: routes, legal move generation, entering, captures,
 * safety squares, blockades, doubles, the opening spin, turn order, AI seats, and
 * win detection (RULES.md §1-§11). The UI only asks "what is legal?" and "apply this".
 */
class GameEngine(
    val rules: RulesData,
    val rng: RandomSource,
    val activeSeats: List<ParchisColor>
) {
    init {
        require(activeSeats.isNotEmpty()) { "at least one active seat is required" }
        require(activeSeats.contains(rules.humanColor)) { "the human seat must be active" }
    }

    /** Four pawns per active color. Inactive colors have no pawns. */
    val pawns: Map<ParchisColor, List<Pawn>> = activeSeats.associateWith { color ->
        (0 until 4).map { Pawn(color, it, RouteIndex.YARD) }
    }

    var phase: GamePhase = GamePhase.OPENING_SPIN
        private set

    /** Index into [activeSeats] of the player whose turn it is. */
    var currentSeatIndex: Int = 0
        private set

    var reel1: Int = 0
        private set
    var reel2: Int = 0
        private set

    private val usedSlots = mutableSetOf<ReelSlot>()
    private var rollMaxValues = 0

    var winner: ParchisColor? = null
        private set

    // ---- Opening spin state -------------------------------------------------

    /** Latest total per color during the opening spin, for the yard labels. */
    val openingTotals = mutableMapOf<ParchisColor, Int>()
    private var openingOrder: List<ParchisColor> = activeSeats
    private var openingIndex = 0

    val currentColor: ParchisColor get() = activeSeats[currentSeatIndex]

    val humanColor: ParchisColor get() = rules.humanColor

    fun isHumanTurn(): Boolean = currentColor == rules.humanColor

    // ---- Opening spin (RULES.md §2, §11 step 0) -----------------------------

    /** The next player who must spin during the opening spin. */
    fun nextOpeningSpinner(): ParchisColor = openingOrder[openingIndex]

    /** Performs one opening spin for [nextOpeningSpinner] and returns the outcome. */
    fun openingSpin(): OpeningSpinResult {
        check(phase == GamePhase.OPENING_SPIN) { "not in the opening-spin phase" }
        val color = openingOrder[openingIndex]
        val a = rng.nextInt(1, 6)
        val b = rng.nextInt(1, 6)
        openingTotals[color] = a + b
        openingIndex++

        if (openingIndex < openingOrder.size) {
            return OpeningSpinResult(color, a, b, a + b, roundComplete = false, starterDecided = false)
        }

        val max = openingOrder.maxOf { openingTotals.getValue(it) }
        val tied = openingOrder.filter { openingTotals.getValue(it) == max }
        return if (tied.size == 1) {
            currentSeatIndex = activeSeats.indexOf(tied.first())
            phase = GamePhase.AWAIT_SPIN
            OpeningSpinResult(color, a, b, a + b, roundComplete = true, starterDecided = true)
        } else {
            openingOrder = tied
            openingIndex = 0
            OpeningSpinResult(color, a, b, a + b, roundComplete = true, starterDecided = false)
        }
    }

    // ---- Normal turns (RULES.md §5, §11) ------------------------------------

    /** Spins both reels. Values are decided here; the animation is cosmetic. */
    fun spin(): SpinResult {
        check(phase == GamePhase.AWAIT_SPIN) { "spin is not allowed in phase $phase" }
        reel1 = rng.nextInt(1, 6)
        reel2 = rng.nextInt(1, 6)
        usedSlots.clear()
        rollMaxValues = maxAchievable(emptySet())
        phase = GamePhase.AWAIT_MOVE
        return SpinResult(reel1, reel2, turnLost = rollMaxValues == 0)
    }

    fun isSlotUsed(slot: ReelSlot): Boolean = slot in usedSlots

    fun remainingSlots(): Set<ReelSlot> =
        ReelSlot.values().toSet() - usedSlots

    fun reelValue(slot: ReelSlot): Int = if (slot == ReelSlot.R1) reel1 else reel2

    /** True when the whole roll has been resolved and no value remains playable. */
    fun rollResolved(): Boolean = legalActions().isEmpty()

    /**
     * Legal single-step actions for the current roll, restricted to sequences that play
     * the maximum possible number of reel values (RULES.md §7, "must move when you can").
     */
    fun legalActions(): List<MoveAction> {
        if (phase != GamePhase.AWAIT_MOVE) return emptyList()
        return rawActions(remainingSlots()).filter { action ->
            usedSlots.size + action.valuesConsumed +
                maxAchievable(usedSlots + action.slots) == rollMaxValues
        }
    }

    /** All single-step actions ignoring the max-values filter (used internally and by tests). */
    fun rawActionsForTesting(): List<MoveAction> = rawActions(remainingSlots())

    /** Applies a legal action. Throws if the action is not currently legal. */
    fun applyMove(action: MoveAction): ApplyResult {
        check(phase == GamePhase.AWAIT_MOVE) { "no move is pending" }
        require(legalActions().contains(action)) { "illegal action: $action" }

        var capturedColor: ParchisColor? = null
        var capturedPawnId: Int? = null
        val pawn = pawns.getValue(action.color).first { it.id == action.pawnId }

        when (action) {
            is MoveAction.Enter -> {
                val start = rules.startSquare(action.color)
                val lone = singleOpponentOnSquare(action.color, start)
                if (lone != null) {
                    lone.index = RouteIndex.YARD
                    capturedColor = lone.color
                    capturedPawnId = lone.id
                }
                pawn.index = RouteIndex.START
            }

            is MoveAction.Advance -> {
                val from = pawn.index
                val to = from + action.amount
                if (to <= RouteIndex.LAST_TRACK) {
                    val square = rules.squareAt(action.color, to)
                    val victim = singleOpponentOnSquare(action.color, square)
                    if (victim != null) {
                        victim.index = RouteIndex.YARD
                        capturedColor = victim.color
                        capturedPawnId = victim.id
                    }
                }
                pawn.index = to
            }
        }

        usedSlots.addAll(action.slots)

        if (pawn.index == RouteIndex.HOME && finishedCount(action.color) == 4) {
            winner = action.color
            phase = GamePhase.GAME_OVER
        }

        val extraSpin = phase != GamePhase.GAME_OVER &&
            reel1 == reel2 && usedSlots.size == 2

        return ApplyResult(
            capturedColor = capturedColor,
            capturedPawnId = capturedPawnId,
            extraSpin = extraSpin,
            turnPassed = false,
            gameOver = phase == GamePhase.GAME_OVER,
            winner = winner
        )
    }

    /** Passes the turn to the next active seat and re-enables spinning. */
    fun endTurn() {
        if (phase == GamePhase.GAME_OVER) return
        currentSeatIndex = (currentSeatIndex + 1) % activeSeats.size
        usedSlots.clear()
        rollMaxValues = 0
        phase = GamePhase.AWAIT_SPIN
    }

    /** Starts a fresh extra spin for the same player (doubles). */
    fun grantExtraSpin() {
        usedSlots.clear()
        rollMaxValues = 0
        phase = GamePhase.AWAIT_SPIN
    }

    fun finishedCount(color: ParchisColor): Int =
        pawns[color]?.count { it.index == RouteIndex.HOME } ?: 0

    /** Maximum number of reel values playable right now (RULES.md §7). */
    fun maxPlayableValues(): Int = maxAchievable(usedSlots)

    /** Maximum number of reel values playable after taking [action] hypothetically. */
    fun maxPlayableAfter(action: MoveAction): Int = maxAchievable(usedSlots + action.slots)

    /**
     * Forces the current roll without consuming RNG. Intended for tests and tools;
     * normal gameplay obtains values through [spin].
     */
    fun setRollForTesting(r1: Int, r2: Int) {
        reel1 = r1
        reel2 = r2
        usedSlots.clear()
        rollMaxValues = maxAchievable(emptySet())
        phase = GamePhase.AWAIT_MOVE
    }

    /** Test hook: forces a player's turn. */
    fun setSeatForTesting(color: ParchisColor) {
        currentSeatIndex = activeSeats.indexOf(color).also { require(it >= 0) }
    }

    // ---- Read-only helpers used by the AI -----------------------------------

    /** Route index a pawn would occupy after [action]. */
    fun indexAfter(action: MoveAction): Int {
        val pawn = pawns.getValue(action.color).first { it.id == action.pawnId }
        return when (action) {
            is MoveAction.Enter -> RouteIndex.START
            is MoveAction.Advance -> pawn.index + action.amount
        }
    }

    /** Square a pawn would land on after [action], or null when it lands on the stretch/home. */
    fun landingSquare(action: MoveAction): Int? {
        val idx = indexAfter(action)
        return if (idx in RouteIndex.START..RouteIndex.LAST_TRACK) rules.squareAt(action.color, idx) else null
    }

    /** Whether [action] captures a lone opponent (exact landing, or entering onto the start). */
    fun actionWouldCapture(action: MoveAction): Boolean {
        val square = landingSquare(action) ?: return false
        return singleOpponentOnSquare(action.color, square) != null
    }

    /** Whether [action] moves a pawn from the common track into its home stretch. */
    fun actionEntersStretch(action: MoveAction): Boolean {
        if (action !is MoveAction.Advance) return false
        val pawn = pawns.getValue(action.color).first { it.id == action.pawnId }
        return pawn.index <= RouteIndex.LAST_TRACK && indexAfter(action) in RouteIndex.STRETCH_START..(RouteIndex.STRETCH_START + 6)
    }

    /** Whether [action] lands on a safety square. */
    fun actionLandsOnSafety(action: MoveAction): Boolean {
        val square = landingSquare(action) ?: return false
        return rules.isSafeSquare(square)
    }

    /** Whether [action] leaves two same-color pawns on one common-track square. */
    fun actionFormsBlockade(action: MoveAction): Boolean {
        val square = landingSquare(action) ?: return false
        val mover = pawns.getValue(action.color).first { it.id == action.pawnId }
        val others = pawns.getValue(action.color).count {
            it.id != mover.id &&
                it.index in RouteIndex.START..RouteIndex.LAST_TRACK &&
                rules.squareAt(action.color, it.index) == square
        }
        return others >= 1
    }

    /** Whether [action] finishes a pawn in Home Box 5. */
    fun actionFinishes(action: MoveAction): Boolean = indexAfter(action) == RouteIndex.HOME

    // ---- Internals ----------------------------------------------------------

    private fun rawActions(remaining: Set<ReelSlot>): List<MoveAction> {
        val color = currentColor
        val out = mutableListOf<MoveAction>()

        // Advance actions: each remaining single reel, plus the combined total.
        val singleValues = remaining.map { it to reelValue(it) }
        for ((slot, value) in singleValues) {
            for (p in pawns.getValue(color)) {
                if (isAdvanceLegal(color, p.index, value)) {
                    out += MoveAction.Advance(color, p.id, setOf(slot), value)
                }
            }
        }
        if (remaining.size == 2) {
            val total = reel1 + reel2
            for (p in pawns.getValue(color)) {
                if (isAdvanceLegal(color, p.index, total)) {
                    out += MoveAction.Advance(color, p.id, remaining, total)
                }
            }
        }

        // Enter actions: a single reel showing 5, or both reels summing to 5.
        val yardPawns = pawns.getValue(color).filter { it.index == RouteIndex.YARD }
        if (yardPawns.isNotEmpty() && isEnterLegal(color)) {
            val slotFive = remaining.firstOrNull { reelValue(it) == 5 }
            if (slotFive != null) {
                out += MoveAction.Enter(color, yardPawns.first().id, setOf(slotFive))
            }
            if (remaining.size == 2 && reel1 + reel2 == 5) {
                out += MoveAction.Enter(color, yardPawns.first().id, remaining.toSet())
            }
        }

        return out
    }

    /** Maximum number of reel values playable from the given used-slot set. */
    private fun maxAchievable(used: Set<ReelSlot>): Int {
        val remaining = ReelSlot.values().toSet() - used
        if (remaining.isEmpty()) return 0
        var best = 0
        for (action in rawActions(remaining)) {
            val v = action.valuesConsumed + maxAchievable(used + action.slots)
            if (v > best) best = v
        }
        return best
    }

    /** Whether a pawn at [from] may advance [amount] spaces. */
    fun isAdvanceLegal(color: ParchisColor, from: Int, amount: Int): Boolean {
        if (from < RouteIndex.START || from > RouteIndex.STRETCH_START + 6) return false
        if (amount <= 0) return false
        val to = from + amount
        if (to > RouteIndex.HOME) return false

        // Blockades block passing over and landing on common-track squares (RULES.md §10).
        for (i in (from + 1)..to) {
            if (i <= RouteIndex.LAST_TRACK) {
                val square = rules.squareAt(color, i)
                if (hasBlockade(square)) return false
            }
        }

        // Landing restrictions on the common track (RULES.md §7 rule 4, §8).
        if (to <= RouteIndex.LAST_TRACK) {
            val square = rules.squareAt(color, to)
            if (rules.isSafeSquare(square) && hasDifferentColorPawn(color, square)) return false
        }
        return true
    }

    /** Whether a pawn of [color] may enter from the yard. */
    fun isEnterLegal(color: ParchisColor): Boolean {
        val start = rules.startSquare(color)
        return !hasBlockade(start)
    }

    private fun hasBlockade(square: Int): Boolean =
        ParchisColor.values().any { c -> pawnsOnSquare(c, square) >= 2 }

    private fun pawnsOnSquare(color: ParchisColor, square: Int): Int =
        pawns[color]?.count { it.index in RouteIndex.START..RouteIndex.LAST_TRACK && rules.squareAt(color, it.index) == square } ?: 0

    private fun hasDifferentColorPawn(color: ParchisColor, square: Int): Boolean =
        ParchisColor.values().any { it != color && pawnsOnSquare(it, square) > 0 }

    private fun singleOpponentOnSquare(color: ParchisColor, square: Int): Pawn? {
        if (rules.isSafeSquare(square) && square != rules.startSquare(color)) return null
        var found: Pawn? = null
        for (c in ParchisColor.values()) {
            if (c == color) continue
            val onSquare = pawns[c]?.filter {
                it.index in RouteIndex.START..RouteIndex.LAST_TRACK && rules.squareAt(c, it.index) == square
            } ?: continue
            if (onSquare.size == 1) {
                if (found != null) return null
                found = onSquare.first()
            } else if (onSquare.size > 1) {
                return null
            }
        }
        return found
    }
}
