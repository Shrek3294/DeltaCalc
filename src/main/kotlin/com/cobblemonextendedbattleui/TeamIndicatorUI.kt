package com.cobblemonextendedbattleui

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.api.pokemon.status.Status
import com.cobblemon.mod.common.api.types.tera.TeraType
import com.cobblemon.mod.common.api.types.tera.TeraTypes
import com.cobblemon.mod.common.client.CobblemonClient
import com.cobblemon.mod.common.client.battle.ClientBattlePokemon
import com.cobblemon.mod.common.client.battle.ClientBattleSide
import com.cobblemon.mod.common.pokemon.FormData
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemonextendedbattleui.compat.delta.DeltaBattleInfoReader
import com.cobblemonextendedbattleui.compat.delta.DeltaTeamPreviewEntry
import com.cobblemonextendedbattleui.pokemon.SafeFormResolver
import com.cobblemonextendedbattleui.pokemon.render.PokemonModelRenderer
import com.cobblemonextendedbattleui.pokemon.render.TeamPanelRenderer
import com.cobblemonextendedbattleui.pokemon.tooltip.MoveInfo
import com.cobblemonextendedbattleui.pokemon.tooltip.PokeballBounds
import com.cobblemonextendedbattleui.pokemon.tooltip.TooltipBoundsData
import com.cobblemonextendedbattleui.pokemon.tooltip.TooltipConstants
import com.cobblemonextendedbattleui.pokemon.tooltip.TooltipData
import com.cobblemonextendedbattleui.pokemon.tooltip.TooltipDataBuilder
import com.cobblemonextendedbattleui.ui.shared.ResponsiveGeometry
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.util.InputUtil
import net.minecraft.util.Identifier
import org.lwjgl.glfw.GLFW
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Displays team indicators for each side's Pokemon using rendered 3D models.
 * Shows status conditions and KO'd Pokemon at a glance via color tinting.
 *
 * Supports both participating in battles and spectating:
 * - When in battle: Uses battle actor's pokemon list for authoritative HP/status data
 * - When spectating: Uses battle data to track both sides as Pokemon are revealed
 *
 * Hover tooltips show detailed info including moves, items, and abilities.
 * Falls back to pokeball rendering if model loading fails.
 *
 * Note: Uses battle data directly instead of client party storage to ensure
 * correct updates on servers where party storage may not sync during battle.
 */
object TeamIndicatorUI {

    // Match Cobblemon's exact positioning constants from BattleOverlay.kt
    internal const val HORIZONTAL_INSET = 12
    internal const val VERTICAL_INSET = 10

    // Cobblemon tile dimensions (from BattleOverlay companion object)
    internal const val TILE_HEIGHT = 40
    internal const val COMPACT_TILE_HEIGHT = 28

    // Pokemon model indicator settings (base values before scaling)
    internal const val BASE_MODEL_SIZE = 24      // Compact size for indicators
    internal const val BASE_MODEL_SPACING = 3    // Tight spacing between models
    internal const val MODEL_OFFSET_Y = 10       // Gap below the last tile (moves panel down)

    // Computed values based on current scale
    internal val modelSize: Int get() = (BASE_MODEL_SIZE * PanelConfig.teamIndicatorScale).toInt()
    internal val modelSpacing: Int get() = (BASE_MODEL_SPACING * PanelConfig.teamIndicatorScale).toInt()

    // Panel padding (used for bounds calculations; rendering delegated to TeamPanelRenderer)
    internal const val PANEL_PADDING_V = 2
    internal const val PANEL_PADDING_H = 5

    private var isMinimised: Boolean = false

    /**
     * Applies the current opacity (minimized state) to a color's alpha channel.
     */
    internal fun applyOpacity(color: Int): Int = UIUtils.applyMinimisedOpacity(color, isMinimised)

    // Track Pokemon as they're revealed in battle
    data class TrackedPokemon(
        val uuid: UUID,
        var hpPercent: Float,  // 0.0 to 1.0
        var status: Status?,
        var isKO: Boolean,
        // Display name (persists after switch-out)
        var displayName: String? = null,
        // For model rendering
        var speciesIdentifier: Identifier? = null,
        var aspects: Set<String> = emptySet(),
        // Original form tracking for Transform/Impostor (Ditto)
        var originalSpeciesIdentifier: Identifier? = null,
        var originalAspects: Set<String> = emptySet(),
        var isTransformed: Boolean = false,
        var form: FormData? = null,
        var teraType: TeraType? = null,
        var previewRevealedMoves: MutableSet<String> = linkedSetOf(),
        var previewRevealedItem: String? = null,
        var previewRevealedAbility: String? = null
    )

    // Track Pokemon for both sides separately (for spectating and opponent tracking)
    private val trackedSide1Pokemon = ConcurrentHashMap<UUID, TrackedPokemon>()
    private val trackedSide2Pokemon = ConcurrentHashMap<UUID, TrackedPokemon>()
    private val trackedSide1Order = mutableListOf<UUID>()
    private val trackedSide2Order = mutableListOf<UUID>()

    // Persistent KO tracking - Pokemon removed from activePokemon after fainting
    // still need to show as KO'd in pokeball indicators
    private val knockedOutPokemon = ConcurrentHashMap.newKeySet<UUID>()

    // Pending transforms - queued when transform message arrives before Pokemon is tracked
    // (handles Impostor ability where transform happens immediately on switch-in)
    private val pendingTransforms = ConcurrentHashMap.newKeySet<UUID>()

    // Track which Pokemon were active last frame (for detecting disappeared/KO'd Pokemon)
    // Maps: isLeftSide -> Set of UUIDs that were in activePokemon
    private val previouslyActiveUuids = ConcurrentHashMap<Boolean, MutableSet<UUID>>()

    private var lastBattleId: UUID? = null
    private var deltaPreviewCompatNote: String? = null
    private var deltaPreviewSuccessLogged = false
    private var deltaPreviewFallbackLogged = false

    data class CalcTrackedPokemonSnapshot(
        val uuid: UUID,
        val displayName: String?,
        val speciesIdentifier: Identifier?,
        val formName: String?,
        val formTypeNames: List<String>,
        val formBaseStats: List<Int>,
        val hpPercent: Float,
        val isKO: Boolean,
        val statusName: String?,
        val revealedMoves: List<String>,
        val revealedItem: String?,
        val revealedAbility: String?,
        val statStages: Map<String, Int>
    )

    private data class BattleContext(
        val battle: com.cobblemon.mod.common.client.battle.ClientBattle,
        val leftSide: ClientBattleSide,
        val rightSide: ClientBattleSide,
        val playerTeam: List<Pokemon>?,
        val playerOnLeft: Boolean,
        val playerOnRight: Boolean
    )

    // ═══════════════════════════════════════════════════════════════════════════
    // Hover Tooltip Support
    // ═══════════════════════════════════════════════════════════════════════════

    // Currently rendered pokeball bounds (refreshed each frame)
    private val pokeballBounds = mutableListOf<PokeballBounds>()

    // Currently hovered pokeball (null if none)
    private var hoveredPokeball: PokeballBounds? = null

    // Currently rendered tooltip bounds (for input handling)
    private var tooltipBounds: TooltipBoundsData? = null

    // Team panel bounds (for input handling - covers all pokeball indicators)
    private var leftTeamPanelBounds: TooltipBoundsData? = null
    private var rightTeamPanelBounds: TooltipBoundsData? = null

    // Help icon bounds (small "?" in corner of each panel)
    private var leftHelpIconBounds: TooltipBoundsData? = null
    private var rightHelpIconBounds: TooltipBoundsData? = null

    // Key state tracking for font size adjustment
    private var wasIncreaseFontKeyPressed = false
    private var wasDecreaseFontKeyPressed = false

    // ═══════════════════════════════════════════════════════════════════════════
    // Drag and Click State Tracking
    // ═══════════════════════════════════════════════════════════════════════════

    // Drag state
    private var isDragging = false
    private var draggingLeftSide = true  // Which side we're dragging
    private var dragStartMouseX = 0
    private var dragStartMouseY = 0
    private var dragStartPanelX = 0
    private var dragStartPanelY = 0
    // For Alt+drag mirrored movement - store the OTHER panel's starting position
    private var dragStartOtherPanelX = 0
    private var dragStartOtherPanelY = 0

    // Click/double-click detection
    private var lastClickTime = 0L
    private var lastClickSide: Boolean? = null  // Which side was clicked
    private const val DOUBLE_CLICK_THRESHOLD_MS = 400L

    // Mouse button state tracking
    private var wasMouseButtonDown = false

    // Tooltip color delegations for PokemonInfoPopup (actual definitions in TooltipConstants)
    internal val TOOLTIP_TEXT get() = TooltipConstants.TOOLTIP_TEXT
    internal val TOOLTIP_HEADER get() = TooltipConstants.TOOLTIP_HEADER
    internal val TOOLTIP_LABEL get() = TooltipConstants.TOOLTIP_LABEL
    internal val TOOLTIP_DIM get() = TooltipConstants.TOOLTIP_DIM
    internal val TOOLTIP_HP_HIGH get() = TooltipConstants.TOOLTIP_HP_HIGH
    internal val TOOLTIP_HP_MED get() = TooltipConstants.TOOLTIP_HP_MED
    internal val TOOLTIP_HP_LOW get() = TooltipConstants.TOOLTIP_HP_LOW
    internal val TOOLTIP_STAT_BOOST get() = TooltipConstants.TOOLTIP_STAT_BOOST
    internal val TOOLTIP_STAT_DROP get() = TooltipConstants.TOOLTIP_STAT_DROP
    internal const val TOOLTIP_BASE_LINE_HEIGHT = 10
    internal const val TOOLTIP_FONT_SCALE = 0.85f
    internal val TOOLTIP_SPEED get() = TooltipConstants.TOOLTIP_SPEED
    internal val TOOLTIP_DEFENSE get() = TooltipConstants.TOOLTIP_DEFENSE
    internal val TOOLTIP_SPECIAL_DEFENSE get() = TooltipConstants.TOOLTIP_SPECIAL_DEFENSE
    internal val TOOLTIP_ATTACK get() = TooltipConstants.TOOLTIP_ATTACK
    internal val TOOLTIP_SPECIAL_ATTACK get() = TooltipConstants.TOOLTIP_SPECIAL_ATTACK
    internal val TOOLTIP_PP get() = TooltipConstants.TOOLTIP_PP
    internal val TOOLTIP_PP_LOW get() = TooltipConstants.TOOLTIP_PP_LOW
    internal val TOOLTIP_ABILITY get() = TooltipConstants.TOOLTIP_ABILITY
    internal val TOOLTIP_ABILITY_POSSIBLE get() = TooltipConstants.TOOLTIP_ABILITY_POSSIBLE

    // Ability name formatting (used internally and by tooltip builder)
    private fun formatAbilityName(abilityId: String): String = TooltipDataBuilder.formatAbilityName(abilityId)

