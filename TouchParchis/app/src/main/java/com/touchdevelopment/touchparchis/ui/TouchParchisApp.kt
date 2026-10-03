package com.touchdevelopment.touchparchis.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.touchdevelopment.touchparchis.R
import com.touchdevelopment.touchparchis.data.GameplayCoords
import com.touchdevelopment.touchparchis.data.NormPoint
import com.touchdevelopment.touchparchis.data.NormRect
import com.touchdevelopment.touchparchis.engine.ParchisColor
import com.touchdevelopment.touchparchis.engine.ReelSlot
import com.touchdevelopment.touchparchis.engine.RouteIndex
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

private val GOLD = Color(0xFFFFD24A)
private val LETTERBOX = Color(0xFF4A2A18)
private val DEST_HIGHLIGHT = Color(0xCC44DD66)
private val DEST_TOTAL = Color(0xCCFF8A2B)
private val SELECT_GLOW = Color(0xFFFFE07A)
private val LEGAL_PAWN_GLOW = Color(0x99FFE07A)
private val SAFE_HIGHLIGHT = Color(0x9957C7FF)

private const val REEL_SYMBOLS_PER_SECOND = 20f
private const val REEL_ACCEL_MS = 250L
private const val REEL_DECEL_MS = 450L
private const val REEL_BOUNCE_MS = 150L
private const val LEVER_ANIMATION_MS = 300L
private const val SLOT_SOUND_VOLUME = 0.68f

private val TEXT_LIGHT = 0xFFFFF3D6.toInt()
private val TEXT_DARK = 0xFF3B1E0E.toInt()

@Composable
fun TouchParchisApp(viewModel: GameViewModel, onExit: () -> Unit) {
    var showQuit by remember { mutableStateOf(false) }
    var frameNow by remember { mutableLongStateOf(0L) }
    val context = LocalContext.current
    val reelSounds = remember(context) { SlotReelSoundController(context) }

    androidx.compose.runtime.DisposableEffect(reelSounds) {
        onDispose { reelSounds.release() }
    }
    val lifecycleOwner = context as? LifecycleOwner
    androidx.compose.runtime.DisposableEffect(lifecycleOwner, reelSounds) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) reelSounds.pause()
        }
        lifecycleOwner?.lifecycle?.addObserver(observer)
        onDispose {
            lifecycleOwner?.lifecycle?.removeObserver(observer)
            reelSounds.pause()
        }
    }

    LaunchedEffect(viewModel.screen, reelSounds) {
        while (viewModel.screen == Screen.GAMEPLAY) {
            withFrameNanos { frame ->
                frameNow = frame / 1_000_000L
                viewModel.tick(frameNow)
                reelSounds.update(viewModel, frameNow)
            }
        }
    }

    BackHandler(enabled = true) {
        when (viewModel.screen) {
            Screen.SPLASH -> onExit()
            Screen.GAMEPLAY -> showQuit = true
            Screen.WIN, Screen.LOSS -> viewModel.reset()
        }
    }

    if (showQuit) {
        AlertDialog(
            onDismissRequest = { showQuit = false },
            title = { Text(stringResource(R.string.quit_title)) },
            text = { Text(stringResource(R.string.quit_message)) },
            confirmButton = {
                TextButton(onClick = { showQuit = false; viewModel.reset() }) { Text(stringResource(R.string.quit_yes)) }
            },
            dismissButton = {
                TextButton(onClick = { showQuit = false }) { Text(stringResource(R.string.quit_no)) }
            }
        )
    }

    when (viewModel.screen) {
        Screen.SPLASH -> SplashScreen(viewModel)
        Screen.GAMEPLAY -> GameplayScreen(viewModel, frameNow)
        Screen.WIN -> ResultScreen(viewModel, R.drawable.screen_win, "PLAY_AGAIN", R.string.play_again)
        Screen.LOSS -> ResultScreen(viewModel, R.drawable.screen_loss, "TRY_AGAIN", R.string.try_again)
    }
}

// Digit faces are rasterized once per display size, then transformed during reel animation.
private object ReelDigitSprites {
    private val cache = android.util.LruCache<Int, Array<Bitmap>>(8)

    fun get(sizePx: Float): Array<Bitmap> {
        val key = sizePx.roundToInt().coerceAtLeast(1)
        return cache.get(key) ?: make(key).also { cache.put(key, it) }
    }

    private fun make(size: Int): Array<Bitmap> = (1..6).map { value ->
        val w = (size * 1.10f).roundToInt().coerceAtLeast(1)
        val h = (size * 1.36f).roundToInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        val baseline = h / 2f - (Paint().apply { textSize = size.toFloat() }.let { (it.ascent() + it.descent()) / 2f })
        val base = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
            textSize = size.toFloat()
            strokeJoin = Paint.Join.ROUND
            style = Paint.Style.STROKE
        }
        base.strokeWidth = size * 0.17f
        base.color = android.graphics.Color.rgb(0x3B, 0x12, 0x05)
        base.setShadowLayer(size * 0.08f, 0f, size * 0.045f, android.graphics.Color.rgb(0x35, 0x0C, 0x00))
        canvas.drawText(value.toString(), w / 2f, baseline, base)
        base.clearShadowLayer()
        base.strokeWidth = size * 0.095f
        base.color = android.graphics.Color.rgb(0xFF, 0xD2, 0x3B)
        canvas.drawText(value.toString(), w / 2f, baseline, base)
        base.style = Paint.Style.FILL
        base.color = android.graphics.Color.rgb(0xF0, 0x28, 0x1C)
        base.setShadowLayer(size * 0.035f, 0f, size * 0.02f, android.graphics.Color.rgb(0x70, 0x08, 0x00))
        canvas.drawText(value.toString(), w / 2f, baseline, base)
        base.clearShadowLayer()
        base.textSize = size * 0.74f
        base.color = android.graphics.Color.argb(90, 0xFF, 0xF5, 0xD2)
        canvas.drawText(value.toString(), w / 2f - size * 0.018f, baseline - size * 0.035f, base)
        bitmap
    }.toTypedArray()
}

