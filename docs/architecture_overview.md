# NinjaAu 项目架构文档

> 最后更新: 2026-06-16
> 版本: v3.0 (节点模式)

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

---

## 1. 项目概述

NinjaAu 是一个 Android 自动化工具，专为手游《忍者必须死3》(Ninja Must Die 3, 忍3) 的悬赏玩法设计。它通过 Android AccessibilityService（模拟手势）+ MediaProjection（屏幕截图）+ OpenCV 模板匹配（图像识别）实现了"导航→扫描→加入→战斗→结算→继续"的完整自动化闭环。

### 核心目标

全自动刷悬赏：用户勾选需要的悬赏等级，启动脚本后自动循环执行：

1. **导航** 从当前界面到招募列表
2. **扫描** 检测悬赏等级图标和加入按钮
3. **加入** 点击加入队伍 → 等级校验 → 点击准备
4. **等待战斗** 检测战斗开始 → 进入战斗
5. **战斗** 释放技能 → 等待结束
6. **结算** 领奖 → 回到大厅 → 继续下一轮
7. 所有等级达到目标次数后自动停止

### 三大业务线

| 业务线 | 说明 | 等级范围 |
|--------|------|---------|
| 每日悬赏 (Daily) | 常规悬赏任务 | SS+, SS, S+, S, A+, A, B, C, D |
| 个人悬赏 (Personal) | 个人悬赏中心 | 复用日常等级列表 |
| 逆袭事件 (NS) | 活动悬赏 | NSS+, NS, NA |

### 使用方式

- 主 UI：Jetpack Compose 页面，勾选等级、启动脚本
- 悬浮窗：前台 Service，显示控制按钮、进度 HUD
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

---

## 3. 模块架构总览

```
com.example.ninjaau/
├── NinjaApp.kt              # Application 入口
├── MainActivity.kt          # 启动 Activity
├── core/
│   ├── GameNode.kt          # 节点接口 + NodeContext + checkNodeTimeout
│   ├── GameManager.kt       # 核心控制器（全局单例）
│   ├── WorkflowEngine.kt    # 自动化流水线引擎（v3 节点模式）
│   ├── accessibility/
│   │   └── NinjaAccessibilityService.kt  # 无障碍模拟点击
│   ├── capture/
│   │   ├── ScreenCapture.kt              # MediaProjection 截图
│   │   └── CapturePermissionActivity.kt  # 权限请求 Activity
│   ├── floating/
│   │   ├── FloatingWindowService.kt      # 悬浮窗前台 Service
│   │   └── HudManager.kt                # HUD 进度覆盖层
│   ├── node/
│   │   ├── LobbyNode.kt                   # 大厅导航
│   │   ├── BountyListNode.kt             # 招募列表扫描
│   │   ├── BountyDetailNode.kt           # 悬赏详情/队伍房间
│   │   ├── BattleLoadingNode.kt          # 战斗加载
│   │   ├── FightNode.kt                  # 战斗
│   │   ├── SettlementNode.kt             # 结算领奖
│   │   ├── RecruitInviteNode.kt          # 招募邀请（TODO 桩）
│   │   ├── DefeatNode.kt                 # 战斗失败（TODO 桩）
│   │   ├── RecoveryNode.kt               # 异常恢复
│   │   ├── PersonalBountyCenterNode.kt   # 个人悬赏中心
│   │   └── PersonalBountyDetailNode.kt   # 个人悬赏详情
│   ├── recognition/
│   │   ├── SceneDetector.kt    # 场景识别（模板缓存+匹配）
│   │   └── TemplateMatcher.kt  # OpenCV 模板匹配引擎
│   ├── config/
│   │   └── ScriptConfigRepository.kt  # 配置持久化仓库
│   └── util/
│       ├── AssetUtil.kt           # assets 文件读取
│       ├── Constant.kt            # 全局常量
│       ├── LogUtil.kt             # 日志工具
│       ├── OpenCVUtil.kt          # OpenCV 初始化 + Mat 转换
│       ├── PermissionManager.kt   # 权限管理（MediaProjection 持久化）
│       └── ToastUtil.kt           # Toast 封装
├── model/
│   ├── BountyConfig.kt       # 悬赏配置数据类
│   ├── BountyGrade.kt        # 悬赏等级枚举（12 等级）
│   ├── GameContext.kt        # 运行时上下文 + GamePhase 枚举（14 阶段）
│   └── ScreenState.kt        # 屏幕状态枚举（33 种）
└── ui/
    ├── NinjaScriptMainUI.kt # Compose 主 UI
    └── theme/
        └── Theme.kt         # NinjaAuTheme
```

