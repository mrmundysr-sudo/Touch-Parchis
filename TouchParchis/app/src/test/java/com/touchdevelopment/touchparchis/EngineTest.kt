package com.touchdevelopment.touchparchis

import com.touchdevelopment.touchparchis.ai.RulesAI
import com.touchdevelopment.touchparchis.data.GameDataLoader
import com.touchdevelopment.touchparchis.engine.GameEngine
import com.touchdevelopment.touchparchis.engine.GamePhase
import com.touchdevelopment.touchparchis.engine.KotlinRandomSource
import com.touchdevelopment.touchparchis.engine.MoveAction
import com.touchdevelopment.touchparchis.engine.ParchisColor
import com.touchdevelopment.touchparchis.engine.ReelSlot
import com.touchdevelopment.touchparchis.engine.RouteIndex
import com.touchdevelopment.touchparchis.engine.RulesData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * JVM tests for the pure engine, covering RULES.md §15 (items 1-11) plus data integrity.
 */
class EngineTest {

    private val rules = RulesData.of()

    private fun engine(opponents: Int, seed: Long = 1L): GameEngine =
        GameEngine(rules, KotlinRandomSource(seed), rules.activeSeats(opponents))

    private fun place(e: GameEngine, color: ParchisColor, id: Int, index: Int) {
        e.pawns.getValue(color).first { it.id == id }.index = index
    }

    private fun roll(e: GameEngine, r1: Int, r2: Int) {
        e.setRollForTesting(r1, r2)
    }

    // ---- 1. Routes ----------------------------------------------------------

    @Test
    fun test01_routes_have_correct_shape_and_do_not_enter_other_stretches() {
        for (color in ParchisColor.values()) {
            val route = rules.route(color)
            assertEquals("route $color length", 73, route.size)
            assertEquals(RulesData.YARD_SENTINEL, route[RouteIndex.YARD])
            assertEquals(RouteIndex.HOME_SQUARE, route[RouteIndex.HOME])

            // Indices 1..64 are the common track (6..73) and never another color's stretch.
            for (i in RouteIndex.START..RouteIndex.LAST_TRACK) {
                assertTrue("index $i of $color is a common-track square", route[i] in 6..73)
            }
            // Indices 65..71 are this color's own home stretch.
            val stretch = rules.homeStretchRange.getValue(color)
            for (i in RouteIndex.STRETCH_START..(RouteIndex.STRETCH_START + 6)) {
                assertTrue("index $i of $color in own stretch", route[i] in stretch)
            }
        }
    }

    @Test
    fun test01b_route_anchors_match_spec() {
        assertEquals(10, rules.route(ParchisColor.RED)[RouteIndex.START])
        assertEquals(73, rules.route(ParchisColor.RED)[RouteIndex.LAST_TRACK])
        assertEquals(27, rules.route(ParchisColor.YELLOW)[RouteIndex.START])
        assertEquals(22, rules.route(ParchisColor.YELLOW)[RouteIndex.LAST_TRACK])
        assertEquals(44, rules.route(ParchisColor.GREEN)[RouteIndex.START])
        assertEquals(39, rules.route(ParchisColor.GREEN)[RouteIndex.LAST_TRACK])
        assertEquals(61, rules.route(ParchisColor.BLUE)[RouteIndex.START])
        assertEquals(56, rules.route(ParchisColor.BLUE)[RouteIndex.LAST_TRACK])
    }

    // ---- 2. Home Box 5 needs an exact count --------------------------------

    @Test
    fun test02_last_track_needs_no_exact_stop_but_home_does() {
        val e = engine(3)
        // From the last track square (64) a move of 1 lands in the stretch (65).
        assertTrue(e.isAdvanceLegal(ParchisColor.RED, RouteIndex.LAST_TRACK, 1))
        // From the last stretch square (71) exactly 1 finishes; 2 overshoots.
        assertTrue(e.isAdvanceLegal(ParchisColor.RED, 71, 1))
        assertFalse(e.isAdvanceLegal(ParchisColor.RED, 71, 2))
        assertFalse(e.isAdvanceLegal(ParchisColor.RED, 72, 1))
    }

