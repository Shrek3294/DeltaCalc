package com.deltacalc.battle;

import com.deltacalc.inference.GuessValue;
import java.util.List;

public record ActiveMonSnapshot(
    String speciesId,
    String displayName,
    int level,
    List<String> types,
    int currentHp,
    int maxHp,
    String status,
    StatStages statStages,
    GuessValue<String> ability,
    GuessValue<String> item,
    List<String> knownMoves,
    List<String> moveList,
    StatBlock baseStats,
    StatBlock effectiveStats
) {
}

