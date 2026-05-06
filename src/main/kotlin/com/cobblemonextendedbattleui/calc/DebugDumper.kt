package com.cobblemonextendedbattleui.calc

import com.cobblemonextendedbattleui.CobblemonExtendedBattleUI
import com.cobblemonextendedbattleui.PanelConfig
import com.cobblemonextendedbattleui.battle.messages.MessageParser
import com.cobblemonextendedbattleui.battle.state.PokemonRegistry
import com.google.gson.GsonBuilder
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.MinecraftClient
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Instant
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

object DebugDumper {
    private const val MAX_ROLLING_DUMPS = 500

    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val latestFingerprint = AtomicReference<String?>(null)
    private val sequence = AtomicLong(0)
    private val lastComputation = AtomicReference<ComputationCache?>(null)
    private val damageTraceLock = Any()
    private val rollingNameFormatter = DateTimeFormatter.ofPattern("HHmmss-SSS")
    private val damageTimestampFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")

    private data class ComputationCache(
        val model: CalcRenderModel,
        val result: DamageComputationResult,
        val capturedAt: Instant
    )

    private val dumpDir: File by lazy {
        val base = FabricLoader.getInstance().configDir.resolve("deltacalc").toFile()
        base.mkdirs()
        base
    }

    private val rollingDir: File by lazy {
        val dir = File(dumpDir, "calc-history")
        dir.mkdirs()
        dir
    }

    private val latestFile: File get() = File(dumpDir, "latest-calc.json")

    fun onComputation(model: CalcRenderModel, result: DamageComputationResult) {
        if (!PanelConfig.debugDumpEnabled) return

        // Cache every computation in memory so the actual-damage comparator can use it
        // even when the snapshot fingerprint matches the prior one (we still want
        // up-to-date HP / move data for comparisons).
        lastComputation.set(ComputationCache(model, result, Instant.now()))

        val fingerprint = model.snapshot.fingerprint()
        if (latestFingerprint.getAndSet(fingerprint) == fingerprint) return
        try {
            val json = gson.toJson(buildDump(model, result))
            latestFile.writeText(json)
            writeRollingDump(model, json)
        } catch (e: Exception) {
            CobblemonExtendedBattleUI.LOGGER.warn("DebugDumper: failed to write calc dump: ${e.message}")
        }
    }

    private fun writeRollingDump(model: CalcRenderModel, json: String) {
        val seq = sequence.incrementAndGet()
        val timestamp = LocalDateTime.now().format(rollingNameFormatter)
        val turn = model.snapshot.turn
        val name = "calc-t%03d-%05d-%s.json".format(turn, seq, timestamp)
        File(rollingDir, name).writeText(json)
        pruneOldDumps()
    }

    private fun pruneOldDumps() {
        val files = rollingDir.listFiles { f -> f.isFile && f.name.startsWith("calc-") } ?: return
        if (files.size <= MAX_ROLLING_DUMPS) return
        files.sortedBy { it.lastModified() }
            .take(files.size - MAX_ROLLING_DUMPS)
            .forEach { runCatching { it.delete() } }
    }

    /**
     * Per-target running aggregator for multi-hit moves. Reset on move resolution.
     * Keyed by `(attackerName, moveName, targetUuid)`. Each entry tracks total damage %
     * and hit count so we can emit a `runningTotal` field useful for diagnosing
     * multi-hit moves whose calc range covers the full move (not per-hit).
     */
    private data class HitAggregate(
        val attacker: String?,
        val move: String?,
        val targetUuid: UUID,
        var totalPct: Float,
        var hits: Int
    )

    private val hitAggregator = AtomicReference<HitAggregate?>(null)

    /**
     * Called from BattleHealthChangeHandlerMixin whenever a tracked HP change
     * registers as damage. Pairs the actual damage with the most recent move
     * from MessageParser and the most recent calc estimate, then writes a
     * comparison line to logs/ebu-damage-compare.log.
     *
     * Same > 0.5% threshold as DamageTracker.recordDamage to ignore noise.
     */
    fun recordActualDamage(
        targetUuid: UUID,
        targetName: String,
        oldPercent: Float,
        newPercent: Float,
        maxHp: Float,
        isFlat: Boolean
    ) {
        if (!PanelConfig.debugDumpEnabled) return
        val deltaPct = oldPercent - newPercent
        if (deltaPct <= 0.5f) return
        try {
            val line = buildDamageLine(targetUuid, targetName, oldPercent, newPercent, maxHp, isFlat, deltaPct)
            damageTraceAppend(line)
        } catch (e: Exception) {
            CobblemonExtendedBattleUI.LOGGER.warn("DebugDumper: failed to write damage trace: ${e.message}")
        }
    }

    /**
     * Reset the multi-hit aggregator. Called externally on move resolution
     * (turn / faint / send-out) so a new attack starts a fresh running total.
     */
    fun resetHitAggregator() {
        hitAggregator.set(null)
    }

