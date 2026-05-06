package com.cobblemonextendedbattleui.calc

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.api.pokemon.stats.Stats
import com.cobblemon.mod.common.api.pokemon.status.Status
import com.cobblemon.mod.common.api.pokemon.status.Statuses
import com.cobblemon.mod.common.api.types.ElementalTypes
import com.cobblemon.mod.common.battles.ai.strongBattleAI.AIUtility
import com.cobblemonextendedbattleui.BattleStateTracker
import com.cobblemonextendedbattleui.PanelConfig
import com.cobblemonextendedbattleui.TeamIndicatorUI
import com.cobblemonextendedbattleui.compat.delta.DeltaBattlePlatformAdapter
import com.cobblemonextendedbattleui.pokemon.stats.StatCalculator
import com.cobblemonextendedbattleui.tracking.BattleStateFacade
import net.minecraft.util.Identifier
import java.util.UUID
import kotlin.math.floor
import kotlin.math.roundToInt

enum class OverrideRow { ITEM, ABILITY, SPREAD }

data class OpponentOverride(
    val itemIndex: Int? = null,
    val abilityIndex: Int? = null,
    val spreadIndex: Int? = null
)

object CalcComputationService {
    private val platformAdapter = DeltaBattlePlatformAdapter
    private val battleDatabase = BattleDatabase.loadDefault()
    private val invalidationCoordinator = CalcInvalidationCoordinator()
    private val inferenceService = OpponentInferenceService()
    private val previewSelectionState = CalcPreviewSelectionState()
    private val damageEngine: DamageEngine = BestEffortDamageEngine
    private var currentModel: CalcRenderModel? = null
    private var lastSelectionFingerprint: String = ""
    // Ephemeral, per-opponent-UUID overrides for inferred Item / Ability / Spread.
    // Cleared when no battle is active (see currentModel() null branch).
    private val overrides = mutableMapOf<UUID, OpponentOverride>()

    fun currentModel(): CalcRenderModel? {
        val truth = BattleStateFacade.capture(platformAdapter) ?: run {
            currentModel = null
            lastSelectionFingerprint = ""
            overrides.clear()
            return null
        }

        val snapshot = CalcBattleSnapshotFactory.fromTruth(truth, battleDatabase)
        val snapshotChanged = invalidationCoordinator.update(snapshot)
        val selection = previewSelectionState.resolve(snapshot)
        val selectionFingerprint = previewSelectionState.fingerprint()
        if (snapshotChanged || currentModel == null || lastSelectionFingerprint != selectionFingerprint) {
            val selectedPlayer = snapshot.findPlayer(selection.playerUuid) ?: snapshot.playerActive
            val selectedOpponent = snapshot.findOpponent(selection.opponentUuid) ?: snapshot.opponentActive
            val rawInferredSet = inferenceService.infer(snapshot, selectedOpponent, battleDatabase)
            val inferredSet = expandAlternatives(rawInferredSet, selectedOpponent)
            val overrideMerged = applyOverride(inferredSet, selectedOpponent)
            // When the effective item is a mega stone, swap the opponent's
            // species data to its mega form so damage / speed / types reflect
            // post-mega stats, even when the in-battle mega evolution hasn't
            // fired yet. Re-points effectiveSet's ability at the mega form's
            // intrinsic ability when the user hasn't overridden it.
            val (effectiveOpponent, effectiveSet) = applyMegaFormSwap(selectedOpponent, overrideMerged)
            val reasons = invalidationCoordinator.consumeReasons()
            val switchPreview = buildSwitchPreview(selectedPlayer, snapshot)
            val damageResult = damageEngine.compute(
                snapshot = snapshot,
                playerSnapshot = selectedPlayer,
                opponentSnapshot = effectiveOpponent,
                inferredSet = effectiveSet,
                playerEffectiveCurrentHp = switchPreview.effectiveCurrentHp,
                emphasizeSelectedMove = selectedPlayer?.uuid == snapshot.playerActiveUuid
            )
            val reasonText = if (reasons.isEmpty()) {
                "Snapshot stable"
            } else {
                "Recomputed: " + reasons.joinToString(", ") { it.name.lowercase().replace('_', ' ') }
            }
            val warningText = damageResult.warnings.takeIf { it.isNotEmpty() }?.joinToString(" | ")
            val trackedFallbackMoves = TeamIndicatorUI.getTrackedRevealedMoves(
                displayName = selectedOpponent?.displayName,
                speciesName = selectedOpponent?.speciesId,
                formName = selectedOpponent?.formName
            )
            val debugText = buildString {
                append("Calc rev: ")
                append(selectedOpponent?.revealedMoves?.joinToString(", ").orEmpty().ifBlank { "none" })
                append(" | Tracked rev: ")
                append(trackedFallbackMoves.joinToString(", ").ifBlank { "none" })
                append(" | Rendered: ")
                append(damageResult.opponentMoves.joinToString(", ") { it.moveName })
            }
            currentModel = CalcRenderModel(
                snapshot = snapshot,
                selectedPlayerUuid = selectedPlayer?.uuid,
                selectedOpponentUuid = selectedOpponent?.uuid,
                selectedPlayer = selectedPlayer,
                selectedOpponent = effectiveOpponent,
                playerTabs = buildTabs(snapshot.playerTeam, selectedPlayer?.uuid, snapshot.playerActiveUuid),
                opponentTabs = buildTabs(snapshot.opponentTeam, selectedOpponent?.uuid, snapshot.opponentActiveUuid),
                matchupLabel = buildMatchupLabel(selectedPlayer, effectiveOpponent),
                switchSummaryText = switchPreview.summaryText,
                hazardNoteText = switchPreview.hazardNoteText,
                isPreview = selectedPlayer?.uuid != snapshot.playerActiveUuid || selectedOpponent?.uuid != snapshot.opponentActiveUuid,
                opponentSet = effectiveSet,
                yourMoves = damageResult.yourMoves.map(::toRow),
                opponentMoves = damageResult.opponentMoves.map(::toRow),
                statusText = listOfNotNull(reasonText, warningText).joinToString(" | "),
                speedText = buildSpeedText(selectedPlayer, effectiveOpponent, effectiveSet),
                debugText = debugText
            )
            lastSelectionFingerprint = selectionFingerprint
            currentModel?.let { DebugDumper.onComputation(it, damageResult) }
        }
        return currentModel
    }