    // Stat/Speed calculation delegations for PokemonInfoPopup
    internal fun getStageMultiplier(stage: Int): Double = com.cobblemonextendedbattleui.pokemon.stats.StatCalculator.getStageMultiplier(stage)
    internal fun canPokemonEvolve(pokemonId: Identifier?): Boolean = com.cobblemonextendedbattleui.pokemon.stats.StatCalculator.canPokemonEvolve(pokemonId)
    internal fun getItemAttackMultiplier(itemName: String?, speciesName: String?): Double = com.cobblemonextendedbattleui.pokemon.stats.StatCalculator.getItemAttackMultiplier(itemName, speciesName)
    internal fun getItemSpecialAttackMultiplier(itemName: String?, speciesName: String?): Double = com.cobblemonextendedbattleui.pokemon.stats.StatCalculator.getItemSpecialAttackMultiplier(itemName, speciesName)
    internal fun getItemDefenseMultiplier(itemName: String?, canEvolve: Boolean): Double = com.cobblemonextendedbattleui.pokemon.stats.StatCalculator.getItemDefenseMultiplier(itemName, canEvolve)
    internal fun getItemSpecialDefenseMultiplier(itemName: String?, speciesName: String?, canEvolve: Boolean): Double = com.cobblemonextendedbattleui.pokemon.stats.StatCalculator.getItemSpecialDefenseMultiplier(itemName, speciesName, canEvolve)

    internal fun calculateEffectiveSpeed(baseSpeed: Int, speedStage: Int, abilityName: String?, status: Status?, itemName: String?, itemConsumed: Boolean): Int =
        com.cobblemonextendedbattleui.pokemon.stats.SpeedCalculator.calculateEffectiveSpeed(baseSpeed, speedStage, abilityName, status, itemName, itemConsumed)

    internal data class SpeedRangeResult(
        val minSpeed: Int,
        val maxSpeed: Int,
        val abilityNote: String? = null,
        val itemNote: String? = null
    )

    internal fun calculateOpponentSpeedRange(
        uuid: UUID,
        pokemonId: Identifier,
        level: Int,
        speedStage: Int,
        status: Status?,
        knownItem: BattleStateTracker.TrackedItem?,
        form: FormData?
    ): SpeedRangeResult? {
        val result = com.cobblemonextendedbattleui.pokemon.stats.SpeedCalculator.calculateOpponentSpeedRange(
            uuid, pokemonId, level, speedStage, status, knownItem, form, ::formatAbilityName
        ) ?: return null
        return SpeedRangeResult(result.minSpeed, result.maxSpeed, result.abilityNote, result.itemNote)
    }

    fun currentPreviewCompatNote(): String? = deltaPreviewCompatNote

    fun getActiveOpponentRevealedMoves(uuid: UUID? = null): Set<String> {
        val battle = CobblemonClient.battle ?: return emptySet()
        val playerUUID = MinecraftClient.getInstance().player?.uuid ?: return emptySet()
        val playerInSide1 = battle.side1.actors.any { it.uuid == playerUUID }
        val playerInSide2 = battle.side2.actors.any { it.uuid == playerUUID }
        if (!playerInSide1 && !playerInSide2) return emptySet()

        val leftSide = if (playerInSide1) battle.side1 else battle.side2
        val rightSide = if (leftSide == battle.side1) battle.side2 else battle.side1
        val opponentTrackedMap = if (playerInSide1) trackedSide2Pokemon else trackedSide1Pokemon
        val opponentSide = if (playerInSide1) rightSide else leftSide

        val activeOpponentUuids = opponentSide.actors
            .flatMap { actor -> actor.activePokemon.mapNotNull { it.battlePokemon?.uuid } }

        return activeOpponentUuids
            .flatMap { activeUuid ->
                if (uuid != null && activeUuid != uuid) {
                    return@flatMap emptyList()
                }
                val trackedUuid = opponentTrackedMap[activeUuid]?.uuid ?: activeUuid
                BattleStateTracker.getRevealedMoves(trackedUuid)
            }
            .toCollection(linkedSetOf())
    }

    fun getTrackedRevealedMoves(displayName: String?, speciesName: String?, formName: String? = null): Set<String> {
        val trackedUuid = findTrackedPokemonUuid(displayName, speciesName, formName) ?: return emptySet()
        return BattleStateTracker.getRevealedMoves(trackedUuid)
    }

    /**
     * Clear tracking when battle ends.
     */
    fun clear() {
        trackedSide1Pokemon.clear()
        trackedSide2Pokemon.clear()
        trackedSide1Order.clear()
        trackedSide2Order.clear()
        knockedOutPokemon.clear()
        pendingTransforms.clear()
        previouslyActiveUuids.clear()
        PokemonModelRenderer.clearFloatingStates()
        pokeballBounds.clear()
        hoveredPokeball = null
        tooltipBounds = null
        leftTeamPanelBounds = null
        rightTeamPanelBounds = null
        wasIncreaseFontKeyPressed = false
        wasDecreaseFontKeyPressed = false
        lastBattleId = null
        deltaPreviewCompatNote = null
        deltaPreviewSuccessLogged = false
        deltaPreviewFallbackLogged = false
    }

    fun getOrderedOpponentTeamForCalc(): List<CalcTrackedPokemonSnapshot> {
        val battleContext = syncBattleTracking() ?: return emptyList()
        val activeOpponentUuids = battleContext.rightSide.activeClientBattlePokemon
            .mapNotNull { it.battlePokemon?.uuid }
            .toSet()
        return orderedTrackedSnapshots(trackedSide2Pokemon, trackedSide2Order, activeOpponentUuids)
    }

    /**
     * Mark a Pokemon as KO'd by name. Called from BattleMessageInterceptor when faint messages arrive.
     * This ensures pokeballs show as KO'd even after Pokemon is removed from activePokemon.
     */
    fun markPokemonAsKO(pokemonName: String) {
        val uuid = BattleStateTracker.getPokemonUuid(pokemonName) ?: run {
            CobblemonExtendedBattleUI.LOGGER.debug("TeamIndicatorUI: Unknown Pokemon '$pokemonName' for KO marking")
            return
        }
        markPokemonAsKO(uuid)
    }

    /**
     * Mark a Pokemon as KO'd by UUID.
     */
    fun markPokemonAsKO(uuid: UUID) {
        knockedOutPokemon.add(uuid)

        // Also update tracked Pokemon maps if present
        trackedSide1Pokemon[uuid]?.isKO = true
        trackedSide2Pokemon[uuid]?.isKO = true

        CobblemonExtendedBattleUI.LOGGER.debug("TeamIndicatorUI: Marked UUID $uuid as KO'd")
    }

    /**
     * Check if a Pokemon is KO'd.
     */
    fun isPokemonKO(uuid: UUID): Boolean = knockedOutPokemon.contains(uuid) || BattleStateTracker.isKO(uuid)

    /**
     * Check for transformed Pokemon that are no longer active and reset their transform status.
     * This handles the case where a transformed Ditto switches out - the switch message only
     * contains the incoming Pokemon's name, not the outgoing one, so we detect switch-out
     * by checking if transformed Pokemon are still in the active Pokemon lists.
     */
    private fun checkForSwitchedOutTransforms(leftSide: ClientBattleSide, rightSide: ClientBattleSide) {
        // Get all currently active Pokemon UUIDs from both sides
        val activeUuids = mutableSetOf<UUID>()
        for (actor in leftSide.actors + rightSide.actors) {
            for (activePokemon in actor.activePokemon) {
                activePokemon.battlePokemon?.uuid?.let { activeUuids.add(it) }
            }
        }

        // Check all tracked Pokemon from both sides
        val allTracked = trackedSide1Pokemon.values + trackedSide2Pokemon.values
        for (tracked in allTracked) {
            if (tracked.isTransformed && tracked.uuid !in activeUuids) {
                // This Pokemon is transformed but not active - it must have switched out
                // Reset it to original form
                CobblemonExtendedBattleUI.LOGGER.debug(
                    "TeamIndicatorUI: Detected switch-out of transformed Pokemon ${tracked.displayName} (UUID: ${tracked.uuid})"
                )
                resetTransformedPokemon(tracked)
            }
        }
    }

    /**
     * Reset a transformed Pokemon back to its original state.
     * Called when we detect the Pokemon is no longer active.
     */
    private fun resetTransformedPokemon(tracked: TrackedPokemon) {
        if (!tracked.isTransformed) return

        val uuid = tracked.uuid

        // Restore original species identifier
        tracked.originalSpeciesIdentifier?.let { originalId ->
            tracked.speciesIdentifier = originalId
            // Restore form from original species
            tracked.form = PokemonSpecies.getByIdentifier(originalId)?.standardForm
            // Restore display name
            tracked.displayName = PokemonSpecies.getByIdentifier(originalId)?.name ?: tracked.displayName
        }
        // Restore original aspects
        tracked.aspects = tracked.originalAspects
        tracked.isTransformed = false

        // Clear the copied ability (it was the target's ability, not ours)
        BattleStateTracker.clearRevealedAbility(uuid)

        // Also clear dynamic types (Transform copies types)
        BattleStateTracker.clearDynamicTypes(uuid)

        // Clear transform tracking in BattleStateTracker too
        BattleStateTracker.clearTransformStatus(uuid)

        // Remove from pending transforms if queued
        pendingTransforms.remove(uuid)

        CobblemonExtendedBattleUI.LOGGER.debug(
            "TeamIndicatorUI: Reset transformed Pokemon to original: ${tracked.originalSpeciesIdentifier}"
        )
    }

    /**
     * Mark a Pokemon as transformed (Ditto via Transform/Impostor).
     * Saves the original species so it can be restored when the Pokemon faints.
     * If the Pokemon isn't tracked yet (Impostor triggers on switch-in), queues for later processing.
     */
    fun markPokemonAsTransformed(transformerName: String, targetName: String) {
        val transformerUuid = BattleStateTracker.getPokemonUuid(transformerName) ?: run {
            CobblemonExtendedBattleUI.LOGGER.debug("TeamIndicatorUI: Unknown transformer '$transformerName' for transform tracking")
            return
        }

        // Find the transformer's tracked data
        val transformer = trackedSide1Pokemon[transformerUuid] ?: trackedSide2Pokemon[transformerUuid]

        // Find the target Pokemon - search both sides by display name
        val target = findTrackedPokemonByName(targetName)

        if (transformer != null) {
            applyTransformToTracked(transformer, transformerName, target)
        } else {
            // Pokemon not tracked yet - queue for when it gets added
            // This handles Impostor ability where transform message arrives before tracking
            pendingTransforms.add(transformerUuid)
            CobblemonExtendedBattleUI.LOGGER.debug(
                "TeamIndicatorUI: Queued pending transform for $transformerName (UUID: $transformerUuid)"
            )
        }
    }

