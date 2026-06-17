# PersonalBountyDetailNode - 个人悬赏详情模块规格

## 屏幕状态

| 方向 | ScreenState | 说明 |
|------|-------------|------|
| 进入 | PERSONAL_BOUNTY_DETAIL_SCREEN | 从 PersonalBountyCenterNode 点击等级后进入 |
| 退出 | BATTLE_LOADING | 点击出发按钮后进入战斗加载 |
| 退出 | PERSONAL_BOUNTY_CENTER | 点击返回按钮回到列表 |

## 交互动作

| # | 动作 | 裁剪区域 | 匹配模板 | 坐标/偏移 | 延迟 | 条件 |
|---|------|----------|----------|----------|------|------|
| 1 | 点击出发按钮 | 全屏 | PERSONAL_BOUNTY_GO | 匹配坐标点击 | 2000ms | 出发按钮出现 |
| 2 | 点击发送消息按钮 | 全屏 | PERSONAL_BOUNTY_SEND_MSG | 匹配坐标点击 | 3000ms | 消息按钮出现 |
| 3 | 点击右下角确认 | 底部 85%宽 92%高 | - | 固定比例坐标 | 1500ms | 发送消息后 |
| 4 | 点击详情页入口 | 全屏 | PERSONAL_BOUNTY_DETAIL_SCREEN | 匹配坐标点击 | 1500ms | 首次进入 |
| 5 | 点击返回按钮 | 全屏 | BACK_BUTTON | 匹配坐标点击 | 500ms | 需要退出时 |

## 坐标计算

| 坐标点 | X 比例 | Y 比例 | 说明 |
|--------|--------|--------|------|
| 右下角确认 | 0.85 | 0.92 | 发送消息后的确认按钮 |

## 决策逻辑

```
循环扫描（1000ms间隔）:
  → 匹配 BATTLE_LOADING → 返回 BATTLE_LOADING
  → 匹配 PERSONAL_BOUNTY_GO → 点击出发 → 等待 2s
  → 匹配 PERSONAL_BOUNTY_SEND_MSG → 点击发送 → 等待 3s → 点击右下角确认 → 等待 1.5s
  → 匹配 PERSONAL_BOUNTY_DETAIL_SCREEN（首次）→ 点击进入 → 设置 clickedEntry=true
  → 匹配 BACK_BUTTON → 点击返回 → 返回 PERSONAL_BOUNTY_CENTER
  → 匹配 CHAT_ICON → 异常退出，返回 PERSONAL_BOUNTY_CENTER
  → 无匹配 → 超时检测
```

## 异常处理

| 异常 | 处理 |
|------|------|
| 截图返回 null | 等待 1000ms 后重试 |
| 30s 无匹配 | 抛 NodeTimeoutException |
| 检测到 CHAT_ICON | 异常退出，返回 PERSONAL_BOUNTY_CENTER |
