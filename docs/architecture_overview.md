# NinjaAu 项目架构文档

> 最后更新: 2026-06-03
> 版本: v2.4

---

## 目录

1. [项目概述](#1-项目概述)
2. [技术栈](#2-技术栈)
3. [模块架构总览](#3-模块架构总览)
4. [模型层详解](#4-模型层详解)
5. [核心引擎详解](#5-核心引擎详解)
6. [识别模块详解](#6-识别模块详解)
7. [截图模块详解](#7-截图模块详解)
8. [无障碍模块详解](#8-无障碍模块详解)
9. [悬浮窗模块详解](#9-悬浮窗模块详解)
10. [工具库说明](#10-工具库说明)
11. [UI 层说明](#11-ui-层说明)
12. [核心流程图](#12-核心流程图)
13. [数据流架构](#13-数据流架构)
14. [已知问题与改进建议](#14-已知问题与改进建议)
15. [待定设计决策](#15-待定设计决策)

---

## 1. 项目概述

NinjaAu 是一个 Android 自动化工具，专为手游《忍者必须死3》(Ninja Must Die 3, 忍3) 的悬赏玩法设计。它通过 Android AccessibilityService（模拟手势）+ MediaProjection（屏幕截图）+ OpenCV 模板匹配（图像识别）实现了"导航→扫描→加入→战斗→结算→继续"的完整自动化闭环。

### 核心目标

全自动刷悬赏：用户勾选需要的悬赏等级，启动脚本后自动循环执行：

1. **导航** 从当前界面到招募列表
2. **扫描** 以 100ms 高频检测悬赏等级图标和加入按钮
3. **加入** 点击加入队伍 → 等级校验 → 点击准备
4. **等待战斗** 检测战斗开始（下划警告）→ 进入战斗
5. **战斗** 释放技能 → 等待结束
6. **结算** 领奖 → 回到大厅 → 继续下一轮
7. 所有等级达到目标次数后自动停止

### 使用方式

- 主 UI：Jetpack Compose 页面，勾选等级、启动脚本
- 悬浮窗：前台 Service，显示控制按钮、进度 HUD、日志和 Toast 提示
- 权限：无障碍服务 + 悬浮窗 + MediaProjection 截图

---

## 2. 技术栈

| 技术 | 用途 | 版本 |
|------|------|------|
| Kotlin | 主要开发语言 | 2.0.0 |
| Android Gradle Plugin | 构建系统 | 8.13.2 |
| compileSdk / targetSdk | 编译/目标 SDK | 34 |
| minSdk | 最低支持 | 28 |
| Jetpack Compose | UI 框架 (主页面) | BOM 2024.04.01 |
| Material3 | UI 组件库 | via Compose |
| Android AccessibilityService | 模拟手势点击 | 系统 API |
| MediaProjection API | 屏幕截图 | 系统 API |
| OpenCV (`com.quickbirdstudios:opencv:4.5.3.0`) | 图像模板匹配 | 4.5.3.0 |
| Kotlin Coroutines + Flow | 异步 + 响应式数据流 | via Kotlin |
| SharedPreferences | 配置持久化 | 系统 API |

### 项目依赖关系

```
NinjaApp (Application)
  └── LogUtil.init()

MainActivity (Launcher)
  └── NinjaScriptMainUI (Compose UI)
        ├── PermissionManager → 检查权限
        ├── BountyConfigStorage → 持久化勾选
        └── GameManager → 启动/暂停/停止

FloatingWindowService (前台 Service)
  ├── 观察 GameManager 的三个 Flow
  ├── 悬浮球控制（开始/悬赏选择/关闭）
  ├── HUD 覆盖层（右上角进度）
  └── Toast 覆盖层（中上方页面事件）

GameManager (全局单例)
  └── WorkflowEngine (每次运行创建)
        ├── ScreenCapture → MediaProjection → Bitmap
        ├── SceneDetector → Bitmap → ScreenState + 坐标
        │     ├── TemplateMatcher → OpenCV TM_CCOEFF_NORMED
        │     └── AssetUtil → assets/ 加载模板
        ├── NinjaAccessibilityService → clickAt(x, y)
        └── GameContext + GamePhase → 状态机
```

---

## 3. 模块架构总览

```
com.example.ninjaau/
├── NinjaApp.kt              # Application 入口
├── MainActivity.kt          # 启动 Activity
├── core/
│   ├── GameNode.kt          # 节点接口 + NodeContext + checkNodeTimeout
│   ├── GameManager.kt       # 核心控制器（全局单例）
│   ├── WorkflowEngine.kt    # 自动化流水线引擎
│   ├── accessibility/
│   │   └── NinjaAccessibilityService.kt  # 无障碍模拟点击
│   ├── capture/
│   │   ├── ScreenCapture.kt              # MediaProjection 截图
│   │   └── CapturePermissionActivity.kt  # 权限请求 Activity
│   ├── floating/
│   │   └── FloatingWindowService.kt      # 悬浮窗前台 Service
│   ├── node/
│   │   ├── HallNode.kt                   # 大厅导航
│   │   ├── BountyListNode.kt             # 招募列表扫描
│   │   ├── BountyDetailNode.kt           # 悬赏详情/队伍房间
│   │   ├── BattleLoadingNode.kt          # 战斗加载
│   │   ├── FightNode.kt                  # 战斗
│   │   ├── SettlementNode.kt             # 结算领奖
│   │   ├── RecruitInviteNode.kt          # 招募邀请弹窗
│   │   ├── DefeatNode.kt                 # 战斗失败
│   │   ├── RecoveryNode.kt               # 异常恢复
│   │   ├── PersonalBountyCenterNode.kt   # 个人悬赏中心
│   │   ├── PersonalBountyDetailNode.kt   # 个人悬赏详情
│   │   └── PersonalBountyPublishNode.kt  # 发布个人悬赏
│   ├── recognition/
│   │   ├── SceneDetector.kt    # 场景识别（模板缓存+阶段检测）
│   │   └── TemplateMatcher.kt  # OpenCV 模板匹配引擎
│   └── util/
│       ├── AssetUtil.kt           # assets 文件读取
│       ├── BountyConfigStorage.kt # 悬赏配置持久化
│       ├── Constant.kt            # 全局常量
│       ├── LogUtil.kt             # 日志工具
│       ├── OpenCVUtil.kt          # OpenCV 初始化 + Mat 转换
│       ├── PermissionManager.kt   # 权限管理（MediaProjection 持久化）
│       └── ToastUtil.kt           # Toast 封装
├── model/
│   ├── BountyConfig.kt       # 悬赏配置数据类
│   ├── BountyGrade.kt        # 悬赏等级枚举
│   ├── GameContext.kt        # 运行时上下文 + GamePhase 枚举
│   └── ScreenState.kt        # 屏幕状态枚举
├── ui/
│   ├── NinjaScriptMainUI.kt # Compose 主 UI（Theme + 配置面板）
│   └── theme/
│       └── Theme.kt         # NinjaAuTheme（透传）
└── res/
    ├── layout/layout_floating_window.xml
    ├── drawable/float_ball_bg.xml, float_btn_bg.xml
    ├── xml/accessibility_service_config.xml
    ├── values/, values-zh/
    └── assets/templates/ → 模板 PNG
```

---

## 4. 模型层详解

### 4.1 `BountyGrade` — 悬赏等级枚举

**文件**: `model/BountyGrade.kt`

定义 12 个悬赏等级的枚举，每个等级包含：

| 字段 | 类型 | 说明 | 示例 |
|------|------|------|------|
| `key` | String | 序列化键 | `"ss_plus"` |
| `displayName` | String | UI 显示名 | `"SS+"` |
| `defaultRuns` | Int | 默认目标次数 | 1~5 |
| `priority` | Int | 排序优先级 | 0~14 |
| `level` | Int | 建议角色等级 | 30~125 |
| `templateName` | String | 模板文件名 | `"ss_plus.png"` |

派生方法:
- `gradeIconPath()` → `"templates/recruit_list/{displayName}.png"` — 招募列表中的等级刻字图标
- `levelIconPath()` → `"templates/team_room/lv{level}.png"` — 队伍房间中的建议等级标识
- `isEvent` → NSS+/NS/NA 为活动悬赏，其余为日常

排序: `sorted()` 按 `priority` 升序 → `SS+(0) > SS(1) > S+(2) > S(3) > A+(4) > A(5) > B(6) > C(7) > D(8) > NSS+(12) > NS(13) > NA(14)`

**设计问题**:
- `levelIconPath()` 中 `lv100.png` 对应 SS 等级但模板缺失（已标记 TODO）
- 等级与经验值映射关系隐式在 `levelIconPath()` 中硬编码

### 4.2 `BountyConfig` — 悬赏配置数据类

```kotlin
data class BountyConfig(
    val grade: BountyGrade,
    val enabled: Boolean = false,
    val targetRuns: Int = grade.defaultRuns,
    val completedRuns: Int = 0,
    val chaseDream: Boolean = false  // 追梦模式：跳过每日上限检查
)
```

`defaultList()`: 生成全部 12 个等级的配置列表，日常默认 `enabled=true`，活动默认 `enabled=false`。

持久化由 `BountyConfigStorage` 管理，分别存储日常、个人、逆袭三组配置到 SharedPreferences。

### 4.3 `GameContext` + `GamePhase` — 运行时上下文

```kotlin
data class GameContext(
    var currentPhase: GamePhase,
    var activeGrades: List<BountyGrade>,   // 从勾选 → 随完成逐步移除
    val totalGrades: List<BountyGrade>,    // 所有勾选等级（含已完成）
    val runCounts: MutableMap<BountyGrade, Int>,
    val targetRuns: Map<BountyGrade, Int>,
    val chaseDreamGrades: Set<BountyGrade>, // 追梦模式等级集合
    var currentBounty: BountyGrade?,       // 当前正在加入的悬赏
    var actualGrade: BountyGrade?,         // 实际匹配到的等级
    var totalCycles: Int,
    var businessLine: BusinessLine,        // DAILY / PERSONAL
    val personalActiveGrades: List<BountyGrade>,
    val personalTargetRuns: Map<BountyGrade, Int>,
    val personalBountyEnabled: Boolean,
    var personalBountyCompleted: Boolean
)
```

`GamePhase` 包含 15 个阶段，含个人悬赏阶段（PERSONAL_BOUNTY_CENTER/DETAIL/PUBLISH）。
}
```

`GamePhase` 枚举：`IDLE → LOBBY → CHAT → RECRUIT_LIST → TEAM_ROOM → FIGHT → SETTLEMENT → RECOVERY → DONE`

**关键设计**: `activeGrades` 是动态缩小的集合 — 一个等级达到目标次数后即从中移除，驱动 `remainingGrades()` 和完成判定。

### 4.4 `ScreenState` — 屏幕状态枚举

20 个枚举常量，按游戏区域分类：

- 大厅: `LOBBY`, `CHAT_ICON`
- 聊天/招募: `RECRUIT_TAB`, `RECRUIT_LIST`, `OUT_OF_RANGE_RECRUIT`, `RECRUIT_INVITE`
- 入队: `JOIN_BUTTON`, `TEAM_ROOM`, `TEAM_COMPLETED`, `TEAM_FULL`, `READY_BUTTON`, `WAITING_SCREEN`, `EXIT_CONFIRM`, `DAILY_LIMIT`
- 战斗: `BATTLE_WARNING`, `BATTLE_ACTIVE`, `ULTIMATE_SKILL`, `WEAPON_SKILL`, `DEFEAT_POPUP`
- 结算: `SETTLEMENT_POPUP`, `CONFIRM_BUTTON`
- 通用: `BACK_BUTTON`, `CHAT_TAB`
- 兜底: `UNKNOWN`

**问题**: `BATTLE_ACTIVE` 和 `LOBBY` 在 `SceneDetector.templates` 映射和 `detectionOrder` 中均未被引用，实际为死代码。

### 4.5 `ActionResult` — 泛型结果包装（未使用）

预留的泛型结果类，当前未被任何代码引用。

---

## 5. 核心引擎详解

### 5.1 `GameManager` — 全局控制器

**文件**: `core/GameManager.kt`  
**类型**: `object` 单例  
**职责**: 脚本生命周期管理、事件分发、协调 WorkflowEngine

#### 状态管理

通过四个 Flow 向外暴露状态：

| Flow | 类型 | 内容 | 消费者 |
|------|------|------|--------|
| `state` | `StateFlow<ScriptState>` | IDLE/RUNNING/PAUSED | UI 按钮、悬浮窗图标 |
| `logEvents` | `SharedFlow<String>` | 运行日志行 | 悬浮窗日志面板、UI 日志 |
| `bountyProgress` | `StateFlow<Map<BountyGrade, Pair<Int,Int>>>` | 各等级完成/目标次数 | 悬浮窗 HUD + 信息面板 |
| `pageEvents` | `SharedFlow<String>` | 页面跳转事件 | 悬浮窗 Toast |

#### 脚本生命周期

```
startScript() → state=RUNNING
     ↓
WorkflowEngine.runLoop(selectedBounties)
     ↓ 用户点击暂停
pauseScript() → 取消 Job → state=PAUSED → 暂停 MediaProjection
     ↓ 用户点击恢复
resumeScript() → 恢复 MediaProjection → state=RUNNING → 创建新 Job
     ↓
stopScript() → 取消 Job → 释放 MediaProjection → state=IDLE
```

**问题**: `resumeScript()` 几乎完全复制了 `startScript()` 的 WorkflowEngine 启动代码（DRY 违规）。

#### 权限等待

`startScript()` 中有一个忙碌等待循环：
```kotlin
while (PermissionManager.mediaProjection == null && waited < 20) {
    if (PermissionManager.hasProjectionPermission())
        PermissionManager.initMediaProjection(appContext)
    delay(500); waited++
}
```
最大等待 10 秒，超时则退出。这是粗暴的轮询模式，但在进程重启后恢复授权数据的场景下是必要的。

### 5.2 `WorkflowEngine` — 自动化流水线

**文件**: `core/WorkflowEngine.kt`  
**类型**: `class`（每次运行创建实例）  
**职责**: 实现完整的自动化状态机

#### 常量配置

| 常量 | 值 | 说明 |
|------|-----|------|
| `MAX_GLOBAL_FAIL` | 3 | 整体判定失败上限 |
| `NAVIGATE_RETRIES` | 6 | 导航到招募列表的重试次数 |
| `POST_CLICK_DELAY` | 1000ms | 点击后等待 UI 稳定的延迟 |
| `NORMAL_INTERVAL_MS` | 1000ms | 普通检测周期 |
| `FAST_INTERVAL_MS` | 100ms | 招募列表高速检测周期 |
| `LINEAR_MAX_MISS` | 3 | 结算阶段连续无匹配上限 |
| `LINEAR_MAX_LOOP` | 300 | 结算阶段最大循环数 |
| `WAIT_BATTLE_TIMEOUT_MS` | 15000ms | 等待战斗开始超时 |

#### 主循环

```
while (active && phase != DONE && globalFail < 3) {
    try {
        next = when (currentPhase) {
            IDLE, LOBBY, CHAT, RECRUIT_LIST → phaseNavigateAndScan()
            TEAM_ROOM → phaseValidate()
            FIGHT → phaseBattle()
            SETTLEMENT → phaseClaim()
            RECOVERY → { delay(1500); IDLE }
        }
        currentPhase = next
        emitProgress()
        if (phaseChanged) onPageEvent()
    } catch (e: Exception) {
        globalFailCount++
        if (globalFailCount >= 3) → writeCrashLog() + STOP
    }
}
```

#### 各阶段详解

##### 阶段 1-3: `phaseNavigateAndScan()` — 导航 + 扫描 + 加入

这是最复杂的阶段（~170 行），内部又分三层：

1. **导航（节点 1+2）**: `ensureRecruitView()` → 从任意界面导航到招募列表
   - 6 次重试，每次用 `SCOPE_NAVIGATE` 按优先级检测
   - 连续 3 次 UNKNOWN 后调 `detectCurrentPage()` 兜底
   - 兜底失败 throw RuntimeException → 全局异常处理 → 3 次后停止

2. **扫描循环（节点 3，100ms 周期）**:
   - **Block ① 刷新**: `OUT_OF_RANGE_RECRUIT` → 点击刷新；`RECRUIT_INVITE` → 点击更新列表
   - **Block ② 校验模式** (`currentBounty != null`): 检测已达上限、加入失败、准备按钮+等级校验
   - **Block ③ 普通扫描**: `matchAnyGrade()` → 匹配成功 → 找 JOIN_BUTTON → 点击
     - 匹配成功但无 JOIN_BUTTON → 检测 RECRUIT_INVITE / READY_BUTTON / 3次异常 → 全屏判定
   - 100ms×3 无匹配 → 检测是否被邀请进队伍，或离开招募界面

**关键设计决策**:
- 使用 100ms 快速扫描周期（相对于其他阶段的 1s）
- 等级匹配通过 `matchAnyGrade()` 用集合操作（所有已勾选等级逐一尝试）
- 3 次异常 → `detectCurrentPage()` 兜底 → 3 次兜底失败 → STOP
- `currentBounty` 作为校验模式的开关

##### 阶段 4: `phaseValidate()` — 队伍房间准备

```
等待战斗（15s 超时）:
  - BATTLE_WARNING → 点击 → FIGHT
  - WAITING_SCREEN → 继续等待
  - SETTLEMENT/CONFIRM → 已结束 → SETTLEMENT
  - CHAT_ICON/RECRUIT_TAB → 回到大厅 → IDLE
  超时 → exitTeam() → IDLE
```

**问题**: 准备按钮的点击在 `phaseNavigateAndScan()` 中已完成（Block ② 校验模式），本阶段只等待战斗开始。这与流程图不符——`phaseValidate` 实际是准备后的等待，准备是扫描阶段完成的。

##### 阶段 5: `phaseBattle()` — 战斗

```
循环:
  - SETTLEMENT_POPUP → SETTLEMENT
  - CONFIRM_BUTTON → SETTLEMENT
  - DEFEAT_POPUP → SETTLEMENT
  - 每 500ms 尝试释放技能 (useSkills)
  - 每 10 次无匹配 → SCOPE_BATTLE 全屏判定
    - CHAT_ICON/RECRUIT_TAB → IDLE
    - 3 次 UNKNOWN → detectCurrentPage() → 3 次失败 → throw
```

**问题**: `useSkills()` 只按顺序检测并点击大招和武器技能，无战斗阶段感知（如 BOSS 战、小怪阶段的技能释放策略差异）。

##### 阶段 6: `phaseClaim()` — 结算领奖

```
循环（最多 300 次）:
  - SETTLEMENT_POPUP → clickOutside() 关闭
  - CONFIRM_BUTTON → 点击确认
  - CHAT_ICON → 回到大厅 → break
  - 3 次无匹配 → SCOPE_CLAIM 全屏判定
    - CHAT_ICON/RECRUIT_TAB → break
    - 3 次 UNKNOWN → detectCurrentPage() → 3 次失败 → throw

结算完成后:
  - runCounts[grade]++
  - 达到目标 → activeGrades 移除该等级
  - activeGrades 为空 → DONE
```

##### 辅助方法

- `detectCurrentPage()`: 全量 `SCOPE_ALL` 兜底检测，返回 `GamePhase?`
- `exitTeam()`: 点击退出/返回按钮直到回到大厅，10 次重试
- `clickOutside()`: 在弹窗外部点击关闭弹窗
- `captureBitmap()`: 截图，3 次重试
- `writeCrashLog()`: 3 次整体判定失败后写崩溃日志到 `filesDir/crash_logs/`

---

## 6. 识别模块详解

### 6.1 `SceneDetector` — 场景识别层

**文件**: `core/recognition/SceneDetector.kt`  
**类型**: `class`  
**职责**: 将 Bitmap 映射为 ScreenState + 点击坐标

#### 模板映射

`templates: Map<ScreenState, TemplateEntry>` 包含约 22 个状态→路径映射：

```
ScreenState → assets 路径 + 阈值
示例:
  CHAT_ICON      → "templates/lobby/hall_chat.png"     (0.75)
  RECRUIT_TAB    → "templates/chat/team_recruit.png"     (0.8)
  JOIN_BUTTON    → "templates/recruit_list/join_team.png" (0.8)
  READY_BUTTON   → "templates/team_room/prepare.png"      (0.8)
  BATTLE_WARNING → "templates/fight/warning.png"          (0.7)
  SETTLEMENT_POPUP → "templates/settlement/black.png"     (0.7)
  ...
```

阈值范围: 0.6~0.9，大部分为 0.8。阈值不一致但相互独立。

#### 阶段检测 Scope

预定义的 `List<ScreenState>` 常量，限制各阶段要检测的状态范围：

| Scope | 包含的状态数 | 用途 |
|-------|-------------|------|
| `SCOPE_NAVIGATE` | 10 | 导航到招募列表的全面检测 |
| `SCOPE_RECRUIT` | 5 | 招募列表中快速循环 |
| `SCOPE_TEAM_ROOM` | 7 | 队伍房间检测 |
| `SCOPE_WAIT_BATTLE` | 6 | 等待战斗开始 |
| `SCOPE_BATTLE` | 6 | 战斗中检测 |
| `SCOPE_CLAIM` | 3 | 结算检测 |
| `SCOPE_EXIT` | 5 | 退出队伍 |
| `SCOPE_ALL` | 全部 | 兜底全量检测 |

#### 三级检测策略

1. **`detectForPhase(screen, scope)`** — 按阶段检测，只匹配 scope 中的状态，最常用
2. **`detectWithCoord(screen)`** — 全量 `detectionOrder`，用于兜底
3. **`matchTemplate(screen, state)`** — 单状态匹配，返回坐标或 null

#### 等级匹配

- `matchGradeIcon()`: 匹配等级刻字图标（招募列表），阈值 0.85
- `matchLevelIcon()`: 匹配建议等级图标（队伍房间），阈值 0.9
- `matchAnyGrade()`: 按优先级对多个等级逐一匹配，返回第一个成功的
- `matchAnyLevelIcon()`: 同上，建议等级版本
- `matchTemplateNear()`: 在指定坐标附近搜索模板，用于等级→加入按钮的邻近匹配

#### 缓存机制

三层 LRU 缓存（`mutableMapOf`）：

- `templateCache: Map<String, Bitmap>` — 模板图片，按文件路径
- `gradeIconCache: Map<BountyGrade, Bitmap>` — 等级图标
- `levelIconCache: Map<BountyGrade, Bitmap>` — 建议等级图标

首次命中后常驻内存，`release()` 时全部回收。

#### 全量检测顺序

`detectionOrder` (22 状态): `CONFIRM > SETTLEMENT > DEFEAT > BATTLE_WARNING > WAITING > READY > DAILY_LIMIT > TEAM_COMPLETED > TEAM_FULL > EXIT_CONFIRM > TEAM_ROOM > JOIN_BUTTON > RECRUIT_INVITE > RECRUIT_TAB > OUT_OF_RANGE_RECRUIT > CHAT_ICON > BACK_BUTTON > CHAT_TAB`

### 6.2 `TemplateMatcher` — OpenCV 匹配引擎

**文件**: `core/recognition/TemplateMatcher.kt`  
**类型**: `object` 单例  
**方法**: `match(screen: Bitmap, template: Bitmap, threshold: Float): MatchResult`

```kotlin
data class MatchResult(
    val isMatched: Boolean,      // 是否达到阈值
    val similarity: Double,      // 最高相似度 (TM_CCOEFF_NORMED)
    val matchX: Int, val matchY: Int,  // 左上角坐标
    val centerX: Float, val centerY: Float  // 中心点坐标
)
```

**算法**: OpenCV `Imgproc.TM_CCOEFF_NORMED`（归一化相关系数匹配），值域 [0, 1]。

**流程**: Bitmap → RGBA Mat → BGR Mat → `matchTemplate()` → `Core.minMaxLoc()` → 获取最高分和坐标 → 自动释放所有 Mat。

**局限性**:
- `TM_CCOEFF_NORMED` 对光照变化敏感，但手游 UI 通常光线恒定
- 每次调用都做完整的图像金字塔匹配，性能开销随模板大小增加

---

## 7. 截图模块详解

### `ScreenCapture` — 截图捕获器

**文件**: `core/capture/ScreenCapture.kt`  
**类型**: `class`（单例模式，双重检查锁定）

**核心流程**:

1. `getInstance(context)` → 首次初始化 VirtualDisplay + ImageReader
2. `capture()` → `ImageReader.acquireLatestImage()` → `Image` → 计算 `rowPadding` → `Bitmap`
3. 截图前自动检查 MediaProjection 状态，必要时重试初始化

**VirtualDisplay 配置**:
- 宽度 = 屏幕宽度，高度 = 屏幕高度，DPI = 屏幕 DPI
- `VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR`
- Surface 从 ImageReader 获取

**问题**:
- `acquireLatestImage()` 可能返回 null（两帧之间调用），此时 capture() 返回 null
- 无背压机制 — 如果引擎以 100ms 频率调用 capture()，但帧生成率低于 10fps，大量调用会返回 null
- `rowPadding` 计算（`Image.Plane.rowStride - pixelStride * width`）是边界情况容易出错的地方

### `CapturePermissionActivity`

**文件**: `core/capture/CapturePermissionActivity.kt`  
**类型**: `Activity`  
**职责**: 通过系统对话框请求 MediaProjection 截图授权

使用已弃用的 `startActivityForResult`（应为 Activity Result API `registerForActivityResult`）。授权 token 通过 `PermissionManager.saveProjectionPermission()` 持久化，进程重启后可尝试恢复。

---

## 8. 无障碍模块详解

### `NinjaAccessibilityService`

**文件**: `core/accessibility/NinjaAccessibilityService.kt`  
**类型**: `AccessibilityService`（单例）

**核心能力**: `clickAt(x: Float, y: Float): Boolean`

使用 `GestureDescription.Builder` 创建点击手势：
```kotlin
GestureDescription.Builder()
    .addStroke(GestureDescription.StrokeDescription(
        Path().apply { moveTo(x, y) }, 0, 100  // 100ms 点击持续时间
    ))
    .build()
```

通过 `dispatchGesture()` 发送。

**设计特点**:
- `onAccessibilityEvent()` 故意留空，不处理任何 UI 事件
- 服务配置文件 `accessibility_service_config.xml` 限制仅对游戏包名 `com.pandadagames.ninja.global` 生效
- 单例通过伴生对象静态 `instance` 字段 + `onCreate()` 设置实现

**竞争条件风险**:
`onCreate()` 中设置 `instance` 与 `onBind`/`onInterrupt`/`onDestroy` 的声明周期存在窗口：Activity 在 AccessibilityService 完全绑定前可能调用 `clickAt()`。

---

## 9. 悬浮窗模块详解

### `FloatingWindowService`

**文件**: `core/floating/FloatingWindowService.kt`  
**类型**: 前台 `Service`（约 750 行）  
**职责**: 悬浮球控制 + HUD 进度显示 + Toast 页面提示

#### 三层窗口架构

| 层 | 类型 | 位置 | 交互 | 内容 |
|----|------|------|------|------|
| 悬浮球 | `TYPE_APPLICATION_OVERLAY` | 可拖拽，贴边吸附 | 可触摸 | 控制按钮 + 信息面板 |
| HUD | `TYPE_APPLICATION_OVERLAY` + `NOT_TOUCHABLE` | 右上角固定 | 点击穿透 | 悬赏进度矩形 |
| Toast | `TYPE_APPLICATION_OVERLAY` + `NOT_TOUCHABLE` | 中上方固定 | 点击穿透 | 页面跳转提示 |

#### 悬浮球交互

- **拖拽**: 直接操作 `LayoutParams.x/y`，带 Y 轴钳制（限制在状态栏和导航栏之间）
- **吸附**: 松手后 `ValueAnimator` 驱动平滑过渡到最近左右边缘（250ms, OvershootInterpolator）
- **点击**: 切换菜单展开/收起
- **自动隐藏**: 5 秒无操作，滑出屏幕边缘 30px 可见标签 + alpha 0.5
- **触摸恢复**: 任何触摸事件自动恢复

#### 信息面板

- 从左侧滑入/滑出（`ViewPropertyAnimator`，OvershootInterpolator）
- 显示完成进度 + 运行日志（最多 50 行，自动滚动）
- 展开时自动将悬浮球推到右侧以腾出空间

#### HUD 覆盖层

- 右上角固定位置，`NOT_TOUCHABLE` 不拦截游戏操作
- 标题 `"◆ 进度"` + 紧凑格式 `S 2/5✓ A 0/3 B 0/4`
- 宽度限制约 150dp，字体 14sp
- 通过 `GameManager.bountyProgress` 驱动更新
- 脚本状态控制显隐（RUNNING/PAUSED 显示，IDLE 隐藏）

#### Toast 覆盖层

- 中上方固定，`NOT_TOUCHABLE`
- 队列机制：依次显示，淡入 200ms → 显示 1.5s → 淡出 300ms → 下一项
- 通过 `GameManager.pageEvents` 驱动
- 关键事件：进入大厅/招募列表/匹配加入/战斗开始/结算/全部完成

#### 数据观测

```kotlin
serviceScope.launch {
    GameManager.bountyProgress.collectLatest { progress ->
        updateProgressPanel(progress)  // 信息面板
        updateHudContent(progress)     // HUD
    }
}
serviceScope.launch {
    GameManager.logEvents.collectLatest { msg -> addLogLine(msg) }
}
serviceScope.launch {
    GameManager.pageEvents.collectLatest { event -> showPageToast(event) }
}
```

---

## 10. 工具库说明

| 工具 | 文件 | 职责 |
|------|------|------|
| `LogUtil` | `util/LogUtil.kt` | Logcat 封装，统一标签 |
| `Constant` | `util/Constant.kt` | 全局常量（包名、广播 Action、配置键等） |
| `AssetUtil` | `util/AssetUtil.kt` | assets → Bitmap 加载 |
| `ToastUtil` | `util/ToastUtil.kt` | Android Toast 便捷封装 |
| `OpenCVUtil` | `util/OpenCVUtil.kt` | OpenCV 初始化 + Bitmap ↔ Mat 转换 |
| `PermissionManager` | `util/PermissionManager.kt` | 权限状态管理 + MediaProjection 持久化 |
| `BountyConfigStorage` | `util/BountyConfigStorage.kt` | 悬赏勾选的 SharedPreferences 持久化 |
| `AppControl` | `util/AppControl.kt` | 应用控制辅助（启动脚本，检查运行状态） |

### `PermissionManager` 关键设计

- MediaProjection token 通过 `SharedPreferences` 持久化（`saveProjectionPermission()` / `restoreProjectionPermission()`）
- 进程重启后 `FloatingWindowService.onCreate()` 尝试恢复授权
- 暂停/恢复: `pauseMediaProjection()` → 释放 VirtualDisplay；`resumeMediaProjection()` → 重新创建
- 线程安全: 所有方法通过 `synchronized(lock)` 保护

**问题**: `restoreProjectionPermission()` 返回 boolean 但调用方（`FloatingWindowService.onCreate()`）不检查返回值就直接调用 `initMediaProjection()`。

### `BountyConfigStorage`

简单 SharedPreferences 存储，key 为 `"bounty_prefs"`，存储格式为逗号分隔的已勾选等级 key 列表。

---

## 11. UI 层说明

### `NinjaScriptMainUI` — Compose 主页面

主要 Compose 页面（~400 行），包含：

- **权限状态芯片**: 无障碍服务、悬浮窗权限、截图权限
- **启动/暂停按钮**: "Link Start" / "暂停" / "继续"
- **悬赏等级网格**: 按 4 列排列的复选框，分"日常" / "活动"两组
- **运行日志面板**: 历史日志（最多 200 行），自动滚动

权限检查逻辑:
1. `PermissionManager.isAccessibilityServiceEnabled()` — 跳转系统设置
2. `PermissionManager.hasOverlayPermission()` — 跳转悬浮窗权限设置
3. `MediaProjection` — 启动 `CapturePermissionActivity`

### `BountyCheckList` — 悬赏复选框列表组件

独立的 Compose 组件，使用 `LazyColumn` 实现。**未被主 UI 使用**——主 UI 使用内联的 `Column` + `Row` 网格布局。

### Theme

标准的 Material3 深色主题，但主 UI 大量使用硬编码颜色覆盖默认值。

---

## 12. 核心流程图

### 12.1 主循环流程图

```
                    ┌─────────────────────────────────────────────┐
                    │              runLoop() 主循环                │
                    │   while(active && phase!=DONE && fail<3)    │
                    └─────────────────────────────────────────────┘
                                      │
                    ┌─────────────────┼─────────────────┐
                    │                 │                 │
                    ▼                 ▼                 ▼
            ┌──────────────┐  ┌──────────────┐  ┌──────────────┐
            │  IDLE/LOBBY  │  │   TEAM_ROOM  │  │    FIGHT     │
            │  CHAT/       │  │  phaseVali-  │  │  phaseBattle │
            │  RECRUIT_LIST│  │  date()      │  │              │
            │              │  │              │  │              │
            │  phaseNavi-  │  │ 15s 超时等待  │  │ 释放技能     │
            │  gateAndScan │  │ 战斗开始      │  │ 检测结算     │
            └───────┬──────┘  └──────┬───────┘  └──────┬───────┘
                    │               │                  │
                    │               │                  │
                    ▼               ▼                  ▼
            ┌──────────────┐  ┌──────────────┐  ┌──────────────┐
            │  SETTLEMENT  │  │   RECOVERY   │  │     DONE     │
            │  phaseClaim  │  │  异常恢复     │  │  全部完成    │
            │              │  │  1.5s→IDLE   │  │  退出循环    │
            │ 领奖+计数    │  └──────────────┘  └──────────────┘
            │ activeGrades │         │
            │ 移除完成项    │         │ (全局异常)
            └──────┬───────┘         │
                   │                 │
                   ▼                 ▼
            ┌─────────────────────────────────┐
            │ activeGrades.isEmpty() → DONE   │
            │ else → IDLE (继续循环)           │
            └─────────────────────────────────┘
```

### 12.2 招募列表扫描流程图 (100ms 循环)

```
┌──────────────────────────────────────────────────────────┐
│                  100ms 扫描循环                            │
│                                                          │
│  ① 刷新检测 (仅非校验模式)                                  │
│  ├─ OUT_OF_RANGE_RECRUIT → 点击刷新 (冷却10周期)           │
│  └─ RECRUIT_INVITE → 点击邀请标识 → 列表刷新               │
│                                                          │
│  ② 校验模式 (currentBounty != null)                       │
│  ├─ DAILY_LIMIT → exitTeam()                              │
│  ├─ SCOPE_RECRUIT 匹配 → 加入失败 → 回主流程                │
│  ├─ READY_BUTTON + matchAnyLevelIcon                      │
│  │   ├─ 匹配成功 → 点击准备 → TEAM_ROOM                    │
│  │   └─ 匹配失败 → exitTeam()                              │
│  └─ 不匹配 → continue                                    │
│                                                          │
│  ③ 普通扫描模式                                           │
│  ├─ matchAnyGrade(remaining)                               │
│  │   ├─ 匹配成功 → matchTemplate(JOIN_BUTTON)              │
│  │   │   ├─ 有 JOIN_BUTTON → 点击加入 → 校验模式           │
│  │   │   ├─ RECRUIT_INVITE → 列表过期点击刷新              │
│  │   │   ├─ READY_BUTTON                                   │
│  │   │   │   ├─ levelMatch → TEAM_ROOM                    │
│  │   │   │   └─ 等级不匹配 → exitTeam()                   │
│  │   │   └─ 3次异常 → detectCurrentPage()                  │
│  │   │       ├─ UNKNOWN→throw→全局异常→3次→STOP           │
│  │   │       └─ 成功 → 返回对应 Phase                     │
│  │   └─ 匹配失败 → noMatchCycles++                         │
│  └─ noMatchCycles ≥ 3                                     │
│      ├─ READY_BUTTON → 被邀请 → 校验模式                   │
│      └─ 界面状态变化 → 重新导航                              │
└──────────────────────────────────────────────────────────┘
```

### 12.3 导航流程图

```
ensureRecruitView()
  6 次重试，每次 1s 周期

  SCOPE_NAVIGATE 检测优先级:
  1. UNKNOWN × 3 → detectCurrentPage()
       ├─ null → throw RuntimeException → 全局异常
       └─ 成功 → 返回对应 GamePhase
  2. CHAT_ICON → 点击 → 等待
  3. RECRUIT_TAB → 点击 → 等待 → 返回 null (成功)
  4. RECRUIT_LIST / JOIN_BUTTON / RECRUIT_INVITE → 已在列表中 → 返回 null
  5. SETTLEMENT_POPUP / CONFIRM_BUTTON → 点击关闭
  6. DAILY_LIMIT / DEFEAT_POPUP → clickOutside()
  7. BACK_BUTTON → 点击返回
  8. 其他 → 继续等待

  6 次用完 → throw RuntimeException → 全局异常
```

### 12.4 战斗/结算流程图

```
phaseBattle():
  ┌─ SETTLEMENT_POPUP → SETTLEMENT
  ├─ CONFIRM_BUTTON → SETTLEMENT
  ├─ DEFEAT_POPUP → SETTLEMENT
  ├─ 每 500ms useSkills()
  │   ├─ ULTIMATE_SKILL → 点击大招
  │   └─ WEAPON_SKILL → 点击武器技能
  └─ 每 10 次无匹配 → SCOPE_BATTLE
      ├─ CHAT_ICON/RECRUIT_TAB → IDLE
      └─ 3 × UNKNOWN → detectCurrentPage() → 3 次 → STOP

phaseClaim():
  ┌─ SETTLEMENT_POPUP → clickOutside()
  ├─ CONFIRM_BUTTON → 点击确认
  ├─ CHAT_ICON → 完成 → break
  └─ 3 次无匹配 → SCOPE_CLAIM
      ├─ CHAT_ICON/RECRUIT_TAB → break
      └─ 3 × UNKNOWN → detectCurrentPage() → 3 次 → STOP

  结算后:
  ├─ runCounts[grade]++
  ├─ 达目标次数 → activeGrades 移除该等级
  └─ activeGrades 为空 → DONE
```

---

## 13. 数据流架构

### 控制流

```
用户操作 (UI/FloatingWindow)
     │
     ▼
GameManager 单例
     │  startScript() / pauseScript() / resumeScript() / stopScript()
     │
     ▼
WorkflowEngine (每次运行创建)
     │  runLoop(configs, onProgress)
     │
     ├─ ScreenCapture.capture() → Bitmap
     ├─ SceneDetector.detectForPhase(screen, scope) → ScreenState + 坐标
     ├─ NinjaAccessibilityService.clickAt(x, y) → Unit
     └─ loop → next GamePhase
```

### 数据流

```
WorkflowEngine.log() ──→ postLog lambda──→ GameManager._logEvents ──→ UI / 悬浮窗日志
WorkflowEngine.emitProgress() ──→ onProgress lambda ──→ GameManager._bountyProgress ──→ UI / HUD
WorkflowEngine.onPageEvent?.invoke() ──→ GameManager._pageEvents ──→ 悬浮窗 Toast

GameManager.state ──→ UI 按钮状态 / 悬浮窗播放按钮图标
```

### 架构特点

- **单向数据流**: WorkflowEngine → GameManager (通过 lambda) → UI (通过 Flow)
- **事件驱动**: 使用 `SharedFlow` 处理日志和页面事件（无粘性事件，仅实时推送）
- **状态驱动**: 使用 `StateFlow` 处理脚本状态和悬赏进度（粘性，新订阅者可获取最新值）
- **无侵入**: WorkflowEngine 通过回调（lambda）向外发送数据，不依赖 GameManager 单例

---

## 14. 已知问题与改进建议

### 14.1 架构设计问题

| # | 问题 | 等级 | 说明 |
|---|------|------|------|
| 1 | **WorkflowEngine 高复杂度** | 🔴 | `phaseNavigateAndScan()` ~170 行，嵌套 4 层以上 if-else，逻辑分支超过 20 条。应拆分为独立方法或子状态机 |
| 2 | **FloatingWindowService 职责过重** | 🔴 | 约 750 行，同时管理悬浮球、HUD、Toast。应拆为 3 个独立组件或类 |
| 3 | **resumeScript() 代码重复** | 🟡 | 与 startScript() 的 WorkflowEngine 启动逻辑完全相同。可抽取共享方法 |
| 4 | **全局单例滥用** | 🟡 | GameManager、PermissionManager、TemplateMatcher、LogUtil、OpenCVUtil、AssetUtil、ToastUtil、BountyConfigStorage 全部是 object 单例。测试困难 |
| 5 | **GameManager 与 WorkflowEngine 耦合** | 🟡 | 通过 lambda 解耦了，但 GameManager 的 startScript() 内部直接 new WorkflowEngine(...) |
| 6 | **CapturePermissionActivity 使用过时 API** | 🟡 | `startActivityForResult` 已弃用，应替换为 `ActivityResultContracts.CreateDocument` 或 `registerForActivityResult` |

### 14.2 识别相关问题

| # | 问题 | 等级 | 说明 |
|---|------|------|------|
| 7 | **等级匹配先入为主** | 🔴 | `matchAnyGrade()` 按优先级顺序匹配，一旦低等级误匹配就抢占。当前已提高阈值至 0.85 缓解 |
| 8 | **等级图标与加入按钮错位** | 🔴 | 无验证机制确保匹配到的"级图标"和"加入按钮"属于同一悬赏条目。多悬赏同时可见时可能匹配条目的等级图标 + B 条目的加入按钮 |
| 9 | **模板阈值不一致** | 🟡 | 范围 0.6~0.9 不等。低阈值（0.6）增加误报风险，高阈值（0.9）可能漏检（尤其等级模板 lv*.png） |
| 10 | **大量未使用模板资产** | 🟡 | 59 个 PNG 中约 34 个未被引用（各种 `img_*.png`、`overtime.png`、`friend.png` 等）。未来维护者可能困惑 |
| 11 | **过时的 TODO 注释** | 🟡 | `DAILY_LIMIT` 和 `DEFEAT_POPUP` 的 TODO 注释说模板缺失，但文件已在资产中 |
| 12 | **BATTLE_ACTIVE 和 LOBBY 状态数据死代码** | 🟡 | 这两个 ScreenState 值未在模板映射或检测逻辑中使用 |

### 14.3 可靠性问题

| # | 问题 | 等级 | 说明 |
|---|------|------|------|
| 13 | **点击后无状态验证** | 🔴 | 点击后仅靠 `POST_CLICK_DELAY=1s` 等待，不验证是否确实到达预期状态。若游戏卡顿/网络延迟，1s 可能不够 |
| 14 | **截图可能返回 null** | 🟡 | `captureBitmap()` 内部 3 次重试，但 `ScreenCapture.capture()` 返回 null 的原因未区分（帧未就绪 vs 权限失效） |
| 15 | **自动隐藏后触摸透传** | 🟡 | 侧边隐藏的悬浮球 alpha=0.5 但未设置 NOT_TOUCHABLE，隐藏状态仍拦截触摸。影响用户体验 |
| 16 | **异常被大范围捕获吞没** | 🟡 | `FloatingWindowService` 中多处 `catch (e: Exception) {}` 无日志，WindowManager 异常被静默处理 |

### 14.4 性能问题

| # | 问题 | 等级 | 说明 |
|---|------|------|------|
| 17 | **100ms 全速扫描 + 截图** | 🟡 | 10fps 截屏率在部分设备上可能 CPU 负载过高。OpenCV 模板匹配是 CPU 密集型操作 |
| 18 | **updateProgressPanel 每次全量重建** | 🟡 | 每次进度更新时 `removeAllViews()` + 重新 `addView()`。效率低但当前数据量小（<10 项），影响不大 |
| 19 | **无截图复用** | 🟡 | 单次 100ms 周期内可能多次调用 `captureBitmap()`（例如 Block ①→③ 各截一次）。可用同一帧复用 |

### 14.5 代码质量问题

| # | 问题 | 等级 | 说明 |
|---|------|------|------|
| 20 | **硬编码魔法数字** | 🟡 | `clickOutside()` 中 `0.88f`/`0.2f`、浮窗各种像素偏移量、`exitTeam()` 中 10 次重试 |
| 21 | **NinjaAccessibilityService 线程安全** | 🟡 | 单例 `instance` 在 `onCreate()` 中赋值，与 `clickAt()` 调用存在窗口期 |
| 22 | **BountyCheckList.kt 未使用** | 🟢 | 独立的悬赏复选框组件未被主 UI 引用，可能是重构遗留 |
| 23 | **ActionResult.kt 未使用** | 🟢 | 泛型结果包装类未被任何代码引用 |

### 14.6 改进建议优先级

**P0（必须修）**:
- 7, 8: 等级匹配先入为主 + 错位 → 增加等级图标→加入按钮的邻近绑定验证
- 13: 点击后状态验证 → 替代固定延迟轮询

**P1（重要）**:
- 1: 拆分 `phaseNavigateAndScan()` 
- 2: 拆分 `FloatingWindowService` 
- 3: 抽取 `resumeScript()` / `startScript()` 共享逻辑
- 15: 自动隐藏后设置 NOT_TOUCHABLE

**P2（值得做）**:
- 6: 替换 `startActivityForResult`
- 9: 统一模板阈值策略
- 10: 清理未使用资产
- 17: 优化扫描帧率适配

**P3（未来）**:
- 4: 依赖注入替代全局单例
- 18: View 复用优化
- 19: 截图帧复用

---

## 15. 待定设计决策

### 15.1 等级图标与加入按钮的绑定

当前无机制确保匹配到的等级图标和点击的加入按钮属于同一悬赏条目。潜在的解决方案：

- **方案 A（邻近匹配）**: `matchTemplateNear()` 在等级图标坐标的右下区域搜索 JOIN_BUTTON。已有此方法但只在特定场景使用。
- **方案 B（区域分割）**: 将截图按行分割为多个区域，每个区域独立检测等级和按钮。
- **方案 C（空间映射）**: 建立等级图标→加入按钮的固定坐标映射（假设游戏 UI 布局固定）。

当前使用的混合方案：`matchAnyGrade()` 全局搜索等级图标 → 若有图标无 JOIN_BUTTON，则用 `matchTemplateNear()` 在图标附近搜索。但 `matchTemplateNear()` 在普通扫描模式未调用。

### 15.2 点击确认机制

当前使用固定 1 秒延迟等待 UI 稳定。需决定是否改为轮询式确认：

```
现状: click() → delay(1000ms) → 继续
改进: click() → 轮询目标状态（最多 3s，100ms 周期）→ 超时则异常处理
```

### 15.3 战斗技能策略

当前技能释放仅按顺序检测大招→武器技能。未来可能需要：
- BOSS 阶段优先保持大招
- 小怪阶段随意释放
- 基于血量/BOSS 阶段感知的技能策略

这需要新增状态识别（BOSS 血量条识别等）和策略配置。

### 15.4 多分辨率适配

模板匹配直接依赖像素级匹配，不同分辨率需要不同模板。当前假设固定分辨率。未来需：
- 模板缩放策略（根据屏幕密度缩放模板）
- 或维护多套模板

### 15.5 日志与 HUD 的权限分离

当前 `logEvents` 同时流向 UI 和悬浮窗。是否需要独立的日志等级系统？例如：
- DEBUG: 详细匹配日志（不进 UI 日志）
- INFO: 正常流程日志
- WARN: 异常恢复日志
- ERROR: 致命错误

### 15.6 错误恢复深度

当前 3 级恢复策略（步骤级→页面级→全局级）是否足够？是否需要更多级别？

```
步骤失败 (3次) → 页面识别 (detectCurrentPage)
页面失败 (3次) → 全局恢复 (3次) → 停止写日志
```

可能需要的第 4 级：**自我修复** — 清理缓存、重新初始化 MediaProjection、重启服务。

---

*本文档由 Claude Code 基于 v2.3 代码库自动生成。*
