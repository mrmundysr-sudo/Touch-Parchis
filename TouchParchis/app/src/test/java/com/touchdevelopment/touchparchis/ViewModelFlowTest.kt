package com.touchdevelopment.touchparchis

import com.touchdevelopment.touchparchis.data.BoardCoordsLoader
import com.touchdevelopment.touchparchis.data.GameDataLoader
import com.touchdevelopment.touchparchis.engine.GamePhase
import com.touchdevelopment.touchparchis.engine.KotlinRandomSource
import com.touchdevelopment.touchparchis.engine.ParchisColor
import com.touchdevelopment.touchparchis.ui.GameViewModel
import com.touchdevelopment.touchparchis.ui.Screen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Drives the full UI state machine on the JVM: splash selection, the opening spin,
 * human and AI turns, and the win/loss transition, with a deterministic clock.
 */
class ViewModelFlowTest {

    private val rules = GameDataLoader.parse(File("src/main/assets/game_data.json").readText())
    private val coords = BoardCoordsLoader.parse(File("src/main/assets/board_coords.json").readText())

    private class FakeClock {
        var t = 0L
        fun advance(ms: Long) { t += ms }
    }

    private fun vm(seed: Long, clock: FakeClock) =
        GameViewModel(rules, coords, KotlinRandomSource(seed), clock = { clock.t })

    @Test
    fun play_with_no_selection_shows_prompt_and_does_not_start() {
        val clock = FakeClock()
        val vm = vm(1, clock)
        assertEquals(Screen.SPLASH, vm.screen)
        vm.play()
        assertEquals(Screen.SPLASH, vm.screen)
        assertTrue(vm.showChoosePrompt)
        assertNull(vm.engine)
        assertEquals(0, vm.selectedOpponents)
    }

    @Test
    fun selecting_opponents_is_mutually_exclusive_and_play_uses_exact_count() {
        val clock = FakeClock()
        val vm = vm(1, clock)
        vm.selectOpponents(3)
        vm.selectOpponents(2)
        assertEquals(2, vm.selectedOpponents)
        vm.play()
        assertEquals(Screen.GAMEPLAY, vm.screen)
        assertEquals(listOf(ParchisColor.RED, ParchisColor.GREEN, ParchisColor.BLUE), vm.engine!!.activeSeats)
    }

    @Test
    fun first_spin_values_are_between_one_and_six() {
        val clock = FakeClock()
        val vm = vm(7, clock)
        vm.selectOpponents(1)
        vm.play()
        // Drive the opening spin to completion.
        runToNormalTurns(vm, clock)
        assertTrue("human should be able to spin", vm.isSpinEnabled())
        vm.humanSpin()
        assertTrue(vm.reel1.value in 1..6)
        assertTrue(vm.reel2.value in 1..6)
    }

    @Test
    fun full_games_reach_a_result_screen_without_deadlock() {
        for (opponents in 1..3) {
            for (seed in 1L..4L) {
                val clock = FakeClock()
                val vm = vm(seed, clock)
                vm.selectOpponents(opponents)
                vm.play()

                var guard = 0
                while (vm.screen == Screen.GAMEPLAY && guard++ < 1_000_000) {
                    clock.advance(50)
                    vm.tick(clock.t)
                    val e = vm.engine ?: break
                    if (vm.isSpinEnabled()) {
                        vm.humanSpin()
                    } else if (e.currentColor == vm.humanColor && e.phase == GamePhase.AWAIT_MOVE) {
                        val actions = e.legalActions()
                        if (actions.isNotEmpty()) {
                            val a = actions.first()
                            vm.humanSelectPawn(a.color, a.pawnId)
                            if (vm.choices != null) vm.humanPlayDestination(e.indexAfter(a))
                        }
                    }
                }

                assertTrue("game with $opponents opponents seed $seed must end (screen=${vm.screen})",
                    vm.screen == Screen.WIN || vm.screen == Screen.LOSS)
                val winner = vm.engine!!.winner
                assertNotNull(winner)
                assertEquals(4, vm.engine!!.finishedCount(winner!!))
                val expected = if (winner == ParchisColor.RED) Screen.WIN else Screen.LOSS
                assertEquals(expected, vm.screen)

                // Reset returns to a fresh splash with nothing selected.
                vm.reset()
                assertEquals(Screen.SPLASH, vm.screen)
                assertEquals(0, vm.selectedOpponents)
                assertNull(vm.engine)
            }
        }
    }

    @Test
    fun result_screen_reset_starts_fresh() {
        val clock = FakeClock()
        val vm = vm(3, clock)
        vm.selectOpponents(1)
        vm.play()
        vm.reset()
        assertEquals(Screen.SPLASH, vm.screen)
        assertEquals(0, vm.selectedOpponents)
        assertFalse(vm.splashPulse)
    }

    private fun runToNormalTurns(vm: GameViewModel, clock: FakeClock) {
        var guard = 0
        while (vm.engine!!.phase == GamePhase.OPENING_SPIN && guard++ < 100_000) {
            clock.advance(50)
            vm.tick(clock.t)
            if (vm.isSpinEnabled()) vm.humanSpin()
        }
        // Let the post-spin pause elapse.
        var guard2 = 0
        while (!vm.isSpinEnabled() && vm.engine!!.phase == GamePhase.AWAIT_SPIN && guard2++ < 1000) {
            clock.advance(100)
            vm.tick(clock.t)
        }
    }
}