### 组件依赖关系

```
NinjaApp (Application)
  └── LogUtil.init()

MainActivity (Launcher)
  └── NinjaScriptMainUI (Compose UI)
        ├── PermissionManager → 检查权限
        ├── ScriptConfigRepository → 配置读写
        └── GameManager → 启动/停止

FloatingWindowService (前台 Service)
  ├── 观察 GameManager 的 Flow
  ├── 悬浮球控制（开始/停止）
  ├── HudManager → HUD 进度覆盖层
  └── Toast 覆盖层（页面事件提示）

GameManager (全局单例)
  └── WorkflowEngine (每次运行创建)
        ├── NodeContext (依赖注入容器)
        ├── 11 个 GameNode 实现
        ├── ScreenCapture → MediaProjection → Bitmap
        ├── SceneDetector → Bitmap → ScreenState + 坐标
        │     ├── TemplateMatcher → OpenCV TM_CCOEFF_NORMED
        │     └── AssetUtil → assets/ 加载模板
        ├── NinjaAccessibilityService → clickAt(x, y)
        └── GameContext + GamePhase → 状态机
```

---

## 4. 模型层详解

### 4.1 BountyGrade — 悬赏等级枚举

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
| `group` | GradeGroup | 等级组 | SS_PLUS, S_GROUP 等 |
| `levelVariants` | List<Int> | 等级图标变体 | SS+: [105,110,115,120,125,130] |

派生属性:
- `isEvent` → NSS+/NS/NA 为活动悬赏，其余为日常
- `canChaseDream` → S, S+, SS+, NSS+, NS, NA 支持追梦模式

方法:
- `gradeIconPath()` → `"templates/bounty_list/{templateName}"` — 招募列表中的等级图标
- `levelIconPath()` → `"templates/team_room/lv{level}.png"` — 队伍房间中的建议等级标识
- `levelIconPaths()` → 所有等级图标变体路径

排序: `sorted()` 按 `priority` 升序

### 4.2 GradeGroup — 等级组

```kotlin
enum class GradeGroup(val defaultRuns: Int) {
    A_GROUP(3), S_GROUP(5), B(4), C(5), D(5),
    SS(1), SS_PLUS(1), NSS_PLUS(1), NS(5), NA(2)
}
```

方法:
- `members()` → 组内所有 BountyGrade
- `totalRuns(runCounts)` → 组内已完成总次数
- `isComplete(runCounts)` → 组是否达到目标

### 4.3 BountyConfig — 悬赏配置数据类

```kotlin
data class BountyConfig(
    val grade: BountyGrade,
    val enabled: Boolean = false,
    val chaseDream: Boolean = false
)
```

### 4.4 GameContext + GamePhase — 运行时上下文

**GamePhase** 枚举（14 个阶段）:

```
IDLE → LOBBY → CHAT → RECRUIT_LIST → RECRUIT_INVITE → BOUNTY_DETAIL
      → BATTLE_LOADING → FIGHT → DEFEAT → SETTLEMENT
      → RECOVERY → DONE
      → PERSONAL_BOUNTY_CENTER → PERSONAL_BOUNTY_DETAIL
```

**GameContext** 关键字段:

