# Touch Parchís — DeepSeek V1 Build Instructions

## 0. How to read this package

Build a complete, playable Android V1 of **Touch Parchís**: a touch-only Parchís/Parcheesi game against 1, 2, or 3 AI opponents, with two slot-machine reels instead of dice. Use the real artwork in this package. Do not build a placeholder skeleton, and do not leave `TODO` stubs in the code.

**Document priority (if two files disagree, the first one wins):**

1. `RULES.md`: every rule of the game, the routes, the reels, the turn flow, the AI, and the engine tests.
2. `coords/board_coords.json`: every position and touch rectangle (see §3). *Never read coordinates off the screenshots.*
3. This file: screens, flow, visuals, architecture, delivery.
4. `HANDOFF_ASSET_CHECKLIST.md` and `touch_assets_worksheet.md`: background only. Ignore any path that mentions a "deleted repository", `Touch Development/APPS/...`, or "Golden frame".

**Do not invent** new screens, assets, rules, setup pages, player names, audio, pause/settings, tutorials, or monetization. If something is not specified, pick the simplest option, write it down in the "assumptions" list (§10), and keep going.

---

## 1. Technical defaults

Use these unless the project owner says otherwise.

| Item | Default |
|---|---|
| Language / UI | Kotlin, a single Activity, Jetpack Compose with a `Canvas`-based game view (a custom `View` is acceptable) |
| Build | Gradle with Kotlin DSL; `./gradlew assembleDebug` must produce an installable APK with no manual steps |
| minSdk / targetSdk | minSdk 26; targetSdk = the latest stable level the toolchain supports (at least 35) |
| Package | `com.touchdevelopment.touchparchis` *(placeholder; project owner may rename)* |
| App name | Touch Parchís |
| Orientation | Portrait only, locked |
| System bars | Immersive: draw edge-to-edge and hide the system bars |
| Permissions | None. No network, no ads, no analytics |
| Back button | Splash: exit the app. Gameplay: confirmation dialog ("Quit to start?"), then go to Splash. Win/Loss: go to Splash |
| Images | `res/drawable-nodpi/` so Android does not rescale them by density |
| Audio / haptics | None in V1 |

---

## 2. Screen flow

```
Splash (choose 1/2/3 opponents) → Touch to Play → Gameplay
        ↑                                              │
        │                                  ┌───────────┴───────────┐
        │                              Win screen             Loss screen
        └──────── Play Again / Try Again ────────┴───────────────────┘
```

Win and loss are separate screens. There is no menu screen.

### 2.1 Zone names (use these names in code; they replace the numbers in the screenshots)

The marked screenshots use "Mark 1, 2, 3…", which is easy to misread (for example "Mark 2" on the splash means *one* opponent). Use these names:

| Screen | Screenshot mark | Zone name | Meaning |
|---|---|---|---|
| Splash | 2 | `OPP_1` | Choose 1 opponent |
| Splash | 3 | `OPP_2` | Choose 2 opponents |
| Splash | 4 | `OPP_3` | Choose 3 opponents |
| Splash | 5 | `PLAY` | Touch to Play |
| Gameplay | 1 | `SPIN` | Spin button (the whole marked area is tappable) |
| Gameplay | 2 | `REEL_1` | Reel 1 display / selector zone |
| Gameplay | 3 | `REEL_2` | Reel 2 display / selector zone |
| Gameplay | 4 | `PLAYER_AREA` | The player's (Red) yard |
| Win | 1 | `PLAY_AGAIN` | Play Again |
| Loss | 1 | `TRY_AGAIN` | Try Again |

### 2.2 Splash screen

