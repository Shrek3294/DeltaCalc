package com.cobblemonextendedbattleui

import com.cobblemon.mod.common.pokemon.FormData
import com.cobblemonextendedbattleui.battle.state.*
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

/**
 * Facade for all battle state tracking. Delegates to focused sub-trackers
 * in the battle.state package while maintaining a stable public API.
 *
 * All enums, data classes, and public function signatures are preserved
 * for backward compatibility with callers (BattleInfoPanel, BattleMessageInterceptor,
 * TeamIndicatorUI, etc.).
 */
object BattleStateTracker {

    // ═══════════════════════════════════════════════════════════════════════════
    // Enums & Data Classes (stable public API)
    // ═══════════════════════════════════════════════════════════════════════════

    enum class BattleStat(val translationKey: String, val abbrKey: String) {
        ATTACK("cobblemonextendedbattleui.stat.attack", "cobblemonextendedbattleui.stat.attack.abbr"),
        DEFENSE("cobblemonextendedbattleui.stat.defense", "cobblemonextendedbattleui.stat.defense.abbr"),
        SPECIAL_ATTACK("cobblemonextendedbattleui.stat.special_attack", "cobblemonextendedbattleui.stat.special_attack.abbr"),
        SPECIAL_DEFENSE("cobblemonextendedbattleui.stat.special_defense", "cobblemonextendedbattleui.stat.special_defense.abbr"),
        SPEED("cobblemonextendedbattleui.stat.speed", "cobblemonextendedbattleui.stat.speed.abbr"),
        ACCURACY("cobblemonextendedbattleui.stat.accuracy", "cobblemonextendedbattleui.stat.accuracy.abbr"),
        EVASION("cobblemonextendedbattleui.stat.evasion", "cobblemonextendedbattleui.stat.evasion.abbr");

        val displayName: String get() = Text.translatable(translationKey).string
        val abbr: String get() = Text.translatable(abbrKey).string
    }

    data class DynamicTypeState(
        val primaryType: String?,
        val secondaryType: String?,
        val hasLostPrimaryType: Boolean = false,
        val addedTypes: List<String> = emptyList(),
        val originalPrimaryType: String?,
        val originalSecondaryType: String?
    )

    data class FormState(
        val currentForm: String,
        val originalForm: String? = null,
        val isMega: Boolean = false,
        val isTemporary: Boolean = false
    )

    enum class Weather(val translationKey: String, val icon: String) {
        RAIN("cobblemonextendedbattleui.weather.rain", "🌧"),
        SUN("cobblemonextendedbattleui.weather.sun", "☀"),
        SANDSTORM("cobblemonextendedbattleui.weather.sandstorm", "🏜"),
        HAIL("cobblemonextendedbattleui.weather.hail", "🌨"),
        SNOW("cobblemonextendedbattleui.weather.snow", "❄");

        val displayName: String get() = Text.translatable(translationKey).string
    }

    data class WeatherState(
        val type: Weather,
        val startTurn: Int,
        var confirmedExtended: Boolean = false
    )

    enum class Terrain(val translationKey: String, val icon: String) {
        ELECTRIC("cobblemonextendedbattleui.terrain.electric", "⚡"),
        GRASSY("cobblemonextendedbattleui.terrain.grassy", "🌿"),
        MISTY("cobblemonextendedbattleui.terrain.misty", "🌫"),
        PSYCHIC("cobblemonextendedbattleui.terrain.psychic", "🔮");

        val displayName: String get() = Text.translatable(translationKey).string
    }

    data class TerrainState(
        val type: Terrain,
        val startTurn: Int,
        var confirmedExtended: Boolean = false
    )

    enum class FieldCondition(val translationKey: String, val icon: String, val baseDuration: Int) {
        TRICK_ROOM("cobblemonextendedbattleui.field.trick_room", "🔄", 5),
        GRAVITY("cobblemonextendedbattleui.field.gravity", "⬇", 5),
        MAGIC_ROOM("cobblemonextendedbattleui.field.magic_room", "✨", 5),
        WONDER_ROOM("cobblemonextendedbattleui.field.wonder_room", "🔀", 5);

        val displayName: String get() = Text.translatable(translationKey).string
    }

