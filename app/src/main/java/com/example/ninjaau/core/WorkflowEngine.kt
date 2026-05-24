package com.example.ninjaau.core

import android.content.Context
import android.graphics.Bitmap
import com.example.ninjaau.core.accessibility.NinjaAccessibilityService
import com.example.ninjaau.core.capture.ScreenCapture
import com.example.ninjaau.core.node.*
import com.example.ninjaau.core.recognition.SceneDetector
import com.example.ninjaau.core.util.LogUtil
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
 *   大厅/聊天 → [HallNode] → 招募列表 → [RecruitListNode] → 悬赏详情 → [BountyDetailNode]
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
    private val recruitListNode: RecruitListNode
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
            clickOutside = { screen -> clickOutside(screen) },
            detectCurrentPage = { screen -> detectCurrentPage(screen) },
            log = { log(it) },
            onPageEvent = onPageEvent,
            delay = { delay(it) }
        )
        hallNode = HallNode(nodeCtx)
        recruitListNode = RecruitListNode(nodeCtx)
        bountyDetailNode = BountyDetailNode(nodeCtx)
        battleLoadingNode = BattleLoadingNode(nodeCtx)
        battleNode = FightNode(nodeCtx)
        settlementNode = SettlementNode(nodeCtx)
        recruitInviteNode = RecruitInviteNode(nodeCtx)
        defeatNode = DefeatNode(nodeCtx)
        recoveryNode = RecoveryNode()
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

                val nextPhase = dispatchPhase(ctx)
                if (nextPhase != null) {
                    ctx.currentPhase = nextPhase
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
            GamePhase.RECRUIT_LIST -> recruitListNode.execute(ctx)
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
        return GameContext(
            currentPhase = GamePhase.IDLE,
            activeGrades = enabled.map { it.grade },
            runCounts = enabled.associate { it.grade to 0 }.toMutableMap(),
            targetRuns = enabled.associate { it.grade to it.targetRuns }
        )
    }

    private fun emitProgress(ctx: GameContext, onProgress: ((Map<BountyGrade, Pair<Int, Int>>) -> Unit)?) {
        if (onProgress == null) return
        val progress = ctx.activeGrades.associateWith { grade ->
            Pair(ctx.runCounts[grade] ?: 0, ctx.targetRuns[grade] ?: grade.defaultRuns)
        }
        onProgress(progress)
    }

    /** 全量页面检测：使用 SCOPE_ALL 兜底识别当前所在页面，返回对应 GamePhase */
    private suspend fun detectCurrentPage(screen: Bitmap): GamePhase? {
        val (state, _) = detector.detectWithCoord(screen)
        if (state == ScreenState.UNKNOWN) return null
        return when (state) {
            ScreenState.CHAT_ICON, ScreenState.RECRUIT_TAB -> GamePhase.IDLE
            ScreenState.READY_BUTTON -> GamePhase.BOUNTY_DETAIL
            ScreenState.BATTLE_LOADING -> GamePhase.BATTLE_LOADING
            ScreenState.WARNING, ScreenState.ULTIMATE_SKILL, ScreenState.WEAPON_SKILL, ScreenState.DEFEAT_POPUP -> GamePhase.FIGHT
            ScreenState.SETTLEMENT_POPUP, ScreenState.CONFIRM_BUTTON -> GamePhase.SETTLEMENT
            ScreenState.DAILY_LIMIT, ScreenState.EXIT_CONFIRM, ScreenState.BACK_BUTTON -> GamePhase.LOBBY
            else -> null
        }
    }

    /**
     * 点击空白区域关闭弹窗。
     * 策略:
     * 1. SETTLEMENT_POPUP → 弹窗底部空白处点击
     * 2. CONFIRM_BUTTON → 弹窗上方空白处点击
     * 3. 兜底 → 屏幕底部中央
     */
    private suspend fun clickOutside(screen: Bitmap? = null) {
        val display = context.resources.displayMetrics
        val w = display.widthPixels.toFloat()
        val h = display.heightPixels.toFloat()

        if (screen != null) {
            if (detector.matchTemplate(screen, ScreenState.SETTLEMENT_POPUP) != null) {
                accessibility?.clickAt(w / 2f, h * 0.88f)
                delay(300)
                return
            }
            if (detector.matchTemplate(screen, ScreenState.CONFIRM_BUTTON) != null) {
                accessibility?.clickAt(w / 2f, h * 0.2f)
                delay(300)
                return
            }
        }

        accessibility?.clickAt(w / 2f, h * 0.88f)
        delay(300)
    }

    private suspend fun captureBitmap(): Bitmap? {
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