    private fun buildDamageLine(
        targetUuid: UUID,
        targetName: String,
        oldPercent: Float,
        newPercent: Float,
        maxHp: Float,
        isFlat: Boolean,
        deltaPct: Float
    ): String {
        val cache = lastComputation.get()
        val attacker = MessageParser.lastMoveUser
        val moveName = MessageParser.lastMoveName
        val moveTarget = MessageParser.lastMoveTarget
        val moveResolved = MessageParser.lastMoveResolved

        // Resolve the move's user/target to UUIDs so we can detect:
        //   (a) self-damage on the attacker (Iron Barbs / Rocky Helmet / Life Orb / recoil)
        //   (b) damage to a Pokemon other than the move's intended target
        val attackerUuid = attacker?.let { PokemonRegistry.resolvePokemonUuid(it, preferAlly = null) }
        val moveTargetUuid = moveTarget?.let { PokemonRegistry.resolvePokemonUuid(it, preferAlly = null) }

        val isSelfDamageOnAttacker = attackerUuid != null && targetUuid == attackerUuid
        val isOnIntendedTarget = moveTargetUuid != null && targetUuid == moveTargetUuid

        // Look up calc snapshots for target and attacker so we can log species/form/types/item.
        // Nicknames in the trace ("Plump fish") tell us nothing about the matchup; species data
        // tells us why the calc thinks what it thinks.
        val snapshot = cache?.model?.snapshot
        val targetSnap = snapshot?.let { it.findPlayer(targetUuid) ?: it.findOpponent(targetUuid) }
        val attackerSnap = if (attackerUuid != null && snapshot != null) {
            snapshot.findPlayer(attackerUuid) ?: snapshot.findOpponent(attackerUuid)
        } else null

        // Multi-hit aggregation: only running-total damage that lands on the same intended target.
        // Self-damage and stray damage events get their own lines without disrupting the aggregate.
        val agg = if (isOnIntendedTarget && !moveResolved) {
            updateAggregate(attacker, moveName, targetUuid, deltaPct)
        } else null

        val sb = StringBuilder()
        sb.append("target='").append(targetName).append("'")
        appendPokemonContext(sb, "target", targetSnap, targetUuid)
        sb.append(" actual=").append("%.2f".format(deltaPct)).append("%")
        if (isFlat && maxHp > 0) {
            sb.append(" actualHp~").append("%.1f".format(deltaPct / 100f * maxHp))
                .append("/").append(maxHp.toInt())
        }
        sb.append(" hp=").append("%.2f->%.2f%%".format(oldPercent, newPercent))
        sb.append(" lastMove='").append(moveName ?: "?").append("'")
            .append(" by='").append(attacker ?: "?").append("'")
        appendPokemonContext(sb, "attacker", attackerSnap, attackerUuid)
        sb.append(" onto='").append(moveTarget ?: "?").append("'")

        // Phase tags so we can filter the log without re-deriving these conditions later.
        when {
            moveResolved -> sb.append(" phase=POST_MOVE")
            isSelfDamageOnAttacker -> sb.append(" phase=ATTACKER_SELF_DAMAGE")
            !isOnIntendedTarget && moveTarget != null -> sb.append(" phase=COLLATERAL")
            else -> sb.append(" phase=MOVE_HIT")
        }

        if (agg != null && agg.hits > 1) {
            sb.append(" runningTotal=").append("%.2f".format(agg.totalPct)).append("%")
                .append(" hit=").append(agg.hits)
        }

        if (cache == null || snapshot == null) {
            sb.append(" predicted=NO_CALC_CACHE")
            return sb.toString()
        }

        val side = when (targetUuid) {
            snapshot.playerActiveUuid -> "player"
            snapshot.opponentActiveUuid -> "opponent"
            else -> "neither"
        }
        sb.append(" side=").append(side)
        sb.append(" turn=").append(snapshot.turn)

        // Skip prediction when the damage clearly isn't from the lastMove on the lastMove's target.
        // We still log the event so the user can see actual damage; we just don't compare.
        if (moveResolved) {
            sb.append(" predicted=POST_MOVE_PHASE")
            return sb.toString()
        }
        if (isSelfDamageOnAttacker) {
            sb.append(" predicted=ATTACKER_SELF_DAMAGE")
            return sb.toString()
        }
        if (moveName == null) {
            sb.append(" predicted=NO_MOVE_CONTEXT")
            return sb.toString()
        }
        if (moveTarget != null && !isOnIntendedTarget) {
            // Damage to a third party (ally hit by spread move, etc.) — don't bind to estimate.
            sb.append(" predicted=NO_MATCH")
            return sb.toString()
        }

        val estimate: DamageEstimate? = when (targetUuid) {
            snapshot.playerActiveUuid -> cache.result.opponentMoves
                .firstOrNull { it.moveName.equals(moveName, ignoreCase = true) }
            snapshot.opponentActiveUuid -> cache.result.yourMoves
                .firstOrNull { it.moveName.equals(moveName, ignoreCase = true) }
            else -> null
        }

        if (estimate == null) {
            sb.append(" predicted=NO_MATCH")
            return sb.toString()
        }

        val pmin = estimate.minPercent
        val pmax = estimate.maxPercent
        // Compare the running total when the estimate covers a multi-hit move; per-hit
        // would always read BELOW. The estimate's `damageRolls` already represent the full move.
        val compareValue = if (agg != null && agg.hits > 1) agg.totalPct else deltaPct
        if (pmin != null && pmax != null) {
            sb.append(" predicted=").append("%.1f-%.1f%%".format(pmin, pmax))
            // KO cap: when the predicted MIN exceeds remaining HP at the time of the hit,
            // the pokemon was always going to die — actual will read low, but it's not a calc bug.
            val remainingPctAtHit = oldPercent
            val isKoOverkill = newPercent <= 0.001f && pmin > remainingPctAtHit + 0.5f
            // 0.5% slack on each end to avoid flagging rounding noise.
            val diagnosis = when {
                isKoOverkill -> "KO_CAP"
                compareValue + 0.5f < pmin.toFloat() -> "BELOW"
                compareValue - 0.5f > pmax.toFloat() -> "ABOVE"
                else -> "IN_RANGE"
            }
            sb.append(" diag=").append(diagnosis)
        } else {
            sb.append(" predicted=UNSUPPORTED")
        }
        if (estimate.minDamage != null && estimate.maxDamage != null) {
            sb.append(" predHp=").append(estimate.minDamage).append("-").append(estimate.maxDamage)
        }
        if (estimate.confidence != DamageConfidence.HIGH) {
            sb.append(" conf=").append(estimate.confidence.name)
        }
        return sb.toString()
    }

