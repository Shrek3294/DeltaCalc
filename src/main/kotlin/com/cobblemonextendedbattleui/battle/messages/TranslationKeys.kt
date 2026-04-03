package com.cobblemonextendedbattleui.battle.messages

import com.cobblemonextendedbattleui.BattleStateTracker.BattleStat
import com.cobblemonextendedbattleui.BattleStateTracker.FieldCondition
import com.cobblemonextendedbattleui.BattleStateTracker.SideCondition
import com.cobblemonextendedbattleui.BattleStateTracker.Terrain
import com.cobblemonextendedbattleui.BattleStateTracker.VolatileStatus
import com.cobblemonextendedbattleui.BattleStateTracker.Weather

/**
 * All translation key mappings used to parse Cobblemon battle messages.
 * Pure constants — zero logic.
 */
object TranslationKeys {

    // ═════════════════════════════════════════════════════════════════════════
    // Stat Boost/Unboost Keys
    // ═════════════════════════════════════════════════════════════════════════

    val BOOST_MAGNITUDE_KEYS = mapOf(
        "cobblemon.battle.boost.slight" to 1,
        "cobblemon.battle.boost.sharp" to 2,
        "cobblemon.battle.boost.severe" to 3,
        "cobblemon.battle.boost.slight.zeffect" to 1,
        "cobblemon.battle.boost.sharp.zeffect" to 2,
        "cobblemon.battle.boost.severe.zeffect" to 3
    )

    val UNBOOST_MAGNITUDE_KEYS = mapOf(
        "cobblemon.battle.unboost.slight" to 1,
        "cobblemon.battle.unboost.sharp" to 2,
        "cobblemon.battle.unboost.severe" to 3
    )

    // Maps Cobblemon stat translation KEYS to BattleStat enum (language-independent)
    // Cobblemon uses ".name" suffix and British spelling (defence, not defense)
    val STAT_KEY_MAPPING = mapOf(
        // Primary keys - exact Cobblemon keys with .name suffix and British spelling
        "cobblemon.stat.attack.name" to BattleStat.ATTACK,
        "cobblemon.stat.defence.name" to BattleStat.DEFENSE,
        "cobblemon.stat.special_attack.name" to BattleStat.SPECIAL_ATTACK,
        "cobblemon.stat.special_defence.name" to BattleStat.SPECIAL_DEFENSE,
        "cobblemon.stat.speed.name" to BattleStat.SPEED,
        "cobblemon.stat.accuracy.name" to BattleStat.ACCURACY,
        "cobblemon.stat.evasion.name" to BattleStat.EVASION,
        // Alternative spellings with .name suffix
        "cobblemon.stat.defense.name" to BattleStat.DEFENSE,
        "cobblemon.stat.special_defense.name" to BattleStat.SPECIAL_DEFENSE,
        "cobblemon.stat.evasiveness.name" to BattleStat.EVASION,
        // Without .name suffix (fallback variants)
        "cobblemon.stat.attack" to BattleStat.ATTACK,
        "cobblemon.stat.atk" to BattleStat.ATTACK,
        "cobblemon.stat.defense" to BattleStat.DEFENSE,
        "cobblemon.stat.defence" to BattleStat.DEFENSE,
        "cobblemon.stat.def" to BattleStat.DEFENSE,
        "cobblemon.stat.special_attack" to BattleStat.SPECIAL_ATTACK,
        "cobblemon.stat.specialattack" to BattleStat.SPECIAL_ATTACK,
        "cobblemon.stat.spa" to BattleStat.SPECIAL_ATTACK,
        "cobblemon.stat.sp_atk" to BattleStat.SPECIAL_ATTACK,
        "cobblemon.stat.special_defense" to BattleStat.SPECIAL_DEFENSE,
        "cobblemon.stat.specialdefense" to BattleStat.SPECIAL_DEFENSE,
        "cobblemon.stat.special_defence" to BattleStat.SPECIAL_DEFENSE,
        "cobblemon.stat.spd" to BattleStat.SPECIAL_DEFENSE,
        "cobblemon.stat.sp_def" to BattleStat.SPECIAL_DEFENSE,
        "cobblemon.stat.speed" to BattleStat.SPEED,
        "cobblemon.stat.spe" to BattleStat.SPEED,
        "cobblemon.stat.accuracy" to BattleStat.ACCURACY,
        "cobblemon.stat.acc" to BattleStat.ACCURACY,
        "cobblemon.stat.evasion" to BattleStat.EVASION,
        "cobblemon.stat.evasiveness" to BattleStat.EVASION,
        "cobblemon.stat.eva" to BattleStat.EVASION
    )

