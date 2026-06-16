
package com.example.ninjaau.core.floating

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 悬浮球定位逻辑的纯函数单元测试。
 * 不依赖 Android 框架，直接验证屏幕坐标计算。
 *
 * 模拟器分辨率 2560x1440，球宽 80px。
 */
class FloatingBallPositionTest {

    private val screenWidth = 2560
    private val ballWidth = 80
    private val menuWidth = 340 // 4 个按钮 * (60+20) + padding

    // ══════════════════════════════════════════
    //  isBallOnRight
    // ══════════════════════════════════════════

    @Test
    fun `ball at left edge is not on right`() {
        assertFalse(FloatingWindowService.isBallOnRight(0, ballWidth, screenWidth))
    }

    @Test
    fun `ball at left-center is not on right`() {
        // x=1200, 球中心=1240, 屏幕中心=1280 → 左侧
        assertFalse(FloatingWindowService.isBallOnRight(1200, ballWidth, screenWidth))
    }

    @Test
    fun `ball at right-center is on right`() {
        // x=1280, 球中心=1320, 屏幕中心=1280 → 右侧
        assertTrue(FloatingWindowService.isBallOnRight(1280, ballWidth, screenWidth))
    }

    @Test
    fun `ball at right edge is on right`() {
        assertTrue(FloatingWindowService.isBallOnRight(screenWidth - ballWidth, ballWidth, screenWidth))
    }

    // ══════════════════════════════════════════
    //  calcRootX — 核心：菜单偏移
    // ══════════════════════════════════════════

    @Test
    fun `root x equals ball x when menu hidden`() {
        assertEquals(0, FloatingWindowService.calcRootX(0, ballWidth, screenWidth, false, 0))
        assertEquals(1000, FloatingWindowService.calcRootX(1000, ballWidth, screenWidth, false, 0))
        assertEquals(screenWidth - ballWidth, FloatingWindowService.calcRootX(screenWidth - ballWidth, ballWidth, screenWidth, false, 0))
    }

    @Test
    fun `root x shifts left by menu width when menu visible on right`() {
        // 球在右边缘 x=2480，菜单宽 340 → 根视图 x=2140
        val ballX = screenWidth - ballWidth  // 2480
        val expected = ballX - menuWidth     // 2140
        assertEquals(expected, FloatingWindowService.calcRootX(ballX, ballWidth, screenWidth, true, menuWidth))
    }

    @Test
    fun `root x stays at ball x when menu visible on left`() {
        // 球在左侧 x=0，菜单在右侧展开 → 根视图不需要偏移
        assertEquals(0, FloatingWindowService.calcRootX(0, ballWidth, screenWidth, true, menuWidth))
    }

    @Test
    fun `ball stays at right edge when right menu opens`() {
        // 验证：球的屏幕位置 = 根视图 x + 菜单宽 + 球宽 = 屏幕宽度
        val ballX = screenWidth - ballWidth
        val rootX = FloatingWindowService.calcRootX(ballX, ballWidth, screenWidth, true, menuWidth)
        val ballScreenPos = rootX + menuWidth + ballWidth
        assertEquals(screenWidth, ballScreenPos)
    }

    // ══════════════════════════════════════════
    //  calcSnapTarget
    // ════════════════════════════════════════

    @Test
    fun `snap target is right edge when ball on right`() {
        assertEquals(
            screenWidth - ballWidth,
            FloatingWindowService.calcSnapTarget(2000, ballWidth, screenWidth)
        )
    }

    @Test
    fun `snap target is left edge when ball on left`() {
        assertEquals(0, FloatingWindowService.calcSnapTarget(500, ballWidth, screenWidth))
    }

    // ══════════════════════════════════════════
    //  calcSideHideTarget
    // ════════════════════════════════════════

    @Test
    fun `side hide target is right edge when ball on right`() {
        // 右侧：向右滑出，保留 VISIBLE_TAB(30) 可见
        assertEquals(
            screenWidth - FloatingWindowService.VISIBLE_TAB,
            FloatingWindowService.calcSideHideTarget(screenWidth - ballWidth, ballWidth, screenWidth)
        )
    }

