package com.touchdevelopment.touchparchis.engine

/** The four Parchís colors. Red is always the human player in V1. */
enum class ParchisColor {
    RED, YELLOW, GREEN, BLUE;

    /** Global pawn slot base: red 0-3, yellow 4-7, green 8-11, blue 12-15. */
    val pawnBase: Int get() = ordinal * 4

    val lower: String get() = name.lowercase()
}

/** One of the two reels (slot reels replace dice). */
enum class ReelSlot { R1, R2 }

/** Route-index constants (see RULES.md §4). */
object RouteIndex {
    const val YARD = 0
    const val START = 1
    const val LAST_TRACK = 64
    const val STRETCH_START = 65
    const val HOME = 72
    /** Sentinel square number for Home Box 5 in the route tables. */
    const val HOME_SQUARE = -1
}

/** A pawn belonging to a color. [id] is 0..3 within the color. [index] is the route index 0..72. */
data class Pawn(val color: ParchisColor, val id: Int, var index: Int)

/** A legal action the active player may take with the current roll. */
sealed class MoveAction {
    abstract val color: ParchisColor
    abstract val pawnId: Int

    /** Which reel values this action consumes. */
    abstract val slots: Set<ReelSlot>

    val valuesConsumed: Int get() = slots.size

    /** Bring a pawn from the yard to its start square (requires a 5). */
    data class Enter(
        override val color: ParchisColor,
        override val pawnId: Int,
        override val slots: Set<ReelSlot>
    ) : MoveAction()

    /** Move a pawn already on the board forward by [amount] (a single reel, or both combined). */
    data class Advance(
        override val color: ParchisColor,
        override val pawnId: Int,
        override val slots: Set<ReelSlot>,
        val amount: Int
    ) : MoveAction()
}

/** Result of applying a single move. */
data class ApplyResult(
    val capturedColor: ParchisColor?,
    val capturedPawnId: Int?,
    /** True when the same player spins again (doubles fully played). */
    val extraSpin: Boolean,
    /** True when play passed to the next active seat. */
    val turnPassed: Boolean,
    val gameOver: Boolean,
    val winner: ParchisColor?
)

/** Result of a normal spin. */
data class SpinResult(val r1: Int, val r2: Int, val turnLost: Boolean)

/** Result of one opening spin. */
data class OpeningSpinResult(
    val color: ParchisColor,
    val r1: Int,
    val r2: Int,
    val total: Int,
    val roundComplete: Boolean,
    val starterDecided: Boolean
)

/** Phase of the engine state machine. */
enum class GamePhase { OPENING_SPIN, AWAIT_SPIN, AWAIT_MOVE, GAME_OVER }
