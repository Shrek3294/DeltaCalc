package com.cobblemonextendedbattleui.calc

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.api.pokemon.stats.Stats
import com.cobblemon.mod.common.client.CobblemonClient
import com.cobblemon.mod.common.client.battle.ClientBattlePokemon
import com.cobblemon.mod.common.pokemon.FormData
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemonextendedbattleui.BattleMoveSupport
import com.cobblemonextendedbattleui.BattleStateTracker
import com.cobblemonextendedbattleui.CobblemonExtendedBattleUI
import com.cobblemonextendedbattleui.battle.state.FormTracker
import com.cobblemonextendedbattleui.pokemon.stats.StatCalculator
import com.cobblemonextendedbattleui.tracking.TrackedBattleTruth
import com.cobblemonextendedbattleui.tracking.TrackedBaseStats
import com.cobblemonextendedbattleui.tracking.TrackedPokemonTruth
import net.minecraft.util.Identifier
import java.util.concurrent.ConcurrentHashMap

object CalcBattleSnapshotFactory {
    private val loggedTypeMismatches = ConcurrentHashMap.newKeySet<String>()

    fun fromTruth(truth: TrackedBattleTruth, battleDatabase: BattleDatabase): CalcBattleSnapshot {
        val playerTeam = truth.playerTeam.map { tracked ->
            val partyPokemon = CobblemonClient.storage.party.findByUUID(tracked.uuid)
            val battlePokemon = tracked.uuid.let(::findActiveBattlePokemon)
            tracked.toCalc(battlePokemon, partyPokemon, isPlayerSide = true, battleDatabase = battleDatabase)
        }
        val opponentTeam = truth.opponentTeam.map { tracked ->
            val battlePokemon = tracked.uuid.let(::findActiveBattlePokemon)
            tracked.toCalc(battlePokemon, null, isPlayerSide = false, battleDatabase = battleDatabase)
        }
        val playerActive = playerTeam.firstOrNull { it.uuid == truth.playerActiveUuid }
        val opponentActive = opponentTeam.firstOrNull { it.uuid == truth.opponentActiveUuid }

        return CalcBattleSnapshot(
            battleId = truth.battleId,
            turn = truth.turn,
            weather = truth.weather,
            terrain = truth.terrain,
            playerSide = CalcSideState(truth.playerSideConditions),
            opponentSide = CalcSideState(truth.opponentSideConditions),
            playerTeam = playerTeam,
            opponentTeam = opponentTeam,
            playerActiveUuid = truth.playerActiveUuid,
            opponentActiveUuid = truth.opponentActiveUuid,
            playerActive = playerActive,
            opponentActive = opponentActive,
            selectedMoveName = truth.selectedMoveName,
            compatNotes = truth.compatNotes
        )
    }

