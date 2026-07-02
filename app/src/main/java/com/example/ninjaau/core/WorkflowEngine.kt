package com.example.ninjaau.core

import android.content.Context
import android.graphics.Bitmap
import android.media.RingtoneManager
import com.example.ninjaau.core.accessibility.NinjaAccessibilityService
import com.example.ninjaau.core.capture.ScreenCapture
import com.example.ninjaau.core.config.ScriptConfigRepository
import com.example.ninjaau.core.node.*
import com.example.ninjaau.core.recognition.SceneDetector
import com.example.ninjaau.core.util.LogUtil
import com.example.ninjaau.core.util.PermissionManager
import com.example.ninjaau.model.BountyConfig
import com.example.ninjaau.model.BusinessLine
import com.example.ninjaau.model.BountyGrade
import com.example.ninjaau.model.GameContext
import com.example.ninjaau.model.GamePhase
import com.example.ninjaau.model.ScreenState
import android.media.Ringtone
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.coroutineContext

/**
 * 悬赏自动化流水线引擎 v3 — 节点模式
 *
 * 每个游戏页面对应一个独立的 GameNode，通过 WorkflowEngine 统一调度。
 * 参考 MAA (MeoAssistanceArknights) 的节点模式设计。
 *
 * 页面 → 节点映射:
 *   大厅/聊天 → [LobbyNode] → 招募列表 → [BountyListNode] → 悬赏详情 → [BountyDetailNode]
 *   → 战斗 → [BattleNode] → 结算 → [SettlementNode] → 大厅 → ...
 *
 * 异常兜底: 3次连续失败 → 整体判定 → 3次整体判定失败 → 停止脚本并写日志
 */
