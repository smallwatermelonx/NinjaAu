# Config 模块规格 - ScriptConfigRepository 配置系统

## 架构

```
ScriptConfigRepository（单例）
├── StateFlow<ConfigState>    — UI 观察配置变化
├── snapshot()                — Engine 线程安全读取当前配置
├── update()                  — UI 更新配置
└── loadGradeConfigs()        — 从 SharedPreferences 加载
```

## 配置项

### 悬赏等级配置

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| gradeEnabled_{grade} | Boolean | true | 该等级是否勾选 |
| gradeMaxRuns_{grade} | Int | 5 | 该等级最大完成次数 |

### 运行时状态

| 状态 | 类型 | 说明 |
|------|------|------|
| activeGrades | List<BountyGrade> | 当前活跃的等级列表 |
| runCounts | Map<BountyGrade, Int> | 各等级已完成次数 |
| currentBounty | BountyGrade? | 当前正在处理的等级 |
| actualGrade | BountyGrade? | 实际匹配到的等级（可能与目标不同） |

### 脚本状态

| 状态 | 类型 | 说明 |
|------|------|------|
| isRunning | Boolean | 脚本是否正在运行 |
| currentPhase | GamePhase | 当前所处阶段 |
| globalFailCount | Int | 全局失败计数（3次后停止脚本） |
| recoveryAttempt | Int | 当前恢复尝试次数 |

## 线程安全规则

1. **Engine 读取配置**：必须使用 `snapshot()` 获取不可变快照
2. **UI 更新配置**：通过 `update()` 方法，内部使用 Mutex 保护
3. **禁止直接访问 StateFlow.value**：Engine 线程中不允许直接读取 StateFlow

## 生命周期

```
Application.onCreate()
  → ScriptConfigRepository.init(context)
  → 从 SharedPreferences 加载配置
  → 发布初始 StateFlow

脚本运行时:
  → Engine: config = snapshot()  // 获取当前快照
  → Engine: 使用 config.activeGrades 等值
  → UI: configRepo.update { copy(...) }  // 用户修改配置
  → StateFlow 通知 UI 更新
```

## SharedPreferences 键名规范

| 键名格式 | 示例 | 说明 |
|----------|------|------|
| gradeEnabled_{level} | gradeEnabled_S | 等级S是否启用 |
| gradeMaxRuns_{level} | gradeMaxRuns_S | 等级S最大次数 |
| isScriptRunning | isScriptRunning | 脚本运行状态 |

**注意**：SharedPreferences 中 Boolean 类型的值必须用 `getBoolean()` 读取，不能用 `getString()`，否则会抛出 `ClassCastException`。
