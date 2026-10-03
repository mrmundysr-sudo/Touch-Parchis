# Touch Parchis Visual Assets Worksheet

## Official asset location

Package-relative `assets/`

- `assets/screens/` contains clean portrait plates.
- `assets/mockups/` contains Toolkit screenshots with touch targets.
- `assets/pawns/` contains gameplay and reserved selector sprites.
- `coords/` contains normalized layout coordinates.
- `data/` contains routes and seat data.

## Screen flow

1. Start / opponent selection → select 1, 2, or 3 opponents, then tap Touch to Play.
2. Gameplay → win or loss result.
3. Win result → Play Again.
4. Loss result → Try Again.

Win and loss are separate screens. Opponent selection is part of the start screen. The current core flow does not require a separate menu screen.

## Start / opponent selection

- Clean plate: `Screens/splash_screen_clean_720x1600.png`
- Full marked mockup: `Mockups/splash_screen_touch_targets_20261002.png`
- `OPP_1`: select one opponent.
- `OPP_2`: select two opponents.
- `OPP_3`: select three opponents.
- Mark 5: tap anywhere in the outlined Touch to Play area to start with the selected opponent count.
- Marks 2–4 are one mutually exclusive selection group; the selected choice is visibly highlighted.
- If no choice is selected, Touch to Play prompts the player to select 1, 2, or 3.
- The splash plate already contains the pawn artwork in these zones. Draw only the code highlight; do not add selector sprites in V1.

## Gameplay

- Clean plate: `Screens/gameplay_screen_clean_720x1600.png` (the original blank-spaces board)
- Approved full mockup: `Mockups/gameplay_screen_touch_targets_APPROVED.png`
- Final pawn-start markup: `Mockups/touch-toolkit-portrait-8-pawn-starts.png`
- Pawn starts: X1–X4 red, X5–X8 yellow, X9–X12 green, X13–X16 blue.
- Gameplay controls: Mark 1 Spin, Mark 2 Reel 1, Mark 3 Reel 2, Mark 4 Player area.
- Inactive AI areas are dimmed and labeled by code according to the selected opponent count; no extra baked markup is required.
- The prior worksheet named `Touch Parchis HD in a golden frame.png` as the gameplay plate. That file is a start/splash image; it is not the gameplay board. Use the clean board plate above with the approved full gameplay mockup.
- Keep the mockup unmodified. Use it as the exact guide for implementing the marked tap zones; do not infer positions from the reduced flow sheet.
- Further gameplay path-space and control mapping can be recorded here as each Toolkit-marked guide is approved.

## Win result

- Clean plate: `Screens/win_screen_clean_720x1600.png`
- Full marked mockup: `Mockups/win_screen_touch_targets.png`
- Mark 1 covers the Play Again control.

## Loss result

- Clean plate: `Screens/loss_screen_clean_720x1600.png`
- Full marked mockup: `Mockups/loss_screen_touch_targets.png`
- Mark 1 covers the Try Again control.

## Pawn sprites

- Red: `Pawns/touch_parchis_piece_red.png`
- Blue: `Pawns/touch_parchis_piece_blue.png`
- Yellow: `Pawns/touch_parchis_piece_yellow.png`
- Green: `Pawns/touch_parchis_piece_green.png`

### V1 overhead gameplay pawns

- Red: `assets/pawns/top_down_gameplay/pawn_red_topdown.png`
- Yellow: `assets/pawns/top_down_gameplay/pawn_yellow_topdown.png`
- Green: `assets/pawns/top_down_gameplay/pawn_green_topdown.png`
- Blue: `assets/pawns/top_down_gameplay/pawn_blue_topdown.png`

Splash selector assets:

- Red: `assets/pawns/side_view_selector/touch_parchis_piece_red.png`
- Yellow: `assets/pawns/side_view_selector/touch_parchis_piece_yellow.png`
- Green: `assets/pawns/side_view_selector/touch_parchis_piece_green.png`
- Blue: `assets/pawns/side_view_selector/touch_parchis_piece_blue.png`
- These are reserved transparent selector resources. V1 does not draw them on the splash because the approved splash plate already contains the pawn artwork.

## Later release assets

The app icon is not part of the in-game screen flow and can be added as a release asset. Audio, pause/settings, and tutorial screens are not yet in the approved scope.
