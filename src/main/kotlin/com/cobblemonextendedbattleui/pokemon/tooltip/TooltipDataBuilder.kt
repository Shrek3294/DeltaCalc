package com.cobblemonextendedbattleui.pokemon.tooltip

import com.cobblemon.mod.common.api.moves.Moves
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.api.pokemon.status.Status
import com.cobblemon.mod.common.api.pokemon.status.Statuses
import com.cobblemon.mod.common.api.types.ElementalTypes
import com.cobblemon.mod.common.api.types.tera.TeraTypes
import com.cobblemon.mod.common.client.CobblemonClient
import com.cobblemon.mod.common.client.battle.ClientBattlePokemon
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemonextendedbattleui.BattleStateTracker
import com.cobblemonextendedbattleui.CobblemonExtendedBattleUI
import net.minecraft.client.MinecraftClient
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import java.util.UUID

/**
 * Builds TooltipData by aggregating information from battle state,
 * tracked Pokemon, and battle Pokemon data.
 */
object TooltipDataBuilder {

    // Ability ID -> Display Name lookup for compound words that can't be split programmatically
    private val ABILITY_DISPLAY_NAMES = mapOf(
        "earlybird" to "Early Bird",
        "flashfire" to "Flash Fire",
        "swiftswim" to "Swift Swim",
        "chlorophyll" to "Chlorophyll",
        "sandveil" to "Sand Veil",
        "sandrush" to "Sand Rush",
        "sandstream" to "Sand Stream",
        "sandforce" to "Sand Force",
        "slushrush" to "Slush Rush",
        "snowcloak" to "Snow Cloak",
        "snowwarning" to "Snow Warning",
        "icebody" to "Ice Body",
        "iceface" to "Ice Face",
        "icescales" to "Ice Scales",
        "dryskin" to "Dry Skin",
        "roughskin" to "Rough Skin",
        "ironfist" to "Iron Fist",
        "ironbarbs" to "Iron Barbs",
        "moldbreaker" to "Mold Breaker",
        "toughclaws" to "Tough Claws",
        "strongjaw" to "Strong Jaw",
        "hugepower" to "Huge Power",
        "purepower" to "Pure Power",
        "hustle" to "Hustle",
        "guts" to "Guts",
        "sheerforce" to "Sheer Force",
        "technician" to "Technician",
        "skilllink" to "Skill Link",
        "serenegrace" to "Serene Grace",
        "superluck" to "Super Luck",
        "sniper" to "Sniper",
        "infiltrator" to "Infiltrator",
        "scrappy" to "Scrappy",
        "noguard" to "No Guard",
        "compoundeyes" to "Compound Eyes",
        "keeneye" to "Keen Eye",
        "wonderguard" to "Wonder Guard",
        "multiscale" to "Multiscale",
        "solidrock" to "Solid Rock",
        "filter" to "Filter",
        "thickfat" to "Thick Fat",
        "furcoat" to "Fur Coat",
        "fluffy" to "Fluffy",
        "battlearmor" to "Battle Armor",
        "shellarmor" to "Shell Armor",
        "magicguard" to "Magic Guard",
        "magicbounce" to "Magic Bounce",
        "naturalcure" to "Natural Cure",
        "regenerator" to "Regenerator",
        "poisonheal" to "Poison Heal",
        "immunity" to "Immunity",
        "limber" to "Limber",
        "owntempo" to "Own Tempo",
        "innerfocus" to "Inner Focus",
        "steadfast" to "Steadfast",
        "contrary" to "Contrary",
        "defiant" to "Defiant",
        "competitive" to "Competitive",
        "speedboost" to "Speed Boost",
        "moxie" to "Moxie",
        "beastboost" to "Beast Boost",
        "intimidate" to "Intimidate",
        "pressure" to "Pressure",
        "unnerve" to "Unnerve",
        "unaware" to "Unaware",
        "oblivious" to "Oblivious",
        "trace" to "Trace",
        "imposter" to "Imposter",
        "prankster" to "Prankster",
        "protean" to "Protean",
        "libero" to "Libero",
        "levitate" to "Levitate",
        "sturdy" to "Sturdy",
        "anticipation" to "Anticipation",
        "forewarn" to "Forewarn",
        "frisk" to "Frisk",
        "pickup" to "Pickup",
        "harvest" to "Harvest",
        "runaway" to "Run Away",
        "quickfeet" to "Quick Feet",
        "unburden" to "Unburden",
        "shadowtag" to "Shadow Tag",
        "arenatrap" to "Arena Trap",
        "magnetpull" to "Magnet Pull",
        "stickyhold" to "Sticky Hold",
        "suctioncups" to "Suction Cups",
        "waterveil" to "Water Veil",
        "waterabsorb" to "Water Absorb",
        "waterbubble" to "Water Bubble",
        "raindish" to "Rain Dish",
        "drizzle" to "Drizzle",
        "drought" to "Drought",
        "solarpower" to "Solar Power"
    )

