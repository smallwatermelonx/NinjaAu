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
| 1 | 检测个人悬赏列表页面 | 全屏 | PERSONAL_BOUNTY_LIST_SCREEN | 仅检测 | - | 已进入列表 |
| 2 | 点击等级图标进入详情 | 等级区域裁剪（cropPersonalBountyGradeArea） | 等级图标模板（matchAnyPersonalGradeIcon） | 裁剪区域匹配+坐标补偿 | 1000ms | 匹配到目标等级 |

## 状态跟踪

- `lastMatchMs`: 最后一次匹配成功的时间戳
- `noMatchCount`: 连续无匹配计数（预留，当前未使用）

## 决策逻辑

```
循环扫描（1000ms间隔）:
  ① 匹配 PERSONAL_BOUNTY_LIST_SCREEN
      → activeGrades 为空 → 返回 DONE
      → matchAnyPersonalGradeIcon 匹配等级图标
        → 匹配成功 → 存储 currentBounty → 点击 → 返回 PERSONAL_BOUNTY_DETAIL
        → 未找到 → 等待刷新
  ② 无匹配 → checkNodeTimeout 超时检测
```

## 常量定义

| 常量 | 值 | 说明 |
|------|-----|------|
| NORMAL_INTERVAL_MS | 1000ms | 扫描循环间隔 |

## 异常处理

| 异常 | 处理 |
|------|------|
| 截图返回 null | 等待 1000ms 后重试 |
| 30s 无匹配 | checkNodeTimeout 超时检测 |
| activeGrades 为空 | 返回 DONE，脚本结束 |
