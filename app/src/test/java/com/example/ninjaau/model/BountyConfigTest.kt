package com.example.ninjaau.model

import org.junit.Assert.*
import org.junit.Test

class BountyConfigTest {

    @Test
    fun `default target runs equals grade default runs`() {
        val config = BountyConfig(grade = BountyGrade.A)
        assertEquals(BountyGrade.A.defaultRuns, config.targetRuns)
    }

    @Test
    fun `default completed runs is 0`() {
        val config = BountyConfig(grade = BountyGrade.SS_PLUS)
        assertEquals(0, config.completedRuns)
    }

    @Test
    fun `default chaseDream is false`() {
        val config = BountyConfig(grade = BountyGrade.S)
        assertFalse(config.chaseDream)
    }

    @Test
    fun `default enabled is false`() {
        val config = BountyConfig(grade = BountyGrade.D)
        assertFalse(config.enabled)
    }

    @Test
    fun `defaultList returns 12 configs`() {
        val list = BountyConfig.defaultList()
        assertEquals(12, list.size)
    }

    @Test
    fun `defaultList daily grades are enabled`() {
        val list = BountyConfig.defaultList()
        val dailyConfigs = list.filter { !it.grade.isEvent }
        assertTrue(dailyConfigs.all { it.enabled })
        assertEquals(9, dailyConfigs.size)
    }

    @Test
    fun `defaultList event grades are disabled`() {
        val list = BountyConfig.defaultList()
        val eventConfigs = list.filter { it.grade.isEvent }
        assertTrue(eventConfigs.all { !it.enabled })
        assertEquals(3, eventConfigs.size)
    }

    @Test
    fun `defaultList is sorted by priority`() {
        val list = BountyConfig.defaultList()
        for (i in 1 until list.size) {
            assertTrue(list[i].grade.priority >= list[i - 1].grade.priority)
        }
    }

    @Test
    fun `custom target runs overrides default`() {
        val config = BountyConfig(grade = BountyGrade.A, targetRuns = 10)
        assertEquals(10, config.targetRuns)
    }
}