| 字段 | 类型 | 说明 |
|------|------|------|
| `currentPhase` | GamePhase | 当前状态机阶段 |
| `activeGrades` | List<BountyGrade> | 仍需完成的等级（动态缩小） |
| `totalGrades` | List<BountyGrade> | 所有勾选等级 |
| `runCounts` | MutableMap<BountyGrade, Int> | 各等级已完成次数 |
| `targetRuns` | Map<BountyGrade, Int> | 各等级目标次数 |
| `currentBounty` | BountyGrade? | 当前正在加入的悬赏 |
| `actualGrade` | BountyGrade? | 实际匹配到的等级（队伍房间 Lv 图标） |
| `chaseDreamGrades` | Set<BountyGrade> | 追梦模式等级集合 |
| `totalCycles` | Int | 总循环计数 |
| `businessLine` | BusinessLine | 当前业务线 (DAILY / PERSONAL) |
| `dailyEnabled` | Boolean | 每日悬赏开关 |
| `nsEnabled` | Boolean | 逆袭事件开关 |
| `personalBountyEnabled` | Boolean | 个人悬赏开关 |
| `personalBountyCompleted` | Boolean | 个人悬赏是否完成 |
| `personalActiveGrades` | List<BountyGrade> | 个人悬赏待完成等级 |
| `personalTargetRuns` | Map<BountyGrade, Int> | 个人悬赏目标次数 |
| `nsActiveGrades` | List<BountyGrade> | 逆袭事件待完成等级 |
| `recoveryAttempt` | Int | 连续恢复尝试次数 |

计算属性: `allCompleted` → 所有非追梦等级是否达到目标

**关键设计**: `activeGrades` 是动态缩小的集合 — 一个等级达到目标次数后即从中移除。

### 4.5 ScreenState — 屏幕状态枚举

**文件**: `model/ScreenState.kt`

共 33 个枚举常量，按游戏区域分类：

| 分类 | ScreenState |
|------|------------|
| 大厅/聊天 | CHAT_ICON, CHAT_TAB |
| 招募 | RECRUIT_TAB, RECRUIT_TAB_BLACK, OUT_OF_RANGE_RECRUIT, RECRUIT_LIST_SCREEN, RECRUIT_INVITE |
| 组队室 | READY_BUTTON, EXIT_CONFIRM, DAILY_LIMIT |
| 战斗加载 | BATTLE_LOADING |
| 战斗 | WARNING, SLIDE_BUTTON, LV_ICON, JUMP_BUTTON, SCROLL_UP, ULTIMATE_SKILL, WEAPON_SKILL, BLOOD_CURSE, DEFEAT_POPUP |
| 结算 | SETTLEMENT_POPUP, CONFIRM_BUTTON |
| 通用 | BACK_BUTTON, UNKNOWN |
| 邀请弹窗 | TEAM_INVITATION, INVITE_REJECT, INVITE_CHECKBOX, INVITE_AGREE |
| 个人悬赏 | PERSONAL_BOUNTY_ENTRY, PERSONAL_BOUNTY_LIST_SCREEN, PERSONAL_BOUNTY_DETAIL_SCREEN, PERSONAL_BOUNTY_SEND_MSG, PERSONAL_BOUNTY_GO |

---

## 5. 核心引擎详解

### 5.1 GameManager — 全局控制器

**文件**: `core/GameManager.kt`
**类型**: `object` 单例
**职责**: 脚本生命周期管理、事件分发、协调 WorkflowEngine

#### 状态管理

| Flow | 类型 | 内容 | 消费者 |
|------|------|------|--------|
| `state` | `StateFlow<ScriptState>` | IDLE / RUNNING | UI 按钮、悬浮窗 |
| `logEvents` | `SharedFlow<String>` | 运行日志行 (buffer 64) | 悬浮窗日志、UI 日志 |
| `bountyProgress` | `StateFlow<Map<BountyGrade, Pair<Int,Int>>>` | 各等级完成/目标次数 | HUD |
| `pageEvents` | `SharedFlow<String>` | 页面跳转事件 (buffer 64) | 悬浮窗 Toast |

**ScriptState**: 仅 `IDLE` 和 `RUNNING`，无 PAUSED 状态。

#### 脚本生命周期

```
toggleScript() → startScript() / stopScript()

startScript():
  1. 检查 MediaProjection 权限（轮询等待最多 10s）
  2. 创建 WorkflowEngine
  3. 启动 runLoop() 协程
  4. state = RUNNING

stopScript():
  1. 取消协程 Job
  2. state = IDLE
```

### 5.2 WorkflowEngine — 自动化流水线 (v3 节点模式)

**文件**: `core/WorkflowEngine.kt`
**类型**: `class`（每次运行创建实例）
**职责**: 节点分发、全局异常拦截、业务线切换

#### 常量配置

