# FightNode - 战斗模块规格

## 屏幕状态

| 方向 | ScreenState | 说明 |
|------|-------------|------|
| 进入 | FIGHT | 从 BattleLoadingNode 加载完成后进入 |
| 退出 | SETTLEMENT | 战斗胜利，进入结算 |
| 退出 | RECOVERY | 异常情况 |

## 交互动作

### 阶段一：下滑阶段

| # | 动作 | 裁剪区域 | 匹配模板 | 坐标/偏移 | 延迟 | 条件 |
|---|------|----------|----------|----------|------|------|
| 1 | 等待开场 | - | - | - | 6000ms | 进入节点后先等6秒 |
| 2 | 点击下滑按钮 | 左下 1/4 区域 | SLIDE_BUTTON | 匹配坐标，Y轴调整到全屏位置 | 500ms | 下滑阶段循环 |
| 3 | 点击血咒技能 | 全屏 | BLOOD_CURSE | 匹配坐标点击 | - | 血咒出现时（仅点击1次） |

### 阶段二：Boss战阶段

| # | 动作 | 裁剪区域 | 匹配模板 | 坐标/偏移 | 延迟 | 条件 |
|---|------|----------|----------|----------|------|------|
| 4 | 检测Lv图标 | 左上 1/8 区域 | LV_ICON | - | 100ms | 快速扫描，Lv出现后进入战斗循环 |
| 5 | 点击跳跃 | 右下 1/4 区域 | JUMP_BUTTON | 匹配坐标点击 | 1000ms | 跳跃按钮出现 |
| 6 | 点击上翻 | 全屏 | SCROLL_UP | 匹配坐标点击 | 1000ms | 上翻按钮出现 |
| 7 | 点击大招 | 左侧 1/6 区域 | ULTIMATE_SKILL | **记住坐标，连点36次** | 240ms/次 | 大招出现时 |
| 8 | 点击武器技能 | 下方 1/4 区域 | WEAPON_SKILL | **记住坐标，连点36次** | 240ms/次 | 武器技能出现时 |

## 裁剪区域说明

| 区域名 | 裁剪方法 | 占全屏比例 | 用途 |
|--------|---------|-----------|------|
| 左下 1/4 | `detector.cropBottomLeftQuarter(mat)` | 宽50% x 高50%（左下） | 下滑按钮 |
| 左上 1/8 | `detector.cropLeftEighth(mat)` | 宽12.5% x 高100%（左侧） | Lv图标检测 |
| 右下 1/4 | `detector.cropBottomRightQuarter(mat)` | 宽50% x 高50%（右下） | 跳跃按钮 |
| 左侧 1/6 | `detector.cropLeftSixth(mat)` | 宽16.7% x 高100% | 大招图标 |
| 下方 1/4 | `detector.cropBottomQuarter(mat)` | 宽100% x 高25%（底部） | 武器技能 |

## 常量定义

| 常量 | 值 | 说明 |
|------|-----|------|
| SLIDE_INTERVAL_MS | 500ms | 下滑阶段扫描间隔 |
| BOSS_DETECT_INTERVAL_MS | 100ms | Boss阶段快速扫描间隔 |
| BOSS_LOOP_INTERVAL_MS | 1000ms | Boss阶段常规循环间隔 |
| MAX_SLIDE_MISS | 3 | 下滑阶段连续无匹配次数，超过后进入Boss阶段 |
| MAX_JUMP_MISS | 3 | 跳跃连续无匹配次数，超过后认为战斗结束 |
| MAX_SKILL_CLICKS | 36 | 大招/武器技能最大连点次数 |
| SKILL_CLICK_INTERVAL_MS | 240ms | 技能连点间隔 |

## 决策逻辑

```
进入 → 等待6秒开场动画

下滑阶段（500ms间隔）:
  → 匹配 SLIDE_BUTTON → 点击，重置miss计数
  → 匹配 BLOOD_CURSE → 点击一次（不重置slideMiss）
  → 连续3次无匹配 → 进入Boss阶段

Boss阶段（100ms快速扫描 → 1000ms常规循环）:
  → 未检测到 Lv → 100ms快速扫描左上1/8
  → Lv出现后：
    → 匹配 JUMP_BUTTON → 点击
    → 匹配 SCROLL_UP → 点击
    → 匹配 ULTIMATE_SKILL → 记住坐标，连点36次（240ms/次）
    → 匹配 WEAPON_SKILL → 记住坐标，连点36次（240ms/次）
    → 匹配 DEFEAT_POPUP → 战斗失败
  → 连续3次未匹配 JUMP_BUTTON + SCROLL_UP → 战斗结束
  → 返回 SETTLEMENT
```

## 技能连点机制

大招和武器技能使用**记住坐标连点**：
1. 第一次匹配到技能图标 → 记住该坐标
2. 在记住的坐标上连续点击36次，每次间隔240ms
3. 连点期间不再做模板匹配，直接盲点
4. 连点结束后恢复常规扫描

## 异常处理

| 异常 | 处理 |
|------|------|
| 截图返回 null | 等待当前间隔后重试 |
| 30s 无匹配 | 抛 NodeTimeoutException |
| 战斗失败（DEFEAT_POPUP） | 进入 DefeatNode（当前为TODO桩） |
