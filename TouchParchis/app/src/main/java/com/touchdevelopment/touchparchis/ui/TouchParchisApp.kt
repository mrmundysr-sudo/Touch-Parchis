package com.touchdevelopment.touchparchis.ui

import android.graphics.Paint
import android.graphics.Typeface
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.touchdevelopment.touchparchis.R
import com.touchdevelopment.touchparchis.data.GameplayCoords
import com.touchdevelopment.touchparchis.data.NormPoint
import com.touchdevelopment.touchparchis.data.NormRect
import com.touchdevelopment.touchparchis.engine.ParchisColor
import com.touchdevelopment.touchparchis.engine.ReelSlot
import com.touchdevelopment.touchparchis.engine.RouteIndex
import kotlin.math.hypot
import kotlin.math.roundToInt

private val GOLD = Color(0xFFFFD24A)
private val LETTERBOX = Color(0xFF4A2A18)
private val DEST_HIGHLIGHT = Color(0xCC44DD66)
private val DEST_TOTAL = Color(0xCCFF8A2B)
private val SELECT_GLOW = Color(0xFFFFE07A)
private val LEGAL_PAWN_GLOW = Color(0x99FFE07A)
private val SAFE_HIGHLIGHT = Color(0x9957C7FF)

private val TEXT_LIGHT = 0xFFFFF3D6.toInt()
private val TEXT_DARK = 0xFF3B1E0E.toInt()

@Composable
fun TouchParchisApp(viewModel: GameViewModel, onExit: () -> Unit) {
    var showQuit by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel.screen) {
        while (viewModel.screen == Screen.GAMEPLAY) {
            withFrameNanos { viewModel.tick(it / 1_000_000L) }
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
        Screen.GAMEPLAY -> GameplayScreen(viewModel)
        Screen.WIN -> ResultScreen(viewModel, R.drawable.screen_win, "PLAY_AGAIN", R.string.play_again)
        Screen.LOSS -> ResultScreen(viewModel, R.drawable.screen_loss, "TRY_AGAIN", R.string.try_again)
    }
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
private fun GameplayScreen(vm: GameViewModel) {
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
                now = System.currentTimeMillis()
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
            drawSlotCabinet(layout, gc)
            drawReels(layout, gc, vm, now)
            drawSpinButton(layout, gc, vm, spinLabel)
            drawOpeningTotals(layout, gc, vm)
            drawMessage(layout, gc, messageText)
        }
    }

    private fun cellRectForIndex(gc: GameplayCoords, rules: com.touchdevelopment.touchparchis.engine.RulesData, color: ParchisColor, index: Int): NormRect {
        if (index == RouteIndex.HOME) return gc.homeBox.rect
        val sc = gc.squares[rules.squareAt(color, index)] ?: return gc.homeBox.rect
        return NormRect(sc.cx - sc.w / 2f, sc.cy - sc.h / 2f, sc.w, sc.h)
    }

    private fun DrawScope.drawSlotCabinet(layout: BoardLayout, gc: GameplayCoords) {
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
            drawRoundRect(Brush.verticalGradient(listOf(Color.White, Color(0xFFB9C2CA), Color(0xFF5B6268), Color(0xFFEFF5F8))), Offset(window.left - layout.w(0.007f), window.top - layout.h(0.006f)), Size(window.width + layout.w(0.014f), window.height + layout.h(0.012f)), CornerRadius(layout.w(0.014f)))
            drawRoundRect(Color(0xFF101217), Offset(window.left, window.top), Size(window.width, window.height), CornerRadius(layout.w(0.010f)), style = Stroke(width = layout.w(0.004f)))
        }
        drawLine(Color(0xFFFFD85A), Offset((one.right + two.left) / 2f, top + layout.h(0.010f)), Offset((one.right + two.left) / 2f, bottom - layout.h(0.010f)), layout.w(0.010f))
        val leverX = right + layout.w(0.026f)
        val leverTop = top + cabinet.height * 0.16f
        drawLine(Color(0xFF6A2B09), Offset(leverX, leverTop), Offset(leverX, leverTop + layout.h(0.070f)), layout.w(0.012f))
        drawCircle(Color(0xFF8B0E12), layout.w(0.028f), Offset(leverX, leverTop))
        drawCircle(Color(0xFFFF4B3E), layout.w(0.017f), Offset(leverX - layout.w(0.006f), leverTop - layout.h(0.006f)))
    }
    private fun DrawScope.drawReels(layout: BoardLayout, gc: GameplayCoords, vm: GameViewModel, now: Long) {
        drawReel(layout, gc.zones.getValue("REEL_1"), vm.reel1, vm.reelAnimationElapsed(now), now)
        drawReel(layout, gc.zones.getValue("REEL_2"), vm.reel2, vm.reelAnimationElapsed(now), now)
    }

    private fun DrawScope.drawReel(
        layout: BoardLayout,
        zone: NormRect,
        state: ReelViewState,
        animElapsed: Long,
        now: Long
    ) {
        val r = layout.rect(zone)
        val radius = CornerRadius(layout.w(0.012f))
        val digitSize = layout.w(0.075f)

        if (state.highlighted) {
            drawRoundRect(GOLD, Offset(r.left, r.top), Size(r.width, r.height), radius, style = Stroke(width = layout.w(0.006f)))
        }

        when (state.phase) {
            ReelPhase.SPINNING -> {
                // Fast scrolling digits with a slight blur.
                clipRect(r.left, r.top, r.right, r.bottom) {
                    val shift = (now % 90L) / 90f
                    for (i in -1..2) {
                        val v = ((state.value + i + 5) % 6) + 1
                        val cy = r.centerY + (i + shift - 0.5f) * layout.h(0.075f)
                        drawReelDigit(v, r.centerX, cy, digitSize, 0x66, 6f)
                    }
                }
            }
            ReelPhase.SETTLING -> {
                clipRect(r.left, r.top, r.right, r.bottom) {
                    val t = ((animElapsed - 600L).coerceAtLeast(0L) / 400f).coerceAtMost(1f)
                    val ease = t * t * (3 - 2 * t)
                    val shift = (1f - ease) * 1.2f
                    for (i in 0..1) {
                        val v = ((state.value + i + 5) % 6) + 1
                        val cy = r.centerY + (i + shift - 0.6f) * layout.h(0.075f)
                        drawReelDigit(v, r.centerX, cy, digitSize, (120 + 135 * ease).toInt(), (6f * (1 - ease)))
                    }
                }
            }
            ReelPhase.IDLE -> {
                if (state.value in 1..6) drawReelDigit(state.value, r.centerX, r.centerY, digitSize, 255, 0f)
            }
        }

        if (state.dimmed) {
            drawRoundRect(Color(0x99000000), Offset(r.left, r.top), Size(r.width, r.height), radius)
        }
    }

    private fun DrawScope.drawReelDigit(value: Int, cx: Float, cy: Float, sizePx: Float, alpha: Int, blurRadius: Float) {
        drawIntoCanvas { canvas ->
            val paint = Paint().apply {
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
                typeface = Typeface.DEFAULT_BOLD
                textSize = sizePx
                color = android.graphics.Color.argb(alpha, 0x3B, 0x1E, 0x0E)
                if (blurRadius > 0.1f) {
                    setShadowLayer(blurRadius, 0f, 0f, android.graphics.Color.argb(alpha, 0x3B, 0x1E, 0x0E))
                }
            }
            val y = cy - (paint.descent() + paint.ascent()) / 2f
            canvas.nativeCanvas.drawText(value.toString(), cx, y, paint)
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
