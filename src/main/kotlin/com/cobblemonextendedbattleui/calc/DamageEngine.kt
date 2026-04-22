package com.cobblemonextendedbattleui.calc

import com.cobblemon.mod.common.api.moves.MoveTemplate
import com.cobblemon.mod.common.api.moves.Moves
import com.cobblemon.mod.common.api.moves.categories.DamageCategories
import com.cobblemon.mod.common.api.types.ElementalTypes
import com.cobblemon.mod.common.battles.ai.strongBattleAI.AIUtility
import com.cobblemonextendedbattleui.BattleMoveSupport
import com.cobblemonextendedbattleui.ItemPowerBoostParser
import com.cobblemonextendedbattleui.pokemon.stats.StatCalculator
import kotlin.math.floor
import kotlin.math.max

interface DamageEngine {
    fun compute(
        snapshot: CalcBattleSnapshot,
        playerSnapshot: CalcPokemonSnapshot?,
        opponentSnapshot: CalcPokemonSnapshot?,
        inferredSet: EffectiveBattleSet,
        playerEffectiveCurrentHp: Int,
        emphasizeSelectedMove: Boolean
    ): DamageComputationResult
}

object BestEffortDamageEngine : DamageEngine {
    private val neutralIvs = CalcStats(31, 31, 31, 31, 31, 31)
    private val zeroEvs = CalcStats(0, 0, 0, 0, 0, 0)

    override fun compute(
        snapshot: CalcBattleSnapshot,
        playerSnapshot: CalcPokemonSnapshot?,
        opponentSnapshot: CalcPokemonSnapshot?,
        inferredSet: EffectiveBattleSet,
        playerEffectiveCurrentHp: Int,
        emphasizeSelectedMove: Boolean
    ): DamageComputationResult {
        if (playerSnapshot == null || opponentSnapshot == null) {
            return DamageComputationResult(
                yourMoves = listOf(unsupportedEstimate("No active Pokemon")),
                opponentMoves = listOf(unsupportedEstimate("No active Pokemon")),
                warnings = listOf("Waiting for active battle state")
            )
        }

        val playerCombatant = buildCombatant(playerSnapshot)
        val playerDefenseSnapshot = playerSnapshot.copy(
            currentHp = playerEffectiveCurrentHp.coerceIn(0, playerSnapshot.maxHp)
        )
        val playerDefenseCombatant = buildCombatant(playerDefenseSnapshot)
        val opponentCombatant = buildCombatant(
            snapshot = opponentSnapshot,
            assumedItem = inferredSet.item.first,
            assumedItemState = inferredSet.item.second,
            assumedAbility = inferredSet.ability.first,
            assumedAbilityState = inferredSet.ability.second,
            assumedSpread = inferredSet.spread
        )

        val playerContext = DamageContext(snapshot.weather, snapshot.terrain, snapshot.playerSide, snapshot.opponentSide)
        val opponentContext = DamageContext(snapshot.weather, snapshot.terrain, snapshot.opponentSide, snapshot.playerSide)

        val yourMoves = playerSnapshot.moveList.take(4).map { moveRef ->
            estimateMove(
                moveId = moveRef.id,
                moveDisplayName = moveRef.displayName,
                attacker = playerCombatant,
                defender = opponentCombatant,
                context = playerContext,
                emphasize = emphasizeSelectedMove && normalizeToken(moveRef.id) == normalizeToken(snapshot.selectedMoveName)
            )
        }

        val visibleOpponentMoves = mergedOpponentMoves(opponentSnapshot.revealedMoves, inferredSet.moves)
        val opponentMoves = visibleOpponentMoves.take(4).map { moveSlot ->
            estimateMove(
                moveId = resolveMoveId(moveSlot.moveName),
                moveDisplayName = moveSlot.moveName,
                attacker = opponentCombatant,
                defender = playerDefenseCombatant,
                context = opponentContext,
                guessed = moveSlot.state == InferenceValueState.GUESSED
            )
        }

        val warnings = (yourMoves + opponentMoves)
            .flatMap { it.warnings }
            .distinct()
            .take(6)

        return DamageComputationResult(
            yourMoves = yourMoves,
            opponentMoves = opponentMoves,
            warnings = warnings
        )
    }

