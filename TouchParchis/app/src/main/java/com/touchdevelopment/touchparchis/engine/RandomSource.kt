package com.touchdevelopment.touchparchis.engine

/**
 * Injectable random source so tests can fix reel values and AI choices.
 * All engine randomness flows through this interface.
 */
interface RandomSource {
    /** Uniform integer in [from, to] inclusive. */
    fun nextInt(from: Int, to: Int): Int

    /** Uniform float in [0.0, 1.0). */
    fun nextFloat(): Float
}

/** Default source backed by kotlin.random.Random. */
class KotlinRandomSource(seed: Long? = null) : RandomSource {
    private val rnd: kotlin.random.Random =
        if (seed == null) kotlin.random.Random.Default else kotlin.random.Random(seed)

    override fun nextInt(from: Int, to: Int): Int = rnd.nextInt(from, to + 1)

    override fun nextFloat(): Float = rnd.nextFloat()
}

/** Deterministic source for tests: returns a fixed script of reel values, then falls back. */
class ScriptedRandomSource(private val values: List<Int>, private val fallback: RandomSource = KotlinRandomSource(1L)) : RandomSource {
    private var i = 0
    override fun nextInt(from: Int, to: Int): Int {
        if (from == 1 && to == 6 && i < values.size) return values[i++]
        return fallback.nextInt(from, to)
    }
    override fun nextFloat(): Float = fallback.nextFloat()
}