    /**
     * Format an ability ID into a proper display name.
     * Uses lookup table for compound words, falls back to formatting for unknown abilities.
     */
    fun formatAbilityName(abilityId: String): String {
        ABILITY_DISPLAY_NAMES[abilityId.lowercase()]?.let { return it }

        return abilityId
            .replace("_", " ")
            .split(Regex("(?=[A-Z])|\\s+"))
            .filter { it.isNotBlank() }
            .joinToString(" ") { word ->
                when (word.lowercase()) {
                    "hp" -> "HP"
                    "pp" -> "PP"
                    else -> word.replaceFirstChar { it.uppercase() }
                }
            }
    }

    fun getStatusDisplayName(status: Status): String {
        return when (status) {
            Statuses.POISON -> Text.translatable("cobblemonextendedbattleui.status.poisoned").string
            Statuses.POISON_BADLY -> Text.translatable("cobblemonextendedbattleui.status.badly_poisoned").string
            Statuses.BURN -> Text.translatable("cobblemonextendedbattleui.status.burned").string
            Statuses.PARALYSIS -> Text.translatable("cobblemonextendedbattleui.status.paralyzed").string
            Statuses.FROZEN -> Text.translatable("cobblemonextendedbattleui.status.frozen").string
            Statuses.SLEEP -> Text.translatable("cobblemonextendedbattleui.status.asleep").string
            else -> status.name.path.replaceFirstChar { it.uppercase() }
        }
    }

    fun getStatusTextColor(status: Status): Int {
        return when (status) {
            Statuses.POISON, Statuses.POISON_BADLY -> color(160, 90, 200)
            Statuses.BURN -> color(255, 140, 50)
            Statuses.PARALYSIS -> color(255, 220, 50)
            Statuses.FROZEN -> color(100, 200, 255)
            Statuses.SLEEP -> color(150, 150, 170)
            else -> TooltipConstants.TOOLTIP_TEXT
        }
    }

