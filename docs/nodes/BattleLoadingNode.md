# BattleLoadingNode - 战斗加载模块规格

## 屏幕状态

| 方向 | ScreenState | 说明 |
|------|-------------|------|
| 进入 | BATTLE_LOADING | 从 BountyDetailNode 全员准备后进入 |
| 退出 | FIGHT | 加载完成（BATTLE_LOADING 模板不再匹配） |

## 交互动作

| # | 动作 | 裁剪区域 | 匹配模板 | 坐标/偏移 | 延迟 | 条件 |
|---|------|----------|----------|----------|------|------|
| 1 | 检测加载状态 | 全屏 | BATTLE_LOADING | - | 1000ms | 循环检测 |

## 决策逻辑

```
循环检测（1000ms间隔）:
  → 匹配 BATTLE_LOADING → 继续等待
  → 不匹配 → 加载完成，返回 FIGHT
  → 无匹配 → 检查超时（30s）→ 抛 NodeTimeoutException
```

## 说明

这是最简单的节点，只做一件事：等待加载画面消失。加载画面以 `smile.png` 模板识别。

## 异常处理

| 异常 | 处理 |
|------|------|
| 截图返回 null | 等待 1000ms 后重试 |
| 30s 仍在加载 | 抛 NodeTimeoutException |