    fun selectPlayerPreview(uuid: UUID?) {
        previewSelectionState.selectPlayer(uuid)
        currentModel = null
    }

    fun selectOpponentPreview(uuid: UUID?) {
        previewSelectionState.selectOpponent(uuid)
        currentModel = null
    }

    fun cycleItem(uuid: UUID, alternativesSize: Int, direction: Int = 1) = cycle(uuid, OverrideRow.ITEM, alternativesSize, direction)
    fun cycleAbility(uuid: UUID, alternativesSize: Int, direction: Int = 1) = cycle(uuid, OverrideRow.ABILITY, alternativesSize, direction)
    fun cycleSpread(uuid: UUID, alternativesSize: Int, direction: Int = 1) = cycle(uuid, OverrideRow.SPREAD, alternativesSize, direction)

    fun resetOverride(uuid: UUID, row: OverrideRow) {
        val existing = overrides[uuid] ?: return
        val updated = when (row) {
            OverrideRow.ITEM -> existing.copy(itemIndex = null)
            OverrideRow.ABILITY -> existing.copy(abilityIndex = null)
            OverrideRow.SPREAD -> existing.copy(spreadIndex = null)
        }
        if (updated == OpponentOverride()) overrides.remove(uuid) else overrides[uuid] = updated
        currentModel = null
    }

    fun hasOverride(uuid: UUID, row: OverrideRow): Boolean {
        val o = overrides[uuid] ?: return false
        return when (row) {
            OverrideRow.ITEM -> o.itemIndex != null
            OverrideRow.ABILITY -> o.abilityIndex != null
            OverrideRow.SPREAD -> o.spreadIndex != null
        }
    }