    data class FieldConditionState(
        val type: FieldCondition,
        val startTurn: Int
    )

    enum class SideCondition(val translationKey: String, val icon: String, val baseDuration: Int?, val maxStacks: Int = 1) {
        REFLECT("cobblemonextendedbattleui.side.reflect", "🛡", 5),
        LIGHT_SCREEN("cobblemonextendedbattleui.side.light_screen", "💡", 5),
        AURORA_VEIL("cobblemonextendedbattleui.side.aurora_veil", "🌈", 5),
        TAILWIND("cobblemonextendedbattleui.side.tailwind", "💨", 4),
        SAFEGUARD("cobblemonextendedbattleui.side.safeguard", "🔰", 5),
        LUCKY_CHANT("cobblemonextendedbattleui.side.lucky_chant", "🍀", 5),
        MIST("cobblemonextendedbattleui.side.mist", "🌁", 5),
        STEALTH_ROCK("cobblemonextendedbattleui.side.stealth_rock", "🪨", null),
        SPIKES("cobblemonextendedbattleui.side.spikes", "📌", null, 3),
        TOXIC_SPIKES("cobblemonextendedbattleui.side.toxic_spikes", "☠", null, 2),
        STICKY_WEB("cobblemonextendedbattleui.side.sticky_web", "🕸", null);

        val displayName: String get() = Text.translatable(translationKey).string
    }

    data class SideConditionState(
        val type: SideCondition,
        val startTurn: Int,
        var stacks: Int = 1,
        var confirmedExtended: Boolean = false
    )

    enum class VolatileStatus(
        val translationKey: String,
        val icon: String,
        val isNegative: Boolean = true,
        val baseDuration: Int? = null,
        val countsDown: Boolean = false
    ) {
        LEECH_SEED("cobblemonextendedbattleui.volatile.leech_seed", "🌱"),
        CONFUSION("cobblemonextendedbattleui.volatile.confusion", "💫", baseDuration = 4),
        TAUNT("cobblemonextendedbattleui.volatile.taunt", "😤", baseDuration = 3),
        ENCORE("cobblemonextendedbattleui.volatile.encore", "🔁", baseDuration = 3),
        DISABLE("cobblemonextendedbattleui.volatile.disable", "🚫", baseDuration = 4),
        TORMENT("cobblemonextendedbattleui.volatile.torment", "😈"),
        INFATUATION("cobblemonextendedbattleui.volatile.infatuation", "💕"),
        PERISH_SONG("cobblemonextendedbattleui.volatile.perish_song", "💀", baseDuration = 4, countsDown = true),
        DROWSY("cobblemonextendedbattleui.volatile.drowsy", "😴", baseDuration = 1),
        CURSE("cobblemonextendedbattleui.volatile.curse", "👻"),
        NIGHTMARE("cobblemonextendedbattleui.volatile.nightmare", "😱"),
        BOUND("cobblemonextendedbattleui.volatile.bound", "⛓", baseDuration = 5),
        TRAPPED("cobblemonextendedbattleui.volatile.trapped", "🚷"),
        SUBSTITUTE("cobblemonextendedbattleui.volatile.substitute", "🎭", isNegative = false),
        AQUA_RING("cobblemonextendedbattleui.volatile.aqua_ring", "💧", isNegative = false),
        INGRAIN("cobblemonextendedbattleui.volatile.ingrain", "🌳", isNegative = false),
        FOCUS_ENERGY("cobblemonextendedbattleui.volatile.focus_energy", "🎯", isNegative = false),
        MAGNET_RISE("cobblemonextendedbattleui.volatile.magnet_rise", "🧲", isNegative = false, baseDuration = 5),
        EMBARGO("cobblemonextendedbattleui.volatile.embargo", "📦", baseDuration = 5),
        HEAL_BLOCK("cobblemonextendedbattleui.volatile.heal_block", "💔", baseDuration = 5),
        DESTINY_BOND("cobblemonextendedbattleui.volatile.destiny_bond", "🔗", isNegative = false),
        FLINCH("cobblemonextendedbattleui.volatile.flinch", "💥");

        val displayName: String get() = Text.translatable(translationKey).string
    }