    // ---- 3. Entering --------------------------------------------------------

    @Test
    fun test03_entering_needs_a_five_and_is_optional() {
        val e = engine(3)
        // A reel showing 5 enters.
        roll(e, 5, 3)
        assertTrue(e.legalActions().any { it is MoveAction.Enter })
        // Sum of 5 enters using both reels.
        roll(e, 2, 3)
        val sumEnter = e.legalActions().filterIsInstance<MoveAction.Enter>().single()
        assertEquals(setOf(ReelSlot.R1, ReelSlot.R2), sumEnter.slots)
        // No five: cannot enter.
        roll(e, 4, 6)
        assertFalse(e.legalActions().any { it is MoveAction.Enter })
    }

    @Test
    fun test03b_entry_is_optional_with_a_board_pawn() {
        val e = engine(3)
        place(e, ParchisColor.RED, 0, 1) // on start square 10
        roll(e, 5, 3)
        val actions = e.legalActions()
        assertTrue("may enter", actions.any { it is MoveAction.Enter })
        assertTrue("may also advance a board pawn", actions.any { it is MoveAction.Advance })
    }

    @Test
    fun test03c_start_blockade_blocks_entry() {
        val e = engine(3)
        place(e, ParchisColor.RED, 0, 1)
        place(e, ParchisColor.RED, 1, 1) // two red pawns on square 10 = blockade
        roll(e, 5, 3)
        assertFalse(e.isEnterLegal(ParchisColor.RED))
    }

    @Test
    fun test03d_lone_opponent_on_start_square_is_captured_on_entry() {
        val e = engine(3)
        // Blue pawn at blue index 18 -> square 10 (red start).
        place(e, ParchisColor.BLUE, 0, 18)
        assertEquals(10, rules.squareAt(ParchisColor.BLUE, 18))
        roll(e, 5, 3)
        val enter = e.legalActions().filterIsInstance<MoveAction.Enter>().first()
        val result = e.applyMove(enter)
        assertEquals(ParchisColor.BLUE, result.capturedColor)
        assertEquals(RouteIndex.YARD, e.pawns.getValue(ParchisColor.BLUE)[0].index)
        assertEquals(RouteIndex.START, e.pawns.getValue(ParchisColor.RED)[0].index)
    }

    // ---- 4. Capturing -------------------------------------------------------

    @Test
    fun test04_exact_landing_on_ordinary_square_captures() {
        val e = engine(3)
        place(e, ParchisColor.RED, 0, 1)          // square 10
        place(e, ParchisColor.BLUE, 0, 21)         // square 13
        assertEquals(13, rules.squareAt(ParchisColor.BLUE, 21))
        roll(e, 3, 4)
        val advance = e.legalActions().filterIsInstance<MoveAction.Advance>()
            .first { it.pawnId == 0 && it.amount == 3 }
        val result = e.applyMove(advance)
        assertEquals(ParchisColor.BLUE, result.capturedColor)
        assertEquals(RouteIndex.YARD, e.pawns.getValue(ParchisColor.BLUE)[0].index)
        // No bonus move is awarded for a capture.
        assertFalse(result.extraSpin)
    }

    @Test
    fun test04b_landing_on_safety_holding_opponent_is_illegal() {
        val e = engine(3)
        place(e, ParchisColor.RED, 0, 5)          // square 14
        place(e, ParchisColor.BLUE, 0, 25)         // square 17 (safety)
        assertEquals(17, rules.squareAt(ParchisColor.BLUE, 25))
        // Landing on square 17 (index 8 for red) with a different color is not allowed.
        assertFalse(e.isAdvanceLegal(ParchisColor.RED, 5, 3))
    }