class WorkflowEngine(
    private val context: Context,
    private val postLog: ((String) -> Unit)? = null,
    private val onPageEvent: ((String) -> Unit)? = null
) {
    private val TAG = "WorkflowEngine"

    private val capture = ScreenCapture.getInstance(context)
    private val detector = SceneDetector(context)
    private val accessibility get() = NinjaAccessibilityService.getInstance()
    private var globalFailCount = 0
    private var phaseStuckCount = 0
    var lastContext: GameContext? = null
        private set

    // ── SS+ 铃声控制 ──
    private var currentRingtone: Ringtone? = null

    // ── 节点实例（每个游戏页面对应一个节点） ──
    private val lobbyNode: LobbyNode
    private val bountyListNode: BountyListNode
    private val bountyDetailNode: BountyDetailNode
    private val battleLoadingNode: BattleLoadingNode
    private val battleNode: FightNode
    private val settlementNode: SettlementNode
    private val defeatNode: DefeatNode
    private val personalBountyCenterNode: PersonalBountyCenterNode
    private val personalBountyDetailNode: PersonalBountyDetailNode
    private val recoveryHandler: RecoveryHandler

    init {
        val nodeCtx = NodeContext(
            detector = detector,
            captureBitmap = { captureBitmap() },
            click = { click(it) },
            log = { log(it) },
            onPageEvent = onPageEvent,
            delay = { delay(it) },
            playAlarm = {
                try {
                    currentRingtone?.stop()
                    val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    val ringtone = RingtoneManager.getRingtone(context, alarmUri)
                    ringtone?.play()
                    currentRingtone = ringtone
                } catch (_: Exception) {}
            }
        )
        lobbyNode = LobbyNode(nodeCtx)
        bountyListNode = BountyListNode(nodeCtx)
        bountyDetailNode = BountyDetailNode(nodeCtx)
        battleLoadingNode = BattleLoadingNode(nodeCtx)
        battleNode = FightNode(nodeCtx)
        settlementNode = SettlementNode(nodeCtx)
        defeatNode = DefeatNode(nodeCtx)
        personalBountyCenterNode = PersonalBountyCenterNode(nodeCtx)
        personalBountyDetailNode = PersonalBountyDetailNode(nodeCtx)
        recoveryHandler = RecoveryHandler(detector, { captureBitmap() }, { log(it) })
    }

    companion object {
        private const val MAX_GLOBAL_FAIL = 3
        private const val MAX_PHASE_STUCK = 5
    }

    // ══════════════════════════════════════════
    //  公开入口（保持签名不变，向后兼容）
    // ══════════════════════════════════════════

    suspend fun runLoop(
        configs: List<BountyConfig>,
        dailyEnabled: Boolean = true,
        personalBountyEnabled: Boolean = false,
        personalConfigs: List<BountyConfig> = emptyList(),
        nsEnabled: Boolean = false,
        onProgress: ((Map<BountyGrade, Pair<Int, Int>>) -> Unit)? = null
    ): Boolean {
        val ctx = buildContext(configs, dailyEnabled, personalBountyEnabled, personalConfigs, nsEnabled)
        globalFailCount = 0
        phaseStuckCount = 0
        emitProgress(ctx, onProgress)

        log("业务线: 日常=${ctx.dailyEnabled}, 个人=${ctx.personalBountyEnabled}, 逆袭=${ctx.nsEnabled}")

        var phaseStartTime = System.currentTimeMillis()

        while (coroutineContext.isActive &&
            ctx.currentPhase != GamePhase.DONE &&
            globalFailCount < MAX_GLOBAL_FAIL
        ) {
            try {
                val phaseName = ctx.currentPhase.name
                log("Phase: $phaseName")

                // ═══ 全局组队邀请拦截（任意节点都可能弹出） ═══
                if (handleInvitation()) continue

                // ═══ 截图权限被系统回收 → 立即停止 ═══
                if (PermissionManager.isProjectionLost) {
                    log("⚠ 截图权限已被系统回收，脚本停止")
                    break
                }

                val nextPhase = dispatchPhase(ctx)
                if (nextPhase != null) {
                    // ═══ 阶段卡死检测：同一 phase 连续返回 MAX_PHASE_STUCK 次 → 强制恢复 ═══
                    if (nextPhase == ctx.currentPhase) {
                        phaseStuckCount++
                        if (phaseStuckCount >= MAX_PHASE_STUCK) {
                            log("⚠ 阶段卡死: ${ctx.currentPhase.name} 连续 $phaseStuckCount 次未转换，尝试恢复")
                            globalFailCount++
                            log("整体判定失败 ($globalFailCount/$MAX_GLOBAL_FAIL)")
                            val recovered = recoveryHandler.tryRecover()
                            ctx.currentPhase = recovered
                            phaseStuckCount = 0
                            continue
                        }
                    } else {
                        phaseStuckCount = 0
                    }

                    ctx.currentPhase = nextPhase

                    val phaseElapsed = System.currentTimeMillis() - phaseStartTime
                    log("[耗时] ${nextPhase.name} 阶段耗时 ${phaseElapsed}ms")
                    phaseStartTime = System.currentTimeMillis()

                    if (nextPhase != GamePhase.IDLE) {
                        globalFailCount = 0
                    }

                    emitProgress(ctx, onProgress)
                    val pageEvent = phaseToEvent(nextPhase)
                    if (pageEvent != null) onPageEvent?.invoke(pageEvent)

                    // ═══ 当前业务线完成 → 切换到下一个 ═══
                    if (nextPhase == GamePhase.DONE) {
                        val next = switchToNextBusinessLine(ctx)
                        if (next != null) {
                            ctx.currentPhase = next
                            emitProgress(ctx, onProgress)
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log("Pipeline 异常于 ${ctx.currentPhase}: ${e.message}")
                LogUtil.e(TAG, "Pipeline 异常", e)
                globalFailCount++
                log("整体判定失败 ($globalFailCount/$MAX_GLOBAL_FAIL)")
                val recovered = recoveryHandler.tryRecover()
                ctx.currentPhase = recovered
                phaseStuckCount = 0
            }
        }

        lastContext = ctx
        val allDone = isAllBusinessLinesDone(ctx)
        if (globalFailCount >= MAX_GLOBAL_FAIL) {
            val msg = "整体判定3次失败，脚本停止"
            log(msg)
            LogUtil.e(TAG, msg)
            writeCrashLog(ctx)
        }
        LogUtil.i(TAG, "流水线结束: allCompleted=$allDone, globalFailCount=$globalFailCount")
        return allDone
    }

    /**
     * 恢复暂停的脚本 — 使用已保存的 GameContext 继续执行。
     */
    suspend fun resumeLoop(
        ctx: GameContext,
        onProgress: ((Map<BountyGrade, Pair<Int, Int>>) -> Unit)? = null
    ): Boolean {
        globalFailCount = 0
        phaseStuckCount = 0
        lastContext = null
        emitProgress(ctx, onProgress)
        log("脚本恢复: Phase=${ctx.currentPhase.name}, 已完成=${ctx.runCounts}")

        var phaseStartTime = System.currentTimeMillis()

        while (coroutineContext.isActive &&
            ctx.currentPhase != GamePhase.DONE &&
            globalFailCount < MAX_GLOBAL_FAIL
        ) {
            try {
                val phaseName = ctx.currentPhase.name
                log("Phase: $phaseName")

                if (handleInvitation()) continue

                if (PermissionManager.isProjectionLost) {
                    log("⚠ 截图权限已被系统回收，脚本停止")
                    break
                }

                val nextPhase = dispatchPhase(ctx)
                if (nextPhase != null) {
                    if (nextPhase == ctx.currentPhase) {
                        phaseStuckCount++
                        if (phaseStuckCount >= MAX_PHASE_STUCK) {
                            log("⚠ 阶段卡死: ${ctx.currentPhase.name} 连续 $phaseStuckCount 次未转换，尝试恢复")
                            globalFailCount++
                            log("整体判定失败 ($globalFailCount/$MAX_GLOBAL_FAIL)")
                            val recovered = recoveryHandler.tryRecover()
                            ctx.currentPhase = recovered
                            phaseStuckCount = 0
                            continue
                        }
                    } else {
                        phaseStuckCount = 0
                    }

                    ctx.currentPhase = nextPhase

                    val phaseElapsed = System.currentTimeMillis() - phaseStartTime
                    log("[耗时] ${nextPhase.name} 阶段耗时 ${phaseElapsed}ms")
                    phaseStartTime = System.currentTimeMillis()

                    if (nextPhase != GamePhase.IDLE) {
                        globalFailCount = 0
                    }

                    emitProgress(ctx, onProgress)
                    val pageEvent = phaseToEvent(nextPhase)
                    if (pageEvent != null) onPageEvent?.invoke(pageEvent)

                    if (nextPhase == GamePhase.DONE) {
                        val next = switchToNextBusinessLine(ctx)
                        if (next != null) {
                            ctx.currentPhase = next
                            emitProgress(ctx, onProgress)
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log("Pipeline 异常于 ${ctx.currentPhase}: ${e.message}")
                LogUtil.e(TAG, "Pipeline 异常", e)
                globalFailCount++
                log("整体判定失败 ($globalFailCount/$MAX_GLOBAL_FAIL)")
                val recovered = recoveryHandler.tryRecover()
                ctx.currentPhase = recovered
                phaseStuckCount = 0
            }
        }

        lastContext = ctx
        val allDone = isAllBusinessLinesDone(ctx)
        if (globalFailCount >= MAX_GLOBAL_FAIL) {
            val msg = "整体判定3次失败，脚本停止"
            log(msg)
            LogUtil.e(TAG, msg)
            writeCrashLog(ctx)
        }
        LogUtil.i(TAG, "流水线恢复结束: allCompleted=$allDone, globalFailCount=$globalFailCount")
        return allDone
    }

    /**
     * 当前业务线完成后，切换到下一个启用的业务线。
     * @return 下一个阶段，或 null 表示全部完成
     */
    private fun switchToNextBusinessLine(ctx: GameContext): GamePhase? {
        when (ctx.businessLine) {
            BusinessLine.DAILY -> {
                // 日常完成（含已合并的逆袭等级）→ 切换到个人悬赏
                if (ctx.personalBountyEnabled && ctx.personalActiveGrades.isNotEmpty()) {
                    log("日常悬赏完成，切换到个人悬赏")
                    ctx.businessLine = BusinessLine.PERSONAL
                    ctx.activeGrades = ctx.personalActiveGrades
                    ctx.currentPhase = GamePhase.IDLE
                    onPageEvent?.invoke("切换到个人悬赏")
                    return GamePhase.IDLE
                }
                log("所有业务线已完成")
                return GamePhase.DONE
            }
            BusinessLine.PERSONAL -> {
                log("个人悬赏完成，所有业务线已完成")
                return GamePhase.DONE
            }
        }
    }

    /** 检查所有启用的业务线是否都已完成 */
    private fun isAllBusinessLinesDone(ctx: GameContext): Boolean {
        // switchToNextBusinessLine 返回 null 表示全部完成
        // 这里做二次校验：当前业务线的 activeGrades 是否清空 + 个人是否完成
        if (ctx.businessLine == BusinessLine.DAILY && ctx.activeGrades.isNotEmpty()) return false
        if (ctx.personalBountyEnabled && !ctx.personalBountyCompleted) return false
        return true
    }

    // ══════════════════════════════════════════
    //  节点调度（按游戏页面分派）
    // ══════════════════════════════════════════

    private suspend fun dispatchPhase(ctx: GameContext): GamePhase? {
        return when (ctx.currentPhase) {
            GamePhase.IDLE, GamePhase.LOBBY -> lobbyNode.execute(ctx)
            GamePhase.RECRUIT_LIST -> bountyListNode.execute(ctx)
            GamePhase.BOUNTY_DETAIL -> bountyDetailNode.execute(ctx)
            GamePhase.BATTLE_LOADING -> battleLoadingNode.execute(ctx)
            GamePhase.FIGHT -> battleNode.execute(ctx)
            GamePhase.DEFEAT -> defeatNode.execute(ctx)
            GamePhase.SETTLEMENT -> settlementNode.execute(ctx)
            GamePhase.PERSONAL_BOUNTY_CENTER -> personalBountyCenterNode.execute(ctx)
            GamePhase.PERSONAL_BOUNTY_DETAIL -> personalBountyDetailNode.execute(ctx)
            GamePhase.DONE -> GamePhase.DONE
        }
    }

    private fun phaseToEvent(phase: GamePhase): String? {
        return when (phase) {
            GamePhase.LOBBY, GamePhase.IDLE -> "大厅"
            GamePhase.RECRUIT_LIST -> "招募列表"
            GamePhase.BOUNTY_DETAIL -> "悬赏详情"
            GamePhase.BATTLE_LOADING -> "战斗加载"
            GamePhase.FIGHT -> "⚔ 战斗开始"
            GamePhase.DEFEAT -> "战斗失败"
            GamePhase.SETTLEMENT -> "结算领奖"
            GamePhase.DONE -> "🎉 全部悬赏完成"
            GamePhase.PERSONAL_BOUNTY_CENTER -> "进入个人悬赏中心"
            GamePhase.PERSONAL_BOUNTY_DETAIL -> "个人悬赏详情"
            else -> null
        }
    }

    // ══════════════════════════════════════════
    //  辅助方法
    // ══════════════════════════════════════════

    private fun buildContext(
        configs: List<BountyConfig>,
        dailyEnabled: Boolean = true,
        personalBountyEnabled: Boolean = false,
        personalConfigs: List<BountyConfig> = emptyList(),
        nsEnabled: Boolean = false
    ): GameContext {
        val enabled = configs.filter { it.enabled }
        val (dailyConfigs, nsConfigs) = enabled.partition { !it.grade.isEvent }
        val dailyGrades = if (dailyEnabled) dailyConfigs.map { it.grade } else emptyList()
        val nsGrades = if (nsEnabled) nsConfigs.map { it.grade } else emptyList()
        // NS 等级合并到日常业务线中统一处理
        val allDailyGrades = dailyGrades + nsGrades
        val chaseDreamGrades = enabled.filter { it.chaseDream }.map { it.grade }.toSet()
        val personalEnabled = personalConfigs.filter { it.enabled }
        val personalGrades = personalEnabled.map { it.grade }

        // 按优先级确定起始业务线
        val startBusinessLine: BusinessLine
        val startGrades: List<BountyGrade>
        val startPhase: GamePhase

        when {
            personalBountyEnabled && personalGrades.isNotEmpty() -> {
                startBusinessLine = BusinessLine.PERSONAL
                startGrades = personalGrades
                startPhase = GamePhase.IDLE
            }
            allDailyGrades.isNotEmpty() -> {
                startBusinessLine = BusinessLine.DAILY
                startGrades = allDailyGrades
                startPhase = GamePhase.IDLE
            }
            else -> {
                startBusinessLine = BusinessLine.DAILY
                startGrades = emptyList()
                startPhase = GamePhase.DONE
            }
        }

        return GameContext(
            currentPhase = startPhase,
            activeGrades = startGrades,
            runCounts = allDailyGrades.associateWith { 0 }.toMutableMap(),
            targetRuns = enabled.associate { it.grade to it.targetRuns },
            chaseDreamGrades = chaseDreamGrades,
            personalBountyEnabled = personalBountyEnabled,
            personalActiveGrades = personalGrades,
            businessLine = startBusinessLine,
            dailyEnabled = allDailyGrades.isNotEmpty(),
            nsEnabled = nsGrades.isNotEmpty(),
            nsActiveGrades = nsGrades
        )
    }

    private fun emitProgress(ctx: GameContext, onProgress: ((Map<BountyGrade, Pair<Int, Int>>) -> Unit)?) {
        if (onProgress == null) return
        // 只显示当前业务线实际处理的等级
        val activeList = if (ctx.businessLine == BusinessLine.PERSONAL) ctx.personalActiveGrades else ctx.activeGrades
        val progress = activeList.associateWith { grade ->
            Pair(ctx.runCounts[grade] ?: 0, ctx.targetRuns[grade] ?: grade.defaultRuns)
        }
        onProgress(progress)
    }

    private suspend fun captureBitmap(): Bitmap? {
        if (PermissionManager.isProjectionLost) {
            log("⚠ 截图权限已被系统回收，脚本暂停")
            return null
        }
        repeat(3) {
            val bmp = capture.capture()
            if (bmp != null) return bmp
            delay(300)
        }
        log("截图失败")
        return null
    }

    private fun click(coord: Pair<Float, Float>) {
        val service = accessibility
        if (service == null) {
            LogUtil.w(TAG, "点击失败: AccessibilityService 未连接 (${coord.first}, ${coord.second})")
            return
        }
        service.clickAt(coord.first, coord.second)
    }

    private fun log(msg: String) {
        LogUtil.i(TAG, msg)
        postLog?.invoke(msg)
    }

    /**
     * 全局组队邀请拦截 — 截屏检测邀请弹窗，命中则点击拒绝。
     * @return true 表示检测到邀请并已处理，调用方应 continue 跳过本轮正常逻辑
     */
    private suspend fun handleInvitation(): Boolean {
        if (!ScriptConfigRepository.inviteCheckEnabled.value) return false
        val screen = captureBitmap() ?: return false
        try {
            val inviteCoord = detector.matchTemplate(screen, ScreenState.TEAM_INVITATION)
            if (inviteCoord != null) {
                log("检测到组队邀请弹窗，拒绝")
                val rejectCoord = detector.matchTemplate(screen, ScreenState.INVITE_REJECT)
                if (rejectCoord != null) {
                    click(rejectCoord)
                    delay(500)
                }
                return true
            }
        } finally {
            screen.recycle()
        }
        return false
    }

    private fun writeCrashLog(ctx: GameContext) {
        try {
            val dir = File(context.filesDir, "crash_logs")
            dir.mkdirs()

            // 清理超过7天的旧日志
            val cutoff = System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L
            dir.listFiles()?.filter { it.isFile && it.lastModified() < cutoff }?.forEach { it.delete() }

            val time = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val file = File(dir, "crash_$time.log")
            FileWriter(file).use { w ->
                w.write("=== NinjaAu Crash Log ===\n")
                w.write("Time: $time\n")
                w.write("Phase: ${ctx.currentPhase}\n")
                w.write("CurrentBounty: ${ctx.currentBounty}\n")
                w.write("RunCounts: ${ctx.runCounts}\n")
                w.write("TotalCycles: ${ctx.totalCycles}\n")
                w.write("GlobalFailCount: $globalFailCount\n")
                w.write("================================\n")
            }
            log("崩溃日志已保存: ${file.absolutePath}")
        } catch (e: Exception) {
            LogUtil.e(TAG, "写崩溃日志失败", e)
        }
    }
}
