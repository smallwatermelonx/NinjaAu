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

1. **`GameManager`** — Global singleton managing script state (IDLE/RUNNING/PAUSED). Launches `WorkflowEngine`, exposes `StateFlow`s for UI binding. Manages three business lines: daily bounties, personal bounties, and NS (逆袭) event bounties.

2. **`WorkflowEngine`** — Main loop dispatches to `GameNode.execute()` based on `GamePhase`. Has `globalFailCount` (max 3 failures → script stops). Each node has 30s timeout via `checkNodeTimeout()`. Supports automatic switching from daily → personal bounties when daily completes.

3. **`GameNode` interface** — Single method: `execute(ctx: GameContext): GamePhase?`. 12 implementations covering all game screens.

### Node Execution Order

```
HALL → RECRUIT_LIST → BOUNTY_DETAIL → BATTLE_LOADING → FIGHT → SETTLEMENT → IDLE (loop)
Personal: PERSONAL_BOUNTY_CENTER → PERSONAL_BOUNTY_DETAIL → PERSONAL_BOUNTY_PUBLISH
```

### Key Dependencies Injected via `NodeContext`

- `detector: SceneDetector` — Template matching
- `captureBitmap(): Bitmap?` — Screen capture via `ScreenCapture`
- `click(x, y)` — Gesture simulation via `NinjaAccessibilityService`
- `log(msg)` — Logging to UI
- `onPageEvent(event)` — Toast notifications

## Business Lines

Three independent bounty automation lines:

1. **日常悬赏 (Daily)** — Standard bounty grades (D through SS+). Default 3-5 runs per grade group.
2. **个人悬赏 (Personal)** — Personal bounty center. SS+ is locked/unavailable. Auto-starts after daily completes.
3. **逆袭悬赏 (NS)** — Event bounty grades (NSS+, NS, NA). Independent toggle.

Additionally, **藏宝图 (Treasure Map)** UI exists but automation is not yet implemented.

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
- Test method: `SceneDetector.testNodeTemplates(screen, group)` for debugging template matches by node

### ScreenState Enum

22+ states covering all game screens. Each maps to a template path + threshold in `SceneDetector.templates`.

## Key Models

- **`BountyGrade`** — 12 grades (D through NSS+), organized into `GradeGroup` (A/A+ share 3 runs, S/S+ share 5 runs). Has `canChaseDream` property for grades that support dream-chasing mode.
- **`GameContext`** — Mutable runtime state: `currentPhase`, `activeGrades`, `runCounts`, `currentBounty`, `actualGrade`, `businessLine` (DAILY/PERSONAL), `personalActiveGrades`, `chaseDreamGrades`
- **`GamePhase`** — 15 phases including personal bounty phases (PERSONAL_BOUNTY_CENTER, PERSONAL_BOUNTY_DETAIL, PERSONAL_BOUNTY_PUBLISH)
- **`BountyConfig`** — User-selected bounty configuration per grade with `chaseDream` flag

## UI Architecture

Jetpack Compose with Material3. Two-tab layout (首页/设置) with 2:6:2 split:

- **Left 20%**: Task list (daily/personal/NS/treasure toggles + gear config buttons) + Link Start button
- **Center 60%**: Config panel showing grade selections for the active business line
- **Right 20%**: Log viewer

Theme: VS Code Dark+ / IntelliJ Light with runtime toggle.

## Floating Window Architecture

`FloatingWindowService` is a foreground service creating overlay windows via `WindowManager`:

1. **Floating ball** — Draggable, edge-snaps to left/right, auto-hides after 5s
2. **Menu** — Staggered button animation, expands from ball position
3. **HUD** — Top-right progress display (always visible when running)
4. **Toast** — Page navigation notifications (queued, auto-dismiss)

## Permissions Required

- `SYSTEM_ALERT_WINDOW` — Floating window overlay
- `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_MEDIA_PROJECTION` — Screen capture
- Accessibility service — Gesture simulation (bound to `com.pandadagames.ninja.global`)

## Known Issues

1. `globalFailCount` never resets — 3 scattered transient exceptions stop the script permanently
2. `DefeatNode` and `RecruitInviteNode` are TODO stubs (return to previous phase)
3. Template images in `assets/templates/` are git-ignored — must be added manually after clone
4. `PermissionManager.resumeMediaProjection()` requires Service context on Android 12+
5. Treasure map automation not yet implemented (UI only)
6. Personal bounty center/detail/publish nodes are framework stubs