| 常量 | 值 | 说明 |
|------|-----|------|
| `MAX_GLOBAL_FAIL` | 3 | 连续异常上限，达到后停止脚本 |
| `MAX_PHASE_STUCK` | 5 | 同一阶段连续返回次数上限，触发 RECOVERY |

#### 核心循环

```
runLoop():
  buildContext() → GameContext
  while (active && phase != DONE && globalFail < 3):
    handleInvitation()        // 全局邀请拦截
    checkProjectionLost()     // 检查截图权限
    dispatchPhase(phase)      // 路由到对应 GameNode
    checkPhaseStuck()         // 阶段卡死检测
    checkCompletion()         // 完成判定 → switchToNextBusinessLine()
```

#### 节点分发 (dispatchPhase)

```kotlin
when (phase) {
    IDLE, LOBBY, CHAT -> hallNode.execute(ctx)
    RECRUIT_LIST -> bountyListNode.execute(ctx)
    RECRUIT_INVITE -> recruitInviteNode.execute(ctx)
    BOUNTY_DETAIL -> bountyDetailNode.execute(ctx)
    BATTLE_LOADING -> battleLoadingNode.execute(ctx)
    FIGHT -> battleNode.execute(ctx)
    DEFEAT -> defeatNode.execute(ctx)
    SETTLEMENT -> settlementNode.execute(ctx)
    RECOVERY -> recoveryNode.execute(ctx)
    PERSONAL_BOUNTY_CENTER -> personalBountyCenterNode.execute(ctx)
    PERSONAL_BOUNTY_DETAIL -> personalBountyDetailNode.execute(ctx)
    DONE -> null
}
```

#### 全局邀请拦截 (handleInvitation)

在每个循环迭代开始前执行，独立于当前节点：
- 检测 `TEAM_INVITATION` → 点击 `INVITE_REJECT` 拒绝
- 通过 `ScriptConfigRepository.inviteCheckEnabled` 控制开关

#### 阶段卡死检测

同一 `GamePhase` 连续返回 5 次（`MAX_PHASE_STUCK`）→ 强制切换到 `RECOVERY`。

#### 业务线切换 (switchToNextBusinessLine)

```
每日悬赏完成 → 检查个人悬赏（已启用且有等级）→ 个人悬赏
个人悬赏完成 → 检查逆袭事件（已启用且有等级）→ 逆袭事件
逆袭事件完成 → DONE
```

#### 异常处理

- 每个节点执行包裹在 try-catch 中
- 异常 → `globalFailCount++` → 强制 RECOVERY
- `globalFailCount >= 3` → 写崩溃日志到 `filesDir/crash_logs/` → 停止脚本

---

## 6. 识别模块详解

### 6.1 SceneDetector — 场景识别层

**文件**: `core/recognition/SceneDetector.kt`
**类型**: `class`
**职责**: 将 Bitmap 映射为 ScreenState + 点击坐标

#### 模板映射

`templates: Map<ScreenState, TemplateEntry>` 包含 28 个状态→路径映射，阈值范围 0.5~0.92。

#### API 方法

| 方法 | 说明 |
|------|------|
| `matchTemplate(screen, state)` | 单状态匹配，返回坐标或 null |
| `matchTemplateMat(screenMat, state)` | Mat 版本，避免重复转换 |
| `matchAnyGradeMat(screenMat, grades)` | 多等级图标匹配 |
| `matchLevelIcon(screen, grade)` | 建议等级图标匹配（多变体最佳匹配） |
| `matchAnyLevelIcon(screen, grades, topFraction)` | 多等级建议等级匹配 |
| `matchAnyGradeIcon(screen, grades)` | 等级字母图标匹配（S, A+ 等） |
| `screenToMat(screen)` | Bitmap → Mat 转换 |
| `testNodeTemplates(screen, group)` | 调试用：测试某节点的所有模板 |

#### 缓存机制

四个 `ConcurrentHashMap` 缓存（线程安全）：

| 缓存 | Key | Value |
|------|-----|-------|
| `templateCache` | ScreenState | Bitmap |
| `gradeIconCache` | BountyGrade | Bitmap |
| `gradeIconMatCache` | BountyGrade | Mat |
| `levelIconCache` | BountyGrade | List<Bitmap> |

