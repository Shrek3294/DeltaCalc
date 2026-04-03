package com.deltacalc.calc;

import com.deltacalc.battle.BattleSide;
import com.deltacalc.inference.ConfidenceBand;
import java.util.List;

public record DamageResult(
    String moveId,
    BattleSide attackerSide,
    int minDamage,
    int maxDamage,
    double minPercent,
    double maxPercent,
    String koLabel,
    ConfidenceBand confidence,
    List<String> warnings
) {
}