    /**
     * Find a tracked Pokemon by display name across both sides.
     * Handles owner prefixes like "Player123's Gardevoir" -> "Gardevoir".
     * Returns the first match found.
     */
    private fun findTrackedPokemonByName(displayName: String): TrackedPokemon? {
        val lowerName = displayName.lowercase()

        // Strip owner prefix if present (e.g., "Player123's Gardevoir" -> "Gardevoir")
        val strippedName = if (lowerName.contains("'s ")) {
            lowerName.substringAfter("'s ")
        } else {
            lowerName
        }

        // Search both sides - try exact match first, then stripped name
        for (tracked in trackedSide1Pokemon.values) {
            val trackedName = tracked.displayName?.lowercase()
            if (trackedName == lowerName || trackedName == strippedName) {
                return tracked
            }
        }

        for (tracked in trackedSide2Pokemon.values) {
            val trackedName = tracked.displayName?.lowercase()
            if (trackedName == lowerName || trackedName == strippedName) {
                return tracked
            }
        }

        CobblemonExtendedBattleUI.LOGGER.debug(
            "TeamIndicatorUI: findTrackedPokemonByName - could not find '$displayName' (stripped: '$strippedName')"
        )
        return null
    }

    private fun findTrackedPokemonUuid(displayName: String?, speciesName: String?, formName: String? = null): UUID? {
        val candidateNames = buildSet {
            displayName?.takeIf { it.isNotBlank() }?.let { add(normalizeLookupName(it)) }
            speciesName?.takeIf { it.isNotBlank() }?.let { add(normalizeLookupName(it)) }
            formName?.takeIf { it.isNotBlank() }?.let { add(normalizeLookupName(it)) }
            if (!speciesName.isNullOrBlank() && !formName.isNullOrBlank()) {
                add(normalizeLookupName("$speciesName-$formName"))
            }
        }
        if (candidateNames.isEmpty()) return null

        val trackedPokemon = (trackedSide1Pokemon.values + trackedSide2Pokemon.values).firstOrNull { tracked ->
            val trackedSpeciesId = tracked.speciesIdentifier
            val trackedForm = tracked.form
            val trackedNames = buildSet {
                tracked.displayName?.takeIf { it.isNotBlank() }?.let { add(normalizeLookupName(it)) }
                trackedSpeciesId?.path?.takeIf { it.isNotBlank() }?.let { add(normalizeLookupName(it)) }
                trackedForm?.name?.takeIf { it.isNotBlank() }?.let { add(normalizeLookupName(it)) }
                if (trackedSpeciesId?.path != null && trackedForm?.name != null) {
                    add(normalizeLookupName("${trackedSpeciesId.path}-${trackedForm.name}"))
                }
            }
            candidateNames.any { it in trackedNames }
        }

        return trackedPokemon?.uuid
    }

    private fun normalizeLookupName(value: String): String {
        return value.lowercase()
            .replace(" ", "")
            .replace("-", "")
            .replace("_", "")
            .replace("'", "")
            .replace(".", "")
    }

    /**
     * Apply transform status to a tracked Pokemon, copying target's species data and ability.
     * @param transformer The Pokemon being transformed (e.g., Ditto)
     * @param debugName Name for logging
     * @param target The target Pokemon whose form is being copied (null if not found)
     */
    private fun applyTransformToTracked(transformer: TrackedPokemon, debugName: String, target: TrackedPokemon?) {
        // Only save original if not already transformed (first transformation)
        if (!transformer.isTransformed) {
            // IMPORTANT: Only save original if not already set from first tracking.
            // Due to a race condition, battle data updates may have already changed speciesIdentifier
            // to the transformed form before this message arrives. The originalSpeciesIdentifier
            // was correctly set when the Pokemon was first tracked, so we preserve that value.
            if (transformer.originalSpeciesIdentifier == null) {
                transformer.originalSpeciesIdentifier = transformer.speciesIdentifier
            }
            if (transformer.originalAspects.isEmpty()) {
                transformer.originalAspects = transformer.aspects
            }
            transformer.isTransformed = true
            CobblemonExtendedBattleUI.LOGGER.debug(
                "TeamIndicatorUI: Marked $debugName as transformed, original form: ${transformer.originalSpeciesIdentifier}"
            )
        }

        // If we found the target, copy its species data to transformer
        if (target != null) {
            transformer.speciesIdentifier = target.speciesIdentifier
            transformer.aspects = target.aspects
            transformer.form = target.form
            CobblemonExtendedBattleUI.LOGGER.debug(
                "TeamIndicatorUI: $debugName copied species from target: ${target.speciesIdentifier}, " +
                "aspects: ${target.aspects}, form: ${target.form?.name}"
            )

            // Update types in BattleStateTracker based on target's species
            target.speciesIdentifier?.let { targetSpeciesId ->
                val targetPrimaryType = target.form?.primaryType?.name
                    ?: PokemonSpecies.getByIdentifier(targetSpeciesId)?.primaryType?.name
                val targetSecondaryType = target.form?.secondaryType?.name
                    ?: PokemonSpecies.getByIdentifier(targetSpeciesId)?.secondaryType?.name

                if (targetPrimaryType != null) {
                    BattleStateTracker.setTypeReplacement(debugName, targetPrimaryType, targetSecondaryType, null)
                    CobblemonExtendedBattleUI.LOGGER.debug(
                        "TeamIndicatorUI: Updated $debugName types to $targetPrimaryType/$targetSecondaryType"
                    )
                }
            }

            // Copy target's ability to transformer (Transform copies ability)
            copyTargetAbilityToTransformer(transformer.uuid, target.uuid, debugName)
        } else {
            CobblemonExtendedBattleUI.LOGGER.debug(
                "TeamIndicatorUI: Could not find target Pokemon to copy species data for $debugName"
            )
        }
    }

    /**
     * Copy the target's ability to the transformer during Transform.
     * For ally targets: get ability directly from battle Pokemon data.
     * For opponent targets: get ability from revealed abilities (if known).
     */
    private fun copyTargetAbilityToTransformer(transformerUuid: UUID, targetUuid: UUID, debugName: String) {
        val battle = CobblemonClient.battle ?: return

        CobblemonExtendedBattleUI.LOGGER.debug(
            "TeamIndicatorUI: copyTargetAbilityToTransformer - transformer=$transformerUuid, target=$targetUuid"
        )

        var targetAbility: String? = null

        // Strategy 1: Try to get target's ability from Pokemon object
        // This works when we have direct access to the Pokemon (our own Pokemon)
        val targetPokemon = getBattlePokemonByUuid(targetUuid, battle)
        if (targetPokemon != null) {
            val rawAbilityName = targetPokemon.ability.name
            targetAbility = formatAbilityName(rawAbilityName)
            CobblemonExtendedBattleUI.LOGGER.debug(
                "TeamIndicatorUI: Got target ability from Pokemon object: '$rawAbilityName' -> '$targetAbility'"
            )
        }

        // Strategy 2: Check if target is in player's own team (we have full data access)
        // actor.pokemon can be empty for opponent actors, but should be populated for our own
        if (targetAbility == null) {
            val playerUuid = MinecraftClient.getInstance().player?.uuid
            if (playerUuid != null) {
                // Find the player's actor
                val playerActor = battle.side1.actors.find { it.uuid == playerUuid }
                    ?: battle.side2.actors.find { it.uuid == playerUuid }

                if (playerActor != null) {
                    val pokemon = playerActor.pokemon.find { it.uuid == targetUuid }
                    if (pokemon != null) {
                        val rawAbilityName = pokemon.ability.name
                        targetAbility = formatAbilityName(rawAbilityName)
                        CobblemonExtendedBattleUI.LOGGER.debug(
                            "TeamIndicatorUI: Got target ability from player's team: '$rawAbilityName' -> '$targetAbility'"
                        )
                    }
                }
            }
        }

        // Strategy 3: Check revealed abilities (for opponent Pokemon whose ability was shown)
        if (targetAbility == null) {
            targetAbility = BattleStateTracker.getRevealedAbility(targetUuid)
            if (targetAbility != null) {
                CobblemonExtendedBattleUI.LOGGER.debug(
                    "TeamIndicatorUI: Got target ability from revealed abilities: $targetAbility"
                )
            }
        }

        // Strategy 4: Try to get ability from TrackedPokemon's form data
        // If we tracked the target and know its species/form, look up default ability
        if (targetAbility == null) {
            val trackedTarget = trackedSide1Pokemon[targetUuid] ?: trackedSide2Pokemon[targetUuid]
            if (trackedTarget?.form != null) {
                // Get the first ability as fallback (most Pokemon have their primary ability)
                val firstAbility = trackedTarget.form?.abilities?.firstOrNull()?.template?.name
                if (firstAbility != null) {
                    targetAbility = formatAbilityName(firstAbility)
                    CobblemonExtendedBattleUI.LOGGER.debug(
                        "TeamIndicatorUI: Got target ability from form data (fallback): $targetAbility"
                    )
                }
            }
        }

        // Set the ability on the transformer
        if (targetAbility != null) {
            BattleStateTracker.setRevealedAbility(transformerUuid, targetAbility)
            CobblemonExtendedBattleUI.LOGGER.debug(
                "TeamIndicatorUI: Copied ability '$targetAbility' to $debugName"
            )
        } else {
            CobblemonExtendedBattleUI.LOGGER.debug(
                "TeamIndicatorUI: Could not determine target ability for $debugName"
            )
        }
    }

    /**
     * Fully reset a transformed Pokemon back to its original state (on switch-out).
     * Restores: species, aspects, form, and clears copied ability.
     */
    fun clearTransformStatus(pokemonName: String) {
        CobblemonExtendedBattleUI.LOGGER.debug("TeamIndicatorUI: clearTransformStatus called for '$pokemonName'")

        // Try to find UUID via BattleStateTracker first
        var uuid = BattleStateTracker.getPokemonUuid(pokemonName)
        var tracked: TrackedPokemon? = null

        if (uuid != null) {
            tracked = trackedSide1Pokemon[uuid] ?: trackedSide2Pokemon[uuid]
        }

        // Fallback: search tracked maps directly by display name
        // This handles cases where name format doesn't match registration
        if (tracked == null) {
            CobblemonExtendedBattleUI.LOGGER.debug("TeamIndicatorUI: UUID lookup failed, trying direct name search for '$pokemonName'")
            val foundByName = findTrackedPokemonByName(pokemonName)
            if (foundByName != null) {
                tracked = foundByName
                uuid = foundByName.uuid
                CobblemonExtendedBattleUI.LOGGER.debug("TeamIndicatorUI: Found by name search: UUID=$uuid")
            }
        }

        if (uuid == null || tracked == null) {
            CobblemonExtendedBattleUI.LOGGER.debug("TeamIndicatorUI: Failed to find Pokemon '$pokemonName' for transform reset")
            return
        }

        // Remove from pending if queued
        pendingTransforms.remove(uuid)

        CobblemonExtendedBattleUI.LOGGER.debug("TeamIndicatorUI: Found UUID $uuid, isTransformed=${tracked.isTransformed}")

        if (tracked.isTransformed) {
            // Restore original species identifier
            tracked.originalSpeciesIdentifier?.let { originalId ->
                tracked.speciesIdentifier = originalId
                // Restore form from original species
                tracked.form = PokemonSpecies.getByIdentifier(originalId)?.standardForm
            }
            // Restore original aspects
            tracked.aspects = tracked.originalAspects
            // Restore original display name if we have it (for Ditto, show "Ditto" not the transformed name)
            tracked.displayName = tracked.originalSpeciesIdentifier?.let {
                PokemonSpecies.getByIdentifier(it)?.name ?: tracked.displayName
            }
            tracked.isTransformed = false

            // Clear the copied ability (it was the target's ability, not ours)
            BattleStateTracker.clearRevealedAbility(uuid)

            // Also clear dynamic types (Transform copies types)
            BattleStateTracker.clearDynamicTypes(uuid)

            CobblemonExtendedBattleUI.LOGGER.debug(
                "TeamIndicatorUI: Reset transformed $pokemonName back to ${tracked.originalSpeciesIdentifier}"
            )
        } else {
            CobblemonExtendedBattleUI.LOGGER.debug(
                "TeamIndicatorUI: $pokemonName not transformed, skipping reset"
            )
        }
    }

