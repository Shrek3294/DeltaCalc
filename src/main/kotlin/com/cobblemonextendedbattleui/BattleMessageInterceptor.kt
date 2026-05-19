package com.cobblemonextendedbattleui

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemonextendedbattleui.battle.messages.MessageParser
import com.cobblemonextendedbattleui.battle.messages.StateUpdater
import com.cobblemonextendedbattleui.battle.messages.TranslationKeys
import com.cobblemonextendedbattleui.calc.DebugDumper
import net.minecraft.text.Text
import net.minecraft.text.TranslatableTextContent
import net.minecraft.util.Identifier

/**
 * Parses Cobblemon battle messages (via TranslatableTextContent) and updates BattleStateTracker.
 * Delegates to TranslationKeys for constant lookups, MessageParser for argument extraction,
 * and StateUpdater for state mutation.
 */
object BattleMessageInterceptor {
    private const val USED_MOVE_ON_KEY = "cobblemon.battle.used_move_on"
    private const val USED_MOVE_KEY = "cobblemon.battle.used_move"

    /**
     * Clear stale move tracking data. Called when battle state is cleared.
     */
    fun clearMoveTracking() {
        MessageParser.clearMoveTracking()
    }

    fun processMessages(messages: List<Text>) {
        for (message in messages) {
            processComponent(message)
        }
    }