    // Fallback: Maps displayed stat names to BattleStat enum (English only, for compatibility)
    val STAT_NAME_MAPPING = mapOf(
        "attack" to BattleStat.ATTACK,
        "defense" to BattleStat.DEFENSE,
        "defence" to BattleStat.DEFENSE,
        "special attack" to BattleStat.SPECIAL_ATTACK,
        "sp. atk" to BattleStat.SPECIAL_ATTACK,
        "special defense" to BattleStat.SPECIAL_DEFENSE,
        "special defence" to BattleStat.SPECIAL_DEFENSE,
        "sp. def" to BattleStat.SPECIAL_DEFENSE,
        "speed" to BattleStat.SPEED,
        "accuracy" to BattleStat.ACCURACY,
        "evasion" to BattleStat.EVASION,
        "evasiveness" to BattleStat.EVASION
    )

    // Cobblemon stat translation keys for reverse lookup (translate key -> compare with received string)
    val COBBLEMON_STAT_KEYS = listOf(
        "cobblemon.stat.attack.name" to BattleStat.ATTACK,
        "cobblemon.stat.defence.name" to BattleStat.DEFENSE,
        "cobblemon.stat.special_attack.name" to BattleStat.SPECIAL_ATTACK,
        "cobblemon.stat.special_defence.name" to BattleStat.SPECIAL_DEFENSE,
        "cobblemon.stat.speed.name" to BattleStat.SPEED,
        "cobblemon.stat.accuracy.name" to BattleStat.ACCURACY,
        "cobblemon.stat.evasion.name" to BattleStat.EVASION
    )

    // ═════════════════════════════════════════════════════════════════════════
    // Move Keys for Special Handling
    // ═════════════════════════════════════════════════════════════════════════

    val BATON_PASS_KEYS = setOf(
        "cobblemon.move.batonpass",
        "cobblemon.move.baton_pass"
    )
    val SPECTRAL_THIEF_KEYS = setOf(
        "cobblemon.move.spectralthief",
        "cobblemon.move.spectral_thief"
    )
    const val BATON_PASS_NAME = "baton pass"
    const val SPECTRAL_THIEF_NAME = "spectral thief"

    // ═════════════════════════════════════════════════════════════════════════
    // Ability Keys
    // ═════════════════════════════════════════════════════════════════════════

    const val PRESSURE_KEY = "cobblemon.battle.ability.pressure"
    const val ABILITY_GENERIC_KEY = "cobblemon.battle.ability.generic"

    val ABILITY_SINGLE_ARG_KEYS = mapOf(
        "cobblemon.battle.ability.sturdy" to "Sturdy",
        "cobblemon.battle.ability.unnerve" to "Unnerve",
        "cobblemon.battle.ability.anticipation" to "Anticipation",
        "cobblemon.battle.ability.intimidate" to "Intimidate"
    )

    const val ABILITY_TRACE_KEY = "cobblemon.battle.ability.trace"
    const val ABILITY_RECEIVER_KEY = "cobblemon.battle.ability.receiver"
    const val ABILITY_REPLACE_KEY = "cobblemon.battle.ability.replace"
    const val ABILITY_MAGICBOUNCE_KEY = "cobblemon.battle.ability.magicbounce"

    val ABILITY_START_KEYS = mapOf(
        "cobblemon.battle.start.flashfire" to "Flash Fire"
    )

    // ═════════════════════════════════════════════════════════════════════════
    // Item Tracking Keys
    // ═════════════════════════════════════════════════════════════════════════

    val ITEM_REVEAL_KEYS = setOf(
        "cobblemon.battle.item.airballoon",
        "cobblemon.battle.item.eat",
        "cobblemon.battle.item.harvest",
        "cobblemon.battle.item.recycle",
        "cobblemon.battle.damage.item"
    )

    const val TRICK_KEY = "cobblemon.battle.item.trick"

    val TRICK_ACTIVATE_KEYS = setOf(
        "cobblemon.battle.activate.trick",
        "cobblemon.battle.activate.switcheroo"
    )