// ---------------------------------------------------------------------------
// Splash
// ---------------------------------------------------------------------------

@Composable
private fun SplashScreen(vm: GameViewModel) {
    val plate = ImageBitmap.imageResource(R.drawable.screen_splash)
    val zones = vm.coords.splash.zones
    val playLabel = stringResource(R.string.splash_play)
    val prompt = stringResource(R.string.splash_choose_prompt)

    // The prompt pulse: a short 3-blink animation driven by the frame clock.
    var pulsePhase by remember { mutableStateOf(0) }
    LaunchedEffect(vm.splashPulse) {
        if (!vm.splashPulse) return@LaunchedEffect
        pulsePhase = 0
        while (pulsePhase < 6) {
            withFrameNanos { }
            kotlinx.coroutines.delay(160)
            pulsePhase++
        }
        vm.clearSplashPulse()
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val layout = remember(w, h) { BoardLayout(w, h, plate.width, plate.height) }

        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { off ->
                        when {
                            layout.hit(zones.getValue("OPP_1"), off.x, off.y) -> vm.selectOpponents(1)
                            layout.hit(zones.getValue("OPP_2"), off.x, off.y) -> vm.selectOpponents(2)
                            layout.hit(zones.getValue("OPP_3"), off.x, off.y) -> vm.selectOpponents(3)
                            layout.hit(zones.getValue("PLAY"), off.x, off.y) -> vm.play()
                        }
                    }
                }
        ) {
            drawRect(LETTERBOX, size = Size(size.width, size.height))
            drawPlate(layout, plate)

            for (n in 1..3) {
                val zone = zones.getValue("OPP_$n")
                when {
                    vm.selectedOpponents == n -> {
                        drawOutline(layout.rect(zone), GOLD, layout.w(0.008f))
                        drawBadge(layout, layout.rect(zone), n.toString(), GOLD)
                    }
                    vm.splashPulse && pulsePhase % 2 == 0 -> {
                        drawOutline(layout.rect(zone), GOLD, layout.w(0.007f))
                    }
                }
            }

            // The PLAY button is blank on the plate: draw its label in code.
            drawCenteredText(layout, zones.getValue("PLAY"), playLabel, layout.w(0.052f), TEXT_LIGHT, bold = true)

            if (vm.showChoosePrompt) {
                val r = zones.getValue("PLAY")
                drawCenteredText(layout, NormRect(r.x, (r.y - 0.06f).coerceAtLeast(0f), r.w, 0.04f), prompt, layout.w(0.032f), GOLD.toArgb(), bold = true)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Gameplay
// ---------------------------------------------------------------------------

@Composable
private fun GameplayScreen(vm: GameViewModel, frameNow: Long) {
    val plate = ImageBitmap.imageResource(R.drawable.screen_gameplay)
    val redPawn = ImageBitmap.imageResource(R.drawable.pawn_board_red)
    val yellowPawn = ImageBitmap.imageResource(R.drawable.pawn_board_yellow)
    val greenPawn = ImageBitmap.imageResource(R.drawable.pawn_board_green)
    val bluePawn = ImageBitmap.imageResource(R.drawable.pawn_board_blue)
    val pawns = remember(redPawn, yellowPawn, greenPawn, bluePawn) {
        mapOf(
            ParchisColor.RED to redPawn,
            ParchisColor.YELLOW to yellowPawn,
            ParchisColor.GREEN to greenPawn,
            ParchisColor.BLUE to bluePawn
        )
    }
    val gc = vm.coords.gameplay
    val notPlayingLabel = stringResource(R.string.not_playing)
    val spinLabel = stringResource(R.string.spin)
    val messageText = vm.message?.let { m ->
        if (m.color != null) stringResource(m.resId, stringResource(vm.colorNameRes(m.color)))
        else stringResource(m.resId)
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val layout = remember(w, h) { BoardLayout(w, h, plate.width, plate.height) }
        val density = LocalDensity.current
        val minTouchPx = with(density) { 48.dp.toPx() }

        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { off -> handleGameplayTap(vm, layout, off.x, off.y, minTouchPx) }
                }
        ) {
            GameplayRenderer.draw(
                scope = this,
                layout = layout,
                gc = gc,
                vm = vm,
                plate = plate,
                pawnSprites = pawns,
                notPlayingLabel = notPlayingLabel,
                spinLabel = spinLabel,
                messageText = messageText,
                now = frameNow
            )
        }
    }
}

