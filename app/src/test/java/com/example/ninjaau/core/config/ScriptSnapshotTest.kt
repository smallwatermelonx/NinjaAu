package com.example.ninjaau.core.config

import com.example.ninjaau.model.BountyConfig
import com.example.ninjaau.model.BountyGrade
import org.junit.Assert.*
import org.junit.Test

class ScriptSnapshotTest {

    @Test
    fun `enabledBountyConfigs filters only enabled`() {
        val configs = listOf(
            BountyConfig(grade = BountyGrade.A, enabled = true),
            BountyConfig(grade = BountyGrade.B, enabled = false),
            BountyConfig(grade = BountyGrade.S, enabled = true)
        )
        val snapshot = ScriptSnapshot(
            bountyConfigs = configs,
            personalConfigs = emptyList(),
            nsConfigs = emptyList(),
            dailyEnabled = true,
            personalEnabled = false,
            nsEnabled = false,
            inviteCheckEnabled = false
        )
        assertEquals(2, snapshot.enabledBountyConfigs.size)
        assertTrue(snapshot.enabledBountyConfigs.all { it.enabled })
    }

    @Test
    fun `enabledPersonalConfigs filters only enabled`() {
        val configs = listOf(
            BountyConfig(grade = BountyGrade.A, enabled = false),
            BountyConfig(grade = BountyGrade.SS, enabled = true)
        )
        val snapshot = ScriptSnapshot(
            bountyConfigs = emptyList(),
            personalConfigs = configs,
            nsConfigs = emptyList(),
            dailyEnabled = false,
            personalEnabled = true,
            nsEnabled = false,
            inviteCheckEnabled = false
        )
        assertEquals(1, snapshot.enabledPersonalConfigs.size)
        assertEquals(BountyGrade.SS, snapshot.enabledPersonalConfigs[0].grade)
    }

    @Test
    fun `enabledNsConfigs filters only enabled`() {
        val configs = listOf(
            BountyConfig(grade = BountyGrade.NS, enabled = true),
            BountyConfig(grade = BountyGrade.NA, enabled = false)
        )
        val snapshot = ScriptSnapshot(
            bountyConfigs = emptyList(),
            personalConfigs = emptyList(),
            nsConfigs = configs,
            dailyEnabled = false,
            personalEnabled = false,
            nsEnabled = true,
            inviteCheckEnabled = false
        )
        assertEquals(1, snapshot.enabledNsConfigs.size)
        assertEquals(BountyGrade.NS, snapshot.enabledNsConfigs[0].grade)
    }

    @Test
    fun `all empty configs returns empty filtered lists`() {
        val snapshot = ScriptSnapshot(
            bountyConfigs = emptyList(),
            personalConfigs = emptyList(),
            nsConfigs = emptyList(),
            dailyEnabled = true,
            personalEnabled = true,
            nsEnabled = true,
            inviteCheckEnabled = true
        )
        assertTrue(snapshot.enabledBountyConfigs.isEmpty())
        assertTrue(snapshot.enabledPersonalConfigs.isEmpty())
        assertTrue(snapshot.enabledNsConfigs.isEmpty())
    }
}