    @Test
    fun `side hide target is left edge when ball on left`() {
        // 左侧：向左滑出，保留 VISIBLE_TAB(30) 可见
        assertEquals(
            -(ballWidth - FloatingWindowService.VISIBLE_TAB),
            FloatingWindowService.calcSideHideTarget(0, ballWidth, screenWidth)
        )
    }

    @Test
    fun `side hide target is NOT hardcoded to left side`() {
        // 关键回归测试：右侧隐藏不再向左滑出
        val rightTarget = FloatingWindowService.calcSideHideTarget(
            screenWidth - ballWidth, ballWidth, screenWidth
        )
        assertTrue("右侧隐藏目标应在屏幕右侧", rightTarget > 0)
    }

    // ══════════════════════════════════════════
    //  calcRestoreTarget
    // ══════════════════════════════════════════

    @Test
    fun `restore target is right edge when ball was on right`() {
        assertEquals(
            screenWidth - ballWidth,
            FloatingWindowService.calcRestoreTarget(screenWidth - ballWidth, ballWidth, screenWidth)
        )
    }

    @Test
    fun `restore target is left edge when ball was on left`() {
        assertEquals(
            0,
            FloatingWindowService.calcRestoreTarget(0, ballWidth, screenWidth)
        )
    }

    @Test
    fun `restore target is NOT always zero`() {
        // 关键回归测试：右侧恢复不再回到 x=0
        val rightRestore = FloatingWindowService.calcRestoreTarget(
            screenWidth - ballWidth, ballWidth, screenWidth
        )
        assertTrue("右侧恢复目标不应为 0", rightRestore > 0)
        assertEquals(screenWidth - ballWidth, rightRestore)
    }

    // ══════════════════════════════════════════
    //  端到端场景模拟
    // ══════════════════════════════════════════

    @Test
    fun `scenario - drag ball from left to right then open menu`() {
        // 1. 球在左侧 x=0
        var ballX = 0
        assertFalse(FloatingWindowService.isBallOnRight(ballX, ballWidth, screenWidth))

        // 2. 拖到右侧，松手后吸附到右边缘
        ballX = 2000  // 拖动结束位置
        val snapTarget = FloatingWindowService.calcSnapTarget(ballX, ballWidth, screenWidth)
        assertEquals(screenWidth - ballWidth, snapTarget)
        ballX = snapTarget  // 2480

        // 3. 打开菜单，根视图左移
        val rootX = FloatingWindowService.calcRootX(ballX, ballWidth, screenWidth, true, menuWidth)
        assertEquals(2140, rootX)

        // 4. 验证球仍在右边缘可见
        val ballVisibleX = rootX + menuWidth + ballWidth
        assertEquals(screenWidth, ballVisibleX)
    }

    @Test
    fun `scenario - side hide from right then restore`() {
        val ballX = screenWidth - ballWidth  // 2480

        // 隐藏：向右滑出
        val hideTarget = FloatingWindowService.calcSideHideTarget(ballX, ballWidth, screenWidth)
        assertEquals(2530, hideTarget)  // screenWidth - 30

        // 恢复：回到右边缘
        val restoreTarget = FloatingWindowService.calcRestoreTarget(hideTarget, ballWidth, screenWidth)
        assertEquals(screenWidth - ballWidth, restoreTarget)
    }

    @Test
    fun `scenario - side hide from left then restore`() {
        val ballX = 0

        // 隐藏：向左滑出
        val hideTarget = FloatingWindowService.calcSideHideTarget(ballX, ballWidth, screenWidth)
        assertEquals(-50, hideTarget)  // -(80 - 30)

        // 恢复：回到左边缘
        val restoreTarget = FloatingWindowService.calcRestoreTarget(hideTarget, ballWidth, screenWidth)
        assertEquals(0, restoreTarget)
    }
}