    private fun mergedOpponentMoves(
        revealedMoves: List<String>,
        inferredMoves: List<InferenceMoveSlot>
    ): List<InferenceMoveSlot> {
        val merged = LinkedHashMap<String, InferenceMoveSlot>()

        revealedMoves
            .distinctBy(::normalizeToken)
            .forEach { moveName ->
                merged[normalizeToken(moveName)] = InferenceMoveSlot(
                    moveName = moveName,
                    state = InferenceValueState.REVEALED,
                    confidenceLabel = "High"
                )
            }

        inferredMoves.forEach { moveSlot ->
            val key = normalizeToken(moveSlot.moveName)
            val existing = merged[key]
            if (existing == null) {
                merged[key] = moveSlot
            } else if (existing.state != InferenceValueState.REVEALED && moveSlot.state == InferenceValueState.REVEALED) {
                merged[key] = moveSlot
            }
        }

        return merged.values.toList()
    }

    private fun buildCombatant(
        snapshot: CalcPokemonSnapshot,
        assumedItem: String? = null,
        assumedItemState: InferenceValueState = InferenceValueState.UNKNOWN,
        assumedAbility: String? = null,
        assumedAbilityState: InferenceValueState = InferenceValueState.UNKNOWN,
        assumedSpread: InferenceSpread? = null
    ): DamageCombatant {
        val blockingIssues = mutableListOf<String>()
        val assumptionWarnings = mutableListOf<String>()

        if (snapshot.typeNames.isEmpty()) {
            blockingIssues += "Need typing"
        }

        val assumptions = if (snapshot.actualStats != null) {
            CalcStatAssumptions(
                baseStats = snapshot.baseStats,
                ivs = neutralIvs,
                evs = zeroEvs,
                nature = "Actual",
                derivedStats = snapshot.actualStats
            )
        } else if (snapshot.baseStats == null) {
            blockingIssues += "Need base stats"
            null
        } else if (assumedSpread != null) {
            assumptionWarnings += if (assumedSpread.state == InferenceValueState.REVEALED) {
                "Revealed spread"
            } else {
                "Inferred spread"
            }
            CalcStatAssumptions(
                baseStats = snapshot.baseStats,
                ivs = neutralIvs,
                evs = assumedSpread.evs,
                nature = assumedSpread.nature,
                derivedStats = deriveStats(snapshot.baseStats, snapshot.level, neutralIvs, assumedSpread.evs, assumedSpread.nature)
            )
        } else {
            blockingIssues += if (snapshot.moveList.isEmpty()) "Need spread" else "Need actual stats"
            null
        }

        val resolvedItem = snapshot.itemName ?: assumedItem
        if (snapshot.itemName == null && assumedItem != null) {
            assumptionWarnings += if (assumedItemState == InferenceValueState.REVEALED) "Revealed item" else "Inferred item"
        } else if (resolvedItem == null) {
            assumptionWarnings += "Unknown item"
        }
        val resolvedAbility = snapshot.abilityName ?: assumedAbility
        if (snapshot.abilityName == null && assumedAbility != null) {
            assumptionWarnings += if (assumedAbilityState == InferenceValueState.REVEALED) "Revealed ability" else "Inferred ability"
        } else if (resolvedAbility == null) {
            assumptionWarnings += "Unknown ability"
        }

        return DamageCombatant(
            snapshot = snapshot,
            itemName = resolvedItem,
            abilityName = resolvedAbility,
            assumptions = assumptions,
            assumptionWarnings = assumptionWarnings.distinct(),
            blockingIssues = blockingIssues.distinct()
        )
    }

