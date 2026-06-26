# BountyDetailNode - 悬赏详情/组队模块规格

## 核心原则

**LV 图标是唯一的等级来源。** 悬赏列表点击不可靠（刷新/滑动可能导致点错），进入详情后必须通过 LV 图标判断实际字母等级，以此作为所有后续决策的依据。

## 屏幕状态

| 方向 | ScreenState | 说明 |
|------|-------------|------|
| 进入 | READY_BUTTON | 从 BountyListNode 点击加入后进入 |
| 退出 | BATTLE_LOADING | 全员准备后进入战斗加载 |
| 退出 | LOBBY | 退出队伍/达上限后回到大厅 |

## 交互动作

| # | 动作 | 裁剪区域 | 匹配模板 | 坐标/偏移 | 延迟 | 条件 |
|---|------|----------|----------|----------|------|------|
| 1 | LV 图标检测 | 上方1/10、中间1/3（cropTopMiddleTenth） | matchAnyLevelIconMat(activeGrades) | - | - | 每轮首先执行，确定实际等级 |
| 2 | 上限检测 | 上方 1/5 | DAILY_LIMIT | 无点击，仅检测 | - | LV 确认为非 chaseDream 后 |
| 3 | 点击准备按钮 | 右下 1/4 | READY_BUTTON | 匹配坐标 + 偏移(halfW, 3/4 height) | 500ms | LV 确认 + 准备按钮出现 |
| 4 | 战斗等待检测 | 左下 1/3 | BATTLE_LOADING | 裁剪匹配 | - | 点击准备后 |
| 5 | 回到大厅检测 | 全屏 | CHAT_ICON | 无点击，仅检测 | - | 异常退出后回到大厅 |
| 6 | 点击返回按钮 | 左上 1/8 | BACK_BUTTON | 匹配坐标点击 | 1000ms | 退出队伍时 |
| 7 | 确认退出弹窗 | 右半下半 | EXIT_CONFIRM | 匹配坐标 + 偏移(halfW, halfH) | - | 返回按钮点击后 |

## 决策逻辑

```
循环扫描:
  → 截图 null → 等待 500ms 后重试

  ① LV 图标检测（上方1/10，matchAnyLevelIconMat + activeGrades）:
    → 匹配失败 → exitTeam → LOBBY（或 BATTLE_LOADING）
    → 匹配成功 → 确定 actualGrade，记录到 ctx.actualGrade

  ② LV 不在 activeGrades → 列表点错，exitTeam → LOBBY（或 BATTLE_LOADING）

  ③ LV 确认后，根据实际等级决定是否检测上限:
    → chaseDream 等级 → 跳过上限检测
    → 普通等级 → 匹配 DAILY_LIMIT（上方1/5）
      → 匹配到 → 标记该组完成 → exitTeam → LOBBY（或 BATTLE_LOADING）

  ④ 准备按钮检测（右下1/4）:
    → 匹配到 READY_BUTTON → 点击准备 → 启动战斗等待计时
    → SS+ (lv105-130) → 播放提醒铃声

  ⑤ 战斗等待中（左下1/3）:
    → 匹配到 BATTLE_LOADING → 返回 BATTLE_LOADING
    → 超过 30s → exitTeam → LOBBY（或 BATTLE_LOADING）

  ⑥ 匹配 CHAT_ICON（全屏）→ 已回到大厅 → LOBBY

  ⑦ 无匹配 → checkNodeTimeout 超时检测（严重错误，进入恢复）
```

> TEAM_INVITATION 由 WorkflowEngine 的全局邀请拦截处理，不在本节点内。

## 等级校验逻辑

进入详情后的第一件事是通过 LV 图标确定实际等级：

1. 匹配 LV 图标（`matchAnyLevelIconMat`，并行匹配所有 activeGrades）
2. 匹配失败 → exitTeam，退出队伍
3. 匹配成功 → 得到 actualGrade
4. actualGrade 不在 activeGrades → 列表点错，退出队伍
5. actualGrade 在 activeGrades → 记录 `ctx.actualGrade`，继续后续流程
6. actualGrade 是 chaseDream → 跳过上限检测
7. actualGrade 是普通等级 → 检测上限

**不信任悬赏列表的点击判断。** 列表可能因刷新/滑动导致点错，只有详情页内的 LV 图标才是准确的。

## DAILY_LIMIT 上限检测

仅在 LV 确认为非 chaseDream 等级后执行：

1. 匹配 DAILY_LIMIT 标识（上方 1/5 区域），不点击
2. 匹配到 → 使用 actualGrade 的 group 信息标记完成
3. 标记该组所有成员 `runCounts = defaultRuns`
4. 从 `activeGrades` 移除该组内**非追梦**等级（追梦等级保留）
5. `exitTeam()` 退出队伍 → 返回 LOBBY（或 BATTLE_LOADING）

## 战斗等待

点击准备后启动 30s 倒计时（`WAIT_BATTLE_TIMEOUT_MS`）：
- 每轮检测左下 1/3 区域的 BATTLE_LOADING
- 倒计时内匹配到 → 返回 BATTLE_LOADING
- 倒计时结束仍未开始 → exitTeam → 返回 LOBBY（或 BATTLE_LOADING）

**注意**：此超时与 `checkNodeTimeout`（30s 无匹配超时）含义不同：
- `WAIT_BATTLE_TIMEOUT_MS`：挂机等待超时，属于正常退出流程
- `checkNodeTimeout`：严重错误（无任何匹配），触发恢复机制

## exitTeam 退出队伍流程

```
阶段①：截图 → 裁剪左上1/8 → 匹配 BACK_BUTTON → 点击返回 → 等待 1000ms
阶段②：截图 → 裁剪右半下半 → 匹配 EXIT_CONFIRM → 点击确认退出
阶段③：等待500ms → 截图检测实际页面:
  → cropLeftTenth 检测 CHAT_ICON → 退出成功，返回 LOBBY
  → cropTopQuarter 检测 BATTLE_LOADING → 退出期间战斗已开始，返回 BATTLE_LOADING
  → 都未匹配 → 默认返回 LOBBY
```

**所有 exitTeam 调用点（LV不匹配、上限、战斗超时）统一返回实际检测到的 GamePhase**，不再无脑返回 LOBBY。

## 异常处理

| 异常 | 处理 |
|------|------|
| 截图返回 null | 等待 500ms (POST_CLICK_DELAY) 后重试 |
| LV 检测失败 | exitTeam → LOBBY / BATTLE_LOADING |
| LV 不在 activeGrades | exitTeam → LOBBY / BATTLE_LOADING |
| 30s 无匹配 | checkNodeTimeout 超时检测（严重错误） |
| 战斗等待超时 30s | exitTeam → LOBBY / BATTLE_LOADING |
| SS+ 悬赏 (lv105-130) | 播放提醒铃声 |

## 性能要求

| 指标 | 目标 | 说明 |
|------|------|------|
| LV 图标匹配 | ≤ 200ms | 多等级×多变体并行匹配（`SceneDetector.matchAnyLevelIconMat`） |
| 等级匹配+点击准备 | ≤ 500ms | 从检测到准备按钮到点击准备完成 |

等级匹配使用协程并行化：所有等级的所有级别变体同时匹配，取最佳结果。
