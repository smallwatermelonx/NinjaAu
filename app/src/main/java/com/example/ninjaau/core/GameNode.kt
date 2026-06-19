package com.example.ninjaau.core

import android.graphics.Bitmap
import com.example.ninjaau.core.recognition.SceneDetector
import com.example.ninjaau.model.GameContext
import com.example.ninjaau.model.GamePhase

interface GameNode {
    suspend fun execute(ctx: GameContext): GamePhase?
}

class NodeTimeoutException : RuntimeException("节点无匹配超时")

/** 铃声播放期间脚本被暂停/停止，需要中断当前节点 */
class AlarmStopException : RuntimeException("铃声已停止，脚本暂停")

fun checkNodeTimeout(lastMatchMs: Long, timeoutMs: Long = 30_000L) {
    if (lastMatchMs > 0L && System.currentTimeMillis() - lastMatchMs >= timeoutMs) {
        throw NodeTimeoutException()
    }
}

class NodeContext(
    val detector: SceneDetector,
    val captureBitmap: suspend () -> Bitmap?,
    val click: (Pair<Float, Float>) -> Unit,
    val log: (String) -> Unit,
    val onPageEvent: ((String) -> Unit)?,
    val delay: suspend (Long) -> Unit,
    val playAlarm: () -> Unit,
    val stopAlarm: () -> Unit
)