    private fun TrackedPokemonTruth.toCalc(
        battlePokemon: ClientBattlePokemon?,
        partyPokemon: Pokemon?,
        isPlayerSide: Boolean,
        battleDatabase: BattleDatabase
    ): CalcPokemonSnapshot {
        val speciesEntry = battleDatabase.findSpecies(speciesKey, speciesId, speciesLabel, displayName)
        val currentHp = if (partyPokemon != null) partyPokemon.currentHealth else currentHp
        val maxHp = if (partyPokemon != null) partyPokemon.maxHealth else maxHp
        val exactHpValues = partyPokemon != null || battlePokemon?.isHpFlat == true
        val level = battlePokemon?.level ?: partyPokemon?.level ?: 100
        val baseStats = formBaseStats?.toCalcStats()
            ?: resolveBaseStats(battlePokemon, partyPokemon, speciesId, formName)
            ?: speciesEntry?.baseStats?.toCalcStats()
        // After a mega evolved Pokemon switches out and back in, Cobblemon has a
        // known visual glitch where the species briefly reports base-form stats
        // even though it is still mega. In that state `partyPokemon.attack` etc.
        // reflect the base form, which would silently downgrade the calc. When
        // FormTracker says this UUID is mega (which we now preserve across
        // switches), re-derive actualStats from the mega baseStats so the damage
        // engine doesn't lose the mega's stat boosts.
        val isMegaTracked = FormTracker.getCurrentForm(uuid)?.isMega == true
        val actualStats = if (partyPokemon != null && !isMegaTracked) {
            CalcStats(
                hp = partyPokemon.maxHealth,
                atk = partyPokemon.attack,
                def = partyPokemon.defence,
                spa = partyPokemon.specialAttack,
                spd = partyPokemon.specialDefence,
                spe = partyPokemon.speed
            )
        } else {
            // Fallback for cases where the battle Pokemon isn't in the player's
            // party storage — e.g. custom-rule battles that clone the team at
            // a forced level (lvl 50 cap, random battle, etc.) — and the mega
            // re-derive path above. Approximate stats are better than nothing;
            // a revealed item/ability or manual override will tighten the calc.
            baseStats?.let { derivedHeuristicStats(it, level) }
        }
        val runtimeTypes = resolveRuntimeOverrideTypes(uuid)
        val dbTypes = speciesEntry?.typeNames.orEmpty()
        val liveFormTypes = formTypeNames.ifEmpty {
            resolveFormTypes(battlePokemon, partyPokemon, speciesId, formName)
        }
        if (dbTypes.isNotEmpty() && liveFormTypes.isNotEmpty() && !typesMatch(dbTypes, liveFormTypes)) {
            val mismatchKey = speciesEntry?.speciesKey ?: speciesId ?: displayName
            if (loggedTypeMismatches.add(mismatchKey)) {
                CobblemonExtendedBattleUI.LOGGER.info(
                    "DeltaCalc type override for {}: mod form reports {} but database says {} — using database",
                    mismatchKey, liveFormTypes, dbTypes
                )
            }
        }
        val typeNames = when {
            runtimeTypes.isNotEmpty() -> runtimeTypes
            dbTypes.isNotEmpty() -> dbTypes
            liveFormTypes.isNotEmpty() -> liveFormTypes
            else -> emptyList()
        }
        val teraType = BattleStateTracker.getTeraType(uuid)
        val itemName = resolveKnownItem(uuid, partyPokemon, revealedItem)
        val abilityName = if (isPlayerSide) {
            partyPokemon?.ability?.name ?: revealedAbility
        } else {
            revealedAbility
        }
        val normalizedRevealedMoves = revealedMoves.map(BattleMoveSupport::resolveDisplayName)

        val resolvedMoves = if (isPlayerSide) {
            resolvePlayerMoves(partyPokemon, moveList)
        } else {
            normalizedRevealedMoves.map { moveName ->
                CalcMoveRef(
                    id = resolveMoveId(moveName),
                    displayName = BattleMoveSupport.resolveDisplayName(moveName)
                )
            }
        }

        return CalcPokemonSnapshot(
            uuid = uuid,
            displayName = displayName,
            speciesId = speciesId,
            speciesKey = speciesEntry?.speciesKey ?: speciesKey,
            speciesLabel = speciesEntry?.displayName ?: speciesLabel,
            formName = speciesEntry?.formName ?: formName,
            level = level,
            currentHp = currentHp,
            maxHp = maxHp,
            exactHpValues = exactHpValues,
            status = status,
            typeNames = typeNames,
            teraType = teraType,
            itemName = itemName,
            abilityName = abilityName,
            revealedMoves = normalizedRevealedMoves,
            statStages = statStages,
            moveList = resolvedMoves,
            baseStats = baseStats,
            actualStats = actualStats,
            canEvolve = StatCalculator.canPokemonEvolve(resolveSpeciesIdentifier(speciesId)),
            weightKg = speciesEntry?.weightKg
        )
    }

    private fun resolvePlayerMoves(partyPokemon: Pokemon?, trackedMoves: List<String>): List<CalcMoveRef> {
        if (partyPokemon != null) {
            return partyPokemon.moveSet.getMoves().map { move ->
                CalcMoveRef(
                    id = resolveMoveId(move.template.name),
                    displayName = BattleMoveSupport.resolveDisplayName(move.displayName.string)
                )
            }
        }
        return trackedMoves.map { moveName ->
            CalcMoveRef(
                id = resolveMoveId(moveName),
                displayName = BattleMoveSupport.resolveDisplayName(moveName)
            )
        }
    }

    private fun resolveKnownItem(uuid: java.util.UUID, partyPokemon: Pokemon?, revealedItem: String?): String? {
        val trackedItem = BattleStateTracker.getItem(uuid)
        if (trackedItem != null && trackedItem.status != BattleStateTracker.ItemStatus.HELD) {
            return null
        }
        if (partyPokemon != null) {
            val heldItem = partyPokemon.heldItem()
            if (!heldItem.isEmpty) {
                return heldItem.name.string
            }
        }
        return trackedItem?.name ?: revealedItem
    }

    private fun resolveBaseStats(
        battlePokemon: ClientBattlePokemon?,
        partyPokemon: Pokemon?,
        speciesId: String?,
        formName: String?
    ): CalcStats? {
        val form = when {
            partyPokemon != null -> partyPokemon.form
            battlePokemon != null -> resolveBattleForm(battlePokemon, speciesId, formName)
            else -> resolveSpeciesIdentifier(speciesId)?.let(PokemonSpecies::getByIdentifier)?.standardForm
        } ?: return null

        return form.baseStats.toCalcStats()
    }