    private fun processComponent(text: Text) {
        val contents = text.content

        if (contents is TranslatableTextContent) {
            val key = contents.key
            val args = contents.args

            if (key.startsWith("cobblemon.battle.")) {
                CobblemonExtendedBattleUI.LOGGER.debug("BattleMessage: key='$key', args=${args.map {
                    when (it) {
                        is Text -> it.string
                        else -> it.toString()
                    }
                }}")
            }

            if (key == TranslationKeys.TURN_KEY) {
                StateUpdater.extractTurn(args)
                MessageParser.markMoveResolved()
                DebugDumper.resetHitAggregator()
                return
            }

            // End-of-move-phase signals: faint, switch-in, send-out, drag.
            // These mark the prior move as resolved so DebugDumper doesn't pair
            // post-move passive damage (Toxic / Leech Seed / hazards) against it.
            if (key == TranslationKeys.FAINT_KEY ||
                key == TranslationKeys.SWITCH_KEY ||
                key == TranslationKeys.DRAG_KEY ||
                key == TranslationKeys.SENDOUT_KEY ||
                key == TranslationKeys.REPLACE_KEY
            ) {
                MessageParser.markMoveResolved()
                DebugDumper.resetHitAggregator()
            }

            // Track move usage with target: [user, moveName, target]
            // Delta can route these through suffixed translation keys.
            if (isUsedMoveOnKey(key) && args.size >= 3) {
                val user = MessageParser.normalizeBattleName(MessageParser.extractPokemonName(args[0]))
                val moveName = MessageParser.argToString(args[1])
                val moveKey = MessageParser.argToTranslationKey(args[1])
                val target = MessageParser.normalizeBattleName(MessageParser.extractPokemonName(args[2]))
                MessageParser.trackMove(user, moveName, moveKey, target)
                CobblemonExtendedBattleUI.LOGGER.debug("BattleMessageInterceptor: Move tracked - $user used $moveName (key=$moveKey) on $target")

                BattleStateTracker.addRevealedMove(user, moveName, target)

                if (MessageParser.isMove(TranslationKeys.SPECTRAL_THIEF_KEYS, TranslationKeys.SPECTRAL_THIEF_NAME)) {
                    BattleStateTracker.stealPositiveStats(user, target)
                }
            }

            // Track move usage without target (self-targeting): [user, moveName]
            if (isUsedMoveKey(key) && args.size >= 2) {
                val user = MessageParser.normalizeBattleName(MessageParser.extractPokemonName(args[0]))
                val moveName = MessageParser.argToString(args[1])
                val moveKey = MessageParser.argToTranslationKey(args[1])
                MessageParser.trackMove(user, moveName, moveKey, null)
                CobblemonExtendedBattleUI.LOGGER.debug("BattleMessageInterceptor: Self-move tracked - $user used $moveName (key=$moveKey)")

                BattleStateTracker.addRevealedMove(user, moveName, null)

                if (MessageParser.isMove(TranslationKeys.BATON_PASS_KEYS, TranslationKeys.BATON_PASS_NAME)) {
                    BattleStateTracker.markBatonPassUsed(user)
                }
            }

            // Pressure ability
            if (key == TranslationKeys.PRESSURE_KEY && args.isNotEmpty()) {
                val pokemonName = MessageParser.argToString(args[0])
                BattleStateTracker.registerPressure(pokemonName)
                BattleStateTracker.setRevealedAbility(pokemonName, "Pressure")
                CobblemonExtendedBattleUI.LOGGER.debug("BattleMessageInterceptor: Pressure registered for $pokemonName")
            }

            // ═══════════════════════════════════════════════════════════════════
            // Ability Reveal Messages
            // ═══════════════════════════════════════════════════════════════════

            if (key == TranslationKeys.ABILITY_GENERIC_KEY && args.size >= 2) {
                val pokemonName = MessageParser.argToString(args[0])
                val abilityName = MessageParser.argToString(args[1])
                BattleStateTracker.setRevealedAbility(pokemonName, abilityName)
                maybeApplyEmbodyAspect(pokemonName, abilityName)
            }

            TranslationKeys.ABILITY_SINGLE_ARG_KEYS[key]?.let { abilityName ->
                if (args.isNotEmpty()) {
                    val pokemonName = MessageParser.argToString(args[0])
                    BattleStateTracker.setRevealedAbility(pokemonName, abilityName)
                    maybeApplyEmbodyAspect(pokemonName, abilityName)
                }
            }

            if (key == TranslationKeys.ABILITY_TRACE_KEY && args.size >= 3) {
                val tracerName = MessageParser.argToString(args[0])
                val targetName = MessageParser.argToString(args[1])
                val copiedAbility = MessageParser.argToString(args[2])
                BattleStateTracker.setRevealedAbility(tracerName, "Trace")
                BattleStateTracker.setRevealedAbility(targetName, copiedAbility)
            }

            if (key == TranslationKeys.ABILITY_RECEIVER_KEY && args.size >= 2) {
                val pokemonName = MessageParser.argToString(args[0])
                val abilityName = MessageParser.argToString(args[1])
                BattleStateTracker.setRevealedAbility(pokemonName, abilityName)
            }

            if (key == TranslationKeys.ABILITY_REPLACE_KEY && args.size >= 2) {
                val pokemonName = MessageParser.argToString(args[0])
                val newAbility = MessageParser.argToString(args[1])
                BattleStateTracker.setRevealedAbility(pokemonName, newAbility)
            }

            if (key == TranslationKeys.ABILITY_MAGICBOUNCE_KEY && args.isNotEmpty()) {
                val pokemonName = MessageParser.argToString(args[0])
                BattleStateTracker.setRevealedAbility(pokemonName, "Magic Bounce")
            }

            TranslationKeys.ABILITY_START_KEYS[key]?.let { abilityName ->
                if (args.isNotEmpty()) {
                    val pokemonName = MessageParser.argToString(args[0])
                    BattleStateTracker.setRevealedAbility(pokemonName, abilityName)
                }
            }

            // ═══════════════════════════════════════════════════════════════════
            // Stat Boost/Unboost
            // ═══════════════════════════════════════════════════════════════════

            mappedKeyLookup(key, TranslationKeys.BOOST_MAGNITUDE_KEYS)?.let { stages ->
                StateUpdater.extractBoost(args, stages)
                return
            }

            mappedKeyLookup(key, TranslationKeys.UNBOOST_MAGNITUDE_KEYS)?.let { stages ->
                StateUpdater.extractBoost(args, -stages)
                return
            }

            if (matchesAnyVariant(key, setOf("cobblemon.battle.setboost.bellydrum", "cobblemon.battle.setboost.angerpoint"))) {
                StateUpdater.extractSetBoost(args)
                return
            }

            if (matchesKeyVariant(key, "cobblemon.battle.clearallboost")) {
                BattleStateTracker.clearAllStatsForAll()
                return
            }

            if (matchesKeyVariant(key, "cobblemon.battle.clearboost")) {
                StateUpdater.extractClearBoost(args)
                return
            }

            if (matchesKeyVariant(key, "cobblemon.battle.invertboost")) {
                StateUpdater.extractInvertBoost(args)
                return
            }

            if (matchesAnyVariant(key, setOf("cobblemon.battle.swapboost.heartswap", "cobblemon.battle.swapboost.generic"))) {
                StateUpdater.extractSwapBoostAllStats(args)
                return
            }

            if (matchesKeyVariant(key, "cobblemon.battle.swapboost.powerswap")) {
                StateUpdater.extractSwapBoostSpecific(args, listOf(BattleStateTracker.BattleStat.ATTACK, BattleStateTracker.BattleStat.SPECIAL_ATTACK))
                return
            }
            if (matchesKeyVariant(key, "cobblemon.battle.swapboost.guardswap")) {
                StateUpdater.extractSwapBoostSpecific(args, listOf(BattleStateTracker.BattleStat.DEFENSE, BattleStateTracker.BattleStat.SPECIAL_DEFENSE))
                return
            }
            if (matchesKeyVariant(key, "cobblemon.battle.activate.speedswap")) {
                StateUpdater.extractSwapBoostSpecific(args, listOf(BattleStateTracker.BattleStat.SPEED))
                return
            }

            if (matchesKeyVariant(key, "cobblemon.battle.copyboost.generic")) {
                StateUpdater.extractCopyBoost(args)
                return
            }

            // ═══════════════════════════════════════════════════════════════════
            // Weather, Terrain, Field, Side Conditions
            // ═══════════════════════════════════════════════════════════════════

            TranslationKeys.WEATHER_START_KEYS[key]?.let { weather ->
                BattleStateTracker.setWeather(weather)
                return
            }

            if (key in TranslationKeys.WEATHER_END_KEYS) {
                BattleStateTracker.clearWeather()
                return
            }

            TranslationKeys.TERRAIN_START_KEYS[key]?.let { terrain ->
                BattleStateTracker.setTerrain(terrain)
                return
            }

            if (key in TranslationKeys.TERRAIN_END_KEYS) {
                BattleStateTracker.clearTerrain()
                return
            }

            TranslationKeys.FIELD_START_KEYS[key]?.let { condition ->
                BattleStateTracker.setFieldCondition(condition)
                return
            }

            TranslationKeys.FIELD_END_KEYS[key]?.let { condition ->
                BattleStateTracker.clearFieldCondition(condition)
                return
            }

            TranslationKeys.SIDE_START_KEYS[key]?.let { (condition, isAlly) ->
                BattleStateTracker.setSideCondition(isAlly, condition)
                return
            }

            TranslationKeys.SIDE_END_KEYS[key]?.let { (condition, isAlly) ->
                BattleStateTracker.clearSideCondition(isAlly, condition)
                return
            }

            // Court Change
            if (key == TranslationKeys.COURT_CHANGE_KEY) {
                BattleStateTracker.swapSideConditions()
                if (args.isNotEmpty()) {
                    val pokemonName = MessageParser.argToString(args[0])
                    BattleStateTracker.addRevealedMove(pokemonName, "Court Change", null)
                }
            }

            // Terastallization
            if (key == TranslationKeys.TERASTALLIZE_KEY && args.size >= 2) {
                val pokemonName = MessageParser.argToString(args[0])
                val teraType = MessageParser.argToString(args[1])
                BattleStateTracker.setTerastallized(pokemonName, teraType)
                CobblemonExtendedBattleUI.LOGGER.debug("BattleMessageInterceptor: $pokemonName Terastallized into $teraType type")
            }

            // ═══════════════════════════════════════════════════════════════════
            // Volatile Statuses
            // ═══════════════════════════════════════════════════════════════════

            TranslationKeys.VOLATILE_START_KEYS[key]?.let { volatileStatus ->
                StateUpdater.extractVolatileStatusStart(args, volatileStatus)
                return
            }

            TranslationKeys.VOLATILE_END_KEYS[key]?.let { volatileStatus ->
                StateUpdater.extractVolatileStatusEnd(args, volatileStatus)
                return
            }

            TranslationKeys.VOLATILE_ACTIVATE_KEYS[key]?.let { volatileStatus ->
                StateUpdater.extractVolatileStatusStart(args, volatileStatus)
                return
            }

            // ═══════════════════════════════════════════════════════════════════
            // Item Tracking
            // ═══════════════════════════════════════════════════════════════════

            if (key in TranslationKeys.ITEM_REVEAL_KEYS) {
                StateUpdater.extractItemReveal(args)
                return
            }

            if (key == TranslationKeys.TRICK_KEY) {
                StateUpdater.extractTrickItem(args)
                return
            }

            if (key == TranslationKeys.LIFE_ORB_KEY) {
                StateUpdater.extractLifeOrbReveal(args)
                return
            }

            if (key == TranslationKeys.FRISK_KEY) {
                StateUpdater.extractFriskItem(args)
                return
            }

            if (key == TranslationKeys.THIEF_KEY) {
                StateUpdater.extractThiefItem(args)
                return
            }

            if (key == TranslationKeys.BESTOW_KEY) {
                StateUpdater.extractBestowItem(args)
                return
            }

            if (key in TranslationKeys.ITEM_CONSUMED_KEYS) {
                StateUpdater.extractItemConsumed(args)
                return
            }

            TranslationKeys.ITEM_CONSUMED_SINGLE_ARG_KEYS[key]?.let { itemName ->
                StateUpdater.extractItemConsumedSingleArg(args, itemName)
                return
            }

            if (key in TranslationKeys.BERRY_DAMAGE_KEYS) {
                StateUpdater.extractBerryFromKey(key, args)
                return
            }

            if (key == TranslationKeys.KNOCKOFF_KEY) {
                StateUpdater.extractKnockOff(args)
                return
            }

            if (key == TranslationKeys.STEALEAT_KEY) {
                StateUpdater.extractStealEat(args)
                return
            }

            if (key == TranslationKeys.CORROSIVEGAS_KEY) {
                StateUpdater.extractCorrosiveGas(args)
                return
            }

            if (key in TranslationKeys.TRICK_ACTIVATE_KEYS) {
                StateUpdater.extractTrickActivation(args)
                return
            }

            if (key in TranslationKeys.HEALING_ITEM_KEYS) {
                StateUpdater.extractHealingItem(args)
                return
            }

            // Perish Song
            if (key == TranslationKeys.PERISH_SONG_FIELD_KEY) {
                BattleStateTracker.applyPerishSongToAll()
                return
            }

            // Transform (Ditto)
            if (key == TranslationKeys.TRANSFORM_KEY) {
                StateUpdater.markPokemonTransformed(args)
                return
            }

            // ═══════════════════════════════════════════════════════════════════
            // Form Change Detection
            // ═══════════════════════════════════════════════════════════════════

            if (key == TranslationKeys.FORMECHANGE_PERMANENT_KEY && args.size >= 2) {
                val pokemonName = MessageParser.argToString(args[0])
                val formName = MessageParser.argToString(args[1])
                BattleStateTracker.setCurrentForm(pokemonName, formName, isMega = false, isTemporary = false)
                val speciesId = BattleStateTracker.getSpeciesIdByName(pokemonName)
                BattleStateTracker.updateTypesForFormChange(pokemonName, speciesId, formName)
            }

            if (key == TranslationKeys.FORMECHANGE_TEMPORARY_KEY && args.size >= 2) {
                val pokemonName = MessageParser.argToString(args[0])
                val formName = MessageParser.argToString(args[1])
                BattleStateTracker.setCurrentForm(pokemonName, formName, isMega = false, isTemporary = true)
                val speciesId = BattleStateTracker.getSpeciesIdByName(pokemonName)
                BattleStateTracker.updateTypesForFormChange(pokemonName, speciesId, formName)
            }

            if (key == TranslationKeys.FORMECHANGE_ENDED_KEY && args.isNotEmpty()) {
                val pokemonName = MessageParser.argToString(args[0])
                BattleStateTracker.clearCurrentForm(pokemonName)
                BattleStateTracker.restoreOriginalTypes(pokemonName)
            }

            if ((key == TranslationKeys.MEGA_FORMECHANGE_KEY || key == TranslationKeys.MEGA_EVOLVED_KEY) && args.isNotEmpty()) {
                val pokemonName = MessageParser.argToString(args[0])
                val speciesId = BattleStateTracker.getSpeciesIdByName(pokemonName)
                // Only apply form=Mega when the species actually has a Mega form. Otherwise
                // some Delta-server messages mis-tag non-Mega Pokémon (e.g. Iron Valiant)
                // with this key, and we'd load wrong base stats from a non-existent Mega form.
                if (speciesId != null && hasMegaForm(speciesId)) {
                    BattleStateTracker.setCurrentForm(pokemonName, "Mega", isMega = true, isTemporary = false)
                    BattleStateTracker.updateTypesForFormChange(pokemonName, speciesId, "Mega")
                } else {
                    CobblemonExtendedBattleUI.LOGGER.debug(
                        "BattleMessageInterceptor: ignoring MEGA form change for $pokemonName — species has no mega form"
                    )
                }
            }

            TranslationKeys.SPECIAL_FORMECHANGE_KEYS[key]?.let { formName ->
                if (args.isNotEmpty()) {
                    val pokemonName = MessageParser.argToString(args[0])
                    BattleStateTracker.setCurrentForm(pokemonName, formName, isMega = false, isTemporary = true)
                    val speciesId = BattleStateTracker.getSpeciesIdByName(pokemonName)
                    BattleStateTracker.updateTypesForFormChange(pokemonName, speciesId, formName)
                }
            }

            TranslationKeys.SPECIAL_FORMECHANGE_END_KEYS[key]?.let { revertFormName ->
                if (args.isNotEmpty()) {
                    val pokemonName = MessageParser.argToString(args[0])
                    BattleStateTracker.clearCurrentForm(pokemonName)
                    BattleStateTracker.restoreOriginalTypes(pokemonName)
                    CobblemonExtendedBattleUI.LOGGER.debug("BattleMessageInterceptor: $pokemonName reverted to $revertFormName")
                }
            }

            if (key == TranslationKeys.DYNAMAX_KEY && args.isNotEmpty()) {
                val pokemonName = MessageParser.argToString(args[0])
                BattleStateTracker.setCurrentForm(pokemonName, "Dynamax", isMega = false, isTemporary = true)
            }

            // Gigantamax handler removed: Gigantamax is not in the Delta pack, so the
            // message key never fires and the form swap had no source data to back it.

            // ═══════════════════════════════════════════════════════════════════
            // Type Modification Moves
            // ═══════════════════════════════════════════════════════════════════

            if (key in TranslationKeys.TYPE_CHANGE_KEYS && args.size >= 2) {
                val targetName = MessageParser.argToString(args[0])
                val newType = MessageParser.argToString(args[1])
                BattleStateTracker.setTypeReplacement(targetName, newType, null, null)
                CobblemonExtendedBattleUI.LOGGER.debug("BattleMessageInterceptor: $targetName type changed to $newType")
            }

            if (key in TranslationKeys.TYPE_ADD_KEYS && args.size >= 2) {
                val targetName = MessageParser.argToString(args[0])
                val addedType = MessageParser.argToString(args[1])
                BattleStateTracker.addType(targetName, addedType, null)
                CobblemonExtendedBattleUI.LOGGER.debug("BattleMessageInterceptor: $targetName gained $addedType type")
            }

            if (key in TranslationKeys.BURN_UP_KEYS && args.isNotEmpty()) {
                val pokemonName = MessageParser.argToString(args[0])
                CobblemonExtendedBattleUI.LOGGER.debug("BattleMessageInterceptor: Burn Up detected for '$pokemonName'")
                BattleStateTracker.loseType(pokemonName, "Fire", null)
            }

            if (key in TranslationKeys.DOUBLE_SHOCK_KEYS && args.isNotEmpty()) {
                val pokemonName = MessageParser.argToString(args[0])
                CobblemonExtendedBattleUI.LOGGER.debug("BattleMessageInterceptor: Double Shock detected for '$pokemonName'")
                BattleStateTracker.loseType(pokemonName, "Electric", null)
            }

            // ═══════════════════════════════════════════════════════════════════
            // Faint / Switch
            // ═══════════════════════════════════════════════════════════════════

            if (key == TranslationKeys.FAINT_KEY) {
                StateUpdater.markPokemonFainted(args)
                return
            }
            if (key == TranslationKeys.SWITCH_KEY || key == TranslationKeys.DRAG_KEY ||
                key == TranslationKeys.SENDOUT_KEY || key == TranslationKeys.REPLACE_KEY) {
                StateUpdater.clearPokemonState(args)
                return
            }
        }