    data class VolatileStatusState(
        val type: VolatileStatus,
        val startTurn: Int
    )

    enum class ItemStatus(val displaySuffix: String?) {
        HELD(null),
        KNOCKED_OFF("knocked off"),
        STOLEN("stolen"),
        SWAPPED("swapped"),
        CONSUMED("used")
    }

    data class TrackedItem(
        val name: String,
        var status: ItemStatus,
        val revealTurn: Int,
        var removalTurn: Int? = null
    )

    class TrackedMove(val name: String, initialPp: Int, val maxPp: Int) {
        private val _currentPp = AtomicInteger(initialPp)
        val currentPp: Int get() = _currentPp.get()

        fun decrementPp(amount: Int = 1): Int {
            return _currentPp.updateAndGet { current -> (current - amount).coerceAtLeast(0) }
        }
    }

    data class BatonPassData(
        val stats: Map<BattleStat, Int>,
        val volatiles: Set<VolatileStatusState>
    )

    // ═══════════════════════════════════════════════════════════════════════════
    // Battle-level state (stays in facade)
    // ═══════════════════════════════════════════════════════════════════════════

    private var lastBattleId: UUID? = null

    var currentTurn: Int = 0
        private set

    var isSpectating: Boolean = false
        private set

    fun setSpectating(spectating: Boolean) {
        isSpectating = spectating
        CobblemonExtendedBattleUI.LOGGER.debug("BattleStateTracker: Spectating mode = $spectating")
    }

    fun checkBattleChanged(battleId: UUID) {
        if (lastBattleId != battleId) {
            clear()
            lastBattleId = battleId
        }
    }

    fun setTurn(turn: Int) {
        currentTurn = turn
        ConditionTracker.checkForExpiredConditions(currentTurn)
        CobblemonExtendedBattleUI.LOGGER.debug("BattleStateTracker: Turn $turn")
    }

