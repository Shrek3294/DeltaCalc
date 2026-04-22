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

    private data class MultiHitInfo(val minHits: Int, val maxHits: Int)

    private val multiHitMoves: Map<String, MultiHitInfo> = mapOf(
        "bulletseed" to MultiHitInfo(2, 5),
        "rockblast" to MultiHitInfo(2, 5),
        "iciclespear" to MultiHitInfo(2, 5),
        "pinmissile" to MultiHitInfo(2, 5),
        "armthrust" to MultiHitInfo(2, 5),
        "tailslap" to MultiHitInfo(2, 5),
        "furyswipes" to MultiHitInfo(2, 5),
        "furyattack" to MultiHitInfo(2, 5),
        "cometpunch" to MultiHitInfo(2, 5),
        "doubleslap" to MultiHitInfo(2, 5),
        "spikecannon" to MultiHitInfo(2, 5),
        "barrage" to MultiHitInfo(2, 5),
        "scaleshot" to MultiHitInfo(2, 5),
        "watershuriken" to MultiHitInfo(2, 5),
        "doublekick" to MultiHitInfo(2, 2),
        "doubleironbash" to MultiHitInfo(2, 2),
        "doublehit" to MultiHitInfo(2, 2),
        "bonemerang" to MultiHitInfo(2, 2),
        "twineedle" to MultiHitInfo(2, 2),
        "dualchop" to MultiHitInfo(2, 2),
        "geargrind" to MultiHitInfo(2, 2),
        "dragondarts" to MultiHitInfo(2, 2),
        "tripleaxel" to MultiHitInfo(3, 3),
        "triplekick" to MultiHitInfo(3, 3),
        "surgingstrikes" to MultiHitInfo(3, 3),
        "populationbomb" to MultiHitInfo(1, 10),
        // Delta-specific multi-hit signature moves (sourced from the Delta
        // team-builder dump). Keys normalized via normalizeToken.
        "twincross" to MultiHitInfo(2, 2),       // Draculedge: Dragon Phys 50 BP
        "lumencascade" to MultiHitInfo(2, 2),    // Normal Spec 50 BP
        "searingclaws" to MultiHitInfo(2, 2),    // Fire Phys 35 BP
        "dualdivide" to MultiHitInfo(2, 2),      // Bug Phys 40 BP
        "tomahawkvolley" to MultiHitInfo(2, 5),  // Fire Phys 20 BP
        "wretchedstab" to MultiHitInfo(2, 5),    // Ghost Phys 20 BP
        "divinevolley" to MultiHitInfo(1, 6),    // Fighting Phys 20 BP
        "quillstorm" to MultiHitInfo(1, 3)       // Fire Phys; per-hit BP escalates 20/40/60
    )

    private fun multiHitFor(template: MoveTemplate): MultiHitInfo? {
        return multiHitMoves[normalizeToken(template.name)]
    }

    // Move tag sets used by ability triggers (Reap, Torque Step, etc.).
    // Keys normalized via normalizeToken.
    private val slicingMoves: Set<String> = setOf(
        "aerialace", "aircutter", "airslash", "behemothblade", "bitterblade",
        "ceaselessedge", "crosspoison", "cut", "furycutter", "kowtowcleave",
        "leafblade", "nightslash", "populationbomb", "psyblade", "psychocut",
        "razorleaf", "razorshell", "sacredsword", "secretsword", "slash",
        "solarblade", "stoneaxe", "stormthrow", "xscissor",
        "dualdivide" // Delta
    )

    private val kickingMoves: Set<String> = setOf(
        "axekick", "blazekick", "doublekick", "highhorsepower", "highjumpkick",
        "jumpkick", "lowkick", "lowsweep", "megakick", "pyroball",
        "rollingkick", "stomp", "stompingtantrum", "thunderouskick",
        "tripleaxel", "triplekick", "tropkick"
    )

    private fun isSlicing(template: MoveTemplate): Boolean =
        normalizeToken(template.name) in slicingMoves

    private fun isKicking(template: MoveTemplate): Boolean =
        normalizeToken(template.name) in kickingMoves

    private fun deltaGuaranteedCritMultiplier(
        attacker: DamageCombatant,
        template: MoveTemplate
    ): Double {
        val ability = normalizeToken(attacker.abilityName)
        val guaranteedCrit = when (ability) {
            "reap" -> isSlicing(template)
            else -> false
        }
        return if (guaranteedCrit) 1.5 else 1.0
    }

    private fun deltaOffensiveAbilityModifier(
        attacker: DamageCombatant,
        moveTypeName: String,
        template: MoveTemplate,
        effectiveness: Double
    ): Double {
        val ability = normalizeToken(attacker.abilityName)
        val type = normalizeToken(moveTypeName)
        return when (ability) {
            "flurry" -> if (type == "ice" && isLowHp(attacker)) 1.5 else 1.0
            "draconic" -> if (type == "dragon" && isLowHp(attacker)) 1.5 else 1.0
            "pyroclastic" -> if (type == "rock") 1.3 else 1.0
            "stoneheart" -> if (type == "rock") 1.5 else 1.0
            "valorheart" -> 1.2
            "torquestep" -> if (isKicking(template)) 1.3 else 1.0
            "conviction" -> if (effectiveness > 1.0) 1.25 else 1.0
            else -> 1.0
        }
    }

    private fun deltaDefensiveAbilityModifier(
        defender: DamageCombatant,
        moveTypeName: String
    ): Double {
        val ability = normalizeToken(defender.abilityName)
        val type = normalizeToken(moveTypeName)
        return when (ability) {
            "igneous" -> if (type == "fire" || type == "water") 0.5 else 1.0
            else -> 1.0
        }
    }

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

        val category = effectiveCategory(template, attacker, defender)
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

        val defenderMaxHp = resolvedMaxHp(defender)
        val defenderCurrentHp = resolvedCurrentHp(defender)
        val defenderAtFullHp = defenderMaxHp > 0 && defenderCurrentHp >= defenderMaxHp

        fixedDamageOverride(template, attacker, defender, defenderMaxHp)?.let { fixed ->
            return DamageEstimate(
                moveId = moveId,
                moveName = moveDisplayName,
                minDamage = fixed,
                maxDamage = fixed,
                minPercent = damagePercent(fixed, defenderMaxHp),
                maxPercent = damagePercent(fixed, defenderMaxHp),
                koLabel = koLabel(defenderCurrentHp, fixed, fixed),
                confidence = confidenceFor(attacker, defender, guessed, warnings, blockingIssues),
                warnings = warnings,
                emphasized = emphasize
            )
        }

        val power = effectivePower(template, attacker, defender, context)
        if (power <= 0) {
            return unsupportedEstimate(resolvedDisplayName, "No damaging power", guessed, emphasize)
        }

        val bypassAbility = bypassesDefenderAbility(attacker.abilityName) || moveBypassesDefenderAbility(template)
        var effectiveness = defender.snapshot.typeNames
            .mapNotNull { ElementalTypes.get(it.lowercase()) }
            .fold(1.0) { acc, defendingType -> acc * AIUtility.getDamageMultiplier(moveType, defendingType) }

        if (!bypassAbility) {
            effectiveness *= abilityTypeModifier(defender.abilityName, moveTypeName)
        }
        if (wonderGuardBlocks(defender, attacker.abilityName, effectiveness)) {
            effectiveness = 0.0
        }
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

        val attackStat = effectiveAttackStat(attacker, defender, template, category)
        val defenseStat = effectiveDefenseStat(defender, category, context)
        if (attackStat <= 0 || defenseStat <= 0) {
            return unsupportedEstimate(resolvedDisplayName, "Incomplete stats", guessed, emphasize)
        }

        val levelFactor = ((2.0 * attacker.snapshot.level) / 5.0) + 2.0
        val baseDamage = ((levelFactor * power * attackStat / defenseStat) / 50.0) + 2.0
        val critAttackStat = effectiveAttackStat(attacker, defender, template, category, isCrit = true)
        val critDefenseStat = effectiveDefenseStat(defender, category, context, isCrit = true)
        val critBaseDamage = ((levelFactor * power * critAttackStat / critDefenseStat) / 50.0) + 2.0

        val screenFactor = screenModifier(defender, category, context)
        val defensiveAbilityFactor = if (bypassAbility) 1.0 else defensiveAbilityModifier(defender, template, moveTypeName, category, effectiveness, defenderAtFullHp)
        var modifier = stabMultiplier(attacker, moveTypeName)
        modifier *= effectiveness
        modifier *= weatherModifier(moveTypeName, context)
        modifier *= terrainModifier(moveTypeName, template, attacker, defender, context)
        modifier *= screenFactor
        modifier *= burnModifier(attacker, category)
        modifier *= offensiveAbilityModifier(attacker, template, moveTypeName, category, context, effectiveness)
        modifier *= defensiveAbilityFactor
        modifier *= deltaOffensiveAbilityModifier(attacker, moveTypeName, template, effectiveness)
        modifier *= deltaDefensiveAbilityModifier(defender, moveTypeName)
        modifier *= itemPowerModifier(attacker, moveTypeName, category, effectiveness)

        val guaranteedCrit = deltaGuaranteedCritMultiplier(attacker, template)
        val normalModifier = modifier * guaranteedCrit
        if (guaranteedCrit > 1.0) {
            warnings += "Guaranteed crit (${attacker.abilityName})"
        }

        val damageRolls = damageRolls(baseDamage, normalModifier)
        val critMultiplier = critMultiplier(attacker)
        val screenBypass = if (screenFactor > 0.0) 1.0 / screenFactor else 1.0
        val critModifier = modifier * screenBypass * critMultiplier
        val critDamageRolls = damageRolls(critBaseDamage, critModifier)

        // Hardcoded Delta/signature multi-hit table wins over the Showdown flag DB
        // (flag DB doesn't know Delta-only moves like Twin Cross, Searing Claws, etc.)
        val flagEntry = MoveFlagDatabase.get(template.name)
        val hardcodedMultiHit = multiHitFor(template)
        val minHits = hardcodedMultiHit?.minHits ?: max(1, flagEntry?.multihitMin ?: 1)
        val maxHits = hardcodedMultiHit?.maxHits ?: max(minHits, flagEntry?.multihitMax ?: minHits)
        if (maxHits > 1) {
            val hitLabel = if (minHits == maxHits) "$minHits hits" else "$minHits-$maxHits hits"
            warnings += "Multi-hit ($hitLabel)"
        }

        val minDamage = damageRolls.first() * minHits
        val maxDamage = damageRolls.last() * maxHits
        val critMinDamage = critDamageRolls.first() * minHits
        val critMaxDamage = critDamageRolls.last() * maxHits
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
            emphasized = emphasize,
            critMinDamage = critMinDamage,
            critMaxDamage = critMaxDamage,
            critMinPercent = damagePercent(critMinDamage, defenderMaxHp),
            critMaxPercent = damagePercent(critMaxDamage, defenderMaxHp),
            damageRolls = damageRolls,
            critDamageRolls = critDamageRolls,
            minHits = minHits,
            maxHits = maxHits
        )
    }

    private fun damageRolls(baseDamage: Double, modifier: Double): List<Int> {
        return (85..100).map { roll ->
            max(1, floor(baseDamage * modifier * roll / 100.0).toInt())
        }
    }

    private fun critMultiplier(attacker: DamageCombatant): Double {
        return when (normalizeToken(attacker.abilityName)) {
            "sniper" -> 2.25
            else -> 1.5
        }
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

    private fun effectiveCategory(template: MoveTemplate, attacker: DamageCombatant, defender: DamageCombatant): com.cobblemon.mod.common.api.moves.categories.DamageCategory {
        val moveName = normalizeToken(template.name)
        if (moveName !in SPLIT_CATEGORY_MOVES) return template.damageCategory
        if (attacker.assumptions == null) return template.damageCategory
        val effectivePhysical = effectiveAttackStat(attacker, defender, template, DamageCategories.PHYSICAL)
        val effectiveSpecial = effectiveAttackStat(attacker, defender, template, DamageCategories.SPECIAL)
        return if (effectivePhysical > effectiveSpecial) DamageCategories.PHYSICAL else DamageCategories.SPECIAL
    }

    private val SPLIT_CATEGORY_MOVES = setOf("terablast", "photongeyser", "lightthatburnsthesky")

    private val MOVES_THAT_BYPASS_ABILITY = setOf(
        "photongeyser",
        "lightthatburnsthesky",
        "sunsteelstrike",
        "moongeistbeam",
        "gmaxdrumsolo",
        "gmaxfireball",
        "gmaxhydrosnipe"
    )

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

        val baseType = when (normalizeToken(template.name)) {
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
        return applyTypeChangingAbility(baseType, attacker)
    }

    private fun applyTypeChangingAbility(baseType: String, attacker: DamageCombatant): String {
        if (!baseType.equals("normal", ignoreCase = true)) return baseType
        return when (normalizeToken(attacker.abilityName)) {
            "pixilate" -> "fairy"
            "aerilate" -> "flying"
            "refrigerate" -> "ice"
            "galvanize" -> "electric"
            else -> baseType
        }
    }

    private fun pixelateBoost(template: MoveTemplate, attacker: DamageCombatant): Double {
        val originalType = template.elementalType.name
        if (!originalType.equals("normal", ignoreCase = true)) return 1.0
        return when (normalizeToken(attacker.abilityName)) {
            "pixilate", "aerilate", "refrigerate", "galvanize" -> 1.2
            else -> 1.0
        }
    }

    private fun effectivePower(
        template: MoveTemplate,
        attacker: DamageCombatant,
        defender: DamageCombatant,
        context: DamageContext
    ): Double {
        val moveName = normalizeToken(template.name)
        return when (moveName) {
            "hex", "barbbarrage", "infernalparade" -> if (defender.snapshot.status != null) template.power * 2.0 else template.power
            "venoshock" -> if (normalizeToken(defender.snapshot.status) in setOf("poison", "poisonbadly", "badpoison")) template.power * 2.0 else template.power
            "weatherball" -> {
                if (normalizeToken(context.weather) in setOf("rain", "harshsunlight", "sun", "sunlight", "sandstorm", "hail", "snow")) {
                    template.power * 2.0
                } else {
                    template.power
                }
            }
            "facade" -> {
                val status = normalizeToken(attacker.snapshot.status)
                if (status in setOf("burn", "poison", "poisonbadly", "badpoison", "paralysis", "paralyze")) template.power * 2.0 else template.power
            }
            "brine" -> {
                val maxHp = resolvedMaxHp(defender)
                val currentHp = resolvedCurrentHp(defender)
                if (maxHp > 0 && currentHp * 2 <= maxHp) template.power * 2.0 else template.power
            }
            "storedpower" -> {
                val positiveStages = sumPositiveStages(attacker)
                (20 + 20 * positiveStages).toDouble()
            }
            "punishment" -> {
                val positiveStages = sumPositiveStages(defender)
                minOf(200.0, (60 + 20 * positiveStages).toDouble())
            }
            "electroball" -> {
                val ratio = speedRatio(attacker, defender)
                when {
                    ratio >= 4.0 -> 150.0
                    ratio >= 3.0 -> 120.0
                    ratio >= 2.0 -> 80.0
                    ratio >= 1.0 -> 60.0
                    else -> 40.0
                }
            }
            "gyroball" -> {
                val attackerSpe = attacker.assumptions?.derivedStats?.spe ?: return 1.0
                val defenderSpe = defender.assumptions?.derivedStats?.spe ?: return 1.0
                if (attackerSpe <= 0) 1.0 else minOf(150.0, (25.0 * defenderSpe) / attackerSpe)
            }
            "eruption", "waterspout", "dragonenergy" -> {
                val maxHp = resolvedMaxHp(attacker)
                val currentHp = resolvedCurrentHp(attacker)
                if (maxHp <= 0) template.power else maxOf(1.0, 150.0 * currentHp / maxHp)
            }
            "lowkick", "grassknot" -> weightBasedPower(defender)
            "heavyslam", "heatcrash" -> weightRatioPower(attacker, defender)
            "acrobatics" -> if (attacker.itemName.isNullOrBlank()) template.power * 2.0 else template.power
            else -> template.power
        }
    }

    private fun sumPositiveStages(combatant: DamageCombatant): Int {
        return combatant.snapshot.statStages.values.filter { it > 0 }.sum()
    }

    private fun speedRatio(attacker: DamageCombatant, defender: DamageCombatant): Double {
        val attackerSpe = attacker.assumptions?.derivedStats?.spe ?: return 0.0
        val defenderSpe = defender.assumptions?.derivedStats?.spe ?: return 0.0
        val attackerStage = attacker.snapshot.stageFor("spe")
        val defenderStage = defender.snapshot.stageFor("spe")
        val effectiveAttackerSpe = StatCalculator.applyStageMultiplier(attackerSpe, attackerStage)
        val effectiveDefenderSpe = StatCalculator.applyStageMultiplier(defenderSpe, defenderStage)
        if (effectiveDefenderSpe <= 0) return 4.0
        return effectiveAttackerSpe.toDouble() / effectiveDefenderSpe.toDouble()
    }

    private fun weightBasedPower(defender: DamageCombatant): Double {
        val kg = defender.snapshot.weightKg ?: return 20.0
        return when {
            kg >= 200.0 -> 120.0
            kg >= 100.0 -> 100.0
            kg >= 50.0 -> 80.0
            kg >= 25.0 -> 60.0
            kg >= 10.0 -> 40.0
            else -> 20.0
        }
    }

    private fun weightRatioPower(attacker: DamageCombatant, defender: DamageCombatant): Double {
        val atkKg = attacker.snapshot.weightKg ?: return 40.0
        val defKg = defender.snapshot.weightKg ?: return 40.0
        if (defKg <= 0) return 120.0
        val ratio = atkKg / defKg
        return when {
            ratio >= 5.0 -> 120.0
            ratio >= 4.0 -> 100.0
            ratio >= 3.0 -> 80.0
            ratio >= 2.0 -> 60.0
            else -> 40.0
        }
    }

    private fun effectiveAttackStat(
        attacker: DamageCombatant,
        defender: DamageCombatant,
        template: MoveTemplate,
        category: com.cobblemon.mod.common.api.moves.categories.DamageCategory,
        isCrit: Boolean = false
    ): Int {
        val moveName = normalizeToken(template.name)
        val usesDefenderAtk = moveName == "foulplay"
        val sourceCombatant = if (usesDefenderAtk) defender else attacker
        val assumptions = sourceCombatant.assumptions ?: return 0
        val baseAttack = when {
            moveName == "bodypress" -> assumptions.derivedStats.def
            category == DamageCategories.PHYSICAL -> assumptions.derivedStats.atk
            else -> assumptions.derivedStats.spa
        }
        val rawStage = when {
            moveName == "bodypress" -> sourceCombatant.snapshot.stageFor("def")
            category == DamageCategories.PHYSICAL -> sourceCombatant.snapshot.stageFor("atk")
            else -> sourceCombatant.snapshot.stageFor("spa")
        }
        val stage = if (isCrit) maxOf(0, rawStage) else rawStage
        var value = StatCalculator.applyStageMultiplier(baseAttack, stage)
        val itemHolder = if (usesDefenderAtk) defender else attacker
        value = when (category) {
            DamageCategories.PHYSICAL -> (value * StatCalculator.getItemAttackMultiplier(itemHolder.itemName, itemHolder.snapshot.speciesId)).toInt()
            DamageCategories.SPECIAL -> (value * StatCalculator.getItemSpecialAttackMultiplier(itemHolder.itemName, itemHolder.snapshot.speciesId)).toInt()
            else -> value
        }
        return max(1, value)
    }

    private fun fixedDamageOverride(
        template: MoveTemplate,
        attacker: DamageCombatant,
        defender: DamageCombatant,
        defenderMaxHp: Int
    ): Int? {
        return when (normalizeToken(template.name)) {
            "seismictoss", "nightshade" -> max(1, attacker.snapshot.level)
            "dragonrage" -> 40
            "sonicboom" -> 20
            "superfang" -> max(1, resolvedCurrentHp(defender) / 2)
            "endeavor" -> {
                val diff = resolvedCurrentHp(defender) - resolvedCurrentHp(attacker)
                if (diff > 0) diff else null
            }
            "finalgambit" -> max(1, resolvedCurrentHp(attacker))
            "psywave" -> max(1, (attacker.snapshot.level * 1.0).toInt())
            else -> null
        }
    }

    private fun effectiveDefenseStat(
        defender: DamageCombatant,
        category: com.cobblemon.mod.common.api.moves.categories.DamageCategory,
        context: DamageContext,
        isCrit: Boolean = false
    ): Int {
        val assumptions = defender.assumptions ?: return 0
        val rawStage = if (category == DamageCategories.PHYSICAL) defender.snapshot.stageFor("def") else defender.snapshot.stageFor("spd")
        val stage = if (isCrit) minOf(0, rawStage) else rawStage
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
        template: MoveTemplate,
        moveTypeName: String,
        category: com.cobblemon.mod.common.api.moves.categories.DamageCategory,
        context: DamageContext,
        effectiveness: Double
    ): Double {
        val ability = normalizeToken(attacker.abilityName)
        val flagEntry = MoveFlagDatabase.get(template.name)
        val power = template.power.toInt()
        return when (ability) {
            "hugepower", "purepower" -> if (category == DamageCategories.PHYSICAL) 2.0 else 1.0
            "guts" -> if (category == DamageCategories.PHYSICAL && attacker.snapshot.status != null) 1.5 else 1.0
            "solarpower" -> if (category == DamageCategories.SPECIAL && normalizeToken(context.weather) in setOf("harshsunlight", "sun", "sunlight")) 1.5 else 1.0
            "flareboost" -> if (category == DamageCategories.SPECIAL && normalizeToken(attacker.snapshot.status) == "burn") 1.5 else 1.0
            "toxicboost" -> if (category == DamageCategories.PHYSICAL && normalizeToken(attacker.snapshot.status) in setOf("poison", "poisonbadly", "badpoison")) 1.5 else 1.0
            "blaze" -> if (normalizeToken(moveTypeName) == "fire" && isLowHp(attacker)) 1.5 else 1.0
            "torrent" -> if (normalizeToken(moveTypeName) == "water" && isLowHp(attacker)) 1.5 else 1.0
            "overgrow" -> if (normalizeToken(moveTypeName) == "grass" && isLowHp(attacker)) 1.5 else 1.0
            "swarm" -> if (normalizeToken(moveTypeName) == "bug" && isLowHp(attacker)) 1.5 else 1.0
            "toughclaws" -> if (flagEntry?.isContact == true) 1.3 else 1.0
            "strongjaw" -> if (flagEntry?.isBite == true) 1.5 else 1.0
            "ironfist" -> if (flagEntry?.isPunch == true) 1.2 else 1.0
            "megalauncher" -> if (flagEntry?.isPulse == true) 1.5 else 1.0
            "sharpness" -> if (flagEntry?.isSlicing == true) 1.5 else 1.0
            "punkrock" -> if (flagEntry?.isSound == true) 1.3 else 1.0
            "reckless" -> if (flagEntry?.isRecoil == true) 1.2 else 1.0
            "sheerforce" -> if (flagEntry?.hasSecondary == true) 1.3 else 1.0
            "technician" -> if (power in 1..60) 1.5 else 1.0
            "tintedlens" -> if (effectiveness in 0.0..0.999) 2.0 else 1.0
            "waterbubble" -> if (normalizeToken(moveTypeName) == "water") 2.0 else 1.0
            "steelworker", "steelyspirit" -> if (normalizeToken(moveTypeName) == "steel") 1.5 else 1.0
            "dragonsmaw" -> if (normalizeToken(moveTypeName) == "dragon") 1.5 else 1.0
            "transistor" -> if (normalizeToken(moveTypeName) == "electric") 1.3 else 1.0
            "rockypayload" -> if (normalizeToken(moveTypeName) == "rock") 1.5 else 1.0
            "aerilate", "pixilate", "refrigerate", "galvanize" -> pixelateBoost(template, attacker)
            else -> 1.0
        }
    }

    private fun defensiveAbilityModifier(
        defender: DamageCombatant,
        template: MoveTemplate,
        moveTypeName: String,
        category: com.cobblemon.mod.common.api.moves.categories.DamageCategory,
        effectiveness: Double,
        defenderAtFullHp: Boolean
    ): Double {
        val flagEntry = MoveFlagDatabase.get(template.name)
        return when (normalizeToken(defender.abilityName)) {
            "thickfat" -> if (normalizeToken(moveTypeName) in setOf("fire", "ice")) 0.5 else 1.0
            "heatproof" -> if (normalizeToken(moveTypeName) == "fire") 0.5 else 1.0
            "waterbubble" -> if (normalizeToken(moveTypeName) == "fire") 0.5 else 1.0
            "dryskin" -> if (normalizeToken(moveTypeName) == "fire") 1.25 else 1.0
            "fluffy" -> when {
                flagEntry?.isContact == true && normalizeToken(moveTypeName) != "fire" -> 0.5
                normalizeToken(moveTypeName) == "fire" -> 2.0
                else -> 1.0
            }
            "furcoat" -> if (category == DamageCategories.PHYSICAL) 0.5 else 1.0
            "icescales" -> if (category == DamageCategories.SPECIAL) 0.5 else 1.0
            "marvelscale" -> if (category == DamageCategories.PHYSICAL && defender.snapshot.status != null) 2.0 / 3.0 else 1.0
            "filter", "solidrock", "prismarmor" -> if (effectiveness > 1.0) 0.75 else 1.0
            "multiscale", "shadowshield" -> if (defenderAtFullHp) 0.5 else 1.0
            "punkrock" -> if (flagEntry?.isSound == true) 0.5 else 1.0
            "purifyingsalt" -> if (normalizeToken(moveTypeName) == "ghost") 0.5 else 1.0
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

    private fun bypassesDefenderAbility(attackerAbility: String?): Boolean {
        return normalizeToken(attackerAbility) in setOf("moldbreaker", "turboblaze", "teravolt")
    }

    private fun moveBypassesDefenderAbility(template: MoveTemplate): Boolean {
        return normalizeToken(template.name) in MOVES_THAT_BYPASS_ABILITY
    }

    private fun wonderGuardBlocks(defender: DamageCombatant, attackerAbility: String?, effectiveness: Double): Boolean {
        if (bypassesDefenderAbility(attackerAbility)) return false
        if (normalizeToken(defender.abilityName) != "wonderguard") return false
        return effectiveness > 0.0 && effectiveness <= 1.0
    }

    private fun itemPowerModifier(
        attacker: DamageCombatant,
        moveTypeName: String,
        category: com.cobblemon.mod.common.api.moves.categories.DamageCategory,
        effectiveness: Double
    ): Double {
        val itemId = normalizeToken(attacker.itemName)
        if (itemId.isBlank()) return 1.0
        when (itemId) {
            "expertbelt" -> return if (effectiveness > 1.0) 1.2 else 1.0
            "muscleband" -> return if (category == DamageCategories.PHYSICAL) 1.1 else 1.0
            "wiseglasses" -> return if (category == DamageCategories.SPECIAL) 1.1 else 1.0
            "metronome" -> return 1.0
        }
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
