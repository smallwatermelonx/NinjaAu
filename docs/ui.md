# UI 模块规格 - 悬浮窗/HUD/Toast/配置面板

## 悬浮窗系统

### 窗口架构

悬浮窗由 `FloatingWindowService`（前台 Service）管理，包含三层独立窗口：

| 层 | 类型 | 触摸行为 | 说明 |
|----|------|---------|------|
| 悬浮球+菜单 | `TYPE_APPLICATION_OVERLAY` | 可触摸 | 主控制面板 |
| HUD 进度 | `TYPE_APPLICATION_OVERLAY` + `NOT_TOUCHABLE` | 点击穿透 | 右上角进度覆盖层 |
| Toast 提示 | `TYPE_APPLICATION_OVERLAY` + `NOT_TOUCHABLE` | 点击穿透 | 中上方页面事件提示 |

### 前台服务

| 属性 | 值 | 说明 |
|------|-----|------|
| Channel ID | `FloatingWindowServiceChannel` | 通知渠道 |
| Notification ID | 102 | 前台通知 ID |
| 通知标题 | "NinjaAu 悬浮窗活跃中" | 低优先级通知 |
| 服务类型 | `FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION` | Android Q+ 截图类型 |

启动时检查 MediaProjection 权限，失败则 Toast 提示并 `stopSelf()`。

### 广播接收

| Action | 行为 |
|--------|------|
| `Constant.HIDE_FLOATING_WINDOW` | 移除所有窗口（悬浮球+HUD+Toast） |
| `Constant.SHOW_FLOATING_WINDOW` | 恢复所有窗口 |

---

## 悬浮球

### 基础属性

| 属性 | 值 | 说明 |
|------|-----|------|
| 尺寸 | 80x80dp | `FrameLayout`，圆角背景 `float_ball_bg` |
| 初始位置 | `x=0, y=statusBar+50px` | 屏幕左侧边缘 |
| 窗口锚点 | `Gravity.TOP \| Gravity.START` | 坐标系原点在屏幕左上角 |
| 窗口类型 | `TYPE_APPLICATION_OVERLAY` | 悬浮窗权限 |
| 标志位 | `NOT_FOCUSABLE \| LAYOUT_NO_LIMITS \| LAYOUT_IN_SCREEN` | 不拦截输入+可超出屏幕 |

### 拖拽

| 属性 | 值 | 说明 |
|------|-----|------|
| 触发阈值 | dx > 10 或 dy > 10 | 防止误触 |
| Y 轴钳制 | `minY=状态栏高度` ~ `maxY=屏幕高-导航栏-200` | 限制在可见区域 |
| X 轴 | 无限制 | 可拖到屏幕任意位置 |
| 拖拽时 | 同步更新 `ballScreenX = layoutParams.x` | 保持球位置跟踪 |

### 贴边吸附

| 属性 | 值 | 说明 |
|------|-----|------|
| 触发 | `ACTION_UP` 且未移动/未关闭菜单 | 松手时 |
| 目标判断 | 球中心 > 屏幕中心 → 右边缘，否则 → 左边缘 | `isBallOnRight()` |
| 目标 X | 右边缘: `screenWidth - ballWidth`，左边缘: `0` | |
| 动画时长 | 250ms | `OvershootInterpolator(1.0f)` |
| 完成后 | `ballScreenX = targetX` | 更新位置跟踪 |

### 贴边隐藏

| 属性 | 值 | 说明 |
|------|-----|------|
| 触发 | 无操作 5000ms | `AUTO_HIDE_MS` |
| 条件 | 菜单未展开 (`!isExpanded`) | 展开时不隐藏 |
| 隐藏目标 X | 右侧: `screenWidth - VISIBLE_TAB(30px)` | 露出 30px 小标签 |
| | 左侧: `-(ballWidth - VISIBLE_TAB)` | 露出 30px 小标签 |
| 动画时长 | 300ms | `DecelerateInterpolator` |
| 透明度 | alpha → 0.5 | 隐藏后半透明 |
| 点击恢复 | 两步：第一次恢复球，第二次展开菜单 | `restoringFromSide` 标志控制 |

### 恢复目标

| 状态 | 目标 X | 说明 |
|------|--------|------|
| 右侧贴边 | `screenWidth - ballWidth` | 完整显示在右侧 |
| 左侧贴边 | `0` | 完整显示在左侧 |
| 动画时长 | 200ms | 恢复后 alpha → 1.0 |

---

## 双菜单布局

### 结构

根视图为水平 `LinearLayout`，包含三个子视图：

```
[ll_menu_left (GONE)] [fl_main_ball (80dp)] [ll_menu_right (GONE)]
```

### 方向判定

| 条件 | 显示菜单 | 展开方向 |
|------|---------|---------|
| `isBallOnRight() = true` | `ll_menuLeft` | 菜单在球左侧展开 |
| `isBallOnRight() = false` | `ll_menuRight` | 菜单在球右侧展开 |

`isBallOnRight()` = `ballScreenX + ballWidth/2 > screenWidth/2`

