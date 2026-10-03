package com.touchdevelopment.touchparchis.ui

import com.touchdevelopment.touchparchis.data.NormPoint
import com.touchdevelopment.touchparchis.data.NormRect

/**
 * The only place that turns normalized fractions into screen pixels.
 *
 * A plate is drawn aspect-fit (largest size that fits without cropping or distortion)
 * and centered. All fractions are applied to that drawn rectangle. Letterbox bars are
 * filled with a single solid color. Pure Kotlin so it can be unit tested on the JVM.
 */
class BoardLayout(
    val viewWidth: Float,
    val viewHeight: Float,
    val plateWidth: Int,
    val plateHeight: Int
) {
    /** Aspect-fit destination rectangle for the plate, in view pixels. */
    val scale: Float = minOf(viewWidth / plateWidth, viewHeight / plateHeight)
    val destW: Float = plateWidth * scale
    val destH: Float = plateHeight * scale
    val destX: Float = (viewWidth - destW) / 2f
    val destY: Float = (viewHeight - destH) / 2f

    /** Maps a normalized point to a pixel center. */
    fun px(n: NormPoint): Pair<Float, Float> =
        destX + n.cx * destW to destY + n.cy * destH

    fun x(fraction: Float): Float = destX + fraction * destW
    fun y(fraction: Float): Float = destY + fraction * destH
    fun w(fraction: Float): Float = fraction * destW
    fun h(fraction: Float): Float = fraction * destH

    /** Maps a normalized rect to pixel left/top/width/height. */
    fun rect(r: NormRect): PixelRect =
        PixelRect(x(r.x), y(r.y), w(r.w), h(r.h))

    /** True when a pixel point lies inside a normalized rect. */
    fun hit(r: NormRect, px: Float, py: Float): Boolean {
        val p = rect(r)
        return px >= p.left && px <= p.left + p.width && py >= p.top && py <= p.top + p.height
    }

    data class PixelRect(val left: Float, val top: Float, val width: Float, val height: Float) {
        val centerX: Float get() = left + width / 2f
        val centerY: Float get() = top + height / 2f
        val right: Float get() = left + width
        val bottom: Float get() = top + height
    }
}