private fun handleGameplayTap(vm: GameViewModel, layout: BoardLayout, px: Float, py: Float, minTouchPx: Float) {
    val e = vm.engine ?: return
    val gc = vm.coords.gameplay

    if (layout.hit(gc.zones.getValue("REEL_1"), px, py)) {
        if (vm.choices != null) vm.humanPlayReel(ReelSlot.R1)
        return
    }
    if (layout.hit(gc.zones.getValue("REEL_2"), px, py)) {
        if (vm.choices != null) vm.humanPlayReel(ReelSlot.R2)
        return
    }
    if (layout.hit(gc.zones.getValue("SPIN"), px, py)) {
        vm.humanSpin()
        return
    }

    val choices = vm.choices
    if (choices != null) {
        for (index in choices.destinations.keys) {
            val (cx, cy) = PawnPositioner.anchorPoint(gc, vm.rules, choices.color, choices.pawnId, PawnPositioner.Anchor.AtIndex(index))
            val (dpx, dpy) = layout.px(NormPoint(cx, cy))
            if (hypot(px - dpx, py - dpy) <= maxOf(minTouchPx, layout.w(0.045f))) {
                vm.humanPlayDestination(index)
                return
            }
        }
    }

    val drawPawns = PawnPositioner.compute(gc, vm.rules, e.pawns)
    var best: PawnPositioner.DrawPawn? = null
    var bestDist = Float.MAX_VALUE
    for (dp in drawPawns) {
        if (!vm.isPawnTappable(dp.color, dp.pawnId)) continue
        val (ppx, ppy) = layout.px(NormPoint(dp.cx, dp.cy))
        val d = hypot(px - ppx, py - ppy)
        if (d < bestDist) { bestDist = d; best = dp }
    }
    if (best != null && bestDist <= maxOf(minTouchPx, layout.w(0.055f))) {
        vm.humanSelectPawn(best.color, best.pawnId)
        return
    }

    if (layout.hit(gc.zones.getValue("PLAYER_AREA"), px, py)) {
        vm.humanEnter()
        return
    }

    vm.clearChoices()
}

// ---------------------------------------------------------------------------
// Result screens
// ---------------------------------------------------------------------------

@Composable
private fun ResultScreen(vm: GameViewModel, plateRes: Int, zone: String, labelRes: Int) {
    val plate = ImageBitmap.imageResource(plateRes)
    val zoneRect = (if (plateRes == R.drawable.screen_win) vm.coords.win else vm.coords.loss).zones.getValue(zone)
    val label = stringResource(labelRes)

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val layout = remember(w, h) { BoardLayout(w, h, plate.width, plate.height) }

        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { off -> if (layout.hit(zoneRect, off.x, off.y)) vm.reset() }
                }
        ) {
            drawRect(LETTERBOX, size = Size(size.width, size.height))
            drawPlate(layout, plate)
            // Only the result button is tappable; draw its label over the plate button.
            drawCenteredText(layout, zoneRect, label, layout.w(0.052f), TEXT_LIGHT, bold = true)
        }
    }
}

// ---------------------------------------------------------------------------
// Rendering
// ---------------------------------------------------------------------------

object GameplayRenderer {

