# BattleLoadingNode - 战斗加载模块规格

## 屏幕状态

| 方向 | ScreenState | 说明 |
|------|-------------|------|
| 进入 | BATTLE_LOADING | 从 BountyDetailNode/PersonalBountyDetailNode 全员准备后进入 |
| 退出 | FIGHT | 加载完成（BATTLE_LOADING 模板不再匹配） |
| 退出 | PERSONAL_BOUNTY_DETAIL / BOUNTY_DETAIL | 入口验证失败（误入），回退到前一阶段 |

## 交互动作

| # | 动作 | 裁剪区域 | 匹配模板 | 坐标/偏移 | 延迟 | 条件 |
|---|------|----------|----------|----------|------|------|
| 1 | 入口验证 | 全屏 | BATTLE_LOADING | 仅检测 | 1500ms | 进入时首次检测 |
| 2 | 检测加载状态 | 全屏 | BATTLE_LOADING | - | 1000ms | 循环检测 |

## 决策逻辑

```
进入时:
  等待 1500ms → 截图检测 BATTLE_LOADING
    → 匹配成功 → 进入循环等待
    → 匹配失败 → 入口验证失败，回退到前一阶段

循环检测（1000ms间隔）:
  → 匹配 BATTLE_LOADING → 继续等待
  → 不匹配 → 加载完成，返回 FIGHT
  → 无匹配 → 检查超时（30s）→ 抛 NodeTimeoutException
```

## 说明

入口验证机制：进入战斗加载节点后等待 1.5 秒再验证，避免因点击出发按钮到游戏实际加载之间的时间差导致误判。如果验证失败，根据当前业务线回退：
- 个人悬赏 → PERSONAL_BOUNTY_DETAIL
- 日常悬赏 → BOUNTY_DETAIL

## 异常处理

| 异常 | 处理 |
|------|------|
| 截图返回 null | 等待 1000ms 后重试 |
| 入口验证失败 | 回退到前一阶段（PERSONAL_BOUNTY_DETAIL 或 BOUNTY_DETAIL） |
| 30s 仍在加载 | 抛 NodeTimeoutException |
