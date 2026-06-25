# DefeatNode - 失败结算模块规格

## 屏幕状态

| 方向 | ScreenState | 说明 |
|------|-------------|------|
| 进入 | DEFEAT_SCREEN / DEFEAT_BACK_BUTTON | FightNode 检测到"失败"字样后返回 DEFEAT 阶段 |
| 退出1 | LOBBY | 点击返回/确定后回到大厅 |
| 退出2 | SETTLEMENT | 队友战斗成功，进入结算 |

## 失败界面类型

| 界面 | 模板 | 说明 | 操作 |
|------|------|------|------|
| full_two | DEFEAT_BACK_BUTTON | 角色死亡，等待队友战斗 | 点击底部"返回"按钮 → LOBBY |
| full | DEFEAT_SCREEN + CONFIRM_BUTTON | 最终失败界面 | 点击"确定"按钮 → LOBBY |
| 结算 | SETTLEMENT_POPUP | 队友战斗成功 | 转入 SETTLEMENT |

## 交互动作

| # | 动作 | 裁剪区域 | 匹配模板 | 坐标/偏移 | 延迟 | 条件 |
|---|------|----------|----------|----------|------|------|
| 1 | 检测最终失败界面 | 全屏 | DEFEAT_SCREEN | - | - | "失败"字样出现 |
| 2 | 点击确定按钮 | 中心1/2宽x1/3高 | CONFIRM_BUTTON | crop偏移 + 全屏偏移 | 500ms | 最终失败界面 |
| 3 | 点击返回按钮 | 底部中心1/3宽x1/9高 | DEFEAT_BACK_BUTTON | crop偏移 + 全屏偏移 | 500ms | 等待队友界面 |
| 4 | 检测结算弹窗 | 全屏 | SETTLEMENT_POPUP | - | - | 队友战斗成功 |

## 裁剪区域说明

| 区域名 | 裁剪方法 | 占全屏比例 | 用途 |
|--------|---------|-----------|------|
| 确定按钮 | `detector.cropCenterHalf(mat)` | 宽50% x 高33%（中心） | 失败界面确定按钮 |
| 返回按钮 | `detector.cropBottomCenterNinth(mat)` | 宽33% x 高11%（底部中心） | 等待队友界面返回按钮 |

## 决策逻辑

```
循环扫描（300ms间隔）:
  → 全屏匹配 DEFEAT_SCREEN → 最终失败界面
    → 裁剪中心区域匹配 CONFIRM_BUTTON → 点击确定 → LOBBY
  → 裁剪底部中心匹配 DEFEAT_BACK_BUTTON → 等待队友界面
    → 点击返回 → LOBBY
  → 全屏匹配 SETTLEMENT_POPUP → 队友成功 → SETTLEMENT
  → 无匹配 → checkNodeTimeout（30s）→ LOBBY
```

## 异常处理

| 异常 | 处理 |
|------|------|
| 截图返回 null | 等待 100ms 后重试 |
| 30s 无匹配 | 返回 LOBBY |
| 确定按钮未找到 | 继续扫描（可能还在动画中） |

## 入口

FightNode 在 Boss 战斗循环中检测到 DEFEAT_SCREEN（"失败"字样）后返回 `GamePhase.DEFEAT`，WorkflowEngine 分发至 DefeatNode。

RecoveryNode 也会检测 DEFEAT_SCREEN 和 DEFEAT_BACK_BUTTON，路由至 `GamePhase.DEFEAT`。
