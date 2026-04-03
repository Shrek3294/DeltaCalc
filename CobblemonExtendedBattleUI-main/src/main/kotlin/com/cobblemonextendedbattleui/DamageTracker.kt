package com.cobblemonextendedbattleui

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks HP changes and injects them into the battle log.
 * Called from BattleHealthChangeHandlerMixin when HP changes are detected.
 *
 * Tracks last known HP percentage per Pokemon by UUID for reliable damage calculations.
 * HP baselines are pre-populated when Pokemon switch in (via BattleSwitchHandlerMixin),
 * ensuring we always have an accurate baseline before any HP changes are processed.
 */
object DamageTracker {

    /**
     * Tracks last known HP percentage (0-100) per Pokemon, keyed by UUID.
     * Using UUID ensures correct tracking even when:
     * - Pokemon switch back into the same slot (same PNX, different Pokemon)
     * - Multiple Pokemon have been in the same position during a battle
     */
    private val lastKnownHpPercent = ConcurrentHashMap<UUID, Float>()

    /**
     * Get the last known HP percentage for a Pokemon.
     * @param uuid The Pokemon's UUID
     * @return The last known HP percentage (0-100), or null if not tracked yet
     */
    fun getLastKnownHpPercent(uuid: UUID): Float? = lastKnownHpPercent[uuid]

    /**
     * Update the tracked HP percentage for a Pokemon.
     * @param uuid The Pokemon's UUID
     * @param percent The new HP percentage (0-100)
     */
    fun updateHpPercent(uuid: UUID, percent: Float) {
        lastKnownHpPercent[uuid] = percent
        CobblemonExtendedBattleUI.LOGGER.debug("DamageTracker: Updated HP for $uuid to $percent%")
    }

    /**
     * Pre-populate HP tracking for a Pokemon that just switched in.
     * This is called from BattleSwitchHandlerMixin with the Pokemon's initial HP.
     * @param uuid The Pokemon's UUID
     * @param hpValue The HP value from the switch packet
     * @param maxHp The max HP from the switch packet
     * @param isFlat Whether the HP values are flat (absolute) or percentage-based
     */
    fun initializeHpFromSwitch(uuid: UUID, hpValue: Float, maxHp: Float, isFlat: Boolean) {
        val hpPercent = if (isFlat && maxHp > 0) {
            (hpValue / maxHp) * 100f
        } else {
            // Non-flat values are already 0.0-1.0 percentages
            hpValue * 100f
        }
        lastKnownHpPercent[uuid] = hpPercent
        CobblemonExtendedBattleUI.LOGGER.debug("DamageTracker: Initialized HP for $uuid at $hpPercent% (from switch)")
    }

    fun recordDamage(targetName: String, damagePercent: Float) {
        BattleLog.addHpChangeEntry("  → ${formatPercent(damagePercent)}% to $targetName")
    }

    fun recordHealing(targetName: String, healPercent: Float) {
        BattleLog.addHpChangeEntry("  → +${formatPercent(healPercent)}% to $targetName", isHealing = true)
    }

    /**
     * Format percentage for display - shows one decimal place to avoid rounding errors
     * that make multi-hit damage not add up correctly.
     * Strips trailing ".0" for clean display of whole numbers.
     */
    private fun formatPercent(percent: Float): String {
        val rounded = kotlin.math.round(percent * 10) / 10  // Round to 1 decimal
        return if (rounded == rounded.toLong().toFloat()) {
            // Whole number - display without decimal
            rounded.toLong().toString()
        } else {
            // Has decimal - show one decimal place
            String.format("%.1f", rounded)
        }
    }

    fun clear() {
        lastKnownHpPercent.clear()
        CobblemonExtendedBattleUI.LOGGER.debug("DamageTracker: Cleared all HP tracking")
    }
}