    private fun estimateMove(
        moveId: String,
        moveDisplayName: String,
        attacker: DamageCombatant,
        defender: DamageCombatant,
        context: DamageContext,
        guessed: Boolean = false,
        emphasize: Boolean = false
    ): DamageEstimate {
        val resolvedDisplayName = BattleMoveSupport.resolveDisplayName(moveDisplayName)
        val template = resolveMoveTemplate(moveId, resolvedDisplayName)
            ?: return unsupportedEstimate(resolvedDisplayName, "Unsupported move data", guessed, emphasize)

        val blockingIssues = buildList {
            addAll(attacker.blockingIssues.map { "Attacker: $it" })
            addAll(defender.blockingIssues.map { "Defender: $it" })
        }
        if (blockingIssues.isNotEmpty()) {
            return unsupportedEstimate(resolvedDisplayName, blockingIssues.first(), guessed, emphasize)
        }

        val category = effectiveCategory(template, attacker)
        if (category == DamageCategories.STATUS) {
            return unsupportedEstimate(resolvedDisplayName, "Status move", guessed, emphasize)
        }

        val moveTypeName = resolveMoveTypeName(template, attacker, context)
        val moveType = ElementalTypes.get(moveTypeName)
            ?: return unsupportedEstimate(resolvedDisplayName, "Unknown move type", guessed, emphasize)

        val warnings = mutableListOf<String>()
        warnings += attacker.assumptionWarnings
        warnings += defender.assumptionWarnings
        if (guessed) {
            warnings += "Guessed opponent move"
        }

        val power = effectivePower(template, attacker, defender, context)
        if (power <= 0) {
            return unsupportedEstimate(resolvedDisplayName, "No damaging power", guessed, emphasize)
        }

        val defenderMaxHp = resolvedMaxHp(defender)
        val defenderCurrentHp = resolvedCurrentHp(defender)

        var effectiveness = defender.snapshot.typeNames
            .mapNotNull { ElementalTypes.get(it.lowercase()) }
            .fold(1.0) { acc, defendingType -> acc * AIUtility.getDamageMultiplier(moveType, defendingType) }

        effectiveness *= abilityTypeModifier(defender.abilityName, moveTypeName)
        if (effectiveness == 0.0) {
            return DamageEstimate(
                moveId = moveId,
                moveName = resolvedDisplayName,
                minDamage = 0,
                maxDamage = 0,
                minPercent = 0.0,
                maxPercent = 0.0,
                koLabel = "Immune",
                confidence = confidenceFor(attacker, defender, guessed, warnings, blockingIssues),
                warnings = warnings,
                emphasized = emphasize
            )
        }

        val attackStat = effectiveAttackStat(attacker, template, category)
        val defenseStat = effectiveDefenseStat(defender, category, context)
        if (attackStat <= 0 || defenseStat <= 0) {
            return unsupportedEstimate(resolvedDisplayName, "Incomplete stats", guessed, emphasize)
        }

        val baseDamage = (((((2.0 * attacker.snapshot.level) / 5.0) + 2.0) * power * attackStat / defenseStat) / 50.0) + 2.0
        var modifier = stabMultiplier(attacker, moveTypeName)
        modifier *= effectiveness
        modifier *= weatherModifier(moveTypeName, context)
        modifier *= terrainModifier(moveTypeName, template, attacker, defender, context)
        modifier *= screenModifier(defender, category, context)
        modifier *= burnModifier(attacker, category)
        modifier *= offensiveAbilityModifier(attacker, moveTypeName, category, context, power)
        modifier *= defensiveAbilityModifier(defender, moveTypeName, category, effectiveness)
        modifier *= itemPowerModifier(attacker, moveTypeName)

        val minDamage = max(1, floor(baseDamage * modifier * 0.85).toInt())
        val maxDamage = max(1, floor(baseDamage * modifier).toInt())
        val minPercent = damagePercent(minDamage, defenderMaxHp)
        val maxPercent = damagePercent(maxDamage, defenderMaxHp)
        val koLabel = koLabel(defenderCurrentHp, minDamage, maxDamage)

        return DamageEstimate(
            moveId = moveId,
            moveName = resolvedDisplayName,
            minDamage = minDamage,
            maxDamage = maxDamage,
            minPercent = minPercent,
            maxPercent = maxPercent,
            koLabel = koLabel,
            confidence = confidenceFor(attacker, defender, guessed, warnings, blockingIssues),
            warnings = warnings,
            emphasized = emphasize
        )
    }

    private fun resolveMoveTemplate(moveId: String, displayName: String): MoveTemplate? {
        return (BattleMoveSupport.moveLookupCandidates(moveId) + BattleMoveSupport.moveLookupCandidates(displayName))
            .distinct()
            .firstNotNullOfOrNull { candidate -> Moves.getByName(candidate) }
    }

    private fun resolveMoveId(displayName: String): String = BattleMoveSupport.resolveMoveId(displayName)

