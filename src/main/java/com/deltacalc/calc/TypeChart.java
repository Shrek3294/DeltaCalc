package com.deltacalc.calc;

import java.util.List;
import java.util.Map;

public final class TypeChart {
    private static final Map<String, Map<String, Double>> CHART = Map.ofEntries(
        Map.entry("normal", Map.of("rock", 0.5, "ghost", 0.0, "steel", 0.5)),
        Map.entry("fire", Map.of("fire", 0.5, "water", 0.5, "grass", 2.0, "ice", 2.0, "bug", 2.0, "rock", 0.5, "dragon", 0.5, "steel", 2.0)),
        Map.entry("water", Map.of("fire", 2.0, "water", 0.5, "grass", 0.5, "ground", 2.0, "rock", 2.0, "dragon", 0.5)),
        Map.entry("electric", Map.of("water", 2.0, "electric", 0.5, "grass", 0.5, "ground", 0.0, "flying", 2.0, "dragon", 0.5)),
        Map.entry("grass", Map.of("fire", 0.5, "water", 2.0, "grass", 0.5, "poison", 0.5, "ground", 2.0, "flying", 0.5, "bug", 0.5, "rock", 2.0, "dragon", 0.5, "steel", 0.5)),
        Map.entry("ice", Map.of("fire", 0.5, "water", 0.5, "grass", 2.0, "ground", 2.0, "flying", 2.0, "dragon", 2.0, "steel", 0.5, "ice", 0.5)),
        Map.entry("fighting", Map.ofEntries(
            Map.entry("normal", 2.0),
            Map.entry("ice", 2.0),
            Map.entry("poison", 0.5),
            Map.entry("flying", 0.5),
            Map.entry("psychic", 0.5),
            Map.entry("bug", 0.5),
            Map.entry("rock", 2.0),
            Map.entry("ghost", 0.0),
            Map.entry("dark", 2.0),
            Map.entry("steel", 2.0),
            Map.entry("fairy", 0.5)
        )),
        Map.entry("poison", Map.of("grass", 2.0, "poison", 0.5, "ground", 0.5, "rock", 0.5, "ghost", 0.5, "steel", 0.0, "fairy", 2.0)),
        Map.entry("ground", Map.of("fire", 2.0, "electric", 2.0, "grass", 0.5, "poison", 2.0, "flying", 0.0, "bug", 0.5, "rock", 2.0, "steel", 2.0)),
        Map.entry("flying", Map.of("electric", 0.5, "grass", 2.0, "fighting", 2.0, "bug", 2.0, "rock", 0.5, "steel", 0.5)),
        Map.entry("psychic", Map.of("fighting", 2.0, "poison", 2.0, "psychic", 0.5, "dark", 0.0, "steel", 0.5)),
        Map.entry("bug", Map.of("fire", 0.5, "grass", 2.0, "fighting", 0.5, "poison", 0.5, "flying", 0.5, "psychic", 2.0, "ghost", 0.5, "dark", 2.0, "steel", 0.5, "fairy", 0.5)),
        Map.entry("rock", Map.of("fire", 2.0, "ice", 2.0, "fighting", 0.5, "ground", 0.5, "flying", 2.0, "bug", 2.0, "steel", 0.5)),
        Map.entry("ghost", Map.of("normal", 0.0, "psychic", 2.0, "ghost", 2.0, "dark", 0.5)),
        Map.entry("dragon", Map.of("dragon", 2.0, "steel", 0.5, "fairy", 0.0)),
        Map.entry("dark", Map.of("fighting", 0.5, "psychic", 2.0, "ghost", 2.0, "dark", 0.5, "fairy", 0.5)),
        Map.entry("steel", Map.of("fire", 0.5, "water", 0.5, "electric", 0.5, "ice", 2.0, "rock", 2.0, "steel", 0.5, "fairy", 2.0)),
        Map.entry("fairy", Map.of("fire", 0.5, "fighting", 2.0, "poison", 0.5, "dragon", 2.0, "dark", 2.0, "steel", 0.5))
    );

    private TypeChart() {
    }

    public static double effectiveness(String attackType, List<String> defenderTypes) {
        if (attackType == null || defenderTypes == null || defenderTypes.isEmpty()) {
            return 1.0;
        }

        Map<String, Double> row = CHART.getOrDefault(attackType.toLowerCase(), Map.of());
        double result = 1.0;
        for (String type : defenderTypes) {
            result *= row.getOrDefault(type.toLowerCase(), 1.0);
        }
        return result;
    }
}
