# PersonalBountyCenterNode - 个人悬赏中心模块规格

## 屏幕状态

| 方向 | ScreenState | 说明 |
|------|-------------|------|
| 进入 | PERSONAL_BOUNTY_ENTRY | 从大厅检测到个人悬赏入口后进入 |
| 退出 | PERSONAL_BOUNTY_DETAIL | 匹配到目标等级图标后点击进入详情 |
| 退出 | DONE | 所有个人悬赏等级完成后退出 |

## 交互动作

| # | 动作 | 裁剪区域 | 匹配模板 | 坐标/偏移 | 延迟 | 条件 |
|---|------|----------|----------|----------|------|------|
| 1 | 点击确认按钮关闭弹窗 | 全屏 | CONFIRM_BUTTON | 匹配坐标点击 | 500ms | 弹窗出现 |
| 2 | 点击等级图标进入详情 | 全屏 | 等级图标模板 | 匹配坐标点击 | 500ms | 匹配到目标等级 |
| 3 | 点击个人悬赏入口 | 全屏 | PERSONAL_BOUNTY_ENTRY | 匹配坐标点击 | 500ms | 在大厅 |
| 4 | 点击返回按钮 | 全屏 | BACK_BUTTON | 匹配坐标点击 | 500ms | 卡住超时 |

## 决策逻辑

```
循环扫描（1000ms间隔）:
  → 匹配 CONFIRM_BUTTON → 点击关闭弹窗
  → 匹配 PERSONAL_BOUNTY_LIST_SCREEN:
      → activeGrades 为空 → 返回 DONE
      → 匹配等级图标（matchAnyGradeIcon）
        → 匹配成功 → 存储 currentBounty → 点击 → 返回 PERSONAL_BOUNTY_DETAIL
  → 匹配 PERSONAL_BOUNTY_ENTRY → 点击进入列表
  → 匹配 CHAT_ICON → 仅更新 lastMatchMs（确认位置）
  → 无匹配 → 超时检测
  → 连续 15s 无匹配 → 点击返回按钮
```

## 常量定义

| 常量 | 值 | 说明 |
|------|-----|------|
| NORMAL_INTERVAL_MS | 1000ms | 扫描循环间隔 |
| BACK_BUTTON_TIMEOUT_MS | 15000ms | 触发返回按钮的超时阈值 |

## 异常处理

| 异常 | 处理 |
|------|------|
| 截图返回 null | 等待 1000ms 后重试 |
| 30s 无匹配 | 抛 NodeTimeoutException |
| 连续 15s 无匹配 | 点击 BACK_BUTTON 尝试返回 |