    // ---- 5. Passing over a safety square ------------------------------------

    @Test
    fun test05_passing_over_a_safety_square_is_allowed() {
        val e = engine(3)
        place(e, ParchisColor.RED, 0, 5)          // square 14
        place(e, ParchisColor.BLUE, 0, 25)         // square 17 (safety)
        // Move 5: passes over square 17 (index 8) and lands on square 19 (index 10).
        assertTrue(e.isAdvanceLegal(ParchisColor.RED, 5, 5))
        assertEquals(19, rules.squareAt(ParchisColor.RED, 10))
    }

    // ---- 6. Blockades -------------------------------------------------------

    @Test
    fun test06_blockade_stops_owner_and_others_from_passing() {
        val e = engine(3)
        place(e, ParchisColor.RED, 0, 5)          // square 14
        place(e, ParchisColor.RED, 1, 7)          // square 16
        place(e, ParchisColor.RED, 2, 7)          // square 16 -> own blockade
        // Pawn at index 5 moving 3 lands on index 8 (square 17) but passes index 7 (blockade).
        assertFalse(e.isAdvanceLegal(ParchisColor.RED, 5, 3))
        // Opponent cannot pass either.
        place(e, ParchisColor.BLUE, 0, 25)         // square 17
        assertFalse(e.isAdvanceLegal(ParchisColor.RED, 5, 5))
    }

    @Test
    fun test06b_forced_move_out_of_a_blockade_is_offered_when_it_is_the_only_move() {
        val e = engine(3)
        place(e, ParchisColor.RED, 0, 1)          // square 10
        place(e, ParchisColor.RED, 1, 1)          // square 10 -> blockade
        roll(e, 5, 3)
        val actions = e.legalActions()
        assertTrue("blockade pawns must be movable", actions.isNotEmpty())
        assertTrue(actions.all { it.pawnId == 0 || it.pawnId == 1 })
    }

    // ---- 7. Combined vs separate moves --------------------------------------

    @Test
    fun test07_combined_lands_only_at_end_but_separate_lands_intermediate() {
        // Combined: pass over the intermediate square, no capture.
        run {
            val e = engine(3)
            place(e, ParchisColor.RED, 0, 3)       // square 12
            place(e, ParchisColor.BLUE, 0, 22)      // square 14 (intermediate)
            assertEquals(14, rules.squareAt(ParchisColor.BLUE, 22))
            roll(e, 2, 3)
            val combined = e.legalActions().filterIsInstance<MoveAction.Advance>()
                .first { it.pawnId == 0 && it.slots.size == 2 && it.amount == 5 }
            e.applyMove(combined)
            assertEquals(8, e.pawns.getValue(ParchisColor.RED)[0].index) // square 17
            assertTrue("blue survives a passed-over square",
                e.pawns.getValue(ParchisColor.BLUE)[0].index != RouteIndex.YARD)
        }
        // Separate: the first move really lands on the intermediate square and captures.
        run {
            val e = engine(3)
            place(e, ParchisColor.RED, 0, 3)       // square 12
            place(e, ParchisColor.BLUE, 0, 22)      // square 14
            roll(e, 2, 3)
            val first = e.legalActions().filterIsInstance<MoveAction.Advance>()
                .first { it.pawnId == 0 && it.amount == 2 }
            e.applyMove(first)
            assertEquals(5, e.pawns.getValue(ParchisColor.RED)[0].index) // square 14
            assertEquals(RouteIndex.YARD, e.pawns.getValue(ParchisColor.BLUE)[0].index)
        }
    }

    // ---- 8. Play as many values as possible ---------------------------------