    private fun cycle(uuid: UUID, row: OverrideRow, alternativesSize: Int, direction: Int) {
        if (alternativesSize <= 1 || direction == 0) return
        val existing = overrides[uuid] ?: OpponentOverride()
        val current = when (row) {
            OverrideRow.ITEM -> existing.itemIndex
            OverrideRow.ABILITY -> existing.abilityIndex
            OverrideRow.SPREAD -> existing.spreadIndex
        }
        // Position 0 is the inferred default (stored as null). Cycle forward
        // (+1) goes 0 -> 1 -> ... -> size-1 -> 0; backward (-1) wraps the
        // other way. Floor-mod keeps the index in [0, size).
        val currentIdx = current ?: 0
        val raw = currentIdx + direction
        val newIdx = ((raw % alternativesSize) + alternativesSize) % alternativesSize
        val next: Int? = if (newIdx == 0) null else newIdx
        val updated = when (row) {
            OverrideRow.ITEM -> existing.copy(itemIndex = next)
            OverrideRow.ABILITY -> existing.copy(abilityIndex = next)
            OverrideRow.SPREAD -> existing.copy(spreadIndex = next)
        }
        if (updated == OpponentOverride()) overrides.remove(uuid) else overrides[uuid] = updated
        currentModel = null
    }

    private fun applyOverride(inferredSet: EffectiveBattleSet, opponent: CalcPokemonSnapshot?): EffectiveBattleSet {
        val uuid = opponent?.uuid ?: return inferredSet
        val override = overrides[uuid] ?: return inferredSet

        // Real reveals always beat overrides — if the battle log has revealed item/ability,
        // the inferred state is already REVEALED and we leave it alone.
        val newItem = override.itemIndex
            ?.takeIf { opponent.itemName == null && it < inferredSet.itemAlternatives.size }
            ?.let { inferredSet.itemAlternatives[it] to InferenceValueState.REVEALED }
            ?: inferredSet.item

        val newAbility = override.abilityIndex
            ?.takeIf { opponent.abilityName == null && it < inferredSet.abilityAlternatives.size }
            ?.let { inferredSet.abilityAlternatives[it] to InferenceValueState.REVEALED }
            ?: inferredSet.ability

        val (newSpread, newSpreadLabel) = override.spreadIndex
            ?.takeIf { it < inferredSet.spreadAlternatives.size }
            ?.let {
                val s = inferredSet.spreadAlternatives[it].copy(state = InferenceValueState.REVEALED)
                s to "${s.nature} ${formatSpreadEvs(s.evs)}"
            }
            ?: (inferredSet.spread to inferredSet.spreadLabel)

        return inferredSet.copy(
            item = newItem,
            ability = newAbility,
            spread = newSpread,
            spreadLabel = newSpreadLabel
        )
    }

    private fun formatSpreadEvs(evs: CalcStats): String {
        return listOf(evs.hp, evs.atk, evs.def, evs.spa, evs.spd, evs.spe).joinToString("/") { it.toString() }
    }

    // Curated list of common competitive items appended to per-species usage
    // alternatives so every mon has a reasonable cycle even when usage data
    // is thin (e.g. low-tier or custom species). Order is roughly "most
    // versatile / most-frequently-correct guess" first.
    private val COMMON_ITEMS = listOf(
        "Leftovers", "Life Orb", "Heavy-Duty Boots",
        "Choice Band", "Choice Specs", "Choice Scarf",
        "Assault Vest", "Focus Sash", "Rocky Helmet",
        "Eviolite", "Air Balloon", "Black Sludge"
    )