    private fun deriveStats(base: CalcStats, level: Int, ivs: CalcStats, evs: CalcStats, nature: String): CalcStats {
        val hp = calculateHp(base.hp, level, ivs.hp, evs.hp)
        val natureMods = natureModifiers(nature)
        return CalcStats(
            hp = hp,
            atk = StatCalculator.calculateStat(base.atk, level, ivs.atk, evs.atk, natureMods["atk"] ?: 1.0),
            def = StatCalculator.calculateStat(base.def, level, ivs.def, evs.def, natureMods["def"] ?: 1.0),
            spa = StatCalculator.calculateStat(base.spa, level, ivs.spa, evs.spa, natureMods["spa"] ?: 1.0),
            spd = StatCalculator.calculateStat(base.spd, level, ivs.spd, evs.spd, natureMods["spd"] ?: 1.0),
            spe = StatCalculator.calculateStat(base.spe, level, ivs.spe, evs.spe, natureMods["spe"] ?: 1.0)
        )
    }

    private fun calculateHp(base: Int, level: Int, iv: Int, ev: Int): Int {
        return (((2 * base + iv + ev / 4) * level) / 100) + level + 10
    }

    private fun effectiveCategory(template: MoveTemplate, attacker: DamageCombatant) =
        if (normalizeToken(template.name) == "terablast" &&
            attacker.assumptions != null &&
            attacker.assumptions.derivedStats.atk > attacker.assumptions.derivedStats.spa) {
            DamageCategories.PHYSICAL
        } else {
            template.damageCategory
        }

    private fun natureModifiers(nature: String): Map<String, Double> {
        val neutral = mapOf("atk" to 1.0, "def" to 1.0, "spa" to 1.0, "spd" to 1.0, "spe" to 1.0)
        val mapping = mapOf(
            "lonely" to ("atk" to "def"),
            "brave" to ("atk" to "spe"),
            "adamant" to ("atk" to "spa"),
            "naughty" to ("atk" to "spd"),
            "bold" to ("def" to "atk"),
            "relaxed" to ("def" to "spe"),
            "impish" to ("def" to "spa"),
            "lax" to ("def" to "spd"),
            "timid" to ("spe" to "atk"),
            "hasty" to ("spe" to "def"),
            "jolly" to ("spe" to "spa"),
            "naive" to ("spe" to "spd"),
            "modest" to ("spa" to "atk"),
            "mild" to ("spa" to "def"),
            "quiet" to ("spa" to "spe"),
            "rash" to ("spa" to "spd"),
            "calm" to ("spd" to "atk"),
            "gentle" to ("spd" to "def"),
            "sassy" to ("spd" to "spe"),
            "careful" to ("spd" to "spa")
        )
        val adjustment = mapping[normalizeToken(nature)] ?: return neutral
        return neutral.toMutableMap().apply {
            this[adjustment.first] = 1.1
            this[adjustment.second] = 0.9
        }
    }

    private fun resolveMoveTypeName(template: MoveTemplate, attacker: DamageCombatant, context: DamageContext): String {
        BattleMoveSupport.resolveIvyCudgelTypeName(
            moveIdOrName = template.name,
            speciesId = attacker.snapshot.speciesId ?: attacker.snapshot.speciesKey,
            formName = attacker.snapshot.formName,
            heldItemName = attacker.itemName,
            heldItemId = attacker.itemName,
            pokemonName = attacker.snapshot.speciesLabel
        )?.let { return it }

        return when (normalizeToken(template.name)) {
            "weatherball" -> when (normalizeToken(context.weather)) {
                "rain" -> "water"
                "harshsunlight", "sun", "sunlight" -> "fire"
                "sandstorm" -> "rock"
                "hail", "snow" -> "ice"
                else -> template.elementalType.name
            }
            "terablast" -> attacker.snapshot.teraType ?: template.elementalType.name
            else -> template.elementalType.name
        }
    }

    private fun effectivePower(
        template: MoveTemplate,
        attacker: DamageCombatant,
        defender: DamageCombatant,
        context: DamageContext
    ): Double {
        return when (normalizeToken(template.name)) {
            "hex" -> if (defender.snapshot.status != null) template.power * 2.0 else template.power
            "weatherball" -> {
                if (normalizeToken(context.weather) in setOf("rain", "harshsunlight", "sun", "sunlight", "sandstorm", "hail", "snow")) {
                    template.power * 2.0
                } else {
                    template.power
                }
            }
            else -> template.power
        }
    }