    private fun resolveRuntimeOverrideTypes(uuid: java.util.UUID): List<String> {
        BattleStateTracker.getTeraType(uuid)?.let { teraType ->
            return listOf(teraType)
        }

        BattleStateTracker.getDynamicTypes(uuid)?.let { dynamic ->
            val dynamicTypes = buildList {
                dynamic.primaryType?.let(::add)
                dynamic.secondaryType?.let(::add)
                addAll(dynamic.addedTypes)
            }
            if (dynamicTypes.isNotEmpty()) {
                return dynamicTypes
            }
        }

        return emptyList()
    }

    private fun resolveFormTypes(
        battlePokemon: ClientBattlePokemon?,
        partyPokemon: Pokemon?,
        speciesId: String?,
        formName: String?
    ): List<String> {
        val form = when {
            partyPokemon != null -> partyPokemon.form
            battlePokemon != null -> resolveBattleForm(battlePokemon, speciesId, formName)
            else -> resolveSpeciesIdentifier(speciesId)?.let(PokemonSpecies::getByIdentifier)?.standardForm
        }

        return listOfNotNull(form?.primaryType?.name, form?.secondaryType?.name)
    }

    private fun typesMatch(a: List<String>, b: List<String>): Boolean {
        if (a.size != b.size) return false
        return a.map { it.lowercase() }.toSet() == b.map { it.lowercase() }.toSet()
    }

    private fun resolveSpeciesIdentifier(speciesId: String?): Identifier? {
        if (speciesId.isNullOrBlank()) return null
        return Identifier.tryParse(speciesId) ?: Identifier.of("cobblemon", speciesId)
    }

    private fun findActiveBattlePokemon(uuid: java.util.UUID): ClientBattlePokemon? {
        val battle = CobblemonClient.battle ?: return null
        return sequenceOf(battle.side1, battle.side2)
            .flatMap { it.activeClientBattlePokemon.asSequence() }
            .mapNotNull { it.battlePokemon }
            .firstOrNull { it.uuid == uuid }
    }

    private fun resolveBattleForm(
        battlePokemon: ClientBattlePokemon,
        speciesId: String?,
        formName: String?
    ): FormData? {
        val explicitForm = resolveExplicitForm(speciesId, formName, battlePokemon.properties.form)
        val liveSpecies = runCatching { battlePokemon.species }.getOrNull()
        val fallbackSpecies = resolveSpeciesIdentifier(speciesId)?.let(PokemonSpecies::getByIdentifier)
        val species = liveSpecies ?: fallbackSpecies ?: return explicitForm
        val battleForm = runCatching { battlePokemon.species.getForm(battlePokemon.state.currentAspects) }.getOrNull()
        if (species.resourceIdentifier.path.equals("shaymin", ignoreCase = true)) {
            return resolveShayminForm(species, battleForm)
        }
        val namedForm = buildList {
            formName?.takeIf { it.isNotBlank() }?.let(::add)
            battlePokemon.properties.form?.takeIf { it.isNotBlank() }?.let(::add)
        }.firstOrNull()
        if (namedForm != null) {
            return explicitForm ?: species.standardForm
        }
        val aspectForm = runCatching { species.getForm(battlePokemon.state.currentAspects) }.getOrNull()
        val standardForm = species.standardForm

        return when {
            explicitForm != null && explicitForm != standardForm -> explicitForm
            aspectForm != null && aspectForm != standardForm -> aspectForm
            explicitForm != null -> explicitForm
            else -> standardForm
        }
    }

    private fun resolveShayminForm(
        species: com.cobblemon.mod.common.pokemon.Species,
        battleForm: FormData?
    ): FormData {
        val skyForm = runCatching { species.getForm(setOf("sky")) }.getOrNull()
        val battleHasFlying = battleForm?.primaryType?.name == "flying" || battleForm?.secondaryType?.name == "flying"
        return if (battleHasFlying) skyForm ?: species.standardForm else species.standardForm
    }