- Show `screen_splash` (the clean plate).
- `OPP_1`, `OPP_2`, `OPP_3` are **one mutually exclusive group**. Nothing is selected when the screen first appears. Tapping one selects it and deselects the others.
- The selected zone shows a **code-drawn highlight** (glowing rounded rectangle plus a "1", "2", or "3" badge). Do not bake the highlight into the plate.
- Tapping `PLAY` with a selection starts a game with that many AI opponents. **Never randomize the count.**
- Tapping `PLAY` with **no** selection shows a short message ("Choose 1, 2, or 3 opponents") and briefly pulses the three zones. It does not start a game.
- **Highlight only (decided).** The splash plate already contains the pawn artwork in these zones, so draw **nothing but the highlight** (glowing outline plus the "1", "2", or "3" badge). Do not draw pawn sprites on the splash screen. The side-view `pawn_selector_*` files are **not used in V1**; keep them in the project as unused resources for later.

### 2.3 Gameplay screen

Show `screen_gameplay` (the clean plate), then draw everything dynamic on top of it in code.

**Seats (full detail in `RULES.md` §2):** the human is always Red (bottom-right yard). Active AI seats: 1 opponent = Blue; 2 = Green + Blue; 3 = Yellow + Green + Blue.

**Inactive AI yards:** draw a dark semi-transparent overlay (about 55% black) over the yard rectangle with the label "Not Playing". That yard has no pawns. It is **code only**; never baked into the plate.

**Start of the game: opening spin** (`RULES.md` §2). Before turn 1, every active player spins once and the highest total starts; ties re-spin among the tied players. The human taps `SPIN` for their own spin; AI spins happen automatically. Show each player's total in a code-drawn label next to their yard. Show a caption such as "Spin to see who starts" in the message area. No pawns move during the opening spin.

**Spin (`SPIN`):** the whole marked area is tappable. It is **disabled** (dimmed by a code overlay) during reel animation, during AI turns, and while a roll is unresolved. Draw the label "SPIN" in code on the button (the plate button is blank).

**Reels (`REEL_1`, `REEL_2`):** two independent reels, each showing a value from 1 to 6.
- The values come from the engine's RNG when Spin is pressed. The animation is cosmetic: digits scroll vertically with a slight blur, then ease to a stop. Reel 1 stops first, Reel 2 about 300 ms later. Total animation under 1.5 s.
- No digit artwork exists. Draw digits in code: a bold sans-serif, large, dark brown on the reel face, centered in the zone.
- A reel value that has been used is dimmed by a code overlay. During a human move, tapping a reel zone selects that value (highlight outline).

**Message area:** a short code-drawn caption (position given in `board_coords.json` as `message_area`) for hints like "Your turn: tap Spin", "No moves", "Blue is thinking…", and capture notices.

**Player area (`PLAYER_AREA`):** the Red yard. When an entry is legal (`RULES.md` §6), tapping **anywhere** in it enters one Red pawn. Yard pawns are interchangeable, so no individual yard pawn needs to be hit.

**Choosing a move (`RULES.md` §12, agreed):** tap a highlighted pawn on the board. Its legal destination squares are highlighted. Tap `REEL_1` or `REEL_2` to move by that value, or tap the highlighted *total* destination to move by the combined value. If the pawn has only one legal move, it plays immediately. If there is exactly one legal action in the whole roll, the game plays it automatically with a short animation. If there is none, show "No moves" and pass the turn.

**AI turns:** pause 600–900 ms, spin, then play moves one at a time with a short animation between each. The AI follows `RULES.md` §13.

**Game end:** when any player's fourth pawn reaches Home Box 5, wait about one second, then show the Win screen (human finished first) or the Loss screen (an AI finished first).

### 2.4 Win and loss screens

- Show `screen_win` or `screen_loss` unchanged. Only `PLAY_AGAIN` / `TRY_AGAIN` is tappable. Nothing else responds to touch.
- Tapping it goes to the Splash screen with **no opponent selected** and a completely fresh game state.

---

## 3. Coordinates, scaling, and the single source of truth

**The screenshots must not be used for pixel coordinates.** The files are not all the same size, and the clean/marked pairs differ:

