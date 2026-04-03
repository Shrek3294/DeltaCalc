package com.deltacalc.data;

public record UsageOption(
    String id,
    String displayName,
    String type,
    double usagePercent,
    Integer sampleCount
) {
}

