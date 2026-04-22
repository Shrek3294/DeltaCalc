package com.deltacalc.calc;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class MoveDex {
    private static final Map<String, MoveDefinition> MOVES = new HashMap<>();

    static {
        register(new MoveDefinition("Earthquake", "ground", 100, MoveCategory.PHYSICAL, false));
        register(new MoveDefinition("Dragon Claw", "dragon", 80, MoveCategory.PHYSICAL, false));
        register(new MoveDefinition("Fire Fang", "fire", 65, MoveCategory.PHYSICAL, false));
        register(new MoveDefinition("Swords Dance", "normal", 0, MoveCategory.STATUS, false));
        register(new MoveDefinition("Quiver Dance", "bug", 0, MoveCategory.STATUS, false));
        register(new MoveDefinition("Fiery Dance", "fire", 80, MoveCategory.SPECIAL, false));
        register(new MoveDefinition("Bug Buzz", "bug", 90, MoveCategory.SPECIAL, false));
        register(new MoveDefinition("Giga Drain", "grass", 75, MoveCategory.SPECIAL, false));
        register(new MoveDefinition("Psychic", "psychic", 90, MoveCategory.SPECIAL, false));
        register(new MoveDefinition("Flamethrower", "fire", 90, MoveCategory.SPECIAL, false));
        register(new MoveDefinition("Roost", "flying", 0, MoveCategory.STATUS, false));
        register(new MoveDefinition("Hurricane", "flying", 110, MoveCategory.SPECIAL, false));
        register(new MoveDefinition("Will-O-Wisp", "fire", 0, MoveCategory.STATUS, false));
        register(new MoveDefinition("Fire Blast", "fire", 110, MoveCategory.SPECIAL, false));
        register(new MoveDefinition("Air Slash", "flying", 75, MoveCategory.SPECIAL, false));
        register(new MoveDefinition("Bullet Seed", "grass", 25, MoveCategory.PHYSICAL, false, 2, 5));
        register(new MoveDefinition("Rock Blast", "rock", 25, MoveCategory.PHYSICAL, false, 2, 5));
        register(new MoveDefinition("Icicle Spear", "ice", 25, MoveCategory.PHYSICAL, false, 2, 5));
        register(new MoveDefinition("Pin Missile", "bug", 25, MoveCategory.PHYSICAL, false, 2, 5));
        register(new MoveDefinition("Tail Slap", "normal", 25, MoveCategory.PHYSICAL, false, 2, 5));
        register(new MoveDefinition("Scale Shot", "dragon", 25, MoveCategory.PHYSICAL, false, 2, 5));
        register(new MoveDefinition("Water Shuriken", "water", 15, MoveCategory.SPECIAL, false, 2, 5));
        register(new MoveDefinition("Double Kick", "fighting", 30, MoveCategory.PHYSICAL, false, 2, 2));
        register(new MoveDefinition("Bonemerang", "ground", 50, MoveCategory.PHYSICAL, false, 2, 2));
        register(new MoveDefinition("Dual Chop", "dragon", 40, MoveCategory.PHYSICAL, false, 2, 2));
        register(new MoveDefinition("Gear Grind", "steel", 50, MoveCategory.PHYSICAL, false, 2, 2));
        register(new MoveDefinition("Dragon Darts", "dragon", 50, MoveCategory.PHYSICAL, false, 2, 2));
        register(new MoveDefinition("Double Iron Bash", "steel", 60, MoveCategory.PHYSICAL, false, 2, 2));
        register(new MoveDefinition("Triple Kick", "fighting", 10, MoveCategory.PHYSICAL, false, 3, 3));
        register(new MoveDefinition("Triple Axel", "ice", 20, MoveCategory.PHYSICAL, false, 3, 3));
        register(new MoveDefinition("Surging Strikes", "water", 25, MoveCategory.PHYSICAL, false, 3, 3));
        register(new MoveDefinition("Population Bomb", "normal", 20, MoveCategory.PHYSICAL, false, 1, 10));
        // Delta signature multi-hit moves (from cobblemon-delta team-builder data).
        register(new MoveDefinition("Twin Cross", "dragon", 50, MoveCategory.PHYSICAL, false, 2, 2));
        register(new MoveDefinition("Lumen Cascade", "normal", 50, MoveCategory.SPECIAL, false, 2, 2));
        register(new MoveDefinition("Searing Claws", "fire", 35, MoveCategory.PHYSICAL, false, 2, 2));
        register(new MoveDefinition("Dual Divide", "bug", 40, MoveCategory.PHYSICAL, false, 2, 2));
        register(new MoveDefinition("Tomahawk Volley", "fire", 20, MoveCategory.PHYSICAL, false, 2, 5));
        register(new MoveDefinition("Wretched Stab", "ghost", 20, MoveCategory.PHYSICAL, false, 2, 5));
        register(new MoveDefinition("Divine Volley", "fighting", 20, MoveCategory.PHYSICAL, false, 1, 6));
        register(new MoveDefinition("Quillstorm", "fire", 20, MoveCategory.PHYSICAL, false, 1, 3));
    }

    private MoveDex() {
    }

    public static MoveDefinition resolve(String moveName) {
        if (moveName == null || moveName.isBlank()) {
            return new MoveDefinition("Unknown", "normal", 60, MoveCategory.PHYSICAL, true);
        }

        MoveDefinition exact = MOVES.get(key(moveName));
        if (exact != null) {
            return exact;
        }

        String lower = moveName.toLowerCase(Locale.ROOT);
        if (lower.contains("dance") || lower.contains("roost") || lower.contains("recover") || lower.contains("protect")
            || lower.contains("substitute") || lower.contains("wisp") || lower.contains("spikes")) {
            return new MoveDefinition(moveName, inferType(lower), 0, MoveCategory.STATUS, true);
        }

        boolean special = lower.contains("beam") || lower.contains("blast") || lower.contains("pulse") || lower.contains("bolt")
            || lower.contains("wave") || lower.contains("buzz") || lower.contains("drain");
        return new MoveDefinition(moveName, inferType(lower), special ? 80 : 75, special ? MoveCategory.SPECIAL : MoveCategory.PHYSICAL, true);
    }

    private static String inferType(String lower) {
        if (lower.contains("fire")) return "fire";
        if (lower.contains("water") || lower.contains("hydro") || lower.contains("surf")) return "water";
        if (lower.contains("thunder") || lower.contains("volt")) return "electric";
        if (lower.contains("psychic") || lower.contains("psy")) return "psychic";
        if (lower.contains("shadow") || lower.contains("ghost")) return "ghost";
        if (lower.contains("dragon")) return "dragon";
        if (lower.contains("bug")) return "bug";
        if (lower.contains("ice")) return "ice";
        if (lower.contains("leaf") || lower.contains("grass") || lower.contains("drain")) return "grass";
        if (lower.contains("earth") || lower.contains("ground")) return "ground";
        if (lower.contains("air") || lower.contains("hurricane") || lower.contains("fly")) return "flying";
        if (lower.contains("dark") || lower.contains("night")) return "dark";
        if (lower.contains("fairy") || lower.contains("moon")) return "fairy";
        if (lower.contains("iron") || lower.contains("steel")) return "steel";
        if (lower.contains("poison") || lower.contains("sludge")) return "poison";
        if (lower.contains("rock") || lower.contains("stone")) return "rock";
        if (lower.contains("fight") || lower.contains("punch") || lower.contains("kick")) return "fighting";
        return "normal";
    }

    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    private static void register(MoveDefinition definition) {
        MOVES.put(key(definition.name()), definition);
    }
}
