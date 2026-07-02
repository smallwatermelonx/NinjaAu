# CLAUDE.md

## 核心规则（必须遵守）

1. **不要乱改已有业务逻辑。** 修改/重构已有功能必须先完全理解业务上下文。不要凭假设修改。已经正常工作的代码，如果没有明确的 bug 证据，不要动它。
2. **不要创建不存在的需求。** 严格按照已有的 Spec 文档（`docs/nodes/`）和用户明确要求来编码。不要凭假设添加功能、标志位、超时机制等。Spec 里没有的，用户没说的，不要做。
3. **优化前必须先确认原始行为。** 要优化某个模块，先用 `git show` 读取历史版本，理解原始设计意图，再做针对性改进。不要把"重构"当"优化"。
3. **改动后必须验证。** 每次改动都要编译、安装、实际运行验证，不能只看代码就认为正确。
4. **新增/修改代码必须用 Kotlin。** 不允许用 Java 写新代码。
5. **不要引入未经确认的第三方库。** 项目当前依赖 OpenCV 和 Kotlin 协程，新增依赖必须先询问。
6. **Spec-First 开发。** 新增/修改功能前，必须先写或更新对应的 Spec 文档（`docs/nodes/` 目录）。Spec 不写，代码不动。修改功能时，先更新 Spec 再改代码，两者必须同步。

## 反模式清单（禁止事项）

- 不要用 `Thread` / `Handler` 做异步，统一用 Kotlin 协程 (`CoroutineScope` + `Dispatchers`)
- 不要硬编码屏幕坐标，坐标必须通过模板匹配或配置获取
- 不要在 `GameNode.execute()` 里直接捕获所有异常后静默吞掉，要抛出或记录
- 不要用 `Thread.sleep()` 做延迟，用 `kotlinx.coroutines.delay()`
- 不要在 UI 线程做截图/模板匹配操作
- 不要手动管理 Bitmap 回收，让 GC 处理或用 `use {}` 自动释放
- 不要绕过 `GameManager.toggleScript()` 直接修改 `ScriptState`
- 不要在 `NodeContext` 外部直接调用 `NinjaAccessibilityService` 的方法
- 不要修改模板图片文件名，文件名就是 `ScreenState` 映射的 key

## 构建命令

```bash
# 构建 debug APK（需要 JDK 11+，Android Studio 自带的 JBR 即可）
export JAVA_HOME="/d/Android/Android Studio/jbr"
./gradlew assembleDebug

# APK 输出路径
# app/build/outputs/apk/debug/app-debug.apk
```

无单元测试。`app/src/test/` 和 `app/src/androidTest/` 为空。

## 项目目录结构

```
app/src/main/java/com/example/ninjaau/
├── MainActivity.kt              # 应用入口 Activity
├── NinjaApp.kt                  # Application 类
├── core/
│   ├── GameManager.kt           # 全局单例，管理脚本状态和启动流程
│   ├── GameNode.kt              # GameNode 接口 + NodeContext 定义
│   ├── WorkflowEngine.kt        # 主循环，按 GamePhase 分发节点
│   ├── node/                    # 所有 GameNode 实现
│   │   ├── LobbyNode.kt         # 大厅导航
│   │   ├── BountyListNode.kt    # 悬赏列表扫描
│   │   ├── BountyDetailNode.kt  # 悬赏详情/组队
│   │   ├── BattleLoadingNode.kt # 战斗加载
│   │   ├── FightNode.kt         # 战斗逻辑
│   │   ├── SettlementNode.kt    # 结算
│   │   ├── PersonalBountyCenterNode.kt  # 个人悬赏中心
│   │   ├── PersonalBountyDetailNode.kt  # 个人悬赏详情
│   │   ├── DefeatNode.kt        # 战斗失败（含观战面板等待）
│   ├── recognition/
│   │   ├── SceneDetector.kt     # 场景检测（模板匹配入口）
│   │   └── TemplateMatcher.kt   # OpenCV 模板匹配实现
│   ├── capture/
│   │   ├── ScreenCapture.kt     # 截图服务
│   │   └── CapturePermissionActivity.kt  # 截图权限申请
│   ├── floating/
│   │   ├── FloatingWindowService.kt  # 悬浮窗前台服务
│   │   └── HudManager.kt       # HUD 进度显示
│   ├── accessibility/
│   │   └── NinjaAccessibilityService.kt  # 无障碍服务（手势模拟）
│   ├── config/
│   │   └── ScriptConfigRepository.kt  # 脚本配置读写
│   └── util/
│       ├── Constant.kt          # 常量定义
│       ├── LogUtil.kt           # 日志工具
│       ├── ToastUtil.kt         # Toast 工具
│       ├── AssetUtil.kt         # Assets 读取
│       ├── OpenCVUtil.kt        # OpenCV 工具
│       └── PermissionManager.kt # 权限管理
├── model/
│   ├── GameContext.kt           # 运行时状态上下文
│   ├── GamePhase.kt            # 游戏阶段枚举
│   ├── ScreenState.kt          # 屏幕状态枚举（28种）
│   ├── BountyGrade.kt          # 悬赏等级定义
│   └── BountyConfig.kt         # 悬赏配置
└── ui/
    ├── NinjaScriptMainUI.kt    # Compose 主界面
    └── theme/
        └── Theme.kt            # 主题定义
```

模板资源路径：`app/src/main/assets/templates/`，按场景分子目录（`bounty_list/`、`fight/`、`lobby/`、`team_room/` 等）。

## Spec 文档

功能规格说明书位于 `docs/` 目录：

```
docs/
├── SRS.md              # 总纲：全局规范、状态机、模块清单
├── nodes/
│   ├── LobbyNode.md    # 大厅导航 Spec
│   ├── BountyListNode.md
│   ├── BountyDetailNode.md
│   ├── BattleLoadingNode.md
│   ├── FightNode.md
│   └── SettlementNode.md
├── ui.md               # 悬浮窗/HUD/配置面板 Spec
└── Config.md           # 配置系统 Spec
```

修改功能时必须同步更新对应 Spec。Spec 是 AI 生成代码的依据。

## 关键配置修改指南

| 要改什么 | 去哪个文件 |
|---------|-----------|
| 模板匹配阈值 | `SceneDetector.kt` 的 `templates` map |
| 新增屏幕状态 | `ScreenState.kt` 枚举 + `SceneDetector.kt` 添加模板映射 |
| 新增 GameNode | `core/node/` 下新建类 + `WorkflowEngine.kt` 注册分发 |
| 新增悬赏等级 | `BountyGrade.kt` |
| 修改悬浮窗行为 | `FloatingWindowService.kt` |
| 修改 UI 布局 | `NinjaScriptMainUI.kt` |

## 已知问题

1. `globalFailCount` 永不重置 — 3次分散的瞬态异常就会永久停止脚本
2. `PermissionManager.resumeMediaProjection()` 在 Android 12+ 需要 Service context
3. 藏宝图自动化未实现（只有 UI）
4. 个人悬赏中心/详情节点是框架桩