    private fun syncBattleTracking(): BattleContext? {
        val battle = CobblemonClient.battle ?: return null
        val mc = MinecraftClient.getInstance()
        val player = mc.player ?: return null
        val playerUUID = player.uuid

        isMinimised = battle.minimised

        if (lastBattleId != battle.battleId) {
            clear()
            lastBattleId = battle.battleId
        }

        val playerInSide1 = battle.side1.actors.any { it.uuid == playerUUID }
        val playerInSide2 = battle.side2.actors.any { it.uuid == playerUUID }
        val isSpectating = !playerInSide1 && !playerInSide2

        val leftSide = when {
            playerInSide1 -> battle.side1
            playerInSide2 -> battle.side2
            else -> battle.side2
        }
        val rightSide = if (leftSide == battle.side1) battle.side2 else battle.side1

        val leftPreview = if (isSpectating) {
            DeltaBattleInfoReader.teamPreviewForSide(
                actorUuids = leftSide.actors.map { it.uuid }.toSet(),
                activePokemonUuids = leftSide.activeClientBattlePokemon.mapNotNull { it.battlePokemon?.uuid }.toSet()
            )
        } else {
            null
        }
        val rightPreview = DeltaBattleInfoReader.teamPreviewForSide(
            actorUuids = rightSide.actors.map { it.uuid }.toSet(),
            activePokemonUuids = rightSide.activeClientBattlePokemon.mapNotNull { it.battlePokemon?.uuid }.toSet()
        )

        if (leftPreview != null && leftPreview.entries.isNotEmpty()) {
            seedTrackedPokemonFromPreview(leftPreview.entries, trackedSide1Pokemon, trackedSide1Order, isAlly = false)
        }
        if (rightPreview.entries.isNotEmpty()) {
            seedTrackedPokemonFromPreview(rightPreview.entries, trackedSide2Pokemon, trackedSide2Order, isAlly = false)
        }

        val leftSideIsPlayer = !isSpectating
        updateTrackedPokemonForSide(leftSide, trackedSide1Pokemon, trackedSide1Order, isLeftSide = true, isPlayerSide = leftSideIsPlayer)
        updateTrackedPokemonForSide(rightSide, trackedSide2Pokemon, trackedSide2Order, isLeftSide = false, isPlayerSide = false)
        checkForSwitchedOutTransforms(leftSide, rightSide)

        val previewResults = listOfNotNull(leftPreview, rightPreview)
        updateDeltaPreviewStatus(
            targetCount = if (isSpectating) 2 else 1,
            successCount = previewResults.count { it.entries.isNotEmpty() },
            failureReasons = previewResults.mapNotNull { it.failureReason }.distinct()
        )

        val playerActor = battle.side1.actors.find { it.uuid == playerUUID }
            ?: battle.side2.actors.find { it.uuid == playerUUID }

        playerActor?.pokemon?.forEach { pokemon ->
            BattleStateTracker.initializeMoves(
                pokemon.uuid,
                pokemon.moveSet.getMoves().map {
                    BattleStateTracker.TrackedMove(it.displayName.string, it.currentPp, it.maxPp)
                }
            )
        }

        return BattleContext(
            battle = battle,
            leftSide = leftSide,
            rightSide = rightSide,
            playerTeam = playerActor?.pokemon,
            playerOnLeft = playerActor != null && leftSide.actors.any { it.uuid == playerUUID },
            playerOnRight = playerActor != null && rightSide.actors.any { it.uuid == playerUUID }
        )
    }

    private fun updateDeltaPreviewStatus(targetCount: Int, successCount: Int, failureReasons: List<String>) {
        val previewComplete = targetCount > 0 && successCount == targetCount
        deltaPreviewCompatNote = if (previewComplete) null else failureReasons.firstOrNull()
            ?: "Delta full team preview unavailable: using active-only tracking"

        if (previewComplete && !deltaPreviewSuccessLogged) {
            CobblemonExtendedBattleUI.LOGGER.debug("TeamIndicatorUI: Seeded Delta team preview for $successCount side(s)")
            deltaPreviewSuccessLogged = true
            deltaPreviewFallbackLogged = false
        } else if (!previewComplete && !deltaPreviewFallbackLogged) {
            CobblemonExtendedBattleUI.LOGGER.debug("TeamIndicatorUI: $deltaPreviewCompatNote")
            deltaPreviewFallbackLogged = true
            deltaPreviewSuccessLogged = false
        }
    }

    private fun seedTrackedPokemonFromPreview(
        entries: List<DeltaTeamPreviewEntry>,
        targetMap: ConcurrentHashMap<UUID, TrackedPokemon>,
        order: MutableList<UUID>,
        isAlly: Boolean
    ) {
        entries.forEach { entry ->
            registerTrackedAliases(entry, isAlly)
            targetMap.compute(entry.uuid) { _, existing ->
                if (entry.uuid !in order) {
                    order += entry.uuid
                }

                if (existing != null) {
                    existing.displayName = existing.displayName ?: entry.displayName
                    existing.speciesIdentifier = existing.speciesIdentifier ?: entry.speciesIdentifier
                    existing.aspects = if (existing.aspects.isEmpty()) entry.aspects else existing.aspects
                    existing.originalSpeciesIdentifier = existing.originalSpeciesIdentifier ?: entry.speciesIdentifier
                    existing.originalAspects = if (existing.originalAspects.isEmpty()) entry.aspects else existing.originalAspects
                    existing.form = existing.form ?: resolvePreviewForm(entry)
                    existing.previewRevealedMoves.addAll(entry.revealedMoves)
                    existing.previewRevealedItem = existing.previewRevealedItem ?: entry.revealedItem
                    existing.previewRevealedAbility = existing.previewRevealedAbility ?: entry.revealedAbility
                    existing
                } else {
                    TrackedPokemon(
                        uuid = entry.uuid,
                        hpPercent = 1f,
                        status = null,
                        isKO = knockedOutPokemon.contains(entry.uuid),
                        displayName = entry.displayName,
                        speciesIdentifier = entry.speciesIdentifier,
                        aspects = entry.aspects,
                        originalSpeciesIdentifier = entry.speciesIdentifier,
                        originalAspects = entry.aspects,
                        isTransformed = false,
                        form = resolvePreviewForm(entry),
                        previewRevealedMoves = entry.revealedMoves.toCollection(linkedSetOf()),
                        previewRevealedItem = entry.revealedItem,
                        previewRevealedAbility = entry.revealedAbility
                    )
                }
            }
        }
    }

    private fun registerTrackedAliases(entry: DeltaTeamPreviewEntry, isAlly: Boolean) {
        BattleStateTracker.registerSpeciesId(entry.uuid, entry.speciesIdentifier)
        BattleStateTracker.registerPokemon(entry.uuid, entry.displayName, isAlly)
        BattleStateTracker.registerPokemon(entry.uuid, entry.speciesIdentifier.path, isAlly)
        entry.formName
            ?.takeIf { it.isNotBlank() }
            ?.let { formName ->
                BattleStateTracker.registerPokemon(entry.uuid, formName, isAlly)
                BattleStateTracker.registerPokemon(entry.uuid, "${entry.speciesIdentifier.path}-$formName", isAlly)
            }
    }

    private fun resolvePreviewForm(entry: DeltaTeamPreviewEntry): FormData? {
        val species = PokemonSpecies.getByIdentifier(entry.speciesIdentifier) ?: return null
        val namedForm = entry.formName?.takeIf { it.isNotBlank() }
        if (namedForm != null) {
            val aspectCandidates = LinkedHashSet<String>()
            aspectCandidates += BattleStateTracker.formNameToAspects(namedForm)
            aspectCandidates += namedForm.lowercase().replace(" ", "-")
            aspectCandidates += namedForm.lowercase().replace(" ", "-").removePrefix(entry.speciesIdentifier.path.lowercase()).trim('-')

            for (candidate in aspectCandidates) {
                if (candidate.isBlank()) continue
                val form = SafeFormResolver.safeGetForm(species, candidate, context = "TeamIndicatorUI.resolvePreviewForm.named")
                if (form != null && (form != species.standardForm || candidate == species.standardForm.aspects.firstOrNull())) {
                    return form
                }
            }

            return species.standardForm
        }
        if (entry.aspects.isNotEmpty()) {
            val aspectForm = SafeFormResolver.safeGetForm(species, entry.aspects, context = "TeamIndicatorUI.resolvePreviewForm.aspects")
            if (aspectForm != null && (aspectForm != species.standardForm || entry.aspects == species.standardForm.aspects)) {
                return aspectForm
            }
        }

        val formName = entry.formName?.takeIf { it.isNotBlank() } ?: return species.standardForm
        val aspectCandidates = LinkedHashSet<String>()
        aspectCandidates += BattleStateTracker.formNameToAspects(formName)
        aspectCandidates += formName.lowercase().replace(" ", "-")
        aspectCandidates += formName.lowercase().replace(" ", "-").removePrefix(entry.speciesIdentifier.path.lowercase()).trim('-')

        for (candidate in aspectCandidates) {
            if (candidate.isBlank()) continue
            val form = SafeFormResolver.safeGetForm(species, candidate, context = "TeamIndicatorUI.resolvePreviewForm.fallback")
            if (form != null && (form != species.standardForm || candidate == species.standardForm.aspects.firstOrNull())) {
                return form
            }
        }

        return species.standardForm
    }