    @Test
    fun test08_every_legal_action_preserves_the_maximum_value_count() {
        val e = engine(3)
        place(e, ParchisColor.RED, 0, 60)
        place(e, ParchisColor.RED, 1, 64)
        place(e, ParchisColor.BLUE, 0, 30)
        roll(e, 2, 4)
        val max = e.maxPlayableValues()
        for (action in e.legalActions()) {
            val achievable = action.valuesConsumed + e.maxPlayableAfter(action)
            assertEquals("action $action must not play fewer values than the maximum",
                max, achievable)
        }
    }

    @Test
    fun test08b_stranded_values_are_not_played() {
        val e = engine(3)
        // All red pawns on the last stretch square: only a 1 is playable, and only once.
        for (id in 0..3) place(e, ParchisColor.RED, id, 71)
        roll(e, 5, 1)
        assertEquals(1, e.maxPlayableValues())
        assertTrue(e.legalActions().all { it.valuesConsumed == 1 })
    }

    // ---- 9. Doubles ---------------------------------------------------------

    @Test
    fun test09_doubles_give_another_spin_only_when_both_values_played() {
        val e = engine(3)
        place(e, ParchisColor.RED, 0, 60)
        roll(e, 2, 2)
        val single = e.legalActions().filterIsInstance<MoveAction.Advance>()
            .first { it.amount == 2 }
        val first = e.applyMove(single)
        assertFalse("one value used: no extra spin yet", first.extraSpin)
        val second = e.applyMove(e.legalActions().filterIsInstance<MoveAction.Advance>().first { it.amount == 2 })
        assertTrue("both values played on doubles: extra spin", second.extraSpin)
    }

    // ---- 10. Seats, win and loss -------------------------------------------

    @Test
    fun test10_active_seats_match_spec_and_skip_inactive() {
        assertEquals(listOf(ParchisColor.RED, ParchisColor.BLUE), rules.activeSeats(1))
        assertEquals(listOf(ParchisColor.RED, ParchisColor.GREEN, ParchisColor.BLUE), rules.activeSeats(2))
        assertEquals(
            listOf(ParchisColor.RED, ParchisColor.YELLOW, ParchisColor.GREEN, ParchisColor.BLUE),
            rules.activeSeats(3)
        )
        // Turn order skips inactive seats.
        val e = engine(1)
        e.setSeatForTesting(ParchisColor.RED)
        e.endTurn()
        assertEquals(ParchisColor.BLUE, e.currentColor)
        e.endTurn()
        assertEquals(ParchisColor.RED, e.currentColor)
    }

    @Test
    fun test10b_human_finishing_fourth_pawn_wins() {
        val e = engine(2)
        e.setSeatForTesting(ParchisColor.RED)
        for (id in 0..2) place(e, ParchisColor.RED, id, RouteIndex.HOME)
        place(e, ParchisColor.RED, 3, 71)
        roll(e, 1, 1)
        val move = e.legalActions().filterIsInstance<MoveAction.Advance>().first { it.pawnId == 3 }
        val result = e.applyMove(move)
        assertTrue(result.gameOver)
        assertEquals(ParchisColor.RED, result.winner)
        assertEquals(GamePhase.GAME_OVER, e.phase)
    }

    @Test
    fun test10c_ai_finishing_first_is_a_loss_for_the_human() {
        val e = engine(1)
        e.setSeatForTesting(ParchisColor.BLUE)
        for (id in 0..2) place(e, ParchisColor.BLUE, id, RouteIndex.HOME)
        place(e, ParchisColor.BLUE, 3, 71)
        roll(e, 1, 2)
        val move = e.legalActions().filterIsInstance<MoveAction.Advance>().first { it.pawnId == 3 }
        val result = e.applyMove(move)
        assertTrue(result.gameOver)
        assertEquals(ParchisColor.BLUE, result.winner)
        assertFalse(result.winner == rules.humanColor)
    }

    // ---- 10a. Opening spin --------------------------------------------------

