package com.touchdevelopment.touchparchis.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.SoundPool
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.touchdevelopment.touchparchis.R
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private fun loadKeyedSpriteSheet(context: Context): ImageBitmap {
    val source = context.assets.open("scoobert_sprite_sheet.png").use {
        BitmapFactory.decodeStream(it).copy(Bitmap.Config.ARGB_8888, true)
    }
    val pixels = IntArray(source.width * source.height)
    source.getPixels(pixels, 0, source.width, 0, 0, source.width, source.height)
    for (i in pixels.indices) {
        val p = pixels[i]
        val r = android.graphics.Color.red(p)
        val g = android.graphics.Color.green(p)
        val b = android.graphics.Color.blue(p)
        val matteDistance = kotlin.math.sqrt(
            ((r - 88) * (r - 88) + (g - 88) * (g - 88) + (b - 88) * (b - 88)).toDouble()
        )
        if (matteDistance < 42.0) pixels[i] = android.graphics.Color.TRANSPARENT
    }
    source.setPixels(pixels, 0, source.width, 0, 0, source.width, source.height)
    return source.asImageBitmap()
}

private data class CelebrationPiece(
    val colorIndex: Int,
    val x: Float,
    val startMs: Long,
    val durationMs: Long,
    val size: Float,
    val rotation: Float,
    val spin: Float,
    val drift: Float,
    val bounce: Float
)

private val pieceColors = listOf(
    com.touchdevelopment.touchparchis.engine.ParchisColor.RED,
    com.touchdevelopment.touchparchis.engine.ParchisColor.YELLOW,
    com.touchdevelopment.touchparchis.engine.ParchisColor.GREEN,
    com.touchdevelopment.touchparchis.engine.ParchisColor.BLUE
)

@Composable
fun ScoobertWinCelebrationOverlay() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val sheet = remember(context) { loadKeyedSpriteSheet(context) }
    val red = ImageBitmap.imageResource(R.drawable.pawn_board_red)
    val yellow = ImageBitmap.imageResource(R.drawable.pawn_board_yellow)
    val green = ImageBitmap.imageResource(R.drawable.pawn_board_green)
    val blue = ImageBitmap.imageResource(R.drawable.pawn_board_blue)
    val pawns = remember(red, yellow, green, blue) { listOf(red, yellow, green, blue) }
    val pieces = remember {
        Random(0x50415243).let { rng ->
            List(28) { i ->
                CelebrationPiece(
                    colorIndex = rng.nextInt(4),
                    x = 0.08f + rng.nextFloat() * 0.84f,
                    startMs = i * 190L + rng.nextLong(0L, 150L),
                    durationMs = 1250L + rng.nextLong(0L, 450L),
                    size = 0.045f + rng.nextFloat() * 0.025f,
                    rotation = rng.nextFloat() * 360f,
                    spin = (if (rng.nextBoolean()) 1f else -1f) * (70f + rng.nextFloat() * 130f),
                    drift = (rng.nextFloat() - 0.5f) * 0.16f,
                    bounce = (rng.nextFloat() - 0.5f) * 0.07f
                )
            }
        }
    }
    var now by remember { mutableLongStateOf(0L) }
    val audio = remember(context) { WinCelebrationSoundController(context) }

    DisposableEffect(audio) { onDispose { audio.release() } }
    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { now = it / 1_000_000L }
            audio.update(now, pieces)
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        Canvas(Modifier.fillMaxSize()) {
            val frame = ((now / 133L) % 15L).toInt()
            val col = frame % 5
            val row = frame / 5
            val srcW = sheet.width / 5
            val srcH = sheet.height / 3
            val scoobertW = size.width * 0.36f
            val scoobertH = scoobertW * srcH.toFloat() / srcW.toFloat()
            drawImage(
                sheet,
                srcOffset = IntOffset(col * srcW, row * srcH),
                srcSize = IntSize(srcW, srcH),
                dstOffset = IntOffset(((size.width - scoobertW) / 2f).toInt(), (size.height * 0.31f).toInt()),
                dstSize = IntSize(scoobertW.toInt(), scoobertH.toInt())
            )

            for (piece in pieces) {
                val elapsed = now - piece.startMs
                if (elapsed < 0L || elapsed > piece.durationMs + 300L) continue
                val t = (elapsed.toFloat() / piece.durationMs).coerceIn(0f, 1.35f)
                val fall = if (t <= 1f) t * t else 1f
                val x = (piece.x + piece.drift * sin(t * PI).toFloat()) * size.width
                val ground = size.height * 0.83f
                val y = -size.height * 0.10f + fall * (ground + size.height * 0.10f)
                val impactT = ((t - 1f) / 0.35f).coerceIn(0f, 1f)
                val bounceY = if (impactT > 0f) -sin(impactT * PI).toFloat() * size.height * piece.bounce else 0f
                val squash = if (impactT > 0f) 1f + sin(impactT * PI).toFloat() * 0.12f else 1f
                val img = pawns[piece.colorIndex]
                val pw = size.width * piece.size
                val ph = pw * img.height.toFloat() / img.width.toFloat()
                scale(squash, 1f / squash, Offset(x, y + bounceY)) {
                    rotate(piece.rotation + piece.spin * t, Offset(x, y + bounceY)) {
                        drawImage(img, dstOffset = IntOffset((x - pw / 2f).toInt(), (y + bounceY - ph / 2f).toInt()), dstSize = IntSize(pw.toInt(), ph.toInt()))
                    }
                }
            }
        }
    }
}

private class WinCelebrationSoundController(context: Context) {
    private val pool = SoundPool.Builder().setMaxStreams(3).setAudioAttributes(
        AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
    ).build()
    private val whoosh = pool.load(context, R.raw.slot_loop, 1)
    private val impact = pool.load(context, R.raw.slot_stop, 1)
    private var lastWhoosh = Long.MIN_VALUE
    private var lastImpact = Long.MIN_VALUE

    fun update(now: Long, pieces: List<CelebrationPiece>) {
        for (piece in pieces) {
            if (now >= piece.startMs && lastWhoosh < piece.startMs) {
                lastWhoosh = piece.startMs
                pool.play(whoosh, 0.10f, 0.10f, 1, 0, 0.86f + (piece.colorIndex * 0.04f))
            }
            val landing = piece.startMs + piece.durationMs
            if (now >= landing && lastImpact < landing) {
                lastImpact = landing
                pool.play(impact, 0.18f, 0.18f, 1, 0, 0.92f + (piece.colorIndex * 0.035f))
            }
        }
    }

    fun release() = pool.release()
}