#### 裁剪工具方法

12+ 个裁剪方法用于 ROI 加速：`cropBottomLeft`, `cropLeftThird`, `cropTopTenth` 等。

#### NodeTemplateGroup

按节点分组模板，用于调试测试：HALL, RECRUIT_LIST, BOUNTY_DETAIL, BATTLE_LOADING, FIGHT, SETTLEMENT, DEFEAT, RECRUIT_INVITE, INVITATION, PERSONAL_BOUNTY_ENTRY, PERSONAL_BOUNTY_LIST, PERSONAL_BOUNTY_DETAIL。

### 6.2 TemplateMatcher — OpenCV 匹配引擎

**文件**: `core/recognition/TemplateMatcher.kt`
**类型**: `object` 单例

**算法**: OpenCV `Imgproc.TM_CCOEFF_NORMED`（归一化相关系数匹配），值域 [0, 1]。

**流程**: Bitmap → RGBA Mat → BGR Mat → `matchTemplate()` → `Core.minMaxLoc()` → 获取最高分和坐标 → 自动释放所有 Mat。

---

## 7. 截图模块详解

### ScreenCapture — 截图捕获器

**文件**: `core/capture/ScreenCapture.kt`
**类型**: `class`（单例模式，双重检查锁定）

**核心流程**:
1. `getInstance(context)` → 首次初始化 VirtualDisplay + ImageReader
2. `capture()` → `ImageReader.acquireLatestImage()` → `Image` → 计算 `rowPadding` → `Bitmap`
3. 截图前自动检查 MediaProjection 状态，必要时重试初始化

**VirtualDisplay 配置**: 宽度 = 屏幕宽度，高度 = 屏幕高度，DPI = 屏幕 DPI

### CapturePermissionActivity

**文件**: `core/capture/CapturePermissionActivity.kt`
**职责**: 通过系统对话框请求 MediaProjection 截图授权

---

## 8. 无障碍模块详解

### NinjaAccessibilityService

**文件**: `core/accessibility/NinjaAccessibilityService.kt`
**类型**: `AccessibilityService`（单例）

**核心能力**: `clickAt(x: Float, y: Float): Boolean`

使用 `GestureDescription.Builder` 创建点击手势（100ms 持续时间），通过 `dispatchGesture()` 发送。

**设计特点**:
- `onAccessibilityEvent()` 故意留空
- 服务配置限制仅对游戏包名 `com.pandadagames.ninja.global` 生效

---

## 9. 悬浮窗模块详解

### FloatingWindowService

**文件**: `core/floating/FloatingWindowService.kt`
**类型**: 前台 `Service`
**职责**: 悬浮球控制 + 双菜单布局 + 日志面板 + Toast 页面提示

#### 窗口架构

| 层 | 类型 | 交互 | 内容 |
|----|------|------|------|
| 悬浮球 | `TYPE_APPLICATION_OVERLAY` | 可触摸 | 80dp 圆形球 + 双侧菜单 |
| HUD | `TYPE_APPLICATION_OVERLAY` + `NOT_TOUCHABLE` | 点击穿透 | 进度覆盖层（由 HudManager 管理） |
| Toast | `TYPE_APPLICATION_OVERLAY` + `NOT_TOUCHABLE` | 点击穿透 | 页面跳转提示 |

#### 悬浮球交互

- **拖拽**: 直接操作 `LayoutParams.x/y`，带 Y 轴钳制
- **吸附**: 松手后 `ValueAnimator` 平滑过渡到最近边缘 (250ms)
- **双菜单**: 水平 LinearLayout `[ll_menu_left | fl_main_ball | ll_menu_right]`
  - 球在右侧 → 菜单向左展开；球在左侧 → 菜单向右展开
  - 展开动画：菜单项依次滑入，每项延迟 60ms
- **贴边隐藏**: 5 秒无操作，自动贴边只露出 30px 小标签
  - 第一次点击：恢复球到完整可见位置
  - 第二次点击：展开菜单
- **收起交互**: 点击菜单项或球收回菜单，根视图平滑过渡

### HudManager — HUD 进度覆盖层

**文件**: `core/floating/HudManager.kt`
**类型**: `class`

独立的 HUD 管理器，从 FloatingWindowService 提取：