    const val COURT_CHANGE_KEY = "cobblemon.battle.activate.courtchange"
    const val TERASTALLIZE_KEY = "cobblemon.battle.terastallize"
    const val FRISK_KEY = "cobblemon.battle.item.frisk"
    const val LIFE_ORB_KEY = "cobblemon.battle.damage.lifeorb"
    const val THIEF_KEY = "cobblemon.battle.item.thief"
    const val BESTOW_KEY = "cobblemon.battle.item.bestow"

    val ITEM_CONSUMED_KEYS = setOf(
        "cobblemon.battle.enditem.eat",
        "cobblemon.battle.enditem.fling",
        "cobblemon.battle.enditem.gem",
        "cobblemon.battle.enditem.incinerate",
        "cobblemon.battle.enditem.airballoon",
        "cobblemon.battle.enditem.generic",
        "cobblemon.battle.enditem.mentalherb",
        "cobblemon.battle.enditem.powerherb",
        "cobblemon.battle.enditem.mirrorherb",
        "cobblemon.battle.enditem.whiteherb",
        "cobblemon.battle.enditem.cellbattery",
        "cobblemon.battle.enditem.ejectbutton",
        "cobblemon.battle.enditem.snowball",
        "cobblemon.battle.enditem.ejectpack",
        "cobblemon.battle.enditem.berryjuice",
        "cobblemon.battle.enditem.redcard"
    )

    val ITEM_CONSUMED_SINGLE_ARG_KEYS = mapOf(
        "cobblemon.battle.enditem.focussash" to "Focus Sash",
        "cobblemon.battle.enditem.focusband" to "Focus Band"
    )

    val BERRY_DAMAGE_KEYS = setOf(
        "cobblemon.battle.enditem.occaberry",
        "cobblemon.battle.enditem.passhoberry",
        "cobblemon.battle.enditem.wacanberry",
        "cobblemon.battle.enditem.rindoberry",
        "cobblemon.battle.enditem.yacheberry",
        "cobblemon.battle.enditem.chopleberry",
        "cobblemon.battle.enditem.kebiaberry",
        "cobblemon.battle.enditem.shucaberry",
        "cobblemon.battle.enditem.cobaberry"
    )

    const val KNOCKOFF_KEY = "cobblemon.battle.enditem.knockoff"
    const val STEALEAT_KEY = "cobblemon.battle.enditem.stealeat"
    const val CORROSIVEGAS_KEY = "cobblemon.battle.enditem.corrosivegas"

    val HEALING_ITEM_KEYS = setOf(
        "cobblemon.battle.heal.leftovers",
        "cobblemon.battle.heal.item"
    )

    // ═════════════════════════════════════════════════════════════════════════
    // Weather, Terrain, Field, Side Condition Keys
    // ═════════════════════════════════════════════════════════════════════════

    val WEATHER_START_KEYS = mapOf(
        "cobblemon.battle.weather.raindance.start" to Weather.RAIN,
        "cobblemon.battle.weather.sunnyday.start" to Weather.SUN,
        "cobblemon.battle.weather.sandstorm.start" to Weather.SANDSTORM,
        "cobblemon.battle.weather.hail.start" to Weather.HAIL,
        "cobblemon.battle.weather.snow.start" to Weather.SNOW
    )

    val WEATHER_END_KEYS = setOf(
        "cobblemon.battle.weather.raindance.end",
        "cobblemon.battle.weather.sunnyday.end",
        "cobblemon.battle.weather.sandstorm.end",
        "cobblemon.battle.weather.hail.end",
        "cobblemon.battle.weather.snow.end"
    )

    val TERRAIN_START_KEYS = mapOf(
        "cobblemon.battle.fieldstart.electricterrain" to Terrain.ELECTRIC,
        "cobblemon.battle.fieldstart.grassyterrain" to Terrain.GRASSY,
        "cobblemon.battle.fieldstart.mistyterrain" to Terrain.MISTY,
        "cobblemon.battle.fieldstart.psychicterrain" to Terrain.PSYCHIC
    )

    val TERRAIN_END_KEYS = setOf(
        "cobblemon.battle.fieldend.electricterrain",
        "cobblemon.battle.fieldend.grassyterrain",
        "cobblemon.battle.fieldend.mistyterrain",
        "cobblemon.battle.fieldend.psychicterrain"
    )

