package com.cobblemonextendedbattleui.calc

import com.google.gson.Gson
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.Locale

data class BattleDatabaseDataset(
    val sourceMeta: Map<String, String> = emptyMap(),
    val species: List<BattleSpeciesEntry> = emptyList(),
    val deltaRanked: List<BattleSetEntry> = emptyList(),
    val smogonFallback: List<BattleSetEntry> = emptyList()
)

data class BattleSpeciesEntry(
    val speciesKey: String,
    val speciesId: String?,
    val displayName: String,
    val slug: String?,
    val formName: String?,
    val aliases: List<String> = emptyList(),
    val typeNames: List<String> = emptyList(),
    val baseStats: BattleBaseStats? = null,
    val weightKg: Double? = null
)

data class BattleBaseStats(
    val hp: Int,
    val atk: Int,
    val def: Int,
    val spa: Int,
    val spd: Int,
    val spe: Int
)

data class BattleSetEntry(
    val speciesKey: String,
    val speciesId: String?,
    val displayName: String,
    val slug: String?,
    val source: String,
    val usageRank: Int,
    val usagePercent: Double,
    val sampleCount: Int,
    val aliases: List<String> = emptyList(),
    val moves: List<BattleOptionEntry> = emptyList(),
    val items: List<BattleOptionEntry> = emptyList(),
    val abilities: List<BattleOptionEntry> = emptyList(),
    val spreads: List<BattleSpreadEntry> = emptyList()
)

data class BattleOptionEntry(
    val id: String,
    val displayName: String,
    val type: String?,
    val usagePercent: Double
)

data class BattleSpreadEntry(
    val nature: String,
    val evs: Map<String, Int>,
    val usagePercent: Double
)

