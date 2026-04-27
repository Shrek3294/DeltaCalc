package com.cobblemonextendedbattleui.calc

import com.google.gson.Gson
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.Locale

data class MoveFlagEntry(
    val id: String,
    val displayName: String,
    val type: String,
    val category: String,
    val basePower: Int,
    val priority: Int,
    val flags: List<String> = emptyList(),
    val hasSecondary: Boolean = false,
    val isRecoil: Boolean = false,
    val isSelfStatDrop: Boolean = false,
    val multihitMin: Int = 1,
    val multihitMax: Int = 1
) {
    // Computed property instead of `by lazy`: Gson bypasses the constructor during
    // deserialization, which leaves the Lazy backing field null and trips an NPE.
    private val flagSet: Set<String>
        get() = flags.map { it.lowercase(Locale.ROOT) }.toSet()
    fun hasFlag(name: String): Boolean = name.lowercase(Locale.ROOT) in flagSet
    val isContact: Boolean get() = hasFlag("contact")
    val isBite: Boolean get() = hasFlag("bite")
    val isPunch: Boolean get() = hasFlag("punch")
    val isPulse: Boolean get() = hasFlag("pulse")
    val isSlicing: Boolean get() = hasFlag("slicing")
    val isSound: Boolean get() = hasFlag("sound")
    val isBullet: Boolean get() = hasFlag("bullet")
    val isWind: Boolean get() = hasFlag("wind")
    val isDance: Boolean get() = hasFlag("dance")
}

private data class MoveFlagDataset(val moves: List<MoveFlagEntry> = emptyList())

object MoveFlagDatabase {
    private val gson = Gson()

    @Volatile
    private var cache: Map<String, MoveFlagEntry>? = null

    fun get(moveId: String?): MoveFlagEntry? {
        if (moveId.isNullOrBlank()) return null
        val map = cache ?: load().also { cache = it }
        return map[normalizeKey(moveId)]
    }

    fun reload() {
        cache = load()
    }

    private fun load(): Map<String, MoveFlagEntry> {
        val stream = MoveFlagDatabase::class.java.getResourceAsStream("/data/deltacalc/database/move-flags.generated.json")
            ?: return emptyMap()
        stream.use {
            val dataset = gson.fromJson(InputStreamReader(it, StandardCharsets.UTF_8), MoveFlagDataset::class.java)
            val map = HashMap<String, MoveFlagEntry>(dataset.moves.size * 2)
            for (move in dataset.moves) {
                map[normalizeKey(move.id)] = move
                map[normalizeKey(move.displayName)] = move
            }
            return map
        }
    }

    private fun normalizeKey(raw: String): String {
        return raw.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]"), "")
    }
}
