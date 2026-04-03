package com.cobblemonextendedbattleui

import net.minecraft.text.Text
import net.minecraft.text.TranslatableTextContent
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Battle log storage and categorization system.
 * Captures all battle messages and categorizes them for filtered display.
 */
object BattleLog {

    /**
     * Categories for battle log entries.
     * Used for filtering the log display.
     */
    enum class EntryType(val displayName: String, val icon: String) {
        TURN("Turn", ""),           // Turn markers (always shown)
        MOVE("Moves", "\u2694"),     // Move usage, effectiveness, crits, misses
        HP("HP", "\u2665"),          // Damage, faints
        HEALING("Healing", "\u2764"), // HP recovery
        EFFECT("Effects", "\u2728"), // Stat changes, status, volatiles
        FIELD("Field", "\u2600"),    // Weather, terrain, screens, hazards
        OTHER("Other", "\u2022")     // Anything else
    }

    /**
     * A single entry in the battle log.
     * Includes cached wrapped lines for performance (computed lazily on first render).
     */
    data class LogEntry(
        val turn: Int,
        val type: EntryType,
        val message: Text,
        val translationKey: String?,
        val timestamp: Long = System.currentTimeMillis()
    ) {
        // Cached wrapped lines (computed once per width/scale combination)
        @Volatile var cachedLines: List<String>? = null
        @Volatile var cachedWidth: Int = 0
        @Volatile var cachedScale: Float = 0f

        /**
         * Gets wrapped lines, using cache if parameters match.
         */
        fun getWrappedLines(width: Int, scale: Float, wrapFn: (String, Int, Float) -> List<String>): List<String> {
            val cached = cachedLines
            if (cached != null && cachedWidth == width && cachedScale == scale) {
                return cached
            }
            val lines = wrapFn(message.string, width, scale)
            cachedLines = lines
            cachedWidth = width
            cachedScale = scale
            return lines
        }

        /**
         * Invalidates the cache (call when font scale changes).
         */
        fun invalidateCache() {
            cachedLines = null
        }
    }

    // Thread-safe list for log entries
    private val entries = CopyOnWriteArrayList<LogEntry>()

    // Maximum entries to prevent memory issues
    private const val MAX_ENTRIES = 500

    // Current turn (updated from BattleStateTracker)
    private var currentTurn: Int = 0

    // ═══════════════════════════════════════════════════════════════════════════
    // Translation key mappings for categorization
    // ═══════════════════════════════════════════════════════════════════════════

    // Move-related keys (attacks, effectiveness, accuracy)
    private val MOVE_KEYS = setOf(
        "cobblemon.battle.used",
        "cobblemon.battle.supereffective",
        "cobblemon.battle.resisted",
        "cobblemon.battle.immune",
        "cobblemon.battle.miss",
        "cobblemon.battle.crit",
        "cobblemon.battle.fail",
        "cobblemon.battle.ohko",
        "cobblemon.battle.hitcount",
        "cobblemon.battle.charge",
        "cobblemon.battle.prepare",
        "cobblemon.battle.mustrecharge",
        "cobblemon.battle.cant",
        "cobblemon.battle.notarget",
        "cobblemon.battle.blocked"
    )

    // HP-related keys (damage, healing, fainting)
    private val HP_KEYS = setOf(
        "cobblemon.battle.damage",
        "cobblemon.battle.fainted",
        "cobblemon.battle.heal",
        "cobblemon.battle.sethp",
        "cobblemon.battle.recoil",
        "cobblemon.battle.drain",
        "cobblemon.battle.leftovers",
        "cobblemon.battle.blacksludge",
        "cobblemon.battle.shellbell",
        "cobblemon.battle.poisonheal"
    )

    // Effect-related keys (stats, status, volatiles)
    private val EFFECT_KEY_PREFIXES = listOf(
        "cobblemon.battle.boost",
        "cobblemon.battle.unboost",
        "cobblemon.battle.setboost",
        "cobblemon.battle.clearboost",
        "cobblemon.battle.invertboost",
        "cobblemon.battle.status",
        "cobblemon.battle.cure",
        "cobblemon.battle.start",  // Volatile status start
        "cobblemon.battle.end",    // Volatile status end
        "cobblemon.battle.ability",
        "cobblemon.battle.transform",
        "cobblemon.battle.mega",
        "cobblemon.battle.zmove",
        "cobblemon.battle.terastallize"
    )

    // Field-related keys (weather, terrain, side conditions)
    private val FIELD_KEY_PREFIXES = listOf(
        "cobblemon.battle.weather",
        "cobblemon.battle.terrain",
        "cobblemon.battle.fieldstart",
        "cobblemon.battle.fieldend",
        "cobblemon.battle.sidestart",
        "cobblemon.battle.sideend"
    )

    // Turn marker key
    private const val TURN_KEY = "cobblemon.battle.turn"

    // Switch-related (categorize as HP since it's about Pokemon state)
    private val SWITCH_KEYS = setOf(
        "cobblemon.battle.switch",
        "cobblemon.battle.drag",
        "cobblemon.battle.replace",
        "cobblemon.battle.sendout"
    )

