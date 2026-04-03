package com.deltacalc.calc;

public record MoveDefinition(
    String name,
    String type,
    int power,
    MoveCategory category,
    boolean fallback
) {
}

