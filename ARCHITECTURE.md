# NinjaAu 架构设计文档 — v3.1

> 忍三自动化脚本 · 悬赏全流程自动识别与执行
> 设计版本 v3.1 · 2026-05

---

## 目录

1. [项目概述](#1-项目概述)
2. [总体架构](#2-总体架构)
3. [模块目录与职责](#3-模块目录与职责)
4. [数据模型层](#4-数据模型层)
5. [状态管理](#5-状态管理)
6. [核心流程引擎](#6-核心流程引擎)
7. [场景识别系统](#7-场景识别系统)
8. [模板资产清单](#8-模板资产清单)
9. [权限系统](#9-权限系统)
10. [UI 层](#10-ui-层)
11. [数据流](#11-数据流)
12. [异常处理策略](#12-异常处理策略)
13. [已知问题与待办](#13-已知问题与待办)
14. [附录：Phase 状态变迁图](#14-附录phase-状态变迁图)

---

## 1. 项目概述

### 1.1 目标

自动完成《忍者必须死3》日常悬赏任务：从大厅 → 进入招募列表 → 扫描匹配等级 → 加入队伍 → 准备 → 战斗(自动技能) → 结算领奖 → 循环。

### 1.2 技术栈

| 层 | 技术 |
|--|--|
| UI | Jetpack Compose + Material3 |
| 截图 | MediaProjection + VirtualDisplay + ImageReader |
| 图像识别 | OpenCV 4.5.3 (`Imgproc.matchTemplate`, `TM_CCOEFF_NORMED`) |
| 动作 | AccessibilityService (`GestureDescription`) |
| 异步 | Kotlin Coroutines (`StateFlow`, `SharedFlow`, `SupervisorJob`) |
| 构建 | Gradle KTS + AGP 8.13 |
| 最低API | Android 8.0 (API 26) |

### 1.3 工作流总览

```
大厅 ─→ 点聊天按钮 ─→ 点招募tab ─→ 扫描招募列表 ─→ 匹配等级 → 加入队伍
  ↑                                                          │
  │                                              ┌───────────┴───────────┐
  │                                              ▼                       ▼
  │                                          已完成?                  等级校验
  │                                          → 退出队伍               → 仅警告
  │                                              │                       │
  │                                              └───────────┬───────────┘
  │                                                          ▼
  │                                                      点击准备
  │                                                          │
  │                                              ┌───────────┴───────────┐
  │                                              ▼                       ▼
  │                                          超时退出               等待倒计时
  │                                              │                       │
  │                                              └───────────┬───────────┘
  │                                                          ▼
  │                                                      战斗中
  │                                              (自动释放技能)
  │                                                          │
  │                                                     结算弹窗
  │                                                          │
  │                                                     领奖回大厅 ────────┘
```

---

## 2. 总体架构

```
┌──────────────────────────────────────────────────────────────────────┐
│                          UI 层 (Compose + Material3)                  │
│  ┌──────────────────────────┐  ┌──────────────────────────────────┐  │
│  │  NinjaScriptMainUI       │  │  FloatingWindowService           │  │
│  │  · 权限状态栏            │  │  · 悬浮球拖拽/边缘吸附          │  │
│  │  · LINK START/暂停/停止  │  │  · 播放/暂停/停止按钮           │  │
│  │  · 悬赏勾选(日常/活动分栏)│  │  · 悬赏快捷勾选弹窗             │  │
│  │  · 运行日志面板          │  │  · 自动隐藏/展开                │  │
│  └──────────┬───────────────┘  └──────────────────┬───────────────┘  │
└─────────────┼─────────────────────────────────────┼──────────────────┘
              │                                     │
┌─────────────▼─────────────────────────────────────▼──────────────────┐
│                      状态管理层                                       │
│                                                                       │
│  GameManager (ScriptState: IDLE / RUNNING / PAUSED)                   │
│  · 脚本生命周期管理 (start/pause/resume/stop)                          │
│  · 日志事件流 (SharedFlow → UI)                                       │
│  · 悬赏配置同步 (updateBountyConfigs)                                  │
│  · MediaProjection 等待/恢复                                           │
└─────────────────────────────┬─────────────────────────────────────────┘
                              │
┌─────────────────────────────▼─────────────────────────────────────────┐
│                      核心引擎层                                        │
│                                                                       │
│  WorkflowEngine (GamePhase 状态机)                                     │
│  ┌──────────────────────────────────────────────────────────────────┐ │
│  │  Phase 流转:                                                      │ │
│  │  IDLE → SCANNING ─→ JOINING ─→ VALIDATING ─→ WAITING ─→ BATTLE  │ │
│  │     ↑                    ↑            │              │           │ │
│  │     └────(重新扫描)──────┘            │              │           │ │
│  │                              (已完成/退出)    (结算弹窗)          │ │
│  │                                        └────→ SETTLEMENT ─→ IDLE │ │
│  └──────────────────────────────────────────────────────────────────┘ │
└─────────────────────────────┬─────────────────────────────────────────┘
                              │
          ┌───────────────────┼───────────────────┐
          ▼                   ▼                    ▼
┌─────────────────┐  ┌─────────────────┐  ┌──────────────────────┐
│  ScreenCapture   │  │  SceneDetector   │  │  NinjaAccessibility  │
│  (截图)          │  │  (场景识别)      │  │  Service             │
│                  │  │                  │  │  (手势点击)          │
│  ImageReader     │  │  TemplateMatcher │  │                      │
│  VirtualDisplay  │  │  (OpenCV)        │  │  GestureDescription  │
└─────────────────┘  └─────────────────┘  └──────────────────────┘
                              │
                    ┌─────────▼─────────┐
                    │   PermissionManager│
                    │   (权限生命周期)   │
                    │   · MediaProjection│
                    │   · SharedPref持久 │
                    └───────────────────┘
```

### 2.1 服务层

```
┌──────────────────────────────────────────────────────────────┐
│  FloatingWindowService (前台服务)                              │
│  foregroundServiceType=mediaProjection (Android 12+)          │
│                                                              │
│  · 悬浮球拖拽 + 边缘吸附 + 半隐藏                              │
│  · 菜单展开/收起动画(Alpha + Translate + Overshoot)            │
│  · 脚本控制按钮(play/pause/stop)                               │
│  · 悬赏快捷勾选弹窗(AlertDialog)                                │
│  · 自动隐藏(5s无操作 → 侧边半隐藏)                             │
└──────────────────────────────────────────────────────────────┘
```

---

## 3. 模块目录与职责

### 3.1 `model/` — 数据模型

| 文件 | 职责 |
|--|--|
| `BountyGrade.kt` | 悬赏等级枚举(12级)：key/displayName/defaultRuns/level/priority/isEvent |
| `BountyConfig.kt` | 用户对某等级的配置：enabled/targetRuns/completedRuns |
| `ScreenState.kt` | 游戏界面枚举(19种状态)：与模板配置解耦 |
| `GameContext.kt` | 运行期上下文：currentPhase/activeGrades/runCounts/currentBounty |
| `ActionResult.kt` | 通用操作结果包装 |

### 3.2 `core/` — 核心逻辑

| 文件 | 职责 |
|--|--|
| `GameManager.kt` | 脚本状态机(ScriptState)，日志事件流，协程管理 |
| `WorkflowEngine.kt` | 流水线引擎，6阶段状态机循环编排 |

### 3.3 `core/recognition/` — 场景识别

| 文件 | 职责 |
|--|--|
| `SceneDetector.kt` | 通用场景检测器：Bitmap → ScreenState + 坐标 |
| `TemplateMatcher.kt` | OpenCV 模板匹配核心（单例） |

### 3.4 `core/capture/` — 截图

| 文件 | 职责 |
|--|--|
| `ScreenCapture.kt` | MediaProjection 截图（VirtualDisplay + ImageReader） |
| `CapturePermissionActivity.kt` | 截图授权引导 Activity |

### 3.5 `core/accessibility/` — 无障碍

| 文件 | 职责 |
|--|--|
| `NinjaAccessibilityService.kt` | 手势点击核心（GestureDescription） |

### 3.6 `core/floating/` — 悬浮窗

| 文件 | 职责 |
|--|--|
| `FloatingWindowService.kt` | 前台服务 + 悬浮球 UI + 菜单 + 快捷勾选 |

### 3.7 `core/util/` — 工具

| 文件 | 职责 |
|--|--|
| `PermissionManager.kt` | MediaProjection 权限生命周期 + SharedPreferences 持久化 |
| `OpenCVUtil.kt` | OpenCV 初始化 + Bitmap/Mat 互转 |
| `AssetUtil.kt` | assets 目录图片读取 |
| `LogUtil.kt` | 日志工具 |
| `Constant.kt` | 广播 action 常量 |
| `ToastUtil.kt` | Toast 工具 |
| `AppControl.kt` | 应用/脚本控制辅助 |

### 3.8 `ui/` — UI

| 文件 | 职责 |
|--|--|
| `NinjaScriptMainUI.kt` | 主界面：权限状态/LINK START/悬赏勾选(分栏)/运行日志 |
| `floating/BountyCheckList.kt` | 悬浮窗内悬赏勾选列表 |

---

## 4. 数据模型层

### 4.1 BountyGrade（悬赏等级枚举）

```kotlin
enum class BountyGrade(
    val key: String,         // 唯一键
    val displayName: String, // 显示名：SS+ / S+ / A ...
    val defaultRuns: Int,    // 默认完成次数
    val templateName: String,// 模板文件名
    val priority: Int,       // 排序优先级（数值越小越优先）
    val level: Int           // 游戏内建议等级
)
```

**等级一览：**

| 枚举 | 显示名 | 优先级 | 建议等级 | 类型 | 默认次数 |
|--|--|--|--|--|--|
| SS_PLUS | SS+ | 0 | 125 | 日常 | 1 |
| SS | SS | 1 | 100 | 日常 | 1 |
| S_PLUS | S+ | 2 | 90 | 日常 | 5 |
| S | S | 3 | 90 | 日常 | 5 |
| A_PLUS | A+ | 4 | 80 | 日常 | 3 |
| A | A | 5 | 80 | 日常 | 3 |
| B | B | 6 | 60 | 日常 | 4 |
| C | C | 7 | 40 | 日常 | 5 |
| D | D | 8 | 30 | 日常 | 5 |
| NSS_PLUS | NSS+ | 12 | 125 | 活动 | 1 |
| NS | NS | 13 | 125 | 活动 | 5 |
| NA | NA | 14 | 125 | 活动 | 2 |

**关键方法：**
- `gradeIconPath()` → `templates/bounty/chatbox/{displayName}.png`（招募列表等级图标）
- `levelIconPath()` → `templates/bounty/preparation/lv{level}.png`（队伍房间建议等级图标）
- `isEvent` → NSS+/NS/NA 为活动悬赏

### 4.2 BountyConfig（用户配置）

```kotlin
data class BountyConfig(
    val grade: BountyGrade,
    val enabled: Boolean = false,      // 用户是否勾选
    val targetRuns: Int = grade.defaultRuns,
    val completedRuns: Int = 0
)
```

默认：日常悬赏(priority 0-8)启用，活动悬赏(priority 12-14)禁用。

### 4.3 ScreenState（界面状态枚举）

19种界面状态，覆盖完整工作流：

| 分类 | 状态 | 说明 |
|--|--|--|
| 大厅 | LOBBY, CHAT_ICON | 大厅及聊天入口 |
| 招募 | RECRUIT_TAB, RECRUIT_LIST | 组队招募 |
| 入队 | JOIN_BUTTON, TEAM_ROOM, TEAM_COMPLETED, TEAM_FULL, READY_BUTTON, WAITING_SCREEN, EXIT_CONFIRM | 加入队伍到准备 |
| 战斗 | BATTLE_WARNING, BATTLE_ACTIVE, BOSS_HP_BAR, COUNTDOWN, ULTIMATE_SKILL, WEAPON_SKILL | 战斗中 |
| 结算 | SETTLEMENT_POPUP, CONFIRM_BUTTON | 奖励结算 |
| 通用 | BACK_BUTTON, UNKNOWN | 返回按钮/未知 |

### 4.4 GameContext（运行期上下文）

```kotlin
data class GameContext(
    var currentPhase: GamePhase,             // 当前阶段
    var activeGrades: List<BountyGrade>,      // 待完成等级
    val runCounts: MutableMap<BountyGrade, Int>, // 各等级已完成次数
    var currentBounty: BountyGrade?,          // 当前处理的悬赏
    var totalCycles: Int                      // 总轮次
)
```

---

## 5. 状态管理

### 5.1 GameManager（脚本状态机）

```
ScriptState: IDLE ↔ RUNNING ↔ PAUSED
```

| 方法 | 功能 |
|--|--|
| `startScript(context)` | IDLE → RUNNING，等待 MediaProjection，启动 WorkflowEngine |
| `pauseScript()` | RUNNING → PAUSED，取消协程，暂停 MediaProjection |
| `resumeScript(context)` | PAUSED → RUNNING，恢复 MediaProjection，重启协程 |
| `stopScript()` | 任何状态 → IDLE，取消协程，释放 MediaProjection |

**关键特性：**
- `logEvents: SharedFlow<String>` — 日志事件流，UI 侧 collect 展示
- `postLog(msg)` — 同时输出到 Logcat 和 UI 面板
- `selectedBounties` — 同步 UI 勾选状态
- 自动重试 MediaProjection 初始化（等待 10s）

### 5.2 GamePhase（流水线阶段）

```
IDLE → SCANNING → JOINING → VALIDATING → WAITING → BATTLE → SETTLEMENT → IDLE(循环)
                        ↑         |                   |
                        +—失败——→ IDLE         超时→ IDLE
                        退出队伍                   退出队伍
```

---

## 6. 核心流程引擎

### 6.1 WorkflowEngine

流水线编排器，通过 `GamePhase` 状态机循环调度。

**构造参数：**
- `context: Context`
- `postLog: ((String) -> Unit)?` — 日志回调（传入 GameManager.postLog）

**入口：**
```kotlin
suspend fun runLoop(configs: List<BountyConfig>): Boolean
```

### 6.2 Phase 详解

#### Phase 1: SCANNING — 导航 + 扫描

```
ensureRecruitView()
├─ detectWithCoord() → 判断当前界面
├─ CHAT_ICON → 点击打开聊天面板
├─ RECRUIT_TAB → 点击进入招募列表 → return true
├─ RECRUIT_LIST / JOIN_BUTTON → return true
├─ BACK_BUTTON → 点击返回
└─ UNKNOWN → 延迟重试

phaseNavigateAndScan()
├─ ensureRecruitView() 成功后
├─ matchAnyGrade() 扫描列表（按优先级匹配）
├─ 匹配到等级 → 点击 → JOINING
├─ 离开招募界面 → 重新导航
└─ 20次重试耗尽 → IDLE
```

#### Phase 2: JOINING — 加入队伍

```
├─ detect() 检查是否已在队伍房间
├─ matchTemplate(JOIN_BUTTON) → 点击加入
├─ 检测到 READY_BUTTON / TEAM_ROOM → VALIDATING
├─ TEAM_FULL → 跳过此队
└─ 未找到 JOIN 按钮 → SCANNING
```

#### Phase 3: VALIDATING — 校验 + 准备

```
├─ TEAM_COMPLETED → 退出队伍 → IDLE
├─ TEAM_FULL → 退出队伍 → IDLE
├─ 等级校验(levelIconPath) → 仅警告，不阻断
├─ READY_BUTTON → 点击准备 → WAITING
├─ TEAM_ROOM → 等待
├─ WAITING_SCREEN → WAITING
└─ 不在队伍房间 → SCANNING
```

**注意：** 等级校验从 v3.1 改为宽松模式：不匹配仅记日志，不再退出队伍。

#### Phase 4: WAITING — 等待战斗

```
├─ BATTLE_WARNING / BATTLE_ACTIVE → BATTLE
├─ WAITING_SCREEN → 继续等待
├─ 回到大厅 → IDLE（不计完成）
├─ 超时 35s → exitTeam() → IDLE
└─ UNKNOWN → 继续等待
```

#### Phase 5: BATTLE — 战斗

```
├─ SETTLEMENT_POPUP → SETTLEMENT
├─ CONFIRM_BUTTON → SETTLEMENT
├─ 回到大厅 → IDLE（异常）
├─ ULTIMATE_SKILL 可检测 → 释放技能(3s CD)
└─ 循环检测
```

#### Phase 6: SETTLEMENT — 结算领奖

```
├─ clickOutside() → 点空白处关闭弹窗
├─ 找 CONFIRM_BUTTON → 点击确定
├─ detect() 回到大厅 → 累加完成次数
├─ allCompleted → DONE
└─ IDLE → 开始下一轮
```

### 6.3 辅助方法

| 方法 | 功能 |
|--|--|
| `exitTeam()` | 点返回按钮(最多10次)，检测EXIT_CONFIRM弹窗自动确认 |
| `useSkills(screen)` | 释放大招(ULTIMATE_SKILL检测) |
| `clickOutside()` | 点击屏幕中央偏下(540, 1200)关闭弹窗 |
| `captureBitmap()` | 截图(最多3次重试) |
| `click(coord)` | 无障碍点击指定坐标 |

---

## 7. 场景识别系统

### 7.1 SceneDetector

模板路径与阈值集中管理，ScreenState 枚举不耦合识别配置。

**模板注册表：**

```kotlin
ScreenState.CHAT_ICON      → templates/hall/hall_chat.png        (0.75f)
ScreenState.RECRUIT_TAB    → templates/chat/team_recruit.png     (0.8f)
ScreenState.JOIN_BUTTON    → templates/bounty/chatbox/join_team.png (0.8f)
ScreenState.TEAM_COMPLETED → templates/bounty/chatbox/out_time.png (0.8f)
ScreenState.TEAM_FULL      → templates/bounty/preparation/full.png (0.8f)
ScreenState.READY_BUTTON   → templates/bounty/preparation/prepare.png (0.8f)
ScreenState.BATTLE_WARNING → templates/fight/warning.png         (0.7f)
ScreenState.ULTIMATE_SKILL → templates/fight/ninjutsu.png        (0.6f)
ScreenState.SETTLEMENT_POPUP → templates/bounty/settlement/congratulations.png (0.8f)
ScreenState.CONFIRM_BUTTON → templates/bounty/settlement/confirm.png (0.8f)
ScreenState.EXIT_CONFIRM   → templates/bounty/preparation/confirm.png (0.8f)
ScreenState.BACK_BUTTON    → templates/other/backward.png        (0.8f)
```

**检测方法：**
- `detect(screen)` → ScreenState（按 detectionOrder 优先匹配）
- `detectWithCoord(screen)` → Pair(ScreenState, Coord)
- `matchTemplate(screen, state)` → Coord?
- `matchGradeIcon(screen, grade)` → Coord?
- `matchLevelIcon(screen, grade)` → Coord?（队伍房间内的建议等级标识）
- `matchAnyGrade(screen, grades)` → Pair(BountyGrade, Coord)?
- `matchAnyLevelIcon(screen, grades)` → Pair(BountyGrade, Coord)?

**检测顺序：**
```
CONFIRM_BUTTON → SETTLEMENT_POPUP → BATTLE_WARNING → WAITING_SCREEN
→ READY_BUTTON → TEAM_COMPLETED → TEAM_FULL → EXIT_CONFIRM
→ TEAM_ROOM → JOIN_BUTTON → RECRUIT_TAB → CHAT_ICON → BACK_BUTTON
```

越靠前的状态越具体/稀有，优先匹配。UNKNOWN 兜底。

### 7.2 TemplateMatcher（单例）

OpenCV 模板匹配核心：

```kotlin
fun match(screen: Bitmap, template: Bitmap, threshold: Float): MatchResult
```

- 算法：`Imgproc.TM_CCOEFF_NORMED`（归一化相关系数匹配）
- 返回：是否匹配 + 相似度 + 坐标 + 中心点坐标
- 自动 OpenCV 按需初始化（`System.loadLibrary("opencv_java4")`）

---

## 8. 模板资产清单

### 8.1 assets/ 目录结构

```
assets/templates/
├── hall/
│   ├── hall_chat.png          # 大廳聊天按钮图标 (17KB)
│   ├── hall_main.png          # 大廳全屏截图 (4.1MB, 未使用)
│   ├── bounty_tab.png         # 悬赏tab
│   └── family_tab.png         # 家族tab
├── chat/
│   ├── team_recruit.png       # 组队招募页签 (16KB)
│   └── chat_bar.png           # 聊天栏
├── bounty/
│   ├── chatbox/
│   │   ├── join_team.png      # 加入队伍按钮
│   │   ├── out_time.png       # 已完成/超时标记
│   │   ├── 资深前台.png       # 招募队长头像? 
│   │   ├── SS+.png / SS.png / S+.png / S.png / A+.png / A.png / B.png / C.png / D.png
│   │   └── (缺: NSS+.png, NS.png, NA.png)
│   ├── preparation/
│   │   ├── prepare.png        # 准备按钮
│   │   ├── full.png           # 队伍已满
│   │   ├── lv30.png           # 建议等级30 (D)
│   │   ├── lv40.png           # 建议等级40 (C)
│   │   ├── lv60.png           # 建议等级60 (B)
│   │   ├── lv125.png          # 建议等级125 (SS+ etc.)
│   │   └── confirm.png        # 退出确认弹窗按钮 (用户提供)
│   │   └── (缺: lv80.png, lv90.png, lv100.png)
│   └── settlement/
│       ├── congratulations.png # 结算弹窗
│       ├── confirm.png         # 确定按钮
│       └── blank_space.png     # 空白关闭区域
├── fight/
│   ├── warning.png            # 战斗WARNING
│   ├── ninjutsu.png           # 大招图标
│   ├── jump.png               # 跳跃按钮
│   ├── decline.png            # 拒绝按钮
│   └── scroll_down.png        # 下滑
├── login/
│   ├── tap_to_start.png       # 点击开始
│   └── user_info.png          # 用户信息
└── other/
    ├── backward.png           # 返回按钮
    ├── home.png               # 主页
    └── Snipaste_*.png         # (备用截图)
```

### 8.2 等级→模板映射

| 等级 | 招募列表图标 | 队伍房间等级标识 | 状态 |
|--|--|--|--|
| SS+ | chatbox/SS+.png ✅ | lv125.png ✅ | 就绪 |
| SS | chatbox/SS.png ✅ | lv100.png ❌缺 | 缺等级模板 |
| S+ | chatbox/S+.png ✅ | lv90.png ❌缺 | 缺等级模板 |
| S | chatbox/S.png ✅ | lv90.png ❌缺 | 缺等级模板 |
| A+ | chatbox/A+.png ✅ | lv80.png ❌缺 | 缺等级模板 |
| A | chatbox/A.png ✅ | lv80.png ❌缺 | 缺等级模板 |
| B | chatbox/B.png ✅ | lv60.png ✅ | 就绪 |
| C | chatbox/C.png ✅ | lv40.png ✅ | 就绪 |
| D | chatbox/D.png ✅ | lv30.png ✅ | 就绪 |
| NSS+ | chatbox/NSS+.png ❌缺 | lv125.png ✅ | 缺图标 |
| NS | chatbox/NS.png ❌缺 | lv125.png ✅ | 缺图标 |
| NA | chatbox/NA.png ❌缺 | lv125.png ✅ | 缺图标 |

---

## 9. 权限系统

### 9.1 权限清单

| 权限 | 用途 | 获取方式 |
|--|--|--|
| 无障碍服务 | 模拟点击 | 系统设置 → 无障碍 → NinjaAu |
| 悬浮窗权限 | 前台悬浮球 | Settings.canDrawOverlays |
| MediaProjection | 截取游戏画面 | startActivityForResult → 用户授权 |

### 9.2 PermissionManager

**关键属性：**
- `mResultCode` / `mProjectionIntent` — 授权数据
- `mediaProjection` — MediaProjection 实例（线程安全）

**关键方法：**
- `initMediaProjection(context)` → Boolean（创建 MediaProjection + 注册 onStop 回调）
- `pauseMediaProjection()` — 暂停时保留权限
- `resumeMediaProjection(context)` — 恢复时重新初始化
- `saveProjectionPermission(context)` — 持久化授权数据到 SharedPreferences
- `restoreProjectionPermission(context)` — 进程重启后恢复授权

**持久化机制：**
- `Intent.toUri(Intent.URI_INTENT_SCHEME)` 序列化 Intent
- 在 CapturePermissionActivity 授权成功时保存
- 在 FloatingWindowService.onCreate() 和 GameManager.startScript() 中恢复

---

## 10. UI 层

### 10.1 NinjaScriptMainUI（主界面）

Compose Material3 暗色主题，包含：

| 区域 | 组件 | 说明 |
|--|--|--|
| 顶栏 | TopAppBar | "NinjaAu 忍三自动化" |
| 权限状态 | PermChip × 3 | 无障碍 / 悬浮窗 / 截图 (绿色=已授权) |
| 控制区 | Button + TextButton | LINK START / 暂停 / 继续 / 停止 |
| 悬赏选择 | BountyGradeCard × 12 | 4列网格，分"日常悬赏"和"活动悬赏"两栏 |
| 运行日志 | Text × N | 最近日志(最多200条，显示8条) |

**权限检查流程（onStart）：**
```
无障碍? → ❌ →跳转无障碍设置
悬浮窗? → ❌ →跳转悬浮窗设置
截图?   → ❌ →跳转授权Activity
勾选?   → ❌ →提示"请勾选悬赏"
→ 启动FloatingWindowService + GameManager.startScript()
```

**生命周期：** DisposableEffect + LifecycleEventObserver(ON_RESUME) 自动刷新权限状态

### 10.2 FloatingWindowService（悬浮窗）

前台服务，包含：

| 功能 | 实现 |
|--|--|
| 悬浮球 | WindowManager + 拖拽 + 边缘吸附 + 5s自动半隐藏 |
| 菜单动画 | TranslateAnimation + AlphaAnimation + OvershootInterpolator |
| 控制按钮 | play/pause/stop 图标（根据 ScriptState 切换） |
| 快捷勾选 | AlertDialog + MultiChoice（12等级） |

### 10.3 BountyCheckList（悬浮窗勾选）

悬浮窗内嵌的悬赏勾选列表，同步 GameManager 配置。

---

## 11. 数据流

### 11.1 勾选配置流

```
[NinjaScriptMainUI 勾选]
  └→ bountyConfigs (Compose State)
       └→ GameManager.updateBountyConfigs()
            └→ selectedBounties
                 └→ WorkflowEngine.runLoop(configs)
                      └→ GameContext(activeGrades, runCounts)

[FloatingWindowService 快捷勾选]
  └→ AlertDialog multi-choice
       └→ GameManager.updateBountyConfigs()
```

### 11.2 运行期数据流

```
WorkflowEngine.runLoop()
  │
  ├─→ captureBitmap()
  │     └─→ ScreenCapture.capture() → Bitmap
  │
  ├─→ detector.detect(screen) → ScreenState
  │     └─→ matchTemplate() → TemplateMatcher.match() → Coord?
  │
  ├─→ detector.matchAnyGrade(screen, grades) → (BountyGrade, Coord)?
  │     └─→ TemplateMatcher.match(gradeIcon) → 匹配等级
  │
  ├─→ click(coord)
  │     └─→ NinjaAccessibilityService.clickAt(x, y)
  │           └─→ dispatchGesture(GestureDescription)
  │
  └─→ postLog(msg) → GameManager._logEvents → UI
```

### 11.3 日志流

```
WorkflowEngine.log() → postLog?.invoke(msg)
                             ↓
                    GameManager.postLog(msg)
                             ↓
                    _logEvents.tryEmit(msg)
                             ↓
                    NinjaScriptMainUI collect
                             ↓
                    logLines (Compose State) → UI 展示
```

---

## 12. 异常处理策略

### 12.1 重试机制

| 场景 | 重试次数 | 间隔 |
|--|--|--|
| 截图失败 | 3次 | 300ms |
| MediaProjection 等待 | 20次(10s) | 500ms |
| 导航到招募列表 | 15次 | 800-1500ms |
| 加入队伍 | 10次 | 800ms |
| 校验准备 | 10次 | 800ms |
| 扫描等级 | 20次 | 1500ms |
| 退出队伍 | 10次 | 500-1200ms |

### 12.2 阶段恢复

```
WorkflowEngine recoveryCount ≤ 3:
  catch(Exception) → currentPhase = RECOVERY → delay(1.5s) → IDLE

超出恢复次数 → runLoop 结束 → GameManager state = IDLE
```

### 12.3 特殊处理

- **MediaProjection 被系统回收** → onStop 回调 → 自动释放（保留授权数据，不清空 mResultCode）
- **授权持久化** → 进程重启后从 SharedPreferences 恢复
- **OpenCV 加载失败** → `System.loadLibrary` 异常捕获 → match 返回 false
- **退出确认弹窗** → EXIT_CONFIRM 检测 + 自动点击确认（最多3次兜底）

---

## 13. 已知问题与待办

### 13.1 模板缺失

| 缺失模板 | 影响 | 优先级 |
|--|--|--|
| chatbox/NSS+.png, NS.png, NA.png | N系列活动悬赏无法扫描匹配 | 低(活动很少) |
| preparation/lv80.png | A/A+等级校验无法使用等级匹配 | 中 |
| preparation/lv90.png | S/S+等级校验无法使用等级匹配 | 中 |
| preparation/lv100.png | SS等级校验无法使用等级匹配 | 中 |

### 13.2 待优化项

| 问题 | 说明 |
|--|--|
| 等级图标模板精度 | 当前 `*.png` 可能不匹配实际游戏画面，需要用户确认/替换 |
| 战斗技能单一 | 仅支持大招，无武器技能模板 |
| 无战斗血条/Boss检测 | BOSS_HP_BAR/COUNTDOWN 无模板，技能释放时机不精确 |
| 无重连机制 | 游戏断线/弹窗等异常场景未处理 |
| 无多分辨率适配 | 假定 1080p 屏幕，坐标硬编码 |

### 13.3 gating 检查清单

- [ ] hall_chat.png → 是否准确匹配聊天按钮
- [ ] team_recruit.png → 是否准确匹配招募tab
- [ ] join_team.png → 是否准确匹配加入按钮
- [ ] prepare.png → 是否准确匹配准备按钮
- [ ] lv30/lv40/lv60/lv125 → 是否准确匹配等级标识
- [ ] confirm.png → 是否准确匹配退出确认按钮
- [ ] congratulations.png → 是否准确匹配结算弹窗
- [ ] confirm.png(settlement) → 是否准确匹配确定按钮
- [ ] backward.png → 是否准确匹配返回按钮

---

## 14. 附录：Phase 状态变迁图

```
                      ┌──────────────────────────────┐
                      │         IDLE                  │
                      │  初始状态 / 回到大厅           │
                      └──────────────┬───────────────┘
                                     │ runLoop 开始
                                     ▼
                      ┌──────────────────────────────┐
                      │       SCANNING                │
                      │  ensureRecruitView()          │
                      │  导航到招募列表 + 扫描等级     │
                      └──────┬───────────────┬───────┘
                             │ 匹配到等级     │ 重试耗尽
                             ▼                ▼
                      ┌──────────────┐  ┌──────────────┐
                      │   JOINING    │  │    IDLE      │
                      │  加入队伍     │  │  回大厅重试   │
                      └──────┬───────┘  └──────────────┘
                             │ 进入队伍房间
                             ▼
                      ┌──────────────────────────────┐
                      │       VALIDATING              │
                      │  已完成→退出                   │
                      │  等级校验→仅警告               │
                      │  点击准备                      │
                      └──────┬───────────────┬───────┘
                             │ 准备成功       │ 已完成/退出
                             ▼                ▼
                      ┌──────────────┐  ┌──────────────┐
                      │   WAITING    │  │    IDLE      │
                      │  等待倒计时   │  │  回大厅      │
                      └──────┬───────┘  └──────────────┘
                    ┌────────┤
                    ▼        ▼ 超时
             ┌──────────┐  ┌──────┐
             │  BATTLE  │  │ IDLE │
             │ 战斗     │  │退出队│
             └────┬─────┘  └──────┘
                  │ 结算弹窗
                  ▼
             ┌──────────┐
             │SETTLEMENT│
             │ 领奖     │
             └────┬─────┘
                  │ 回大厅
                  ▼
               [IDLE/S面CANNING 下一轮]
```

---

> 本文档对应架构版本 v3.1
> 最后更新：2026-05-11
