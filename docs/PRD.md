# 忍者必须死3 — 自动化悬赏工具 产品需求文档 (PRD)

| 版本 | 日期 | 作者 | 变更说明 |
|------|------|------|---------|
| v1.0 | 2026-06-07 | SMALLWATERMELON | 初始版本 |

---

## 1. 产品概述

### 1.1 产品名称
NajiaAu（忍者自动化）

### 1.2 产品定位
Android 平台的《忍者必须死3》悬赏任务自动化工具。通过无障碍服务 + MediaProjection 截图识别，实现悬赏全流程自动挂机。

### 1.3 目标用户
- 已 root 或已授权无障碍服务的 Android 设备用户
- 需要重复完成日常/个人/逆袭悬赏的玩家

### 1.4 核心价值
- 自动完成日常悬赏（D~SS+ 共 12 个等级）
- 自动完成个人悬赏
- 自动完成逆袭（NS）活动悬赏
- 支持追梦模式（跳过每日上限，持续刷稀有等级）

---

## 2. 业务线定义

### 2.1 日常悬赏（Daily）

| 等级组 | 包含等级 | 默认次数 | 建议等级 |
|--------|---------|---------|---------|
| A_GROUP | A, A+ | 3 | lv80 |
| S_GROUP | S, S+ | 5 | lv90 |
| B | B | 4 | lv60 |
| C | C | 5 | lv40 |
| D | D | 5 | lv30 |
| SS | SS | 1 | lv100 |
| SS_PLUS | SS+ | 1 | lv105~130 |

### 2.2 逆袭悬赏（NS）

| 等级 | 默认次数 | 建议等级 |
|------|---------|---------|
| NSS+ | 1 | lv125 |
| NS | 5 | lv125 |
| NA | 2 | lv125 |

### 2.3 个人悬赏（Personal）
- 日常悬赏全部完成后自动切换
- 使用日常等级列表，独立计数

---

## 3. 系统架构

### 3.1 技术栈
- **语言**: Kotlin
- **Min SDK**: 28 (Android 9)
- **Target SDK**: 34 (Android 14)
- **核心依赖**: OpenCV 4.5.3 (模板匹配), Jetpack Compose (UI)

### 3.2 架构模式
```
GameManager (单例)
  └── WorkflowEngine (主循环)
        └── GameNode (11个节点实现)
```

### 3.3 节点流程

```
HALL → RECRUIT_LIST → BOUNTY_DETAIL → BATTLE_LOADING → FIGHT → SETTLEMENT → HALL (循环)
                        ↑                                      ↓
                        └──────────────────────────────────────┘
              RECRUIT_INVITE (TODO 桩)    DEFEAT (TODO 桩)
              个人悬赏: PERSONAL_BOUNTY_CENTER → PERSONAL_BOUNTY_DETAIL
```

| 节点 | 入口 Phase | 出口 Phase | 状态 |
|------|-----------|-----------|------|
| HallNode | IDLE / LOBBY / CHAT | RECRUIT_LIST | 已实现 |
| BountyListNode | RECRUIT_LIST | BOUNTY_DETAIL | 已实现 |
| RecruitInviteNode | RECRUIT_INVITE | RECRUIT_LIST | TODO 桩 |
| BountyDetailNode | BOUNTY_DETAIL | BATTLE_LOADING | 已实现 |
| BattleLoadingNode | BATTLE_LOADING | FIGHT | 已实现 |
| FightNode | FIGHT | SETTLEMENT | 已实现 |
| DefeatNode | DEFEAT | LOBBY | TODO 桩 |
| SettlementNode | SETTLEMENT | LOBBY / DONE | 已实现 |
| RecoveryNode | RECOVERY | 各正常阶段 | 已实现 |
| PersonalBountyCenterNode | PERSONAL_BOUNTY_CENTER | PERSONAL_BOUNTY_DETAIL / DONE | 已实现 |
| PersonalBountyDetailNode | PERSONAL_BOUNTY_DETAIL | BATTLE_LOADING / PERSONAL_BOUNTY_CENTER | 已实现 |

### 3.4 异常恢复

入口哨兵失败 → `return null` → WorkflowEngine 触发 RecoveryNode → 全局页面匹配 → 路由到正确节点

RecoveryNode 匹配优先级：
1. 组队邀请弹窗 → 拒绝
2. 结算弹窗 → SETTLEMENT
3. 确认按钮 → SETTLEMENT
4. 准备按钮 → BOUNTY_DETAIL
5. 战斗加载 → BATTLE_LOADING
6. 滑铲/跳跃按钮 → FIGHT
7. 招募列表 → RECRUIT_LIST
8. 聊天图标 → LOBBY
9. 全部不匹配 → globalFailCount++ (3次停止)

---

## 4. 模板匹配规格

### 4.1 匹配引擎
- OpenCV `Imgproc.matchTemplate` with `TM_CCOEFF_NORMED`
- 模板存储于 `assets/templates/`
- 每个模板独立阈值 (0.5~0.92)

### 4.2 关键模板

| 模板 | 路径 | 阈值 | 用途 |
|------|------|------|------|
| 聊天图标 | lobby/hall_chat.png | 0.75 | 大厅识别 |
| 招募页签 | chat/team_recruit.png | 0.8 | 招募列表入口 |
| 准备按钮 | team_room/prepare.png | 0.8 | 队伍房间 |
| 战斗加载 | battle_loading/smile.png | 0.7 | 加载界面 |
| 滑铲按钮 | fight/slide.png | 0.7 | 战斗-下滑 |
| 跳跃按钮 | fight/jump.png | 0.7 | 战斗-跳跃 |
| 大招图标 | fight/role/shihara/r_shihara.png | 0.6 | 战斗-大招 |
| 武器图标 | fight/wopen/shedao.png | 0.6 | 战斗-武器 |
| 血咒技能 | fight/role/shihara/blood_curse.png | 0.85 | 战斗-血咒(buff) |
| 等级图标 | team_room/lv{level}.png | 0.9 | 队伍等级识别 |
| 结算弹窗 | settlement/black.png | 0.7 | 结算界面 |

---

## 5. UI/UX 规格

### 5.1 主界面
- 双栏布局 (Jetpack Compose + Material3)
- 左侧 20%: 任务开关 + Link Start 按钮
- 中间 60%: 等级配置面板
- 右侧 20%: 实时日志

### 5.2 悬浮窗
- 悬浮球: 可拖拽，边缘吸附，5秒自动隐藏
- 菜单: 从悬浮球位置展开
- HUD: 右上角进度显示
- Toast: 页面导航通知

### 5.3 权限要求
- `SYSTEM_ALERT_WINDOW` — 悬浮窗
- `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_MEDIA_PROJECTION` — 截图
- AccessibilityService — 手势模拟 (绑定 `com.pandadagames.ninja.global`)

---

## 6. 非功能需求

### 6.1 性能
- 单轮截图+识别 < 500ms
- 模板匹配使用 ROI 裁剪加速

### 6.2 兼容性
- Android 9+ (API 28)
- 仅支持 ARM 架构 (arm64-v8a, armeabi-v7a)

### 6.3 稳定性
- 全局兜底: 3次 Recovery 失败后脚本停止
- 异常捕获: 所有节点 try-finally 保证资源释放
