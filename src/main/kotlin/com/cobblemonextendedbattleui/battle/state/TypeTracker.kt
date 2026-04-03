package com.cobblemonextendedbattleui.battle.state

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemonextendedbattleui.BattleStateTracker.DynamicTypeState
import com.cobblemonextendedbattleui.CobblemonExtendedBattleUI
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks dynamic type changes mid-battle (Soak, Burn Up, Trick-or-Treat,
 * Forest's Curse, form changes). Stores original types for reversion.
 */
object TypeTracker {

    private val dynamicTypes = ConcurrentHashMap<UUID, DynamicTypeState>()

    fun clear() {
        dynamicTypes.clear()
    }

    fun initializeDynamicTypes(uuid: UUID, primaryType: String?, secondaryType: String?) {
        dynamicTypes[uuid] = DynamicTypeState(
            primaryType = primaryType,
            secondaryType = secondaryType,
            hasLostPrimaryType = false,
            addedTypes = emptyList(),
            originalPrimaryType = primaryType,
            originalSecondaryType = secondaryType
        )
        CobblemonExtendedBattleUI.LOGGER.debug(
            "TypeTracker: Initialized dynamic types for $uuid - primary=$primaryType, secondary=$secondaryType"
        )
    }

    fun setTypeReplacement(pokemonName: String, newPrimaryType: String?, newSecondaryType: String?, preferAlly: Boolean?) {
        val uuid = PokemonRegistry.resolvePokemonUuid(pokemonName, preferAlly) ?: run {
            CobblemonExtendedBattleUI.LOGGER.debug("TypeTracker: Unknown Pokemon '$pokemonName' for type replacement")
            return
        }

        var effectivePrimaryType = newPrimaryType
        var effectiveSecondaryType = newSecondaryType
        if (effectivePrimaryType == null && effectiveSecondaryType == null) {
            val speciesId = FormTracker.getSpeciesId(uuid)
            val species = speciesId?.let { PokemonSpecies.getByIdentifier(it) }
            if (species != null) {
                effectivePrimaryType = species.primaryType.name
                effectiveSecondaryType = species.secondaryType?.name
                CobblemonExtendedBattleUI.LOGGER.debug(
                    "TypeTracker: Type replacement for $pokemonName - using species fallback: $effectivePrimaryType/$effectiveSecondaryType"
                )
            }
        }

        val existing = dynamicTypes[uuid]

        if (existing?.hasLostPrimaryType == true) {
            CobblemonExtendedBattleUI.LOGGER.debug(
                "TypeTracker: Ignoring type replacement for $pokemonName - hasLostPrimaryType is already true"
            )
            return
        }

        var originalPrimary = existing?.originalPrimaryType
        var originalSecondary = existing?.originalSecondaryType

        if (originalPrimary == null) {
            val speciesId = FormTracker.getSpeciesId(uuid)
            val species = speciesId?.let { PokemonSpecies.getByIdentifier(it) }
            if (species != null) {
                originalPrimary = species.standardForm.primaryType?.name
                originalSecondary = species.standardForm.secondaryType?.name
                CobblemonExtendedBattleUI.LOGGER.debug(
                    "TypeTracker: Initialized original types from base species for $pokemonName: $originalPrimary/$originalSecondary"
                )
            } else {
                originalPrimary = effectivePrimaryType
                originalSecondary = effectiveSecondaryType
            }
        }

        dynamicTypes[uuid] = DynamicTypeState(
            primaryType = effectivePrimaryType,
            secondaryType = effectiveSecondaryType,
            hasLostPrimaryType = false,
            addedTypes = emptyList(),
            originalPrimaryType = originalPrimary,
            originalSecondaryType = originalSecondary
        )
        CobblemonExtendedBattleUI.LOGGER.debug(
            "TypeTracker: Type replacement for $pokemonName - current=$effectivePrimaryType/$effectiveSecondaryType, original=$originalPrimary/$originalSecondary"
        )
    }

    fun loseType(pokemonName: String, typeName: String, preferAlly: Boolean?) {
        val uuid = PokemonRegistry.resolvePokemonUuid(pokemonName, preferAlly) ?: run {
            CobblemonExtendedBattleUI.LOGGER.debug("TypeTracker: Unknown Pokemon '$pokemonName' for type loss")
            return
        }

        var existing = dynamicTypes[uuid]

        if (existing == null) {
            val speciesId = FormTracker.getSpeciesId(uuid)
            val species = speciesId?.let { PokemonSpecies.getByIdentifier(it) }

            if (species != null) {
                val primaryType = species.primaryType.name
                val secondaryType = species.secondaryType?.name
                existing = DynamicTypeState(
                    primaryType = primaryType,
                    secondaryType = secondaryType,
                    hasLostPrimaryType = false,
                    originalPrimaryType = primaryType,
                    originalSecondaryType = secondaryType
                )
                dynamicTypes[uuid] = existing
                CobblemonExtendedBattleUI.LOGGER.debug(
                    "TypeTracker: Initialized types for $pokemonName: $primaryType/$secondaryType"
                )
            } else {
                existing = DynamicTypeState(
                    primaryType = typeName,
                    secondaryType = null,
                    hasLostPrimaryType = false,
                    originalPrimaryType = typeName,
                    originalSecondaryType = null
                )
                dynamicTypes[uuid] = existing
            }
        }

        val typeNameLower = typeName.lowercase()
        val existingPrimaryLower = existing.primaryType?.lowercase()
        val existingSecondaryLower = existing.secondaryType?.lowercase()
        val isPrimaryLost = existingPrimaryLower == typeNameLower
        val isSecondaryLost = existingSecondaryLower == typeNameLower

        if (isPrimaryLost || isSecondaryLost) {
            val newState = existing.copy(
                primaryType = if (isPrimaryLost) null else existing.primaryType,
                secondaryType = if (isSecondaryLost) null else existing.secondaryType,
                hasLostPrimaryType = isPrimaryLost || existing.hasLostPrimaryType
            )
            dynamicTypes[uuid] = newState
            CobblemonExtendedBattleUI.LOGGER.debug(
                "TypeTracker: $pokemonName lost type $typeName - hasLostPrimaryType=${newState.hasLostPrimaryType}"
            )
        } else if (existing.primaryType == null && existing.secondaryType == null && !existing.hasLostPrimaryType) {
            val newState = existing.copy(hasLostPrimaryType = true)
            dynamicTypes[uuid] = newState
            CobblemonExtendedBattleUI.LOGGER.debug(
                "TypeTracker: $pokemonName has unknown types but used type-loss move - marking hasLostPrimaryType=true"
            )
        } else {
            CobblemonExtendedBattleUI.LOGGER.debug(
                "TypeTracker: Type '$typeName' not found on $pokemonName (types: ${existing.primaryType}/${existing.secondaryType})"
            )
        }
    }

    fun addType(pokemonName: String, typeName: String, preferAlly: Boolean?) {
        val uuid = PokemonRegistry.resolvePokemonUuid(pokemonName, preferAlly) ?: run {
            CobblemonExtendedBattleUI.LOGGER.debug("TypeTracker: Unknown Pokemon '$pokemonName' for add type")
            return
        }

        var existing = dynamicTypes[uuid]
        if (existing == null) {
            val speciesId = FormTracker.getSpeciesId(uuid)
            val species = speciesId?.let { PokemonSpecies.getByIdentifier(it) }
            if (species != null) {
                val primaryType = species.primaryType.name
                val secondaryType = species.secondaryType?.name
                existing = DynamicTypeState(
                    primaryType = primaryType,
                    secondaryType = secondaryType,
                    hasLostPrimaryType = false,
                    originalPrimaryType = primaryType,
                    originalSecondaryType = secondaryType
                )
            } else {
                existing = DynamicTypeState(
                    primaryType = null,
                    secondaryType = null,
                    hasLostPrimaryType = false,
                    originalPrimaryType = null,
                    originalSecondaryType = null
                )
            }
            dynamicTypes[uuid] = existing
            CobblemonExtendedBattleUI.LOGGER.debug(
                "TypeTracker: Initialized types for $pokemonName: ${existing.primaryType}/${existing.secondaryType}"
            )
        }

        val normalizedName = typeName.replaceFirstChar { it.uppercase() }

        if (existing.primaryType?.equals(normalizedName, ignoreCase = true) == true ||
            existing.secondaryType?.equals(normalizedName, ignoreCase = true) == true ||
            existing.addedTypes.any { it.equals(normalizedName, ignoreCase = true) }) {
            return
        }

        dynamicTypes[uuid] = existing.copy(
            addedTypes = existing.addedTypes + normalizedName
        )
        CobblemonExtendedBattleUI.LOGGER.debug("TypeTracker: $pokemonName gained type $normalizedName")
    }

    fun getDynamicTypes(uuid: UUID): DynamicTypeState? = dynamicTypes[uuid]

    fun clearDynamicTypes(uuid: UUID) {
        if (dynamicTypes.remove(uuid) != null) {
            CobblemonExtendedBattleUI.LOGGER.debug("TypeTracker: Cleared dynamic types for $uuid")
        }
    }

    fun restoreOriginalTypes(pokemonName: String, preferAlly: Boolean? = null) {
        val uuid = PokemonRegistry.resolvePokemonUuid(pokemonName, preferAlly) ?: return
        restoreOriginalTypes(uuid)
    }

    fun restoreOriginalTypes(uuid: UUID) {
        val state = dynamicTypes[uuid] ?: return

        dynamicTypes[uuid] = state.copy(
            primaryType = state.originalPrimaryType,
            secondaryType = state.originalSecondaryType,
            hasLostPrimaryType = false,
            addedTypes = emptyList()
        )
        CobblemonExtendedBattleUI.LOGGER.debug("TypeTracker: Restored original types for UUID $uuid")
    }
}