    private fun effectiveAttackStat(
        attacker: DamageCombatant,
        template: MoveTemplate,
        category: com.cobblemon.mod.common.api.moves.categories.DamageCategory
    ): Int {
        val assumptions = attacker.assumptions ?: return 0
        val baseAttack = when {
            normalizeToken(template.name) == "bodypress" -> assumptions.derivedStats.def
            category == DamageCategories.PHYSICAL -> assumptions.derivedStats.atk
            else -> assumptions.derivedStats.spa
        }
        val stage = when {
            normalizeToken(template.name) == "bodypress" -> attacker.snapshot.stageFor("def")
            category == DamageCategories.PHYSICAL -> attacker.snapshot.stageFor("atk")
            else -> attacker.snapshot.stageFor("spa")
        }
        var value = StatCalculator.applyStageMultiplier(baseAttack, stage)
        value = when (category) {
            DamageCategories.PHYSICAL -> (value * StatCalculator.getItemAttackMultiplier(attacker.itemName, attacker.snapshot.speciesId)).toInt()
            DamageCategories.SPECIAL -> (value * StatCalculator.getItemSpecialAttackMultiplier(attacker.itemName, attacker.snapshot.speciesId)).toInt()
            else -> value
        }
        return max(1, value)
    }

    private fun effectiveDefenseStat(
        defender: DamageCombatant,
        category: com.cobblemon.mod.common.api.moves.categories.DamageCategory,
        context: DamageContext
    ): Int {
        val assumptions = defender.assumptions ?: return 0
        val stage = if (category == DamageCategories.PHYSICAL) defender.snapshot.stageFor("def") else defender.snapshot.stageFor("spd")
        var value = if (category == DamageCategories.PHYSICAL) {
            StatCalculator.applyStageMultiplier(assumptions.derivedStats.def, stage)
        } else {
            StatCalculator.applyStageMultiplier(assumptions.derivedStats.spd, stage)
        }

        value = if (category == DamageCategories.PHYSICAL) {
            (value * StatCalculator.getItemDefenseMultiplier(defender.itemName, defender.snapshot.canEvolve)).toInt()
        } else {
            (value * StatCalculator.getItemSpecialDefenseMultiplier(defender.itemName, defender.snapshot.speciesId, defender.snapshot.canEvolve)).toInt()
        }

        if (category == DamageCategories.SPECIAL &&
            normalizeToken(context.weather) == "sandstorm" &&
            defender.snapshot.hasType("rock")
        ) {
            value = (value * 1.5).toInt()
        }
        if (category == DamageCategories.PHYSICAL &&
            normalizeToken(context.weather) == "snow" &&
            defender.snapshot.hasType("ice")
        ) {
            value = (value * 1.5).toInt()
        }

        return max(1, value)
    }

    private fun stabMultiplier(attacker: DamageCombatant, moveTypeName: String): Double {
        if (!attacker.snapshot.hasType(moveTypeName)) return 1.0
        return if (normalizeToken(attacker.abilityName) == "adaptability") 2.0 else 1.5
    }

    private fun weatherModifier(
        moveTypeName: String,
        context: DamageContext
    ): Double {
        return when (normalizeToken(context.weather)) {
            "harshsunlight", "sun", "sunlight" -> when (normalizeToken(moveTypeName)) {
                "fire" -> 1.5
                "water" -> 0.5
                else -> 1.0
            }
            "rain" -> when (normalizeToken(moveTypeName)) {
                "water" -> 1.5
                "fire" -> 0.5
                else -> 1.0
            }
            else -> 1.0
        }
    }

    private fun terrainModifier(
        moveTypeName: String,
        template: MoveTemplate,
        attacker: DamageCombatant,
        defender: DamageCombatant,
        context: DamageContext
    ): Double {
        val terrain = normalizeToken(context.terrain)
        if (terrain.isBlank()) return 1.0
        val attackerGrounded = isGrounded(attacker)
        val defenderGrounded = isGrounded(defender)

        return when (terrain) {
            "electricterrain" -> if (attackerGrounded && normalizeToken(moveTypeName) == "electric") 1.3 else 1.0
            "psychicterrain" -> if (attackerGrounded && normalizeToken(moveTypeName) == "psychic") 1.3 else 1.0
            "grassyterrain" -> when {
                attackerGrounded && normalizeToken(moveTypeName) == "grass" -> 1.3
                normalizeToken(template.name) in setOf("earthquake", "bulldoze", "magnitude") && defenderGrounded -> 0.5
                else -> 1.0
            }
            "mistyterrain" -> if (defenderGrounded && normalizeToken(moveTypeName) == "dragon") 0.5 else 1.0
            else -> 1.0
        }
    }

