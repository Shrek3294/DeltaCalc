package com.cobblemonextendedbattleui.calc

import com.google.gson.Gson
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.Locale

data class UsageDexDataset(
    val sourceMeta: Map<String, String>,
    val species: List<UsageSpeciesEntry>
)

data class UsageSpeciesEntry(
    val speciesId: String,
    val displayName: String,
    val slug: String,
    val usageRank: Int,
    val usagePercent: Double,
    val sampleCount: Int,
    val aliases: List<String>,
    val moves: List<UsageOptionEntry>,
    val items: List<UsageOptionEntry>,
    val abilities: List<UsageOptionEntry>,
    val spreads: List<UsageSpreadEntry>
)

data class UsageOptionEntry(
    val id: String,
    val displayName: String,
    val type: String,
    val usagePercent: Double
)

data class UsageSpreadEntry(
    val nature: String,
    val evs: Map<String, Int>,
    val usagePercent: Double
)

class UsageDex private constructor(private val byKey: Map<String, UsageSpeciesEntry>) {
    fun find(speciesKey: String?, speciesId: String?, speciesLabel: String?, displayName: String?): UsageSpeciesEntry? {
        val keys = listOfNotNull(speciesKey, speciesId, speciesLabel, displayName).map(::normalize)
        return keys.firstNotNullOfOrNull { byKey[it] }
    }

    companion object {
        private val gson = Gson()
        private val resourceCandidates = listOf(
            "/data/deltacalc/usage/season-6-mid-1300.generated.json",
            "/data/deltacalc/usage/season-6-mid-1000.generated.json",
            "/data/deltacalc/usage/season-6-mid-1500.generated.json",
            "/data/deltacalc/usage/season-6-mid-1000.sample.json"
        )

        fun loadDefault(): UsageDex {
            val stream = resourceCandidates.firstNotNullOfOrNull { UsageDex::class.java.getResourceAsStream(it) }
                ?: return UsageDex(emptyMap())
            stream.use {
                val dataset = gson.fromJson(InputStreamReader(it, StandardCharsets.UTF_8), UsageDexDataset::class.java)
                val indexed = buildMap {
                    dataset.species.forEach { entry ->
                        put(normalize(entry.speciesId), entry)
                        put(normalize(entry.displayName), entry)
                        put(normalize(entry.slug), entry)
                        entry.aliases.forEach { alias -> put(normalize(alias), entry) }
                    }
                }
                return UsageDex(indexed)
            }
        }

        private fun normalize(raw: String): String {
            return raw.lowercase(Locale.ROOT).replace(" ", "-").replace("_", "-")
        }
    }
}
