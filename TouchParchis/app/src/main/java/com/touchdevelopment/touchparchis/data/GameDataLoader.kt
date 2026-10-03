package com.touchdevelopment.touchparchis.data

import com.touchdevelopment.touchparchis.engine.ParchisColor
import com.touchdevelopment.touchparchis.engine.RouteIndex
import com.touchdevelopment.touchparchis.engine.RulesData

/** Parses data/game_data.json into [RulesData]. */
object GameDataLoader {

    fun parse(text: String): RulesData {
        val root = Json.obj(Json.parse(text))

        val seats = Json.obj(root["seats"])
        val human = color(Json.str(seats["human"]))
        val turnOrder = Json.arr(seats["turn_order"]).map { color(Json.str(it)) }

        val activeAi = Json.obj(seats["active_ai"]).entries.associate { (k, v) ->
            k.toInt() to Json.arr(v).map { color(Json.str(it)) }
        }

        val startSquares = Json.obj(root["start_squares"]).entries.associate { (k, v) ->
            color(k) to Json.int(v)
        }
        val lastTrack = Json.obj(root["last_track_square"]).entries.associate { (k, v) ->
            color(k) to Json.int(v)
        }
        val homeStretch = Json.obj(root["home_stretch"]).entries.associate { (k, v) ->
            val pair = Json.arr(v)
            color(k) to (Json.int(pair[0])..Json.int(pair[1]))
        }
        val safe = Json.arr(root["safe_squares"]).map { Json.int(it) }.toSet()

        val routesObj = Json.obj(root["routes"])
        val routes = ParchisColor.values().associateWith { c ->
            val list = Json.arr(routesObj[c.lower])
            // The JSON lists route indices 1..72 (72 entries); the engine prepends the yard.
            require(list.size == 72) { "route ${c.lower} must have 72 entries (indices 1..72)" }
            IntArray(73) { idx ->
                when {
                    idx == RouteIndex.YARD -> RulesData.YARD_SENTINEL
                    else -> {
                        val v = list[idx - 1]
                        if (v is String) RouteIndex.HOME_SQUARE else Json.int(v)
                    }
                }
            }
        }

        return RulesData(
            turnOrder = turnOrder,
            humanColor = human,
            activeAiByOpponents = activeAi,
            startSquares = startSquares,
            lastTrackSquare = lastTrack,
            homeStretchRange = homeStretch,
            safeSquares = safe,
            routes = routes
        )
    }

    private fun color(name: String): ParchisColor = ParchisColor.valueOf(name.uppercase())
}
