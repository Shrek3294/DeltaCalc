package com.deltacalc.battle;

public record StatStages(int atk, int def, int spa, int spd, int spe) {
    public static final StatStages NEUTRAL = new StatStages(0, 0, 0, 0, 0);
}

