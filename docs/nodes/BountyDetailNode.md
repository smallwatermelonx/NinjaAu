# BountyDetailNode - 悬赏详情/组队模块规格

## 屏幕状态

| 方向 | ScreenState | 说明 |
|------|-------------|------|
| 进入 | READY_BUTTON | 从 BountyListNode 点击加入后进入 |
| 退出 | BATTLE_LOADING | 全员准备后进入战斗加载 |
| 退出 | LOBBY | 退出队伍/达上限后回到大厅 |

## 交互动作

| # | 动作 | 裁剪区域 | 匹配模板 | 坐标/偏移 | 延迟 | 条件 |
|---|------|----------|----------|----------|------|------|
| 1 | 点击准备按钮 | 全屏 | READY_BUTTON | 匹配坐标点击 | 1000ms | 准备按钮出现 |
| 2 | 确认退出队伍 | 全屏 | EXIT_CONFIRM | 匹配坐标点击 | 500ms | 退出确认弹窗 |
| 3 | 点击返回按钮 | 全屏 | BACK_BUTTON | 匹配坐标点击 | 500ms | 需要退出时 |
| 4 | 检测已达上限 | 全屏 | DAILY_LIMIT | - | - | 今日次数用完 |
| 5 | 处理组队邀请弹窗 | 全屏 | TEAM_INVITATION / INVITE_REJECT | 点击拒绝 | 500ms | 邀请弹窗出现 |

## 决策逻辑

```
循环扫描（500ms间隔）:
  → 匹配 EXIT_CONFIRM → 点击确认退出
  → 匹配 DAILY_LIMIT → 退出队伍，回到大厅
  → 匹配 READY_BUTTON → 等级校验通过后点击准备
    → 等级校验失败 → 退出队伍
  → 匹配 BATTLE_LOADING → 返回 BATTLE_LOADING
  → 匹配 CHAT_ICON / RECRUIT_TAB → 已回到大厅，返回 LOBBY
  → 匹配 TEAM_INVITATION → 点击拒绝邀请
  → 无匹配 → 检查超时（30s）→ 抛 NodeTimeoutException
```

## 等级校验逻辑

点击准备前需校验当前队伍等级是否在 `activeGrades` 范围内：
- 匹配等级图标 → 获取等级信息
- 等级不在勾选范围内 → 退出队伍
- 等级在范围内 → 点击准备

## 战斗等待

点击准备后启动 30s 倒计时：
- 倒计时内匹配到 BATTLE_LOADING → 进入战斗加载
- 倒计时结束仍未开始 → 超时处理

## 异常处理

| 异常 | 处理 |
|------|------|
| 截图返回 null | 等待 500ms 后重试 |
| 30s 无匹配 | 抛 NodeTimeoutException |
| 组队邀请弹窗 | 拒绝邀请，继续当前流程 |
| 等级不符 | 退出队伍，回到招募列表 |