| File | Actual size |
|---|---|
| `splash_screen_clean_720x1600.png` | 720×1600 |
| `splash_screen_touch_targets_20261002.png` | 691×1536 |
| `gameplay_screen_clean_720x1600.png` | **691×1536** (despite the name) |
| `gameplay_screen_touch_targets_APPROVED.png` | 692×1536 |
| `win_screen_clean_720x1600.png` | 720×1600 |
| `win_screen_touch_targets.png` | 841×1870 |
| `loss_screen_clean_720x1600.png` | 720×1600 |
| `loss_screen_touch_targets.png` | 720×1600 |

All of these are about 9:20. Therefore:

1. `coords/board_coords.json` gives **every** position (all board squares 6–101, Home Box 5, yard slots X1–X16, reels, buttons, message area, selector zones) as **fractions (0.0–1.0) of the plate's width and height**. Nothing is stored in pixels.
2. The game draws each plate at the largest size that **fits the screen without cropping or distortion** (aspect-fit, centered). All fractions are applied to that drawn rectangle.
3. Any leftover bars (phones taller or wider than 9:20) are filled with a single solid color taken from the plate's outermost edge. Keep it as a named constant.
4. Use one function for every coordinate lookup (`BoardLayout`), so a plate swap changes data only, not code.
5. **Never guess** a coordinate. If something is missing from the JSON, report it in the assumptions list instead of estimating from the images.

---

## 4. Assets

Copy the files into `res/drawable-nodpi/` with these **stable names**. Renaming is fine, but never crop, resize, recolor, or flatten them.

| Source file | Resource name | Used for |
|---|---|---|
| `assets/screens/splash_screen_clean_720x1600.png` | `screen_splash` | Splash plate |
| `assets/screens/gameplay_screen_clean_720x1600.png` | `screen_gameplay` | Gameplay plate |
| `assets/screens/win_screen_clean_720x1600.png` | `screen_win` | Win plate |
| `assets/screens/loss_screen_clean_720x1600.png` | `screen_loss` | Loss plate |
| `assets/pawns/top_down_gameplay/pawn_{color}_topdown.png` | `pawn_board_{color}` | **Board and yard pawns only** |
| `assets/pawns/side_view_selector/touch_parchis_piece_{color}.png` | `pawn_selector_{color}` | **Not used in V1** (reserved; the splash plate already shows the pawn art) |

`{color}` = `red`, `yellow`, `green`, `blue`. The files in `assets/mockups/` are **reference only**. Never copy them into the app or show them.

**Pawn drawing rules**
- Each color's top-down sprite is used for all four of that color's pawns.
- The source images are not square (about 516–677 px) and are much larger than a board cell. Scale them **down uniformly** (keep the aspect ratio) to about **80% of one board-cell width** and anchor them at their **center**.
- When two pawns share a square, draw both at about 65% size, offset left and right by about 25% of a cell width. Never overlap them completely.
- Draw order, bottom to top: plate → inactive-yard overlays → destination highlights → pawns (finished pawns in Home Box 5 first) → selected-pawn glow → reel digits and overlays → message area.
- Pawns that are finished (index 72) are drawn inside Home Box 5, fanned slightly so that up to four are visible.
- Never use a side-view sprite on the board.

**Starting positions (yards)**
- X1–X4 Red, X5–X8 Yellow, X9–X12 Green, X13–X16 Blue. Slot centers come from `board_coords.json`.
- A captured pawn returns to the first free slot of its yard with a short move animation.

---

## 5. Rules and routes

Implement **exactly** what `RULES.md` says. Summary of the traps, to avoid reading them wrong:

- There are **no dice and no summing by default.** Each reel value is its own move. A player may instead combine both values for one pawn (§5 of the rules).
- A pawn's route is 72 indices: yard (0), start square (1), common track to index 64, seven home-stretch squares (65–71), Home Box 5 (72). Only the final step into Home Box 5 needs an exact count.
- Leaving the yard needs a 5 on a reel or a sum of 5; it is optional; a lone opponent on the start square is captured.
- No bonus moves, no mandatory capture, no penalty for repeated doubles.
- Blockades, safety squares, doubles, and "play as many values as possible" are in `RULES.md` §7–§10.