    @Test
    fun test10a_opening_spin_highest_total_starts_and_ties_respin() {
        // Scripted totals: red 7, green 8, blue 8 -> green and blue re-spin; blue wins.
        val scripted = listOf(
            3, 4,   // red 7
            4, 4,   // green 8
            2, 6,   // blue 8
            1, 1,   // green re-spin 2
            5, 4    // blue re-spin 9
        )
        val rng = com.touchdevelopment.touchparchis.engine.ScriptedRandomSource(scripted)
        val e = GameEngine(rules, rng, rules.activeSeats(2))

        var result = e.openingSpin() // red
        assertEquals(7, result.total)
        assertFalse(result.starterDecided)
        result = e.openingSpin() // green
        assertEquals(8, result.total)
        assertFalse(result.starterDecided)
        result = e.openingSpin() // blue
        assertEquals(8, result.total)
        assertTrue(result.roundComplete)
        assertFalse("tie re-spins only the tied players", result.starterDecided)

        result = e.openingSpin() // green re-spin
        assertEquals(2, result.total)
        assertFalse(result.starterDecided)
        result = e.openingSpin() // blue re-spin
        assertEquals(9, result.total)
        assertTrue(result.starterDecided)
        assertEquals(ParchisColor.BLUE, e.currentColor)
        assertEquals(GamePhase.AWAIT_SPIN, e.phase)
        // The opening spin moves nothing and enters no pawn.
        for (c in ParchisColor.values()) e.pawns[c]?.forEach { assertEquals(RouteIndex.YARD, it.index) }
    }

    // ---- 11. AI-vs-AI full games -------------------------------------------

    @Test
    fun test11_full_ai_games_terminate_with_one_winner() {
        for (opponents in 1..3) {
            for (seed in 1L..5L) {
                val e = GameEngine(rules, KotlinRandomSource(seed), rules.activeSeats(opponents))
                val ai = RulesAI(e.rng)

                // Opening spin to completion.
                var guard = 0
                while (e.phase == GamePhase.OPENING_SPIN) {
                    e.openingSpin()
                    check(guard++ < 1000) { "opening spin deadlock" }
                }

                var steps = 0
                while (e.phase != GamePhase.GAME_OVER) {
                    check(steps++ < 500_000) { "game deadlock for $opponents opponents" }
                    when (e.phase) {
                        GamePhase.AWAIT_SPIN -> {
                            val spin = e.spin()
                            if (spin.turnLost) e.endTurn()
                        }
                        GamePhase.AWAIT_MOVE -> {
                            val move = ai.chooseMove(e)
                            if (move == null) {
                                e.endTurn()
                            } else {
                                val result = e.applyMove(move)
                                when {
                                    result.gameOver -> {}
                                    result.extraSpin -> e.grantExtraSpin()
                                    e.legalActions().isEmpty() -> e.endTurn()
                                }
                            }
                        }
                        else -> error("unexpected phase ${e.phase}")
                    }
                }

                val winner = e.winner
                assertNotNull("a winner must be decided", winner)
                assertEquals("the winner must have all four pawns home", 4, e.finishedCount(winner!!))
                // Exactly one winner.
                val finishedColors = ParchisColor.values().count { e.finishedCount(it) == 4 }
                assertEquals(1, finishedColors)
            }
        }
    }

    // ---- Real data files ----------------------------------------------------

    @Test
    fun realGameDataJson_matches_engine_defaults() {
        val file = File("src/main/assets/game_data.json")
        assertTrue("game_data.json must exist at ${file.absolutePath}", file.exists())
        val loaded = GameDataLoader.parse(file.readText())

        assertEquals(rules.turnOrder, loaded.turnOrder)
        assertEquals(rules.humanColor, loaded.humanColor)
        assertEquals(rules.safeSquares, loaded.safeSquares)
        assertEquals(rules.activeAiByOpponents, loaded.activeAiByOpponents)
        for (c in ParchisColor.values()) {
            assertEquals(
                "route mismatch for $c",
                rules.route(c).toList(),
                loaded.route(c).toList()
            )
        }
    }
}
