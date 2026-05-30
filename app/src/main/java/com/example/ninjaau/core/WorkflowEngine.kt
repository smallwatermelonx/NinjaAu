package com.example.ninjaau.core

import android.content.Context
import android.graphics.Bitmap
import com.example.ninjaau.core.accessibility.NinjaAccessibilityService
import com.example.ninjaau.core.capture.ScreenCapture
import com.example.ninjaau.core.node.*
import com.example.ninjaau.core.recognition.SceneDetector
import com.example.ninjaau.core.util.LogUtil
import com.example.ninjaau.core.util.PermissionManager
import com.example.ninjaau.model.BountyConfig
import com.example.ninjaau.model.BountyGrade
import com.example.ninjaau.model.GameContext
import com.example.ninjaau.model.GamePhase
import com.example.ninjaau.model.ScreenState
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
 *   大厅/聊天 → [HallNode] → 招募列表 → [BountyListNode] → 悬赏详情 → [BountyDetailNode]
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

    // ── 节点实例（每个游戏页面对应一个节点） ──
    private val hallNode: HallNode
    private val bountyListNode: BountyListNode
    private val bountyDetailNode: BountyDetailNode
    private val battleLoadingNode: BattleLoadingNode
    private val battleNode: FightNode
    private val settlementNode: SettlementNode
    private val recruitInviteNode: RecruitInviteNode
    private val defeatNode: DefeatNode
    private val recoveryNode: RecoveryNode

    init {
        val nodeCtx = NodeContext(
            detector = detector,
            captureBitmap = { captureBitmap() },
            click = { click(it) },
            log = { log(it) },
            onPageEvent = onPageEvent,
            delay = { delay(it) }
        )
        hallNode = HallNode(nodeCtx)
        bountyListNode = BountyListNode(nodeCtx)
        bountyDetailNode = BountyDetailNode(nodeCtx)
        battleLoadingNode = BattleLoadingNode(nodeCtx)
        battleNode = FightNode(nodeCtx)
        settlementNode = SettlementNode(nodeCtx)
        recruitInviteNode = RecruitInviteNode(nodeCtx)
        defeatNode = DefeatNode(nodeCtx)
        recoveryNode = RecoveryNode(nodeCtx)
    }

    companion object {
        private const val MAX_GLOBAL_FAIL = 3
    }

    // ══════════════════════════════════════════
    //  公开入口（保持签名不变，向后兼容）
    // ══════════════════════════════════════════

    suspend fun runLoop(
        configs: List<BountyConfig>,
        onProgress: ((Map<BountyGrade, Pair<Int, Int>>) -> Unit)? = null
    ): Boolean {
        val ctx = buildContext(configs)
        globalFailCount = 0
        emitProgress(ctx, onProgress)

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
                    ctx.currentPhase = nextPhase
                    globalFailCount = 0
                    emitProgress(ctx, onProgress)
                    val pageEvent = phaseToEvent(nextPhase)
                    if (pageEvent != null) onPageEvent?.invoke(pageEvent)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log("Pipeline 异常于 ${ctx.currentPhase}: ${e.message}")
                LogUtil.e(TAG, "Pipeline 异常", e)
                globalFailCount++
                log("整体判定失败 ($globalFailCount/$MAX_GLOBAL_FAIL)")
                ctx.currentPhase = GamePhase.RECOVERY
            }
        }

        val allDone = ctx.allCompleted
        if (globalFailCount >= MAX_GLOBAL_FAIL) {
            val msg = "整体判定3次失败，脚本停止"
            log(msg)
            LogUtil.e(TAG, msg)
            writeCrashLog(ctx)
        }
        LogUtil.i(TAG, "流水线结束: allCompleted=$allDone, globalFailCount=$globalFailCount")
        return allDone
    }

    // ══════════════════════════════════════════
    //  节点调度（按游戏页面分派）
    // ══════════════════════════════════════════

    private suspend fun dispatchPhase(ctx: GameContext): GamePhase? {
        return when (ctx.currentPhase) {
            GamePhase.IDLE, GamePhase.LOBBY, GamePhase.CHAT -> hallNode.execute(ctx)
            GamePhase.RECRUIT_LIST -> bountyListNode.execute(ctx)
            GamePhase.RECRUIT_INVITE -> recruitInviteNode.execute(ctx)
            GamePhase.BOUNTY_DETAIL -> bountyDetailNode.execute(ctx)
            GamePhase.BATTLE_LOADING -> battleLoadingNode.execute(ctx)
            GamePhase.FIGHT -> battleNode.execute(ctx)
            GamePhase.DEFEAT -> defeatNode.execute(ctx)
            GamePhase.SETTLEMENT -> settlementNode.execute(ctx)
            GamePhase.RECOVERY -> recoveryNode.execute(ctx)
            GamePhase.DONE -> GamePhase.DONE
        }
    }

    private fun phaseToEvent(phase: GamePhase): String? {
        return when (phase) {
            GamePhase.LOBBY, GamePhase.IDLE -> "进入大厅"
            GamePhase.RECRUIT_LIST -> "进入招募列表"
            GamePhase.BOUNTY_DETAIL, GamePhase.BOUNTY_DETAIL -> "队伍房间准备就绪"
            GamePhase.BATTLE_LOADING -> "战斗加载"
            GamePhase.FIGHT -> "⚔ 战斗开始"
            GamePhase.SETTLEMENT -> "结算领奖"
            GamePhase.DONE -> "🎉 全部悬赏完成"
            else -> null
        }
    }

    // ══════════════════════════════════════════
    //  辅助方法
    // ══════════════════════════════════════════

    private fun buildContext(configs: List<BountyConfig>): GameContext {
        val enabled = configs.filter { it.enabled }
        val grades = enabled.map { it.grade }
        val chaseDreamGrades = enabled.filter { it.chaseDream }.map { it.grade }.toSet()
        return GameContext(
            currentPhase = GamePhase.IDLE,
            activeGrades = grades,
            totalGrades = grades,
            runCounts = enabled.associate { it.grade to 0 }.toMutableMap(),
            targetRuns = enabled.associate { it.grade to it.targetRuns },
            chaseDreamGrades = chaseDreamGrades
        )
    }

    private fun emitProgress(ctx: GameContext, onProgress: ((Map<BountyGrade, Pair<Int, Int>>) -> Unit)?) {
        if (onProgress == null) return
        // 使用 totalGrades（含已完成）确保悬浮窗始终显示所有等级进度
        val progress = ctx.totalGrades.associateWith { grade ->
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
        accessibility?.clickAt(coord.first, coord.second)
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
