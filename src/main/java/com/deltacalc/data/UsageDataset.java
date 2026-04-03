package com.deltacalc.data;

import java.util.List;

public record UsageDataset(
    SourceMeta sourceMeta,
    List<SpeciesUsageEntry> species
) {
}