    private fun screenModifier(
        defender: DamageCombatant,
        category: com.cobblemon.mod.common.api.moves.categories.DamageCategory,
        context: DamageContext
    ): Double {
        if (context.defenderSide.hasCondition("Aurora Veil")) return 0.5
        if (category == DamageCategories.PHYSICAL && context.defenderSide.hasCondition("Reflect")) return 0.5
        if (category == DamageCategories.SPECIAL && context.defenderSide.hasCondition("Light Screen")) return 0.5
        return 1.0
    }

    private fun burnModifier(attacker: DamageCombatant, category: com.cobblemon.mod.common.api.moves.categories.DamageCategory): Double {
        if (category != DamageCategories.PHYSICAL) return 1.0
        if (normalizeToken(attacker.snapshot.status) != "burn") return 1.0
        return if (normalizeToken(attacker.abilityName) == "guts") 1.0 else 0.5
    }

    private fun offensiveAbilityModifier(
        attacker: DamageCombatant,
        moveTypeName: String,
        category: com.cobblemon.mod.common.api.moves.categories.DamageCategory,
        context: DamageContext,
        power: Double
    ): Double {
        return when (normalizeToken(attacker.abilityName)) {
            "hugepower", "purepower" -> if (category == DamageCategories.PHYSICAL) 2.0 else 1.0
            "technician" -> if (power > 0.0 && power <= 60.0) 1.5 else 1.0
            "guts" -> if (category == DamageCategories.PHYSICAL && attacker.snapshot.status != null) 1.5 else 1.0
            "solarpower" -> if (category == DamageCategories.SPECIAL && normalizeToken(context.weather) in setOf("harshsunlight", "sun", "sunlight")) 1.5 else 1.0
            "flareboost" -> if (category == DamageCategories.SPECIAL && normalizeToken(attacker.snapshot.status) == "burn") 1.5 else 1.0
            "toxicboost" -> if (category == DamageCategories.PHYSICAL && normalizeToken(attacker.snapshot.status) in setOf("poison", "poisonbadly", "badpoison")) 1.5 else 1.0
            "blaze" -> if (normalizeToken(moveTypeName) == "fire" && isLowHp(attacker)) 1.5 else 1.0
            "torrent" -> if (normalizeToken(moveTypeName) == "water" && isLowHp(attacker)) 1.5 else 1.0
            "overgrow" -> if (normalizeToken(moveTypeName) == "grass" && isLowHp(attacker)) 1.5 else 1.0
            "swarm" -> if (normalizeToken(moveTypeName) == "bug" && isLowHp(attacker)) 1.5 else 1.0
            else -> 1.0
        }
    }

    private fun defensiveAbilityModifier(
        defender: DamageCombatant,
        moveTypeName: String,
        category: com.cobblemon.mod.common.api.moves.categories.DamageCategory,
        effectiveness: Double
    ): Double {
        return when (normalizeToken(defender.abilityName)) {
            "thickfat" -> if (normalizeToken(moveTypeName) in setOf("fire", "ice")) 0.5 else 1.0
            "heatproof" -> if (normalizeToken(moveTypeName) == "fire") 0.5 else 1.0
            "furcoat" -> if (category == DamageCategories.PHYSICAL) 0.5 else 1.0
            "marvelscale" -> if (category == DamageCategories.PHYSICAL && defender.snapshot.status != null) 2.0 / 3.0 else 1.0
            "filter", "solidrock", "prismarmor" -> if (effectiveness > 1.0) 0.75 else 1.0
            else -> 1.0
        }
    }

    private fun abilityTypeModifier(defenderAbilityName: String?, moveTypeName: String): Double {
        return when (normalizeToken(defenderAbilityName)) {
            "levitate", "eartheater" -> if (normalizeToken(moveTypeName) == "ground") 0.0 else 1.0
            "waterabsorb", "stormdrain", "dryskin" -> if (normalizeToken(moveTypeName) == "water") 0.0 else 1.0
            "flashfire", "wellbakedbody" -> if (normalizeToken(moveTypeName) == "fire") 0.0 else 1.0
            "voltabsorb", "lightningrod", "motordrive" -> if (normalizeToken(moveTypeName) == "electric") 0.0 else 1.0
            "sapsipper" -> if (normalizeToken(moveTypeName) == "grass") 0.0 else 1.0
            else -> 1.0
        }
    }

