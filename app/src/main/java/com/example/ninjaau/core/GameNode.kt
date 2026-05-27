package com.example.ninjaau.core

import android.graphics.Bitmap
import com.example.ninjaau.core.recognition.SceneDetector
import com.example.ninjaau.model.GameContext
import com.example.ninjaau.model.GamePhase

/**
 * 游戏节点 — 参考 MAA (MeoAssistanceArknights) 的节点模式设计。
 *
 * 每个节点封装一个可识别游戏界面的完整逻辑：
 * 1. [recognize] — 判断当前截图是否属于该节点
 * 2. [execute] — 执行该界面的操作（点击、等待、修改上下文等）
 *
 * 主循环：截图 → 根据 currentPhase 分派到对应节点 → execute → 下一个 phase
 */
interface GameNode {
    /**
     * 识别当前截图是否属于该节点管辖的界面。
     * @param screen 当前屏幕截图
     * @return 识别结果：是否匹配，以及可选的命中坐标
     */
    suspend fun recognize(screen: Bitmap): RecognizeResult

    /**
     * 执行该节点的操作逻辑。
     * 可能包含内部循环（如招募扫描的 100ms 轮询），直到需要切换阶段才返回。
     * @param ctx 游戏上下文（可修改 runCounts、currentBounty 等）
     * @return 下一个 GamePhase，null 表示留在当前节点继续
     */
    suspend fun execute(ctx: GameContext): GamePhase?
}

/** 识别结果 */
data class RecognizeResult(
    val matched: Boolean,
    val coord: Pair<Float, Float>? = null
)

/**
 * 节点超时异常 — 节点连续30秒无任何匹配识别时抛出，
 * 由 WorkflowEngine 捕获后进入 RECOVERY 流程。
 */
class NodeTimeoutException : RuntimeException("节点无匹配超时")

/**
 * 节点无匹配超时检测。
 * 距上次匹配超过 [timeoutMs] 则抛出 [NodeTimeoutException]，
 * 回到主流程由 WorkflowEngine 统一处理。
 *
 * 每个节点在 execute 循环开始处初始化 lastMatchMs，
 * 每次匹配成功时重置，无匹配路径调用此方法。
 */
fun checkNodeTimeout(lastMatchMs: Long, timeoutMs: Long = 30_000L) {
    if (lastMatchMs > 0L && System.currentTimeMillis() - lastMatchMs >= timeoutMs) {
        throw NodeTimeoutException()
    }
}

/**
 * 节点执行所需的上下文工具。
 * WorkflowEngine 在初始化时构造此对象并注入所有节点。
 */
class NodeContext(
    val detector: SceneDetector,
    val captureBitmap: suspend () -> Bitmap?,
    val click: (Pair<Float, Float>) -> Unit,
    val log: (String) -> Unit,
    val onPageEvent: ((String) -> Unit)?,
    val delay: suspend (Long) -> Unit
)
