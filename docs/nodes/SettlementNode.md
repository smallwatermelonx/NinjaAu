# SettlementNode - 结算模块规格

## 屏幕状态

| 方向 | ScreenState | 说明 |
|------|-------------|------|
| 进入 | SETTLEMENT | 从 FightNode 战斗胜利后进入 |
| 退出 | IDLE | 领奖完成后回到大厅（由 WorkflowEngine 路由到 LobbyNode） |
| 退出 | PERSONAL_BOUNTY_CENTER | 个人悬赏领奖后返回个人悬赏中心 |
| 退出 | DONE | 所有等级完成后脚本结束 |

## 交互动作

| # | 动作 | 裁剪区域 | 匹配模板 | 坐标/偏移 | 延迟 | 条件 |
|---|------|----------|----------|----------|------|------|
| 1 | 点击结算弹窗空白处关闭 | 下方 1/5 中间 1/3 | SETTLEMENT_POPUP | 匹配坐标点击 | 500ms | 结算弹窗出现 |
| 2 | 点击确定按钮领奖 | 下方 1/5 中间 1/3 | CONFIRM_BUTTON | 匹配坐标点击 | 500ms | 确定按钮出现 |

## 裁剪区域说明

| 区域名 | 裁剪方法 | 占全屏比例 | 用途 |
|--------|---------|-----------|------|
| 下方 1/5 中间 1/3 | `detector.cropBottomMiddleFifth(mat)` | 宽33% x 高20%（底部中间） | 结算弹窗和确认按钮 |

**坐标计算**：
- X 基准：`screenMat.cols() / 3f`（全屏宽度的1/3处）
- Y 基准：`screenMat.rows() * 4f / 5f`（全屏高度的4/5处）

## 决策逻辑

```
循环扫描（500ms间隔）:
  → 匹配 SETTLEMENT_POPUP → 点击空白处关闭弹窗
  → 匹配 CONFIRM_BUTTON → 点击领奖 → 设置 confirmClicked=true
  → confirmClicked 且确认按钮消失 → 领奖完成，跳出循环
  → 无匹配 → 检查超时（30s）→ 抛 NodeTimeoutException

跳出循环后:
  → 更新 runCounts + activeGrades（仅日常悬赏，个人悬赏无次数限制）
  → activeGrades 为空:
    → 日常悬赏 + 个人悬赏启用 → 切换业务线到 PERSONAL → 返回 PERSONAL_BOUNTY_CENTER
    → 否则 → 返回 DONE
  → activeGrades 非空 → 返回 IDLE（回到大厅继续下一轮）
```

## 计数更新逻辑

领奖完成后需更新状态：
1. 仅日常悬赏更新计数（个人悬赏无次数限制）
2. `runCounts[grade]++` — 该等级完成次数+1
3. 检查组内是否完成（`group.isComplete`）
4. 组完成 → 从 `activeGrades` 移除该组内**非追梦**等级（追梦等级保留）
5. `activeGrades` 为空 + 日常悬赏 + 个人悬赏启用 → SettlementNode 内直接切换业务线到 PERSONAL
6. `activeGrades` 为空 + 其他情况 → 返回 DONE
7. `activeGrades` 非空 → 返回 IDLE 继续下一轮

## 异常处理

| 异常 | 处理 |
|------|------|
| 截图返回 null | 等待 500ms 后重试 |
| 30s 无匹配 | 抛 NodeTimeoutException |
| 领奖后未回到大厅 | 继续扫描，等待页面切换 |
