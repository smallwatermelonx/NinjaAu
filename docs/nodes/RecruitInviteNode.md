# RecruitInviteNode - 招募邀请模块规格

> **状态**: TODO 桩（未实现）

## 屏幕状态

| 方向 | ScreenState | 说明 |
|------|-------------|------|
| 进入 | RECRUIT_INVITE | 检测到招募邀请标识 |
| 退出 | RECRUIT_LIST | 直接返回招募列表（硬编码） |

## 当前实现

```kotlin
override suspend fun execute(ctx: GameContext): GamePhase? {
    log("招募邀请 Phase（暂未实现）")
    return GamePhase.RECRUIT_LIST
}
```

当前为 TODO 桩：不做任何屏幕检测或交互，直接返回 `GamePhase.RECRUIT_LIST`。

## 交互动作

| # | 动作 | 说明 |
|---|------|------|
| - | 无 | 当前实现无任何交互 |

## 计划实现逻辑

```
循环扫描:
  → 匹配 RECRUIT_INVITE → 点击邀请标识刷新列表
  → 匹配 RECRUIT_LIST_SCREEN → 已在列表 → 返回 RECRUIT_LIST
  → 无匹配 → 检查超时
```

## 异常处理

| 异常 | 处理 |
|------|------|
| 当前无异常处理 | 直接返回 RECRUIT_LIST |
