package com.deltacalc.calc;

import com.deltacalc.battle.ActiveMonSnapshot;
import com.deltacalc.battle.BattleSide;
import com.deltacalc.inference.ConfidenceBand;
import java.util.ArrayList;
import java.util.List;

public final class SimpleDamageCalculator {
    public DamageResult calculate(BattleSide attackerSide, ActiveMonSnapshot attacker, ActiveMonSnapshot defender, String moveName) {
        MoveDefinition move = MoveDex.resolve(moveName);
        List<String> warnings = new ArrayList<>();
        if (move.fallback()) {
            warnings.add("Fallback move data");
        }
        if (move.category() == MoveCategory.STATUS || move.power() <= 0) {
            return new DamageResult(move.name(), attackerSide, 0, 0, 0.0, 0.0, "Status", ConfidenceBand.LOW, warnings);
        }

        int rawAttack = move.category() == MoveCategory.PHYSICAL ? attacker.effectiveStats().atk() : attacker.effectiveStats().spa();
        int rawDefense = move.category() == MoveCategory.PHYSICAL ? defender.effectiveStats().def() : defender.effectiveStats().spd();
        int attack = Math.max(1, applyStage(rawAttack, move.category() == MoveCategory.PHYSICAL ? attacker.statStages().atk() : attacker.statStages().spa()));
        int defense = Math.max(1, applyStage(rawDefense, move.category() == MoveCategory.PHYSICAL ? defender.statStages().def() : defender.statStages().spd()));

        double base = (((2.0 * attacker.level() / 5.0 + 2.0) * move.power() * attack / defense) / 50.0) + 2.0;
        double stab = attacker.types().stream().anyMatch(type -> type.equalsIgnoreCase(move.type())) ? 1.5 : 1.0;
        double effectiveness = TypeChart.effectiveness(move.type(), defender.types());
        double itemModifier = attacker.item() != null && "Life Orb".equalsIgnoreCase(attacker.item().value()) ? 1.3 : 1.0;
        double burnModifier = "burn".equalsIgnoreCase(attacker.status()) && move.category() == MoveCategory.PHYSICAL ? 0.5 : 1.0;
        double modifier = stab * effectiveness * itemModifier * burnModifier;

        int singleMax = effectiveness == 0.0 ? 0 : (int) Math.max(1, Math.floor(base * modifier));
        int singleMin = effectiveness == 0.0 ? 0 : (int) Math.max(1, Math.floor(base * modifier * 0.85));
        int maxDamage = singleMax * move.maxHits();
        int minDamage = singleMin * move.minHits();
        if (move.isMultiHit()) {
            String hitLabel = move.minHits() == move.maxHits()
                ? move.minHits() + " hits"
                : move.minHits() + "-" + move.maxHits() + " hits";
            warnings.add("Multi-hit (" + hitLabel + ")");
        }
        double minPercent = roundPercent(minDamage, defender.maxHp());
        double maxPercent = roundPercent(maxDamage, defender.maxHp());

        if (effectiveness == 0.0) {
            warnings.add("No effect");
        }

        return new DamageResult(
            move.name(),
            attackerSide,
            minDamage,
            maxDamage,
            minPercent,
            maxPercent,
            koLabel(defender.currentHp(), minDamage, maxDamage),
            warnings.isEmpty() ? ConfidenceBand.MEDIUM : ConfidenceBand.LOW,
            warnings
        );
    }

    private int applyStage(int stat, int stage) {
        if (stage == 0) {
            return stat;
        }
        if (stage > 0) {
            return stat * (2 + stage) / 2;
        }
        return stat * 2 / (2 + Math.abs(stage));
    }

    private double roundPercent(int damage, int hp) {
        return Math.round((damage * 1000.0 / Math.max(1, hp))) / 10.0;
    }

    private String koLabel(int currentHp, int minDamage, int maxDamage) {
        if (maxDamage <= 0) {
            return "--";
        }
        if (minDamage >= currentHp) {
            return "OHKO";
        }
        int hits = (int) Math.ceil(currentHp / (double) maxDamage);
        return hits <= 1 ? "OHKO" : hits + "HKO";
    }
}
