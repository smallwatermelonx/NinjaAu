# BountyDetailNode - 悬赏详情/组队模块规格

## 屏幕状态

| 方向 | ScreenState | 说明 |
|------|-------------|------|
| 进入 | READY_BUTTON | 从 BountyListNode 点击加入后进入 |
| 退出 | BATTLE_LOADING | 全员准备后进入战斗加载 |
| 退出 | LOBBY | 退出队伍/达上限后回到大厅 |

## 交互动作

| # | 动作 | 裁剪区域 | 匹配模板 | 坐标/偏移 | 延迟 | 条件 |
|---|------|----------|----------|----------|------|------|
| 1 | 上限检测 | 上方 1/5 | DAILY_LIMIT | 无点击，仅检测 | - | 非 chaseDream 且上限标识出现 |
| 2 | 点击准备按钮 | 右下 1/4 | READY_BUTTON | 匹配坐标 + 偏移(halfW, 3/4 height) | 500ms | 准备按钮出现且等级校验通过 |
| 3 | 战斗等待检测 | 左下 1/3 | BATTLE_LOADING | 匹配坐标点击 | - | 点击准备后 |
| 4 | 回到大厅检测 | 全屏 | CHAT_ICON | 匹配坐标点击 | - | 异常退出后回到大厅 |
| 5 | 点击返回按钮 | 左上 1/8 | BACK_BUTTON | 匹配坐标点击 | 1000ms | 退出队伍时 |
| 6 | 确认退出弹窗 | 右半下半 | EXIT_CONFIRM | 匹配坐标 + 偏移(halfW, halfH) | - | 返回按钮点击后 |

## 决策逻辑

```
循环扫描（无显式间隔，依赖截图+匹配开销）:
  → 截图 null → 等待 500ms 后重试
  → 匹配 DAILY_LIMIT（上方1/5）→ 匹配等级图标 → 标记对应组完成 → exitTeam → 返回 LOBBY
  → battleWaitStart == 0 时匹配 READY_BUTTON（右下1/4）
    → 匹配到 → 匹配等级图标（activeGrades）
      → 等级匹配失败 → exitTeam → 返回 LOBBY
      → 等级匹配成功 → 坐标偏移点击准备 → 启动战斗等待计时
      → SS+ (lv105-130) → 播放提醒铃声
  → battleWaitStart > 0 时匹配 BATTLE_LOADING（左下1/3）
    → 匹配到 → 返回 BATTLE_LOADING
    → 超过 30s → exitTeam → 返回 LOBBY
  → 匹配 CHAT_ICON（全屏）→ 已回到大厅 → 返回 LOBBY
  → 无匹配 → checkNodeTimeout 超时检测
```

> 注意：TEAM_INVITATION 由 WorkflowEngine 的全局邀请拦截处理，不在本节点内。
> DAILY_LIMIT 由本节点自行检测（含二次确认机制）。

## 等级校验逻辑

点击准备前需校验当前队伍等级是否在 `activeGrades` 范围内：
- 匹配等级图标（`matchAnyLevelIconMat`，并行匹配所有 activeGrades）→ 获取等级信息
- 等级匹配失败（不在勾选范围内）→ 退出队伍
- 等级在范围内 → 记录 `ctx.actualGrade` → 点击准备
- SS+ 悬赏（lv105-130）→ 播放提醒铃声

## DAILY_LIMIT 上限检测

检测到达今日上限的特殊情况：
1. 匹配 DAILY_LIMIT 标识（上方 1/5 区域），不点击
2. 匹配等级图标确定哪个组达上限
3. 标记该组所有成员 `runCounts = defaultRuns`
4. 从 `activeGrades` 移除该组
5. `exitTeam()` 退出队伍 → 返回 LOBBY

> chaseDream 等级跳过上限检测（追梦悬赏不检查上限）。
> 等级匹配失败时用 `currentBounty` 兜底标记完成。

## 战斗等待

点击准备后启动 30s 倒计时（`WAIT_BATTLE_TIMEOUT_MS`）：
- 每轮检测左下 1/3 区域的 BATTLE_LOADING
- 倒计时内匹配到 → 返回 BATTLE_LOADING
- 倒计时结束仍未开始 → exitTeam → 返回 LOBBY

## exitTeam 退出队伍流程

```
阶段①：截图 → 裁剪左上1/8 → 匹配 BACK_BUTTON → 点击返回 → 等待 1000ms
阶段②：截图 → 裁剪右半下半 → 匹配 EXIT_CONFIRM → 点击确认退出
```

## 异常处理

| 异常 | 处理 |
|------|------|
| 截图返回 null | 等待 500ms (POST_CLICK_DELAY) 后重试 |
| 30s 无匹配 | checkNodeTimeout 超时检测 |
| 等级匹配失败 | exitTeam，回到大厅 |
| 战斗等待超时 30s | exitTeam，回到大厅 |
| SS+ 悬赏 (lv105-130) | 播放提醒铃声 |

## 性能要求

| 指标 | 目标 | 说明 |
|------|------|------|
| 等级匹配+点击准备 | ≤ 500ms | 从检测到准备按钮到点击准备完成 |
| 等级图标匹配 | ≤ 200ms | 多等级×多变体并行匹配（`SceneDetector.matchAnyLevelIconMat`） |

等级匹配使用协程并行化：所有等级的所有级别变体同时匹配，取最佳结果。
