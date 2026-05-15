# 忍三悬赏自动化 — 页面规范与代码实现对照文档

> 版本: v2.3  
> 生成日期: 2026-05-15  
> 说明：本文档严格以 `WorkflowEngine.kt`、`SceneDetector.kt`、`ScreenState.kt`、`GameContext.kt` 的实际代码逻辑为准，逐页逐步骤记录所有动作、条件、分支、异常处理。不包含代码中未实现的理论步骤。

---

## 目录

1. [全局配置参数](#1-全局配置参数)
2. [状态枚举与阶段定义](#2-状态枚举与阶段定义)
3. [主循环（runLoop）](#3-主循环runloop)
4. [导航流程（ensureRecruitView）](#4-导航流程ensurerecruitview)
5. [招募列表扫描循环（phaseNavigateAndScan）](#5-招募列表扫描循环phasenavigateandscan)
6. [队伍房间等待（phaseValidate）](#6-队伍房间等待phasevalidate)
7. [战斗阶段（phaseBattle）](#7-战斗阶段phasebattle)
8. [结算阶段（phaseClaim）](#8-结算阶段phaseclaim)
9. [退出队伍（exitTeam）](#9-退出队伍exitteam)
10. [全局页面定位（detectCurrentPage）](#10-全局页面定位detectcurrentpage)
11. [场景检测器 Scope 定义](#11-场景检测器-scope-定义)
12. [ScreenState 模板映射表](#12-screenstate-模板映射表)
13. [技能释放（useSkills）](#13-技能释放useskills)
14. [点击空白关闭弹窗（clickOutside）](#14-点击空白关闭弹窗clickoutside)
15. [截图与点击](#15-截图与点击)
16. [异常处理层次](#16-异常处理层次)
17. [主流程状态跳转图](#17-主流程状态跳转图)

---

## 1. 全局配置参数

所有常量定义在 `WorkflowEngine.companion object` 中：

| 常量名 | 值 | 代码位置 | 用途 | 使用场景 |
|--------|-----|---------|------|---------|
| `MAX_GLOBAL_FAIL` | 3 | `WorkflowEngine.kt:52` | 主循环整体异常最大次数 | 主循环 catch Exception 计数，达上限写 crash log |
| `NAVIGATE_RETRIES` | 6 | `WorkflowEngine.kt:53` | 导航重试次数 | `ensureRecruitView()` 的 repeat 循环 |
| `POST_CLICK_DELAY` | 1000L | `WorkflowEngine.kt:54` | 点击后通用等待延迟(ms) | 几乎所有点击后的 `delay()` |
| `NORMAL_INTERVAL_MS` | 1000L | `WorkflowEngine.kt:55` | 普通检测周期(ms) | 导航、结算阶段的 `delay()` |
| `FAST_INTERVAL_MS` | 100L | `WorkflowEngine.kt:56` | 快速检测周期(ms) | 扫描循环、等待战斗、战斗中的 `delay()` |
| `LINEAR_MAX_MISS` | 3 | `WorkflowEngine.kt:57` | 结算连续无匹配上限 | `phaseClaim` 中的 `missCount` 阈值 |
| `LINEAR_MAX_LOOP` | 300 | `WorkflowEngine.kt:58` | 结算最大循环数 | `phaseClaim` 的 `while` 条件 |
| `WAIT_BATTLE_TIMEOUT_MS` | 15000L | `WorkflowEngine.kt:59` | 等待战斗超时(ms) | `phaseValidate` 中的超时判断 |

### 二级常量（方法内定义）

| 常量名 | 值 | 所在方法 | 用途 |
|--------|-----|---------|------|
| `refreshCooldown` 初始值 | 10 | `phaseNavigateAndScan` | 超出范围悬赏点击后的冷却周期数（10 × 100ms = 1s） |
| `anomalyCount` 阈值 | 3 | `phaseNavigateAndScan` | 扫描异常连续计数上限 |
| `noMatchCycles` 阈值 | 3 | `phaseNavigateAndScan` | 无等级匹配连续周期数上限（3 × 100ms = 300ms） |
| `skillCooldown` | 2000L | `phaseBattle` | 技能释放冷却(ms) |
| `lastSkillTime` 检测间隔 | 500L | `phaseBattle` | 技能图标检测周期(ms) |
| `missCount` 阈值 | 10 | `phaseBattle` | 战斗无匹配计数上限（10 × 100ms = 1s） |
| `battleFallbackCount` 阈值 | 3 | `phaseBattle` | 战斗全屏判定失败上限 |
| `claimFallbackCount` 阈值 | 3 | `phaseClaim` | 结算全屏判定失败上限 |
| `confirmAttempts` 阈值 | 3 | `exitTeam` | 退出确认弹窗点击上限 |
| `exitTeam` 重试次数 | 10 | `exitTeam` | 退出队伍最大循环次数 |
| `captureBitmap` 重试次数 | 3 | `captureBitmap` | 截图失败重试次数 |
| `captureBitmap` 重试延迟 | 300ms | `captureBitmap` | 截图重试间隔 |
| 未知计数器阈值 | 3 | `ensureRecruitView` | 界面无法识别连续次数上限 |

### ScreenCapture 重试

```kotlin
// WorkflowEngine.kt:704-712
private suspend fun captureBitmap(): Bitmap? {
    repeat(3) {
        val bmp = capture.capture()
        if (bmp != null) return bmp
        delay(300)
    }
    log("⚠ 截图失败")
    return null
}
```

每次截图最多重试 3 次，每次间隔 300ms。全部失败返回 null，调用方自行处理（延迟后继续循环或跳过）。

---

## 2. 状态枚举与阶段定义

### 2.1 GamePhase — 流水线阶段

定义在 `GameContext.kt`：

```kotlin
enum class GamePhase {
    IDLE,          // 初始/空闲
    LOBBY,         // 大厅
    CHAT,          // 聊天界面
    RECRUIT_LIST,  // 招募列表
    TEAM_ROOM,     // 队伍房间
    FIGHT,         // 战斗中
    SETTLEMENT,    // 结算
    RECOVERY,      // 异常恢复
    DONE           // 全部完成
}
```

### 2.2 ScriptState — 脚本状态

定义在 `GameManager.kt`：

```kotlin
enum class ScriptState { IDLE, RUNNING, PAUSED }
```

### 2.3 ScreenState — 屏幕识别状态

定义在 `ScreenState.kt`，共 24 个枚举常量。按游戏区域分组：

| 分组 | 枚举常量 | 实际代码中是否被模板引用 |
|------|---------|----------------------|
| 大厅 | `LOBBY`, `CHAT_ICON` | `LOBBY` 无模板；`CHAT_ICON` 有模板 |
| 聊天/招募 | `RECRUIT_TAB`, `RECRUIT_LIST`, `OUT_OF_RANGE_RECRUIT`, `RECRUIT_INVITE` | 全部有模板 |
| 入队 | `JOIN_BUTTON`, `TEAM_ROOM`, `TEAM_COMPLETED`, `TEAM_FULL`, `READY_BUTTON`, `WAITING_SCREEN`, `EXIT_CONFIRM`, `DAILY_LIMIT` | 全部有模板 |
| 战斗 | `BATTLE_WARNING`, `BATTLE_ACTIVE`, `ULTIMATE_SKILL`, `WEAPON_SKILL`, `DEFEAT_POPUP` | `BATTLE_ACTIVE` 无模板 |
| 结算 | `SETTLEMENT_POPUP`, `CONFIRM_BUTTON` | 全部有模板 |
| 通用 | `BACK_BUTTON`, `CHAT_TAB` | 全部有模板 |
| 兜底 | `UNKNOWN` | 无模板，表示无任何状态匹配 |

**注意**: `LOBBY` 和 `BATTLE_ACTIVE` 虽然定义在枚举中，但在 `SceneDetector.templates` 映射中没有对应的模板文件和阈值，实际为死代码。`LOBBY` 在 `detectCurrentPage()` 映射中也不作为输入出现（仅 `DAILY_LIMIT`, `EXIT_CONFIRM`, `BACK_BUTTON` 映射到 `GamePhase.LOBBY`）。

---

## 3. 主循环（runLoop）

**代码位置**: `WorkflowEngine.kt:66-123`

### 入口

```kotlin
suspend fun runLoop(configs: List<BountyConfig>, onProgress: ((Map<BountyGrade, Pair<Int, Int>>) -> Unit)? = null): Boolean
```

- `configs`: 用户勾选的悬赏配置列表（含 grade + enabled + targetRuns）
- `onProgress`: 进度回调，每次阶段跳转后调用
- 返回值: `true` = 所有悬赏完成，`false` = 异常终止（或从上下文看实际表示 `allCompleted`）

### GameContext 初始化

```kotlin
val enabled = configs.filter { it.enabled }
GameContext(
    currentPhase = GamePhase.IDLE,
    activeGrades = enabled.map { it.grade },     // 已勾选的等级列表
    runCounts = enabled.associate { it.grade to 0 }.toMutableMap(),  // 各等级已完成次数
    targetRuns = enabled.associate { it.grade to it.targetRuns }     // 各等级目标次数
)
```

### 循环体

```kotlin
while (coroutineContext.isActive && ctx.currentPhase != DONE && globalFailCount < MAX_GLOBAL_FAIL)
```

循环条件：
1. 协程未被取消
2. 阶段未达到 DONE
3. 全局失败次数 < 3

### 阶段分派

| `currentPhase` 值 | 调用的方法 | 说明 |
|------------------|-----------|------|
| `IDLE`, `LOBBY`, `CHAT`, `RECRUIT_LIST` | `phaseNavigateAndScan(ctx)` | 导航到招募列表 → 扫描 → 加入/准备 |
| `TEAM_ROOM` | `phaseValidate(ctx)` | 等待战斗开始 |
| `FIGHT` | `phaseBattle(ctx)` | 战斗中释放技能，等待结算 |
| `SETTLEMENT` | `phaseClaim(ctx)` | 结算领奖，更新计数 |
| `RECOVERY` | `delay(1500); return IDLE` | 异常后恢复，1.5s 后回到 IDLE |
| `DONE` | `return DONE` | 退出循环 |

### 异常处理

```kotlin
catch (e: CancellationException) -> throw e  // 暂停/停止时不吞异常
catch (e: Exception) ->
    globalFailCount++
    currentPhase = RECOVERY  // 下次循环进入 RECOVERY 分支
```

- 所有非 CancellationException 的异常被捕获
- `globalFailCount` 递增
- 阶段设为 `RECOVERY`（1.5s 后回到 IDLE 重试）
- 若 `globalFailCount ≥ 3` → 循环退出后调用 `writeCrashLog(ctx)`，记录崩溃日志到 `filesDir/crash_logs/crash_yyyyMMdd_HHmmss.log`

### 阶段跳转后动作

每次阶段跳转后执行：
1. `ctx.currentPhase = next`
2. `emitProgress(ctx, onProgress)` — 发射进度数据
3. `onPageEvent?.invoke(phaseToEvent)` — 发射页面跳转事件（用于 Toast）

### 页面跳转事件映射

| 跳转到的阶段 | 事件文本 |
|-------------|---------|
| `LOBBY`, `IDLE` | "进入大厅" |
| `RECRUIT_LIST` | "进入招募列表" |
| `TEAM_ROOM` | "队伍房间准备就绪" |
| `FIGHT` | "⚔ 战斗开始" |
| `SETTLEMENT` | "结算领奖" |
| `DONE` | "🎉 全部悬赏完成" |

---

## 4. 导航流程（ensureRecruitView）

**代码位置**: `WorkflowEngine.kt:548-612`  
**识别周期**: `NORMAL_INTERVAL_MS = 1000ms`  
**最大重试**: `NAVIGATE_RETRIES = 6` 次  
**返回值**: `GamePhase?` — `null` 表示导航成功到达招募列表；非 `null` 表示检测到的当前页面，外层据此跳转

### 检测 Scope

使用 `SceneDetector.SCOPE_NAVIGATE`（10 个状态，按以下优先级检测）：

```
CONFIRM_BUTTON → SETTLEMENT_POPUP → DAILY_LIMIT → DEFEAT_POPUP → CHAT_ICON
→ RECRUIT_TAB → RECRUIT_LIST → RECRUIT_INVITE → JOIN_BUTTON → BACK_BUTTON
```

### 状态处理逻辑

```
for each state in SCOPE_NAVIGATE:
    if matchTemplate(screen, state) → 命中
    
    UNKNOWN:
        unknownCount++
        if unknownCount < 3: delay(1s) → 继续重试
        if unknownCount >= 3:
            detectCurrentPage(screen)
            if 成功 → return detectedPhase (外层跳转)
            if 失败 → throw RuntimeException("无法识别当前页面") → 全局异常

    CHAT_ICON:
        点击 CHAT_ICON 坐标
        delay(POST_CLICK_DELAY = 1000ms)
        → 下一轮检测 RECRUIT_TAB

    RECRUIT_TAB:
        点击 RECRUIT_TAB 坐标
        delay(1000ms)
        → return null (导航成功)

    RECRUIT_LIST / JOIN_BUTTON / RECRUIT_INVITE:
        log("已在招募列表")
        → return null (导航成功)

    SETTLEMENT_POPUP / CONFIRM_BUTTON:
        点击 模板坐标
        delay(POST_CLICK_DELAY = 1000ms)
        → 继续下一轮检测

    DAILY_LIMIT / DEFEAT_POPUP:
        clickOutside(screen)
        delay(POST_CLICK_DELAY = 1000ms)
        → 继续下一轮检测

    BACK_BUTTON:
        点击 BACK_BUTTON 坐标
        delay(800ms)
        → 继续下一轮检测

    else (其他状态):
        delay(NORMAL_INTERVAL_MS = 1000ms)
        → 继续下一轮检测
```

### 重试耗尽

6 次全部用完 → `throw RuntimeException("导航重试耗尽")` → 进入全局异常处理

### 导航流程总结

```
从任意界面开始
repeat ≤ 6:
    SCOPE_NAVIGATE 检测
    ├─ UNKNOWN × 3 → detectCurrentPage → success(跳转) / fail(STOP)
    ├─ CHAT_ICON → 点击 → 等1s
    ├─ RECRUIT_TAB → 点击 → 等1s → return null ✓
    ├─ RECRUIT_LIST/JOIN_BUTTON/RECRUIT_INVITE → return null ✓
    ├─ 弹窗状态 → 关闭 → 继续
    ├─ BACK_BUTTON → 返回 → 继续
    └─ 其他 → delay(1s) → 继续
6次用完 → throw RuntimeException ✗
```

---

## 5. 招募列表扫描循环（phaseNavigateAndScan）

**代码位置**: `WorkflowEngine.kt:129-298`  
**识别周期**: `FAST_INTERVAL_MS = 100ms`  
**核心职责**: 在招募列表中高频扫描 → 匹配等级 → 点击加入 → 校验加入结果

### 前置操作

```kotlin
val remaining = remainingGrades(ctx)  // 过滤已完成等级
if (remaining.isEmpty()) return DONE  // 全部完成直接返回

val detectedPhase = ensureRecruitView()  // 先导航到招募列表
if (detectedPhase != null) return detectedPhase  // 导航失败跳转
```

`remainingGrades(ctx)` 逻辑：
```kotlin
ctx.activeGrades.filter { g -> (runCounts[g] ?: 0) < (targetRuns[g] ?: g.defaultRuns) }
```

### 循环前初始化

```kotlin
var noMatchCycles = 0     // 连续无匹配周期数
var refreshCooldown = 0   // 刷新冷却计数
var anomalyCount = 0       // 连续异常计数
ctx.currentBounty = null   // 清空当前悬赏
```

### 主循环 - 三个检测块

#### Block ① 刷新检测（非校验模式时执行）

```kotlin
if (ctx.currentBounty == null) {
    // 1a. 超出范围悬赏刷新（带冷却）
    if (refreshCooldown <= 0) {
        val rangeCoord = matchTemplate(screen, OUT_OF_RANGE_RECRUIT)
        if (rangeCoord != null) {
            click(rangeCoord)                    // 点击"超出范围"的悬赏位置
            noMatchCycles = 0
            refreshCooldown = 10                 // 冷却10周期(1s)
            continue
        }
    }
    // 1b. 列表过期 → 点击邀请标识
    val inviteCoord = matchTemplate(screen, RECRUIT_INVITE)
    if (inviteCoord != null) {
        click(inviteCoord)                       // 点击邀请标识
        delay(POST_CLICK_DELAY = 1000ms)          // 等1s列表刷新
        noMatchCycles = 0
        continue
    }
}
if (refreshCooldown > 0) refreshCooldown--       // 冷却递减
```

**关键点**:
- Block ① 仅在 `currentBounty == null`（非校验模式）执行
- `OUT_OF_RANGE_RECRUIT` 有 10 周期冷却（1s）防无限刷新
- `RECRUIT_INVITE` 无冷却，每次循环都检测
- 两个刷新操作均重置 `noMatchCycles`

#### Block ② 校验模式（currentBounty != null）

进入条件：`ctx.currentBounty != null`（已点击加入按钮，等待进入队伍房间）

```
检测 DAILY_LIMIT:
    → exitTeam() → 清除 currentBounty → continue

检测 SCOPE_RECRUIT (RECRUIT_LIST / RECRUIT_INVITE / JOIN_BUTTON / RECRUIT_TAB / CHAT_ICON):
    若匹配到任意状态:
    → log("加入失败，仍在招募界面")
    → 清除 currentBounty
    → return LOBBY (回到大厅重新导航)

检测 READY_BUTTON:
    ├─ matchAnyLevelIcon(activeGrades) 匹配成功:
    │     → log("等级匹配 {grade}")
    │     → click(READY_BUTTON)
    │     → delay(1000ms)
    │     → return TEAM_ROOM ✓
    └─ matchAnyLevelIcon(activeGrades) 匹配失败:
          → log("队伍级别不在勾选范围内")
          → exitTeam()
          → 清除 currentBounty
          → continue

其他状态 (SCOPE_RECRUIT 无匹配，READY_BUTTON 无匹配):
    delay(100ms) → continue
```

**关键点**:
- `SCOPE_RECRUIT` 检测优先级：`RECRUIT_LIST > RECRUIT_INVITE > JOIN_BUTTON > RECRUIT_TAB > CHAT_ICON`
- 加入失败检测使用 `detectForPhase(screen, SCOPE_RECRUIT)` 而非单个 `matchTemplate`
- 等级校验使用 `matchAnyLevelIcon`（建议等级图标，如 lv30/lv40/lv60 等），匹配用户勾选的**全部** `activeGrades`（不是仅 `remaining`）
- 匹配成功后点击准备 → 直接 `return TEAM_ROOM`（跳出扫描循环），后续由 `phaseValidate` 处理等待战斗

#### Block ③ 普通扫描模式（currentBounty == null）

```kotlin
val match = detector.matchAnyGrade(screen, remaining)
if (match != null) {
    val (grade, _) = match
    ctx.currentBounty = grade

    // ③a. 找到加入按钮
    val joinCoord = matchTemplate(screen, JOIN_BUTTON)
    if (joinCoord != null) {
        click(joinCoord)
        onPageEvent?.invoke("匹配到 {grade} 悬赏，加入队伍")
        delay(1000ms)
        noMatchCycles = 0
        anomalyCount = 0
        continue  // 下周期进入 Block ② 校验模式
    }

    // ③b. 已在队伍 → 检测准备按钮
    val readyCoord = matchTemplate(screen, READY_BUTTON)
    if (readyCoord != null) {
        val levelMatch = matchAnyLevelIcon(activeGrades)
        if (levelMatch != null) {
            return TEAM_ROOM  // 已在正确队伍
        }
        exitTeam()
        currentBounty = null
        anomalyCount = 0
        continue
    }

    // ③c. 连续异常
    anomalyCount++
    currentBounty = null  // 清除标记
    if (anomalyCount >= 3) {
        detectForPhase(screen, SCOPE_RECRUIT)
        if UNKNOWN → throw RuntimeException("界面无法识别")
        else → return LOBBY
    }
    delay(100ms)
    continue
}

// 无等级匹配 → 检查邀请
noMatchCycles++
if (noMatchCycles >= 3) {  // 300ms 无匹配
    noMatchCycles = 0
    val readyCoord = matchTemplate(screen, READY_BUTTON)
    if (readyCoord != null) {
        val levelMatch = matchAnyLevelIcon(activeGrades)
        if (levelMatch != null) {
            currentBounty = grade  // 被邀请进队
            continue  // 下周期进入校验模式
        }
        exitTeam()
        continue
    }
    // 界面状态检查
    detectForPhase(screen, SCOPE_RECRUIT)
    if (CHAT_ICON || RECRUIT_TAB) {
        return LOBBY  // 离开招募界面了
    }
}
```

**`matchAnyGrade` 匹配逻辑**（`SceneDetector.kt:269-282`）：

```kotlin
fun matchAnyGrade(screen, grades: List<BountyGrade>): Pair<BountyGrade, Pair<Float, Float>>? {
    for (grade in grades) {  // 按 BountyGrade.sorted() 优先级顺序
        val coord = matchGradeIcon(screen, grade) ?: continue
        LogUtil.i(TAG, "matchAnyGrade → 选中 ${grade.displayName}")
        return Pair(grade, coord)
    }
    LogUtil.d(TAG, "matchAnyGrade: ${grades.size}个等级均未匹配")
    return null
}
```

**关键点**:
- `matchAnyGrade` 按 `BountyGrade.sorted()` 优先级顺序逐个尝试（SS+ > SS > S+ > S > A+ > A > B...）
- 匹配到等级后设置 `currentBounty`，**但不再验证 JOIN_BUTTON 是否属于该等级对应的悬赏条目**
- 等级匹配成功但无 JOIN_BUTTON 的分支处理：尝试 READY_BUTTON（已在队伍）→ 3 次异常（清除 currentBounty → 全屏判定）
- `noMatchCycles ≥ 3` 时检查 READY_BUTTON（被邀请场景），不再匹配等级图标

### 循环退出条件

| 条件 | 返回值 | 说明 |
|------|--------|------|
| 点击准备成功 | `TEAM_ROOM` | 进入队伍房间等待战斗 |
| 加入失败（仍在招募界面） | `LOBBY` | 回到大厅重新导航 |
| `SCOPE_RECRUIT` 检测到 CHAT_ICON / RECRUIT_TAB | `LOBBY` | 离开招募界面 |
| 已在队伍中（等级匹配） | `TEAM_ROOM` | 直接进入等待战斗 |
| 3 次异常且全屏判定 UNKNOWN | `throw RuntimeException` | 全局异常处理 |
| `remaining.isEmpty()` | `DONE` | 全部完成 |
| `coroutineContext.isActive == false` | `DONE` | 协程取消（暂停/停止） |

---

## 6. 队伍房间等待（phaseValidate）

**代码位置**: `WorkflowEngine.kt:304-358`  
**识别周期**: `FAST_INTERVAL_MS = 100ms`  
**超时**: `WAIT_BATTLE_TIMEOUT_MS = 15000ms`

### 前置

```kotlin
val targetGrade = ctx.currentBounty ?: return LOBBY
```

如果 `currentBounty == null`（异常路径），直接回到 LOBBY。

### 等待循环

15 秒内每 100ms 检测一次：

```
循环直到 System.currentTimeMillis() - startTime >= 15000:
    
    检测 BATTLE_WARNING:
        → click(BATTLE_WARNING 坐标)
        → delay(1000ms)
        → return FIGHT ✓

    检测 WAITING_SCREEN:
        → continue (继续等待)

    检测 SETTLEMENT_POPUP 或 CONFIRM_BUTTON:
        → log("战斗已结束")
        → return SETTLEMENT ✓

    检测 CHAT_ICON 或 RECRUIT_TAB:
        → log("等待期间回到大厅")
        → return IDLE

    每 5 秒输出一次日志: "等待中... ({秒数}s)"
```

### 超时

```kotlin
log("⚠ 等待战斗超时 15000ms")
exitTeam()
return IDLE
```

**关键点**:
- 超时后调用 `exitTeam()` 退出队伍
- 返回 IDLE，由主循环再次导航
- 本阶段不处理 DAILY_LIMIT、EXIT_CONFIRM 等状态（仅在 SCOPE_EXIT 中出现）

---

## 7. 战斗阶段（phaseBattle）

**代码位置**: `WorkflowEngine.kt:364-428`  
**识别周期**: `FAST_INTERVAL_MS = 100ms`

### 局部变量

```kotlin
var lastSkillTime = 0L      // 上次技能释放时间
val skillCooldown = 2000L   // 技能冷却(ms) - 实际未使用
var missCount = 0           // 连续无匹配计数
var battleFallbackCount = 0 // 全屏判定失败计数
```

### 循环体

```
无限循环 (直到 return):
    
    检测 SETTLEMENT_POPUP:
        → return SETTLEMENT ✓

    检测 CONFIRM_BUTTON:
        → return SETTLEMENT ✓

    检测 DEFEAT_POPUP:
        → return SETTLEMENT ✓

    技能释放（每500ms）:
        if (now - lastSkillTime >= 500) {
            if (useSkills(screen)) lastSkillTime = now
        }

    missCount++
    if (missCount >= 10) {  // 10周期(1s)无任何匹配
        detectForPhase(screen, SCOPE_BATTLE)
        ├─ CHAT_ICON 或 RECRUIT_TAB:
        │     → return IDLE (战斗异常回到大厅)
        ├─ UNKNOWN:
        │     battleFallbackCount++
        │     if (battleFallbackCount < 3):
        │         missCount = 0 → continue
        │     if (battleFallbackCount >= 3):
        │         detectCurrentPage(screen)
        │         ├─ 成功 → return detectedPhase
        │         └─ 失败 → throw RuntimeException("战斗阶段页面无法识别")
        └─ 其他状态 (如 ULTIMATE_SKILL):
              missCount = 0 → continue
    }
```

**关键点**:
- `missCount` 每周期++，达到 10（≈1秒）触发 `SCOPE_BATTLE` 全屏判定
- 技能释放检测周期 500ms（`now - lastSkillTime >= 500`）
- `skillCooldown = 2000L` 已定义但**未在代码中实际使用**（仅用于 `useSkills` 的冷却判断，但 `useSkills` 本身不检查冷却）

---

## 8. 结算阶段（phaseClaim）

**代码位置**: `WorkflowEngine.kt:434-510`  
**识别周期**: `NORMAL_INTERVAL_MS = 1000ms`  
**最大循环**: `LINEAR_MAX_LOOP = 300` 次（300s ≈ 5min）

### 局部变量

```kotlin
val grade = ctx.currentBounty      // 当前完成的悬赏等级
var missCount = 0                   // 连续无匹配计数
var claimFallbackCount = 0          // 全屏判定失败计数
var loopCount = 0                   // 循环计数
```

### 循环体

```
while (active && loopCount < 300):
    loopCount++

    检测 SETTLEMENT_POPUP:
        → clickOutside(screen)
        → delay(800ms)
        → continue

    检测 CONFIRM_BUTTON:
        → click(CONFIRM_BUTTON 坐标)
        → delay(POST_CLICK_DELAY = 1000ms)
        → continue

    检测 CHAT_ICON:
        → log("已回到大厅")
        → break

    missCount++
    if (missCount >= 3):
        detectForPhase(screen, SCOPE_CLAIM)
        ├─ CHAT_ICON 或 RECRUIT_TAB:
        │     → break (回到主页)
        ├─ UNKNOWN:
        │     claimFallbackCount++
        │     if (claimFallbackCount < 3):
        │         missCount = 0 → continue
        │     if (claimFallbackCount >= 3):
        │         detectCurrentPage(screen)
        │         ├─ 成功 → return detectedPhase
        │         └─ 失败 → throw RuntimeException("结算阶段页面无法识别")
        └─ 其他状态 (CONFIRM_BUTTON):
              missCount = 0 → continue
```

### 结算后处理（break 或 300 次循环结束）

```kotlin
if (grade != null) {
    val count = ctx.runCounts[grade] ?: 0
    ctx.runCounts[grade] = count + 1
    ctx.totalCycles++
    // 判断是否达到目标次数
    if ((runCounts[grade] ?: 0) >= (targetRuns[grade] ?: grade.defaultRuns)) {
        ctx.activeGrades = ctx.activeGrades - grade  // 从集合移除
    }
}
ctx.currentBounty = null

if (ctx.activeGrades.isEmpty()) → return DONE
else → return IDLE
```

**关键点**:
- 300 次最大循环对应约 5 分钟，防止结算界面卡死
- 等级完成的判定：`runCounts >= targetRuns`，从 `activeGrades` 移除
- `activeGrades` 为空 → `DONE`
- 结算阶段不区分胜利/失败：`DEFEAT_POPUP` 在战斗阶段（`phaseBattle`）已处理，`phaseClaim` 只关 `SETTLEMENT_POPUP` + `CONFIRM_BUTTON`

---

## 9. 退出队伍（exitTeam）

**代码位置**: `WorkflowEngine.kt:615-648`  
**识别周期**: 500ms（每次截图重试间隔）  
**最大重试**: 10 次  
**无返回值** — 到达大厅后 `return`，否则循环耗尽后静默退出

### 检测 Scope

使用 `SceneDetector.SCOPE_EXIT`（5 个状态）：

```
EXIT_CONFIRM → DAILY_LIMIT → CHAT_ICON → RECRUIT_TAB → BACK_BUTTON
```

### 循环体

```
repeat (10):
    
    截图失败 → delay(500ms) → 继续

    detectForPhase(screen, SCOPE_EXIT)
    
    if CHAT_ICON 或 RECRUIT_TAB:
        → log("已回到大厅")
        → return ✓

    if EXIT_CONFIRM:
        matchTemplate(screen, EXIT_CONFIRM)
        if 成功:
            click(EXIT_CONFIRM)
            delay(1000ms)
            confirmAttempts++
            if (confirmAttempts > 3):
                clickOutside()    // 兜底，可能弹窗位置变了
                delay(800ms)
            → 继续下一轮

    matchTemplate(screen, BACK_BUTTON)
    if 成功:
        click(BACK_BUTTON)
        delay(1200ms)
        → 继续下一轮

    else:
        if (state == UNKNOWN):
            clickOutside()       // 兜底点击空白
            delay(800ms)
        else:
            delay(500ms)
```

**关键点**:
- 最多 10 次重试，到达大厅即返回
- `confirmAttempts > 3` 的兜底：点击屏幕空白（可能弹窗位置移动或按钮被遮挡）
- 10 次用完：**不抛异常**，静默结束。调用方需自行处理（如继续扫描循环发现还在队伍房间）

---

## 10. 全局页面定位（detectCurrentPage）

**代码位置**: `WorkflowEngine.kt:533-545`  
**调用场景**:
- `ensureRecruitView()` 中连续 3 次 UNKNOWN 后
- `phaseBattle()` 中 `battleFallbackCount ≥ 3` 后
- `phaseClaim()` 中 `claimFallbackCount ≥ 3` 后

### 实现

```kotlin
suspend fun detectCurrentPage(screen: Bitmap): GamePhase? {
    val (state, _) = detector.detectWithCoord(screen)  // 使用 detectionOrder 全量扫描
    if (state == UNKNOWN) return null
    return when (state) {
        CHAT_ICON, RECRUIT_TAB → IDLE
        RECRUIT_LIST, JOIN_BUTTON, RECRUIT_INVITE → RECRUIT_LIST
        READY_BUTTON, TEAM_ROOM, WAITING_SCREEN → TEAM_ROOM
        BATTLE_WARNING, ULTIMATE_SKILL, WEAPON_SKILL, DEFEAT_POPUP → FIGHT
        SETTLEMENT_POPUP, CONFIRM_BUTTON → SETTLEMENT
        DAILY_LIMIT, EXIT_CONFIRM, BACK_BUTTON → LOBBY
        else → null
    }
}
```

### detectionOrder（全量扫描顺序）

定义在 `SceneDetector.kt:278-297`：

```
CONFIRM_BUTTON → SETTLEMENT_POPUP → DEFEAT_POPUP → BATTLE_WARNING
→ WAITING_SCREEN → READY_BUTTON → DAILY_LIMIT → TEAM_COMPLETED
→ TEAM_FULL → EXIT_CONFIRM → TEAM_ROOM → JOIN_BUTTON
→ RECRUIT_INVITE → RECRUIT_TAB → OUT_OF_RANGE_RECRUIT → CHAT_ICON
→ BACK_BUTTON → CHAT_TAB
```

### 状态→阶段映射表

| 匹配到的 ScreenState | 返回的 GamePhase | 说明 |
|---------------------|-----------------|------|
| `CHAT_ICON` | `IDLE` | 大厅/主页 |
| `RECRUIT_TAB` | `IDLE` | 大厅（含页签） |
| `RECRUIT_LIST` | `RECRUIT_LIST` | 招募列表中 |
| `JOIN_BUTTON` | `RECRUIT_LIST` | 招募列表含加入按钮 |
| `RECRUIT_INVITE` | `RECRUIT_LIST` | 招募列表过期 |
| `READY_BUTTON` | `TEAM_ROOM` | 队伍房间已准备 |
| `TEAM_ROOM` | `TEAM_ROOM` | 队伍房间 |
| `WAITING_SCREEN` | `TEAM_ROOM` | 等待倒计时 |
| `BATTLE_WARNING` | `FIGHT` | 即将开始（下滑警告） |
| `ULTIMATE_SKILL` | `FIGHT` | 战斗中 | 
| `WEAPON_SKILL` | `FIGHT` | 战斗中 |
| `DEFEAT_POPUP` | `FIGHT` | 战斗失败（仍算战斗阶段） |
| `SETTLEMENT_POPUP` | `SETTLEMENT` | 结算弹窗 |
| `CONFIRM_BUTTON` | `SETTLEMENT` | 确定按钮 |
| `DAILY_LIMIT` | `LOBBY` | 已达上限弹窗（需退出） |
| `EXIT_CONFIRM` | `LOBBY` | 退出确认弹窗 |
| `BACK_BUTTON` | `LOBBY` | 返回按钮 |
| `UNKNOWN` / 其他 | `null` | 无法定位 |

---

## 11. 场景检测器 Scope 定义

所有 Scope 定义在 `SceneDetector.companion object`：

### SCOPE_NAVIGATE — 导航（节点 1~2）

10 个状态，检测优先级顺序：

```
CONFIRM_BUTTON → SETTLEMENT_POPUP → DAILY_LIMIT → DEFEAT_POPUP
→ CHAT_ICON → RECRUIT_TAB → RECRUIT_LIST → RECRUIT_INVITE
→ JOIN_BUTTON → BACK_BUTTON
```

### SCOPE_RECRUIT — 招募列表（节点 3）

5 个状态，用于校验模式中检测是否仍在招募界面：

```
RECRUIT_LIST → RECRUIT_INVITE → JOIN_BUTTON → RECRUIT_TAB → CHAT_ICON
```

### SCOPE_TEAM_ROOM — 队伍房间（节点 4）

7 个状态（当前 `phaseValidate` 中未直接使用此 scope）：

```
READY_BUTTON → TEAM_ROOM → TEAM_COMPLETED → TEAM_FULL
→ WAITING_SCREEN → DAILY_LIMIT → EXIT_CONFIRM
```

### SCOPE_WAIT_BATTLE — 等待战斗（节点 4 子集）

6 个状态（当前 `phaseValidate` 中未直接使用此 scope，但检测了部分状态）：

```
BATTLE_WARNING → WAITING_SCREEN → ULTIMATE_SKILL
→ SETTLEMENT_POPUP → CHAT_ICON → RECRUIT_TAB
```

### SCOPE_BATTLE — 战斗中（节点 5）

6 个状态，用于 `phaseBattle` 的 missCount ≥ 10 时全屏判定：

```
SETTLEMENT_POPUP → CONFIRM_BUTTON → DEFEAT_POPUP
→ CHAT_ICON → RECRUIT_TAB → ULTIMATE_SKILL
```

### SCOPE_CLAIM — 结算（节点 6）

3 个状态，用于 `phaseClaim`：

```
CONFIRM_BUTTON → CHAT_ICON → RECRUIT_TAB
```

### SCOPE_EXIT — 退出

5 个状态，用于 `exitTeam`：

```
EXIT_CONFIRM → DAILY_LIMIT → CHAT_ICON → RECRUIT_TAB → BACK_BUTTON
```

### SCOPE_ALL — 全量

`ScreenState.values().toList()`，包含所有 24 个枚举值。

---

## 12. ScreenState 模板映射表

定义在 `SceneDetector.kt` 的 `templates: Map<ScreenState, TemplateEntry>`：

| ScreenState | 模板路径 | 阈值 | 游戏内说明 |
|-------------|---------|------|-----------|
| `CHAT_ICON` | `templates/lobby/hall_chat.png` | 0.75 | 大厅右下聊天图标 |
| `RECRUIT_TAB` | `templates/chat/team_recruit.png` | 0.8 | 组队招募页签 |
| `OUT_OF_RANGE_RECRUIT` | `templates/recruit_list/out_of_range.png` | 0.7 | 超出范围悬赏（需刷新） |
| `RECRUIT_INVITE` | `templates/recruit_list/recruit_invite.png` | 0.8 | 列表过期邀请标识 |
| `JOIN_BUTTON` | `templates/recruit_list/join_team.png` | 0.8 | 加入队伍按钮 |
| `TEAM_COMPLETED` | `templates/recruit_list/out_time.png` | 0.8 | 已完成标记 |
| `TEAM_FULL` | `templates/team_room/template.png` | 0.8 | 队伍已满弹窗 |
| `READY_BUTTON` | `templates/team_room/prepare.png` | 0.8 | 准备按钮 |
| `EXIT_CONFIRM` | `templates/team_room/confirm.png` | 0.8 | 退出确认弹窗 |
| `DAILY_LIMIT` | `templates/team_room/daily_limit.png` | 0.8 | 已达上限弹窗 |
| `BATTLE_WARNING` | `templates/fight/warning.png` | 0.7 | 下滑警告屏 |
| `ULTIMATE_SKILL` | `templates/fight/r_ziyuan.png` | 0.6 | 大招图标（较低阈值） |
| `WEAPON_SKILL` | `templates/fight/wopen_shedao.png` | 0.6 | 武器技能图标（较低阈值） |
| `DEFEAT_POPUP` | `templates/fight/defeat_popup.png` | 0.6 | 失败弹窗 |
| `SETTLEMENT_POPUP` | `templates/settlement/black.png` | 0.7 | 结算黑色遮罩 |
| `CONFIRM_BUTTON` | `templates/settlement/confirm.png` | 0.8 | 确定按钮 |
| `BACK_BUTTON` | `templates/other/backward.png` | 0.8 | 通用返回按钮 |
| `CHAT_TAB` | `templates/chat/private_chat.png` | 0.8 | 私聊页签 |

### 等级图标匹配（单独缓存，不在 templates map 中）

| 方法 | 路径模式 | 阈值 |
|------|---------|------|
| `matchGradeIcon(grade)` | `templates/recruit_list/{grade.displayName}.png` | 0.85 |
| `matchLevelIcon(grade)` | `templates/team_room/lv{grade.level}.png` | 0.9 |

### 无模板的状态

以下 ScreenState 在 `templates` 映射中无对应条目：

- `LOBBY` — 无模板，无检测
- `RECRUIT_LIST` — 无模板（无法通过模板识别招募列表本身，仅通过 JOIN_BUTTON / RECRUIT_INVITE 间接判断）
- `TEAM_ROOM` — 无模板，无检测
- `WAITING_SCREEN` — 无模板，无检测
- `BATTLE_ACTIVE` — 无模板，无检测
- `UNKNOWN` — 无模板，兜底

**注意**: `RECRUIT_LIST` 和 `TEAM_ROOM` 虽然无模板，但在 `detectCurrentPage()` 的 `when` 分支中有对应的 GamePhase 映射。但由于 `detectWithCoord()` 遍历 `detectionOrder` 时遇到这两个状态会跳过（`getTemplate()` 返回 null），实际上它们永远不会被匹配到。

---

## 13. 技能释放（useSkills）

**代码位置**: `WorkflowEngine.kt:651-668`

```kotlin
private suspend fun useSkills(screen: Bitmap): Boolean {
    val ultimate = detector.matchTemplate(screen, ScreenState.ULTIMATE_SKILL)
    if (ultimate != null) {
        click(ultimate)
        log("释放大招")
        delay(200)
        return true
    }
    val weapon = detector.matchTemplate(screen, ScreenState.WEAPON_SKILL)
    if (weapon != null) {
        click(weapon)
        log("释放武器技能")
        delay(200)
        return true
    }
    return false
}
```

**策略**: 顺序检测，先大招后武器技能。找到一个可用即释放，延迟 200ms。

**在 `phaseBattle` 中的调用**: 每 500ms（`now - lastSkillTime >= 500`）调用一次，成功则更新 `lastSkillTime = now`。

**注意**: `skillCooldown = 2000L` 在 `phaseBattle` 中定义但未用于限制技能释放频率，实际限制由 500ms 检测间隔提供。

---

## 14. 点击空白关闭弹窗（clickOutside）

**代码位置**: `WorkflowEngine.kt:681-702`

```kotlin
private suspend fun clickOutside(screen: Bitmap? = null) {
    val display = context.resources.displayMetrics
    val w = display.widthPixels.toFloat()
    val h = display.heightPixels.toFloat()

    if (screen != null) {
        try matchTemplate(SETTLEMENT_POPUP):
            → clickAt(w/2, h*0.88)  // 弹窗底部外
            → delay(300ms)
            → return
        try matchTemplate(CONFIRM_BUTTON):
            → clickAt(w/2, h*0.2)   // 弹窗顶部外
            → delay(300ms)
            → return
    }
    
    // 兜底
    → clickAt(w/2, h*0.88)  // 屏幕底部中央
    → delay(300ms)
}
```

**点击位置**:
- `SETTLEMENT_POPUP` 存在：`(屏幕宽/2, 屏幕高 × 0.88)` — 弹窗下方空白处
- `CONFIRM_BUTTON` 存在：`(屏幕宽/2, 屏幕高 × 0.2)` — 弹窗上方空白处
- 兜底：`(屏幕宽/2, 屏幕高 × 0.88)` — 默认底部中央

---

## 15. 截图与点击

### 截图（captureBitmap）

```kotlin
private suspend fun captureBitmap(): Bitmap? {
    repeat(3) {
        val bmp = capture.capture()
        if (bmp != null) return bmp
        delay(300)
    }
    log("⚠ 截图失败")
    return null
}
```

- 最多 3 次，每次间隔 300ms
- 返回 null 时调用方 `delay(interval); continue`
- 截得的 Bitmap 在当前循环体 `finally { screen.recycle() }` 中回收

### 截图后的生命周期管理

```kotlin
// 每个循环体的标准模式：
val screen = captureBitmap()
if (screen == null) { delay(interval); continue }
try {
    // ... 检测和操作 ...
} finally {
    screen.recycle()  // 确保回收
}
```

### 点击

```kotlin
private fun click(coord: Pair<Float, Float>) {
    accessibility?.clickAt(coord.first, coord.second)
}
```

- 委托给 `NinjaAccessibilityService.getInstance()?.clickAt(x, y)`
- 如果 accessibility 实例为空（服务未绑定），点击静默忽略
- 实际的 AccessibilityService 手势持续 100ms

---

## 16. 异常处理层次

### 层级体系

```
Level 1 — 步骤级异常（方法内部计数，3次后触发 Level 2）
├── ensureRecruitView: unknownCount ≥ 3 → detectCurrentPage()
├── phaseNavigateAndScan:
│   ├── anomalyCount ≥ 3 → detectForPhase(SCOPE_RECRUIT)
│   └── noMatchCycles ≥ 3 → 检查被邀请/离开界面
├── phaseBattle: missCount ≥ 10 → detectForPhase(SCOPE_BATTLE)
├── phaseClaim: missCount ≥ 3 → detectForPhase(SCOPE_CLAIM)
└── exitTeam: 10次重试耗尽 → 静默结束

Level 2 — 页面级异常（3次 Level 1 失败后触发 Level 3）
├── detectCurrentPage() → GamePhase? (成功则跳转)
└── 检测到特定页面 → 跳转到对应流程

Level 3 — 全局级异常（3次 Level 2 失败后触发）
├── throw RuntimeException → 主循环 catch Exception
├── globalFailCount++
├── globalFailCount < 3 → RECOVERY → IDLE → 重试
└── globalFailCount ≥ 3 → writeCrashLog() → 停止
```

### 具体方法的异常出口

| 方法 | 失败条件 | 失败动作 |
|------|---------|---------|
| `ensureRecruitView` | 3次 UNKNOWN + detectCurrentPage = null | `throw RuntimeException` |
| `ensureRecruitView` | 6次重试耗尽 | `throw RuntimeException` |
| `phaseNavigateAndScan` | 3次 anomaly + SCOPE_RECRUIT = UNKNOWN | `throw RuntimeException` |
| `phaseBattle` | 3次 battleFallback + detectCurrentPage = null | `throw RuntimeException` |
| `phaseClaim` | 3次 claimFallback + detectCurrentPage = null | `throw RuntimeException` |
| `exitTeam` | 10次重试耗尽 | 静默退出（无异常） |

### 崩溃日志格式

```
=== NinjaAu Crash Log ===
Time: yyyyMMdd_HHmmss
Phase: {currentPhase}
CurrentBounty: {grade or null}
RunCounts: {map of grade->count}
TotalCycles: {total runs completed}
GlobalFailCount: {1,2, or 3}
================================
```

写入路径: `filesDir/crash_logs/crash_yyyyMMdd_HHmmss.log`

---

## 17. 主流程状态跳转图

```
  ┌────────────────────────────────────────────────────────────┐
  │                        主循环                               │
  │  while (active && phase != DONE && globalFail < 3)          │
  └────────────────────────────────────────────────────────────┘
                           │
          ┌────────────────┼────────────────┬──────────────────┐
          │                │                │                  │
          ▼                ▼                ▼                  ▼
   ┌────────────┐  ┌────────────┐  ┌────────────┐  ┌──────────────┐
   │IDLE/LOBBY/ │  │ TEAM_ROOM  │  │   FIGHT    │  │ SETTLEMENT   │
   │CHAT/       │  │            │  │            │  │              │
   │RECRUIT_LIST│  │ phaseVali- │  │phaseBattle │  │ phaseClaim   │
   │            │  │ date()     │  │            │  │              │
   │phaseNavi-  │  │            │  │ ①结算弹窗   │  │ ①关弹窗+确认  │
   │gateAndScan │  │①BATTLE_   │  │ ②确定按钮   │  │ ②回大厅       │
   │            │  │  WARNING   │  │ ③失败弹窗   │  │ ③更新计数     │
   │①导航→扫描  │  │  →FIGHT    │  │ →SETTLEMENT │  │ →IDLE/DONE   │
   │②匹配→加入  │  │②SETTLEMENT │  │            │  │              │
   │③准备→TEAM  │  │ →SETTLEMENT│  │ 异常:       │  │ 异常:        │
   │            │  │③CHAT_ICON  │  │ ①SCOPE_    │  │ ①SCOPE_CLAIM │
   │异常:       │  │ →IDLE      │  │  BATTLE     │  │ ②3次→detect-│
   │①noMatch≥3  │  │④超时15s    │  │ ②3次→detect│  │  CurrentPage  │
   │ →检查邀请   │  │ →exitTeam  │  │  CurrentPage│  │③3次→STOP     │
   │②anomaly≥3  │  │ →IDLE      │  │③3次→STOP   │  │              │
   │ →SCOPE_    │  │            │  │            │  │              │
   │ RECRUIT    │  │            │  │            │  │              │
   │③→LOBBY/STOP│  │            │  │            │  │              │
   └──────┬─────┘  └──────┬─────┘  └──────┬─────┘  └──────┬───────┘
          │               │               │               │
          ▼               ▼               ▼               │
   ┌──────────────────────────────────────────────────────┘
   │
   ▼
┌──────────┐    ┌──────────┐    ┌─────────────────┐
│  IDLE    │◄───│ RECOVERY │◄───│ Exception catch  │
│ (重新导航)│    │ (1.5s后) │    │ globalFailCount++ │
└──────────┘    └──────────┘    └─────────────────┘
                                        │
                                   globalFail ≥ 3?
                                        │
                                        ▼
                                 ┌──────────────┐
                                 │ writeCrashLog │
                                 │    停止       │
                                 └──────────────┘
```

### 完成路径

```
activeGrades 为空 → DONE
    ↓
runLoop 返回 true
    ↓
GameManager._state.value = IDLE
```

### 暂停/恢复路径

```
runLoop 运行中
    ↓
GameManager.pauseScript() → mainJob.cancel() → state = PAUSED
    ↓
GameManager.resumeScript() → 新 mainJob = scope.launch { 新 WorkflowEngine.runLoop(...) }
    ↓
新的独立 WorkflowEngine 实例从 IDLE 开始
```

**注意**: 暂停→恢复会创建全新的 `WorkflowEngine` 实例，`GameContext` 从 `GameManager.selectedBounties` 重建。这意味着之前的内存计数丢失（但 `runCounts` 在恢复时重置为 0，而 `BountyConfig.targetRuns` 不保存已完成的次数）。当前没有跨暂停周期的持久化计数。

---

*本文档严格依据 `WorkflowEngine.kt` (v2.3)、`SceneDetector.kt`、`ScreenState.kt`、`GameContext.kt`、`TemplateMatcher.kt` 的实际代码逻辑编写。每个分支、每个条件、每个常量均可在对应文件中找到精确对应。*