    private fun orderedTrackedSnapshots(
        tracked: ConcurrentHashMap<UUID, TrackedPokemon>,
        order: List<UUID>,
        previewRevealUuids: Set<UUID>
    ): List<CalcTrackedPokemonSnapshot> {
        val result = mutableListOf<CalcTrackedPokemonSnapshot>()
        val seen = HashSet<UUID>()

        order.forEach { uuid ->
            val trackedPokemon = tracked[uuid] ?: return@forEach
            result += trackedPokemon.toCalcSnapshot(includePreviewReveals = uuid in previewRevealUuids)
            seen += uuid
        }

        tracked.entries
            .sortedBy { it.value.displayName.orEmpty() }
            .forEach { (uuid, trackedPokemon) ->
                if (seen.add(uuid)) {
                    result += trackedPokemon.toCalcSnapshot(includePreviewReveals = uuid in previewRevealUuids)
                }
            }

        return result
    }

    private fun TrackedPokemon.toCalcSnapshot(includePreviewReveals: Boolean): CalcTrackedPokemonSnapshot {
        val resolvedRevealedMoves = linkedSetOf<String>().apply {
            if (includePreviewReveals) {
                addAll(previewRevealedMoves)
            }
            addAll(BattleStateTracker.getRevealedMoves(uuid))
        }
        return CalcTrackedPokemonSnapshot(
            uuid = uuid,
            displayName = displayName,
            speciesIdentifier = speciesIdentifier,
            formName = form?.name?.takeIf { it.isNotBlank() },
            formTypeNames = listOfNotNull(form?.primaryType?.name, form?.secondaryType?.name),
            formBaseStats = listOf(
                form?.baseStats?.get(com.cobblemon.mod.common.api.pokemon.stats.Stats.HP) ?: 0,
                form?.baseStats?.get(com.cobblemon.mod.common.api.pokemon.stats.Stats.ATTACK) ?: 0,
                form?.baseStats?.get(com.cobblemon.mod.common.api.pokemon.stats.Stats.DEFENCE) ?: 0,
                form?.baseStats?.get(com.cobblemon.mod.common.api.pokemon.stats.Stats.SPECIAL_ATTACK) ?: 0,
                form?.baseStats?.get(com.cobblemon.mod.common.api.pokemon.stats.Stats.SPECIAL_DEFENCE) ?: 0,
                form?.baseStats?.get(com.cobblemon.mod.common.api.pokemon.stats.Stats.SPEED) ?: 0
            ),
            hpPercent = hpPercent,
            isKO = isKO || isPokemonKO(uuid),
            statusName = status?.name?.path,
            revealedMoves = resolvedRevealedMoves.toList(),
            revealedItem = BattleStateTracker.getItem(uuid)?.takeIf { it.status == BattleStateTracker.ItemStatus.HELD }?.name
                ?: previewRevealedItem?.takeIf { includePreviewReveals },
            revealedAbility = BattleStateTracker.getRevealedAbility(uuid)
                ?: previewRevealedAbility?.takeIf { includePreviewReveals },
            statStages = BattleStateTracker.getStatChanges(uuid).mapKeys { it.key.displayName }
        )
    }

    fun render(context: DrawContext) {
        val battleContext = syncBattleTracking() ?: return

        // Clear pokeball bounds for this frame
        pokeballBounds.clear()

        val mc = MinecraftClient.getInstance()
        val screenWidth = mc.window.scaledWidth
        val screenHeight = mc.window.scaledHeight

        // Get mouse position for hover detection
        val mouseX = (mc.mouse.x * mc.window.scaledWidth / mc.window.width).toInt()
        val mouseY = (mc.mouse.y * mc.window.scaledHeight / mc.window.height).toInt()

        val battle = battleContext.battle
        val leftSide = battleContext.leftSide
        val rightSide = battleContext.rightSide
        val playerOnLeft = battleContext.playerOnLeft
        val playerOnRight = battleContext.playerOnRight
        val playerTeam = battleContext.playerTeam

        // Count active Pokemon for positioning (determines how many tiles are shown)
        val leftActiveCount = leftSide.actors.sumOf { it.activePokemon.size }
        val rightActiveCount = rightSide.actors.sumOf { it.activePokemon.size }

        val leftY = calculateIndicatorY(leftActiveCount)
        val rightY = calculateIndicatorY(rightActiveCount)

        // Get team sizes for position calculations
        val leftTeamSize = if (playerOnLeft) playerTeam?.size ?: 0 else trackedSide1Pokemon.size
        val rightTeamSize = if (playerOnRight) playerTeam?.size ?: 0 else trackedSide2Pokemon.size

        // Calculate positions for left and right teams
        val (leftX, leftFinalY) = getTeamPosition(
            isLeftSide = true,
            teamSize = leftTeamSize,
            defaultY = leftY,
            screenWidth = screenWidth,
            screenHeight = screenHeight
        )
        val (rightX, rightFinalY) = getTeamPosition(
            isLeftSide = false,
            teamSize = rightTeamSize,
            defaultY = rightY,
            screenWidth = screenWidth,
            screenHeight = screenHeight
        )

        // Render LEFT side - player's team if they're on left, otherwise tracked
        if (playerOnLeft) {
            playerTeam?.let {
                renderBattleTeam(context, leftX, leftFinalY, it, isLeftSide = true)
            }
        } else {
            val leftTeam = trackedSide1Order.mapNotNull(trackedSide1Pokemon::get)
            if (leftTeam.isNotEmpty()) {
                renderTrackedTeam(context, leftX, leftFinalY, leftTeam, isLeftSide = true)
            }
        }

        // Render RIGHT side - player's team if they're on right, otherwise tracked
        if (playerOnRight) {
            playerTeam?.let {
                renderBattleTeam(context, rightX, rightFinalY, it, isLeftSide = false)
            }
        } else {
            val rightTeam = trackedSide2Order.mapNotNull(trackedSide2Pokemon::get)
            if (rightTeam.isNotEmpty()) {
                renderTrackedTeam(context, rightX, rightFinalY, rightTeam, isLeftSide = false)
            }
        }

        // Check if mouse is over a help icon first (takes precedence over Pokemon hover)
        val overHelpIcon = isMouseOverHelpIcon(mouseX, mouseY)

        // Detect hovered pokeball (but not if over help icon)
        hoveredPokeball = if (overHelpIcon) {
            null
        } else {
            pokeballBounds.find { bounds ->
                mouseX >= bounds.x && mouseX <= bounds.x + bounds.width &&
                    mouseY >= bounds.y && mouseY <= bounds.y + bounds.height
            }
        }

        // Handle all input (dragging, clicking, font scaling)
        handleInput(mc, mouseX, mouseY)
    }

    /**
     * Check if mouse coordinates are over any help icon.
     */
    private fun isMouseOverHelpIcon(mouseX: Int, mouseY: Int): Boolean {
        leftHelpIconBounds?.let { bounds ->
            if (mouseX >= bounds.x && mouseX <= bounds.x + bounds.width &&
                mouseY >= bounds.y && mouseY <= bounds.y + bounds.height
            ) {
                return true
            }
        }
        rightHelpIconBounds?.let { bounds ->
            if (mouseX >= bounds.x && mouseX <= bounds.x + bounds.width &&
                mouseY >= bounds.y && mouseY <= bounds.y + bounds.height
            ) {
                return true
            }
        }
        return false
    }

    /**
     * Check if mouse is over any team panel.
     */
    private fun isMouseOverTeamPanels(): Boolean {
        val mc = MinecraftClient.getInstance()
        val mouseX = (mc.mouse.x * mc.window.scaledWidth / mc.window.width).toInt()
        val mouseY = (mc.mouse.y * mc.window.scaledHeight / mc.window.height).toInt()

        leftTeamPanelBounds?.let { bounds ->
            if (mouseX >= bounds.x && mouseX <= bounds.x + bounds.width &&
                mouseY >= bounds.y && mouseY <= bounds.y + bounds.height
            ) {
                return true
            }
        }
        rightTeamPanelBounds?.let { bounds ->
            if (mouseX >= bounds.x && mouseX <= bounds.x + bounds.width &&
                mouseY >= bounds.y && mouseY <= bounds.y + bounds.height
            ) {
                return true
            }
        }
        return false
    }

    /**
     * Check if TeamIndicatorUI should have priority for font input handling.
     * Returns true if tooltip is visible OR mouse is over team panels.
     */
    fun shouldHandleFontInput(): Boolean {
        return hoveredPokeball != null || isMouseOverTeamPanels()
    }

    internal fun calculateModelPositionFromDrag(
        dragStartModelCoord: Int,
        delta: Int,
        padding: Int,
        panelSpan: Int,
        viewportSpan: Int
    ): Int {
        if (viewportSpan <= 0) return padding
        val candidatePanelCoord = dragStartModelCoord.toLong() - padding.toLong() + delta.toLong()
        val clampedPanelCoord = ResponsiveGeometry.clampCoord(
            candidate = candidatePanelCoord.coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt(),
            widgetSpan = panelSpan,
            viewportSpan = viewportSpan
        )
        return clampedPanelCoord + padding
    }

    /**
     * Handle all input: dragging, clicking, font keybinds.
     */
    private fun handleInput(mc: MinecraftClient, mouseX: Int, mouseY: Int) {
        val handle = mc.window.handle
        val isMouseDown = GLFW.glfwGetMouseButton(handle, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS
        val isShiftDown = GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS ||
            GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS
        val isAltDown = GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_ALT) == GLFW.GLFW_PRESS ||
            GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_ALT) == GLFW.GLFW_PRESS

        val repositioningEnabled = PanelConfig.teamIndicatorRepositioningEnabled

        // Handle drag in progress (only if repositioning is enabled)
        if (isDragging && repositioningEnabled) {
            if (isMouseDown) {
                // Continue dragging - allow positioning to screen edges
                val deltaX = mouseX - dragStartMouseX
                val deltaY = mouseY - dragStartMouseY

                val draggedBounds = if (draggingLeftSide) leftTeamPanelBounds else rightTeamPanelBounds
                val panelWidth = draggedBounds?.width ?: (modelSize + PANEL_PADDING_H * 2)
                val panelHeight = draggedBounds?.height ?: (modelSize + PANEL_PADDING_V * 2)

                val newX = calculateModelPositionFromDrag(dragStartPanelX, deltaX, PANEL_PADDING_H, panelWidth, mc.window.scaledWidth)
                val newY = calculateModelPositionFromDrag(dragStartPanelY, deltaY, PANEL_PADDING_V, panelHeight, mc.window.scaledHeight)

                if (draggingLeftSide) {
                    PanelConfig.setTeamIndicatorLeftPosition(newX, newY)
                } else {
                    PanelConfig.setTeamIndicatorRightPosition(newX, newY)
                }

                // Alt+drag: Also move the OTHER panel with mirrored X movement (same Y)
                // This helps users align both panels symmetrically
                if (isAltDown) {
                    val otherBounds = if (draggingLeftSide) rightTeamPanelBounds else leftTeamPanelBounds
                    val otherWidth = otherBounds?.width ?: (modelSize + PANEL_PADDING_H * 2)
                    val otherHeight = otherBounds?.height ?: (modelSize + PANEL_PADDING_V * 2)

                    val mirroredX = calculateModelPositionFromDrag(dragStartOtherPanelX, -deltaX, PANEL_PADDING_H, otherWidth, mc.window.scaledWidth)
                    val sameY = calculateModelPositionFromDrag(dragStartOtherPanelY, deltaY, PANEL_PADDING_V, otherHeight, mc.window.scaledHeight)

                    if (draggingLeftSide) {
                        PanelConfig.setTeamIndicatorRightPosition(mirroredX, sameY)
                    } else {
                        PanelConfig.setTeamIndicatorLeftPosition(mirroredX, sameY)
                    }
                }
            } else {
                // Mouse released - end drag
                isDragging = false
                PanelConfig.save()
            }
            wasMouseButtonDown = isMouseDown
            return  // Don't process other input while dragging
        } else if (isDragging && !repositioningEnabled) {
            // Repositioning was disabled mid-drag, cancel it
            isDragging = false
        }

