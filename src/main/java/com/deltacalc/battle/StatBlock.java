package com.deltacalc.battle;

public record StatBlock(int hp, int atk, int def, int spa, int spd, int spe) {
    public static final StatBlock EMPTY = new StatBlock(1, 1, 1, 1, 1, 1);
}