| 属性 | 值 | 说明 |
|------|-----|------|
| 位置 | 右上角固定 | Gravity.TOP \| Gravity.END |
| 背景 | 半透明深色圆角矩形 | 0xDD1E1E1E |
| 标题 | "◆ 进度" | 蓝色粗体 |
| 最大宽度 | 屏幕 36% | 防止遮挡过多 |
| 透明度 | 0.9 | 显示/隐藏带渐变动画 |

进度显示格式：
```
◆ 进度
SS+  0/1
S    3/5 ✓
A    2/3
B    0/4
```

等级显示顺序：SS+ → SS → S → A → B → C → D → NSS+ → NS → NA

同组共享计数：S 和 S+ 共享 S_GROUP 的进度。

---

## 10. 工具库说明

| 工具 | 文件 | 职责 |
|------|------|------|
| `LogUtil` | `util/LogUtil.kt` | Logcat 封装，统一标签 |
| `Constant` | `util/Constant.kt` | 全局常量（包名、广播 Action 等） |
| `AssetUtil` | `util/AssetUtil.kt` | assets → Bitmap 加载 |
| `ToastUtil` | `util/ToastUtil.kt` | Android Toast 便捷封装 |
| `OpenCVUtil` | `util/OpenCVUtil.kt` | OpenCV 初始化 + Bitmap ↔ Mat 转换 |
| `PermissionManager` | `util/PermissionManager.kt` | 权限状态管理 + MediaProjection 持久化 |

### PermissionManager 关键设计

- MediaProjection token 通过 SharedPreferences 持久化
- 进程重启后 FloatingWindowService.onCreate() 尝试恢复授权
- 线程安全: 所有方法通过 `synchronized(lock)` 保护

---

## 11. UI 层说明

### NinjaScriptMainUI — Compose 主页面

主要 Compose 页面，包含：

- **权限状态芯片**: 无障碍服务、悬浮窗权限、截图权限
- **启动/停止按钮**: "Link Start" / "停止"
- **业务线开关**: 每日悬赏、个人悬赏、逆袭事件
- **悬赏等级网格**: 按等级组排列的复选框
- **运行日志面板**: 历史日志，自动滚动

### Theme

标准的 Material3 深色主题。

---

## 12. 核心流程图

### 12.1 主循环流程图

```
                    ┌─────────────────────────────────────────────┐
                    │              runLoop() 主循环                │
                    │  while(active && phase!=DONE && fail<3)     │
                    └─────────────────────────────────────────────┘
                                      │
                    ┌─────────────────┼─────────────────┐
                    │                 │                 │
                    ▼                 ▼                 ▼
            ┌──────────────┐  ┌──────────────┐  ┌──────────────┐
            │  LobbyNode   │  │ BountyDetail │  │   FightNode  │
            │  (IDLE/LOBBY │  │ Node         │  │              │
            │   /CHAT)     │  │ (BOUNTY_     │  │  释放技能    │
            │              │  │  DETAIL)     │  │  检测结算    │
            │  导航到招募   │  │ 准备/组队    │  │              │
            └───────┬──────┘  └──────┬───────┘  └──────┬───────┘
                    │               │                  │
                    ▼               ▼                  ▼
            ┌──────────────┐  ┌──────────────┐  ┌──────────────┐
            │ BountyList   │  │ BattleLoading│  │ SettlementNode│
            │ Node         │  │ Node         │  │              │
            │ (RECRUIT_    │  │ (BATTLE_     │  │  领奖+计数   │
            │  LIST)       │  │  LOADING)    │  │  更新进度    │
            │ 扫描等级图标  │  │ 等待加载完成  │  │              │
            └──────────────┘  └──────────────┘  └──────┬───────┘
                                                       │
                    ┌──────────────────────────────────┤
                    │                                  │
                    ▼                                  ▼
            ┌──────────────┐                   ┌──────────────┐
            │  RECOVERY    │                   │  DONE        │
            │  异常恢复     │                   │  全部完成     │
            │  路由到正确   │                   │  退出循环     │
            │  节点         │                   │              │
            └──────────────┘                   └──────────────┘
```

### 12.2 节点分发流程

