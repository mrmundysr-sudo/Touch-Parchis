package com.touchdevelopment.touchparchis.data

import com.touchdevelopment.touchparchis.engine.ParchisColor

/** A normalized rectangle: x, y = top-left; w, h = size; all fractions of the plate. */
data class NormRect(val x: Float, val y: Float, val w: Float, val h: Float) {
    val cx: Float get() = x + w / 2f
    val cy: Float get() = y + h / 2f
}

/** A normalized point (a slot or square center). */
data class NormPoint(val cx: Float, val cy: Float)

/** One board square, indexed by its printed square number. */
data class SquareCoord(
    val cx: Float,
    val cy: Float,
    val w: Float,
    val h: Float,
    val safe: Boolean,
    val kind: String,
    val longAxisX: Boolean
)

data class YardCoord(
    val rect: NormRect,
    val slots: List<NormPoint>,
    val labelStrip: NormRect,
    val color: ParchisColor
)

data class HomeBoxCoord(
    val rect: NormRect,
    val cx: Float,
    val cy: Float,
    val finishedSlots: List<NormPoint>
)

data class PawnSize(
    val boardW: Float,
    val yardW: Float,
    val twoOnSquareScale: Float,
    val twoOnSquareOffset: Float
)

data class GameplayCoords(
    val plateResource: String,
    val squares: Map<Int, SquareCoord>,
    val homeBox: HomeBoxCoord,
    val yards: Map<ParchisColor, YardCoord>,
    val zones: Map<String, NormRect>,
    val pawnSize: PawnSize
)

data class ScreenCoords(val plateResource: String, val zones: Map<String, NormRect>)

/** All normalized coordinates for all four screens. */
data class BoardCoords(
    val splash: ScreenCoords,
    val gameplay: GameplayCoords,
    val win: ScreenCoords,
    val loss: ScreenCoords
)
