package com.deltacalc.data;

import java.util.List;

public record SpeciesUsageEntry(
    String speciesId,
    String displayName,
    String slug,
    int usageRank,
    double usagePercent,
    int sampleCount,
    List<String> aliases,
    List<UsageOption> moves,
    List<UsageOption> items,
    List<UsageOption> abilities,
    List<EvSpreadUsage> spreads
) {
}

