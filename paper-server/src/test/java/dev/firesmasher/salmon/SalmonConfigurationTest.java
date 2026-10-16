package dev.firesmasher.salmon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SalmonConfigurationTest {
    @TempDir
    Path serverDirectory;

    @Test
    void createsTheThreeFilesAndLoadsActiveChunkCaps() throws Exception {
        final SalmonConfiguration configuration = SalmonConfiguration.load(this.serverDirectory);

        assertTrue(configuration.enabled());
        assertTrue(configuration.options().chunkCapsEnabled());
        assertEquals(16, configuration.options().maxPrimedTntPerChunk());
        assertEquals(32, configuration.options().maxHostileEntitiesPerChunk());
        assertEquals(64, configuration.options().maxPassiveEntitiesPerChunk());
        assertEquals(48, configuration.options().maxWaterEntitiesPerChunk());
        assertEquals(16, configuration.options().maxAmbientEntitiesPerChunk());
        assertFalse(configuration.options().cleanupEnabled());
        assertTrue(configuration.options().pluginMetricsEnabled());
        assertEquals(
            java.util.Set.of("minecraft:zombie"),
            configuration.options().cleanupEntityTypes()
        );
        assertTrue(Files.isRegularFile(this.serverDirectory.resolve("salmon.yml")));
        assertTrue(Files.isRegularFile(this.serverDirectory.resolve("config/salmon-defaults.yml")));
        assertTrue(Files.isRegularFile(this.serverDirectory.resolve("config/salmon-opts.yml")));
    }

    @Test
    void appliesOptionsOverrides() throws Exception {
        SalmonConfiguration.load(this.serverDirectory);
        Files.writeString(this.serverDirectory.resolve("config/salmon-opts.yml"), """
            chunks:
              enabled: true
              max-primed-tnt-per-chunk: 24
              max-passive-entities-per-chunk: 80
            plugins:
              metrics:
                report-interval-seconds: 90
            """);

        final SalmonConfiguration configuration = SalmonConfiguration.load(this.serverDirectory);

        assertTrue(configuration.options().chunkCapsEnabled());
        assertEquals(24, configuration.options().maxPrimedTntPerChunk());
        assertEquals(80, configuration.options().maxPassiveEntitiesPerChunk());
        assertEquals(90, configuration.options().pluginReportIntervalSeconds());
        assertFalse(configuration.options().cleanupEnabled());
    }

    @Test
    void rejectsInvalidNumericOverrides() throws Exception {
        SalmonConfiguration.load(this.serverDirectory);
        Files.writeString(this.serverDirectory.resolve("config/salmon-opts.yml"), """
            chunks:
              max-hostile-entities-per-chunk: -1
            """);

        org.junit.jupiter.api.Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> SalmonConfiguration.load(this.serverDirectory)
        );
    }
}