    /**
     * Expands the inferred alternatives lists so the cycle UI always has
     * something to scroll through:
     * - Abilities: merges usage-stat ranking with the species' full legal
     *   ability set from Cobblemon's PokemonSpecies API. Mons whose usage
     *   only shows one ability (Ferrothorn -> Iron Barbs) gain Anticipation
     *   / hidden abilities as cycle options.
     * - Items: appends a curated list of common competitive items deduped
     *   against the usage-stat alternatives, so even custom / niche mons
     *   have multiple options to cycle through.
     */
    private fun expandAlternatives(
        inferredSet: EffectiveBattleSet,
        opponent: CalcPokemonSnapshot?
    ): EffectiveBattleSet {
        opponent ?: return inferredSet

        // Abilities: pull species.abilities and append any legal ones not
        // already covered by usage stats. Defensive lookup so delta species
        // or species missing form data fall back to usage-only.
        val expandedAbilities = run {
            val identifier = opponent.speciesId?.let { resolveSpeciesIdentifier(it) }
            val species = identifier?.let { PokemonSpecies.getByIdentifier(it) }
            val speciesAbilities = species?.let {
                runCatching { it.abilities.mapNotNull { ab -> ab.template.name } }.getOrNull()
            } ?: emptyList()
            if (speciesAbilities.isEmpty()) {
                inferredSet.abilityAlternatives
            } else {
                val knownNorm = inferredSet.abilityAlternatives.map(::normalizeToken).toSet()
                val additions = speciesAbilities
                    .filter { normalizeToken(it) !in knownNorm }
                    .distinctBy(::normalizeToken)
                    .map { name -> name.split(" ", "-", "_").joinToString(" ") { tok -> tok.replaceFirstChar { c -> c.uppercaseChar() } } }
                (inferredSet.abilityAlternatives + additions).take(6)
            }
        }

        // Items: append curated common items deduped against usage alternatives.
        val knownItemsNorm = inferredSet.itemAlternatives.map(::normalizeToken).toSet()
        val additionalItems = COMMON_ITEMS.filter { normalizeToken(it) !in knownItemsNorm }
        val expandedItems = (inferredSet.itemAlternatives + additionalItems)
            .distinctBy(::normalizeToken)
            .take(8)

        return inferredSet.copy(
            itemAlternatives = expandedItems,
            abilityAlternatives = expandedAbilities
        )
    }

    // Curated mega-stone -> form-aspect map. Most stones map to the "mega"
    // aspect; Charizardite / Mewtwonite have X/Y variants.
    private val MEGA_STONE_FORMS = mapOf(
        "abomasite" to "mega", "absolite" to "mega", "aerodactylite" to "mega",
        "aggronite" to "mega", "alakazite" to "mega", "altarianite" to "mega",
        "ampharosite" to "mega", "audinite" to "mega", "banettite" to "mega",
        "beedrillite" to "mega", "blastoisinite" to "mega", "blazikenite" to "mega",
        "cameruptite" to "mega", "diancite" to "mega", "galladite" to "mega",
        "garchompite" to "mega", "gardevoirite" to "mega", "gengarite" to "mega",
        "glalitite" to "mega", "gyaradosite" to "mega", "heracronite" to "mega",
        "houndoominite" to "mega", "kangaskhanite" to "mega", "latiasite" to "mega",
        "latiosite" to "mega", "lopunnite" to "mega", "lucarionite" to "mega",
        "manectite" to "mega", "mawilite" to "mega", "medichamite" to "mega",
        "metagrossite" to "mega", "pidgeotite" to "mega", "pinsirite" to "mega",
        "sablenite" to "mega", "salamencite" to "mega", "sceptilite" to "mega",
        "scizorite" to "mega", "sharpedonite" to "mega", "slowbronite" to "mega",
        "steelixite" to "mega", "swampertite" to "mega", "tyranitarite" to "mega",
        "venusaurite" to "mega",
        "charizarditex" to "mega-x", "charizarditey" to "mega-y",
        "mewtwonitex" to "mega-x", "mewtwonitey" to "mega-y"
    )

    private fun megaFormAspect(itemName: String?): String? {
        if (itemName.isNullOrBlank()) return null
        val key = itemName.lowercase().replace(" ", "").replace("-", "").replace("'", "")
        return MEGA_STONE_FORMS[key]
    }

