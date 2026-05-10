# NinjaAu 架构设计文档

> 忍三自动化脚本 — 通过图像识别自动执行悬赏任务流程
> 版本 v2.0.0 · 2026-05

---

## 目录

1. [项目概述](#1-项目概述)
2. [总体架构](#2-总体架构)
3. [模块目录](#3-模块目录)
4. [数据流](#4-数据流)
5. [状态机](#5-状态机)
6. [界面检测流程](#6-界面检测流程)
7. [当前缺陷清单](#7-当前缺陷清单)
8. [代码质量评估](#8-代码质量评估)
9. [建议重构路线图](#9-建议重构路线图)

---

## 1. 项目概述

### 1.1 业务目标

Android 辅助工具，通过无障碍服务 + MediaProjection 截图 + OpenCV 模板匹配，自动完成游戏"忍者必须死3"中的悬赏任务流程。

### 1.2 用户使用路径

```
安装 → 开启无障碍服务 → 授权悬浮窗 → 授权截图权限 → 悬浮窗出现
    → 勾选悬赏等级 → 点击播放 → 脚本自动识别和执行 → 完成
```

### 1.3 技术栈

| 层 | 技术 |
|--|--|
| 语言 | Kotlin |
| UI | Jetpack Compose + Material3 |
| 截图 | MediaProjection + VirtualDisplay + ImageReader |
| 触控 | AccessibilityService (GestureDescription) |
| 图像识别 | OpenCV (Imgproc.matchTemplate, TM_CCOEFF_NORMED) |
| 异步 | Kotlin Coroutines + StateFlow |
| 构建 | Gradle KTS + AGP |
| 最低支持 | Android 9 (API 28) |

---

## 2. 总体架构

```
┌─────────────────────────────────────────────────────────────┐
│                        NinjaApp                            │
│                    (OpenCV + Log 初始化)                      │
└─────────────────────────────────────────────────────────────┘
                             │
                    ┌────────┴────────┐
                    │  MainActivity   │
                    │ (Compose 宿主)   │
                    └────────┬────────┘
                             │
              ┌──────────────┴──────────────┐
              │     NinjaScriptMainUI       │
              │  启动Tab  |  关于Tab        │
              └──────────────┬──────────────┘
                             │
                    ┌────────┴────────┐
                    │  GameManager    │  ← 全局单例状态机
                    │  IDLE → RUNNING │
                    │       → PAUSED  │
                    └────────┬────────┘
                             │ 启动
                    ┌────────┴────────┐
                    │   ScriptEngine  │  ← 主循环引擎
                    │  ┌────────────┐ │
                    │  │ 截图→检测→执行│ │  循环
                    │  └────────────┘ │
                    └────────┬────────┘
                             │
          ┌──────────────────┼──────────────────┐
          ▼                  ▼                  ▼
   ┌─────────────┐  ┌──────────────┐  ┌──────────────┐
   │ ScreenCapture│  │ ScreenDetector│  │BountyExecutor│
   │ (MediaProj.) │  │ (OpenCV匹配)  │  │ (点击/等待)  │
   └─────────────┘  └──────────────┘  └──────────────┘
          │                 │                  │
          ▼                 ▼                  ▼
   ┌─────────────┐  ┌──────────────┐  ┌──────────────────┐
   │PermissionMgr│  │TemplateMatcher│  │NinjaAccessibility│
   │             │  │ (OpenCV封装)  │  │  Service (手势)  │
   └─────────────┘  └──────────────┘  └──────────────────┘
```

### 2.1 服务层架构

```
┌─────────────────────────────────────────────────┐
│              FloatingWindowService              │
│  ┌──────────┐  ┌──────────┐  ┌───────────────┐ │
│  │ 悬浮球拖拽│  │菜单展开/隐藏│  │悬赏选择弹窗   │ │
│  │ 边缘吸附  │  │播放/暂停  │  │(待完善)      │ │
│  └──────────┘  └──────────┘  └───────────────┘ │
│  前台服务 · foregroundServiceType=mediaProjection │
└─────────────────────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────────────────┐
│           CapturePermissionActivity             │
│    透明Activity · 请求 MediaProjection 授权      │
│    授权成功后自动启动 FloatingWindowService       │
└─────────────────────────────────────────────────┘
```

---

## 3. 模块目录

### 3.1 `model/` — 数据模型层

| 文件 | 职责 | 问题 |
|--|--|--|
| `ActionResult<T>` | 统一操作结果 | 通用但泛型在状态流转中类型信息丢失 |
| `BountyConfig` | 悬赏等级配置 + 预设列表 | `gradeIcon` 路径 schema 未明确，与 ScreenState 的 templateConfig 无关联 |
| `ScreenState` | 界面状态枚举 + 模板配置 | **严重问题**: enum 内嵌了 `TemplateConfig`，与 `UIMap` 重复定义了两套同样的配置 |

### 3.2 `core/` — 核心引擎

| 文件 | 职责 | 依赖 |
|--|--|--|
| `GameManager` | 状态机 (IDLE/RUNNING/PAUSED) + 协程生命周期 | PermissionManager, ScriptEngine |
| `ScriptEngine` | 主循环: 截图→检测→执行→循环 | ScreenCapture, ScreenDetector, BountyExecutor |

### 3.3 `core/capture/` — 截图模块

| 文件 | 职责 |
|--|--|
| `ScreenCapture` | 单例，管理 VirtualDisplay + ImageReader，输出 Bitmap |
| `CapturePermissionActivity` | 透明 Activity 申请 MediaProjection 授权 |

### 3.4 `core/recognition/` — 识别与执行

| 文件 | 职责 |
|--|--|
| `TemplateMatcher` | OpenCV 单次模板匹配封装 (TM_CCOEFF_NORMED) |
| `ScreenDetector` | 一次截图 → 检测 9 种界面状态（逆序优先） |
| `BountyExecutor` | 根据检测结果执行点击/等待/验证等动作序列 |

### 3.5 `core/floating/` — 悬浮窗

| 文件 | 职责 |
|--|--|
| `FloatingWindowService` | 前台服务: 悬浮球拖拽/吸附/菜单/播放控制 |

### 3.6 `core/accessibility/` — 无障碍服务

| 文件 | 职责 |
|--|--|
| `NinjaAccessibilityService` | 单例，通过 `dispatchGesture` 执行坐标点击 |

### 3.7 `core/util/` — 工具层

| 文件 | 职责 | 使用状态 |
|--|--|--|
| `PermissionManager` | MediaProjection 生命周期 + 权限检查 | ✅ 活跃 |
| `AppControl` | 启动脚本 + 检查游戏进程 | ❌ **零引用** |
| `AssetUtil` | 从 assets 加载 Bitmap | ✅ 活跃 |
| `Constant` | 全局常量 | ✅ 活跃 |
| `LogUtil` | 日志封装 | ✅ 活跃 |
| `OpenCVUtil` | Bitmap↔Mat 转换 | ✅ 活跃 |
| `ToastUtil` | Toast 封装 | ⚠️ 仅在 FloatingWindowService 中引用 |

### 3.8 `ui/` — 界面层

| 文件 | 职责 |
|--|--|
| `NinjaScriptMainUI` | 主 Compose 界面: 权限引导 + 启动/断开 |
| `floating/BountyCheckList` | 悬赏勾选列表 Compose 组件 |

### 3.9 `core/uimap/` — 模板配置 (待移除)

| 文件 | 职责 |
|--|--|
| `UIMap` | TemplateConfig 集合，嵌套 UI 屏幕分组 |

> 注意: `UIMap` 中定义的配置与 `model/ScreenState.kt` 中嵌入的配置完全重复。这是一个待消除的冗余。

---

## 4. 数据流

### 4.1 权限启动流

```
用户点 [Link Start]
    │
    ├─→ 无障碍服务未开启? → 跳转系统设置
    ├─→ 悬浮窗权限未开启? → 跳转设置
    ├─→ 截图权限未授权? → CapturePermissionActivity
    │       └─→ 授权成功 → 保存数据 → 启动 FloatingWindowService
    │                       → 用户再次点 Link Start → 进入 else
    └─→ 全部已授权 → 启动 FloatingWindowService
```

### 4.2 脚本循环流

```
用户点悬浮窗 [播放]
    │
    ▼
GameManager.startScript()
    │
    ▼  (协程)
ScriptEngine.runLoop()
    │
    ┌──────────────────────────────────────────┐
    │  while(isActive && state == RUNNING) {   │
    │    1. ScreenCapture.capture() → Bitmap   │
    │    2. ScreenDetector.detect(Bitmap)      │
    │       → DetectionResult(state, coord)    │
    │    3. BountyExecutor.execute(detection)  │
    │       → ActionResult(nextState)          │
    │    4. delay(1000)                        │
    │  }                                       │
    └──────────────────────────────────────────┘
```

### 4.3 界面检测流

```
ScreenDetector.detect(bitmap)
    │
    ├─→ matchScreen(BACK_BUTTON)  → 命中? → 返回
    ├─→ matchScreen(CONFIRM_BTN)  → 命中? → 返回
    ├─→ matchScreen(REWARD_POPUP) → 命中? → 返回
    ├─→ matchScreen(BATTLE_WARNING)→ 命中? → 返回
    ├─→ matchScreen(SLIDE_BUTTON) → 命中? → 返回
    ├─→ matchScreen(READY_BTN)    → 命中? → 返回
    ├─→ matchScreen(JOIN_TEAM)    → 命中? → 返回
    ├─→ matchScreen(RECRUIT_TAB)  → 命中? → 返回
    ├─→ matchScreen(HALL_CHAT)    → 命中? → 返回
    └─→ UNKNOWN

matchScreen 内部:
    loadBitmapFromAssets(template) → TemplateMatcher.match(screen, template, threshold)
    → MatchResult(isMatched, centerX, centerY)
```

### 4.4 动作执行流

```
BountyExecutor.execute(detection)
    │
    ├─ HALL_CHAT     → clickCoord → wait 2s → RECRUIT_TAB
    ├─ RECRUIT_TAB   → clickCoord → wait 1s → JOIN_TEAM
    ├─ JOIN_TEAM     → 循环检测: 点击加入 → 检查准备按钮 → READY_BTN
    ├─ READY_BTN     → clickCoord → wait 1s → SLIDE_BUTTON
    ├─ SLIDE_BUTTON  → 检测30s → 持续点击5s → BATTLE_WARNING
    ├─ BATTLE_WARNING → 检测WARNING → wait 3s → 点击目标24次 → REWARD_POPUP
    ├─ REWARD_POPUP  → 循环点击关闭 → 验证关闭 → CONFIRM_BTN
    ├─ CONFIRM_BTN   → clickCoord → HALL_CHAT (完成)
    ├─ BACK_BUTTON   → clickCoord → wait 2s → HALL_CHAT
    └─ UNKNOWN       → 返回失败
```

---

## 5. 状态机

```
                  ┌─────────────────────────────┐
                  │                             │
    toggleScript  ▼   startScript               │
  ┌─────────┐ ─────→ ┌──────────┐              │
  │  IDLE   │         │ RUNNING  │ ───→ pause   │
  └─────────┘         └──────────┘              │
       ▲                  │    ▲                │
       │    stopScript    │    │                │
       │                  ▼    │   resume       │
       │              ┌──────────┐              │
       │              │  PAUSED  │ ──────────────┘
       │              └──────────┘
       │                  
       └─────────────────┘
         (异常/完成时自动回到 IDLE)
```

状态边界:
- **IDLE**: 无协程运行，MediaProjection 已释放
- **RUNNING**: 主协程运行中，ScriptEngine 循环执行
- **PAUSED**: 主协程已取消，MediaProjection 暂停（保留授权数据）

---

## 6. 界面检测流程

### 6.1 检测顺序（逆序）

检测采用逆序策略：从流程末尾向开头检测，避免前序节点误匹配。

原因：游戏后期界面（如奖励弹窗）的 UI 元素更独特、更稳定；大厅 Chat 图标等早期节点容易和其他界面混淆。

### 6.2 匹配参数

| 界面 | 模板路径 | 阈值 | 备注 |
|--|--|--|--|
| HALL_CHAT | `templates/hall/hall_chat.png` | 0.8 | |
| RECRUIT_TAB | `templates/chat/team_recruit.png` | 0.8 | |
| JOIN_TEAM | `templates/bounty/chatbox/join_team.png` | 0.8 | |
| READY_BTN | `templates/bounty/preparation/prepare.png` | 0.8 | |
| SLIDE_BUTTON | `templates/fight/decline.png` | 0.8 | |
| BATTLE_WARNING | `templates/fight/warning.png` | 0.7 | 较低阈值 |
| BATTLE_TARGET | `templates/fight/ninjutsu.png` | 0.6 | 最低阈值 |
| REWARD_POPUP | `templates/bounty/settlement/blank_space.png` | 0.8 | |
| CONFIRM_BTN | `templates/bounty/settlement/confirm.png` | 0.8 | |
| BACK_BUTTON | `templates/other/backward.png` | 0.8 | |

### 6.3 OpenCV 匹配策略

- 方法: `Imgproc.TM_CCOEFF_NORMED`
- 相似度范围: [0, 1]，越接近 1 越匹配
- 匹配条件: `maxSimilarity >= threshold`
- 坐标计算: 返回匹配区域中心点 (centerX, centerY)

---

## 7. 当前缺陷清单

按严重度排列：

### 🔴 P0 — 功能性缺陷

| ID | 缺陷 | 位置 | 影响 |
|--|--|--|--|
| D-001 | **ScreenState 和 UIMap 配置重复**：同样的模板配置在两个地方定义，一处修改另处必不一致 | `model/ScreenState.kt` vs `core/uimap/UIMap.kt` | 后续新增模板时极易出现遗漏或错位 |
| D-002 | **ScriptEngine 的流程是硬编码顺序**，不是真正的"扫描匹配"逻辑。当前只做 HALL→END 的线性流程，无法实现"持续扫描招募列表，发现匹配等级的悬赏才进入"的需求 | `ScriptEngine.runLoop()` | 与用户的"勾选后续自动识别"需求不匹配 |
| D-003 | **BountyCheckList 状态有双重同步**：内部 `editableConfigs` 和 `GameManager.updateBountyConfigs` 在两个地方写入 | `ui/floating/BountyCheckList.kt` | 可能导致状态不一致 |
| D-004 | **ScreenDetector 在 detect() 中对每个分支调用两次 matchScreen**（一次条件判断、一次返回值），浪费一倍 CPU | `ScreenDetector.detect()` | 性能浪费，每轮循环多 8 次模板匹配 |

### 🟠 P1 — 设计缺陷

| ID | 缺陷 | 位置 |
|--|--|--|
| D-005 | **ScriptEngine 和 BountyExecutor 都持有 ScreenDetector**，职责重叠 | 两个文件 |
| D-006 | **model 层引用 core 层**：`ScreenState` 在 `model/` 包中导入了 `core.uimap.TemplateConfig`，违反分层依赖规则 | `model/ScreenState.kt` |
| D-007 | **AppControl 零引用**：创建了完整的应用控制工具但没有任何地方调用 | `core/util/AppControl.kt` |
| D-008 | **MainActivity 中的 mediaProjectionManager 字段未被任何方法使用** | `MainActivity.kt:11` |

### 🟡 P2 — 代码质量

| ID | 缺陷 | 位置 |
|--|--|--|
| D-009 | **BountyExecutor 中大部分方法仍然自己做截图**，仅 `clickCoord` 复用了传入的坐标。executeBattle / executeCloseReward / executeSlide 等每个都自己走 `capture.capture()` → `detector.matchScreen()` 流程 | `BountyExecutor.kt` |
| D-010 | **ScreenCapture 是单例 + 双重锁**：测试困难，无法 mock | `capture/ScreenCapture.kt` |
| D-011 | **FloatingWindowService（313行）和 BountyExecutor（196行）偏大**，各承担了 3+ 种不同职责 | 两个文件 |
| D-012 | **权限引导流程繁琐**：用户授权截图后需要再次点击 Link Start 才能启动悬浮窗 | `NinjaScriptMainUI.kt` + `CapturePermissionActivity.kt` |
| D-013 | **模板配置名与实际图片文件名可能不一致**：`TemplateConfig.templateName` 和 assets 目录下的文件名无编译期校验 | `UIMap.kt` |

---

## 8. 代码质量评估

### 8.1 量化评分

| 维度 | 评分 | 说明 |
|--|--|--|
| **可读性** | ⭐⭐⭐⭐ 4/5 | 命名清晰，有中文注释，逻辑分段合理 |
| **模块化** | ⭐⭐⭐ 3/5 | 目录结构合理但模块间职责有重叠 |
| **可测试性** | ⭐ 1/5 | 大量单例 + Android 依赖，无法单元测试 |
| **错误处理** | ⭐⭐⭐ 3/5 | try-finally 保证 recycle，但异常后恢复策略简单 |
| **性能** | ⭐⭐⭐ 3/5 | Bitmap 及时回收，但 detect() 中重复匹配浪费 |
| **一致性** | ⭐⭐ 2/5 | 配置两套、检测两套、状态同步有两条路径 |
| **可扩展性** | ⭐⭐ 2/5 | 新增悬赏等级需改多个地方，没有插件化设计 |

**综合评分: 2.6 / 5** — 可运行但维护成本较高，扩展困难。

### 8.2 代码量统计

> 注: 已按重构前的代码量计算，下方含文件路径参照

| 包 | 文件数 | 代码行(约) |
|--|--|--|
| `model/` | 3 | 52 |
| `core/capture/` | 2 | 177 |
| `core/recognition/` | 3 | 293 |
| `core/floating/` | 1 | 313 |
| `core/accessibility/` | 1 | 53 |
| `core/uimap/` | 1 | 45 |
| `core/util/` | 6 | 410 |
| `core/` (GameManager + ScriptEngine) | 2 | 197 |
| `ui/` | 3 | 208 |
| **总计** | **22** | **~1748** |

---

## 9. 建议重构路线图

### 阶段一：消除冗余和缺陷（短期 1-2 天）

| 序号 | 任务 | 涉及文件 |
|--|--|--|
| 1 | 统一配置：删除 `UIMap.kt`，将配置全部收敛到 `ScreenState.kt` 或独立的 `ScreenConfig.kt` | UIMap.kt, ScreenState.kt, ScreenDetector.kt |
| 2 | 修复 ScreenDetector 重复匹配 | ScreenDetector.detect() |
| 3 | 修复 BountyCheckList 状态同步 | BountyCheckList.kt |
| 4 | 删除 AppControl + MainActivity 死字段 | AppControl.kt, MainActivity.kt |
| 5 | 删除 `model/ScreenState` 对 `core.uimap` 的依赖 | ScreenState.kt |

### 阶段二：重新设计循环引擎（中期 3-5 天）

| 序号 | 任务 | 说明 |
|--|--|--|
| 6 | 重新设计 ScriptEngine：改为"扫描→匹配→执行"模式 | 不再硬编码 HALL→END 顺序 |
| 7 | 抽象 `ScriptTask` 接口：单一动作步骤（检测+点击+验证） | 取代 BountyExecutor 中的重复代码 |
| 8 | 引入 `WorkflowPipeline`：按需组合多个 ScriptTask | 支持不同悬赏等级的不同流程 |
| 9 | 将 ScreenState 检测输出缓存到当前上下文，避免重复截图 | 提升性能 |

### 阶段三：依赖注入 + 可测试性（中期 3-5 天）

| 序号 | 任务 | 说明 |
|--|--|--|
| 10 | ScreenCapture 改为接口 + 实现，移除单例 | 可 mock 截图进行测试 |
| 11 | GameManager 状态机抽离接口 | 方便单元测试 |
| 12 | BountyExecutor 改为组合模式而非 switch-case | 方便为不同悬赏等级定制动作 |

### 阶段四：悬浮窗 UI 增强（长期）

| 序号 | 任务 |
|--|--|
| 13 | 悬浮窗菜单改用 Compose (而非 XML layout) |
| 14 | 悬赏勾选面板嵌入悬浮窗滑动菜单 |
| 15 | 实时日志显示面板 |
| 16 | 运行状态指示器 |

---

> 本文档对应代码版本 v2.0.0。
> 下次更新本文档时，请同步更新版本号和变更日期。
