package com.cobblemonextendedbattleui.calc

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.api.pokemon.stats.Stats
import com.cobblemon.mod.common.client.CobblemonClient
import com.cobblemon.mod.common.client.battle.ClientBattlePokemon
import com.cobblemon.mod.common.pokemon.FormData
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemonextendedbattleui.BattleMoveSupport
import com.cobblemonextendedbattleui.BattleStateTracker
import com.cobblemonextendedbattleui.battle.state.FormTracker
import com.cobblemonextendedbattleui.pokemon.stats.StatCalculator
import com.cobblemonextendedbattleui.tracking.TrackedBattleTruth
import com.cobblemonextendedbattleui.tracking.TrackedBaseStats
import com.cobblemonextendedbattleui.tracking.TrackedPokemonTruth
import net.minecraft.util.Identifier

object CalcBattleSnapshotFactory {
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
        val actualStats = partyPokemon?.let {
            CalcStats(
                hp = it.maxHealth,
                atk = it.attack,
                def = it.defence,
                spa = it.specialAttack,
                spd = it.specialDefence,
                spe = it.speed
            )
        }
        val typeNames = formTypeNames.ifEmpty {
            resolveTypeNames(uuid, battlePokemon, partyPokemon, speciesId, formName)
        }.ifEmpty { speciesEntry?.typeNames ?: emptyList() }
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
            canEvolve = StatCalculator.canPokemonEvolve(resolveSpeciesIdentifier(speciesId))
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

    private fun resolveTypeNames(
        uuid: java.util.UUID,
        battlePokemon: ClientBattlePokemon?,
        partyPokemon: Pokemon?,
        speciesId: String?,
        formName: String?
    ): List<String> {
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

        val form = when {
            partyPokemon != null -> partyPokemon.form
            battlePokemon != null -> resolveBattleForm(battlePokemon, speciesId, formName)
            else -> resolveSpeciesIdentifier(speciesId)?.let(PokemonSpecies::getByIdentifier)?.standardForm
        }

        return listOfNotNull(form?.primaryType?.name, form?.secondaryType?.name)
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
}
