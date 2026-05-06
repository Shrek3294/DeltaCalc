package com.cobblemonextendedbattleui.tracking

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.client.CobblemonClient
import com.cobblemon.mod.common.client.battle.ClientBattlePokemon
import com.cobblemon.mod.common.client.battle.ClientBattleSide
import com.cobblemon.mod.common.pokemon.FormData
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemonextendedbattleui.BattleStateTracker
import com.cobblemonextendedbattleui.CobblemonExtendedBattleUI
import com.cobblemonextendedbattleui.TeamIndicatorUI
import com.cobblemonextendedbattleui.battle.state.FormTracker
import com.cobblemonextendedbattleui.compat.core.BattlePlatformAdapter
import com.cobblemonextendedbattleui.compat.delta.DeltaBattleInfoReader
import net.minecraft.client.MinecraftClient
import net.minecraft.util.Identifier
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
    val playerSideConditions: Map<String, Int>,
    val opponentSideConditions: Map<String, Int>,
    val playerTeam: List<TrackedPokemonTruth>,
    val opponentTeam: List<TrackedPokemonTruth>,
    val playerActiveUuid: UUID?,
    val opponentActiveUuid: UUID?,
    val selectedMoveName: String?,
    val compatNotes: List<String>
) {
    val playerActive: TrackedPokemonTruth?
        get() = playerTeam.firstOrNull { it.uuid == playerActiveUuid }

    val opponentActive: TrackedPokemonTruth?
        get() = opponentTeam.firstOrNull { it.uuid == opponentActiveUuid }
}

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
        val playerActor = playerSide.actors.find { it.uuid == playerUUID }
        val playerActivePokemon = playerSide.activeClientBattlePokemon.firstOrNull()?.battlePokemon
        val opponentActivePokemon = opponentSide.activeClientBattlePokemon.firstOrNull()?.battlePokemon
        val playerTeam = buildPlayerTeamTruth(playerSide, playerActor?.pokemon ?: playerSide.actors.flatMap { it.pokemon }, playerActivePokemon)
        val opponentTeam = buildOpponentTeamTruth(opponentSide, opponentActivePokemon)

        return TrackedBattleTruth(
            battleId = battle.battleId,
            turn = platform.currentTurn(),
            weather = BattleStateTracker.weather?.type?.displayName,
            terrain = BattleStateTracker.terrain?.type?.displayName,
            playerSideConditions = BattleStateTracker.getPlayerSideConditions()
                .mapKeys { it.key.displayName }
                .mapValues { it.value.stacks },
            opponentSideConditions = BattleStateTracker.getOpponentSideConditions()
                .mapKeys { it.key.displayName }
                .mapValues { it.value.stacks },
            playerTeam = playerTeam,
            opponentTeam = opponentTeam,
            playerActiveUuid = playerActivePokemon?.uuid,
            opponentActiveUuid = opponentActivePokemon?.uuid,
            selectedMoveName = platform.selectedMoveName(),
            compatNotes = (platform.compatNotes() + listOfNotNull(TeamIndicatorUI.currentPreviewCompatNote())).distinct()
        )
    }

    private fun buildPlayerTeamTruth(
        side: ClientBattleSide,
        party: List<Pokemon>,
        activePokemon: ClientBattlePokemon?
    ): List<TrackedPokemonTruth> {
        val truths = LinkedHashMap<UUID, TrackedPokemonTruth>()
        party.forEach { pokemon ->
            truths[pokemon.uuid] = pokemon.toTruth(side, isPlayerSide = true)
        }
        activePokemon?.let { pokemon ->
            truths.putIfAbsent(pokemon.uuid, pokemon.toTruth(side, isPlayerSide = true))
        }
        return truths.values.toList()
    }

    private fun buildOpponentTeamTruth(
        side: ClientBattleSide,
        activePokemon: ClientBattlePokemon?
    ): List<TrackedPokemonTruth> {
        val truths = LinkedHashMap<UUID, TrackedPokemonTruth>()
        TeamIndicatorUI.getOrderedOpponentTeamForCalc().forEach { tracked ->
            truths[tracked.uuid] = tracked.toTruth()
        }
        activePokemon?.let { pokemon ->
            truths[pokemon.uuid] = pokemon.toTruth(side, isPlayerSide = false)
        }
        return truths.values.toList()
    }

    private fun ClientBattlePokemon.toTruth(side: ClientBattleSide, isPlayerSide: Boolean): TrackedPokemonTruth {
        val actorPokemon = side.actors
            .flatMap { it.pokemon }
            .firstOrNull { it.uuid == uuid }
        val deltaRevealData = if (isPlayerSide) null else DeltaBattleInfoReader.activeOpponentRevealData(uuid)

        val trackedSpeciesId = BattleStateTracker.getSpeciesId(uuid)?.path
        val propertiesSpecies = properties.species
        // properties.species can carry a regional aspect (e.g. "moltres-galar") for entities
        // that were spawned as the regional variant. When the tracker hasn't confirmed a
        // species yet, split that aspect off so a Kantonian Moltres doesn't get classified
        // as Galarian. canonicalSpeciesKey re-attaches the suffix once the form resolves.
        val (propertiesBase, propertiesRegional) = if (trackedSpeciesId == null) {
            splitRegionalForm(propertiesSpecies)
        } else {
            propertiesSpecies to null
        }
        val baseSpeciesId = trackedSpeciesId ?: propertiesBase
        val trackedFormName = BattleStateTracker.getCurrentForm(uuid)?.currentForm
        val propertyFormName = properties.form?.takeIf { it.isNotBlank() }
        // For Ogerpon, the held mask determines the form even though there's no form-change message.
        val heldItemForOgerpon = if (isPlayerSide) {
            actorPokemon?.heldItem()?.takeIf { !it.isEmpty }?.name?.string
        } else {
            BattleStateTracker.getItem(uuid)?.name ?: deltaRevealData?.heldItem
        }
        val maskFormName = inferOgerponMaskFormName(baseSpeciesId, heldItemForOgerpon, trackedFormName ?: propertyFormName)
        // Form-name precedence: tracker-confirmed > Ogerpon mask > properties.form > regional
        // aspect stripped from properties.species. The regional aspect is the lowest priority
        // because anything else is a stronger signal of the actual form.
        val resolvedFormName = trackedFormName ?: maskFormName ?: propertyFormName ?: propertiesRegional
        // If FormTracker has a recent form change (Aegislash Stance Change, Mega Evolution,
        // Ogerpon mask), the actor's `form` reference often still points at the original form.
        // Resolve the form via aspect lookup using the tracked form name so baseStats / types
        // reflect the post-change form.
        val actorForm = resolveCurrentForm(
            speciesId = baseSpeciesId,
            trackedFormName = trackedFormName ?: maskFormName,
            fallbackForm = actorPokemon?.form
        )
        val resolvedRevealedMoves = resolveRevealedMoves(
            uuid = uuid,
            displayName = displayName.string,
            speciesName = properties.species,
            formName = resolvedFormName,
            isPlayerSide = isPlayerSide,
            deltaRevealMoves = deltaRevealData?.moves.orEmpty()
        )
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

    private fun Pokemon.toTruth(side: ClientBattleSide, isPlayerSide: Boolean): TrackedPokemonTruth {
        val trackedSpeciesId = BattleStateTracker.getSpeciesId(uuid)?.path
        val baseSpeciesId = trackedSpeciesId ?: species.resourceIdentifier.path
        val trackedFormName = BattleStateTracker.getCurrentForm(uuid)?.currentForm
        val heldItem = heldItem()
        val maskFormName = inferOgerponMaskFormName(
            baseSpeciesId,
            if (!heldItem.isEmpty) heldItem.name.string else null,
            trackedFormName ?: form.name.takeIf { it.isNotBlank() }
        )
        val resolvedFormName = trackedFormName ?: maskFormName ?: form.name.takeIf { it.isNotBlank() }
        // Same form-override path as ClientBattlePokemon.toTruth: resolve via FormTracker
        // first so Stance Change / Mega / Ogerpon mask reflect in baseStats and types.
        val effectiveForm = resolveCurrentForm(
            speciesId = baseSpeciesId,
            trackedFormName = trackedFormName ?: maskFormName,
            fallbackForm = form
        ) ?: form

        return TrackedPokemonTruth(
            uuid = uuid,
            displayName = getDisplayName().string,
            speciesId = baseSpeciesId,
            speciesKey = canonicalSpeciesKey(baseSpeciesId, resolvedFormName),
            speciesLabel = canonicalSpeciesLabel(baseSpeciesId, getDisplayName().string, resolvedFormName),
            formName = resolvedFormName,
            formTypeNames = listOfNotNull(effectiveForm.primaryType?.name, effectiveForm.secondaryType?.name),
            formBaseStats = effectiveForm.baseStats.let { stats ->
                TrackedBaseStats(
                    hp = stats[com.cobblemon.mod.common.api.pokemon.stats.Stats.HP] ?: 0,
                    atk = stats[com.cobblemon.mod.common.api.pokemon.stats.Stats.ATTACK] ?: 0,
                    def = stats[com.cobblemon.mod.common.api.pokemon.stats.Stats.DEFENCE] ?: 0,
                    spa = stats[com.cobblemon.mod.common.api.pokemon.stats.Stats.SPECIAL_ATTACK] ?: 0,
                    spd = stats[com.cobblemon.mod.common.api.pokemon.stats.Stats.SPECIAL_DEFENCE] ?: 0,
                    spe = stats[com.cobblemon.mod.common.api.pokemon.stats.Stats.SPEED] ?: 0
                )
            },
            currentHp = currentHealth,
            maxHp = maxHealth,
            status = status?.status?.name?.path,
            revealedMoves = resolveRevealedMoves(
                uuid = uuid,
                displayName = getDisplayName().string,
                speciesName = species.resourceIdentifier.path,
                formName = resolvedFormName,
                isPlayerSide = isPlayerSide,
                deltaRevealMoves = emptyList()
            ),
            revealedItem = if (!heldItem.isEmpty) heldItem.name.string else BattleStateTracker.getItem(uuid)?.name,
            revealedAbility = ability.name,
            statStages = BattleStateTracker.getStatChanges(uuid).mapKeys { it.key.displayName },
            moveList = moveSet.getMoves().map { it.displayName.string }
        )
    }

    private fun TeamIndicatorUI.CalcTrackedPokemonSnapshot.toTruth(): TrackedPokemonTruth {
        val speciesId = speciesIdentifier?.path
        val display = displayName ?: speciesId ?: "Unknown"
        val currentHpPercent = if (isKO) 0 else (hpPercent * 100f).roundToInt().coerceIn(0, 100)
        val baseStats = formBaseStats.takeIf { it.any { value -> value > 0 } }?.let {
            TrackedBaseStats(
                hp = it[0],
                atk = it[1],
                def = it[2],
                spa = it[3],
                spd = it[4],
                spe = it[5]
            )
        }

        return TrackedPokemonTruth(
            uuid = uuid,
            displayName = display,
            speciesId = speciesId,
            speciesKey = canonicalSpeciesKey(speciesId, formName),
            speciesLabel = canonicalSpeciesLabel(speciesId, display, formName),
            formName = formName,
            formTypeNames = formTypeNames,
            formBaseStats = baseStats,
            currentHp = currentHpPercent,
            maxHp = 100,
            status = statusName,
            revealedMoves = revealedMoves,
            revealedItem = revealedItem,
            revealedAbility = revealedAbility,
            statStages = statStages,
            moveList = emptyList()
        )
    }

    /**
     * Resolve the FormData for a species + tracked form name (Aegislash Blade, Mega, Ogerpon mask, etc.).
     *
     * Cobblemon's `actorPokemon.form` reference often does NOT update mid-battle for client-side
     * form swaps (Stance Change, Mega Evolution), so trusting it gives stale baseStats / types.
     * Instead, when FormTracker has captured a form-change message we look up the matching form
     * via `species.getForm(setOf(aspect))` using the same aspect candidates as `FormTracker`.
     *
     * Returns null when no form change is tracked, when the species is unknown, or when the
     * aspect lookup doesn't resolve to a non-standard form. Callers fall back to the live
     * actor form in that case.
     */
    private fun resolveCurrentForm(
        speciesId: String?,
        trackedFormName: String?,
        fallbackForm: FormData?
    ): FormData? {
        if (trackedFormName.isNullOrBlank() || speciesId.isNullOrBlank()) return fallbackForm
        val identifier = Identifier.tryParse(speciesId) ?: Identifier.of("cobblemon", speciesId)
        val species = PokemonSpecies.getByIdentifier(identifier) ?: return fallbackForm
        val standard = species.standardForm

        for (aspect in FormTracker.formNameToAspects(trackedFormName)) {
            val candidate = runCatching { species.getForm(setOf(aspect)) }.getOrNull() ?: continue
            // A successful form lookup either returns a non-standard form, OR the standard form
            // when the tracked aspect IS the standard one. Only override when we genuinely
            // resolved to a different form than the fallback.
            if (candidate != standard) {
                CobblemonExtendedBattleUI.LOGGER.debug(
                    "BattleStateFacade: resolved form '{}' for {} via aspect '{}' (atk={}, def={})",
                    trackedFormName, speciesId, aspect,
                    candidate.baseStats[com.cobblemon.mod.common.api.pokemon.stats.Stats.ATTACK],
                    candidate.baseStats[com.cobblemon.mod.common.api.pokemon.stats.Stats.DEFENCE]
                )
                return candidate
            }
        }

        // Tried all aspect candidates — nothing matched a non-standard form. Keep fallback.
        return fallbackForm
    }

    /**
     * Ogerpon's mask determines its form (Teal / Hearthflame / Wellspring / Cornerstone),
     * which changes both base stats and the type of Ivy Cudgel. There's no form-change
     * message — the form is fixed by which mask Ogerpon holds at battle start.
     *
     * If the tracked form name is empty but the held item is a mask, return the matching
     * form name so `resolveCurrentForm` can pick the right form aspect.
     */
    private fun inferOgerponMaskFormName(speciesId: String?, heldItemName: String?, existingFormName: String?): String? {
        if (existingFormName?.isNotBlank() == true) return existingFormName
        if (speciesId == null || !speciesId.lowercase().contains("ogerpon")) return existingFormName
        val item = heldItemName?.lowercase().orEmpty()
        return when {
            "hearthflame" in item -> "Hearthflame"
            "wellspring" in item -> "Wellspring"
            "cornerstone" in item -> "Cornerstone"
            "teal" in item -> "Teal"
            else -> existingFormName
        }
    }

    private fun resolveRevealedMoves(
        uuid: UUID,
        displayName: String,
        speciesName: String?,
        formName: String?,
        isPlayerSide: Boolean,
        deltaRevealMoves: List<String>
    ): List<String> {
        return buildSet {
            addAll(deltaRevealMoves)
            addAll(BattleStateTracker.getRevealedMoves(uuid))
            if (isPlayerSide) {
                addAll(BattleStateTracker.getRevealedMovesByName(displayName, true))
                speciesName?.takeIf { it.isNotBlank() }?.let { addAll(BattleStateTracker.getRevealedMovesByName(it, true)) }
                formName?.takeIf { it.isNotBlank() }?.let { resolvedFormName ->
                    speciesName?.takeIf { it.isNotBlank() }?.let { addAll(BattleStateTracker.getRevealedMovesByName("$it-$resolvedFormName", true)) }
                    addAll(BattleStateTracker.getRevealedMovesByName(resolvedFormName, true))
                }
            } else {
                // Opponent calc state must stay UUID-scoped; name/species fallback can smear the lead's moves onto later switch-ins.
                addAll(TeamIndicatorUI.getActiveOpponentRevealedMoves(uuid))
            }
        }.toList()
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

    private val REGIONAL_ASPECTS = listOf("galar", "alola", "hisui", "paldea")

    private fun splitRegionalForm(species: String?): Pair<String?, String?> {
        if (species == null) return null to null
        if (species.startsWith("delta:", ignoreCase = true)) return species to null
        for (aspect in REGIONAL_ASPECTS) {
            val suffix = "-$aspect"
            if (species.endsWith(suffix, ignoreCase = true)) {
                return species.dropLast(suffix.length) to aspect
            }
        }
        return species to null
    }
}
