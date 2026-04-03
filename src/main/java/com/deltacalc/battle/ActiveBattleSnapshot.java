package com.deltacalc.battle;

import java.util.List;

public record ActiveBattleSnapshot(
    String battleId,
    int turn,
    boolean demo,
    ActiveMonSnapshot playerActive,
    ActiveMonSnapshot opponentActive,
    String weather,
    String terrain,
    List<String> sideConditions,
    List<String> globalConditions
) {
}