        for (sibling in text.siblings) {
            processComponent(sibling)
        }
    }

    private fun isUsedMoveOnKey(key: String): Boolean {
        return key == USED_MOVE_ON_KEY || key.startsWith("$USED_MOVE_ON_KEY.")
    }

    private fun isUsedMoveKey(key: String): Boolean {
        return key == USED_MOVE_KEY || key.startsWith("$USED_MOVE_KEY.")
    }

    private fun matchesKeyVariant(key: String, baseKey: String): Boolean {
        return key == baseKey || key.startsWith("$baseKey.")
    }

    private fun matchesAnyVariant(key: String, baseKeys: Set<String>): Boolean {
        return baseKeys.any { matchesKeyVariant(key, it) }
    }

    private fun <T> mappedKeyLookup(key: String, mapping: Map<String, T>): T? {
        mapping[key]?.let { return it }
        return mapping.entries.firstOrNull { matchesKeyVariant(key, it.key) }?.value
    }

    /**
     * Ogerpon's Embody Aspect grants a passive +1 stat boost on switch-in,
     * varying by mask. Cobblemon emits the ability via ABILITY_GENERIC_KEY /
     * ABILITY_SINGLE_ARG_KEYS without a separate boost message, so the boost
     * has to be applied here. Strictly gated on species == ogerpon to prevent
     * Trace/Receiver copying onto a non-Ogerpon from triggering. clearPokemonState
     * zeros stages on switch-out, so seeing the slot already at +1 means the
     * ability message re-emitted within one switch — skip to avoid double-boost.
     */
    private fun maybeApplyEmbodyAspect(pokemonName: String, abilityName: String) {
        val normalized = abilityName.lowercase().replace(" ", "").replace("_", "").replace("-", "")
        if (normalized != "embodyaspect") return
        val speciesId = BattleStateTracker.getSpeciesIdByName(pokemonName)?.path ?: return
        if (!speciesId.startsWith("ogerpon", ignoreCase = true)) return

        val formName = BattleStateTracker.getCurrentFormByName(pokemonName)?.currentForm.orEmpty()
        val tokens = "$speciesId-$formName".lowercase()
        val stat = when {
            "hearthflame" in tokens -> BattleStateTracker.BattleStat.ATTACK
            "wellspring" in tokens -> BattleStateTracker.BattleStat.SPECIAL_DEFENSE
            "cornerstone" in tokens -> BattleStateTracker.BattleStat.DEFENSE
            else -> BattleStateTracker.BattleStat.SPEED
        }

        val current = BattleStateTracker.getStatChangesByName(pokemonName)[stat] ?: 0
        if (current >= 1) return

        BattleStateTracker.applyStatChange(pokemonName, stat, 1)
        CobblemonExtendedBattleUI.LOGGER.debug(
            "BattleMessageInterceptor: Embody Aspect on $pokemonName -> +1 ${stat.abbr}"
        )
    }

    /**
     * Whether this species actually has a Mega Evolution form. Used to filter spurious
     * `cobblemon.battle.formechange.mega` / `cobblemon.battle.mega` messages that some
     * Delta-server flows fire for non-mega Pokémon (e.g. Iron Valiant), which would
     * otherwise corrupt our form tracking and pull wrong base stats from a non-existent
     * Mega form.
     */
    private fun hasMegaForm(speciesId: Identifier): Boolean {
        val species = PokemonSpecies.getByIdentifier(speciesId) ?: return false
        val standard = species.standardForm
        val megaForm = runCatching { species.getForm(setOf("mega")) }.getOrNull()
        if (megaForm != null && megaForm != standard) return true
        // Charizard / Mewtwo have mega-x and mega-y variants.
        for (variant in listOf("mega-x", "mega-y", "megax", "megay")) {
            val form = runCatching { species.getForm(setOf(variant)) }.getOrNull()
            if (form != null && form != standard) return true
        }
        return false
    }
}