    fun draw(
        scope: DrawScope,
        layout: BoardLayout,
        gc: GameplayCoords,
        vm: GameViewModel,
        plate: ImageBitmap,
        pawnSprites: Map<ParchisColor, ImageBitmap>,
        notPlayingLabel: String,
        spinLabel: String,
        messageText: String?,
        now: Long
    ) {
        with(scope) {
            drawRect(LETTERBOX, size = Size(size.width, size.height))
            drawPlate(layout, plate)

            val e = vm.engine ?: return

            // Inactive-yard overlays (code only).
            for (color in ParchisColor.values().filter { it !in e.activeSeats }) {
                val yard = gc.yards.getValue(color)
                val r = layout.rect(yard.rect)
                drawRect(Color(0x8C000000), topLeft = Offset(r.left, r.top), size = Size(r.width, r.height))
                drawCenteredText(layout, yard.rect, notPlayingLabel, layout.w(0.026f), Color.White.toArgb(), bold = true)
            }

            // Safety-square tint (subtle, code-drawn) to communicate the brown squares.
            for (sc in gc.squares.values) {
                if (!sc.safe) continue
                val cell = NormRect(sc.cx - sc.w / 2f, sc.cy - sc.h / 2f, sc.w, sc.h)
                val r = layout.rect(cell)
                drawRoundRect(
                    color = SAFE_HIGHLIGHT,
                    topLeft = Offset(r.left, r.top), size = Size(r.width, r.height),
                    cornerRadius = CornerRadius(layout.w(0.008f)), style = Stroke(width = layout.w(0.0025f))
                )
            }

            // Destination highlights for the selected pawn.
            vm.choices?.let { choices ->
                for ((index, combined) in choices.destinations) {
                    val cell = cellRectForIndex(gc, vm.rules, choices.color, index)
                    val r = layout.rect(cell)
                    drawRoundRect(
                        color = if (combined) DEST_TOTAL else DEST_HIGHLIGHT,
                        topLeft = Offset(r.left, r.top), size = Size(r.width, r.height),
                        cornerRadius = CornerRadius(layout.w(0.01f))
                    )
                }
            }

            // Pawns: finished (Home Box 5) first, then board/yard.
            val animByKey = vm.animations.associateBy { it.key }
            val drawPawns = PawnPositioner.compute(gc, vm.rules, e.pawns)
            val boardBase = layout.w(gc.pawnSize.boardW)
            val yardBase = layout.w(gc.pawnSize.yardW)

            // Glow under any pawn the human can legally move.
            for (dp in drawPawns) {
                if (!vm.isPawnHighlighted(dp.color, dp.pawnId)) continue
                val cxp = layout.x(dp.cx)
                val cyp = layout.y(dp.cy)
                drawCircle(LEGAL_PAWN_GLOW, radius = boardBase * 0.62f, center = Offset(cxp, cyp), style = Stroke(width = layout.w(0.005f)))
            }

            for (dp in drawPawns) {
                val anim = animByKey[dp.color to dp.pawnId]
                val base = if (dp.index == RouteIndex.YARD) yardBase else boardBase
                val sprite = pawnSprites[dp.color] ?: continue
                val cxp: Float
                val cyp: Float
                if (anim != null) {
                    val t = ((now - anim.startMs).toFloat() / anim.durationMs).coerceIn(0f, 1f)
                    val ease = t * t * (3 - 2 * t)
                    cxp = layout.x(anim.fromCx + (anim.toCx - anim.fromCx) * ease)
                    cyp = layout.y(anim.fromCy + (anim.toCy - anim.fromCy) * ease)
                } else {
                    cxp = layout.x(dp.cx)
                    cyp = layout.y(dp.cy)
                }
                val wpx = base * dp.scale
                val hpx = wpx * (sprite.height.toFloat() / sprite.width.toFloat())
                drawImage(
                    image = sprite,
                    dstOffset = IntOffset((cxp - wpx / 2f).roundToInt(), (cyp - hpx / 2f).roundToInt()),
                    dstSize = IntSize(wpx.roundToInt(), hpx.roundToInt())
                )
            }

            // Selected-pawn glow.
            vm.choices?.let { choices ->
                val idx = e.pawns.getValue(choices.color).first { it.id == choices.pawnId }.index
                val (cx, cy) = PawnPositioner.anchorPoint(gc, vm.rules, choices.color, choices.pawnId, PawnPositioner.Anchor.AtIndex(idx))
                val (ppx, ppy) = layout.px(NormPoint(cx, cy))
                drawCircle(SELECT_GLOW, radius = boardBase * 0.72f, center = Offset(ppx, ppy), style = Stroke(width = layout.w(0.006f)))
            }

            // Slot cabinet, reels, spin button, opening-spin totals, message.
            drawSlotCabinet(layout, gc, vm, now)
            drawReels(layout, gc, vm, now)
            drawDoublesFlourish(layout, gc, vm, now)
            drawAiReelDisplays(layout, gc, vm, now)
            drawSpinButton(layout, gc, vm, spinLabel)
            drawMessage(layout, gc, messageText)
        }
    }

    private fun cellRectForIndex(gc: GameplayCoords, rules: com.touchdevelopment.touchparchis.engine.RulesData, color: ParchisColor, index: Int): NormRect {
        if (index == RouteIndex.HOME) return gc.homeBox.rect
        val sc = gc.squares[rules.squareAt(color, index)] ?: return gc.homeBox.rect
        return NormRect(sc.cx - sc.w / 2f, sc.cy - sc.h / 2f, sc.w, sc.h)
    }

