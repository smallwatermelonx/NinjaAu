# RecoveryNode - 异常恢复模块规格

## 屏幕状态

| 方向 | ScreenState | 说明 |
|------|-------------|------|
| 进入 | 任意（异常状态） | 从任何节点超时或异常后进入 |
| 退出 | IDLE / 各正常阶段 | 根据识别到的页面路由到对应节点 |

## 恢复策略（按优先级）

### 策略1：识别当前页面 → 路由到正确节点

| 识别到的页面 | 目标 Phase | 裁剪区域 | 说明 |
|-------------|-----------|---------|------|
| SETTLEMENT_POPUP | SETTLEMENT | 全屏 | 在结算中，继续领奖 |
| CONFIRM_BUTTON | SETTLEMENT | 底部中间 1/5（cropBottomMiddleFifth） | 在结算中，继续领奖 |
| BATTLE_LOADING | BATTLE_LOADING | 上方 1/4（cropTopQuarter） | 在加载中，等待 |
| READY_BUTTON | BOUNTY_DETAIL | 上方 1/8 | 在详情页，继续组队 |
| SLIDE_BUTTON | FIGHT | 左半边下方 1/4（cropBottomLeftQuarter） | 在战斗中，继续战斗 |
| JUMP_BUTTON | FIGHT | 右半边下方 1/4（cropBottomRightQuarter） | 在战斗中，继续战斗 |
| PERSONAL_BOUNTY_LIST_SCREEN | PERSONAL_BOUNTY_CENTER | 全屏 | 在个人悬赏列表 |
| PERSONAL_BOUNTY_DETAIL_SCREEN | PERSONAL_BOUNTY_DETAIL | 右侧 55%~82%、下方 82%~98%（cropPersonalBountyTeamInvite） | 在个人悬赏详情 |
| PERSONAL_BOUNTY_GO | PERSONAL_BOUNTY_DETAIL | 右侧 74%~98%、下方 82%~98%（cropPersonalBountyGo） | 在个人悬赏详情（出发按钮） |
| RECRUIT_LIST_SCREEN | RECRUIT_LIST | 全屏 | 在招募列表，继续扫描 |
| CHAT_ICON | LOBBY | 左侧 1/10（cropLeftTenth） | 在大厅，重新导航 |

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
| 1 | 截图识别当前页面 | 各裁剪区域 | 各页面标识模板 | 裁剪区域匹配+坐标补偿 | 1500ms | 恢复循环 |
| 2 | 关闭弹窗 | 底部中间/右下半 | CONFIRM_BUTTON / EXIT_CONFIRM | 裁剪区域匹配+坐标补偿 | 800ms | 检测到弹窗 |
| 3 | 点击返回 | 全屏 | BACK_BUTTON | 匹配坐标点击 | 1000ms | 策略3 |

## 裁剪区域说明

| 区域名 | 裁剪方法 | 占全屏比例 | 参考节点 |
|--------|---------|-----------|---------|
| 确认按钮 | `detector.cropBottomMiddleFifth(mat)` | 宽33% x 高20%（底部中间） | BountyDetailNode |
| 战斗加载 | `detector.cropTopQuarter(mat)` | 宽100% x 高25%（上方） | BattleLoadingNode |
| 准备按钮 | `Mat(mat, Rect(0, 0, w, h/8))` | 宽100% x 高12.5%（上方） | BountyDetailNode |
| 滑铲按钮 | `detector.cropBottomLeftQuarter(mat)` | 宽50% x 高25%（左下） | FightNode |
| 跳跃按钮 | `detector.cropBottomRightQuarter(mat)` | 宽50% x 高25%（右下） | FightNode |
| 组队邀请 | `detector.cropPersonalBountyTeamInvite(mat)` | 宽27% x 高16%（右下） | PersonalBountyDetailNode |
| 出发按钮 | `detector.cropPersonalBountyGo(mat)` | 宽24% x 高16%（右下） | PersonalBountyDetailNode |
| 大厅聊天 | `detector.cropLeftTenth(mat)` | 宽10% x 高100%（左侧） | LobbyNode |

## 决策逻辑

```
recoveryAttempt++

if recoveryAttempt > MAX_RECOVERY_ATTEMPTS (5):
  → 强制重置，返回 IDLE（重新导航）

截图 → 识别当前页面（使用裁剪匹配）:
  → SETTLEMENT_POPUP / CONFIRM_BUTTON → SETTLEMENT
  → BATTLE_LOADING（上方1/4裁剪）→ BATTLE_LOADING
  → READY_BUTTON（上方1/8裁剪）→ BOUNTY_DETAIL
  → SLIDE_BUTTON / JUMP_BUTTON（下方裁剪）→ FIGHT
  → PERSONAL_BOUNTY_LIST_SCREEN → PERSONAL_BOUNTY_CENTER
  → PERSONAL_BOUNTY_DETAIL_SCREEN / PERSONAL_BOUNTY_GO → PERSONAL_BOUNTY_DETAIL
  → RECRUIT_LIST_SCREEN → RECRUIT_LIST
  → CHAT_ICON（左侧1/10裁剪）→ LOBBY
  → 未识别 → 尝试关闭弹窗 → 点击返回按钮
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