    fun clear() {
        lastBattleId = null
        currentTurn = 0
        isSpectating = false
        PokemonRegistry.clear()
        StatTracker.clear()
        ConditionTracker.clear()
        VolatileStatusTracker.clear()
        TypeTracker.clear()
        FormTracker.clear()
        AbilityItemTracker.clear()
        MoveTracker.clear()
        pendingBatonPass.clear()
        batonPassUsers.clear()
        BattleMessageInterceptor.clearMoveTracking()
        CobblemonExtendedBattleUI.LOGGER.debug("BattleStateTracker: Cleared all state")
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Pokemon Registry (delegates to PokemonRegistry)
    // ═══════════════════════════════════════════════════════════════════════════

    fun setPlayerNames(allyName: String, opponentName: String) = PokemonRegistry.setPlayerNames(allyName, opponentName)
    fun registerPokemon(uuid: UUID, name: String, isAlly: Boolean) {
        PokemonRegistry.registerPokemon(uuid, name, isAlly)
        VolatileStatusTracker.ensureInitialized(uuid)
    }
    fun isPokemonAlly(uuid: UUID): Boolean = PokemonRegistry.isPokemonAlly(uuid)
    fun getPokemonUuid(pokemonName: String, preferAlly: Boolean? = null): UUID? = PokemonRegistry.getPokemonUuid(pokemonName, preferAlly)

    // ── KO & Transform Tracking ──────────────────────────────────────────────

    fun markAsKO(pokemonName: String, preferAlly: Boolean? = null) = PokemonRegistry.markAsKO(pokemonName, preferAlly)
    fun markAsKO(uuid: UUID) = PokemonRegistry.markAsKO(uuid)
    fun isKO(uuid: UUID): Boolean = PokemonRegistry.isKO(uuid)

    fun markAsTransformed(pokemonName: String, preferAlly: Boolean? = null) = PokemonRegistry.markAsTransformed(pokemonName, preferAlly)
    fun markAsTransformed(uuid: UUID) = PokemonRegistry.markAsTransformed(uuid)
    fun isTransformed(uuid: UUID): Boolean = PokemonRegistry.isTransformed(uuid)
    fun clearTransformStatus(uuid: UUID) = PokemonRegistry.clearTransformStatus(uuid)
    fun clearTransformStatusByName(pokemonName: String, preferAlly: Boolean? = null) = PokemonRegistry.clearTransformStatusByName(pokemonName, preferAlly)

    // ═══════════════════════════════════════════════════════════════════════════
    // Stat Changes (delegates to StatTracker)
    // ═══════════════════════════════════════════════════════════════════════════

    fun applyStatChange(pokemonName: String, stat: BattleStat, stages: Int, preferAlly: Boolean? = null) = StatTracker.applyStatChange(pokemonName, stat, stages, preferAlly)
    fun setStatStage(pokemonName: String, stat: BattleStat, stage: Int, preferAlly: Boolean? = null) = StatTracker.setStatStage(pokemonName, stat, stage, preferAlly)
    fun clearPokemonStats(uuid: UUID) = StatTracker.clearPokemonStats(uuid)
    fun clearPokemonStatsByName(pokemonName: String, preferAlly: Boolean? = null) = StatTracker.clearPokemonStatsByName(pokemonName, preferAlly)
    fun clearAllStatsForAll() = StatTracker.clearAllStatsForAll()
    fun invertStats(pokemonName: String, preferAlly: Boolean? = null) = StatTracker.invertStats(pokemonName, preferAlly)
    fun copyStats(sourceName: String, targetName: String, sourceIsAlly: Boolean? = null, targetIsAlly: Boolean? = null) = StatTracker.copyStats(sourceName, targetName, sourceIsAlly, targetIsAlly)
    fun swapStats(pokemon1Name: String, pokemon2Name: String, pokemon1IsAlly: Boolean? = null, pokemon2IsAlly: Boolean? = null) = StatTracker.swapStats(pokemon1Name, pokemon2Name, pokemon1IsAlly, pokemon2IsAlly)
    fun swapSpecificStats(pokemon1Name: String, pokemon2Name: String, statsToSwap: List<BattleStat>, pokemon1IsAlly: Boolean? = null, pokemon2IsAlly: Boolean? = null) = StatTracker.swapSpecificStats(pokemon1Name, pokemon2Name, statsToSwap, pokemon1IsAlly, pokemon2IsAlly)
    fun stealPositiveStats(userPokemonName: String, targetPokemonName: String, userIsAlly: Boolean? = null, targetIsAlly: Boolean? = null) = StatTracker.stealPositiveStats(userPokemonName, targetPokemonName, userIsAlly, targetIsAlly)
    fun getStatChanges(uuid: UUID): Map<BattleStat, Int> = StatTracker.getStatChanges(uuid)
    fun getStatFromName(name: String): BattleStat? = StatTracker.getStatFromName(name)

    // ═══════════════════════════════════════════════════════════════════════════
    // Conditions (delegates to ConditionTracker)
    // ═══════════════════════════════════════════════════════════════════════════

    val weather: WeatherState? get() = ConditionTracker.weather
    val terrain: TerrainState? get() = ConditionTracker.terrain

    fun setWeather(type: Weather) = ConditionTracker.setWeather(type, currentTurn)
    fun clearWeather() = ConditionTracker.clearWeather(currentTurn)
    fun getWeatherTurnsRemaining(): String? = ConditionTracker.getWeatherTurnsRemaining(currentTurn)

    fun setTerrain(type: Terrain) = ConditionTracker.setTerrain(type, currentTurn)
    fun clearTerrain() = ConditionTracker.clearTerrain(currentTurn)
    fun getTerrainTurnsRemaining(): String? = ConditionTracker.getTerrainTurnsRemaining(currentTurn)

    fun setFieldCondition(type: FieldCondition) = ConditionTracker.setFieldCondition(type, currentTurn)
    fun clearFieldCondition(type: FieldCondition) = ConditionTracker.clearFieldCondition(type)
    fun getFieldConditions(): Map<FieldCondition, FieldConditionState> = ConditionTracker.getFieldConditions()
    fun getFieldConditionTurnsRemaining(type: FieldCondition): String? = ConditionTracker.getFieldConditionTurnsRemaining(type, currentTurn)

    fun setSideCondition(isPlayerSide: Boolean, type: SideCondition) = ConditionTracker.setSideCondition(isPlayerSide, type, currentTurn)
    fun clearSideCondition(isPlayerSide: Boolean, type: SideCondition) = ConditionTracker.clearSideCondition(isPlayerSide, type)
    fun getPlayerSideConditions(): Map<SideCondition, SideConditionState> = ConditionTracker.getPlayerSideConditions()
    fun getOpponentSideConditions(): Map<SideCondition, SideConditionState> = ConditionTracker.getOpponentSideConditions()
    fun swapSideConditions() = ConditionTracker.swapSideConditions()
    fun getSideConditionTurnsRemaining(isPlayerSide: Boolean, type: SideCondition): String? = ConditionTracker.getSideConditionTurnsRemaining(isPlayerSide, type, currentTurn)

    // ── Terastallization ─────────────────────────────────────────────────────

    fun setTerastallized(pokemonName: String, teraTypeName: String, preferAlly: Boolean? = null) {
        val uuid = PokemonRegistry.resolvePokemonUuid(pokemonName, preferAlly) ?: run {
            CobblemonExtendedBattleUI.LOGGER.debug("BattleStateTracker: Unknown Pokemon '$pokemonName' for Terastallization")
            return
        }
        setTerastallized(uuid, teraTypeName)
    }
    fun setTerastallized(uuid: UUID, teraTypeName: String) = ConditionTracker.setTerastallized(uuid, teraTypeName)
    fun isTerastallized(uuid: UUID): Boolean = ConditionTracker.isTerastallized(uuid)
    fun getTeraType(uuid: UUID): String? = ConditionTracker.getTeraType(uuid)

    // ═══════════════════════════════════════════════════════════════════════════
    // Volatile Statuses (delegates to VolatileStatusTracker)
    // ═══════════════════════════════════════════════════════════════════════════

    fun setVolatileStatus(pokemonName: String, type: VolatileStatus, preferAlly: Boolean? = null) = VolatileStatusTracker.setVolatileStatus(pokemonName, type, currentTurn, preferAlly)
    fun clearVolatileStatus(pokemonName: String, type: VolatileStatus, preferAlly: Boolean? = null) = VolatileStatusTracker.clearVolatileStatus(pokemonName, type, preferAlly)
    fun clearPokemonVolatiles(uuid: UUID) = VolatileStatusTracker.clearPokemonVolatiles(uuid)
    fun clearPokemonVolatilesByName(pokemonName: String, preferAlly: Boolean? = null) = VolatileStatusTracker.clearPokemonVolatilesByName(pokemonName, preferAlly)
    fun getVolatileStatuses(uuid: UUID): Set<VolatileStatusState> = VolatileStatusTracker.getVolatileStatuses(uuid)
    fun getVolatileTurnsRemaining(state: VolatileStatusState): String? = VolatileStatusTracker.getVolatileTurnsRemaining(state, currentTurn)
    fun applyPerishSongToAll() = VolatileStatusTracker.applyPerishSongToAll(currentTurn)

    // ═══════════════════════════════════════════════════════════════════════════
    // Dynamic Types (delegates to TypeTracker)
    // ═══════════════════════════════════════════════════════════════════════════

    fun initializeDynamicTypes(uuid: UUID, primaryType: String?, secondaryType: String?) = TypeTracker.initializeDynamicTypes(uuid, primaryType, secondaryType)
    fun setTypeReplacement(pokemonName: String, newPrimaryType: String?, newSecondaryType: String?, preferAlly: Boolean?) = TypeTracker.setTypeReplacement(pokemonName, newPrimaryType, newSecondaryType, preferAlly)
    fun loseType(pokemonName: String, typeName: String, preferAlly: Boolean?) = TypeTracker.loseType(pokemonName, typeName, preferAlly)
    fun addType(pokemonName: String, typeName: String, preferAlly: Boolean?) = TypeTracker.addType(pokemonName, typeName, preferAlly)
    fun getDynamicTypes(uuid: UUID): DynamicTypeState? = TypeTracker.getDynamicTypes(uuid)
    fun clearDynamicTypes(uuid: UUID) = TypeTracker.clearDynamicTypes(uuid)
    fun restoreOriginalTypes(pokemonName: String, preferAlly: Boolean? = null) = TypeTracker.restoreOriginalTypes(pokemonName, preferAlly)
    fun restoreOriginalTypes(uuid: UUID) = TypeTracker.restoreOriginalTypes(uuid)

    // ═══════════════════════════════════════════════════════════════════════════
    // Form Changes (delegates to FormTracker)
    // ═══════════════════════════════════════════════════════════════════════════

    fun registerSpeciesId(uuid: UUID, speciesId: Identifier) = FormTracker.registerSpeciesId(uuid, speciesId)
    fun getSpeciesId(uuid: UUID): Identifier? = FormTracker.getSpeciesId(uuid)
    fun getSpeciesIdByName(pokemonName: String, preferAlly: Boolean? = null): Identifier? = FormTracker.getSpeciesIdByName(pokemonName, preferAlly)

    fun setCurrentForm(pokemonName: String, formName: String, isMega: Boolean = false, isTemporary: Boolean = false, preferAlly: Boolean? = null) = FormTracker.setCurrentForm(pokemonName, formName, isMega, isTemporary, preferAlly)
    fun clearCurrentForm(pokemonName: String, preferAlly: Boolean? = null) = FormTracker.clearCurrentForm(pokemonName, preferAlly)
    fun getCurrentForm(uuid: UUID): FormState? = FormTracker.getCurrentForm(uuid)
    fun getCurrentFormByName(pokemonName: String, preferAlly: Boolean? = null): FormState? = FormTracker.getCurrentFormByName(pokemonName, preferAlly)

    fun formNameToAspects(formName: String): List<String> = FormTracker.formNameToAspects(formName)
    fun formNameToAspect(formName: String): String = FormTracker.formNameToAspect(formName)
    fun updateTypesForFormChange(pokemonName: String, speciesId: Identifier?, formName: String, preferAlly: Boolean? = null): FormData? = FormTracker.updateTypesForFormChange(pokemonName, speciesId, formName, preferAlly)

    // ═══════════════════════════════════════════════════════════════════════════
    // Abilities & Items (delegates to AbilityItemTracker)
    // ═══════════════════════════════════════════════════════════════════════════

    fun setRevealedAbility(pokemonName: String, abilityName: String, preferAlly: Boolean? = null) = AbilityItemTracker.setRevealedAbility(pokemonName, abilityName, preferAlly)
    fun setRevealedAbility(uuid: UUID, abilityName: String) = AbilityItemTracker.setRevealedAbility(uuid, abilityName)
    fun getRevealedAbility(uuid: UUID): String? = AbilityItemTracker.getRevealedAbility(uuid)
    fun getRevealedAbilityByName(pokemonName: String, preferAlly: Boolean? = null): String? = AbilityItemTracker.getRevealedAbilityByName(pokemonName, preferAlly)
    fun clearRevealedAbility(uuid: UUID) = AbilityItemTracker.clearRevealedAbility(uuid)

    fun setItem(pokemonName: String, itemName: String, status: ItemStatus, preferAlly: Boolean? = null) = AbilityItemTracker.setItem(pokemonName, itemName, status, currentTurn, preferAlly)
    fun getItem(uuid: UUID): TrackedItem? = AbilityItemTracker.getItem(uuid)
    fun getItemByName(pokemonName: String, preferAlly: Boolean? = null): TrackedItem? = AbilityItemTracker.getItemByName(pokemonName, preferAlly)
    fun transferItem(fromPokemon: String, toPokemon: String, itemName: String, fromIsAlly: Boolean? = null, toIsAlly: Boolean? = null) = AbilityItemTracker.transferItem(fromPokemon, toPokemon, itemName, currentTurn, fromIsAlly, toIsAlly)
    fun receiveItemViaTrick(pokemonName: String, newItemName: String, preferAlly: Boolean? = null) = AbilityItemTracker.receiveItemViaTrick(pokemonName, newItemName, currentTurn, preferAlly)
    fun markItemSwapped(pokemonName: String, preferAlly: Boolean? = null) = AbilityItemTracker.markItemSwapped(pokemonName, currentTurn, preferAlly)

    // ═══════════════════════════════════════════════════════════════════════════
    // Moves & PP (delegates to MoveTracker)
    // ═══════════════════════════════════════════════════════════════════════════

    fun initializeMoves(uuid: UUID, moves: List<TrackedMove>) = MoveTracker.initializeMoves(uuid, moves)
    fun decrementPP(uuid: UUID, moveName: String, targetName: String? = null) = MoveTracker.decrementPP(uuid, moveName, targetName)
    fun registerPressure(pokemonName: String) = MoveTracker.registerPressure(pokemonName)
    fun hasPressure(uuid: UUID): Boolean = MoveTracker.hasPressure(uuid)
    fun getTrackedMoves(uuid: UUID): List<TrackedMove>? = MoveTracker.getTrackedMoves(uuid)
    fun addRevealedMove(pokemonName: String, moveName: String, targetName: String? = null, preferAlly: Boolean? = null) = MoveTracker.addRevealedMove(pokemonName, moveName, targetName, preferAlly)
    fun getRevealedMoves(uuid: UUID): Set<String> = MoveTracker.getRevealedMoves(uuid)
    fun getMoveUsageCount(uuid: UUID, moveName: String): Int = MoveTracker.getMoveUsageCount(uuid, moveName)

    // ═══════════════════════════════════════════════════════════════════════════
    // Baton Pass (coordinates between StatTracker and VolatileStatusTracker)
    // ═══════════════════════════════════════════════════════════════════════════

    private val BATON_PASS_VOLATILES = setOf(
        VolatileStatus.SUBSTITUTE,
        VolatileStatus.FOCUS_ENERGY,
        VolatileStatus.INGRAIN,
        VolatileStatus.AQUA_RING,
        VolatileStatus.MAGNET_RISE,
        VolatileStatus.CONFUSION,
        VolatileStatus.EMBARGO,
        VolatileStatus.HEAL_BLOCK
    )

    private val pendingBatonPass = java.util.concurrent.ConcurrentHashMap<Boolean, BatonPassData>()
    private val batonPassUsers = java.util.concurrent.ConcurrentHashMap.newKeySet<UUID>()

    fun markBatonPassUsed(pokemonName: String, preferAlly: Boolean? = null) {
        val uuid = PokemonRegistry.resolvePokemonUuid(pokemonName, preferAlly) ?: return
        batonPassUsers.add(uuid)
        CobblemonExtendedBattleUI.LOGGER.debug("BattleStateTracker: $pokemonName used Baton Pass")
    }

    fun prepareBatonPassIfUsed(uuid: UUID): Boolean {
        if (!batonPassUsers.remove(uuid)) return false

        val isAlly = PokemonRegistry.isPokemonAlly(uuid)
        val stats = StatTracker.getRawStats(uuid) ?: emptyMap()
        val volatiles = VolatileStatusTracker.getRawVolatiles(uuid)
            ?.filter { it.type in BATON_PASS_VOLATILES }
            ?.toSet() ?: emptySet()

        if (stats.isNotEmpty() || volatiles.isNotEmpty()) {
            pendingBatonPass[isAlly] = BatonPassData(stats, volatiles)
            CobblemonExtendedBattleUI.LOGGER.debug(
                "BattleStateTracker: Prepared Baton Pass data for ${if (isAlly) "ally" else "opponent"} side: " +
                "${stats.size} stats, ${volatiles.size} volatiles"
            )
        }

        StatTracker.clearPokemonStats(uuid)
        VolatileStatusTracker.clearPokemonVolatiles(uuid)

        return true
    }

    fun applyBatonPassIfPending(uuid: UUID) {
        val isAlly = PokemonRegistry.isPokemonAlly(uuid)
        val batonData = pendingBatonPass.remove(isAlly) ?: return

        if (batonData.stats.isNotEmpty()) {
            StatTracker.applyBatonPassStats(uuid, batonData.stats)
        }

        if (batonData.volatiles.isNotEmpty()) {
            VolatileStatusTracker.applyBatonPassVolatiles(uuid, batonData.volatiles, currentTurn)
        }

        CobblemonExtendedBattleUI.LOGGER.debug(
            "BattleStateTracker: Applied Baton Pass to UUID $uuid: " +
            "${batonData.stats.size} stats, ${batonData.volatiles.size} volatiles"
        )
    }

    fun hasPendingBatonPass(isAlly: Boolean): Boolean = pendingBatonPass.containsKey(isAlly)
}