    /**
     * If the effective item is a mega stone for the opponent's species, swaps the
     * opponent snapshot to that mega form (new base stats, types, intrinsic
     * ability if not user-overridden) so damage / speed reflect post-mega values.
     * Returns the original opponent + set when no swap applies.
     */
    private fun applyMegaFormSwap(
        opponent: CalcPokemonSnapshot?,
        effectiveSet: EffectiveBattleSet
    ): Pair<CalcPokemonSnapshot?, EffectiveBattleSet> {
        opponent ?: return null to effectiveSet
        val itemName = effectiveSet.item.first ?: return opponent to effectiveSet
        val aspect = megaFormAspect(itemName) ?: return opponent to effectiveSet

        val baseSpeciesId = opponent.speciesId ?: return opponent to effectiveSet
        val identifier = resolveSpeciesIdentifier(baseSpeciesId) ?: return opponent to effectiveSet
        val species = PokemonSpecies.getByIdentifier(identifier) ?: return opponent to effectiveSet
        val standard = species.standardForm
        val candidates = if (aspect == "mega-x") listOf("mega-x", "megax")
            else if (aspect == "mega-y") listOf("mega-y", "megay")
            else listOf("mega")
        val megaForm = candidates.firstNotNullOfOrNull { variant ->
            runCatching { species.getForm(setOf(variant)) }.getOrNull()?.takeIf { it != standard }
        } ?: return opponent to effectiveSet

        val newBaseStats = CalcStats(
            hp = megaForm.baseStats[Stats.HP] ?: opponent.baseStats?.hp ?: 0,
            atk = megaForm.baseStats[Stats.ATTACK] ?: opponent.baseStats?.atk ?: 0,
            def = megaForm.baseStats[Stats.DEFENCE] ?: opponent.baseStats?.def ?: 0,
            spa = megaForm.baseStats[Stats.SPECIAL_ATTACK] ?: opponent.baseStats?.spa ?: 0,
            spd = megaForm.baseStats[Stats.SPECIAL_DEFENCE] ?: opponent.baseStats?.spd ?: 0,
            spe = megaForm.baseStats[Stats.SPEED] ?: opponent.baseStats?.spe ?: 0
        )
        val newTypes = listOfNotNull(megaForm.primaryType?.name, megaForm.secondaryType?.name)
        val megaAbility = runCatching {
            megaForm.abilities.mapNotNull { it.template.name }.firstOrNull()
        }.getOrNull()

        val formSuffix = when (aspect) {
            "mega-x" -> " X"
            "mega-y" -> " Y"
            else -> ""
        }
        val transformedOpponent = opponent.copy(
            baseStats = newBaseStats,
            actualStats = null, // re-derive from new base stats
            typeNames = newTypes,
            formName = "Mega$formSuffix",
            speciesLabel = "${opponent.speciesLabel} (Mega$formSuffix)",
            // Null out so the EffectiveBattleSet's ability wins downstream — this
            // lets the mega form's intrinsic ability (filled in below) apply.
            abilityName = null
        )

        val abilityOverridden = overrides[opponent.uuid]?.abilityIndex != null
        val nextAbility = if (!abilityOverridden && !megaAbility.isNullOrBlank()) {
            megaAbility to InferenceValueState.REVEALED
        } else {
            effectiveSet.ability
        }

        return transformedOpponent to effectiveSet.copy(ability = nextAbility)
    }

    private fun toRow(estimate: DamageEstimate): CalcMoveRow {
        val damageText = when {
            !estimate.supported -> estimate.warnings.firstOrNull()?.lowercase() ?: "unsupported"
            estimate.minPercent != null && estimate.maxPercent != null -> buildString {
                append(formatPercent(estimate.minPercent))
                append(" - ")
                append(formatPercent(estimate.maxPercent))
                append('%')
                if (PanelConfig.showMultiHitCount && estimate.maxHits > 1) {
                    if (estimate.minHits == estimate.maxHits) {
                        append(" ×").append(estimate.minHits)
                    } else {
                        append(" ×").append(estimate.minHits).append('-').append(estimate.maxHits)
                    }
                }
                if (PanelConfig.showCritDamage && estimate.critMinPercent != null && estimate.critMaxPercent != null) {
                    append(" / crit ")
                    append(formatPercent(estimate.critMinPercent))
                    append('-')
                    append(formatPercent(estimate.critMaxPercent))
                    append('%')
                }
            }
            else -> "best-effort"
        }
        return CalcMoveRow(
            moveName = estimate.moveName,
            damageText = damageText,
            koText = estimate.koLabel,
            emphasized = estimate.emphasized,
            minPercent = estimate.minPercent,
            maxPercent = estimate.maxPercent,
            isStatus = estimate.koLabel.equals("status", ignoreCase = true)
        )
    }

    private fun formatPercent(value: Double): String {
        val roundedTenth = (value * 10.0).roundToInt() / 10.0
        return if (roundedTenth == roundedTenth.toInt().toDouble()) {
            roundedTenth.toInt().toString()
        } else {
            roundedTenth.toString()
        }
    }

    private fun buildTabs(
        team: List<CalcPokemonSnapshot>,
        selectedUuid: UUID?,
        activeUuid: UUID?
    ): List<CalcPreviewTab> {
        return team.map { pokemon ->
            CalcPreviewTab(
                uuid = pokemon.uuid,
                label = tabLabel(pokemon),
                isSelected = pokemon.uuid == selectedUuid,
                isActive = pokemon.uuid == activeUuid,
                isDisabled = pokemon.currentHp <= 0
            )
        }
    }

