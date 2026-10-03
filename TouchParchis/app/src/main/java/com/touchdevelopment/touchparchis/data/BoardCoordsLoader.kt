package com.touchdevelopment.touchparchis.data

import com.touchdevelopment.touchparchis.engine.ParchisColor

/** Parses coords/board_coords.json. Every position is a fraction of the plate (never pixels). */
object BoardCoordsLoader {

    fun parse(text: String): BoardCoords {
        val root = Json.obj(Json.parse(text))
        val screens = Json.obj(root["screens"])

        val splash = screen(Json.obj(screens["splash"]))
        val win = screen(Json.obj(screens["win"]))
        val loss = screen(Json.obj(screens["loss"]))
        val gameplay = gameplay(Json.obj(screens["gameplay"]))

        return BoardCoords(splash, gameplay, win, loss)
    }

    private fun screen(o: Map<String, Any?>): ScreenCoords {
        val zones = Json.obj(o["zones"]).entries.associate { (k, v) -> k to rect(v) }
        return ScreenCoords(Json.str(o["plate_resource"]), zones)
    }

    private fun gameplay(o: Map<String, Any?>): GameplayCoords {
        val squares = Json.obj(o["squares"]).entries.associate { (k, v) ->
            val m = Json.obj(v)
            k.toInt() to SquareCoord(
                cx = f(m["cx"]), cy = f(m["cy"]), w = f(m["w"]), h = f(m["h"]),
                safe = Json.bool(m["safe"]),
                kind = Json.str(m["kind"]),
                longAxisX = Json.str(m["long_axis"]) == "x"
            )
        }

        val hb = Json.obj(o["home_box_5"])
        val homeBox = HomeBoxCoord(
            rect = rect(hb["rect"]),
            cx = f(hb["cx"]),
            cy = f(hb["cy"]),
            finishedSlots = Json.arr(hb["finished_slots"]).map {
                val m = Json.obj(it); NormPoint(f(m["cx"]), f(m["cy"]))
            }
        )

        val yards = Json.obj(o["yards"]).entries.associate { (k, v) ->
            val m = Json.obj(v)
            ParchisColor.valueOf(k.uppercase()) to YardCoord(
                rect = rect(m["rect"]),
                slots = Json.arr(m["slots"]).map {
                    val s = Json.obj(it); NormPoint(f(s["cx"]), f(s["cy"]))
                },
                labelStrip = rect(m["label_strip"]),
                color = ParchisColor.valueOf(Json.str(m["pawn_color"]).uppercase())
            )
        }

        val zones = Json.obj(o["zones"]).entries.associate { (k, v) -> k to rect(v) }

        val ps = Json.obj(o["pawn_size"])
        val pawnSize = PawnSize(
            boardW = f(ps["board_w"]),
            yardW = f(ps["yard_w"]),
            twoOnSquareScale = f(ps["two_on_square_scale"]),
            twoOnSquareOffset = f(ps["two_on_square_offset_of_cell_long_axis"])
        )

        return GameplayCoords(
            plateResource = Json.str(o["plate_resource"]),
            squares = squares, homeBox = homeBox, yards = yards,
            zones = zones, pawnSize = pawnSize
        )
    }

    private fun rect(v: Any?): NormRect {
        val m = Json.obj(v)
        return NormRect(f(m["x"]), f(m["y"]), f(m["w"]), f(m["h"]))
    }

    private fun f(v: Any?): Float = Json.num(v).toFloat()
}