### 菜单容器属性

| 属性 | 值 | 说明 |
|------|-----|------|
| 高度 | 80dp | 与球等高 |
| 宽度 | `WRAP_CONTENT` | 由子菜单项撑开 |
| 内边距 | 10dp (左右) | |
| 背景 | 透明 | |

### 菜单项（每侧 4 个，对称布局）

| 顺序 | ID (左/右) | 图标 | 尺寸 | 功能 |
|------|-----------|------|------|------|
| 1 (右侧最远) | `btn_start_left/right` | `ic_media_play` / `ic_media_pause` | 60x60dp | 开始/停止脚本 |
| 2 | `btn_bounty_left/right` | `ic_menu_agenda` | 60x60dp | 打开悬赏等级选择 |
| 3 | `iv_close_left/right` | `ic_lock_power_off` | 60x60dp | 关闭服务 |
| 4 (左侧最远) | `btn_test_left/right` | `ic_menu_search` | 60x60dp | 模板测试 |

每个菜单项为 `FrameLayout`，背景 `float_btn_bg`，内含居中 30x30dp `ImageView`，间距 10dp。

### 展开动画

| 属性 | 值 | 说明 |
|------|-----|------|
| 方向 | 球在右侧 → 从右向左滑入 | `child.translationX = +slidePx` |
| | 球在左侧 → 从左向右滑入 | `child.translationX = -slidePx` |
| slidePx | `180dp` | 滑入距离 |
| 动画时长 | 300ms (`ANIM_DURATION`) | |
| 插值器 | `DecelerateInterpolator(2.5f)` | |
| 逐项延迟 | 60ms × index | 第 i 个菜单项延迟 i×60ms |
| 属性 | `translationX` + `alpha` | 从偏移+透明 → 原位+不透明 |

### 收起动画

| 属性 | 值 | 说明 |
|------|-----|------|
| 动画时长 | 150ms (`ANIM_DURATION / 2`) | 比展开快 |
| 插值器 | `AccelerateInterpolator` | |
| 方向 | 与展开相反 | 球在右侧 → 向右滑出 |
| 完成后 | `visibility = GONE` + 重置 `translationX/alpha` | |
| 根视图 | `updateMenuOffset()` 回收偏移 | |

### 菜单偏移计算

当菜单展开且球在右侧时，根视图需要左移菜单宽度，防止菜单超出屏幕：

```
targetX = calcRootX(ballScreenX, ballWidth, screenWidth, menuVisible, menuWidth)
```

| 条件 | targetX |
|------|---------|
| 菜单展开 + 球在右侧 | `ballScreenX - menuWidth` |
| 其他 | `ballScreenX` |

---

## 菜单功能

### 开始/停止按钮

| 属性 | 值 | 说明 |
|------|-----|------|
| 调用 | `GameManager.toggleScript(context)` | 切换 RUNNING/IDLE |
| 图标切换 | RUNNING → `ic_media_pause`，IDLE → `ic_media_play` | 左右两侧同步更新 |
| 点击时 | 重置自动隐藏计时器 | |

### 悬赏等级选择

| 属性 | 值 | 说明 |
|------|-----|------|
| 弹窗类型 | `AlertDialog` + `setMultiChoiceItems` | 多选列表 |
| 窗口类型 | `TYPE_APPLICATION_OVERLAY` | 悬浮在游戏上方 |
| 列表内容 | `BountyGrade.sorted()` 全部 12 等级 | 格式: `"SS+  (1次)"` |
| 初始状态 | 从 `ScriptConfigRepository.bountyConfigs` 读取已启用等级 | |
| 确认操作 | `ScriptConfigRepository.setBountyConfigs(configs)` | 持久化到 SP |
| 提示 | `ToastUtil.show("已选择: SS+, S, A")` | 显示已选等级名 |

### 关闭按钮

| 属性 | 值 | 说明 |
|------|-----|------|
| 调用 | `GameManager.stopScript()` + `stopSelf()` | 停止脚本并关闭服务 |
| 效果 | 移除所有窗口，释放 MediaProjection | |

### 模板测试

| 属性 | 值 | 说明 |
|------|-----|------|
| 第一步 | 弹窗选择节点 | `SceneDetector.NodeTemplateGroup.entries` 单选列表 |
| 第二步 | 截图 + 运行模板匹配 | `detector.testNodeTemplates(screen, group)` |
| 第三步 | 显示结果弹窗 | 滚动列表，每项显示: 模板名 + 相似度 / 阈值 + 坐标 |
| 结果格式 | `✅ chat_icon 0.923 / 0.800 (150, 230)` | 绿色通过/橙色接近/灰色失败 |
| 统计 | 顶部显示 `通过 8 / 12` | 绿色(全过)/橙色(有失败) |

---

## Toast 覆盖层

### 属性

