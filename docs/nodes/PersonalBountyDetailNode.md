# PersonalBountyDetailNode - 个人悬赏详情模块规格

## 核心职责

处理个人悬赏详情页面的交互流程：进入详情 → 发送消息 → 确认 → 出发战斗。

## 屏幕状态

| 方向 | ScreenState | 说明 |
|------|-------------|------|
| 进入 | PERSONAL_BOUNTY_DETAIL | 从 PersonalBountyCenterNode 点击等级后进入 |
| 退出 | BATTLE_LOADING | 点击出发按钮后进入战斗加载 |
| 退出 | PERSONAL_BOUNTY_CENTER | 点击返回按钮或异常退出回到列表 |

## 交互动作

| # | 动作 | 裁剪区域 | 匹配模板 | 坐标/偏移 | 延迟 | 条件 |
|---|------|----------|----------|----------|------|------|
| 1 | 检测战斗加载 | 全屏 | BATTLE_LOADING | 仅检测 | - | 出发成功 |
| 2 | 点击出发按钮 | 全屏 | PERSONAL_BOUNTY_GO | 匹配坐标点击 | 2000ms | 出发按钮出现 |
| 3 | 点击发送消息按钮 | 全屏 | PERSONAL_BOUNTY_SEND_MSG | 匹配坐标点击 | 3000ms | 消息按钮出现 |
| 4 | 点击右下角确认 | 固定比例 | - | (0.85, 0.92) | 1500ms | 发送消息后 |
| 5 | 点击详情页入口 | 全屏 | PERSONAL_BOUNTY_DETAIL_SCREEN | 匹配坐标点击 | 1500ms | 首次进入（clickedEntry=false） |
| 6 | 点击返回按钮 | 全屏 | BACK_BUTTON | 匹配坐标点击 | 800ms | 需要退出时 |
| 7 | 大厅聊天图标检测 | 全屏 | CHAT_ICON | 仅检测 | - | 异常退出 |

## 状态跟踪

- `clickedEntry`: 标记是否已点击详情页入口（防止重复点击）

## 坐标计算

| 坐标点 | X 比例 | Y 比例 | 说明 |
|--------|--------|--------|------|
| 右下角确认 | 0.85 | 0.92 | 发送消息后的确认按钮（BOTTOM_RIGHT_CONFIRM_X/Y） |

## 决策逻辑

```
循环扫描（1000ms间隔）:
  ① 匹配 BATTLE_LOADING → 返回 BATTLE_LOADING
  ② 匹配 PERSONAL_BOUNTY_GO → 点击出发 → 等待 2s
  ③ 匹配 PERSONAL_BOUNTY_SEND_MSG → 点击发送 → 等待 3s → 点击右下角确认(0.85, 0.92) → 等待 1.5s
  ④ 匹配 PERSONAL_BOUNTY_DETAIL_SCREEN（clickedEntry=false）→ 点击进入 → 设置 clickedEntry=true
  ⑤ 匹配 BACK_BUTTON → 点击返回 → 返回 PERSONAL_BOUNTY_CENTER
  ⑥ 匹配 CHAT_ICON → 异常退出，返回 PERSONAL_BOUNTY_CENTER
  ⑦ 无匹配 → checkNodeTimeout 超时检测
```

## 常量定义

| 常量 | 值 | 说明 |
|------|-----|------|
| NORMAL_INTERVAL_MS | 1000ms | 扫描循环间隔 |
| BOTTOM_RIGHT_CONFIRM_X | 0.85 | 右下角确认按钮X坐标比例 |
| BOTTOM_RIGHT_CONFIRM_Y | 0.92 | 右下角确认按钮Y坐标比例 |

## 异常处理

| 异常 | 处理 |
|------|------|
| 截图返回 null | 等待 1000ms 后重试 |
| 30s 无匹配 | checkNodeTimeout 超时检测 |
| 检测到 CHAT_ICON | 异常退出，返回 PERSONAL_BOUNTY_CENTER |
| 点击返回按钮 | 返回 PERSONAL_BOUNTY_CENTER |
