# DefeatNode - 失败结算模块规格

## 屏幕状态

| 方向 | ScreenState | 说明 |
|------|-------------|------|
| 进入 | DEFEAT_SCREEN / DEFEAT_BACK_BUTTON / DEFEAT_SKIP | FightNode 检测到失败后返回 DEFEAT 阶段 |
| 退出1 | LOBBY | 队长解散队伍，回到大厅 |
| 退出2 | BOUNTY_DETAIL | 未解散，仍在悬赏详情，继续准备 |
| 退出3 | SETTLEMENT | 队友战斗成功，进入结算 |

## 失败界面类型

| 界面 | 模板 | 说明 | 操作 |
|------|------|------|------|
| full_two | DEFEAT_BACK_BUTTON | 角色死亡，等待队友战斗 | 点击"返回"按钮 → LOBBY |
| full | DEFEAT_SCREEN + DEFEAT_CONFIRM | 最终失败界面 | 点击"确定" → 检测页面 → LOBBY 或 BOUNTY_DETAIL |
| 结算 | SETTLEMENT_POPUP | 队友战斗成功 | 转入 SETTLEMENT |

## 交互动作

| # | 动作 | 裁剪区域 | 匹配模板 | 坐标/偏移 | 延迟 | 条件 |
|---|------|----------|----------|----------|------|------|
| 1 | 检测最终失败界面 | 全屏 | DEFEAT_SCREEN | - | - | "失败"字样出现 |
| 2 | 点击确定按钮 | 下半侧（y=50%~100%） | DEFEAT_CONFIRM | crop偏移 + y偏移rows/2 | 800ms | 最终失败界面 |
| 3 | 退出后页面检测 | 左侧1/10 + 上方1/4 | CHAT_ICON / BATTLE_LOADING | 裁剪匹配 | - | 点击确定后 |
| 4 | 点击返回按钮 | 三分之一中间区域上半侧 | DEFEAT_BACK_BUTTON | crop偏移 + x偏移cols/3 | 500ms | 等待队友界面 |
| 5 | 检测结算弹窗 | 全屏 | SETTLEMENT_POPUP | - | - | 队友战斗成功 |

## 裁剪区域说明

| 区域名 | 裁剪方法 | 占全屏比例 | 用途 |
|--------|---------|-----------|------|
| 确定按钮 | `Mat(mat, Rect(0, rows/2, cols, rows/2))` | 宽100% x 高50%（下半侧） | 失败界面确定按钮 |
| 返回按钮 | `Mat(mat, Rect(cols/3, 0, cols/3, rows/2))` | 宽33% x 高50%（三分之一中间区域上半侧） | 等待队友界面返回按钮 |
| 大厅聊天 | `detector.cropLeftTenth(mat)` | 宽10% x 高100%（左侧） | 退出后页面检测 |
| 战斗加载 | `detector.cropTopQuarter(mat)` | 宽100% x 高25%（上方） | 退出后页面检测 |

## 决策逻辑

```
循环扫描（300ms间隔）:
  → 全屏匹配 DEFEAT_SCREEN → 最终失败界面
    → 裁剪下半侧匹配 DEFEAT_CONFIRM → 点击确定
      → 等待800ms → detectPostDefeat():
        → cropLeftTenth 检测 CHAT_ICON → 队长解散 → LOBBY
        → cropTopQuarter 检测 BATTLE_LOADING → 战斗已开始 → BATTLE_LOADING
        → 都未匹配 → 默认 LOBBY
  → 三分之一中间区域上半侧匹配 DEFEAT_BACK_BUTTON → 等待队友界面
    → 点击返回 → LOBBY
  → 全屏匹配 SETTLEMENT_POPUP → 队友成功 → SETTLEMENT
  → 无匹配 → checkNodeTimeout（30s）→ LOBBY
```

## 入口

FightNode 在 Boss 战斗循环中检测到以下情况后返回 `GamePhase.DEFEAT`：
- DEFEAT_SCREEN（"失败"字样）
- DEFEAT_SKIP（接力界面跳过按钮，裁剪中间1/5 × 下方1/5）

RecoveryNode 也会检测 DEFEAT_SCREEN 和 DEFEAT_BACK_BUTTON，路由至 `GamePhase.DEFEAT`。

## 异常处理

| 异常 | 处理 |
|------|------|
| 截图返回 null | 等待 100ms 后重试 |
| 30s 无匹配 | checkNodeTimeout → 返回 LOBBY |
| 确定按钮未找到 | 继续扫描（可能还在动画中） |
| 退出后无法识别页面 | 默认返回 LOBBY |