| 属性 | 值 | 说明 |
|------|-----|------|
| 位置 | 顶部居中 | `Gravity.TOP \| CENTER_HORIZONTAL` |
| Y 偏移 | `statusBar + 140px` | 状态栏下方 |
| 背景 | 圆角矩形 `0xBB222222` | 半透明深色，圆角 24dp |
| 文字 | 白色粗体 16sp | 居中对齐 |
| 内边距 | 32dp (左右) × 14dp (上下) | |
| 触摸 | `NOT_TOUCHABLE` | 点击穿透 |

### 队列机制

| 属性 | 值 | 说明 |
|------|-----|------|
| 队列 | `ConcurrentLinkedQueue<String>` | 线程安全 |
| 显示中标志 | `isToastShowing` | 防止并发显示 |
| 流程 | `offer()` → `processToastQueue()` | 依次处理 |

### 动画时序

```
淡入 (200ms) → 显示 (1500ms) → 淡出 (300ms) → 隐藏 → 处理下一条
```

| 阶段 | 时长 | 说明 |
|------|------|------|
| 淡入 | 200ms | alpha 0→1 |
| 显示 | 1500ms (`TOAST_DURATION_MS`) | 静态显示 |
| 淡出 | 300ms | alpha 1→0 |
| 间隔 | 0ms | 淡出后立即处理下一条 |

---

## HUD 进度显示

HUD 由独立的 `HudManager` 类管理，从 `FloatingWindowService` 提取。

### 显示条件

HUD 仅在以下条件下显示：

| 条件 | 说明 |
|------|------|
| 脚本状态 = RUNNING | 脚本停止时隐藏 |
| 当前页面 = 大厅 或 招募列表 | 其他节点（战斗、结算等）不显示 |

页面判定通过 `pageEvents` 事件驱动：
- 收到 `"大厅"` 事件 → `isOnGameDataPage = true` → 显示 HUD
- 收到 `"招募列表"` 事件 → `isOnGameDataPage = true` → 显示 HUD
- 收到其他页面事件 → `isOnGameDataPage = false` → 隐藏 HUD
- 脚本停止 (IDLE) → 隐藏 HUD

### 属性

| 属性 | 值 | 说明 |
|------|-----|------|
| 位置 | 右上角固定 | `Gravity.TOP \| Gravity.END` |
| X 偏移 | `12dp` | 距右边缘 |
| Y 偏移 | `statusBar + 50dp` | 状态栏下方 |
| 最大宽度 | 屏幕 36% | 防止遮挡过多 |
| 背景 | 圆角矩形 `0xDD1E1E1E` | 半透明深色，圆角 10dp |
| 内边距 | 12dp (左右) × 8dp (上) × 6dp (下) | |
| 透明度 | 0.9 | 显示/隐藏带 200ms 渐变动画 |
| 标题 | "◆ 进度" | 蓝色 `0xFF8AB4F8` 粗体 14sp |
| 标题底距 | 4dp | 与内容行间距 |

### 进度显示

| 属性 | 值 | 说明 |
|------|-----|------|
| 等级显示顺序 | SS+ → SS → S → A → B → C → D → NSS+ → NS → NA | 日常高等级在前 |
| 同组共享计数 | S/S+ 共享 S_GROUP，A/A+ 共享 A_GROUP | 用组内最大值 |
| 目标次数 | 用 `GradeGroup.defaultRuns` | 不用单个等级的 targetRuns |
| 显示格式 | `S  3/5 ✓` | 等级名左对齐+粗体，次数右对齐+等宽 |
| 完成标记 | 绿色 `✓` | `totalCompleted >= target` 时显示 |
| 完成颜色 | 次数绿色 `0xFF4EC9B0` | |
| 未完成颜色 | 次数灰色 `0xFF969696` | |
| 等级名颜色 | 浅灰 `0xFFE0E0E0` | 粗体 |
| 字号 | 13sp | |
| 行间距 | 1dp (上下) | 每行间 |

### 显示示例

```
◆ 进度
SS+  0/1
S    3/5 ✓
A    2/3
B    0/4
```

### 数据来源

`GameManager.bountyProgress: StateFlow<Map<BountyGrade, Pair<Int, Int>>>`

- Key: `BountyGrade`
- Value: `(completed, target)`
- 更新频率: 每次结算领奖后

---

## 配置面板

### Compose UI 布局

| 组件 | 说明 |
|------|------|
| 悬赏等级勾选 | 多选框，勾选要自动完成的等级 |
| 业务线开关 | 每日悬赏/个人悬赏/逆袭事件独立开关 |
| 运行状态显示 | 当前脚本运行状态 |
| 启动/停止按钮 | 控制脚本启停 |

### 配置持久化

- 存储方式：SharedPreferences (`script_config`)
- 配置读写：通过 `ScriptConfigRepository` 统一管理
- 线程安全：使用 StateFlow + snapshot() 模式

---

## 异常处理

| 异常 | 处理 |
|------|------|
| 悬浮窗权限被撤销 | 重新请求权限 |
| MediaProjection 初始化失败 | Toast 提示 + `stopSelf()` |
| 配置读取失败 | 使用默认值 |
| WindowManager 操作异常 | catch + LogUtil.e()，静默处理 |
| Toast 添加失败 | catch + 记录日志 |
