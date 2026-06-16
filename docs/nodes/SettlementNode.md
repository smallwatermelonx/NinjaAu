# SettlementNode - 结算模块规格

## 屏幕状态

| 方向 | ScreenState | 说明 |
|------|-------------|------|
| 进入 | SETTLEMENT | 从 FightNode 战斗胜利后进入 |
| 退出 | LOBBY | 领奖完成后回到大厅 |
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
  → 匹配 CONFIRM_BUTTON → 点击领奖
    → 点击后检查是否回到大厅（CHAT_ICON / RECRUIT_TAB）
      → 是 → 更新 runCounts + activeGrades → 返回 LOBBY 或 DONE
  → 无匹配 → 检查超时（30s）→ 抛 NodeTimeoutException
```

## 计数更新逻辑

领奖完成后需更新状态：
1. `runCounts[grade]++` — 该等级完成次数+1
2. 检查 `runCounts[grade] >= maxRuns` — 是否达到该等级最大次数
3. 达到上限 → 从 `activeGrades` 移除该等级
4. `activeGrades` 为空 → 所有等级完成，返回 DONE
5. 否则 → 返回 LOBBY 继续下一轮

## 业务线切换

SettlementNode 负责在每日悬赏完成后切换到个人悬赏：
- 每日悬赏全部完成 → 切换到个人悬赏业务线
- 个人悬赏全部完成 → 检查是否有NS事件悬赏
- 全部完成 → 返回 DONE

## 异常处理

| 异常 | 处理 |
|------|------|
| 截图返回 null | 等待 500ms 后重试 |
| 30s 无匹配 | 抛 NodeTimeoutException |
| 领奖后未回到大厅 | 继续扫描，等待页面切换 |
