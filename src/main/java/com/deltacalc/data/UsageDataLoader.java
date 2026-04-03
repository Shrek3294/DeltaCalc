package com.deltacalc.data;

import com.google.gson.Gson;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;

public final class UsageDataLoader {
    private static final Gson GSON = new Gson();

    private UsageDataLoader() {
    }

    public static UsageDatabase loadBundled(String resourcePath, Logger logger) {
        try (InputStream stream = UsageDataLoader.class.getResourceAsStream(resourcePath)) {
            if (stream == null) {
                logger.warn("Missing usage resource {}", resourcePath);
                return new UsageDatabase(new UsageDataset(new SourceMeta("missing", "unknown", "unknown", "unknown", "unknown"), java.util.List.of()));
            }
            UsageDataset dataset = GSON.fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), UsageDataset.class);
            return new UsageDatabase(dataset);
        } catch (Exception exception) {
            logger.error("Failed to load usage data from {}", resourcePath, exception);
            return new UsageDatabase(new UsageDataset(new SourceMeta("error", "unknown", "unknown", "unknown", "unknown"), java.util.List.of()));
        }
    }
}