Route tables and safe squares live in **one data file** (see §6), not in game logic.

---

## 6. Architecture

Keep these in separate modules or packages so assets and rules can change independently:

```
engine/     Pure Kotlin, NO Android imports. GameState, Pawn, Route, Move generation,
            legality, capture/blockade/safety logic, turn flow, win detection, opening spin.
ai/         Opponent move choice (RulesAI) using only the engine's legal-move list.
data/       Route tables, safe-square list, seat/color config, board_coords.json loader.
ui/         Screens, BoardLayout (the only place that turns fractions into screen pixels),
            sprite drawing, reel view, highlights, overlays, touch routing.
res/        Plates, pawn sprites, strings (every label, caption, and message).
```

Requirements:
- The engine takes an **injectable random source** so tests can fix reel values.
- The UI only asks the engine "what moves are legal?" and "apply this move". It never re-implements a rule.
- Screens, sprites, reel visuals, labels, highlights, overlays, and touch rectangles are **separate resources or classes**. Nothing dynamic is flattened into a plate.
- All text is in a strings resource.
- Every board coordinate comes from `board_coords.json`; there is no hard-coded pixel anywhere.
- Touch targets for pawns are at least 48dp (or the cell plus a margin). Use the nearest highlighted candidate when taps are close together.

---

## 7. Tests (required)

Implement the 11 engine tests in `RULES.md` §15 (including 10a, the opening spin) as JVM unit tests, plus:

- A seeded AI-vs-AI full game finishes with exactly one winner, for 1, 2, and 3 opponents.
- `BoardLayout` maps the corners and center of each plate correctly at 9:20, 9:19.5, and 9:21 screens.

Run them and report the results.

---

## 8. Out of scope for V1

Setup pages, player names, audio, pause/settings, tutorials, monetization, partnership play, online play, saving or resuming a game, app-icon design.

---

## 9. Delivery order

The project is large. Deliver it in stages, so a long reply is never cut off. After each stage, say what is done and what is next.

1. **Stage 1:** project skeleton, data files, the `engine/` module, and the unit tests.
2. **Stage 2:** `ai/`, then `ui/`: plates, `BoardLayout`, splash, gameplay drawing (pawns, reels, highlights, overlays).
3. **Stage 3:** input and flow: opening spin, turns, AI turns, win/loss, reset; then the final build command and the acceptance checklist below.

---

## 10. Acceptance checks and report

Before declaring the build complete, run through this list and report pass/fail for each:

1. The app launches into the clean splash plate; nothing is pre-selected.
2. Selecting 1, 2, or 3 visibly moves the highlight; only one is ever selected.
3. `PLAY` with no selection shows the prompt and does not start; with a selection it starts with exactly that opponent count.
4. Gameplay shows the approved board and two reels, no dice anywhere. Inactive AI yards are dimmed with "Not Playing"; active seats match 1 = Blue, 2 = Green + Blue, 3 = all.
5. The opening spin runs, ties re-spin, and the winner starts.
6. A spin produces two values, 1 to 6; each can be played as its own move, or the pair combined on one pawn; Spin stays locked until the roll is resolved.
7. All four routes, the safety squares, captures, blockades, doubles, and Home Box 5 behave as in `RULES.md`.
8. Win and loss each expose only their one button, and both return to a fresh splash.
9. No plate is stretched, cropped, or altered. No mockup is shown.
10. The engine tests pass, and `./gradlew assembleDebug` produces an APK.

**Final report must also include:**
- An **assumptions list**: everything you decided because it was not specified.
- A **gaps list**: anything missing from the package (a coordinate, an asset) that you could not complete.
- The test results.