        // Check if mouse is over either panel
        val overLeftPanel = leftTeamPanelBounds?.let { bounds ->
            mouseX >= bounds.x && mouseX <= bounds.x + bounds.width &&
                mouseY >= bounds.y && mouseY <= bounds.y + bounds.height
        } ?: false

        val overRightPanel = rightTeamPanelBounds?.let { bounds ->
            mouseX >= bounds.x && mouseX <= bounds.x + bounds.width &&
                mouseY >= bounds.y && mouseY <= bounds.y + bounds.height
        } ?: false

        val overAnyPanel = overLeftPanel || overRightPanel
        val hoveredSide = when {
            overLeftPanel -> true
            overRightPanel -> false
            else -> null
        }

        // Handle mouse button press (start drag, toggle orientation, or double-click reset)
        if (isMouseDown && !wasMouseButtonDown && overAnyPanel && hoveredSide != null) {
            val currentTime = System.currentTimeMillis()

            if (isShiftDown) {
                // Shift+Click: Toggle orientation (Alt+Shift+Click affects both panels - same orientation)
                PanelConfig.toggleTeamIndicatorOrientation()
                PanelConfig.save()
                // Note: Orientation is a global setting, so it already affects both panels
            } else {
                // Check for double-click
                val isDoubleClick = (currentTime - lastClickTime) < DOUBLE_CLICK_THRESHOLD_MS &&
                    lastClickSide == hoveredSide

                if (isDoubleClick) {
                    // Double-click: Reset position
                    if (isAltDown) {
                        // Alt+Double-click: Reset BOTH panels
                        PanelConfig.resetTeamIndicatorLeftPosition()
                        PanelConfig.resetTeamIndicatorRightPosition()
                    } else {
                        // Regular double-click: Reset only this side
                        if (hoveredSide) {
                            PanelConfig.resetTeamIndicatorLeftPosition()
                        } else {
                            PanelConfig.resetTeamIndicatorRightPosition()
                        }
                    }
                    PanelConfig.save()

                    // Reset click tracking
                    lastClickTime = 0
                    lastClickSide = null
                } else if (repositioningEnabled) {
                    // Single click - start drag (only if repositioning is enabled)
                    isDragging = true
                    draggingLeftSide = hoveredSide
                    dragStartMouseX = mouseX
                    dragStartMouseY = mouseY

                    // Get current panel position as drag start
                    val bounds = if (hoveredSide) leftTeamPanelBounds else rightTeamPanelBounds
                    dragStartPanelX = bounds?.x?.plus(PANEL_PADDING_H) ?: mouseX
                    dragStartPanelY = bounds?.y?.plus(PANEL_PADDING_V) ?: mouseY

                    // Also store the OTHER panel's position for Alt+drag mirrored movement
                    val otherBounds = if (hoveredSide) rightTeamPanelBounds else leftTeamPanelBounds
                    dragStartOtherPanelX = otherBounds?.x?.plus(PANEL_PADDING_H) ?: (mc.window.scaledWidth - mouseX)
                    dragStartOtherPanelY = otherBounds?.y?.plus(PANEL_PADDING_V) ?: mouseY

                    // Record click for double-click detection
                    lastClickTime = currentTime
                    lastClickSide = hoveredSide
                }
            }
        }

        wasMouseButtonDown = isMouseDown