class BattleDatabase private constructor(
    private val speciesByKey: Map<String, BattleSpeciesEntry>,
    private val deltaUsageByKey: Map<String, BattleSetEntry>,
    private val smogonUsageByKey: Map<String, BattleSetEntry>
) {
    fun findSpecies(vararg candidates: String?): BattleSpeciesEntry? {
        val keys = candidates.mapNotNull { it?.takeIf(String::isNotBlank) }.flatMap(::normalizedCandidates)
        return keys.firstNotNullOfOrNull { speciesByKey[it] }
    }

    fun findDefaultSet(species: BattleSpeciesEntry?, vararg candidates: String?): BattleSetEntry? {
        val keys = buildList {
            species?.speciesKey?.let(::add)
            species?.speciesId?.let(::add)
            species?.displayName?.let(::add)
            addAll(candidates.filterNotNull())
        }.flatMap(::normalizedCandidates)

        return keys.firstNotNullOfOrNull { deltaUsageByKey[it] }
            ?: keys.firstNotNullOfOrNull { smogonUsageByKey[it] }
    }

    companion object {
        private val gson = Gson()
        private val rankedResourceCandidates = listOf(
            "/data/deltacalc/usage/season-6-mid-1500.generated.json",
            "/data/deltacalc/usage/season-6-mid-1000.generated.json",
            "/data/deltacalc/usage/season-6-mid-1300.generated.json",
            "/data/deltacalc/usage/season-6-mid-1000.sample.json"
        )

        fun loadDefault(): BattleDatabase {
            val stream = BattleDatabase::class.java.getResourceAsStream("/data/deltacalc/database/battle-database.generated.json")
            if (stream == null) {
                return loadFromRankedFallback()
            }

            stream.use {
                val dataset = gson.fromJson(InputStreamReader(it, StandardCharsets.UTF_8), BattleDatabaseDataset::class.java)
                return fromDataset(dataset)
            }
        }

        private fun loadFromRankedFallback(): BattleDatabase {
            val deltaEntries = mutableListOf<BattleSetEntry>()
            val speciesEntries = linkedMapOf<String, BattleSpeciesEntry>()

            rankedResourceCandidates.forEach { resource ->
                val sourceLabel = when {
                    resource.contains("1300") -> "delta-ranked-1300"
                    resource.contains("1000") -> "delta-ranked-1000"
                    resource.contains("1500") -> "delta-ranked-1500"
                    else -> "delta-ranked-sample"
                }
                val stream = BattleDatabase::class.java.getResourceAsStream(resource) ?: return@forEach
                stream.use {
                    val dataset = gson.fromJson(InputStreamReader(it, StandardCharsets.UTF_8), UsageDexDataset::class.java)
                    dataset.species.forEach { usage ->
                        val speciesKey = normalize(usage.slug.ifBlank { usage.speciesId })
                        if (speciesKey !in speciesEntries) {
                            speciesEntries[speciesKey] = BattleSpeciesEntry(
                                speciesKey = speciesKey,
                                speciesId = usage.speciesId,
                                displayName = usage.displayName,
                                slug = usage.slug,
                                formName = usage.displayName.substringAfter('-', "").ifBlank { null },
                                aliases = usage.aliases,
                                typeNames = emptyList(),
                                baseStats = null
                            )
                        }
                        deltaEntries += BattleSetEntry(
                            speciesKey = speciesKey,
                            speciesId = usage.speciesId,
                            displayName = usage.displayName,
                            slug = usage.slug,
                            source = sourceLabel,
                            usageRank = usage.usageRank,
                            usagePercent = usage.usagePercent,
                            sampleCount = usage.sampleCount,
                            aliases = usage.aliases,
                            moves = usage.moves.map { BattleOptionEntry(it.id, it.displayName, it.type, it.usagePercent) },
                            items = usage.items.map { BattleOptionEntry(it.id, it.displayName, it.type, it.usagePercent) },
                            abilities = usage.abilities.map { BattleOptionEntry(it.id, it.displayName, it.type, it.usagePercent) },
                            spreads = usage.spreads.map { BattleSpreadEntry(it.nature, it.evs, it.usagePercent) }
                        )
                    }
                }
            }

            return fromDataset(
                BattleDatabaseDataset(
                    sourceMeta = mapOf("fallback" to "ranked-json-only"),
                    species = speciesEntries.values.toList(),
                    deltaRanked = deltaEntries,
                    smogonFallback = emptyList()
                )
            )
        }

        private fun fromDataset(dataset: BattleDatabaseDataset): BattleDatabase {
            // Two-pass indexing: primary identifiers first, aliases via putIfAbsent.
            // The generated dataset includes the *base* species name in every variant's
            // aliases (e.g. ursaluna-bloodmoon's aliases list "Ursaluna"; zapdos-galar's
            // list "Zapdos"). Single-pass `put` lets the last-indexed variant overwrite
            // the base species' key, which is why the calc kept showing Ursaluna as
            // Ursaluna-Bloodmoon and Zapdos as Zapdos-Galar.
            //
            // Primary identifiers (speciesKey/speciesId/slug/displayName) get precedence;
            // aliases only fill gaps they don't already cover. This is a stable fix
            // regardless of dataset ordering.
            val speciesByKey = buildMap {
                dataset.species.forEach { entry -> indexSpeciesPrimary(entry) }
                dataset.species.forEach { entry -> indexSpeciesAliases(entry) }
            }
            val deltaUsageByKey = buildMap {
                dataset.deltaRanked.forEach { entry -> indexSetPrimary(entry) }
                dataset.deltaRanked.forEach { entry -> indexSetAliases(entry) }
            }
            val smogonUsageByKey = buildMap {
                dataset.smogonFallback.forEach { entry -> indexSetPrimary(entry) }
                dataset.smogonFallback.forEach { entry -> indexSetAliases(entry) }
            }
            return BattleDatabase(speciesByKey, deltaUsageByKey, smogonUsageByKey)
        }

        private fun MutableMap<String, BattleSpeciesEntry>.indexSpeciesPrimary(entry: BattleSpeciesEntry) {
            normalizedCandidates(entry.speciesKey).forEach { putIfAbsent(it, entry) }
            entry.speciesId?.let(::normalizedCandidates)?.forEach { putIfAbsent(it, entry) }
            entry.slug?.let(::normalizedCandidates)?.forEach { putIfAbsent(it, entry) }
            normalizedCandidates(entry.displayName).forEach { putIfAbsent(it, entry) }
        }

        private fun MutableMap<String, BattleSpeciesEntry>.indexSpeciesAliases(entry: BattleSpeciesEntry) {
            entry.aliases.flatMap(::normalizedCandidates).forEach { putIfAbsent(it, entry) }
        }

        private fun MutableMap<String, BattleSetEntry>.indexSetPrimary(entry: BattleSetEntry) {
            normalizedCandidates(entry.speciesKey).forEach { putIfAbsent(it, entry) }
            entry.speciesId?.let(::normalizedCandidates)?.forEach { putIfAbsent(it, entry) }
            entry.slug?.let(::normalizedCandidates)?.forEach { putIfAbsent(it, entry) }
            normalizedCandidates(entry.displayName).forEach { putIfAbsent(it, entry) }
        }

        private fun MutableMap<String, BattleSetEntry>.indexSetAliases(entry: BattleSetEntry) {
            entry.aliases.flatMap(::normalizedCandidates).forEach { putIfAbsent(it, entry) }
        }

        private fun normalizedCandidates(raw: String): List<String> {
            val trimmed = raw.trim()
            if (trimmed.isBlank()) {
                return emptyList()
            }

            val normalized = normalize(trimmed)
            val compact = normalizeCompact(trimmed)
            return linkedSetOf(
                normalized,
                compact,
                normalized.removePrefix("cobblemon-"),
                compact.removePrefix("cobblemon")
            ).filter { it.isNotBlank() }
        }

        private fun normalize(raw: String): String {
            return raw.lowercase(Locale.ROOT)
                .substringAfter(':')
                .replace(" ", "-")
                .replace("_", "-")
        }

        private fun normalizeCompact(raw: String): String {
            return normalize(raw)
                .replace("-", "")
                .replace("'", "")
                .replace(".", "")
        }
    }
}
