package com.deltacalc.data;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class UsageDatabase {
    private final Map<String, SpeciesUsageEntry> byKey = new HashMap<>();

    public UsageDatabase(UsageDataset dataset) {
        if (dataset == null || dataset.species() == null) {
            return;
        }

        for (SpeciesUsageEntry entry : dataset.species()) {
            index(entry.speciesId(), entry);
            index(entry.slug(), entry);
            index(entry.displayName(), entry);
            if (entry.aliases() != null) {
                entry.aliases().forEach(alias -> index(alias, entry));
            }
        }
    }

    public Optional<SpeciesUsageEntry> find(String speciesKey) {
        if (speciesKey == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(byKey.get(normalize(speciesKey)));
    }

    public int size() {
        return (int) byKey.values().stream().distinct().count();
    }

    private void index(String key, SpeciesUsageEntry entry) {
        if (key != null && !key.isBlank()) {
            byKey.put(normalize(key), entry);
        }
    }

    public static String normalize(String raw) {
        return raw.toLowerCase(Locale.ROOT)
            .replace(" ", "-")
            .replace("_", "-");
    }
}

