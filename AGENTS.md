# Touch Parchís — Repository Guide

Touch Parchís V1: a portrait-only, offline Android Parchís game (Kotlin, single Activity,
Jetpack Compose Canvas) against 1–3 AI opponents. Two slot reels replace dice.

## Source of truth

- `Touch-Parchis-DeepSeek-V1-Handoff-REVISED-20261002.zip` is the handoff. **Do not delete it.**
- Extracted handoff: `Touch-Parchis-DeepSeek-V1-Handoff/`
- Android project: `TouchParchis/`
- Doc priority: `spec/RULES.md` > `coords/board_coords.json` > `spec/DEEPSEEK_BUILD_INSTRUCTIONS.md` > worksheet/checklist.

## Build & test (offline toolchain)

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export ANDROID_HOME=/workspace/toolchain/android-sdk
export PATH=$JAVA_HOME/bin:$PATH
cd TouchParchis
./gradlew clean assembleDebug
./gradlew lintDebug
./gradlew test
```

APK: `TouchParchis/app/build/outputs/apk/debug/app-debug.apk`
Long Gradle runs: start in the background and tail a log (foreground commands cap at ~1080s).

## Layout

- `engine/` — pure Kotlin, no Android imports. `GameEngine` owns every rule; `RulesData` holds route
  tables (`index 0=yard … 72=Home Box 5`, 73 entries), start squares, safe squares, seat config.
- `ai/` — `RulesAI`, chooses only from `engine.legalActions()`.
- `data/` — hand-written JSON parser, `board_coords.json` / `game_data.json` loaders.
- `ui/` — `BoardLayout` is the *only* place fractions become pixels (aspect-fit). `PawnPositioner`
  resolves normalized pawn centers. `GameViewModel` drives the engine from one frame `tick(now)`.
  `TouchParchisApp` draws plates and code overlays on a Compose `Canvas`.
- `res/drawable-nodpi/` — approved plates and pawn sprites, byte-identical to the handoff. Never
  crop, stretch, recolor, or flatten them.

## Invariants that are easy to break

- All coordinates are **fractions (0.0–1.0)** of the plate's own width/height. No pixel coordinates
  anywhere in code. Clean gameplay plate is **691×1536** px (stored as `screen_gameplay.png`).
- Engine takes an injectable `RandomSource`; the ViewModel takes an injectable `clock`.
- After every reel settle and after every move, re-check `legalActions()`. A spin or a move can
  leave `AWAIT_MOVE` with zero legal actions — end the turn then, or the game deadlocks.
- Doubles extra spin: `spinLocked = e.currentColor != humanColor` (the human must be able to spin).
- All user-visible text lives in `strings.xml`; `UiMessage(resId, color)` carries a color arg.

## V1 scope guardrails

No setup pages, names, audio, ads, monetization, tutorials, pause menus, online play, or saving.
No network permissions. Side-view `pawn_selector_*` sprites are reserved and unused in V1.
