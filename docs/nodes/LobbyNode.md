# LobbyNode - 大厅导航模块规格

## 核心职责

从大厅导航到目标页面：日常悬赏走招募流程，个人悬赏走入口点击。

## 屏幕状态

| 方向 | ScreenState | 说明 |
|------|-------------|------|
| 进入 | IDLE / LOBBY / CHAT | 从初始状态、大厅或聊天页面进入 |
| 退出 | RECRUIT_LIST | 日常悬赏：导航到招募列表 |
| 退出 | PERSONAL_BOUNTY_CENTER | 个人悬赏：点击入口后进入个人悬赏列表 |

## 交互动作

| # | 动作 | 裁剪区域 | 匹配模板 | 坐标/偏移 | 延迟 | 条件 |
|---|------|----------|----------|----------|------|------|
| 1 | 点击聊天图标进入招募 | 左侧 1/10 区域 | CHAT_ICON | 匹配坐标点击 | 500ms | dailyEnabled=true |
| 2 | 点击招募页签 | 上方 1/10 区域 | RECRUIT_TAB | 匹配坐标点击 | 500ms | dailyEnabled=true |
| 3 | 点击个人悬赏入口 | 右侧 50%~82% 宽度、中间 40%~75% 高度 | PERSONAL_BOUNTY_ENTRY | 裁剪区域匹配+坐标补偿 | 1500ms | personalBountyEnabled=true |

## 裁剪区域说明

| 区域名 | 裁剪方法 | 占全屏比例 |
|--------|---------|-----------|
| 左侧 1/10 | `detector.cropLeftTenth(mat)` | 宽10% x 高100% |
| 上方 1/10 | `detector.cropTopTenth(mat)` | 宽100% x 高10% |
| 大厅悬赏令入口 | `detector.cropLobbyPersonalBountyEntry(mat)` | 宽32% x 高35% |

## 决策逻辑

```
进入时记录 roundStartTime（首次进入时）
循环扫描（500ms间隔）:
  → dailyEnabled=true 时:
    → 匹配 CHAT_ICON（左侧1/10）→ 点击 → 继续循环
    → 匹配 RECRUIT_TAB（上方1/10）→ 点击 → 返回 RECRUIT_LIST
  → personalBountyEnabled=true 时:
    → 匹配 PERSONAL_BOUNTY_ENTRY（全屏）→ 点击 → 返回 PERSONAL_BOUNTY_CENTER
  → 无匹配 → 检查超时（30s）→ 抛 NodeTimeoutException
```

## 异常处理

| 异常 | 处理 |
|------|------|
| 截图返回 null | 等待 500ms 后重试 |
| 30s 无匹配 | 抛 NodeTimeoutException，由 WorkflowEngine 进入 RECOVERY |
| Mat 未释放 | finally 块中 release()，防止内存泄漏 |
