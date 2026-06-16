package com.example.ninjaau.model

import org.junit.Assert.*
import org.junit.Test

class GameContextTest {

    @Test
    fun `allCompleted when all grades meet target`() {
        val ctx = GameContext(
            activeGrades = listOf(BountyGrade.A, BountyGrade.S),
            runCounts = mutableMapOf(BountyGrade.A to 3, BountyGrade.S to 5),
            targetRuns = mapOf(BountyGrade.A to 3, BountyGrade.S to 5)
        )
        assertTrue(ctx.allCompleted)
    }

    @Test
    fun `allCompleted false when some grades below target`() {
        val ctx = GameContext(
            activeGrades = listOf(BountyGrade.A, BountyGrade.S),
            runCounts = mutableMapOf(BountyGrade.A to 3, BountyGrade.S to 3),
            targetRuns = mapOf(BountyGrade.A to 3, BountyGrade.S to 5)
        )
        assertFalse(ctx.allCompleted)
    }

    @Test
    fun `allCompleted with empty activeGrades is true`() {
        val ctx = GameContext(activeGrades = emptyList())
        assertTrue(ctx.allCompleted)
    }

    @Test
    fun `allCompleted chaseDream grades are excluded`() {
        val ctx = GameContext(
            activeGrades = listOf(BountyGrade.S, BountyGrade.A),
            runCounts = mutableMapOf(BountyGrade.S to 0, BountyGrade.A to 3),
            targetRuns = mapOf(BountyGrade.S to 5, BountyGrade.A to 3),
            chaseDreamGrades = setOf(BountyGrade.S)
        )
        // S is chaseDream → excluded, A meets target → allCompleted = true
        assertTrue(ctx.allCompleted)
    }

    @Test
    fun `allCompleted with no runCounts and no targets uses defaults`() {
        val ctx = GameContext(
            activeGrades = listOf(BountyGrade.D),
            runCounts = mutableMapOf(),
            targetRuns = emptyMap()
        )
        // D.defaultRuns = 5, runCounts[D] = 0, 0 < 5 → false
        assertFalse(ctx.allCompleted)
    }

    @Test
    fun `allCompleted with exact default runs`() {
        val ctx = GameContext(
            activeGrades = listOf(BountyGrade.D),
            runCounts = mutableMapOf(BountyGrade.D to 5),
            targetRuns = emptyMap()
        )
        assertTrue(ctx.allCompleted)
    }

    @Test
    fun `BusinessLine enum values`() {
        assertEquals(2, BusinessLine.entries.size)
        assertTrue(BusinessLine.entries.contains(BusinessLine.DAILY))
        assertTrue(BusinessLine.entries.contains(BusinessLine.PERSONAL))
    }

    @Test
    fun `GamePhase has 14 values`() {
        assertEquals(14, GamePhase.entries.size)
    }
}