    private fun itemPowerModifier(attacker: DamageCombatant, moveTypeName: String): Double {
        val itemId = normalizeToken(attacker.itemName)
        val itemBoost = ItemPowerBoostParser.getBoostForItem(itemId) ?: return 1.0
        if (itemBoost.boostedType == null) return itemBoost.multiplier
        return if (normalizeToken(itemBoost.boostedType) == normalizeToken(moveTypeName)) itemBoost.multiplier else 1.0
    }

    private fun isGrounded(combatant: DamageCombatant): Boolean {
        if (combatant.snapshot.hasType("flying")) return false
        return normalizeToken(combatant.abilityName) != "levitate"
    }

    private fun isLowHp(combatant: DamageCombatant): Boolean {
        return resolvedCurrentHp(combatant) * 3 <= max(1, resolvedMaxHp(combatant))
    }

    private fun damagePercent(damage: Int, defenderMaxHp: Int): Double {
        if (defenderMaxHp <= 0) return 0.0
        return damage * 100.0 / defenderMaxHp.toDouble()
    }

    private fun resolvedMaxHp(combatant: DamageCombatant): Int {
        if (combatant.snapshot.exactHpValues) {
            return combatant.snapshot.maxHp
        }
        return combatant.assumptions?.derivedStats?.hp ?: combatant.snapshot.maxHp
    }

    private fun resolvedCurrentHp(combatant: DamageCombatant): Int {
        if (combatant.snapshot.exactHpValues) {
            return combatant.snapshot.currentHp
        }
        val maxHp = resolvedMaxHp(combatant)
        return (maxHp * combatant.snapshot.currentHp / 100.0).toInt().coerceIn(0, maxHp)
    }

    private fun koLabel(currentHp: Int, minDamage: Int, maxDamage: Int): String {
        if (currentHp <= 0) return "KO"
        if (minDamage >= currentHp) return "OHKO"
        if (maxDamage >= currentHp) return "Likely OHKO"
        if (maxDamage * 2 >= currentHp) return "2HKO"
        if (maxDamage * 3 >= currentHp) return "3HKO"
        return "4HKO+"
    }

    private fun confidenceFor(
        attacker: DamageCombatant,
        defender: DamageCombatant,
        guessed: Boolean,
        warnings: List<String>,
        blockingIssues: List<String>
    ): DamageConfidence {
        return when {
            blockingIssues.isNotEmpty() -> DamageConfidence.LOW
            guessed -> DamageConfidence.LOW
            warnings.any { it.startsWith("Unknown", ignoreCase = true) } -> DamageConfidence.LOW
            warnings.any { it.startsWith("Inferred", ignoreCase = true) } -> DamageConfidence.MEDIUM
            attacker.snapshot.actualStats == null || defender.snapshot.actualStats == null -> DamageConfidence.MEDIUM
            else -> DamageConfidence.HIGH
        }
    }

    private fun unsupportedEstimate(
        moveName: String,
        warning: String = "Unsupported",
        guessed: Boolean = false,
        emphasized: Boolean = false
    ): DamageEstimate {
        val warnings = buildList {
            add(warning)
            if (guessed) add("Guessed opponent move")
        }
        return DamageEstimate(
            moveId = normalizeToken(moveName),
            moveName = moveName,
            minDamage = null,
            maxDamage = null,
            minPercent = null,
            maxPercent = null,
            koLabel = "--",
            confidence = DamageConfidence.LOW,
            warnings = warnings,
            supported = false,
            emphasized = emphasized
        )
    }

    private data class DamageCombatant(
        val snapshot: CalcPokemonSnapshot,
        val itemName: String?,
        val abilityName: String?,
        val assumptions: CalcStatAssumptions?,
        val assumptionWarnings: List<String>,
        val blockingIssues: List<String>
    )

    private data class DamageContext(
        val weather: String?,
        val terrain: String?,
        val attackerSide: CalcSideState,
        val defenderSide: CalcSideState
    )
}
