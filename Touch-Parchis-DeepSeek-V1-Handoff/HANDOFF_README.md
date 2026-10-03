# Touch Parchís V1 DeepSeek Handoff

This package is the clean-room first-pass handoff for the V1 build.

## Contents

- `spec/DEEPSEEK_BUILD_INSTRUCTIONS.md` — complete build and rules brief.
- `spec/RULES.md` — authoritative game rules and required engine tests.
- `spec/touch_assets_worksheet.md` — visual worksheet and approved screen map.
- `coords/board_coords.json` — normalized screen, board, yard, and touch coordinates.
- `data/game_data.json` — route, seat, start-square, and safety-square data.
- `assets/screens/` — clean 720×1600 plates.
- `assets/mockups/` — marked touch-target references.
- `assets/pawns/top_down_gameplay/` — transparent board sprites, one per color.
- `assets/pawns/side_view_selector/` — transparent side-view pawn assets for the splash 1/2/3 opponent selector.
- `assets/mockups/touch-toolkit-portrait-8-pawn-starts.png` — final X1–X16 pawn-start reference.

## Baseline rule

This ZIP is the complete source of truth for a clean-room first pass. Do not depend on the deleted repository or invent replacement screens/assets. Use the checklist in `spec/HANDOFF_ASSET_CHECKLIST.md` before declaring the build complete.

The mockups are references only. The clean plates are the artwork used in the app. The code must keep visuals modular and must implement dynamic highlights, reel values, player labels, inactive AI overlays, and pawn movement separately from the plate images.

Coordinate warning: use `coords/board_coords.json` for all runtime positions and touch zones. Do not read pixel coordinates from screenshots.
