package com.deltacalc.data;

import com.deltacalc.battle.StatBlock;

public record EvSpreadUsage(
    String nature,
    StatBlock evs,
    double usagePercent
) {
}

