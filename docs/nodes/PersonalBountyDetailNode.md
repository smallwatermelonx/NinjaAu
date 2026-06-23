# PersonalBountyDetailNode - 个人悬赏详情模块规格

## 核心职责

处理个人悬赏详情页面的交互流程：点击组队邀请 → 发送消息 → 关闭弹窗 → 等人加入 → 点击出发。

## 屏幕状态

| 方向 | ScreenState | 说明 |
|------|-------------|------|
| 进入 | PERSONAL_BOUNTY_DETAIL | 从 PersonalBountyCenterNode 点击等级后进入 |
| 退出 | BATTLE_LOADING | 点击出发按钮后进入战斗加载 |

## 交互动作

| # | 动作 | 裁剪区域 | 匹配模板 | 坐标/偏移 | 延迟 | 条件 |
|---|------|----------|----------|----------|------|------|
| 1 | 点击出发按钮 | 右侧 74%~98% 宽度、下方 82%~98% 高度 | PERSONAL_BOUNTY_GO | 裁剪区域匹配+坐标补偿 | 500ms | 出发按钮出现（最高优先级） |
| 2 | 点击组队邀请按钮 | 全屏 | PERSONAL_BOUNTY_DETAIL_SCREEN | 匹配坐标点击 | - | 首次进入（clickedEntry=false） |
| 3 | 点击发送消息按钮 | 中间 30%~70% 宽度、下方 72%~95% 高度 | PERSONAL_BOUNTY_SEND_MSG | 裁剪区域匹配+坐标补偿 | 500ms | 弹窗出现（msgSent=false） |
| 4 | 点击空白处关闭弹窗 | 固定比例 | - | (0.85, 0.5) | 1000ms | 发送消息后立即执行 |

## 裁剪区域说明

| 区域名 | 裁剪方法 | 占全屏比例 |
|--------|---------|-----------|
| 出发按钮 | `detector.cropPersonalBountyGo(mat)` | 宽24% x 高16% |
| 发送消息按钮 | `detector.cropPersonalBountySendMessage(mat)` | 宽40% x 高23% |

## 状态跟踪

- `clickedEntry`: 标记是否已点击组队邀请按钮（防止重复点击）
- `msgSent`: 标记是否已点击发送消息（防止重复发送）

## 决策逻辑

```
循环扫描（500ms间隔）:
  ① 出发按钮（最高优先级）→ 裁剪匹配 → 点击出发 → 等待 3000ms → 返回 BATTLE_LOADING
  ② 组队邀请按钮（clickedEntry=false）→ 点击进入 → 设置 clickedEntry=true
  ③ 发送消息按钮（msgSent=false）→ 裁剪匹配 → 点击发送 → 设置 msgSent=true
     → 等待 500ms → 点击空白处(0.85, 0.5)关闭弹窗
  ④ 无匹配 → 超时检测（30s）→ 点击返回按钮 → 等待 1s → 裁剪匹配确认按钮 → 点击确认
     → 返回 PERSONAL_BOUNTY_CENTER
```

## 流程说明

1. 进入详情页 → 检测到组队邀请按钮 → 点击进入
2. 弹窗出现 → 检测到发送消息按钮 → 点击发送
3. 发送后立即点击弹窗右侧空白处关闭弹窗
4. 等待队友加入 → 出发按钮出现
5. 出发按钮为最高优先级，一出现就点击
6. 点击出发后返回 BATTLE_LOADING

## 常量定义

| 常量 | 值 | 说明 |
|------|-----|------|
| NORMAL_INTERVAL_MS | 500ms | 扫描循环间隔 |

## 异常处理

| 异常 | 处理 |
|------|------|
| 截图返回 null | 等待 500ms 后重试 |
| 30s 无匹配 | 正常业务逻辑退出：点击返回按钮 → 确认弹窗 → 返回 PERSONAL_BOUNTY_CENTER |