```
dispatchPhase(currentPhase):
  IDLE/LOBBY/CHAT → LobbyNode → 导航到招募列表/个人悬赏
  RECRUIT_LIST → BountyListNode → 扫描匹配等级
  RECRUIT_INVITE → RecruitInviteNode → 处理邀请（TODO）
  BOUNTY_DETAIL → BattleDetailNode → 等级校验/准备
  BATTLE_LOADING → BattleLoadingNode → 等待加载完成
  FIGHT → FightNode → 释放技能/检测结算
  DEFEAT → DefeatNode → 失败处理（TODO）
  SETTLEMENT → SettlementNode → 领奖/更新计数
  RECOVERY → RecoveryNode → 识别页面/路由
  PERSONAL_BOUNTY_CENTER → PersonalBountyCenterNode → 个人悬赏列表
  PERSONAL_BOUNTY_DETAIL → PersonalBountyDetailNode → 个人悬赏详情
```

---

## 13. 数据流架构

### 控制流

```
用户操作 (UI/FloatingWindow)
     │
     ▼
GameManager 单例
     │  startScript() / stopScript()
     │
     ▼
WorkflowEngine (每次运行创建)
     │  runLoop()
     │
     ├─ ScreenCapture.capture() → Bitmap
     ├─ SceneDetector.matchTemplate(screen, state) → 坐标
     ├─ NinjaAccessibilityService.clickAt(x, y) → Unit
     └─ GameNode.execute(GameContext) → next GamePhase
```

### 数据流

```
WorkflowEngine.log() ──→ postLog lambda ──→ GameManager._logEvents ──→ UI / 悬浮窗
WorkflowEngine.emitProgress() ──→ onProgress lambda ──→ GameManager._bountyProgress ──→ HUD
WorkflowEngine.onPageEvent?.invoke() ──→ GameManager._pageEvents ──→ 悬浮窗 Toast

GameManager.state ──→ UI 按钮状态
ScriptConfigRepository.*StateFlow ──→ UI 配置面板
```

### 架构特点

- **单向数据流**: WorkflowEngine → GameManager (通过 lambda) → UI (通过 Flow)
- **节点模式**: 每个 GamePhase 对应一个 GameNode，WorkflowEngine 只负责分发
- **依赖注入**: NodeContext 打包所有依赖（截图、点击、识别、日志），注入到每个节点

---

## 14. 已知问题与改进建议

### 14.1 架构设计问题

| # | 问题 | 等级 | 说明 |
|---|------|------|------|
| 1 | **FloatingWindowService 职责过重** | 高 | 同时管理悬浮球、菜单、日志面板、Toast。HudManager 已提取 |
| 2 | **全局单例滥用** | 中 | GameManager、PermissionManager 等均为 object 单例，测试困难 |
| 3 | **CapturePermissionActivity 使用过时 API** | 中 | `startActivityForResult` 已弃用 |

### 14.2 识别相关问题

| # | 问题 | 等级 | 说明 |
|---|------|------|------|
| 4 | **等级匹配先入为主** | 高 | `matchAnyGrade()` 按优先级顺序匹配，低等级误匹配可能抢占 |
| 5 | **等级图标与加入按钮错位** | 高 | 无验证机制确保匹配到的等级图标和加入按钮属于同一悬赏条目 |
| 6 | **模板阈值不一致** | 中 | 范围 0.5~0.92，部分模板可能漏检或误报 |

### 14.3 可靠性问题

| # | 问题 | 等级 | 说明 |
|---|------|------|------|
| 7 | **点击后无状态验证** | 高 | 仅靠固定延迟等待 UI 稳定，不验证是否到达预期状态 |
| 8 | **globalFailCount 永不重置** | 中 | 3 次分散的瞬态异常就会永久停止脚本 |
| 9 | **截图可能返回 null** | 中 | 未区分帧未就绪 vs 权限失效 |

### 14.4 TODO 桩

| 节点 | 状态 | 说明 |
|------|------|------|
| DefeatNode | TODO 桩 | 直接返回 LOBBY，无失败处理逻辑 |
| RecruitInviteNode | TODO 桩 | 直接返回 RECRUIT_LIST，无邀请处理逻辑 |

---

*本文档基于 v3 节点模式代码库编写。*