        // Handle font keybinds when hovering
        if (shouldHandleFontInput()) {
            handleFontKeybinds(handle)
        }
    }

    /**
     * Update tracked Pokemon for a battle side (used for opponent and spectator views).
     * Also detects Pokemon that disappeared from activePokemon without a switch message,
     * indicating they were KO'd (e.g., by Perish Song, Memento, Explosion).
     */
    private fun updateTrackedPokemonForSide(
        side: ClientBattleSide,
        tracked: ConcurrentHashMap<UUID, TrackedPokemon>,
        order: MutableList<UUID>,
        isLeftSide: Boolean,
        isPlayerSide: Boolean = isLeftSide  // Default: left side is player's side (unless spectating)
    ) {
        val currentlyActiveUuids = mutableSetOf<UUID>()

        for (actor in side.actors) {
            for (activePokemon in actor.activePokemon) {
                val battlePokemon = activePokemon.battlePokemon ?: continue
                currentlyActiveUuids.add(battlePokemon.uuid)
                updateTrackedPokemonInMap(battlePokemon, tracked, order, isPlayerSide)
            }
        }

        // Check for Pokemon that were active last frame but aren't anymore
        val previousActive = previouslyActiveUuids[isLeftSide]
        if (previousActive != null) {
            // Key insight: if a Pokemon left AND a new Pokemon appeared on this side,
            // it was a switch (or faint + replacement). If a Pokemon left but no new
            // one appeared, it was a KO with no replacement available.
            //
            // We do NOT check switchMessageReceived here because of a race condition:
            // the battle state (activePokemon) can update before the switch message is processed.
            // The presence of a new Pokemon on the same side is sufficient evidence of a switch.
            val newPokemonAppearedOnThisSide = currentlyActiveUuids.any { it !in previousActive }

            for (uuid in previousActive) {
                if (uuid !in currentlyActiveUuids) {
                    // Pokemon left the active slot on this side
                    if (newPokemonAppearedOnThisSide) {
                        // A new Pokemon appeared on this side - the old one was replaced
                        // Don't auto-mark as KO; if it fainted, the faint message handles it
                        CobblemonExtendedBattleUI.LOGGER.debug(
                            "TeamIndicatorUI: Pokemon $uuid left active (replaced by new Pokemon on ${if (isLeftSide) "left" else "right"} side)"
                        )
                    } else if (!isPokemonKO(uuid)) {
                        // No new Pokemon appeared - this was a KO with no replacement
                        // (last Pokemon fainted, or self-KO move like Explosion/Memento)
                        val pokemon = tracked[uuid]
                        if (pokemon != null && !pokemon.isKO) {
                            CobblemonExtendedBattleUI.LOGGER.debug(
                                "TeamIndicatorUI: Pokemon $uuid disappeared with no replacement - marking as KO"
                            )
                            markPokemonAsKO(uuid)
                        }
                    }
                }
            }
        }

        // Update tracking for next frame
        previouslyActiveUuids[isLeftSide] = currentlyActiveUuids
    }

    internal fun calculateIndicatorY(activeCount: Int): Int {
        if (activeCount <= 0) return VERTICAL_INSET + TILE_HEIGHT + MODEL_OFFSET_Y

        val count = activeCount.coerceIn(0, 100)
        // Cobblemon uses compact mode when there are 3+ active Pokemon on a side
        val isCompact = count >= 3
        val tileHeight = if (isCompact) COMPACT_TILE_HEIGHT else TILE_HEIGHT

        // Visual tile stacking - empirically adjusted based on in-game testing
        // Singles/Doubles: tiles are spaced 15px apart
        // Triples+: tiles use compact mode with tighter spacing, but need more total space
        val effectiveSpacing = when {
            count >= 3 -> 30  // Triple battles need more spacing to clear all tiles
            else -> 15        // Singles and doubles
        }

        val bottomOfTiles = VERTICAL_INSET + (count - 1) * effectiveSpacing + tileHeight

        return bottomOfTiles + MODEL_OFFSET_Y
    }

    /**
     * Get the position for a team indicator panel.
     * Reconciles candidate positions against the viewport using actual panel bounds,
     * guarding against extreme saved coordinates while preserving user configuration.
     */
    internal fun getTeamPosition(
        isLeftSide: Boolean,
        teamSize: Int,
        defaultY: Int,
        screenWidth: Int,
        screenHeight: Int
    ): Pair<Int, Int> {
        val customX = if (isLeftSide) PanelConfig.teamIndicatorLeftX else PanelConfig.teamIndicatorRightX
        val customY = if (isLeftSide) PanelConfig.teamIndicatorLeftY else PanelConfig.teamIndicatorRightY

        val isVertical = PanelConfig.teamIndicatorOrientation == PanelConfig.TeamIndicatorOrientation.VERTICAL
        val (panelWidth, panelHeight) = calculatePanelDimensions(teamSize)

        val defaultX = if (isLeftSide) {
            HORIZONTAL_INSET
        } else {
            // Right side: align to right edge
            if (isVertical) {
                screenWidth - HORIZONTAL_INSET - modelSize
            } else {
                val teamWidth = teamSize * modelSize + (teamSize - 1) * modelSpacing
                screenWidth - HORIZONTAL_INSET - teamWidth
            }
        }

        val candidateX = customX ?: defaultX
        val candidateY = customY ?: defaultY

        // Reconcile coordinates against screen dimensions using panel bounds
        // panel starts at (candidateX - PANEL_PADDING_H, candidateY - PANEL_PADDING_V)
        val candidatePanelX = candidateX.toLong() - PANEL_PADDING_H.toLong()
        val candidatePanelY = candidateY.toLong() - PANEL_PADDING_V.toLong()

        val clampedPanelX = ResponsiveGeometry.clampCoord(
            candidate = candidatePanelX.coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt(),
            widgetSpan = panelWidth,
            viewportSpan = screenWidth
        )
        val clampedPanelY = ResponsiveGeometry.clampCoord(
            candidate = candidatePanelY.coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt(),
            widgetSpan = panelHeight,
            viewportSpan = screenHeight
        )

        return Pair(clampedPanelX + PANEL_PADDING_H, clampedPanelY + PANEL_PADDING_V)
    }

    private fun resolveBattlePokemonForm(
        battlePokemon: ClientBattlePokemon,
        speciesId: Identifier?,
        aspects: Set<String>
    ): FormData? {
        val liveSpecies = runCatching { battlePokemon.species }.getOrNull()
        val fallbackSpecies = speciesId?.let(PokemonSpecies::getByIdentifier)
        val species = liveSpecies ?: fallbackSpecies ?: return null
        val battleForm = SafeFormResolver.safeGetForm(species, aspects, context = "TeamIndicatorUI.resolveBattlePokemonForm.battleForm")
        if (species.resourceIdentifier.path.equals("shaymin", ignoreCase = true)) {
            return resolveShayminForm(species, battleForm)
        }
        val namedForm = battlePokemon.properties.form?.takeIf { it.isNotBlank() }
        val explicitForm = resolveExplicitFormData(speciesId, namedForm)
        if (!namedForm.isNullOrBlank()) {
            return explicitForm ?: species.standardForm
        }
        return SafeFormResolver.safeGetForm(species, aspects, context = "TeamIndicatorUI.resolveBattlePokemonForm.aspectForm") ?: explicitForm ?: species.standardForm
    }

    private fun resolveShayminForm(
        species: com.cobblemon.mod.common.pokemon.Species,
        battleForm: FormData?
    ): FormData {
        val skyForm = SafeFormResolver.safeGetForm(species, "sky", context = "TeamIndicatorUI.resolveShayminForm")
        val battleHasFlying = battleForm?.primaryType?.name == "flying" || battleForm?.secondaryType?.name == "flying"
        return if (battleHasFlying) skyForm ?: species.standardForm else species.standardForm
    }

    private fun resolveExplicitFormData(speciesId: Identifier?, formName: String?): FormData? {
        val rawSpeciesId = speciesId?.path ?: return null
        val species = PokemonSpecies.getByIdentifier(speciesId) ?: return null
        val candidates = buildList {
            formName?.takeIf { it.isNotBlank() }?.let(::add)
        }
        for (candidate in candidates) {
            val aspectCandidates = LinkedHashSet<String>()
            aspectCandidates += BattleStateTracker.formNameToAspects(candidate)
            aspectCandidates += candidate.lowercase().replace(" ", "-")
            aspectCandidates += candidate.lowercase().replace(" ", "-").removePrefix(rawSpeciesId.lowercase()).trim('-')
            for (aspect in aspectCandidates) {
                val form = SafeFormResolver.safeGetForm(species, aspect, context = "TeamIndicatorUI.resolveExplicitFormData")
                if (form != null && (form != species.standardForm || aspect == species.standardForm.aspects.firstOrNull())) {
                    return form
                }
            }
        }
        return null
    }

    /**
     * Update tracked Pokemon in the specified map.
     * Also adds to knockedOutPokemon set when HP reaches 0 for reliable KO tracking.
     * Processes pending transforms for Impostor ability (transform before tracking).
     */
    private fun updateTrackedPokemonInMap(
        battlePokemon: ClientBattlePokemon,
        targetMap: ConcurrentHashMap<UUID, TrackedPokemon>,
        order: MutableList<UUID>,
        isAlly: Boolean = true
    ) {
        val uuid = battlePokemon.uuid
        // For opponent Pokemon, hpValue is already a 0.0-1.0 percentage (isHpFlat = false)
        // For player Pokemon, hpValue is absolute and needs to be divided by maxHp (isHpFlat = true)
        val hpPercent = if (battlePokemon.isHpFlat && battlePokemon.maxHp > 0) {
            battlePokemon.hpValue / battlePokemon.maxHp
        } else {
            battlePokemon.hpValue  // Already 0.0-1.0
        }
        val isKO = battlePokemon.hpValue <= 0
        val status = battlePokemon.status

        // Get species identifier for model rendering
        // properties.species returns a String like "pikachu", convert to Identifier
        val speciesName = battlePokemon.properties.species
        val speciesId = speciesName?.let { runCatching { Identifier.of("cobblemon", it) }.getOrNull() }
        val aspects = battlePokemon.state.currentAspects
        val displayName = battlePokemon.displayName.string
        val form = resolveBattlePokemonForm(battlePokemon, speciesId, aspects)
        val teraType = battlePokemon.properties.teraType?.let { TeraTypes.get(it) };

        // If HP is 0, add to persistent KO tracking
        // This catches KO status even if faint message hasn't arrived yet
        if (isKO) {
            knockedOutPokemon.add(uuid)
        }

        // Register species ID with BattleStateTracker for form lookup
        if (speciesId != null) {
            BattleStateTracker.registerSpeciesId(uuid, speciesId)
        }

        // Register Pokemon with BattleStateTracker for name-to-UUID resolution
        // This is needed for Terastallization tracking and other message-based updates
        if (displayName.isNotEmpty()) {
            BattleStateTracker.registerPokemon(uuid, displayName, isAlly)
        }
        // Also register under species name for robust lookup (messages may use either)
        if (speciesName != null) {
            BattleStateTracker.registerPokemon(uuid, speciesName, isAlly)
            battlePokemon.properties.form
                ?.takeIf { it.isNotBlank() }
                ?.let { formName ->
                    BattleStateTracker.registerPokemon(uuid, "$speciesName-$formName", isAlly)
                }
        }
        form?.name
            ?.takeIf { it.isNotBlank() }
            ?.let { formLabel ->
                BattleStateTracker.registerPokemon(uuid, formLabel, isAlly)
            }

        // Check if this Pokemon has a pending transform (Impostor triggered before tracking)
        val hasPendingTransform = pendingTransforms.remove(uuid)

        targetMap.compute(uuid) { _, existing ->
            if (existing != null) {
                if (uuid !in order) {
                    order += uuid
                }
                if (hasPendingTransform && !existing.isTransformed) {
                    existing.originalSpeciesIdentifier = existing.speciesIdentifier ?: speciesId
                    existing.originalAspects = existing.aspects
                    existing.isTransformed = true
                }
                // Update existing - also check persistent KO set
                existing.hpPercent = hpPercent
                existing.status = status
                existing.isKO = isKO || knockedOutPokemon.contains(uuid)
                // Update display name if we have one (persist the name)
                if (displayName.isNotEmpty()) existing.displayName = displayName
                // Always update the current species (for transformed Pokemon, this shows the transformed form)
                // The original form is tracked separately in originalSpeciesIdentifier
                existing.speciesIdentifier = speciesId ?: existing.speciesIdentifier
                existing.aspects = aspects.ifEmpty { existing.aspects }
                existing.form = form
                existing.teraType = teraType
                existing
            } else {
                if (uuid !in order) {
                    order += uuid
                }
                // New Pokemon revealed
                // Check if transform was queued before we could track this Pokemon (Impostor ability)
                val isTransformed = hasPendingTransform
                // If pending transform, the current species is already transformed - original was Ditto
                val originalSpecies = if (isTransformed) DITTO_SPECIES_ID else speciesId
                val originalAspects = if (isTransformed) emptySet() else aspects

                if (isTransformed) {
                    CobblemonExtendedBattleUI.LOGGER.debug(
                        "TeamIndicatorUI: Processing pending transform for UUID $uuid - original species: ditto, current: $speciesName"
                    )
                }

                TrackedPokemon(
                    uuid = uuid,
                    hpPercent = hpPercent,
                    status = status,
                    isKO = isKO || knockedOutPokemon.contains(uuid),
                    displayName = displayName.ifEmpty { null },
                    speciesIdentifier = speciesId,
                    aspects = aspects,
                    originalSpeciesIdentifier = originalSpecies,
                    originalAspects = originalAspects,
                    isTransformed = isTransformed,
                    form = form,
                    teraType = teraType
                )
            }
        }
    }

    // Ditto species identifier for transform reversion
    private val DITTO_SPECIES_ID = Identifier.of("cobblemon", "ditto")

    // Help icon settings
    private const val HELP_ICON_SIZE = 8
    private const val HELP_ICON_MARGIN = 2

    internal fun calculatePanelDimensions(teamSize: Int): Pair<Int, Int> =
        TeamPanelRenderer.calculatePanelDimensions(teamSize, modelSize, modelSpacing)

    private fun drawTeamPanel(context: DrawContext, x: Int, y: Int, teamSize: Int) =
        TeamPanelRenderer.drawTeamPanel(context, x, y, teamSize, modelSize, modelSpacing, ::applyOpacity)

    private fun drawPanelCornerOverlays(context: DrawContext, x: Int, y: Int, teamSize: Int) =
        TeamPanelRenderer.drawPanelCornerOverlays(context, x, y, teamSize, modelSize, modelSpacing, ::applyOpacity)

    private fun drawHelpIcon(
        context: DrawContext, panelX: Int, panelY: Int, panelWidth: Int, panelHeight: Int, isLeftSide: Boolean
    ): TooltipBoundsData =
        TeamPanelRenderer.drawHelpIcon(context, panelX, panelY, panelWidth, panelHeight, isLeftSide, ::applyOpacity)

    /**
     * Render a team using battle actor's pokemon list.
     * This uses authoritative battle data which works correctly on servers.
     * Also checks persistent KO tracking as a fallback for race conditions.
     */
    private fun renderBattleTeam(
        context: DrawContext,
        startX: Int,
        startY: Int,
        team: List<Pokemon>,
        isLeftSide: Boolean
    ) {
        // Draw background panel first
        drawTeamPanel(context, startX, startY, team.size)

        // Track panel bounds for input handling
        val (panelWidth, panelHeight) = calculatePanelDimensions(team.size)
        val bounds = TooltipBoundsData(startX - PANEL_PADDING_H, startY - PANEL_PADDING_V, panelWidth, panelHeight)
        if (isLeftSide) leftTeamPanelBounds = bounds else rightTeamPanelBounds = bounds

        val isVertical = PanelConfig.teamIndicatorOrientation == PanelConfig.TeamIndicatorOrientation.VERTICAL
        var x = startX
        var y = startY

        for (pokemon in team) {
            // Use battle-authoritative data, with KO tracking as fallback
            val isKO = pokemon.currentHealth <= 0 || isPokemonKO(pokemon.uuid)
            val status = pokemon.status?.status

            drawPokemonModel(
                context = context,
                x = x,
                y = y,
                renderablePokemon = pokemon.asRenderablePokemon(),
                speciesIdentifier = null,
                aspects = pokemon.aspects,
                uuid = pokemon.uuid,
                isKO = isKO,
                status = status,
                isLeftSide = isLeftSide
            )

            // Store bounds for hover detection (this is player's own Pokemon)
            pokeballBounds.add(
                PokeballBounds(
                    x,
                    y,
                    modelSize,
                    modelSize,
                    pokemon.uuid,
                    isLeftSide,
                    isPlayerPokemon = true
                )
            )

            if (isVertical) {
                y += modelSize + modelSpacing
            } else {
                x += modelSize + modelSpacing
            }
        }

        // Draw corner overlays AFTER models to ensure rounded corners appear on top
        drawPanelCornerOverlays(context, startX, startY, team.size)

        // Draw help icon and track its bounds
        val helpBounds = drawHelpIcon(context, bounds.x, bounds.y, panelWidth, panelHeight, isLeftSide)
        if (isLeftSide) leftHelpIconBounds = helpBounds else rightHelpIconBounds = helpBounds
    }

    /**
     * Render a team using tracked battle data (used for opponent team and when spectating).
     * Uses both the tracked Pokemon's isKO field AND the persistent knockedOutPokemon set
     * to handle race conditions where Pokemon is removed from activePokemon before we render.
     */
    private fun renderTrackedTeam(
        context: DrawContext,
        startX: Int,
        startY: Int,
        team: List<TrackedPokemon>,
        isLeftSide: Boolean
    ) {
        // Draw background panel first
        drawTeamPanel(context, startX, startY, team.size)

        // Track panel bounds for input handling
        val (panelWidth, panelHeight) = calculatePanelDimensions(team.size)
        val bounds = TooltipBoundsData(startX - PANEL_PADDING_H, startY - PANEL_PADDING_V, panelWidth, panelHeight)
        if (isLeftSide) leftTeamPanelBounds = bounds else rightTeamPanelBounds = bounds

        val isVertical = PanelConfig.teamIndicatorOrientation == PanelConfig.TeamIndicatorOrientation.VERTICAL
        var x = startX
        var y = startY

        for (pokemon in team) {
            // Check both the tracked isKO flag and the persistent KO set
            val isKO = pokemon.isKO || isPokemonKO(pokemon.uuid)

            // If Pokemon is KO'd and was transformed, revert to original form
            val displaySpecies: Identifier?
            val displayAspects: Set<String>
            if (isKO && pokemon.isTransformed && pokemon.originalSpeciesIdentifier != null) {
                displaySpecies = pokemon.originalSpeciesIdentifier
                displayAspects = pokemon.originalAspects
            } else {
                displaySpecies = pokemon.speciesIdentifier
                displayAspects = pokemon.aspects
            }

            drawPokemonModel(
                context = context,
                x = x,
                y = y,
                renderablePokemon = null,
                speciesIdentifier = displaySpecies,
                aspects = displayAspects,
                uuid = pokemon.uuid,
                isKO = isKO,
                status = pokemon.status,
                isLeftSide = isLeftSide
            )

            // Store bounds for hover detection (opponent or spectated team)
            pokeballBounds.add(
                PokeballBounds(
                    x,
                    y,
                    modelSize,
                    modelSize,
                    pokemon.uuid,
                    isLeftSide,
                    isPlayerPokemon = false
                )
            )

            if (isVertical) {
                y += modelSize + modelSpacing
            } else {
                x += modelSize + modelSpacing
            }
        }

        // Draw corner overlays AFTER models to ensure rounded corners appear on top
        drawPanelCornerOverlays(context, startX, startY, team.size)

        // Draw help icon and track its bounds
        val helpBounds = drawHelpIcon(context, bounds.x, bounds.y, panelWidth, panelHeight, isLeftSide)
        if (isLeftSide) leftHelpIconBounds = helpBounds else rightHelpIconBounds = helpBounds
    }

    // Model rendering delegated to PokemonModelRenderer
    private fun drawPokemonModel(
        context: DrawContext, x: Int, y: Int,
        renderablePokemon: com.cobblemon.mod.common.pokemon.RenderablePokemon?,
        speciesIdentifier: Identifier?, aspects: Set<String>,
        uuid: UUID, isKO: Boolean, status: Status?, isLeftSide: Boolean
    ) = PokemonModelRenderer.drawPokemonModel(
        context, x, y, modelSize, renderablePokemon, speciesIdentifier, aspects,
        uuid, isKO, status, isLeftSide, ::applyOpacity, PanelConfig.teamIndicatorScale
    )

    // ═══════════════════════════════════════════════════════════════════════════
    // Tooltip Rendering (called from BattleInfoRenderer after all other UI)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Render tooltip for hovered pokeball, or control hints when hovering help icon.
     * Should be called LAST in render pipeline to appear on top.
     */
    fun renderHoverTooltip(context: DrawContext) {
        val hovered = hoveredPokeball

        if (hovered != null) {
            // Hovering over a specific Pokemon - show its tooltip
            val battle = CobblemonClient.battle ?: return
            val trackedPokemon = trackedSide1Pokemon[hovered.uuid] ?: trackedSide2Pokemon[hovered.uuid]
            val battlePokemon = getBattlePokemonByUuid(hovered.uuid, battle)
            val tooltipData = getTooltipData(hovered.uuid, trackedPokemon, battlePokemon, hovered.isPlayerPokemon)
            renderTooltip(context, hovered, tooltipData)
        } else {
            // Not hovering a Pokemon - check if hovering help icon
            val hoveredHelp = getHoveredHelpIcon()
            if (hoveredHelp != null) {
                renderControlHints(context, hoveredHelp)
            }
        }
    }

    /**
     * Get the help icon info if mouse is currently over a help icon.
     * Returns pair of (panel bounds, isLeftSide) for hint positioning.
     */
    private fun getHoveredHelpIcon(): Pair<TooltipBoundsData, Boolean>? {
        val mc = MinecraftClient.getInstance()
        val mouseX = (mc.mouse.x * mc.window.scaledWidth / mc.window.width).toInt()
        val mouseY = (mc.mouse.y * mc.window.scaledHeight / mc.window.height).toInt()

        leftHelpIconBounds?.let { bounds ->
            if (mouseX >= bounds.x && mouseX <= bounds.x + bounds.width &&
                mouseY >= bounds.y && mouseY <= bounds.y + bounds.height
            ) {
                // Return panel bounds for hint positioning
                return leftTeamPanelBounds?.let { Pair(it, true) }
            }
        }
        rightHelpIconBounds?.let { bounds ->
            if (mouseX >= bounds.x && mouseX <= bounds.x + bounds.width &&
                mouseY >= bounds.y && mouseY <= bounds.y + bounds.height
            ) {
                // Return panel bounds for hint positioning
                return rightTeamPanelBounds?.let { Pair(it, false) }
            }
        }
        return null
    }

    private fun isInDefaultState(isLeftSide: Boolean): Boolean {
        val hasDefaultOrientation = PanelConfig.teamIndicatorOrientation == PanelConfig.TeamIndicatorOrientation.HORIZONTAL
        val hasDefaultScale = PanelConfig.teamIndicatorScale == 1.0f
        val hasDefaultPosition = if (isLeftSide) {
            PanelConfig.teamIndicatorLeftX == null && PanelConfig.teamIndicatorLeftY == null
        } else {
            PanelConfig.teamIndicatorRightX == null && PanelConfig.teamIndicatorRightY == null
        }
        return hasDefaultOrientation && hasDefaultScale && hasDefaultPosition
    }

    private fun renderControlHints(context: DrawContext, panelInfo: Pair<TooltipBoundsData, Boolean>) {
        val (bounds, isLeftSide) = panelInfo
        TeamPanelRenderer.renderControlHints(
            context, bounds, isLeftSide,
            !isInDefaultState(isLeftSide),
            PanelConfig.teamIndicatorRepositioningEnabled,
            ::applyOpacity
        )
    }

    private fun getBattlePokemonByUuid(
        uuid: UUID,
        battle: com.cobblemon.mod.common.client.battle.ClientBattle
    ): Pokemon? {
        for (side in listOf(battle.side1, battle.side2)) {
            for (actor in side.actors) {
                actor.pokemon.find { it.uuid == uuid }?.let { return it }
            }
        }
        return null
    }

    // getClientBattlePokemonByUuid and getPokemonNameFromUuid moved to TooltipDataBuilder

    private fun getTooltipData(
        uuid: UUID,
        trackedPokemon: TrackedPokemon?,
        battlePokemon: Pokemon?,
        isPlayerPokemon: Boolean
    ): TooltipData {
        // Convert TrackedPokemon to snapshot for TooltipDataBuilder
        val snapshot = trackedPokemon?.let { tp ->
            TooltipDataBuilder.TrackedPokemonSnapshot(
                speciesIdentifier = tp.speciesIdentifier,
                displayName = tp.displayName,
                hpPercent = tp.hpPercent,
                status = tp.status,
                isKO = tp.isKO,
                isTransformed = tp.isTransformed,
                form = tp.form,
                teraType = tp.teraType,
                formAbilities = tp.form?.abilities?.map { ability ->
                    TooltipDataBuilder.AbilityInfo(ability.template.name, ability.template.displayName)
                } ?: emptyList()
            )
        }
        return TooltipDataBuilder.buildTooltipData(uuid, snapshot, battlePokemon, isPlayerPokemon, ::isPokemonKO)
    }

    private fun renderTooltip(context: DrawContext, bounds: PokeballBounds, data: TooltipData) {
        val mc = MinecraftClient.getInstance()
        val screenWidth = mc.window.scaledWidth
        val screenHeight = mc.window.scaledHeight

        val result = PokemonInfoPopup.render(
            context, bounds, data, screenWidth, screenHeight, isMinimised
        )

        // Store tooltip bounds for input handling
        tooltipBounds = result
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Tooltip Input Handling
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Handle font size keybinds ([ and ] keys).
     */
    private fun handleFontKeybinds(handle: Long) {
        val increaseKey =
            InputUtil.fromTranslationKey(CobblemonExtendedBattleUIClient.increaseFontKey.boundKeyTranslationKey)
        val isIncreaseDown = UIUtils.isKeyOrButtonPressed(handle, increaseKey)
        if (isIncreaseDown && !wasIncreaseFontKeyPressed) {
            PanelConfig.adjustTooltipFontScale(PanelConfig.FONT_SCALE_STEP)
            PanelConfig.save()
        }
        wasIncreaseFontKeyPressed = isIncreaseDown

        val decreaseKey =
            InputUtil.fromTranslationKey(CobblemonExtendedBattleUIClient.decreaseFontKey.boundKeyTranslationKey)
        val isDecreaseDown = UIUtils.isKeyOrButtonPressed(handle, decreaseKey)
        if (isDecreaseDown && !wasDecreaseFontKeyPressed) {
            PanelConfig.adjustTooltipFontScale(-PanelConfig.FONT_SCALE_STEP)
            PanelConfig.save()
        }
        wasDecreaseFontKeyPressed = isDecreaseDown
    }

    /**
     * Handle scroll event for scaling when Ctrl is held.
     * Ctrl+Scroll: Adjust team indicator scale (model size)
     * Ctrl+Shift+Scroll: Adjust tooltip font scale
     * Returns true if the event was consumed.
     */
    fun handleScroll(deltaY: Double): Boolean {
        if (!shouldHandleFontInput()) return false

        val mc = MinecraftClient.getInstance()
        val handle = mc.window.handle
        val isCtrlDown = GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS ||
            GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS
        val isShiftDown = GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS ||
            GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS

        if (isCtrlDown) {
            val delta = if (deltaY > 0) PanelConfig.FONT_SCALE_STEP else -PanelConfig.FONT_SCALE_STEP
            if (isShiftDown) {
                // Ctrl+Shift+Scroll: Adjust tooltip font scale
                PanelConfig.adjustTooltipFontScale(delta)
            } else {
                // Ctrl+Scroll: Adjust team indicator scale (model size)
                PanelConfig.adjustTeamIndicatorScale(delta)
            }
            PanelConfig.save()
            return true
        }
        return false
    }

    internal fun getStatusDisplayName(status: Status): String = TooltipDataBuilder.getStatusDisplayName(status)
    internal fun getStatusTextColor(status: Status): Int = TooltipDataBuilder.getStatusTextColor(status)
}
