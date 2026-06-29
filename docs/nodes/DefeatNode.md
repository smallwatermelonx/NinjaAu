# DefeatNode - 失败结算模块规格

## 屏幕状态

| 方向 | ScreenState | 说明 |
|------|-------------|------|
| 进入 | DEFEAT | FightNode Lv消失后检测到失败界面 |
| 退出1 | LOBBY | 队长解散队伍，回到大厅 |
| 退出2 | BOUNTY_DETAIL | 未解散，仍在悬赏详情，继续准备 |
| 退出3 | SETTLEMENT | 队友战斗成功，进入结算 |

## 失败界面类型

| 界面 | 模板 | 说明 | 操作 |
|------|------|------|------|
| 观战面板 | DEFEAT_BACK_BUTTON | 角色死亡，等待队友战斗 | 点击"返回" → detectPostDefeat() |
| 最终失败 | DEFEAT_SCREEN + DEFEAT_CONFIRM | 失败界面 | 点击"确定" → detectPostDefeat() |
| 结算 | SETTLEMENT_POPUP / CONFIRM_BUTTON | 队友战斗成功（追梦场景直接出现确认按钮） | 转入 SETTLEMENT |

## 交互动作

| # | 动作 | 裁剪区域 | 匹配模板 | 坐标/偏移 | 延迟 | 条件 |
|---|------|----------|----------|----------|------|------|
| 1 | 检测最终失败界面 | 全屏 | DEFEAT_SCREEN | - | - | "失败"字样出现 |
| 2 | 点击确定按钮 | 下半侧（y=50%~100%） | DEFEAT_CONFIRM | crop偏移 + y偏移rows/2 | 800ms | 最终失败界面 |
| 3 | 点击返回按钮 | 中间1/3上半侧 | DEFEAT_BACK_BUTTON | crop偏移 + x偏移cols/3 | 800ms | 观战面板 |
| 3.5 | 助战按钮检测 | 左下1/3 | ASSIST_BUTTON | crop偏移 + y偏移rows*2/3 | - | 观战面板标识，搜索返回按钮 |
| 4 | 落点检测 | 左侧1/10 | CHAT_ICON | 裁剪匹配 | - | 点击确定/返回后 |
| 5 | 检测结算弹窗 | 全屏 | SETTLEMENT_POPUP / CONFIRM_BUTTON | - | - | 队友战斗成功 |

## 裁剪区域说明

| 区域名 | 裁剪方法 | 占全屏比例 | 用途 |
|--------|---------|-----------|------|
| 确定按钮 | `Mat(mat, Rect(0, rows/2, cols, rows/2))` | 宽100% x 高50%（下半侧） | 失败界面确定按钮 |
| 返回按钮 | `Mat(mat, Rect(cols/3, 0, cols/3, rows/2))` | 宽33% x 高50%（中间1/3上半侧） | 观战面板返回按钮 |
| 助战按钮 | `detector.cropBottomLeftThird(mat)` | 宽33% x 高33%（左下） | 观战面板标识 |
| 大厅聊天 | `detector.cropLeftTenth(mat)` | 宽10% x 高100%（左侧） | 落点检测 |

## 决策逻辑

```
循环扫描（300ms间隔）:
  → 全屏匹配 DEFEAT_SCREEN → 最终失败界面
    → 裁剪下半侧匹配 DEFEAT_CONFIRM → 点击确定
      → 等待800ms → detectPostDefeat():
        → cropLeftTenth 检测 CHAT_ICON → 队长解散 → LOBBY
        → 否则 → 仍在悬赏详情 → BOUNTY_DETAIL
  → 中间1/3上半侧匹配 DEFEAT_BACK_BUTTON → 观战面板
    → 点击返回 → 等待800ms → detectPostDefeat()
      → cropLeftTenth 检测 CHAT_ICON → 队长解散 → LOBBY
      → 否则 → 仍在悬赏详情 → BOUNTY_DETAIL
  → 左下1/3匹配 ASSIST_BUTTON → 观战面板标识
    → 全屏搜索 DEFEAT_BACK_BUTTON → 点击返回 → 等待800ms → detectPostDefeat()
  → 全屏匹配 SETTLEMENT_POPUP 或 CONFIRM_BUTTON → 队友成功 → SETTLEMENT
  → 无匹配 → checkNodeTimeout（30s）→ LOBBY
```

## detectPostDefeat 逻辑

点击确定或返回按钮后，截图检测当前页面：
- cropLeftTenth 匹配 CHAT_ICON → 大厅标识 → 队长已解散 → 返回 LOBBY
- 未匹配 CHAT_ICON → 仍在悬赏详情 → 返回 BOUNTY_DETAIL（继续准备）

## 入口

FightNode Lv消失后 → 500ms等待 → 检测 DEFEAT_SCREEN → 返回 DEFEAT 阶段。

RecoveryNode 也会检测 DEFEAT_SCREEN 和 DEFEAT_BACK_BUTTON，路由至 `GamePhase.DEFEAT`。

## 异常处理

| 异常 | 处理 |
|------|------|
| 截图返回 null | 等待 100ms 后重试 |
| 30s 无匹配 | checkNodeTimeout → NodeTimeoutException → RECOVERY |
| 确定/返回按钮未找到 | 继续扫描（可能还在动画中） |
| 退出后无法识别页面 | 默认返回 LOBBY |
