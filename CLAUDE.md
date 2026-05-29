# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
# Build debug APK (requires JDK 11+, Android Studio JBR works)
export JAVA_HOME="/d/Android/Android Studio/jbr"
./gradlew assembleDebug

# APK output: app/build/outputs/apk/debug/app-debug.apk
```

No unit tests exist yet. `app/src/test/` and `app/src/androidTest/` are empty.

## Project Overview

Android automation tool for the game "Ninja Must Die 3" (忍者必须死3). Automates bounty hunting: navigation → recruit scanning → team joining → combat → reward collection.

**Package**: `com.example.ninjaau` | **Min SDK**: 28 | **Target SDK**: 34

## Architecture: MAA-Inspired Node Pattern

The core pipeline follows a node-based automation pattern:

```
GameManager (singleton) → WorkflowEngine → GameNode implementations
```

### Core Flow

1. **`GameManager`** — Global singleton managing script state (IDLE/RUNNING/PAUSED). Launches `WorkflowEngine`, exposes `StateFlow`s for UI binding.

2. **`WorkflowEngine`** — Main loop dispatches to `GameNode.execute()` based on `GamePhase`. Has `globalFailCount` (max 3 failures → script stops). Each node has 30s timeout via `checkNodeTimeout()`.

3. **`GameNode` interface** — `recognize(screen: Bitmap): RecognizeResult` + `execute(ctx: GameContext): GamePhase?`. 9 implementations, 2 are TODO stubs.

### Node Execution Order

```
HALL → RECRUIT_LIST → BOUNTY_DETAIL → BATTLE_LOADING → FIGHT → SETTLEMENT → IDLE (loop)
```

### Key Dependencies Injected via `NodeContext`

- `detector: SceneDetector` — Template matching
- `captureBitmap(): Bitmap?` — Screen capture via `ScreenCapture`
- `click(x, y)` — Gesture simulation via `NinjaAccessibilityService`
- `log(msg)` — Logging to UI
- `onPageEvent(event)` — Toast notifications

## Screen Capture & Recognition

### Critical: Android 12+ MediaProjection Constraint

**MediaProjection MUST be created from a foreground service context with `FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION`**, not from Application context. `FloatingWindowService.onCreate()` handles this. `GameManager.startScript()` only waits for the projection to become available — it does NOT create it.

### Template Matching Pipeline

```
ScreenCapture.capture() → Bitmap → SceneDetector.matchTemplate() → TemplateMatcher (OpenCV TM_CCOEFF_NORMED)
```

- Templates stored in `app/src/main/assets/templates/` (git-ignored)
- Per-template thresholds (0.6–0.85) configured in `SceneDetector.templates` map
- `SceneDetector` caches loaded bitmaps in memory
- Test method: `SceneDetector.testMatchStates(screen, states)` returns `List<TemplateMatchResult>` with similarity/threshold/coordinates

### ScreenState Enum

22 states covering all game screens. Each maps to a template path + threshold in `SceneDetector.templates`.

## Key Models

- **`BountyGrade`** — 12 grades (D through NSS+), organized into `GradeGroup` (A/A+ share 3 runs, S/S+ share 5 runs)
- **`GameContext`** — Mutable runtime state: `currentPhase`, `activeGrades`, `runCounts`, `currentBounty`, `actualGrade`
- **`GamePhase`** — 12 phases (IDLE, HALL, RECRUIT_LIST, BOUNTY_DETAIL, BATTLE_LOADING, FIGHT, SETTLEMENT, etc.)
- **`BountyConfig`** — User-selected bounty configuration per grade

## Floating Window Architecture

`FloatingWindowService` is a foreground service creating 4 overlay windows via `WindowManager`:

1. **Floating ball** — Draggable, edge-snaps to left/right, auto-hides after 5s
2. **Menu** — Staggered button animation, expands from ball position
3. **HUD** — Top-right progress display (always visible when running)
4. **Info panel** — Log viewer (visible only during大厅/招募列表 pages)
5. **Toast** — Page navigation notifications (queued, auto-dismiss)

## Permissions Required

- `SYSTEM_ALERT_WINDOW` — Floating window overlay
- `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_MEDIA_PROJECTION` — Screen capture
- Accessibility service — Gesture simulation (bound to `com.pandadagames.ninja.global`)

## Known Issues

1. `globalFailCount` never resets — 3 scattered transient exceptions stop the script permanently
2. `DefeatNode` and `RecruitInviteNode` are TODO stubs (return to previous phase)
3. Template images in `assets/templates/` are git-ignored — must be added manually after clone
4. `PermissionManager.resumeMediaProjection()` requires Service context on Android 12+
