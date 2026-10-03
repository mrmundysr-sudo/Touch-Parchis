package com.touchdevelopment.touchparchis.ai

import com.touchdevelopment.touchparchis.engine.GameEngine
import com.touchdevelopment.touchparchis.engine.MoveAction
import com.touchdevelopment.touchparchis.engine.RandomSource
import com.touchdevelopment.touchparchis.engine.RouteIndex

/**
 * AI move selection for V1 (RULES.md §13).
 *
 * Picks the first rule that applies, and a random action among ties:
 * 1 finish, 2 capture, 3 enter, 4 enter the home stretch, 5 land on safety,
 * 6 form a blockade, 7 move the most advanced pawn not yet in its home stretch.
 * It only ever chooses from the engine's legal-move list, so it obeys the
 * "play as many values as possible" rule automatically.
 */
class RulesAI(private val rng: RandomSource) {

    fun chooseMove(engine: GameEngine): MoveAction? {
        val legal = engine.legalActions()
        if (legal.isEmpty()) return null

        val tiers: List<(MoveAction) -> Boolean> = listOf(
            { engine.actionFinishes(it) },
            { engine.actionWouldCapture(it) },
            { it is MoveAction.Enter },
            { engine.actionEntersStretch(it) },
            { engine.actionLandsOnSafety(it) },
            { engine.actionFormsBlockade(it) }
        )

        for (predicate in tiers) {
            val matches = legal.filter(predicate)
            if (matches.isNotEmpty()) return pick(matches)
        }

        // Fall back to moving the most advanced pawn that is not in its home stretch.
        val onTrack = legal.filter { action ->
            val pawn = engine.pawns.getValue(action.color).first { it.id == action.pawnId }
            pawn.index <= RouteIndex.LAST_TRACK
        }
        val pool = onTrack.ifEmpty { legal }
        val maxIndex = pool.maxOf { action ->
            engine.pawns.getValue(action.color).first { it.id == action.pawnId }.index
        }
        return pick(pool.filter {
            engine.pawns.getValue(it.color).first { p -> p.id == it.pawnId }.index == maxIndex
        })
    }

    private fun pick(actions: List<MoveAction>): MoveAction {
        require(actions.isNotEmpty())
        val i = rng.nextInt(0, actions.size - 1)
        return actions[i]
    }
}