    private fun buildMatchupLabel(player: CalcPokemonSnapshot?, opponent: CalcPokemonSnapshot?): String {
        val playerName = player?.speciesLabel ?: player?.displayName ?: "Your Active"
        val opponentName = opponent?.speciesLabel ?: opponent?.displayName ?: "Opponent Active"
        return "$playerName -> $opponentName"
    }

    private fun buildSpeedText(player: CalcPokemonSnapshot?, opponent: CalcPokemonSnapshot?, inferredSet: EffectiveBattleSet): String? {
        player ?: return null
        opponent ?: return null
        val playerBaseSpeed = player.actualStats?.spe ?: return null
        val playerEffective = TeamIndicatorUI.calculateEffectiveSpeed(
            playerBaseSpeed,
            player.stageFor("spe"),
            player.abilityName,
            resolveStatus(player.status),
            player.itemName,
            false
        )

        val opponentRange = resolveOpponentSpeedRange(opponent)
        val guessedOpponentSpeed = resolveGuessedOpponentSpeed(opponent, inferredSet)

        return when {
            opponentRange != null && playerEffective > opponentRange.maxSpeed -> "Speed: You move first"
            opponentRange != null && playerEffective < opponentRange.minSpeed -> "Speed: Opponent moves first"
            guessedOpponentSpeed != null && playerEffective > guessedOpponentSpeed -> "Speed: You likely move first"
            guessedOpponentSpeed != null && playerEffective < guessedOpponentSpeed -> "Speed: Opponent likely moves first"
            guessedOpponentSpeed != null -> "Speed: Likely speed tie"
            opponentRange != null -> "Speed: Speed tie window"
            else -> null
        }?.let { "$it ($playerEffective vs ${formatOpponentSpeed(opponentRange, guessedOpponentSpeed)})" }
    }

    private fun buildSwitchPreview(player: CalcPokemonSnapshot?, snapshot: CalcBattleSnapshot): SwitchPreviewInfo {
        player ?: return SwitchPreviewInfo(0, null, null)
        if (player.uuid == snapshot.playerActiveUuid) {
            return SwitchPreviewInfo(
                effectiveCurrentHp = player.currentHp,
                summaryText = "On field HP: ${player.currentHp}/${player.maxHp} (${formatPercent(player.currentHp * 100.0 / player.maxHp.coerceAtLeast(1))}%)",
                hazardNoteText = null
            )
        }

        val notes = mutableListOf<String>()
        var currentHp = player.currentHp
        val maxHp = player.maxHp.coerceAtLeast(1)

        if (snapshot.opponentSide.hasCondition("Stealth Rock")) {
            val rockDamage = floor(maxHp * stealthRockFraction(player)).toInt().coerceAtLeast(1)
            currentHp = (currentHp - rockDamage).coerceAtLeast(0)
            notes += "Stealth Rock -${formatPercent(rockDamage * 100.0 / maxHp)}%"
        }

        val spikesLayers = snapshot.opponentSide.layersFor("Spikes")
        if (spikesLayers > 0 && isGrounded(player)) {
            val spikesFraction = when (spikesLayers.coerceAtMost(3)) {
                1 -> 1.0 / 8.0
                2 -> 1.0 / 6.0
                else -> 1.0 / 4.0
            }
            val spikesDamage = floor(maxHp * spikesFraction).toInt().coerceAtLeast(1)
            currentHp = (currentHp - spikesDamage).coerceAtLeast(0)
            notes += "Spikes x$spikesLayers -${formatPercent(spikesDamage * 100.0 / maxHp)}%"
        }

        if (snapshot.opponentSide.layersFor("Toxic Spikes") > 0 && isGrounded(player) && !isToxicSpikesImmune(player)) {
            notes += "Toxic Spikes on entry"
        }
        if (snapshot.opponentSide.hasCondition("Sticky Web") && isGrounded(player)) {
            notes += "Sticky Web speed drop"
        }

        val noteText = notes.takeIf { it.isNotEmpty() }?.joinToString(" | ")
        return SwitchPreviewInfo(
            effectiveCurrentHp = currentHp,
            summaryText = "Switch-in HP: $currentHp/$maxHp (${formatPercent(currentHp * 100.0 / maxHp) }%)",
            hazardNoteText = noteText
        )
    }

