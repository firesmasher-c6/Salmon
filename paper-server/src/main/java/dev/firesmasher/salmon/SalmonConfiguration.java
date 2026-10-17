package dev.firesmasher.salmon;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

public record SalmonConfiguration(boolean enabled, Options options) {
    private static final String RESOURCE_DIRECTORY = "configurations/";

    public record Options(
        boolean chunkCapsEnabled,
        int maxPrimedTntPerChunk,
        int maxHostileEntitiesPerChunk,
        int maxPassiveEntitiesPerChunk,
        int maxWaterEntitiesPerChunk,
        int maxAmbientEntitiesPerChunk,
        boolean countNamedHostiles,
        boolean cleanupEnabled,
        int cleanupIntervalSeconds,
        int cleanupMinimumAgeSeconds,
        int cleanupMaxPerPass,
        Set<String> cleanupEntityTypes,
        boolean cleanupExcludeNamed,
        boolean cleanupExcludeTamed,
        boolean cleanupExcludeLeashed,
        boolean cleanupExcludePassengers,
        int cleanupProtectPlayerRadius,
        boolean pluginMetricsEnabled,
        int pluginReportIntervalSeconds,
        int pluginMaxEntries,
        long pluginMinimumReportMillis,
        boolean pluginTrackEventHandlers,
        boolean pluginTrackSchedulerTasks,
        long pluginSlowCallWarningMillis,
        boolean chunkyPregenerationEnabled,
        boolean modrinthServicesEnabled
    ) {
    }

    public static SalmonConfiguration load(final Path serverDirectory) throws IOException, InvalidConfigurationException {
        final Path configDirectory = serverDirectory.resolve("config");
        Files.createDirectories(configDirectory);

        final Path mainPath = serverDirectory.resolve("salmon.yml");
        final Path defaultsPath = configDirectory.resolve("salmon-defaults.yml");
        final Path optionsPath = configDirectory.resolve("salmon-opts.yml");
        copyIfMissing("salmon.yml", mainPath);
        copyIfMissing("salmon-defaults.yml", defaultsPath);
        copyIfMissing("salmon-opts.yml", optionsPath);

        final FileConfiguration main = loadFile(mainPath);
        final FileConfiguration defaults = loadFile(defaultsPath);
        final FileConfiguration options = loadFile(optionsPath);
        options.setDefaults(defaults);
        options.options().copyDefaults(true);

        final Set<String> cleanupTypes = options.getStringList("entities.cleanup.entity-types").stream()
            .map(type -> type.toLowerCase(Locale.ROOT))
            .collect(Collectors.toUnmodifiableSet());

        final Options parsedOptions = new Options(
            options.getBoolean("chunks.enabled"),
            nonNegativeInt(options, "chunks.max-primed-tnt-per-chunk"),
            nonNegativeInt(options, "chunks.max-hostile-entities-per-chunk"),
            nonNegativeInt(options, "chunks.max-passive-entities-per-chunk"),
            nonNegativeInt(options, "chunks.max-water-entities-per-chunk"),
            nonNegativeInt(options, "chunks.max-ambient-entities-per-chunk"),
            options.getBoolean("chunks.count-named-hostiles"),
            options.getBoolean("entities.cleanup.enabled"),
            positiveInt(options, "entities.cleanup.interval-seconds"),
            positiveInt(options, "entities.cleanup.minimum-age-seconds"),
            positiveInt(options, "entities.cleanup.max-per-pass"),
            cleanupTypes,
            options.getBoolean("entities.cleanup.exclude-named"),
            options.getBoolean("entities.cleanup.exclude-tamed"),
            options.getBoolean("entities.cleanup.exclude-leashed"),
            options.getBoolean("entities.cleanup.exclude-passengers"),
            nonNegativeInt(options, "entities.cleanup.protect-player-radius"),
            options.getBoolean("plugins.metrics.enabled"),
            positiveInt(options, "plugins.metrics.report-interval-seconds"),
            positiveInt(options, "plugins.metrics.max-plugins-in-report"),
            nonNegativeLong(options, "plugins.metrics.minimum-report-ms"),
            options.getBoolean("plugins.metrics.track-event-handlers"),
            options.getBoolean("plugins.metrics.track-scheduler-tasks"),
            nonNegativeLong(options, "plugins.metrics.slow-call-warning-ms"),
            options.getBoolean("plugins.chunky-pregeneration.enabled"),
            options.getBoolean("plugins.modrinth.enabled")
        );
        return new SalmonConfiguration(main.getBoolean("salmon.enabled", true), parsedOptions);
    }

    private static int nonNegativeInt(final FileConfiguration config, final String path) {
        final int value = config.getInt(path);
        if (value < 0) {
            throw new IllegalArgumentException("Salmon setting '" + path + "' must be zero or greater");
        }
        return value;
    }

    private static int positiveInt(final FileConfiguration config, final String path) {
        final int value = config.getInt(path);
        if (value < 1) {
            throw new IllegalArgumentException("Salmon setting '" + path + "' must be greater than zero");
        }
        return value;
    }

    private static long nonNegativeLong(final FileConfiguration config, final String path) {
        final long value = config.getLong(path);
        if (value < 0L) {
            throw new IllegalArgumentException("Salmon setting '" + path + "' must be zero or greater");
        }
        return value;
    }

    private static FileConfiguration loadFile(final Path path) throws IOException, InvalidConfigurationException {
        final YamlConfiguration configuration = new YamlConfiguration();
        configuration.load(path.toFile());
        return configuration;
    }

    private static void copyIfMissing(final String fileName, final Path target) throws IOException {
        if (Files.exists(target)) {
            return;
        }
        try (InputStream input = SalmonConfiguration.class.getClassLoader().getResourceAsStream(RESOURCE_DIRECTORY + fileName)) {
            if (input == null) {
                throw new IOException("Missing Salmon default resource: " + RESOURCE_DIRECTORY + fileName);
            }
            Files.copy(input, target);
        }
    }
}
