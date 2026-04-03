package com.cobblemonextendedbattleui

import net.minecraft.client.MinecraftClient
import java.io.File

/**
 * Parses Showdown's items.js to extract held item power boost information.
 * This allows the mod to dynamically detect which items boost move power
 * without hardcoding a list.
 */
object ItemPowerBoostParser {

    /**
     * Data class representing an item that boosts move power.
     */
    data class ItemPowerBoost(
        val itemId: String,           // Showdown item ID (e.g., "charcoal")
        val displayName: String,      // Display name (e.g., "Charcoal")
        val boostedType: String?,     // Type boosted (null for Life Orb which boosts all)
        val multiplier: Double,       // Power multiplier (e.g., 1.2)
        val isGem: Boolean = false    // Whether this is a consumable gem
    )

    // Cache of parsed items - populated on first access
    private var itemBoosts: Map<String, ItemPowerBoost>? = null
    private var parseAttempted = false

    /**
     * Get the power boost for a specific item ID.
     * @param itemId The item ID in Showdown format (lowercase, no underscores)
     * @return The power boost info, or null if the item doesn't boost power
     */
    fun getBoostForItem(itemId: String): ItemPowerBoost? {
        ensureParsed()
        // Convert to lowercase for matching
        val showdownId = itemId.lowercase()
        return itemBoosts?.get(showdownId)
    }

    /**
     * Force re-parsing of items.js (useful if file changes)
     */
    fun reload() {
        parseAttempted = false
        itemBoosts = null
        ensureParsed()
    }

    private fun ensureParsed() {
        if (parseAttempted) return
        parseAttempted = true
        itemBoosts = parseItemsFile()
    }

    private val finalItemId: Map<String, String> = mapOf(
        "charcoal" to "charcoalstick"
    )

    private fun parseItemsFile(): Map<String, ItemPowerBoost> {
        val result = mutableMapOf<String, ItemPowerBoost>()

        try {
            // Find items.js relative to game directory
            val gameDir = MinecraftClient.getInstance().runDirectory
            val itemsFile = File(gameDir, "showdown/data/items.js")

            if (!itemsFile.exists()) {
                CobblemonExtendedBattleUI.LOGGER.debug("items.js not found at ${itemsFile.absolutePath}")
                return emptyMap()
            }

            val content = itemsFile.readText()

            // Extract individual item blocks using brace-depth tracking
            val itemBlocks = extractItemBlocks(content)

            for ((itemId, block) in itemBlocks) {
                // Extract display name
                val displayName = extractName(block) ?: continue

                // Check for type-boosting items (onBasePower with move.type check)
                if (block.contains("onBasePower") && block.contains("move.type")) {
                    val type = extractMoveType(block)
                    val multiplier = extractChainModifyMultiplier(block)

                    if (type != null && multiplier != null) {
                        // Skip Pokemon-specific items (Adamant Crystal, etc.)
                        if (block.contains("baseSpecies") || block.contains("itemUser")) {
                            continue
                        }
                        val finalId = finalItemId[itemId] ?: itemId

                        result[finalId] = ItemPowerBoost(
                            itemId = finalId,
                            displayName = displayName,
                            boostedType = type,
                            multiplier = multiplier,
                            isGem = false
                        )
                    }
                }

                // Check for Life Orb (onModifyDamage without type check)
                if (itemId == "lifeorb" && block.contains("onModifyDamage")) {
                    val multiplier = extractChainModifyMultiplier(block)
                    if (multiplier != null) {
                        result[itemId] = ItemPowerBoost(
                            itemId = itemId,
                            displayName = displayName,
                            boostedType = null, // Boosts all types
                            multiplier = multiplier,
                            isGem = false
                        )
                    }
                }

                // Check for gems (isGem: true)
                if (block.contains("isGem: true") || block.contains("isGem:true")) {
                    val type = extractMoveType(block)
                    if (type != null) {
                        // Gems use 1.3x multiplier (5325/4096 from conditions.js)
                        result[itemId] = ItemPowerBoost(
                            itemId = itemId,
                            displayName = displayName,
                            boostedType = type,
                            multiplier = 5325.0 / 4096.0, // ~1.3x
                            isGem = true
                        )
                    }
                }
            }

            CobblemonExtendedBattleUI.LOGGER.info("Parsed ${result.size} power-boosting items from items.js")

        } catch (e: Exception) {
            CobblemonExtendedBattleUI.LOGGER.error("Error parsing items.js: ${e.message}", e)
        }

        return result
    }

    /**
     * Extract individual item blocks from the items.js content.
     * Uses brace-depth tracking to handle nested braces in function bodies.
     */
    private fun extractItemBlocks(content: String): Map<String, String> {
        val result = mutableMapOf<String, String>()

        // Pattern to find the start of an item: "itemid: {"
        val itemStartPattern = """^\s*(\w+):\s*\{""".toRegex(RegexOption.MULTILINE)

        var searchStart = 0
        while (searchStart < content.length) {
            val match = itemStartPattern.find(content, searchStart) ?: break
            val itemId = match.groupValues[1]
            val blockStart = match.range.last // Position of the opening brace

            // Find the matching closing brace
            var depth = 1
            var pos = blockStart + 1
            while (pos < content.length && depth > 0) {
                when (content[pos]) {
                    '{' -> depth++
                    '}' -> depth--
                }
                pos++
            }

            if (depth == 0) {
                val block = content.substring(blockStart, pos)
                result[itemId] = block
            }

            searchStart = pos
        }

        return result
    }

    /**
     * Extract the display name from an item block.
     */
    private fun extractName(block: String): String? {
        val namePattern = """name:\s*"([^"]+)"""".toRegex()
        return namePattern.find(block)?.groupValues?.get(1)
    }

    /**
     * Extract the move type from a type check in the item block.
     * Looks for patterns like: move.type === "Fire"
     */
    private fun extractMoveType(block: String): String? {
        val typePattern = """move\.type\s*===\s*"(\w+)"""".toRegex()
        return typePattern.find(block)?.groupValues?.get(1)
    }

    /**
     * Extract the power multiplier from chainModify calls.
     * Looks for patterns like: chainModify([4915, 4096]) -> 4915/4096
     */
    private fun extractChainModifyMultiplier(block: String): Double? {
        val multiplierPattern = """chainModify\s*\(\s*\[\s*(\d+)\s*,\s*4096\s*\]\s*\)""".toRegex()
        val match = multiplierPattern.find(block) ?: return null
        val numerator = match.groupValues[1].toDoubleOrNull() ?: return null
        return numerator / 4096.0
    }
}
