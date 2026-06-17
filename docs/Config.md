# Config 模块规格 - ScriptConfigRepository 配置系统

## 架构

```
ScriptConfigRepository（object 单例）
├── 多个独立 StateFlow    — UI 观察配置变化
├── snapshot()            — Engine 线程安全读取当前配置（返回 ScriptSnapshot）
├── set*() 方法           — UI 更新各项配置
└── loadAll()             — 从 SharedPreferences 加载全部配置
```

## ScriptSnapshot 数据类

Engine 启动时通过 `snapshot()` 获取不可变快照，避免并发修改：

```kotlin
data class ScriptSnapshot(
    val bountyConfigs: List<BountyConfig>,
    val personalConfigs: List<BountyConfig>,
    val nsConfigs: List<BountyConfig>,
    val dailyEnabled: Boolean,
    val personalEnabled: Boolean,
    val nsEnabled: Boolean,
    val inviteCheckEnabled: Boolean,
    val savedRunCounts: Map<BountyGrade, Int>
) {
    val enabledBountyConfigs get() = bountyConfigs.filter { it.enabled }
    val enabledPersonalConfigs get() = personalConfigs.filter { it.enabled }
    val enabledNsConfigs get() = nsConfigs.filter { it.enabled }
}
```

## 配置项

### 业务线开关

| StateFlow | Setter | SP Key | 默认值 | 说明 |
|-----------|--------|--------|--------|------|
| `dailyEnabled` | `setDailyEnabled()` | `daily_enabled` | true | 每日悬赏开关 |
| `personalEnabled` | `setPersonalEnabled()` | `personal_enabled` | false | 个人悬赏开关 |
| `nsEnabled` | `setNsEnabled()` | `ns_enabled` | false | 逆袭事件开关 |
| `treasureEnabled` | `setTreasureEnabled()` | `treasure_enabled` | false | 藏宝图开关 |
| `inviteCheckEnabled` | `setInviteCheckEnabled()` | `invite_check` | false | 组队邀请检测开关 |

### 悬赏等级配置

| StateFlow | Setter | SP Key (启用) | SP Key (追梦) | 说明 |
|-----------|--------|--------------|--------------|------|
| `bountyConfigs` | `setBountyConfigs()` | `cfg_bounty_enabled` | `cfg_bounty_chase_dream` | 每日悬赏等级配置 |
| `personalConfigs` | `setPersonalConfigs()` | `cfg_personal_enabled` | - | 个人悬赏等级配置 |
| `nsConfigs` | `setNsConfigs()` | `cfg_ns_enabled` | - | 逆袭事件等级配置 |

### 完成次数持久化

| SP Key | 格式 | 说明 |
|--------|------|------|
| `run_counts` | `grade_key=count,grade_key=count,...` | 各等级已完成次数 |

| 方法 | 说明 |
|------|------|
| `saveRunCounts(counts)` | 保存次数到 SP |
| `loadRunCounts()` | 从 SP 加载次数（内部方法） |
| `clearRunCounts()` | 清空所有次数记录 |

## SharedPreferences 键名完整列表

| 键名 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `daily_enabled` | Boolean | true | 每日悬赏开关 |
| `personal_enabled` | Boolean | false | 个人悬赏开关 |
| `ns_enabled` | Boolean | false | 逆袭事件开关 |
| `treasure_enabled` | Boolean | false | 藏宝图开关 |
| `invite_check` | Boolean | false | 组队邀请检测开关 |
| `cfg_bounty_enabled` | String | `"ss_plus,ss,s_plus,s,a_plus,a,b,c,d"` | 已启用等级 key（逗号分隔） |
| `cfg_bounty_chase_dream` | String | `""` | 追梦模式等级 key（逗号分隔） |
| `cfg_personal_enabled` | String | `"ss_plus,ss,s_plus,s,a_plus,a,b,c,d"` | 个人悬赏已启用等级 key |
| `cfg_ns_enabled` | String | `""` | 逆袭事件已启用等级 key |
| `run_counts` | String | `""` | 完成次数（`key=count,...` 格式） |

## 线程安全规则

1. **Engine 读取配置**：必须使用 `snapshot()` 获取不可变 `ScriptSnapshot`
2. **UI 更新配置**：通过对应的 `set*()` 方法，内部更新 StateFlow + 持久化到 SP
3. **禁止直接访问 StateFlow.value**：Engine 线程中不允许直接读取 StateFlow

## 生命周期

```
Application.onCreate()
  → ScriptConfigRepository.init(context)
  → 从 SharedPreferences 加载全部配置到 StateFlow
  → 发布初始状态

脚本运行时:
  → Engine: val snap = snapshot()  // 获取当前快照
  → Engine: 使用 snap.enabledBountyConfigs 等值
  → UI: scriptConfigRepository.setBountyConfigs(newList)  // 用户修改配置
  → StateFlow 通知 UI 更新 + 自动持久化到 SP
```

## 等级配置加载逻辑

`loadGradeConfigs(enabledKey, chaseDreamKey)` 从 SP 读取逗号分隔的 key 列表，生成 `List<BountyConfig>`：

1. 读取 `enabledKey` 对应的逗号分隔字符串 → 解析为已启用等级 key 集合
2. 若 `chaseDreamKey` 非空，读取追梦等级 key 集合
3. 对 `BountyGrade.sorted()` 中每个等级生成 `BountyConfig`：
   - `enabled` = 该等级 key 在已启用集合中
   - `chaseDream` = 该等级 `canChaseDream` 且 key 在追梦集合中
4. NS 配置额外过滤：仅保留 `isEvent = true` 的等级（NSS+, NS, NA）
