package com.touchdevelopment.touchparchis

import com.touchdevelopment.touchparchis.data.BoardCoordsLoader
import com.touchdevelopment.touchparchis.data.NormPoint
import com.touchdevelopment.touchparchis.ui.BoardLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Verifies aspect-fit mapping and that board_coords.json loads with every required entry.
 */
class BoardLayoutTest {

    private fun approx(a: Float, b: Float, eps: Float = 0.5f) =
        assertTrue("expected $a ~= $b", kotlin.math.abs(a - b) <= eps)

    @Test
    fun aspectFit_maps_corners_and_center_on_920_9195_921_screens() {
        val plateW = 691
        val plateH = 1536
        // 9:20, 9:19.5, 9:21 screens (taller and shorter than the plate).
        val screens = listOf(
            1080f to 2400f,   // 9:20
            1080f to 2340f,   // 9:19.5
            1080f to 2520f    // 9:21
        )
        for ((w, h) in screens) {
            val layout = BoardLayout(w, h, plateW, plateH)
            // The plate fits inside the view without cropping.
            assertTrue(layout.destW <= w + 0.01f)
            assertTrue(layout.destH <= h + 0.01f)
            // Aspect ratio preserved.
            approx(layout.destW / layout.destH, plateW.toFloat() / plateH, 0.001f)
            // Center of the plate maps to the center of the view.
            val (cx, cy) = layout.px(NormPoint(0.5f, 0.5f))
            approx(cx, w / 2f)
            approx(cy, h / 2f)
            // Corners map to the plate's destination corners.
            val (tlx, tly) = layout.px(NormPoint(0f, 0f))
            approx(tlx, layout.destX)
            approx(tly, layout.destY)
            val (brx, bry) = layout.px(NormPoint(1f, 1f))
            approx(brx, layout.destX + layout.destW)
            approx(bry, layout.destY + layout.destH)
        }
    }

    @Test
    fun boardCoordsJson_has_all_squares_zones_yards_and_slots() {
        val file = File("src/main/assets/board_coords.json")
        assertTrue("board_coords.json must exist at ${file.absolutePath}", file.exists())
        val coords = BoardCoordsLoader.parse(file.readText())

        // All board squares 6..101.
        for (s in 6..101) {
            assertTrue("missing square $s", coords.gameplay.squares.containsKey(s))
        }
        // Home Box 5 has four finished slots.
        assertEquals(4, coords.gameplay.homeBox.finishedSlots.size)
        // Four yards, four slots each.
        assertEquals(4, coords.gameplay.yards.size)
        for ((_, yard) in coords.gameplay.yards) assertEquals(4, yard.slots.size)
        // Gameplay zones.
        for (zone in listOf("REEL_1", "REEL_2", "SPIN", "PLAYER_AREA", "MESSAGE_AREA")) {
            assertTrue("missing zone $zone", coords.gameplay.zones.containsKey(zone))
        }
        // Splash zones.
        for (zone in listOf("OPP_1", "OPP_2", "OPP_3", "PLAY")) {
            assertTrue("missing splash zone $zone", coords.splash.zones.containsKey(zone))
        }
        // Result zones.
        assertTrue(coords.win.zones.containsKey("PLAY_AGAIN"))
        assertTrue(coords.loss.zones.containsKey("TRY_AGAIN"))
    }
}
