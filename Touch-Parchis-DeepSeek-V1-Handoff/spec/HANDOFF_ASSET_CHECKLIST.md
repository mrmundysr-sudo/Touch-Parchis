# Touch Parchís V1 Handoff Asset Checklist

This checklist is part of the handoff. DeepSeek should treat every item below as required input and should not rely on the deleted old repository.

## Screens and coordinate references

- [x] `assets/screens/splash_screen_clean_720x1600.png`
- [x] `assets/mockups/splash_screen_touch_targets_20261002.png`
- [x] `assets/screens/gameplay_screen_clean_720x1600.png`
- [x] `assets/mockups/gameplay_screen_touch_targets_APPROVED.png`
- [x] `assets/mockups/touch-toolkit-portrait-8-pawn-starts.png` — X1–X16 starting-slot reference.
- [x] `assets/screens/win_screen_clean_720x1600.png`
- [x] `assets/mockups/win_screen_touch_targets.png`
- [x] `assets/screens/loss_screen_clean_720x1600.png`
- [x] `assets/mockups/loss_screen_touch_targets.png`

Clean plates are runtime artwork. Markups are coordinate/touch references only and must not be shown as the final UI.

## Pawn assets

- [x] Four transparent top-down gameplay sprites: red, yellow, green, blue.
- [x] Four transparent side-view selector sprites: red, yellow, green, blue.
- [x] Top-down sprites are duplicated into four board pawns per color.
- [x] Side-view sprites are included as reserved selector resources; V1 uses the pawn artwork already present on the splash plate.

## Required gameplay behavior

- [x] Two slot-style reels replace dice completely.
- [x] Splash choices 1, 2, and 3 are mutually exclusive and visibly highlighted.
- [x] Touch to Play uses the selected opponent count; it does not randomize the count.
- [x] Inactive AI areas are dimmed by code-only overlays.
- [x] Gameplay marks 1–4 map to Spin, Reel 1, Reel 2, and Player Area.
- [x] X1–X16 map to four red, four yellow, four green, and four blue starting slots.
- [x] Win exposes only Play Again; loss exposes only Try Again.

## Documentation included

- [x] `spec/DEEPSEEK_BUILD_INSTRUCTIONS.md`
- [x] `spec/RULES.md`
- [x] `spec/touch_assets_worksheet.md`
- [x] `coords/board_coords.json`
- [x] `data/game_data.json`
- [x] This checklist.

## Final verification before coding

Verify the file tree, image dimensions, transparency of all eight pawn sprites, and the normalized coordinates in `coords/board_coords.json`. The clean and marked images do not all have identical pixel dimensions; use aspect-fit scaling and normalized coordinates. Do not crop, stretch, flatten dynamic labels/highlights/reels, or add setup/name/audio/tutorial screens in V1.
