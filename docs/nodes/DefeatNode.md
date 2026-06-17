# DefeatNode - 失败结算模块规格

> **状态**: TODO 桩（未实现）

## 屏幕状态

| 方向 | ScreenState | 说明 |
|------|-------------|------|
| 进入 | DEFEAT_POPUP | 战斗失败后弹出失败弹窗 |
| 退出 | LOBBY | 直接返回大厅（硬编码） |

## 当前实现

```kotlin
override suspend fun execute(ctx: GameContext): GamePhase? {
    log("失败结算 Phase（暂未实现）")
    return GamePhase.LOBBY
}
```

当前为 TODO 桩：不做任何屏幕检测或交互，直接返回 `GamePhase.LOBBY`。

## 交互动作

| # | 动作 | 说明 |
|---|------|------|
| - | 无 | 当前实现无任何交互 |

## 计划实现逻辑

```
循环扫描:
  → 匹配 DEFEAT_POPUP → 点击关闭
  → 匹配 CONFIRM_BUTTON → 点击确认
  → 匹配 CHAT_ICON → 已回到大厅 → 返回 LOBBY
  → 无匹配 → 检查超时
```

## 异常处理

| 异常 | 处理 |
|------|------|
| 当前无异常处理 | 直接返回 LOBBY |