    /**
     * Emit a compact `<role>=species|form|types|item|stages` field next to the role's name
     * so logs are self-contained: nicknames don't tell us anything about typing/items, but
     * the calc snapshot does. Stages only show when non-zero. Item only shows when known.
     */
    private fun appendPokemonContext(sb: StringBuilder, role: String, snap: CalcPokemonSnapshot?, uuid: UUID?) {
        if (snap == null) {
            // Fall back to just the uuid so we can correlate the line to the next computation dump.
            if (uuid != null) sb.append(" ").append(role).append("Uuid=").append(uuid)
            return
        }
        sb.append(" ").append(role).append("=")
        sb.append(snap.speciesKey ?: snap.speciesId ?: snap.speciesLabel)
        snap.formName?.takeIf { it.isNotBlank() && !it.equals("normal", ignoreCase = true) }?.let {
            sb.append("|form=").append(it)
        }
        if (snap.typeNames.isNotEmpty()) {
            sb.append("|types=").append(snap.typeNames.joinToString("/"))
        }
        snap.teraType?.takeIf { it.isNotBlank() }?.let { sb.append("|tera=").append(it) }
        snap.itemName?.takeIf { it.isNotBlank() }?.let { sb.append("|item=").append(it) }
        snap.abilityName?.takeIf { it.isNotBlank() }?.let { sb.append("|ability=").append(it) }
        val stageStr = snap.statStages.entries
            .filter { it.value != 0 }
            .sortedBy { it.key }
            .joinToString(",") { "${it.key}${if (it.value > 0) "+" else ""}${it.value}" }
        if (stageStr.isNotEmpty()) sb.append("|stages=").append(stageStr)
    }

    private fun updateAggregate(
        attacker: String?,
        moveName: String?,
        targetUuid: UUID,
        deltaPct: Float
    ): HitAggregate {
        while (true) {
            val current = hitAggregator.get()
            val matches = current != null &&
                current.attacker == attacker &&
                current.move == moveName &&
                current.targetUuid == targetUuid
            val updated = if (matches) {
                current!!.copy(totalPct = current.totalPct + deltaPct, hits = current.hits + 1)
            } else {
                HitAggregate(attacker, moveName, targetUuid, deltaPct, 1)
            }
            if (hitAggregator.compareAndSet(current, updated)) {
                return updated
            }
        }
    }

    private fun damageTraceAppend(line: String) {
        val client = MinecraftClient.getInstance() ?: return
        val gameDir = client.runDirectory?.toPath() ?: return
        val path: Path = gameDir.resolve("logs").resolve("ebu-damage-compare.log")
        val full = "[${LocalDateTime.now().format(damageTimestampFormatter)}] $line${System.lineSeparator()}"
        synchronized(damageTraceLock) {
            Files.createDirectories(path.parent)
            Files.writeString(path, full, StandardOpenOption.CREATE, StandardOpenOption.APPEND)
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
