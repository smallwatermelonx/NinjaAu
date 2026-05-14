# 变更记录

> 关联文档: [架构设计](v2.3-architecture.md) | [业务流程](v2.3-flow.md) | [识别层](v2.3-recognition.md)

---

## 版本记录

| 版本 | 日期 | 变更说明 |
|------|------|---------|
| v2.0 | 2026-05-12 | 初始架构文档，6 节点流水线设计，三段式 SCANNING→JOINING→VALIDATING |
| v2.0.1 | 2026-05-13 | 新增 RECRUIT_EXCEPTION 检测、修复 gradeIconPath 路径、修复 TAB 刷新 delay 重复 |
| v2.1 | 2026-05-13 | 核心重构: SCANNING 合并 JOINING，单循环处理 匹配→加入→准备 全流程; SCAN_REFRESH_CYCLES 10→3, SCAN_MAX_REFRESH 3→5; RECRUIT_EXCEPTION 阈值 0.8→0.7; 扫描循环新增 UNKNOWN 立即刷新; phaseJoin 废弃为死代码 |
| v2.2 | 2026-05-13 | GamePhase 页面模型重构; 修复 targetRuns 不生效; 修复 SCOPE_NAVIGATE 缺少 DAILY_LIMIT/DEFEAT_POPUP; 修复 phaseClaim clickOutside 顺序; clickOutside() 改用 OpenCV 动态检测; grade/level 图标模板缓存 |
| **v2.3** | **2026-05-14** | **校验模式重构: 移除10周期节拍，每100ms全量检测; 加入失败异常→SCOPE_RECRUIT检测扔回主流程; 移除TEAM_FULL/TEAM_COMPLETED误检测; 等级校验改为matchAnyLevelIcon(activeGrades)集合匹配; 完成标记从activeGrades移除; RECRUIT_EXCEPTION→OUT_OF_RANGE_RECRUIT(点击刷新); 日志清理; 文档拆分为架构/流程/识别三层** |

---

## v2.3 已修复

1. **校验分支 10-cycle 节拍** — 移除 `noMatchCycles % 10 != 0` 跳过逻辑，校验模式每 100ms 全量检测
2. **加入失败死循环** — 校验分支用 `SCOPE_RECRUIT` 屏幕状态检测替代超时计数器
3. **TEAM_FULL/TEAM_COMPLETED 误检测** — 移除不可靠的 OpenCV 模板匹配
4. **等级校验只针对 currentBounty** — 改为 `matchAnyLevelIcon(activeGrades)` 集合匹配
5. **完成标记逻辑** — 结算后从 `activeGrades` 移除，`isEmpty()` → DONE
6. **RECRUIT_EXCEPTION → OUT_OF_RANGE_RECRUIT** — 重命名，点击刷新替代 TAB 切页
7. **异常检测跳过校验模式** — 加上 `ctx.currentBounty == null` 条件
8. **"X匹配，点击加入"日志误导** — 改为 "X悬赏可见，点击加入"
9. **DEBUG级相似度日志刷屏** — 删除匹配失败的 DEBUG 日志
10. **文档拆分** — 单文件拆分为架构/流程/识别/变更记录四模块

---

## 待修复

1. **`globalFailCount` 永不归零** — 累计计数而非连续失败计数，3 次分散的瞬态异常也会停止脚本
2. **活动悬赏等级图标模板缺失** — NSS+.png / NS.png / NA.png
3. **`phaseClaim` 无超时上限** — 300 轮 × 1s = 5 分钟才退出
4. **`phaseJoin` 死代码** — JOINING 阶段已废弃，方法仍保留
