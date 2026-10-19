package dev.firesmasher.salmon.config;

import java.io.IOException;
import java.io.InputStreamReader;
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

    public enum RedstoneImplementation {
        PAPER,
        VANILLA,
        EIGENCRAFT,
        ALTERNATE_CURRENT
    }

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
        boolean modrinthServicesEnabled,
        boolean performanceActivationRangesEnabled,
        String performanceActivationRangePreset,
        int performanceAnimalActivationRange,
        int performanceMonsterActivationRange,
        int performanceRaiderActivationRange,
        int performanceMiscActivationRange,
        int performanceFlyingMonsterActivationRange,
        int performanceWaterActivationRange,
        int performanceVillagerActivationRange,
        boolean performanceChunkLoadingEnabled,
        int performancePlayerMaxConcurrentChunkGenerates,
        double performancePlayerMaxChunkLoadRate,
        double performancePlayerMaxChunkGenerateRate,
        boolean performanceAsyncSchedulingEnabled,
        int performanceAsyncIoThreads,
        int performanceAsyncWorkerThreads,
        boolean performanceTrackingRangesEnabled,
        int performancePlayerTrackingRange,
        int performanceMonsterTrackingRange,
        int performanceAnimalTrackingRange,
        int performanceMiscTrackingRange,
        int performanceDisplayTrackingRange,
        int performanceOtherTrackingRange,
        boolean performanceCollisionsEnabled,
        int performanceMaxEntityCollisions,
        boolean performanceOnlyPlayersCollide,
        boolean performanceWorldTuningEnabled,
        double performanceItemMergeRadius,
        double performanceExperienceMergeRadius,
        int performanceNonPlayerArrowDespawnRate,
        boolean performanceHopperCooldownWhenFull,
        boolean performanceTickInactiveVillagers,
        boolean performanceVillagerWakeUpEnabled,
        int performanceVillagerWakeUpMaxPerTick,
        int performanceVillagerWakeUpEvery,
        int performanceVillagerWakeUpFor,
        boolean performanceTickSendEnabled,
        int performanceTickDistance,
        int performanceSendDistance,
        RedstoneImplementation redstoneImplementation
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

        final FileConfiguration defaultMain = loadResource("salmon.yml");
        final FileConfiguration defaultOptions = defaults;

        // Self-heal: merge any keys present in the shipped defaults but missing from
        // an operator's existing configuration file. Existing values are never
        // overwritten, so upgrades add new knobs without clobbering customization.
        if (mergeMissing(main, defaultMain)) {
            main.save(mainPath.toFile());
        }
        if (mergeMissing(options, defaultOptions)) {
            options.save(optionsPath.toFile());
        }

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
            options.getBoolean("plugins.modrinth.enabled"),
            options.getBoolean("performance.activation-ranges.enabled"),
            options.getString("performance.activation-ranges.preset", "vanilla"),
            optimizationActivationRange(options, "performance.activation-ranges.animals"),
            optimizationActivationRange(options, "performance.activation-ranges.monsters"),
            optimizationActivationRange(options, "performance.activation-ranges.raiders"),
            optimizationActivationRange(options, "performance.activation-ranges.misc"),
            optimizationActivationRange(options, "performance.activation-ranges.flying-monsters"),
            optimizationActivationRange(options, "performance.activation-ranges.water"),
            optimizationActivationRange(options, "performance.activation-ranges.villagers"),
            options.getBoolean("performance.chunk-loading.enabled"),
            optimizationActivationRange(options, "performance.chunk-loading.player-max-concurrent-chunk-generates"),
            doubleOption(options, "performance.chunk-loading.player-max-chunk-load-rate"),
            doubleOption(options, "performance.chunk-loading.player-max-chunk-generate-rate"),
            options.getBoolean("performance.async-scheduling.enabled"),
            optimizationActivationRange(options, "performance.async-scheduling.io-threads"),
            optimizationActivationRange(options, "performance.async-scheduling.worker-threads"),
            options.getBoolean("performance.tracking-ranges.enabled"),
            optimizationActivationRange(options, "performance.tracking-ranges.player"),
            optimizationActivationRange(options, "performance.tracking-ranges.monster"),
            optimizationActivationRange(options, "performance.tracking-ranges.animal"),
            optimizationActivationRange(options, "performance.tracking-ranges.misc"),
            optimizationActivationRange(options, "performance.tracking-ranges.display"),
            optimizationActivationRange(options, "performance.tracking-ranges.other"),
            options.getBoolean("performance.collisions.enabled"),
            optimizationActivationRange(options, "performance.collisions.max-entity-collisions"),
            options.getBoolean("performance.collisions.only-players-collide"),
            options.getBoolean("performance.world-tuning.enabled"),
            doubleOption(options, "performance.world-tuning.item-merge-radius"),
            doubleOption(options, "performance.world-tuning.experience-merge-radius"),
            optimizationActivationRange(options, "performance.world-tuning.non-player-arrow-despawn-rate"),
            options.getBoolean("performance.world-tuning.hopper-cooldown-when-full"),
            options.getBoolean("performance.world-tuning.tick-inactive-villagers"),
            options.getBoolean("performance.world-tuning.villager-wake-up.enabled"),
            optimizationActivationRange(options, "performance.world-tuning.villager-wake-up.max-per-tick"),
            optimizationActivationRange(options, "performance.world-tuning.villager-wake-up.every"),
            optimizationActivationRange(options, "performance.world-tuning.villager-wake-up.for"),
            options.getBoolean("performance.tick-send-distance.enabled"),
            optimizationActivationRange(options, "performance.tick-send-distance.tick"),
            optimizationActivationRange(options, "performance.tick-send-distance.send"),
            redstoneImplementation(options.getString("redstone.implementation", "alternate_current"))
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

    private static RedstoneImplementation redstoneImplementation(final String value) {
        return switch (value.toLowerCase(Locale.ROOT).replace('-', '_')) {
            case "paper" -> RedstoneImplementation.PAPER;
            case "vanilla" -> RedstoneImplementation.VANILLA;
            case "eigencraft" -> RedstoneImplementation.EIGENCRAFT;
            case "alternate_current" -> RedstoneImplementation.ALTERNATE_CURRENT;
            default -> throw new IllegalArgumentException(
                "Salmon setting 'redstone.implementation' must be paper, vanilla, eigencraft, or alternate_current"
            );
        };
    }


    private static FileConfiguration loadResource(final String fileName) throws IOException, InvalidConfigurationException {
        try (InputStream input = SalmonConfiguration.class.getClassLoader().getResourceAsStream(RESOURCE_DIRECTORY + fileName)) {
            if (input == null) {
                throw new IOException("Missing Salmon default resource: " + RESOURCE_DIRECTORY + fileName);
            }
            final YamlConfiguration configuration = new YamlConfiguration();
            configuration.load(new InputStreamReader(input, java.nio.charset.StandardCharsets.UTF_8));
            return configuration;
        }
    }

    private static boolean mergeMissing(final org.bukkit.configuration.ConfigurationSection target, final org.bukkit.configuration.ConfigurationSection defaults) {
        boolean changed = false;
        for (final String key : defaults.getKeys(false)) {
            if (defaults.isConfigurationSection(key)) {
                if (!target.isConfigurationSection(key)) {
                    target.createSection(key);
                    changed = true;
                }
                if (mergeMissing(target.getConfigurationSection(key), defaults.getConfigurationSection(key))) {
                    changed = true;
                }
            } else if (!target.getKeys(false).contains(key)) {
                target.set(key, defaults.get(key));
                changed = true;
            }
        }
        return changed;
    }


    private static int optimizationActivationRange(final FileConfiguration options, final String path) {
        final int value = options.getInt(path, -1);
        if (value < -1) {
            throw new IllegalArgumentException("Salmon setting '" + path + "' must be -1 or greater");
        }
        return value;
    }


    private static double doubleOption(final FileConfiguration options, final String path) {
        return options.getDouble(path, -1.0);
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
