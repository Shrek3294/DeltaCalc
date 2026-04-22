package com.cobblemonextendedbattleui.tracking

import com.cobblemon.mod.common.client.CobblemonClient
import com.cobblemon.mod.common.client.battle.ClientBattlePokemon
import com.cobblemon.mod.common.client.battle.ClientBattleSide
import com.cobblemonextendedbattleui.BattleStateTracker
import com.cobblemonextendedbattleui.TeamIndicatorUI
import com.cobblemonextendedbattleui.compat.core.BattlePlatformAdapter
import com.cobblemonextendedbattleui.compat.delta.DeltaBattleInfoReader
import net.minecraft.client.MinecraftClient
import java.util.UUID
import kotlin.math.roundToInt

data class TrackedBaseStats(
    val hp: Int,
    val atk: Int,
    val def: Int,
    val spa: Int,
    val spd: Int,
    val spe: Int
)

data class TrackedPokemonTruth(
    val uuid: UUID,
    val displayName: String,
    val speciesId: String?,
    val speciesKey: String?,
    val speciesLabel: String,
    val formName: String?,
    val formTypeNames: List<String>,
    val formBaseStats: TrackedBaseStats?,
    val currentHp: Int,
    val maxHp: Int,
    val status: String?,
    val revealedMoves: List<String>,
    val revealedItem: String?,
    val revealedAbility: String?,
    val statStages: Map<String, Int>,
    val moveList: List<String>
)

data class TrackedBattleTruth(
    val battleId: UUID,
    val turn: Int,
    val weather: String?,
    val terrain: String?,
    val playerSideConditions: List<String>,
    val opponentSideConditions: List<String>,
    val playerActive: TrackedPokemonTruth?,
    val opponentActive: TrackedPokemonTruth?,
    val selectedMoveName: String?,
    val compatNotes: List<String>
)

object BattleStateFacade {
    fun capture(platform: BattlePlatformAdapter): TrackedBattleTruth? {
        val battle = CobblemonClient.battle ?: return null
        val playerUUID = MinecraftClient.getInstance().player?.uuid ?: return null

        val playerSide = when {
            battle.side1.actors.any { it.uuid == playerUUID } -> battle.side1
            battle.side2.actors.any { it.uuid == playerUUID } -> battle.side2
            else -> battle.side2
        }
        val opponentSide = if (playerSide == battle.side1) battle.side2 else battle.side1

        return TrackedBattleTruth(
            battleId = battle.battleId,
            turn = platform.currentTurn(),
            weather = BattleStateTracker.weather?.type?.displayName,
            terrain = BattleStateTracker.terrain?.type?.displayName,
            playerSideConditions = BattleStateTracker.getPlayerSideConditions().keys.map { it.displayName },
            opponentSideConditions = BattleStateTracker.getOpponentSideConditions().keys.map { it.displayName },
            playerActive = playerSide.activeClientBattlePokemon.firstOrNull()?.battlePokemon?.toTruth(playerSide, true),
            opponentActive = opponentSide.activeClientBattlePokemon.firstOrNull()?.battlePokemon?.toTruth(opponentSide, false),
            selectedMoveName = platform.selectedMoveName(),
            compatNotes = platform.compatNotes()
        )
    }

