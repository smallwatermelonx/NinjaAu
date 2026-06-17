# 忍者必须死3 自动化 - 需求规格说明书（总纲）

## 项目目标

自动完成悬赏任务循环：大厅导航 → 招募列表扫描 → 组队/悬赏详情 → 战斗加载 → 战斗 → 结算 → 循环。

三大业务线：每日悬赏（Daily）、个人悬赏（Personal）、逆袭事件悬赏（NS）。

## 全局规范

| 规范项 | 值 | 说明 |
|--------|-----|------|
| 分辨率基准 | 1080x2340 | 其他分辨率需等比缩放 |
| 坐标系 | 相对坐标 (0.0~1.0) | 代码中 `Pair(x, y)` 为像素坐标，Spec 中记录相对坐标 |
| 默认点击延迟 | 500ms | 点击操作后等待界面响应的时间 |
| 默认扫描间隔 | 100ms | 快速扫描循环的间隔 |
| 默认截图匹配阈值 | 0.8 | SceneDetector 模板匹配阈值 |
| 节点超时 | 30s | 无匹配超过30s抛 NodeTimeoutException |
| 最大恢复次数 | 5 | RecoveryNode 连续失败上限 |
| 异步方式 | Kotlin 协程 | 禁止 Thread/Handler |
| 截图方式 | MediaProjection + ScreenCapture | 必须从前台 Service context 创建 |

## 状态机总览

### GamePhase（阶段流转）

```
IDLE → LOBBY → CHAT → RECRUIT_LIST → RECRUIT_INVITE → BOUNTY_DETAIL
      → BATTLE_LOADING → FIGHT → DEFEAT → SETTLEMENT
      → RECOVERY → DONE
      → PERSONAL_BOUNTY_CENTER → PERSONAL_BOUNTY_DETAIL
```

| Phase | 负责节点 | 说明 |
|-------|---------|------|
| IDLE | - | 初始状态，等待启动 |
| LOBBY | HallNode | 大厅导航 |
| CHAT | HallNode | 大厅聊天页面 |
| RECRUIT_LIST | BountyListNode | 招募列表扫描 |
| RECRUIT_INVITE | RecruitInviteNode | 招募邀请处理（TODO 桩） |
| BOUNTY_DETAIL | BountyDetailNode | 悬赏详情/组队 |
| BATTLE_LOADING | BattleLoadingNode | 等待战斗加载 |
| FIGHT | FightNode | 战斗逻辑 |
| DEFEAT | DefeatNode | 战斗失败处理（TODO 桩） |
| SETTLEMENT | SettlementNode | 结算领奖 |
| RECOVERY | RecoveryNode | 异常恢复 |
| DONE | - | 所有等级完成，脚本结束 |
| PERSONAL_BOUNTY_CENTER | PersonalBountyCenterNode | 个人悬赏中心列表 |
| PERSONAL_BOUNTY_DETAIL | PersonalBountyDetailNode | 个人悬赏详情/出发 |

### ScreenState（屏幕识别状态）

详见 `app/src/main/java/com/example/ninjaau/model/ScreenState.kt`，共33种屏幕状态。

## 模块清单

| 模块 | Spec 文件 | 职责 |
|------|----------|------|
| HallNode | [nodes/HallNode.md](nodes/HallNode.md) | 大厅导航，进入招募列表 |
| BountyListNode | [nodes/BountyListNode.md](nodes/BountyListNode.md) | 扫描招募列表，选择悬赏等级 |
| BountyDetailNode | [nodes/BountyDetailNode.md](nodes/BountyDetailNode.md) | 悬赏详情页，组队/准备/退出 |
| BattleLoadingNode | [nodes/BattleLoadingNode.md](nodes/BattleLoadingNode.md) | 等待战斗加载完成 |
| FightNode | [nodes/FightNode.md](nodes/FightNode.md) | 战斗逻辑（下滑+Boss战） |
| SettlementNode | [nodes/SettlementNode.md](nodes/SettlementNode.md) | 结算领奖，更新计数 |
| RecoveryNode | [nodes/RecoveryNode.md](nodes/RecoveryNode.md) | 异常恢复策略 |
| RecruitInviteNode | [nodes/RecruitInviteNode.md](nodes/RecruitInviteNode.md) | 招募邀请处理（TODO 桩） |
| DefeatNode | [nodes/DefeatNode.md](nodes/DefeatNode.md) | 战斗失败处理（TODO 桩） |
| PersonalBountyCenterNode | [nodes/PersonalBountyCenterNode.md](nodes/PersonalBountyCenterNode.md) | 个人悬赏中心列表 |
| PersonalBountyDetailNode | [nodes/PersonalBountyDetailNode.md](nodes/PersonalBountyDetailNode.md) | 个人悬赏详情/出发 |
| UI | [ui.md](ui.md) | 悬浮窗/HUD/配置面板 |
| Config | [Config.md](Config.md) | ScriptConfigRepository 配置系统 |

## 模板资源规范

- 路径：`app/src/main/assets/templates/`
- 命名：文件名 = `ScreenState` 枚举值的小写下划线形式（如 `chat_icon.png`）
- 分目录：按场景分目录（`bounty_list/`、`fight/`、`lobby/`、`team_room/` 等）
- **禁止修改模板文件名**，文件名即 ScreenState 映射的 key
