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
import com.cobblemonextendedbattleui.pokemon.render.PokemonModelRenderer
import com.cobblemonextendedbattleui.pokemon.render.TeamPanelRenderer
import com.cobblemonextendedbattleui.pokemon.tooltip.MoveInfo
import com.cobblemonextendedbattleui.pokemon.tooltip.PokeballBounds
import com.cobblemonextendedbattleui.pokemon.tooltip.TooltipBoundsData
import com.cobblemonextendedbattleui.pokemon.tooltip.TooltipConstants
import com.cobblemonextendedbattleui.pokemon.tooltip.TooltipData
import com.cobblemonextendedbattleui.pokemon.tooltip.TooltipDataBuilder
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
    private const val HORIZONTAL_INSET = 12
    private const val VERTICAL_INSET = 10

    // Cobblemon tile dimensions (from BattleOverlay companion object)
    private const val TILE_HEIGHT = 40
    private const val COMPACT_TILE_HEIGHT = 28

    // Pokemon model indicator settings (base values before scaling)
    private const val BASE_MODEL_SIZE = 24      // Compact size for indicators
    private const val BASE_MODEL_SPACING = 3    // Tight spacing between models
    private const val MODEL_OFFSET_Y = 10       // Gap below the last tile (moves panel down)

    // Computed values based on current scale
    private val modelSize: Int get() = (BASE_MODEL_SIZE * PanelConfig.teamIndicatorScale).toInt()
    private val modelSpacing: Int get() = (BASE_MODEL_SPACING * PanelConfig.teamIndicatorScale).toInt()

    // Panel padding (used for bounds calculations; rendering delegated to TeamPanelRenderer)
    private const val PANEL_PADDING_V = 2
    private const val PANEL_PADDING_H = 5

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
        var teraType: TeraType? = null
    )

    // Track Pokemon for both sides separately (for spectating and opponent tracking)
    private val trackedSide1Pokemon = ConcurrentHashMap<UUID, TrackedPokemon>()
    private val trackedSide2Pokemon = ConcurrentHashMap<UUID, TrackedPokemon>()

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

    /**
     * Clear tracking when battle ends.
     */
    fun clear() {
        trackedSide1Pokemon.clear()
        trackedSide2Pokemon.clear()
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

    fun render(context: DrawContext) {
        val battle = CobblemonClient.battle ?: return

        // Track minimized state - render greyed out instead of hiding
        isMinimised = battle.minimised

        // Clear tracking if this is a new battle
        if (lastBattleId != battle.battleId) {
            clear()
            lastBattleId = battle.battleId
        }

        // Clear pokeball bounds for this frame
        pokeballBounds.clear()

        val mc = MinecraftClient.getInstance()
        val screenWidth = mc.window.scaledWidth
        val player = mc.player ?: return
        val playerUUID = player.uuid

        // Get mouse position for hover detection
        val mouseX = (mc.mouse.x * mc.window.scaledWidth / mc.window.width).toInt()
        val mouseY = (mc.mouse.y * mc.window.scaledHeight / mc.window.height).toInt()

        // Determine if player is in the battle and which side they're on
        val playerInSide1 = battle.side1.actors.any { it.uuid == playerUUID }
        val playerInSide2 = battle.side2.actors.any { it.uuid == playerUUID }
        val isSpectating = !playerInSide1 && !playerInSide2

        // Debug logging for spectator mode to help diagnose flip issues
        if (isSpectating && lastBattleId != battle.battleId) {
            val side1Names = battle.side1.actors.map { it.displayName.string }
            val side2Names = battle.side2.actors.map { it.displayName.string }
            CobblemonExtendedBattleUI.LOGGER.debug(
                "TeamIndicatorUI: Spectating - battle.side1: $side1Names, battle.side2: $side2Names (side2 on LEFT)"
            )
        }

        // Cobblemon's BattleOverlay swaps sides based on player presence:
        // - If player is in side1: side1 is LEFT, side2 is RIGHT
        // - If player is in side2: side2 is LEFT, side1 is RIGHT
        // - If spectating: side2 is LEFT, side1 is RIGHT
        // We must match this positioning for team preview to align with the battle tiles.
        val leftSide = when {
            playerInSide1 -> battle.side1
            playerInSide2 -> battle.side2
            else -> battle.side2  // When spectating, side2 is on LEFT
        }
        val rightSide = if (leftSide == battle.side1) battle.side2 else battle.side1

        // Update tracked Pokemon for both sides from battle data
        // When spectating, neither side is considered "player's side"
        val leftSideIsPlayer = !isSpectating  // Left side is player's side when not spectating
        updateTrackedPokemonForSide(leftSide, trackedSide1Pokemon, isLeftSide = true, isPlayerSide = leftSideIsPlayer)
        updateTrackedPokemonForSide(rightSide, trackedSide2Pokemon, isLeftSide = false, isPlayerSide = false)

        // Check for transformed Pokemon that switched out (no longer active)
        // This is needed because switch messages only contain the INCOMING Pokemon name,
        // not the outgoing one, so we detect switch-out by checking active status
        checkForSwitchedOutTransforms(leftSide, rightSide)

        // Count active Pokemon for positioning (determines how many tiles are shown)
        val leftActiveCount = leftSide.actors.sumOf { it.activePokemon.size }
        val rightActiveCount = rightSide.actors.sumOf { it.activePokemon.size }

        val leftY = calculateIndicatorY(leftActiveCount)
        val rightY = calculateIndicatorY(rightActiveCount)

        // Find the player's actor if they're in the battle
        val playerActor = battle.side1.actors.find { it.uuid == playerUUID }
            ?: battle.side2.actors.find { it.uuid == playerUUID }

        // Initialize PP tracking for all player's Pokemon (idempotent - only initializes once)
        playerActor?.pokemon?.forEach { pokemon ->
            BattleStateTracker.initializeMoves(
                pokemon.uuid,
                pokemon.moveSet.getMoves().map {
                    BattleStateTracker.TrackedMove(it.displayName.string, it.currentPp, it.maxPp)
                }
            )
        }

        // Determine if player is on the left or right side
        val playerOnLeft = playerActor != null && leftSide.actors.any { it.uuid == playerUUID }
        val playerOnRight = playerActor != null && rightSide.actors.any { it.uuid == playerUUID }

        // Get team sizes for position calculations
        val leftTeamSize = if (playerOnLeft) playerActor!!.pokemon.size else trackedSide1Pokemon.size
        val rightTeamSize = if (playerOnRight) playerActor!!.pokemon.size else trackedSide2Pokemon.size

        // Calculate positions for left and right teams
        val (leftX, leftFinalY) = getTeamPosition(
            isLeftSide = true,
            teamSize = leftTeamSize,
            defaultY = leftY,
            screenWidth = screenWidth
        )
        val (rightX, rightFinalY) = getTeamPosition(
            isLeftSide = false,
            teamSize = rightTeamSize,
            defaultY = rightY,
            screenWidth = screenWidth
        )

        // Render LEFT side - player's team if they're on left, otherwise tracked
        if (playerOnLeft) {
            // Player is on left - use battle actor's pokemon list for authoritative data
            val playerTeam = playerActor!!.pokemon
            renderBattleTeam(context, leftX, leftFinalY, playerTeam, isLeftSide = true)
        } else {
            // Left side is opponent or we're spectating - use tracked Pokemon from battle data
            val leftTeam = trackedSide1Pokemon.values.toList()
            if (leftTeam.isNotEmpty()) {
                renderTrackedTeam(context, leftX, leftFinalY, leftTeam, isLeftSide = true)
            }
        }

        // Render RIGHT side - player's team if they're on right, otherwise tracked
        if (playerOnRight) {
            // Player is on right - use battle actor's pokemon list for authoritative data
            val playerTeam = playerActor!!.pokemon
            renderBattleTeam(context, rightX, rightFinalY, playerTeam, isLeftSide = false)
        } else {
            // Right side is opponent or we're spectating - use tracked Pokemon from battle data
            val rightTeam = trackedSide2Pokemon.values.toList()
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
                val newX = (dragStartPanelX + deltaX).coerceIn(0, mc.window.scaledWidth - modelSize)
                val newY = (dragStartPanelY + deltaY).coerceIn(0, mc.window.scaledHeight - modelSize)

                if (draggingLeftSide) {
                    PanelConfig.setTeamIndicatorLeftPosition(newX, newY)
                } else {
                    PanelConfig.setTeamIndicatorRightPosition(newX, newY)
                }

                // Alt+drag: Also move the OTHER panel with mirrored X movement (same Y)
                // This helps users align both panels symmetrically
                if (isAltDown) {
                    // Mirrored X: if we move right (+deltaX), other panel moves left (-deltaX)
                    // Same Y: both panels move in the same vertical direction
                    val mirroredX = (dragStartOtherPanelX - deltaX).coerceIn(0, mc.window.scaledWidth - modelSize)
                    val sameY = (dragStartOtherPanelY + deltaY).coerceIn(0, mc.window.scaledHeight - modelSize)

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
        isLeftSide: Boolean,
        isPlayerSide: Boolean = isLeftSide  // Default: left side is player's side (unless spectating)
    ) {
        val currentlyActiveUuids = mutableSetOf<UUID>()

        for (actor in side.actors) {
            for (activePokemon in actor.activePokemon) {
                val battlePokemon = activePokemon.battlePokemon ?: continue
                currentlyActiveUuids.add(battlePokemon.uuid)
                updateTrackedPokemonInMap(battlePokemon, tracked, isPlayerSide)
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

    private fun calculateIndicatorY(activeCount: Int): Int {
        if (activeCount <= 0) return VERTICAL_INSET + TILE_HEIGHT + MODEL_OFFSET_Y

        // Cobblemon uses compact mode when there are 3+ active Pokemon on a side
        val isCompact = activeCount >= 3
        val tileHeight = if (isCompact) COMPACT_TILE_HEIGHT else TILE_HEIGHT

        // Visual tile stacking - empirically adjusted based on in-game testing
        // Singles/Doubles: tiles are spaced 15px apart
        // Triples+: tiles use compact mode with tighter spacing, but need more total space
        val effectiveSpacing = when {
            activeCount >= 3 -> 30  // Triple battles need more spacing to clear all tiles
            else -> 15              // Singles and doubles
        }

        val bottomOfTiles = VERTICAL_INSET + (activeCount - 1) * effectiveSpacing + tileHeight

        return bottomOfTiles + MODEL_OFFSET_Y
    }

    /**
     * Get the position for a team indicator panel.
     * Returns custom position if set, otherwise calculates default based on orientation.
     */
    private fun getTeamPosition(
        isLeftSide: Boolean,
        teamSize: Int,
        defaultY: Int,
        screenWidth: Int
    ): Pair<Int, Int> {
        // Check for custom position first
        val customX = if (isLeftSide) PanelConfig.teamIndicatorLeftX else PanelConfig.teamIndicatorRightX
        val customY = if (isLeftSide) PanelConfig.teamIndicatorLeftY else PanelConfig.teamIndicatorRightY

        if (customX != null && customY != null) {
            return Pair(customX, customY)
        }

        // Calculate default position based on orientation
        val isVertical = PanelConfig.teamIndicatorOrientation == PanelConfig.TeamIndicatorOrientation.VERTICAL

        val defaultX = if (isLeftSide) {
            HORIZONTAL_INSET
        } else {
            // Right side: align to right edge
            if (isVertical) {
                // For vertical, panel is narrow (single column)
                screenWidth - HORIZONTAL_INSET - modelSize
            } else {
                // For horizontal, calculate full width
                val teamWidth = teamSize * modelSize + (teamSize - 1) * modelSpacing
                screenWidth - HORIZONTAL_INSET - teamWidth
            }
        }

        return Pair(customX ?: defaultX, customY ?: defaultY)
    }

    /**
     * Update tracked Pokemon in the specified map.
     * Also adds to knockedOutPokemon set when HP reaches 0 for reliable KO tracking.
     * Processes pending transforms for Impostor ability (transform before tracking).
     */
    private fun updateTrackedPokemonInMap(
        battlePokemon: ClientBattlePokemon,
        targetMap: ConcurrentHashMap<UUID, TrackedPokemon>,
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
        val speciesId = speciesName?.let { Identifier.of("cobblemon", it) }
        val aspects = battlePokemon.state.currentAspects
        val displayName = battlePokemon.displayName.string
        val form = battlePokemon.species.getForm(aspects)
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
        }

        // Check if this Pokemon has a pending transform (Impostor triggered before tracking)
        val hasPendingTransform = pendingTransforms.remove(uuid)

        targetMap.compute(uuid) { _, existing ->
            if (existing != null) {
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
                existing
            } else {
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

    private fun calculatePanelDimensions(teamSize: Int): Pair<Int, Int> =
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
