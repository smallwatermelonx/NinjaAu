# RecoveryNode - 异常恢复模块规格

## 屏幕状态

| 方向 | ScreenState | 说明 |
|------|-------------|------|
| 进入 | 任意（异常状态） | 从任何节点超时或异常后进入 |
| 退出 | IDLE / 各正常阶段 | 根据识别到的页面路由到对应节点 |

## 恢复策略（按优先级）

### 策略1：识别当前页面 → 路由到正确节点

| 识别到的页面 | 目标 Phase | 说明 |
|-------------|-----------|------|
| SETTLEMENT_POPUP | SETTLEMENT | 在结算中，继续领奖 |
| CONFIRM_BUTTON | SETTLEMENT | 在结算中，继续领奖 |
| READY_BUTTON | BOUNTY_DETAIL | 在详情页，继续组队 |
| BATTLE_LOADING | BATTLE_LOADING | 在加载中，等待 |
| SLIDE_BUTTON / JUMP_BUTTON | FIGHT | 在战斗中，继续战斗 |
| PERSONAL_BOUNTY_LIST_SCREEN | PERSONAL_BOUNTY_CENTER | 在个人悬赏列表 |
| RECRUIT_LIST_SCREEN | RECRUIT_LIST | 在招募列表，继续扫描 |
| CHAT_ICON | LOBBY | 在大厅，重新导航 |

### 策略2：关闭弹窗

匹配到以下弹窗时点击关闭/拒绝：
- TEAM_INVITATION → 点击 INVITE_REJECT
- EXIT_CONFIRM → 点击确认
- 其他未知弹窗 → 尝试点击返回按钮

### 策略3：返回大厅

- 点击返回按钮（BACK_BUTTON）
- 连续返回直到识别到 CHAT_ICON

## 交互动作

| # | 动作 | 裁剪区域 | 匹配模板 | 坐标/偏移 | 延迟 | 条件 |
|---|------|----------|----------|----------|------|------|
| 1 | 截图识别当前页面 | 全屏 | 各页面标识模板 | - | 1500ms | 恢复循环 |
| 2 | 关闭弹窗 | 全屏 | 弹窗关闭按钮 | 匹配坐标点击 | 500ms | 检测到弹窗 |
| 3 | 点击返回 | 全屏 | BACK_BUTTON | 匹配坐标点击 | 500ms | 策略3 |

## 决策逻辑

```
recoveryAttempt++

if recoveryAttempt > MAX_RECOVERY_ATTEMPTS (5):
  → 强制重置，返回 IDLE（重新导航）

截图 → 识别当前页面:
  → 识别到正常页面 → 路由到对应 Phase
  → 识别到弹窗 → 关闭弹窗
  → 识别不到 → 点击返回按钮
  → 等待 1500ms → 重复
```

## 常量定义

| 常量 | 值 | 说明 |
|------|-----|------|
| RECOVERY_DELAY_MS | 1500ms | 恢复循环等待间隔 |
| MAX_RECOVERY_ATTEMPTS | 5 | 最大恢复尝试次数 |

## 异常处理

| 异常 | 处理 |
|------|------|
| 截图返回 null | 等待 1500ms 后重试 |
| 连续 >5 次恢复失败 | 重置 recoveryAttempt，强制返回 IDLE |
| 恢复到 IDLE 后再次失败 | 由 WorkflowEngine 的 globalFailCount 处理（3次后停止脚本） |
