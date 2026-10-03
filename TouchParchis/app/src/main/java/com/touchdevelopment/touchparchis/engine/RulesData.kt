package com.touchdevelopment.touchparchis.engine

/**
 * Immutable rules data: route tables, start squares, safe squares, and seat configuration.
 *
 * This is the single source of truth for *where* a route index points. Game logic never
 * computes squares by arithmetic; it looks them up here (RULES.md §4).
 */
class RulesData(
    val turnOrder: List<ParchisColor>,
    val humanColor: ParchisColor,
    val activeAiByOpponents: Map<Int, List<ParchisColor>>,
    val startSquares: Map<ParchisColor, Int>,
    val lastTrackSquare: Map<ParchisColor, Int>,
    val homeStretchRange: Map<ParchisColor, IntRange>,
    val safeSquares: Set<Int>,
    private val routes: Map<ParchisColor, IntArray>
) {
    init {
        for (c in ParchisColor.values()) {
            val r = routes[c] ?: error("missing route for $c")
            require(r.size == 73) { "route for $c must have 73 entries (index 0..72)" }
        }
    }

    /** Square number at a route index. Index 0 is the yard sentinel; index 72 is Home Box 5. */
    fun squareAt(color: ParchisColor, index: Int): Int {
        require(index in 0..RouteIndex.HOME) { "route index out of range: $index" }
        if (index == RouteIndex.YARD) return RouteIndex.HOME_SQUARE
        return routes.getValue(color)[index]
    }

    fun route(color: ParchisColor): IntArray = routes.getValue(color)

    fun startSquare(color: ParchisColor): Int = startSquares.getValue(color)

    fun isSafeSquare(square: Int): Boolean = square in safeSquares

    /** Active seats (human + chosen AI opponents), in fixed turn order. */
    fun activeSeats(opponents: Int): List<ParchisColor> {
        val ai = activeAiByOpponents.getValue(opponents)
        return turnOrder.filter { it == humanColor || it in ai }
    }

    companion object {
        const val YARD_SENTINEL = -2

        /** Builds rules data from explicit route tables (used by tests and the JSON loader). */
        fun of(
            turnOrder: List<ParchisColor> = listOf(
                ParchisColor.RED, ParchisColor.YELLOW, ParchisColor.GREEN, ParchisColor.BLUE
            ),
            humanColor: ParchisColor = ParchisColor.RED,
            activeAiByOpponents: Map<Int, List<ParchisColor>> = mapOf(
                1 to listOf(ParchisColor.BLUE),
                2 to listOf(ParchisColor.GREEN, ParchisColor.BLUE),
                3 to listOf(ParchisColor.YELLOW, ParchisColor.GREEN, ParchisColor.BLUE)
            ),
            startSquares: Map<ParchisColor, Int> = mapOf(
                ParchisColor.RED to 10, ParchisColor.YELLOW to 27,
                ParchisColor.GREEN to 44, ParchisColor.BLUE to 61
            ),
            lastTrackSquare: Map<ParchisColor, Int> = mapOf(
                ParchisColor.RED to 73, ParchisColor.YELLOW to 22,
                ParchisColor.GREEN to 39, ParchisColor.BLUE to 56
            ),
            homeStretchRange: Map<ParchisColor, IntRange> = mapOf(
                ParchisColor.RED to 74..80, ParchisColor.YELLOW to 81..87,
                ParchisColor.GREEN to 88..94, ParchisColor.BLUE to 95..101
            ),
            safeSquares: Set<Int> = setOf(10, 17, 22, 27, 34, 39, 44, 51, 56, 61, 68, 73),
            routes: Map<ParchisColor, IntArray> = defaultRoutes()
        ): RulesData = RulesData(
            turnOrder, humanColor, activeAiByOpponents, startSquares,
            lastTrackSquare, homeStretchRange, safeSquares, routes
        )

        /** Route tables exactly as specified in RULES.md §4 / data/game_data.json. */
        fun defaultRoutes(): Map<ParchisColor, IntArray> = mapOf(
            ParchisColor.RED to buildRoute(listOf(10..73), 74..80),
            ParchisColor.YELLOW to buildRoute(listOf(27..73, 6..22), 81..87),
            ParchisColor.GREEN to buildRoute(listOf(44..73, 6..39), 88..94),
            ParchisColor.BLUE to buildRoute(listOf(61..73, 6..56), 95..101)
        )

        private fun buildRoute(trackSegments: List<IntRange>, stretch: IntRange): IntArray {
            val arr = IntArray(73)
            arr[0] = YARD_SENTINEL
            var i = 1
            for (seg in trackSegments) for (s in seg) { arr[i++] = s }
            for (s in stretch) { arr[i++] = s }
            arr[i] = RouteIndex.HOME_SQUARE
            require(i == RouteIndex.HOME) { "route must fill indices 1..72, ended at $i" }
            return arr
        }
    }
}