    val FIELD_START_KEYS = mapOf(
        "cobblemon.battle.fieldstart.trickroom" to FieldCondition.TRICK_ROOM,
        "cobblemon.battle.fieldstart.gravity" to FieldCondition.GRAVITY,
        "cobblemon.battle.fieldstart.magicroom" to FieldCondition.MAGIC_ROOM,
        "cobblemon.battle.fieldstart.wonderroom" to FieldCondition.WONDER_ROOM
    )

    val FIELD_END_KEYS = mapOf(
        "cobblemon.battle.fieldend.trickroom" to FieldCondition.TRICK_ROOM,
        "cobblemon.battle.fieldend.gravity" to FieldCondition.GRAVITY,
        "cobblemon.battle.fieldend.magicroom" to FieldCondition.MAGIC_ROOM,
        "cobblemon.battle.fieldend.wonderroom" to FieldCondition.WONDER_ROOM
    )

    val SIDE_START_KEYS = buildMap {
        put("cobblemon.battle.sidestart.ally.reflect", SideCondition.REFLECT to true)
        put("cobblemon.battle.sidestart.ally.lightscreen", SideCondition.LIGHT_SCREEN to true)
        put("cobblemon.battle.sidestart.ally.auroraveil", SideCondition.AURORA_VEIL to true)
        put("cobblemon.battle.sidestart.ally.tailwind", SideCondition.TAILWIND to true)
        put("cobblemon.battle.sidestart.ally.safeguard", SideCondition.SAFEGUARD to true)
        put("cobblemon.battle.sidestart.ally.luckychant", SideCondition.LUCKY_CHANT to true)
        put("cobblemon.battle.sidestart.ally.mist", SideCondition.MIST to true)
        put("cobblemon.battle.sidestart.ally.stealthrock", SideCondition.STEALTH_ROCK to true)
        put("cobblemon.battle.sidestart.ally.spikes", SideCondition.SPIKES to true)
        put("cobblemon.battle.sidestart.ally.toxicspikes", SideCondition.TOXIC_SPIKES to true)
        put("cobblemon.battle.sidestart.ally.stickyweb", SideCondition.STICKY_WEB to true)
        put("cobblemon.battle.sidestart.opponent.reflect", SideCondition.REFLECT to false)
        put("cobblemon.battle.sidestart.opponent.lightscreen", SideCondition.LIGHT_SCREEN to false)
        put("cobblemon.battle.sidestart.opponent.auroraveil", SideCondition.AURORA_VEIL to false)
        put("cobblemon.battle.sidestart.opponent.tailwind", SideCondition.TAILWIND to false)
        put("cobblemon.battle.sidestart.opponent.safeguard", SideCondition.SAFEGUARD to false)
        put("cobblemon.battle.sidestart.opponent.luckychant", SideCondition.LUCKY_CHANT to false)
        put("cobblemon.battle.sidestart.opponent.mist", SideCondition.MIST to false)
        put("cobblemon.battle.sidestart.opponent.stealthrock", SideCondition.STEALTH_ROCK to false)
        put("cobblemon.battle.sidestart.opponent.spikes", SideCondition.SPIKES to false)
        put("cobblemon.battle.sidestart.opponent.toxicspikes", SideCondition.TOXIC_SPIKES to false)
        put("cobblemon.battle.sidestart.opponent.stickyweb", SideCondition.STICKY_WEB to false)
    }