    private fun DrawScope.drawSlotCabinet(layout: BoardLayout, gc: GameplayCoords, vm: GameViewModel, now: Long) {
        val one = layout.rect(gc.zones.getValue("REEL_1"))
        val two = layout.rect(gc.zones.getValue("REEL_2"))
        val left = minOf(one.left, two.left) - layout.w(0.018f)
        val top = minOf(one.top, two.top) - layout.h(0.012f)
        val right = maxOf(one.right, two.right) + layout.w(0.018f)
        val bottom = maxOf(one.bottom, two.bottom) + layout.h(0.012f)
        val cabinet = Size(right - left, bottom - top)
        val radius = CornerRadius(layout.w(0.028f))
        drawRoundRect(Color(0xAA160B06), Offset(left + layout.w(0.008f), top + layout.h(0.010f)), cabinet, radius)
        drawRoundRect(Brush.verticalGradient(listOf(Color(0xFFFFF0A0), Color(0xFFD88A12), Color(0xFF7A3508), Color(0xFFF6C64A))), Offset(left, top), cabinet, radius)
        drawRoundRect(Color(0xFF3A1708), Offset(left + layout.w(0.010f), top + layout.h(0.010f)), Size(cabinet.width - layout.w(0.020f), cabinet.height - layout.h(0.020f)), CornerRadius(layout.w(0.020f)))
        for (window in listOf(one, two)) {
            // Premium chrome rim and deep glass interior.
            drawRoundRect(Brush.verticalGradient(listOf(Color(0xFFFFF7D0), Color(0xFFD99A25), Color(0xFF6E2A08), Color(0xFFFFD45A))), Offset(window.left - layout.w(0.007f), window.top - layout.h(0.006f)), Size(window.width + layout.w(0.014f), window.height + layout.h(0.012f)), CornerRadius(layout.w(0.014f)))
            // Bright porcelain reel bed, with darker side rails to make the moving
            // number strip read as a real vertical reel behind glass.
            drawRoundRect(
                Brush.verticalGradient(listOf(Color(0xFFFFF8DF), Color(0xFFFFE9B0), Color(0xFFFFF6D8), Color(0xFFE9C77B))),
                Offset(window.left, window.top), Size(window.width, window.height), CornerRadius(layout.w(0.010f))
            )
            drawRoundRect(
                Brush.horizontalGradient(listOf(Color(0x663A1608), Color.Transparent, Color.Transparent, Color(0x663A1608))),
                Offset(window.left, window.top), Size(window.width, window.height), CornerRadius(layout.w(0.010f))
            )
            drawRoundRect(Color(0xFFFFE8A0), Offset(window.left, window.top), Size(window.width, window.height), CornerRadius(layout.w(0.010f)), style = Stroke(width = layout.w(0.004f)))
            drawRoundRect(Color(0x66FFFFFF), Offset(window.left + layout.w(0.008f), window.top + layout.h(0.008f)), Size(window.width - layout.w(0.016f), window.height * 0.12f), CornerRadius(layout.w(0.006f)))
        }
        val dividerX = (one.right + two.left) / 2f
        drawLine(Color(0xFF2A1008), Offset(dividerX, top + layout.h(0.010f)), Offset(dividerX, bottom - layout.h(0.010f)), layout.w(0.004f))
        val leverX = right + layout.w(0.026f)
        val leverTop = top + cabinet.height * 0.16f
        val pullElapsed = if (vm.isHumanReelSpinActive()) vm.reelAnimationElapsed(now) else -1L
        val pull = if (pullElapsed in 0..LEVER_ANIMATION_MS) sin(Math.PI * pullElapsed / LEVER_ANIMATION_MS).toFloat() else 0f
        val leverShift = layout.h(0.030f) * pull
        drawLine(Color(0xFF6A2B09), Offset(leverX, leverTop + leverShift), Offset(leverX, leverTop + leverShift + layout.h(0.070f)), layout.w(0.012f))
        drawCircle(Color(0xFF8B0E12), layout.w(0.028f), Offset(leverX, leverTop + leverShift))
        drawCircle(Color(0xFFFF4B3E), layout.w(0.017f), Offset(leverX - layout.w(0.006f), leverTop + leverShift - layout.h(0.006f)))
    }
    private fun DrawScope.drawAiReelDisplays(layout: BoardLayout, gc: GameplayCoords, vm: GameViewModel, now: Long) {
        val engine = vm.engine ?: return
        val human = vm.humanColor
        val aiColors = engine.activeSeats.filter { it != human }
        if (aiColors.isEmpty()) return
        for (color in aiColors) {
            val strip = layout.rect(gc.yards.getValue(color).labelStrip)
            val displayW = strip.width * 0.58f
            val displayH = minOf(strip.height * 1.65f, layout.h(0.050f))
            val left = strip.centerX - displayW / 2f
            val top = strip.centerY - displayH / 2f
            val gap = layout.w(0.008f)
            val cellW = (displayW - gap) / 2f
            // AI displays show the actual last completed roll and hold it until that
            // player rolls again. Only the human's large cabinet animates.
            val saved = vm.lastRolls[color] ?: (0 to 0)
            for (i in 0..1) {
                val x = left + i * (cellW + gap)
                drawRoundRect(Color(0xDD211108), Offset(x, top), Size(cellW, displayH), CornerRadius(layout.w(0.008f)))
                drawRoundRect(Color(0xFFFFD24A), Offset(x, top), Size(cellW, displayH), CornerRadius(layout.w(0.008f)), style = Stroke(width = layout.w(0.0025f)))
                val value = if (i == 0) saved.first else saved.second
                if (value in 1..6) drawMiniReelDigit(value, x + cellW / 2f, top + displayH / 2f, displayH * 0.72f, 255)
            }
        }
    }

    private fun DrawScope.drawMiniReelDigit(value: Int, cx: Float, cy: Float, sizePx: Float, alpha: Int) {
        // Use the same premium layered treatment as the main reels; only the scale changes.
        drawReelDigit(value, cx, cy, sizePx, alpha, 1.5f)
    }
    private fun DrawScope.drawReels(layout: BoardLayout, gc: GameplayCoords, vm: GameViewModel, now: Long) {
        val engine = vm.engine ?: return
        val saved = vm.lastRolls[vm.humanColor]
        val humanSpin = vm.isHumanReelSpinActive()
        val showingHumanRoll = humanSpin || (
            engine.currentColor == vm.humanColor &&
                engine.phase == com.touchdevelopment.touchparchis.engine.GamePhase.AWAIT_MOVE
            )

        if (showingHumanRoll) {
            val first = if (vm.reel1.value in 1..6) vm.reel1 else ReelViewState(value = saved?.first ?: 0)
            val second = if (vm.reel2.value in 1..6) vm.reel2 else ReelViewState(value = saved?.second ?: 0)
            val elapsed = if (humanSpin) vm.reelAnimationElapsed(now) else -1L
            drawReel(layout, gc.zones.getValue("REEL_1"), first, elapsed, vm.reelSettleElapsed(now, ReelSlot.R1), ReelSlot.R1, vm)
            drawReel(layout, gc.zones.getValue("REEL_2"), second, elapsed, vm.reelSettleElapsed(now, ReelSlot.R2), ReelSlot.R2, vm)
        } else {
            drawReel(layout, gc.zones.getValue("REEL_1"), ReelViewState(value = saved?.first ?: 0), -1L, -1L, ReelSlot.R1, vm)
            drawReel(layout, gc.zones.getValue("REEL_2"), ReelViewState(value = saved?.second ?: 0), -1L, -1L, ReelSlot.R2, vm)
        }
    }