    // ═══════════════════════════════════════════════════════════════════════════
    // Public API
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Process a list of battle messages and add them to the log.
     * Uses two-pass approach to correctly assign turns even when turn messages
     * appear after action messages in the same batch.
     */
    fun processMessages(messages: List<Text>) {
        if (messages.isEmpty()) return

        // PASS 1: Find all turn markers and their positions in the message list
        val turnPositions = mutableListOf<Pair<Int, Int>>()  // (messageIndex, turnNumber)
        for ((index, message) in messages.withIndex()) {
            val key = extractTranslationKey(message)
            if (key == TURN_KEY) {
                extractTurnNumber(message)?.let { turn ->
                    turnPositions.add(index to turn)
                }
            }
        }

        // Determine starting turn:
        // - If first message is a turn marker, use that turn
        // - Otherwise, use the current turn from BattleStateTracker
        var effectiveTurn = if (turnPositions.isNotEmpty() && turnPositions[0].first == 0) {
            turnPositions[0].second
        } else {
            BattleStateTracker.currentTurn
        }

        // PASS 2: Process messages with correct turn assignments
        var nextTurnIdx = 0
        for ((index, message) in messages.withIndex()) {
            // Advance to the correct turn when we reach or pass a turn marker
            while (nextTurnIdx < turnPositions.size && turnPositions[nextTurnIdx].first <= index) {
                effectiveTurn = turnPositions[nextTurnIdx].second
                nextTurnIdx++
            }

            val key = extractTranslationKey(message)
            val type = categorizeMessage(key)

            addEntry(LogEntry(
                turn = effectiveTurn,
                type = type,
                message = message,
                translationKey = key
            ))
        }

        // Update currentTurn with the final turn from this batch
        if (turnPositions.isNotEmpty()) {
            currentTurn = turnPositions.last().second
        } else if (messages.isNotEmpty()) {
            // No turn markers in batch, but update to match effective turn
            currentTurn = effectiveTurn
        }
    }

    /**
     * Add an HP change entry immediately.
     * Called directly from DamageTracker when HP changes are detected.
     */
    fun addHpChangeEntry(text: String, isHealing: Boolean = false) {
        addEntry(LogEntry(
            turn = currentTurn,
            type = if (isHealing) EntryType.HEALING else EntryType.HP,
            message = Text.literal(text),
            translationKey = null
        ))
    }

    /**
     * Get all entries, optionally filtered by types.
     * @param activeFilters Set of EntryType to include. If empty or contains all types, returns everything.
     */
    fun getEntries(activeFilters: Set<EntryType>? = null): List<LogEntry> {
        if (activeFilters == null || activeFilters.isEmpty() || activeFilters.size == EntryType.entries.size) {
            return entries.toList()
        }
        // Always include TURN markers for context
        return entries.filter { it.type in activeFilters || it.type == EntryType.TURN }
    }

    /**
     * Get entries grouped by turn for rendering with separators.
     */
    fun getEntriesGroupedByTurn(activeFilters: Set<EntryType>? = null): Map<Int, List<LogEntry>> {
        return getEntries(activeFilters).groupBy { it.turn }
    }

    /**
     * Get the total number of entries.
     */
    fun size(): Int = entries.size

    /**
     * Check if the log is empty.
     */
    fun isEmpty(): Boolean = entries.isEmpty()

    /**
     * Clear all entries (called when battle ends).
     */
    fun clear() {
        entries.clear()
        currentTurn = 0
        DamageTracker.clear()
        CobblemonExtendedBattleUI.LOGGER.debug("BattleLog: Cleared")
    }

    /**
     * Invalidate all cached wrapped lines (call when font scale changes).
     */
    fun invalidateWrappedTextCache() {
        for (entry in entries) {
            entry.invalidateCache()
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Internal helpers
    // ═══════════════════════════════════════════════════════════════════════════

    private fun addEntry(entry: LogEntry) {
        entries.add(entry)

        // Trim old entries if we exceed max
        while (entries.size > MAX_ENTRIES) {
            entries.removeAt(0)
        }
    }

    /**
     * Extract the translation key from a Text component.
     * Recursively checks siblings if the main content doesn't have a key.
     */
    private fun extractTranslationKey(text: Text): String? {
        val content = text.content
        if (content is TranslatableTextContent) {
            return content.key
        }

        // Check siblings
        for (sibling in text.siblings) {
            extractTranslationKey(sibling)?.let { return it }
        }

        return null
    }

    /**
     * Extract turn number from a turn message.
     */
    private fun extractTurnNumber(text: Text): Int? {
        val content = text.content
        if (content is TranslatableTextContent && content.key == TURN_KEY) {
            val args = content.args
            if (args.isNotEmpty()) {
                val turnStr = when (val arg = args[0]) {
                    is Text -> arg.string
                    is Number -> arg.toString()
                    else -> arg.toString()
                }
                return turnStr.toIntOrNull()
            }
        }
        return null
    }

    /**
     * Categorize a message based on its translation key.
     */
    private fun categorizeMessage(key: String?): EntryType {
        if (key == null) return EntryType.OTHER

        return when {
            // Turn markers
            key == TURN_KEY -> EntryType.TURN

            // Exact match for moves
            key in MOVE_KEYS -> EntryType.MOVE

            // Exact match for HP
            key in HP_KEYS -> EntryType.HP

            // Switch is like HP (Pokemon state change)
            key in SWITCH_KEYS -> EntryType.HP

            // Prefix match for effects
            EFFECT_KEY_PREFIXES.any { key.startsWith(it) } -> EntryType.EFFECT

            // Prefix match for field
            FIELD_KEY_PREFIXES.any { key.startsWith(it) } -> EntryType.FIELD

            // Check for more move-related patterns
            key.contains(".move.") || key.contains(".attack") -> EntryType.MOVE

            // Check for damage patterns
            key.contains(".damage") || key.contains(".hurt") -> EntryType.HP

            // Check for status patterns
            key.contains(".status") || key.contains(".poison") ||
            key.contains(".burn") || key.contains(".paralyze") ||
            key.contains(".freeze") || key.contains(".sleep") -> EntryType.EFFECT

            // Default
            else -> EntryType.OTHER
        }
    }
}