    private fun resolveExplicitForm(
        speciesId: String?,
        trackedFormName: String?,
        propertyFormName: String?
    ): FormData? {
        val species = resolveSpeciesIdentifier(speciesId)?.let(PokemonSpecies::getByIdentifier) ?: return null
        val candidates = buildList {
            trackedFormName?.takeIf { it.isNotBlank() }?.let(::add)
            propertyFormName?.takeIf { it.isNotBlank() }?.let(::add)
        }
        for (candidate in candidates) {
            val aspectCandidates = LinkedHashSet<String>()
            aspectCandidates += FormTracker.formNameToAspects(candidate)
            aspectCandidates += candidate.lowercase().replace(" ", "-")
            aspectCandidates += candidate.lowercase()
            for (aspect in aspectCandidates) {
                val form = species.getForm(setOf(aspect))
                if (form != species.standardForm || aspect == species.standardForm.aspects.firstOrNull()) {
                    return form
                }
            }
        }
        return null
    }

    private fun resolveMoveId(moveName: String): String {
        return BattleMoveSupport.resolveMoveId(moveName)
    }

    private fun Map<com.cobblemon.mod.common.api.pokemon.stats.Stat, Int>.toCalcStats(): CalcStats {
        return CalcStats(
            hp = this[Stats.HP] ?: 0,
            atk = this[Stats.ATTACK] ?: 0,
            def = this[Stats.DEFENCE] ?: 0,
            spa = this[Stats.SPECIAL_ATTACK] ?: 0,
            spd = this[Stats.SPECIAL_DEFENCE] ?: 0,
            spe = this[Stats.SPEED] ?: 0
        )
    }

    private fun TrackedBaseStats.toCalcStats(): CalcStats {
        return CalcStats(
            hp = hp,
            atk = atk,
            def = def,
            spa = spa,
            spd = spd,
            spe = spe
        )
    }

    private fun BattleBaseStats.toCalcStats(): CalcStats {
        return CalcStats(
            hp = hp,
            atk = atk,
            def = def,
            spa = spa,
            spd = spd,
            spe = spe
        )
    }

    /**
     * Heuristic stat block for the player side when partyPokemon is null
     * (custom-rule clone battles, lvl 50 cap, random battle, etc.) and for
     * mega-stone swaps that need a quick re-derive from new base stats.
     * Picks the same offensive/defensive spread shape that
     * build_default_delta_sets.py uses on the data-pipeline side, then runs
     * Cobblemon's standard level/IV/EV/nature math to produce final stats.
     * Approximate but within damage-roll variance, and revealed info
     * tightens it from there.
     */
    internal fun derivedHeuristicStats(base: CalcStats, level: Int): CalcStats {
        val isPhysical = base.atk >= base.spa
        val offensiveScore = maxOf(base.atk, base.spa) + base.spe
        val defensiveScore = base.hp + maxOf(base.def, base.spd)

        val (nature, evs) = if (offensiveScore >= defensiveScore) {
            val natureName = if (isPhysical) "Adamant" else "Modest"
            val evBlock = if (isPhysical) {
                CalcStats(hp = 4, atk = 252, def = 0, spa = 0, spd = 0, spe = 252)
            } else {
                CalcStats(hp = 4, atk = 0, def = 0, spa = 252, spd = 0, spe = 252)
            }
            natureName to evBlock
        } else {
            val bestDefPhysical = base.def >= base.spd
            val natureName = if (bestDefPhysical) "Bold" else "Calm"
            val evBlock = if (bestDefPhysical) {
                CalcStats(hp = 252, atk = 0, def = 252, spa = 0, spd = 0, spe = 4)
            } else {
                CalcStats(hp = 252, atk = 0, def = 0, spa = 0, spd = 252, spe = 4)
            }
            natureName to evBlock
        }

        val mods = heuristicNatureMods(nature)
        val hp = (((2 * base.hp + 31 + evs.hp / 4) * level) / 100) + level + 10
        return CalcStats(
            hp = hp,
            atk = StatCalculator.calculateStat(base.atk, level, 31, evs.atk, mods["atk"] ?: 1.0),
            def = StatCalculator.calculateStat(base.def, level, 31, evs.def, mods["def"] ?: 1.0),
            spa = StatCalculator.calculateStat(base.spa, level, 31, evs.spa, mods["spa"] ?: 1.0),
            spd = StatCalculator.calculateStat(base.spd, level, 31, evs.spd, mods["spd"] ?: 1.0),
            spe = StatCalculator.calculateStat(base.spe, level, 31, evs.spe, mods["spe"] ?: 1.0)
        )
    }

    private fun heuristicNatureMods(nature: String): Map<String, Double> {
        return when (nature) {
            "Adamant" -> mapOf("atk" to 1.1, "spa" to 0.9)
            "Modest"  -> mapOf("spa" to 1.1, "atk" to 0.9)
            "Bold"    -> mapOf("def" to 1.1, "atk" to 0.9)
            "Calm"    -> mapOf("spd" to 1.1, "atk" to 0.9)
            else      -> emptyMap()
        }
    }
}