    private fun DrawScope.drawReel(
        layout: BoardLayout,
        zone: NormRect,
        state: ReelViewState,
        animElapsed: Long,
        settleElapsed: Long,
        slot: ReelSlot,
        vm: GameViewModel
    ) {
        if (state.phase == ReelPhase.IDLE && state.value !in 1..6) return
        val r = layout.rect(zone)
        val radius = CornerRadius(layout.w(0.010f))
        val digitSize = minOf(r.width * 0.78f, r.height * 0.58f)
        val spacing = r.height * 0.43f
        if (state.highlighted) {
            drawRoundRect(Color(0xFFFFF1A8), Offset(r.left, r.top), Size(r.width, r.height), radius, style = Stroke(width = layout.w(0.006f)))
        }

        clipRect(r.left, r.top, r.right, r.bottom) {
            val target = state.value.coerceIn(1, 6)
            val duration = vm.reelStopDurationMs(slot).coerceAtLeast(REEL_ACCEL_MS + REEL_DECEL_MS + 1L)
            val elapsed = when (state.phase) {
                ReelPhase.IDLE -> duration
                ReelPhase.SPINNING -> animElapsed.coerceIn(0L, duration - REEL_DECEL_MS)
                ReelPhase.SETTLING -> (duration - REEL_DECEL_MS + settleElapsed).coerceIn(0L, duration + REEL_BOUNCE_MS)
            }
            val areaMs = duration - (2f * (REEL_ACCEL_MS + REEL_DECEL_MS) / 3f)
            val variation = (vm.reelStartSeed() + if (slot == ReelSlot.R1) 0 else 1).mod(3) - 1
            val totalSteps = (REEL_SYMBOLS_PER_SECOND * areaMs / 1000f).roundToInt().plus(variation).coerceAtLeast(1)
            val speed = totalSteps / (areaMs / 1000f)
            val decelStart = duration - REEL_DECEL_MS
            val travel = when {
                elapsed <= REEL_ACCEL_MS -> {
                    val p = elapsed.toFloat() / REEL_ACCEL_MS
                    speed * (REEL_ACCEL_MS / 1000f) * p * p * p / 3f
                }
                elapsed <= decelStart -> speed * (REEL_ACCEL_MS / 3000f + (elapsed - REEL_ACCEL_MS) / 1000f)
                else -> {
                    val cruise = speed * (REEL_ACCEL_MS / 3000f + (decelStart - REEL_ACCEL_MS) / 1000f)
                    val p = ((elapsed - decelStart).toFloat() / REEL_DECEL_MS).coerceIn(0f, 1f)
                    cruise + speed * (REEL_DECEL_MS / 1000f) * (p - p * p + p * p * p / 3f)
                }
            }.coerceIn(0f, totalSteps.toFloat())
            val step = kotlin.math.floor(travel).toInt()
            val fraction = travel - step
            val targetIndex = target - 1
            val firstIndex = Math.floorMod(targetIndex + totalSteps, 6)
            val centerIndex = Math.floorMod(firstIndex - step, 6)
            val lockedElapsed = (elapsed - duration).coerceIn(0L, REEL_BOUNCE_MS)
            val bounceProgress = lockedElapsed.toFloat() / REEL_BOUNCE_MS
            val bounceOffset = if (state.phase == ReelPhase.SETTLING && lockedElapsed > 0L) {
                sin(Math.PI * bounceProgress).toFloat() * spacing * 0.09f
            } else 0f
            val spinning = state.phase != ReelPhase.IDLE
            val centerOffset = if (spinning && elapsed < duration) fraction else 0f

            for (row in -2..2) {
                val index = Math.floorMod(centerIndex + row, 6)
                val value = index + 1
                val rowOffset = row + centerOffset
                val distance = kotlin.math.abs(rowOffset)
                val edgeFade = (1f - ((distance - 0.72f) / 1.30f).coerceIn(0f, 0.76f))
                val alpha = (if (distance < 0.42f) 255f else 172f * edgeFade).toInt().coerceIn(24, 255)
                val scaleY = if (distance < 0.42f) 1f else if (spinning) 1.18f else 0.68f
                val y = r.centerY + rowOffset * spacing + bounceOffset
                if (spinning && distance < 1.6f) {
                    drawReelDigit(value, r.centerX, y + digitSize * 0.10f, digitSize, (alpha * 0.23f).toInt(), 1.35f)
                }
                drawReelDigit(value, r.centerX, y, digitSize, alpha, if (spinning) 0f else 0f, scaleY)
            }

            // Curved glass falloff, recessed inner edge, center gloss, and a fine payline.
            drawRect(Brush.verticalGradient(listOf(Color(0x70401A08), Color.Transparent, Color.Transparent, Color(0x80401A08))), Offset(r.left, r.top), Size(r.width, r.height))
            drawRect(Color(0x22FFFFFF), Offset(r.left, r.centerY - layout.h(0.009f)), Size(r.width, layout.h(0.018f)))
            drawLine(Color(0x66B12B18), Offset(r.left + layout.w(0.012f), r.centerY), Offset(r.right - layout.w(0.012f), r.centerY), layout.h(0.0018f))
            drawRoundRect(Color(0x88401A08), Offset(r.left, r.top), Size(r.width, r.height), radius, style = Stroke(width = layout.w(0.006f)))
        }

        if (state.dimmed) {
            drawRoundRect(Color(0x99000000), Offset(r.left, r.top), Size(r.width, r.height), radius)
        }
    }