    /**
     * Build tooltip data for a Pokemon.
     * @param uuid The Pokemon's UUID
     * @param trackedPokemon Tracked data (nullable for player's own Pokemon)
     * @param battlePokemon Battle Pokemon data (nullable for opponent)
     * @param isPlayerPokemon Whether this is the player's own Pokemon
     * @param isPokemonKO Function to check if a Pokemon is KO'd
     */
    fun buildTooltipData(
        uuid: UUID,
        trackedPokemon: TrackedPokemonSnapshot?,
        battlePokemon: Pokemon?,
        isPlayerPokemon: Boolean,
        isPokemonKO: (UUID) -> Boolean
    ): TooltipData {
        val pokemonId = trackedPokemon?.speciesIdentifier
            ?: battlePokemon?.species?.resourceIdentifier
        val name = battlePokemon?.getDisplayName()?.string
            ?: getPokemonNameFromUuid(uuid)
            ?: trackedPokemon?.displayName
            ?: "Unknown"

        val hpPercent = trackedPokemon?.hpPercent
            ?: battlePokemon?.let {
                if (it.maxHealth > 0) it.currentHealth.toFloat() / it.maxHealth else 0f
            }
            ?: 0f

        val clientBattlePokemon = getClientBattlePokemonByUuid(uuid)

        val level: Int? = clientBattlePokemon?.properties?.level
            ?: battlePokemon?.level

        val speciesName: String? = trackedPokemon?.speciesIdentifier?.path
            ?: clientBattlePokemon?.properties?.species
            ?: battlePokemon?.species?.name

        val moves: List<MoveInfo>
        val item: BattleStateTracker.TrackedItem?
        val actualSpeed: Int?
        val actualDefence: Int?
        val actualSpecialDefence: Int?
        val actualAttack: Int?
        val actualSpecialAttack: Int?
        val abilityName: String?
        val possibleAbilities: List<String>?

        if (isPlayerPokemon && battlePokemon != null) {
            val trackedMoves = BattleStateTracker.getTrackedMoves(uuid)
            moves = if (trackedMoves != null) {
                trackedMoves.map { MoveInfo(it.name, currentPp = it.currentPp, maxPp = it.maxPp) }
            } else {
                battlePokemon.moveSet.getMoves().map {
                    MoveInfo(it.displayName.string, currentPp = it.currentPp, maxPp = it.maxPp)
                }
            }

            val heldItem = battlePokemon.heldItem()
            val trackedItem = BattleStateTracker.getItem(uuid)
            item = if (trackedItem != null) {
                if (trackedItem.status != BattleStateTracker.ItemStatus.HELD) {
                    trackedItem
                } else if (!heldItem.isEmpty && heldItem.name.string != trackedItem.name) {
                    trackedItem
                } else if (!heldItem.isEmpty) {
                    BattleStateTracker.TrackedItem(
                        heldItem.name.string,
                        BattleStateTracker.ItemStatus.HELD,
                        BattleStateTracker.currentTurn
                    )
                } else {
                    trackedItem
                }
            } else if (!heldItem.isEmpty) {
                BattleStateTracker.TrackedItem(
                    heldItem.name.string,
                    BattleStateTracker.ItemStatus.HELD,
                    BattleStateTracker.currentTurn
                )
            } else {
                null
            }

            actualSpeed = battlePokemon.speed
            actualDefence = battlePokemon.defence
            actualSpecialDefence = battlePokemon.specialDefence
            actualAttack = battlePokemon.attack
            actualSpecialAttack = battlePokemon.specialAttack

            val isTransformed = trackedPokemon?.isTransformed == true || BattleStateTracker.isTransformed(uuid)
            val copiedAbility = if (isTransformed) BattleStateTracker.getRevealedAbility(uuid) else null

            if (copiedAbility != null) {
                abilityName = copiedAbility
            } else {
                val rawAbilityName = battlePokemon.ability.name
                val translatedAbility = Text.translatable("cobblemon.ability.$rawAbilityName").string
                abilityName = if (translatedAbility.startsWith("cobblemon.") || translatedAbility == rawAbilityName) {
                    formatAbilityName(rawAbilityName)
                } else {
                    translatedAbility
                }
            }
            possibleAbilities = null
        } else {
            moves = BattleStateTracker.getRevealedMoves(uuid).map { moveName ->
                val usageCount = BattleStateTracker.getMoveUsageCount(uuid, moveName)
                val basePp = Moves.getByName(moveName.lowercase().replace(" ", ""))?.pp
                    ?: Moves.getByName(moveName)?.pp
                if (basePp != null) {
                    val estimatedMax = basePp * 8 / 5
                    val estimatedRemaining = (estimatedMax - usageCount).coerceAtLeast(0)
                    MoveInfo(moveName, estimatedRemaining = estimatedRemaining, estimatedMax = estimatedMax)
                } else {
                    MoveInfo(moveName, usageCount = usageCount)
                }
            }
            item = BattleStateTracker.getItem(uuid)
            actualSpeed = null
            actualDefence = null
            actualSpecialDefence = null
            actualAttack = null
            actualSpecialAttack = null
            val revealedAbility = BattleStateTracker.getRevealedAbility(uuid)
            if (revealedAbility != null) {
                abilityName = revealedAbility
                possibleAbilities = null
            } else {
                abilityName = null
                possibleAbilities = trackedPokemon?.formAbilities?.mapNotNull { potentialAbility ->
                    val translated = Text.translatable(potentialAbility.displayName).string
                    val abilityId = potentialAbility.name
                    if (translated.startsWith("cobblemon.") || translated == abilityId) {
                        formatAbilityName(abilityId)
                    } else {
                        translated
                    }
                }
            }
        }

        val species = if (trackedPokemon?.speciesIdentifier != null) PokemonSpecies.getByIdentifier(trackedPokemon.speciesIdentifier)
        else if (battlePokemon != null) PokemonSpecies.getByIdentifier(battlePokemon.species.resourceIdentifier)
        else null

        val currentFormState = BattleStateTracker.getCurrentForm(uuid)
        val effectiveForm = if (currentFormState != null && species != null) {
            val aspect = BattleStateTracker.formNameToAspect(currentFormState.currentForm)
            species.getForm(setOf(aspect))
        } else {
            trackedPokemon?.form ?: battlePokemon?.form
        }

        val dynamicTypeState = BattleStateTracker.getDynamicTypes(uuid)
        val lostPrimaryType: Boolean
        val addedTypes: List<String>
        val primaryType: com.cobblemon.mod.common.api.types.ElementalType?
        val secondaryType: com.cobblemon.mod.common.api.types.ElementalType?

        if (dynamicTypeState != null) {
            lostPrimaryType = dynamicTypeState.hasLostPrimaryType
            addedTypes = dynamicTypeState.addedTypes

            CobblemonExtendedBattleUI.LOGGER.debug(
                "TooltipDataBuilder: Dynamic types for $name - hasLostPrimary=${dynamicTypeState.hasLostPrimaryType}, " +
                "primary=${dynamicTypeState.primaryType}, secondary=${dynamicTypeState.secondaryType}"
            )

            primaryType = dynamicTypeState.primaryType?.let { ElementalTypes.get(it.lowercase()) }
            secondaryType = dynamicTypeState.secondaryType?.let { ElementalTypes.get(it.lowercase()) }
        } else {
            lostPrimaryType = false
            addedTypes = emptyList()

            primaryType = if (battlePokemon != null && currentFormState == null) battlePokemon.form.primaryType
            else effectiveForm?.primaryType ?: species?.primaryType

            secondaryType = if (battlePokemon != null && currentFormState == null) battlePokemon.form.secondaryType
            else effectiveForm?.secondaryType ?: species?.secondaryType
        }

        val teraType = if (battlePokemon != null) battlePokemon.teraType
        else trackedPokemon?.teraType

        val activeTeraTypeName = BattleStateTracker.getTeraType(uuid)
        val isTerastallized = activeTeraTypeName != null

        return TooltipData(
            uuid = uuid,
            pokemonName = name,
            pokemonId = pokemonId,
            hpPercent = hpPercent,
            statusCondition = trackedPokemon?.status ?: battlePokemon?.status?.status,
            isKO = trackedPokemon?.isKO ?: isPokemonKO(uuid),
            moves = moves,
            item = item,
            statChanges = BattleStateTracker.getStatChanges(uuid),
            volatileStatuses = BattleStateTracker.getVolatileStatuses(uuid),
            level = level,
            speciesName = speciesName,
            isPlayerPokemon = isPlayerPokemon,
            actualSpeed = actualSpeed,
            actualDefence = actualDefence,
            actualSpecialDefence = actualSpecialDefence,
            actualAttack = actualAttack,
            actualSpecialAttack = actualSpecialAttack,
            abilityName = abilityName,
            possibleAbilities = possibleAbilities,
            primaryType = primaryType,
            secondaryType = secondaryType,
            teraType = teraType,
            form = effectiveForm,
            lostPrimaryType = lostPrimaryType,
            addedTypes = addedTypes,
            isTerastallized = isTerastallized,
            activeTeraTypeName = activeTeraTypeName
        )
    }