    private fun ClientBattlePokemon.toTruth(side: ClientBattleSide, isPlayerSide: Boolean): TrackedPokemonTruth {
        val actorPokemon = side.actors
            .flatMap { it.pokemon }
            .firstOrNull { it.uuid == uuid }
        val deltaRevealData = if (isPlayerSide) null else DeltaBattleInfoReader.activeOpponentRevealData()

        val trackedSpeciesId = BattleStateTracker.getSpeciesId(uuid)?.path
        val propertiesSpecies = properties.species
        val baseSpeciesId = trackedSpeciesId ?: propertiesSpecies
        val trackedFormName = BattleStateTracker.getCurrentForm(uuid)?.currentForm
        val propertyFormName = properties.form?.takeIf { it.isNotBlank() }
        val resolvedFormName = trackedFormName ?: propertyFormName
        val actorForm = actorPokemon?.form
        val resolvedRevealedMoves = buildSet {
            addAll(deltaRevealData?.moves.orEmpty())
            addAll(BattleStateTracker.getRevealedMoves(uuid))
            addAll(BattleStateTracker.getRevealedMovesByName(displayName.string, isPlayerSide))
            properties.species?.takeIf { it.isNotBlank() }?.let { addAll(BattleStateTracker.getRevealedMovesByName(it, isPlayerSide)) }
            resolvedFormName?.takeIf { it.isNotBlank() }?.let { formName ->
                properties.species?.takeIf { it.isNotBlank() }?.let { speciesName ->
                    addAll(BattleStateTracker.getRevealedMovesByName("$speciesName-$formName", isPlayerSide))
                }
                addAll(BattleStateTracker.getRevealedMovesByName(formName, isPlayerSide))
            }
            addAll(TeamIndicatorUI.getTrackedRevealedMoves(displayName.string, properties.species, resolvedFormName))
            if (!isPlayerSide) {
                addAll(TeamIndicatorUI.getActiveOpponentRevealedMoves())
            }
        }
        val (currentHp, maxHp) = when {
            actorPokemon != null -> actorPokemon.currentHealth to actorPokemon.maxHealth
            isHpFlat && maxHp > 0f -> hpValue.roundToInt().coerceAtLeast(0) to maxHp.roundToInt().coerceAtLeast(1)
            maxHp > 0f -> (hpValue * 100f).roundToInt().coerceIn(0, 100) to 100
            else -> 0 to 100
        }

        return TrackedPokemonTruth(
            uuid = uuid,
            displayName = displayName.string,
            speciesId = baseSpeciesId,
            speciesKey = canonicalSpeciesKey(baseSpeciesId, resolvedFormName),
            speciesLabel = canonicalSpeciesLabel(baseSpeciesId, displayName.string, resolvedFormName),
            formName = resolvedFormName,
            formTypeNames = listOfNotNull(actorForm?.primaryType?.name, actorForm?.secondaryType?.name),
            formBaseStats = actorForm?.baseStats?.let { stats ->
                TrackedBaseStats(
                    hp = stats[com.cobblemon.mod.common.api.pokemon.stats.Stats.HP] ?: 0,
                    atk = stats[com.cobblemon.mod.common.api.pokemon.stats.Stats.ATTACK] ?: 0,
                    def = stats[com.cobblemon.mod.common.api.pokemon.stats.Stats.DEFENCE] ?: 0,
                    spa = stats[com.cobblemon.mod.common.api.pokemon.stats.Stats.SPECIAL_ATTACK] ?: 0,
                    spd = stats[com.cobblemon.mod.common.api.pokemon.stats.Stats.SPECIAL_DEFENCE] ?: 0,
                    spe = stats[com.cobblemon.mod.common.api.pokemon.stats.Stats.SPEED] ?: 0
                )
            },
            currentHp = currentHp,
            maxHp = maxHp,
            status = status?.name?.path ?: properties.status,
            revealedMoves = resolvedRevealedMoves.toList(),
            revealedItem = deltaRevealData?.heldItem ?: BattleStateTracker.getItem(uuid)?.name,
            revealedAbility = deltaRevealData?.ability ?: BattleStateTracker.getRevealedAbility(uuid),
            statStages = BattleStateTracker.getStatChanges(uuid).mapKeys { it.key.displayName },
            moveList = if (isPlayerSide) {
                actorPokemon?.moveSet?.getMoves()?.map { it.displayName.string } ?: emptyList()
            } else {
                emptyList()
            }
        )
    }

    private fun canonicalSpeciesKey(baseSpeciesId: String?, formName: String?): String? {
        if (baseSpeciesId.isNullOrBlank()) return null
        val isDeltaNamespaced = baseSpeciesId.startsWith("delta:", ignoreCase = true)
        val normalizedBase = normalizeFormToken(baseSpeciesId)
        val normalizedForm = normalizeFormToken(formName)
        val effectiveForm = if (
            isDeltaNamespaced &&
            !normalizedBase.endsWith("-delta") &&
            !normalizedForm.contains("delta")
        ) {
            if (normalizedForm.isBlank()) "delta" else "$normalizedForm-delta"
        } else {
            normalizedForm
        }
        if (effectiveForm.isBlank()) return baseSpeciesId
        val trimmedForm = effectiveForm.removePrefix(normalizedBase).trim('-')
        return if (trimmedForm.isBlank()) baseSpeciesId else "$baseSpeciesId-$trimmedForm"
    }

    private fun canonicalSpeciesLabel(baseSpeciesId: String?, fallbackDisplayName: String, formName: String?): String {
        val baseLabel = baseSpeciesId
            ?.split("-", "_")
            ?.joinToString("-") { part -> part.replaceFirstChar { it.uppercase() } }
            ?: fallbackDisplayName
        val normalizedBase = normalizeFormToken(baseSpeciesId)
        val rawForm = formName?.trim().orEmpty()
        if (rawForm.isBlank()) return baseLabel
        val cleanedForm = rawForm
            .replace(baseLabel, "", ignoreCase = true)
            .trim()
            .trim('-', '_', ' ')
        return if (cleanedForm.isBlank() || normalizeFormToken(cleanedForm) == normalizedBase) {
            baseLabel
        } else {
            "$baseLabel-${cleanedForm.split("-", "_", " ").filter { it.isNotBlank() }.joinToString("-") { token -> token.replaceFirstChar { it.uppercase() } }}"
        }
    }

    private fun normalizeFormToken(value: String?): String {
        return value.orEmpty().lowercase()
            .replace(" ", "-")
            .replace("_", "-")
    }
}