    private fun DrawScope.drawReelDigit(
        value: Int,
        cx: Float,
        cy: Float,
        sizePx: Float,
        alpha: Int,
        blurRadius: Float,
        scaleY: Float = 1f
    ) {
        val bitmap = ReelDigitSprites.get(sizePx)[value.coerceIn(1, 6) - 1]
        val width = bitmap.width.toFloat()
        val height = bitmap.height.toFloat() * scaleY
        drawIntoCanvas { canvas ->
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                this.alpha = alpha.coerceIn(0, 255)
                if (blurRadius > 0f) setShadowLayer(blurRadius, 0f, 0f, android.graphics.Color.argb(alpha.coerceIn(0,255), 40, 0, 0))
            }
            canvas.nativeCanvas.drawBitmap(bitmap, null, RectF(cx - width / 2f, cy - height / 2f, cx + width / 2f, cy + height / 2f), paint)
        }
    }

    private fun DrawScope.drawDoublesFlourish(layout: BoardLayout, gc: GameplayCoords, vm: GameViewModel, now: Long) {
        val elapsed = now - vm.doublesPulseAtMs
        if (vm.doublesPulseAtMs <= 0L || elapsed !in 0L..850L) return
        val one = layout.rect(gc.zones.getValue("REEL_1"))
        val two = layout.rect(gc.zones.getValue("REEL_2"))
        val bounds = androidx.compose.ui.geometry.Rect(
            minOf(one.left, two.left) - layout.w(0.026f),
            minOf(one.top, two.top) - layout.h(0.022f),
            maxOf(one.right, two.right) + layout.w(0.026f),
            maxOf(one.bottom, two.bottom) + layout.h(0.022f)
        )
        val pulse = 0.55f + 0.45f * sin(Math.PI * elapsed / 850f).toFloat()
        drawRoundRect(Color(0xFFFFD24A).copy(alpha = pulse * 0.55f), Offset(bounds.left, bounds.top), Size(bounds.width, bounds.height), CornerRadius(layout.w(0.03f)), style = Stroke(width = layout.w(0.009f)))
        for (i in 0..3) {
            val phase = ((elapsed / 35L + i * 23L) % 40L) / 40f
            val x = if (i % 2 == 0) bounds.left + layout.w(0.012f) else bounds.right - layout.w(0.012f)
            val y = bounds.top + (bounds.height * phase)
            drawCircle(Color(0xFFFFF4B0).copy(alpha = (1f - phase) * pulse), layout.w(0.006f + (i % 2) * 0.002f), Offset(x, y))
        }
    }

    private fun DrawScope.drawSpinButton(layout: BoardLayout, gc: GameplayCoords, vm: GameViewModel, spinLabel: String) {
        val zone = gc.zones.getValue("SPIN")
        val r = layout.rect(zone)
        val radius = CornerRadius(layout.w(0.012f))
        if (!vm.isSpinEnabled()) {
            drawRoundRect(Color(0x8C000000), Offset(r.left, r.top), Size(r.width, r.height), radius)
        }
        drawCenteredText(layout, zone, spinLabel, layout.w(0.05f), TEXT_LIGHT, bold = true)
    }

    private fun DrawScope.drawOpeningTotals(layout: BoardLayout, gc: GameplayCoords, vm: GameViewModel) {
        for ((color, total) in vm.openingTotals) {
            drawCenteredText(layout, gc.yards.getValue(color).labelStrip, total.toString(), layout.w(0.04f), GOLD.toArgb(), bold = true)
        }
    }

    private fun DrawScope.drawMessage(layout: BoardLayout, gc: GameplayCoords, text: String?) {
        if (text.isNullOrBlank()) return
        drawCenteredText(layout, gc.zones.getValue("MESSAGE_AREA"), text, layout.w(0.034f), TEXT_LIGHT, bold = true)
    }
}