    /**
     * Snapshot of tracked pokemon data needed for tooltip building.
     * Decouples tooltip builder from TeamIndicatorUI's internal TrackedPokemon class.
     */
    data class TrackedPokemonSnapshot(
        val speciesIdentifier: Identifier?,
        val displayName: String?,
        val hpPercent: Float,
        val status: Status?,
        val isKO: Boolean,
        val isTransformed: Boolean,
        val form: com.cobblemon.mod.common.pokemon.FormData?,
        val teraType: com.cobblemon.mod.common.api.types.tera.TeraType?,
        val formAbilities: List<AbilityInfo>
    )

    /**
     * Minimal ability info needed for tooltip display.
     */
    data class AbilityInfo(
        val name: String,
        val displayName: String
    )

    private fun getClientBattlePokemonByUuid(uuid: UUID): ClientBattlePokemon? {
        val battle = CobblemonClient.battle ?: return null
        for (side in listOf(battle.side1, battle.side2)) {
            for (actor in side.actors) {
                for (active in actor.activePokemon) {
                    active.battlePokemon?.let {
                        if (it.uuid == uuid) return it
                    }
                }
            }
        }
        return null
    }

    private fun getPokemonNameFromUuid(uuid: UUID): String? {
        val battle = CobblemonClient.battle ?: return null
        for (side in listOf(battle.side1, battle.side2)) {
            for (actor in side.actors) {
                for (pokemon in actor.pokemon) {
                    if (pokemon.uuid == uuid) return pokemon.getDisplayName().string
                }
                for (active in actor.activePokemon) {
                    active.battlePokemon?.let {
                        if (it.uuid == uuid) return it.displayName.string
                    }
                }
            }
        }
        return null
    }

    private fun color(r: Int, g: Int, b: Int, a: Int = 255): Int =
        com.cobblemonextendedbattleui.UIUtils.color(r, g, b, a)
}
