package com.touchdevelopment.touchparchis.ui

import com.touchdevelopment.touchparchis.data.GameplayCoords
import com.touchdevelopment.touchparchis.engine.ParchisColor
import com.touchdevelopment.touchparchis.engine.Pawn
import com.touchdevelopment.touchparchis.engine.RouteIndex
import com.touchdevelopment.touchparchis.engine.RulesData

/**
 * Resolves every pawn to a normalized (plate-fraction) center plus a draw scale.
 *
 * Pure Kotlin so the same logic backs both the Canvas and any unit test. No pixel
 * coordinates are hard-coded here; everything comes from [GameplayCoords].
 */
object PawnPositioner {

    /** A pawn ready to draw at a normalized center. [scale] multiplies the base pawn width. */
    data class DrawPawn(
        val color: ParchisColor,
        val pawnId: Int,
        val index: Int,
        val cx: Float,
        val cy: Float,
        val scale: Float
    )

    /** Anchor for an animation endpoint. */
    sealed class Anchor {
        data class AtIndex(val index: Int) : Anchor()
        data class AtYard(val slot: Int) : Anchor()
    }

    /** Yard slot index for a pawn: the lowest free slot, filling gaps in pawn-id order. */
    fun yardSlot(pawns: Map<ParchisColor, List<Pawn>>, color: ParchisColor, pawnId: Int): Int {
        val list = pawns[color] ?: return pawnId
        val yard = list.filter { it.index == RouteIndex.YARD }.sortedBy { it.id }
        val rank = yard.indexOfFirst { it.id == pawnId }
        return if (rank >= 0) rank else pawnId
    }

    /** Normalized center for a pawn's position, using [rules] for route lookups. */
    fun anchorPoint(
        gc: GameplayCoords,
        rules: RulesData,
        color: ParchisColor,
        pawnId: Int,
        anchor: Anchor,
        yardSlot: Int = -1
    ): Pair<Float, Float> = when (anchor) {
        is Anchor.AtYard -> {
            val slot = gc.yards.getValue(color).slots[anchor.slot.coerceIn(0, 3)]
            slot.cx to slot.cy
        }
        is Anchor.AtIndex -> pointForIndex(gc, rules, color, pawnId, anchor.index, yardSlot)
    }

    private fun pointForIndex(
        gc: GameplayCoords,
        rules: RulesData,
        color: ParchisColor,
        pawnId: Int,
        index: Int,
        yardSlot: Int
    ): Pair<Float, Float> = when {
        index == RouteIndex.YARD -> {
            val slotIndex = (if (yardSlot >= 0) yardSlot else pawnId).coerceIn(0, 3)
            val slot = gc.yards.getValue(color).slots[slotIndex]
            slot.cx to slot.cy
        }
        index == RouteIndex.HOME -> {
            val slot = gc.homeBox.finishedSlots[pawnId.coerceIn(0, 3)]
            slot.cx to slot.cy
        }
        else -> {
            val square = rules.squareAt(color, index)
            val sc = gc.squares[square]
            if (sc != null) sc.cx to sc.cy else gc.homeBox.cx to gc.homeBox.cy
        }
    }

    /** Computes draw entries for all pawns, applying stacking offsets. */
    fun compute(
        gc: GameplayCoords,
        rules: RulesData,
        pawns: Map<ParchisColor, List<Pawn>>
    ): List<DrawPawn> {
        val out = mutableListOf<DrawPawn>()
        val board = mutableListOf<Pawn>()
        val stretch = mutableListOf<Pawn>()
        val home = mutableListOf<Pawn>()

        for ((_, list) in pawns) for (p in list) {
            when {
                p.index == RouteIndex.YARD -> {
                    val slot = yardSlot(pawns, p.color, p.id)
                    val s = gc.yards.getValue(p.color).slots[slot.coerceIn(0, 3)]
                    out += DrawPawn(p.color, p.id, p.index, s.cx, s.cy, 1f)
                }
                p.index == RouteIndex.HOME -> home += p
                p.index in RouteIndex.STRETCH_START..(RouteIndex.STRETCH_START + 6) -> stretch += p
                else -> board += p
            }
        }

        val bySquare = board.groupBy { rules.squareAt(it.color, it.index) }
        for ((square, group) in bySquare) {
            val sc = gc.squares[square] ?: continue
            placeGroup(gc, group, sc.cx, sc.cy, sc.w, sc.h, sc.longAxisX, out)
        }

        val byStretch = stretch.groupBy { rules.squareAt(it.color, it.index) }
        for ((square, group) in byStretch) {
            val sc = gc.squares[square] ?: continue
            placeGroup(gc, group, sc.cx, sc.cy, sc.w, sc.h, sc.longAxisX, out)
        }

        for (p in home) {
            val slot = gc.homeBox.finishedSlots[p.id.coerceIn(0, 3)]
            out += DrawPawn(p.color, p.id, p.index, slot.cx, slot.cy, 1f)
        }

        return out.sortedBy { if (it.index == RouteIndex.HOME) 0 else 1 }
    }

    private fun placeGroup(
        gc: GameplayCoords,
        group: List<Pawn>,
        cx: Float,
        cy: Float,
        w: Float,
        h: Float,
        longAxisX: Boolean,
        out: MutableList<DrawPawn>
    ) {
        if (group.size == 1) {
            val p = group[0]
            out += DrawPawn(p.color, p.id, p.index, cx, cy, 1f)
            return
        }
        val scale = gc.pawnSize.twoOnSquareScale
        val offset = gc.pawnSize.twoOnSquareOffset * (if (longAxisX) w else h)
        if (group.size == 2) {
            val (a, b) = group
            if (longAxisX) {
                out += DrawPawn(a.color, a.id, a.index, cx - offset, cy, scale)
                out += DrawPawn(b.color, b.id, b.index, cx + offset, cy, scale)
            } else {
                out += DrawPawn(a.color, a.id, a.index, cx, cy - offset, scale)
                out += DrawPawn(b.color, b.id, b.index, cx, cy + offset, scale)
            }
        } else {
            val o = offset * 0.9f
            val offsets = listOf(-o to -o, o to -o, -o to o, o to o)
            group.forEachIndexed { i, p ->
                val (dx, dy) = offsets[i % 4]
                out += DrawPawn(p.color, p.id, p.index, cx + dx, cy + dy, scale)
            }
        }
    }
}