// Low-latency reel sounds; SoundPool plays short synthesized WAV assets and follows
// the system media mute state. This V1 project has no in-app sound toggle.
private class SlotReelSoundController(context: Context) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val pool = SoundPool.Builder()
        .setMaxStreams(6)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()
    private val loadedSamples = mutableSetOf<Int>()
    private val pendingSounds = mutableMapOf<Int, Float>()

    init {
        pool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) {
                loadedSamples += sampleId
                pendingSounds.remove(sampleId)?.let { playLoaded(sampleId, it) }
            }
        }
    }

    private val lever = pool.load(context, R.raw.slot_lever, 1)
    private val loop = pool.load(context, R.raw.slot_loop, 1)
    private val tick = pool.load(context, R.raw.slot_tick, 1)
    private val stop = pool.load(context, R.raw.slot_stop, 1)
    private val doubles = pool.load(context, R.raw.slot_doubles, 1)

    private var active = false
    private var loopStream = 0
    private var lastDoublesAt = 0L
    private val lastTickAt = longArrayOf(0L, 0L)
    private val stopPlayed = booleanArrayOf(false, false)

    fun update(vm: GameViewModel, now: Long) {
        val oneActive = vm.reel1.phase != ReelPhase.IDLE
        val twoActive = vm.reel2.phase != ReelPhase.IDLE
        val anyActive = vm.isHumanReelSpinActive() && (oneActive || twoActive)
        if (anyActive && !active) {
            active = true
            lastTickAt[0] = now
            lastTickAt[1] = now
            stopPlayed[0] = false
            stopPlayed[1] = false
            play(lever)
        }
        if (anyActive) {
            if (loopStream == 0 && loadedSamples.contains(loop)) {
                val v = volume()
                if (v > 0f) loopStream = pool.play(loop, v, v, 1, -1, 0.72f)
            }
            if (loopStream != 0) pool.setRate(loopStream, reelPitch(vm, now))
            for (i in 0..1) {
                val slot = if (i == 0) ReelSlot.R1 else ReelSlot.R2
                val settling = vm.reelSettleElapsed(now, slot)
                if (settling in 0L..REEL_DECEL_MS) {
                    val progress = settling.toFloat() / REEL_DECEL_MS
                    val speed = (REEL_SYMBOLS_PER_SECOND * (1f - progress) * (1f - progress)).coerceAtLeast(2f)
                    val interval = (1000f / speed).toLong()
                    if (now - lastTickAt[i] >= interval) {
                        play(tick)
                        lastTickAt[i] = now
                    }
                }
                if (settling >= REEL_DECEL_MS && !stopPlayed[i]) {
                    stopPlayed[i] = true
                    play(stop, if (i == 0) 0.92f else 1.06f)
                }
            }
        } else if (active) {
            active = false
            if (loopStream != 0) pool.stop(loopStream)
            loopStream = 0
        }
        if (vm.doublesPulseAtMs > 0L && vm.doublesPulseAtMs != lastDoublesAt) {
            lastDoublesAt = vm.doublesPulseAtMs
            play(doubles)
        }
    }

    private fun reelPitch(vm: GameViewModel, now: Long): Float {
        val elapsed = vm.reelAnimationElapsed(now).coerceAtLeast(0L)
        val durations = listOf(vm.reelStopDurationMs(ReelSlot.R1), vm.reelStopDurationMs(ReelSlot.R2))
        var pitch = 0.72f
        for (duration in durations) {
            val decelStart = duration - REEL_DECEL_MS
            val current = when {
                elapsed < REEL_ACCEL_MS -> 0.72f + 0.34f * (elapsed.toFloat() / REEL_ACCEL_MS).coerceIn(0f, 1f)
                elapsed < decelStart -> 1.06f
                elapsed < duration -> 1.06f - 0.46f * ((elapsed - decelStart).toFloat() / REEL_DECEL_MS).coerceIn(0f, 1f)
                else -> 0.60f
            }
            pitch = maxOf(pitch, current)
        }
        return pitch.coerceIn(0.60f, 1.12f)
    }

    private fun volume(): Float {
        val mediaMuted = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) <= 0
        return if (mediaMuted) 0f else SLOT_SOUND_VOLUME
    }

    private fun play(sound: Int, rate: Float = 1f) {
        val v = volume()
        if (v <= 0f) return
        if (!loadedSamples.contains(sound)) {
            pendingSounds[sound] = rate
            return
        }
        playLoaded(sound, rate)
    }

    private fun playLoaded(sound: Int, rate: Float) {
        val v = volume()
        if (v > 0f) pool.play(sound, v, v, 1, 0, rate)
    }

    fun pause() {
        if (loopStream != 0) pool.stop(loopStream)
        loopStream = 0
    }

    fun release() {
        pause()
        pool.release()
    }
}

// ---------------------------------------------------------------------------
// Drawing helpers
// ---------------------------------------------------------------------------

private fun DrawScope.drawPlate(layout: BoardLayout, plate: ImageBitmap) {
    drawImage(
        image = plate,
        dstOffset = IntOffset(layout.destX.roundToInt(), layout.destY.roundToInt()),
        dstSize = IntSize(layout.destW.roundToInt(), layout.destH.roundToInt())
    )
}

private fun DrawScope.drawOutline(rect: BoardLayout.PixelRect, color: Color, strokePx: Float) {
    drawRoundRect(
        color, topLeft = Offset(rect.left, rect.top), size = Size(rect.width, rect.height),
        cornerRadius = CornerRadius(strokePx * 1.5f), style = Stroke(width = strokePx)
    )
}

private fun DrawScope.drawBadge(layout: BoardLayout, rect: BoardLayout.PixelRect, text: String, color: Color) {
    val radius = layout.w(0.024f)
    val cx = rect.left + rect.width - radius * 0.9f
    val cy = rect.top + radius * 0.9f
    drawCircle(color, radius = radius, center = Offset(cx, cy))
    drawIntoCanvas { canvas ->
        val paint = Paint().apply {
            isAntiAlias = true
            this.color = TEXT_DARK
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
            textSize = radius * 1.2f
        }
        val y = cy - (paint.descent() + paint.ascent()) / 2f
        canvas.nativeCanvas.drawText(text, cx, y, paint)
    }
}

private fun DrawScope.drawCenteredText(
    layout: BoardLayout,
    zone: NormRect,
    text: String,
    sizePx: Float,
    colorArgb: Int,
    bold: Boolean = false
) {
    val r = layout.rect(zone)
    drawIntoCanvas { canvas ->
        val paint = Paint().apply {
            isAntiAlias = true
            color = colorArgb
            textAlign = Paint.Align.CENTER
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            textSize = sizePx
        }
        val cx = r.centerX
        val cy = r.centerY - (paint.descent() + paint.ascent()) / 2f
        canvas.nativeCanvas.drawText(text, cx, cy, paint)
    }
}
