# PersonalBountyCenterNode - 个人悬赏中心模块规格

## 核心职责

在个人悬赏列表中扫描并点击用户选择的等级图标进入详情。

## 屏幕状态

| 方向 | ScreenState | 说明 |
|------|-------------|------|
| 进入 | PERSONAL_BOUNTY_CENTER | 由 LobbyNode 点击个人悬赏入口后进入 |
| 退出 | PERSONAL_BOUNTY_DETAIL | 匹配到目标等级图标后点击进入详情 |
| 退出 | DONE | 所有个人悬赏等级完成后退出 |

## 交互动作

| # | 动作 | 裁剪区域 | 匹配模板 | 坐标/偏移 | 延迟 | 条件 |
|---|------|----------|----------|----------|------|------|
| 1 | 点击确认按钮关闭弹窗 | 全屏 | CONFIRM_BUTTON | 匹配坐标点击 | 800ms | 弹窗出现（最高优先级） |
| 2 | 检测个人悬赏列表页面 | 全屏 | PERSONAL_BOUNTY_LIST_SCREEN | 仅检测 | - | 已进入列表 |
| 3 | 点击等级图标进入详情 | 全屏 | 等级图标模板（matchAnyPersonalGradeIcon） | 匹配坐标点击 | 1000ms | 匹配到目标等级 |
| 4 | 点击返回按钮（兜底） | 全屏 | BACK_BUTTON | 匹配坐标点击 | 800ms | 连续15s无匹配 |

## 状态跟踪

- `lastMatchMs`: 最后一次匹配成功的时间戳
- `noMatchCount`: 连续无匹配计数

## 决策逻辑

```
循环扫描（1000ms间隔）:
  ① 匹配 CONFIRM_BUTTON → 点击关闭弹窗（最高优先级）
  ② 匹配 PERSONAL_BOUNTY_LIST_SCREEN
      → activeGrades 为空 → 返回 DONE
      → matchAnyPersonalGradeIcon 匹配等级图标
        → 匹配成功 → 存储 currentBounty → 点击 → 返回 PERSONAL_BOUNTY_DETAIL
        → 未找到 → 等待刷新
  ③ 无匹配 → noMatchCount++
  ④ 连续 15s 无匹配 → 点击 BACK_BUTTON 尝试返回
  ⑤ 30s 无匹配 → checkNodeTimeout 超时检测
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
| 30s 无匹配 | checkNodeTimeout 超时检测 |
| 连续 15s 无匹配 | 点击 BACK_BUTTON 尝试返回 |
| activeGrades 为空 | 返回 DONE，脚本结束 |
