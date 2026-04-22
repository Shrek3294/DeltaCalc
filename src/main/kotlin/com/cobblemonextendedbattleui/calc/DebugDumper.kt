package com.cobblemonextendedbattleui.calc

import com.cobblemonextendedbattleui.CobblemonExtendedBattleUI
import com.cobblemonextendedbattleui.PanelConfig
import com.google.gson.GsonBuilder
import net.fabricmc.loader.api.FabricLoader
import java.io.File
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

object DebugDumper {
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val latestFingerprint = AtomicReference<String?>(null)

    private val dumpDir: File by lazy {
        val base = FabricLoader.getInstance().configDir.resolve("deltacalc").toFile()
        base.mkdirs()
        base
    }

    private val latestFile: File get() = File(dumpDir, "latest-calc.json")

    fun onComputation(model: CalcRenderModel, result: DamageComputationResult) {
        if (!PanelConfig.debugDumpEnabled) return
        val fingerprint = model.snapshot.fingerprint()
        if (latestFingerprint.getAndSet(fingerprint) == fingerprint) return
        try {
            latestFile.writeText(gson.toJson(buildDump(model, result)))
        } catch (e: Exception) {
            CobblemonExtendedBattleUI.LOGGER.warn("DebugDumper: failed to write latest-calc.json: ${e.message}")
        }
    }

    private fun buildDump(model: CalcRenderModel, result: DamageComputationResult): Map<String, Any?> {
        return linkedMapOf(
            "capturedAt" to Instant.now().toString(),
            "turn" to model.snapshot.turn,
            "battleId" to model.snapshot.battleId.toString(),
            "weather" to model.snapshot.weather,
            "terrain" to model.snapshot.terrain,
            "playerSideConditions" to model.snapshot.playerSide.sideConditions.toList(),
            "opponentSideConditions" to model.snapshot.opponentSide.sideConditions.toList(),
            "playerActive" to dumpPokemon(model.snapshot.playerActive),
            "opponentActive" to dumpPokemon(model.snapshot.opponentActive),
            "opponentInferredSet" to dumpSet(model.opponentSet),
            "yourMoveEstimates" to result.yourMoves.map(::dumpEstimate),
            "opponentMoveEstimates" to result.opponentMoves.map(::dumpEstimate),
            "warnings" to result.warnings
        )
    }

    private fun dumpPokemon(p: CalcPokemonSnapshot?): Map<String, Any?>? {
        if (p == null) return null
        return linkedMapOf(
            "displayName" to p.displayName,
            "speciesKey" to p.speciesKey,
            "speciesId" to p.speciesId,
            "formName" to p.formName,
            "level" to p.level,
            "hp" to "${p.currentHp}/${p.maxHp}",
            "status" to p.status,
            "typeNames" to p.typeNames,
            "teraType" to p.teraType,
            "item" to p.itemName,
            "ability" to p.abilityName,
            "weightKg" to p.weightKg,
            "statStages" to p.statStages,
            "baseStats" to p.baseStats,
            "actualStats" to p.actualStats,
            "revealedMoves" to p.revealedMoves,
            "moveList" to p.moveList.map { mapOf("id" to it.id, "name" to it.displayName) }
        )
    }

    private fun dumpSet(s: EffectiveBattleSet): Map<String, Any?> {
        return linkedMapOf(
            "speciesId" to s.speciesId,
            "source" to s.sourceLabel,
            "item" to mapOf("name" to s.item.first, "state" to s.item.second.name),
            "ability" to mapOf("name" to s.ability.first, "state" to s.ability.second.name),
            "spread" to s.spread?.let { mapOf("nature" to it.nature, "evs" to it.evs, "state" to it.state.name) },
            "moves" to s.moves.map { mapOf("name" to it.moveName, "state" to it.state.name) }
        )
    }

    private fun dumpEstimate(e: DamageEstimate): Map<String, Any?> {
        return linkedMapOf(
            "moveName" to e.moveName,
            "supported" to e.supported,
            "normalRangePercent" to e.minPercent?.let { "%.1f-%.1f".format(it, e.maxPercent ?: it) },
            "normalRangeDamage" to e.minDamage?.let { "${it}-${e.maxDamage ?: it}" },
            "critRangePercent" to e.critMinPercent?.let { "%.1f-%.1f".format(it, e.critMaxPercent ?: it) },
            "hits" to "${e.minHits}-${e.maxHits}",
            "koLabel" to e.koLabel,
            "confidence" to e.confidence.name,
            "warnings" to e.warnings,
            "rolls" to e.damageRolls,
            "critRolls" to e.critDamageRolls
        )
    }
}
