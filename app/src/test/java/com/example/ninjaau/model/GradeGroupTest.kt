package com.example.ninjaau.model

import org.junit.Assert.*
import org.junit.Test

class GradeGroupTest {

    @Test
    fun `A_GROUP has correct members and default runs`() {
        val members = GradeGroup.A_GROUP.members()
        assertEquals(2, members.size)
        assertTrue(members.contains(BountyGrade.A))
        assertTrue(members.contains(BountyGrade.A_PLUS))
        assertEquals(3, GradeGroup.A_GROUP.defaultRuns)
    }

    @Test
    fun `S_GROUP has correct members and default runs`() {
        val members = GradeGroup.S_GROUP.members()
        assertEquals(2, members.size)
        assertTrue(members.contains(BountyGrade.S))
        assertTrue(members.contains(BountyGrade.S_PLUS))
        assertEquals(5, GradeGroup.S_GROUP.defaultRuns)
    }

    @Test
    fun `SS group has 1 member and 1 default run`() {
        val members = GradeGroup.SS.members()
        assertEquals(1, members.size)
        assertTrue(members.contains(BountyGrade.SS))
        assertEquals(1, GradeGroup.SS.defaultRuns)
    }

    @Test
    fun `SS_PLUS group has 1 member and 1 default run`() {
        val members = GradeGroup.SS_PLUS.members()
        assertEquals(1, members.size)
        assertTrue(members.contains(BountyGrade.SS_PLUS))
        assertEquals(1, GradeGroup.SS_PLUS.defaultRuns)
    }

    @Test
    fun `totalRuns sums correctly`() {
        val runCounts = mapOf(
            BountyGrade.A to 2,
            BountyGrade.A_PLUS to 1
        )
        assertEquals(3, GradeGroup.A_GROUP.totalRuns(runCounts))
    }

    @Test
    fun `totalRuns returns 0 for empty counts`() {
        assertEquals(0, GradeGroup.A_GROUP.totalRuns(emptyMap()))
    }

    @Test
    fun `totalRuns only counts members of the group`() {
        val runCounts = mapOf(
            BountyGrade.A to 2,
            BountyGrade.S to 5
        )
        assertEquals(2, GradeGroup.A_GROUP.totalRuns(runCounts))
    }

    @Test
    fun `isComplete when total runs equals default`() {
        val runCounts = mapOf(
            BountyGrade.A to 2,
            BountyGrade.A_PLUS to 1
        )
        assertTrue(GradeGroup.A_GROUP.isComplete(runCounts))
    }

    @Test
    fun `isComplete when total runs exceeds default`() {
        val runCounts = mapOf(
            BountyGrade.A to 3,
            BountyGrade.A_PLUS to 1
        )
        assertTrue(GradeGroup.A_GROUP.isComplete(runCounts))
    }

    @Test
    fun `is not complete when total runs less than default`() {
        val runCounts = mapOf(
            BountyGrade.A to 1,
            BountyGrade.A_PLUS to 1
        )
        assertFalse(GradeGroup.A_GROUP.isComplete(runCounts))
    }

    @Test
    fun `isComplete with empty counts is false`() {
        assertFalse(GradeGroup.A_GROUP.isComplete(emptyMap()))
    }
}
