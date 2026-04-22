package com.deltacalc.calc;

public record MoveDefinition(
    String name,
    String type,
    int power,
    MoveCategory category,
    boolean fallback,
    int minHits,
    int maxHits
) {
    public MoveDefinition(String name, String type, int power, MoveCategory category, boolean fallback) {
        this(name, type, power, category, fallback, 1, 1);
    }

    public boolean isMultiHit() {
        return maxHits > 1;
    }
}