    private fun stealthRockFraction(player: CalcPokemonSnapshot): Double {
        val rockType = ElementalTypes.get("rock") ?: return 0.125
        val effectiveness = player.typeNames
            .mapNotNull { ElementalTypes.get(it.lowercase()) }
            .fold(1.0) { acc, defendingType -> acc * AIUtility.getDamageMultiplier(rockType, defendingType) }
        return 0.125 * effectiveness
    }

    private fun isGrounded(player: CalcPokemonSnapshot): Boolean {
        return !player.hasType("flying") && normalizeToken(player.abilityName) != "levitate"
    }

    private fun isToxicSpikesImmune(player: CalcPokemonSnapshot): Boolean {
        return player.hasType("poison") || player.hasType("steel") || !isGrounded(player)
    }

    private fun tabLabel(pokemon: CalcPokemonSnapshot): String {
        // Return the full species label (or display name). The UI decides how
        // much fits and truncates based on actual tab width.
        return pokemon.speciesLabel.ifBlank { pokemon.displayName }
    }

    private fun resolveOpponentSpeedRange(opponent: CalcPokemonSnapshot): TeamIndicatorUI.SpeedRangeResult? {
        val speciesId = resolveSpeciesIdentifier(opponent.speciesId ?: opponent.speciesKey) ?: return null
        val trackedItem = opponent.itemName?.let {
            BattleStateTracker.TrackedItem(it, BattleStateTracker.ItemStatus.HELD, 0)
        }
        return TeamIndicatorUI.calculateOpponentSpeedRange(
            opponent.uuid,
            speciesId,
            opponent.level,
            opponent.stageFor("spe"),
            resolveStatus(opponent.status),
            trackedItem,
            null
        )
    }

    private fun resolveGuessedOpponentSpeed(opponent: CalcPokemonSnapshot, inferredSet: EffectiveBattleSet): Int? {
        val spread = inferredSet.spread ?: return null
        val baseSpeed = opponent.baseStats?.spe ?: return null
        val derivedSpeed = StatCalculator.calculateStat(
            baseSpeed,
            opponent.level,
            31,
            spread.evs.spe,
            natureSpeedModifier(spread.nature)
        )
        val effectiveAbility = opponent.abilityName ?: inferredSet.ability.first
        val effectiveItem = opponent.itemName ?: inferredSet.item.first
        return TeamIndicatorUI.calculateEffectiveSpeed(
            derivedSpeed,
            opponent.stageFor("spe"),
            effectiveAbility,
            resolveStatus(opponent.status),
            effectiveItem,
            false
        )
    }

    private fun formatOpponentSpeed(
        range: TeamIndicatorUI.SpeedRangeResult?,
        guessedSpeed: Int?
    ): String {
        return when {
            range != null -> "${range.minSpeed}-${range.maxSpeed}"
            guessedSpeed != null -> guessedSpeed.toString()
            else -> "?"
        }
    }

    private fun resolveSpeciesIdentifier(speciesId: String?): Identifier? {
        if (speciesId.isNullOrBlank()) return null
        return Identifier.tryParse(speciesId) ?: Identifier.of("cobblemon", speciesId)
    }

    private fun resolveStatus(statusId: String?): Status? {
        return when (statusId?.lowercase()) {
            "poison" -> Statuses.POISON
            "poisonbadly", "badpoison", "toxic" -> Statuses.POISON_BADLY
            "burn" -> Statuses.BURN
            "paralysis", "paralyzed", "par" -> Statuses.PARALYSIS
            "frozen", "freeze" -> Statuses.FROZEN
            "sleep", "asleep" -> Statuses.SLEEP
            else -> null
        }
    }

    private fun natureSpeedModifier(nature: String): Double {
        return when (nature.lowercase()) {
            "timid", "hasty", "jolly", "naive" -> 1.1
            "brave", "relaxed", "quiet", "sassy" -> 0.9
            else -> 1.0
        }
    }

    private data class SwitchPreviewInfo(
        val effectiveCurrentHp: Int,
        val summaryText: String?,
        val hazardNoteText: String?
    )
}
