package com.example.ninjaau.model

import org.junit.Assert.*
import org.junit.Test

class BountyGradeTest {

    @Test
    fun `all 12 grades exist`() {
        assertEquals(12, BountyGrade.entries.size)
    }

    @Test
    fun `SS+ has 6 level icon paths`() {
        val paths = BountyGrade.SS_PLUS.levelIconPaths()
        assertEquals(6, paths.size)
        assertTrue(paths.all { it.contains("lv") && it.endsWith(".png") })
    }

    @Test
    fun `non SS+ grades have single level icon path`() {
        val singlePathGrades = listOf(
            BountyGrade.D, BountyGrade.C, BountyGrade.B,
            BountyGrade.A, BountyGrade.A_PLUS,
            BountyGrade.S, BountyGrade.S_PLUS,
            BountyGrade.SS, BountyGrade.NSS_PLUS,
            BountyGrade.NS, BountyGrade.NA
        )
        for (grade in singlePathGrades) {
            assertEquals("${grade.displayName} should have 1 path", 1, grade.levelIconPaths().size)
        }
    }

    @Test
    fun `daily excludes NSS+, NS, NA`() {
        val daily = BountyGrade.daily()
        assertFalse(daily.contains(BountyGrade.NSS_PLUS))
        assertFalse(daily.contains(BountyGrade.NS))
        assertFalse(daily.contains(BountyGrade.NA))
        assertEquals(9, daily.size)
    }

    @Test
    fun `event only contains NSS+, NS, NA`() {
        val event = BountyGrade.event()
        assertEquals(3, event.size)
        assertTrue(event.contains(BountyGrade.NSS_PLUS))
        assertTrue(event.contains(BountyGrade.NS))
        assertTrue(event.contains(BountyGrade.NA))
    }

    @Test
    fun `sorted returns all grades by priority ascending`() {
        val sorted = BountyGrade.sorted()
        assertEquals(12, sorted.size)
        for (i in 1 until sorted.size) {
            assertTrue(sorted[i].priority >= sorted[i - 1].priority)
        }
    }

    @Test
    fun `grade icon path format is correct`() {
        assertEquals("templates/bounty_list/SS+.png", BountyGrade.SS_PLUS.gradeIconPath())
        assertEquals("templates/bounty_list/D.png", BountyGrade.D.gradeIconPath())
    }

    @Test
    fun `isEvent is true only for N series`() {
        assertTrue(BountyGrade.NSS_PLUS.isEvent)
        assertTrue(BountyGrade.NS.isEvent)
        assertTrue(BountyGrade.NA.isEvent)
        assertFalse(BountyGrade.SS_PLUS.isEvent)
        assertFalse(BountyGrade.SS.isEvent)
        assertFalse(BountyGrade.D.isEvent)
    }

    @Test
    fun `canChaseDream for S and above`() {
        assertTrue(BountyGrade.S.canChaseDream)
        assertTrue(BountyGrade.S_PLUS.canChaseDream)
        assertTrue(BountyGrade.SS_PLUS.canChaseDream)
        assertTrue(BountyGrade.NSS_PLUS.canChaseDream)
        assertTrue(BountyGrade.NS.canChaseDream)
        assertTrue(BountyGrade.NA.canChaseDream)
        assertFalse(BountyGrade.A.canChaseDream)
        assertFalse(BountyGrade.B.canChaseDream)
    }

    @Test
    fun `each grade has correct key and display name`() {
        assertEquals("ss_plus", BountyGrade.SS_PLUS.key)
        assertEquals("SS+", BountyGrade.SS_PLUS.displayName)
        assertEquals("d", BountyGrade.D.key)
        assertEquals("D", BountyGrade.D.displayName)
        assertEquals("nss_plus", BountyGrade.NSS_PLUS.key)
        assertEquals("NSS+", BountyGrade.NSS_PLUS.displayName)
    }

    @Test
    fun `SS+ level variants are 105 to 130`() {
        assertEquals(listOf(105, 110, 115, 120, 125, 130), BountyGrade.SS_PLUS.levelVariants)
    }

    @Test
    fun `non SS+ grades have empty level variants`() {
        val nonSSPlus = BountyGrade.entries.filter { it != BountyGrade.SS_PLUS }
        for (grade in nonSSPlus) {
            assertTrue("${grade.displayName} should have empty levelVariants", grade.levelVariants.isEmpty())
        }
    }
}