    val SIDE_END_KEYS = buildMap {
        put("cobblemon.battle.sideend.ally.reflect", SideCondition.REFLECT to true)
        put("cobblemon.battle.sideend.ally.lightscreen", SideCondition.LIGHT_SCREEN to true)
        put("cobblemon.battle.sideend.ally.auroraveil", SideCondition.AURORA_VEIL to true)
        put("cobblemon.battle.sideend.ally.tailwind", SideCondition.TAILWIND to true)
        put("cobblemon.battle.sideend.ally.safeguard", SideCondition.SAFEGUARD to true)
        put("cobblemon.battle.sideend.ally.luckychant", SideCondition.LUCKY_CHANT to true)
        put("cobblemon.battle.sideend.ally.mist", SideCondition.MIST to true)
        put("cobblemon.battle.sideend.ally.stealthrock", SideCondition.STEALTH_ROCK to true)
        put("cobblemon.battle.sideend.ally.spikes", SideCondition.SPIKES to true)
        put("cobblemon.battle.sideend.ally.toxicspikes", SideCondition.TOXIC_SPIKES to true)
        put("cobblemon.battle.sideend.ally.stickyweb", SideCondition.STICKY_WEB to true)
        put("cobblemon.battle.sideend.opponent.reflect", SideCondition.REFLECT to false)
        put("cobblemon.battle.sideend.opponent.lightscreen", SideCondition.LIGHT_SCREEN to false)
        put("cobblemon.battle.sideend.opponent.auroraveil", SideCondition.AURORA_VEIL to false)
        put("cobblemon.battle.sideend.opponent.tailwind", SideCondition.TAILWIND to false)
        put("cobblemon.battle.sideend.opponent.safeguard", SideCondition.SAFEGUARD to false)
        put("cobblemon.battle.sideend.opponent.luckychant", SideCondition.LUCKY_CHANT to false)
        put("cobblemon.battle.sideend.opponent.mist", SideCondition.MIST to false)
        put("cobblemon.battle.sideend.opponent.stealthrock", SideCondition.STEALTH_ROCK to false)
        put("cobblemon.battle.sideend.opponent.spikes", SideCondition.SPIKES to false)
        put("cobblemon.battle.sideend.opponent.toxicspikes", SideCondition.TOXIC_SPIKES to false)
        put("cobblemon.battle.sideend.opponent.stickyweb", SideCondition.STICKY_WEB to false)
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Turn, Faint, Switch Keys
    // ═════════════════════════════════════════════════════════════════════════

    const val TURN_KEY = "cobblemon.battle.turn"
    const val FAINT_KEY = "cobblemon.battle.fainted"
    const val SWITCH_KEY = "cobblemon.battle.switch"
    const val DRAG_KEY = "cobblemon.battle.drag"
    const val SENDOUT_KEY = "cobblemon.battle.sendout"
    const val REPLACE_KEY = "cobblemon.battle.replace"
    const val PERISH_SONG_FIELD_KEY = "cobblemon.battle.fieldactivate.perishsong"
    const val TRANSFORM_KEY = "cobblemon.battle.transform"

    // ═════════════════════════════════════════════════════════════════════════
    // Form Change Keys
    // ═════════════════════════════════════════════════════════════════════════

    const val FORMECHANGE_PERMANENT_KEY = "cobblemon.battle.formechange.default.permanent"
    const val FORMECHANGE_TEMPORARY_KEY = "cobblemon.battle.formechange.default.temporary"
    const val FORMECHANGE_ENDED_KEY = "cobblemon.battle.formechange.default.temporary.end"
    const val MEGA_FORMECHANGE_KEY = "cobblemon.battle.formechange.mega"
    const val MEGA_EVOLVED_KEY = "cobblemon.battle.mega"

    val SPECIAL_FORMECHANGE_KEYS = mapOf(
        "cobblemon.battle.formechange.ash" to "Ash-Greninja",
        "cobblemon.battle.formechange.school" to "School",
        "cobblemon.battle.formechange.meteor" to "Meteor"
    )

    val SPECIAL_FORMECHANGE_END_KEYS = mapOf(
        "cobblemon.battle.formechange.wishiwashi" to "Solo",
        "cobblemon.battle.formechange.minior" to "Core"
    )

    const val DYNAMAX_KEY = "cobblemon.battle.start.dynamax"
    const val GIGANTAMAX_KEY = "cobblemon.battle.start.gmax"

    // ═════════════════════════════════════════════════════════════════════════
    // Type Modification Move Keys
    // ═════════════════════════════════════════════════════════════════════════

    val TYPE_CHANGE_KEYS = setOf(
        "cobblemon.battle.typechange",
        "cobblemon.battle.typechange.soak",
        "cobblemon.battle.start.typechange"
    )

    val TYPE_ADD_KEYS = setOf(
        "cobblemon.battle.typeadd",
        "cobblemon.battle.typeadd.trickortreat",
        "cobblemon.battle.typeadd.forestscurse",
        "cobblemon.battle.start.typeadd"
    )

    val BURN_UP_KEYS = setOf(
        "cobblemon.battle.start.burnup",
        "cobblemon.battle.start.typechange.burnup",
        "cobblemon.battle.typechange.burnup",
        "cobblemon.battle.burnup"
    )

    val DOUBLE_SHOCK_KEYS = setOf(
        "cobblemon.battle.start.doubleshock",
        "cobblemon.battle.start.typechange.doubleshock",
        "cobblemon.battle.typechange.doubleshock",
        "cobblemon.battle.doubleshock"
    )

    // ═════════════════════════════════════════════════════════════════════════
    // Volatile Status Keys
    // ═════════════════════════════════════════════════════════════════════════

    val VOLATILE_START_KEYS = mapOf(
        "cobblemon.battle.start.leechseed" to VolatileStatus.LEECH_SEED,
        "cobblemon.battle.start.confusion" to VolatileStatus.CONFUSION,
        "cobblemon.battle.start.taunt" to VolatileStatus.TAUNT,
        "cobblemon.battle.start.encore" to VolatileStatus.ENCORE,
        "cobblemon.battle.start.disable" to VolatileStatus.DISABLE,
        "cobblemon.battle.start.torment" to VolatileStatus.TORMENT,
        "cobblemon.battle.start.attract" to VolatileStatus.INFATUATION,
        "cobblemon.battle.start.perish" to VolatileStatus.PERISH_SONG,
        "cobblemon.battle.start.yawn" to VolatileStatus.DROWSY,
        "cobblemon.battle.start.curse" to VolatileStatus.CURSE,
        "cobblemon.battle.start.nightmare" to VolatileStatus.NIGHTMARE,
        "cobblemon.battle.start.substitute" to VolatileStatus.SUBSTITUTE,
        "cobblemon.battle.start.aquaring" to VolatileStatus.AQUA_RING,
        "cobblemon.battle.start.ingrain" to VolatileStatus.INGRAIN,
        "cobblemon.battle.start.focusenergy" to VolatileStatus.FOCUS_ENERGY,
        "cobblemon.battle.start.magnetrise" to VolatileStatus.MAGNET_RISE,
        "cobblemon.battle.start.embargo" to VolatileStatus.EMBARGO,
        "cobblemon.battle.start.healblock" to VolatileStatus.HEAL_BLOCK
    )

    val VOLATILE_ACTIVATE_KEYS = mapOf(
        "cobblemon.battle.activate.bind" to VolatileStatus.BOUND,
        "cobblemon.battle.activate.wrap" to VolatileStatus.BOUND,
        "cobblemon.battle.activate.firespin" to VolatileStatus.BOUND,
        "cobblemon.battle.activate.whirlpool" to VolatileStatus.BOUND,
        "cobblemon.battle.activate.sandtomb" to VolatileStatus.BOUND,
        "cobblemon.battle.activate.clamp" to VolatileStatus.BOUND,
        "cobblemon.battle.activate.infestation" to VolatileStatus.BOUND,
        "cobblemon.battle.activate.magmastorm" to VolatileStatus.BOUND,
        "cobblemon.battle.activate.snaptrap" to VolatileStatus.BOUND,
        "cobblemon.battle.activate.thundercage" to VolatileStatus.BOUND,
        "cobblemon.battle.activate.trapped" to VolatileStatus.TRAPPED
    )

    val VOLATILE_END_KEYS = mapOf(
        "cobblemon.battle.end.leechseed" to VolatileStatus.LEECH_SEED,
        "cobblemon.battle.end.confusion" to VolatileStatus.CONFUSION,
        "cobblemon.battle.end.taunt" to VolatileStatus.TAUNT,
        "cobblemon.battle.end.encore" to VolatileStatus.ENCORE,
        "cobblemon.battle.end.disable" to VolatileStatus.DISABLE,
        "cobblemon.battle.end.torment" to VolatileStatus.TORMENT,
        "cobblemon.battle.end.attract" to VolatileStatus.INFATUATION,
        "cobblemon.battle.end.substitute" to VolatileStatus.SUBSTITUTE,
        "cobblemon.battle.end.magnetrise" to VolatileStatus.MAGNET_RISE,
        "cobblemon.battle.end.embargo" to VolatileStatus.EMBARGO,
        "cobblemon.battle.end.healblock" to VolatileStatus.HEAL_BLOCK,
        "cobblemon.battle.end.bind" to VolatileStatus.BOUND,
        "cobblemon.battle.end.wrap" to VolatileStatus.BOUND,
        "cobblemon.battle.end.firespin" to VolatileStatus.BOUND,
        "cobblemon.battle.end.whirlpool" to VolatileStatus.BOUND,
        "cobblemon.battle.end.sandtomb" to VolatileStatus.BOUND,
        "cobblemon.battle.end.clamp" to VolatileStatus.BOUND,
        "cobblemon.battle.end.infestation" to VolatileStatus.BOUND
    )
}
