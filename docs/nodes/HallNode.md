# HallNode - 大厅导航模块规格

## 屏幕状态

| 方向 | ScreenState | 说明 |
|------|-------------|------|
| 进入 | LOBBY / 任意状态恢复后 | 从大厅或异常恢复后进入 |
| 退出 | RECRUIT_LIST_SCREEN | 匹配到招募列表页面后切换到 BountyListNode |

## 交互动作

| # | 动作 | 裁剪区域 | 匹配模板 | 坐标/偏移 | 延迟 | 条件 |
|---|------|----------|----------|----------|------|------|
| 1 | 点击聊天图标进入招募 | 左侧 1/10 区域 | CHAT_ICON | 匹配坐标点击 | 500ms | 当前在大厅 |
| 2 | 点击招募页签 | 上方 1/10 区域 | RECRUIT_TAB | 匹配坐标点击 | 500ms | 在招募子页面 |
| 3 | 点击招募列表页面 | 上方 1/10 区域 | RECRUIT_LIST_SCREEN | 匹配坐标点击 | 500ms | 进入招募列表 |

## 裁剪区域说明

| 区域名 | 裁剪方法 | 占全屏比例 |
|--------|---------|-----------|
| 左侧 1/10 | `detector.cropLeftTenth(mat)` | 宽10% x 高100% |
| 上方 1/10 | `detector.cropTopTenth(mat)` | 宽100% x 高10% |

## 决策逻辑

```
循环扫描（500ms间隔）:
  → 匹配 CHAT_ICON（左侧1/10）→ 点击 → 继续循环
  → 匹配 RECRUIT_TAB（上方1/10）→ 点击 → 继续循环
  → 匹配 RECRUIT_LIST_SCREEN（上方1/10）→ 返回 BOUNTY_LIST
  → 无匹配 → 检查超时（30s）→ 抛 NodeTimeoutException
```

## 异常处理

| 异常 | 处理 |
|------|------|
| 截图返回 null | 等待 500ms 后重试 |
| 30s 无匹配 | 抛 NodeTimeoutException，由 WorkflowEngine 进入 RECOVERY |
| Mat 未释放 | finally 块中 release()，防止内存泄漏 |
