package dev.firesmasher.salmon;

import dev.firesmasher.salmon.config.SalmonConfiguration;
import dev.firesmasher.salmon.runtime.SalmonMotdManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
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
        assertFalse(configuration.options().pluginMetricsEnabled());
        assertTrue(configuration.options().chunkyPregenerationEnabled());
        assertTrue(configuration.options().modrinthServicesEnabled());
        assertEquals(SalmonConfiguration.RedstoneImplementation.ALTERNATE_CURRENT,
            configuration.options().redstoneImplementation());
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
              chunky-pregeneration:
                enabled: false
              modrinth:
                enabled: false
              metrics:
                enabled: true
                report-interval-seconds: 90
            """);

        final SalmonConfiguration configuration = SalmonConfiguration.load(this.serverDirectory);

        assertTrue(configuration.options().chunkCapsEnabled());
        assertEquals(24, configuration.options().maxPrimedTntPerChunk());
        assertEquals(80, configuration.options().maxPassiveEntitiesPerChunk());
        assertEquals(90, configuration.options().pluginReportIntervalSeconds());
        assertTrue(configuration.options().pluginMetricsEnabled());
        assertFalse(configuration.options().chunkyPregenerationEnabled());
        assertFalse(configuration.options().modrinthServicesEnabled());
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

    @Test
    void acceptsPaperAndVanillaRedstoneModesAndRejectsUnknownModes() throws Exception {
        SalmonConfiguration.load(this.serverDirectory);
        final Path optionsPath = this.serverDirectory.resolve("config/salmon-opts.yml");

        Files.writeString(optionsPath, "redstone:\n  implementation: paper\n");
        assertEquals(SalmonConfiguration.RedstoneImplementation.PAPER,
            SalmonConfiguration.load(this.serverDirectory).options().redstoneImplementation());

        Files.writeString(optionsPath, "redstone:\n  implementation: vanilla\n");
        assertEquals(SalmonConfiguration.RedstoneImplementation.VANILLA,
            SalmonConfiguration.load(this.serverDirectory).options().redstoneImplementation());

        Files.writeString(optionsPath, "redstone:\n  implementation: not-a-redstone-engine\n");
        org.junit.jupiter.api.Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> SalmonConfiguration.load(this.serverDirectory)
        );
    }

    @Test
    void buildsMotdAndEnforcesPersistedMaintenanceWhitelist() throws Exception {
        SalmonConfiguration.load(this.serverDirectory);
        final SalmonMotdManager manager = new SalmonMotdManager(this.serverDirectory.resolve("salmon.yml"));

        assertEquals("Salmon SMP\nA Salmon server (2/20)",
            PlainTextComponentSerializer.plainText().serialize(manager.render(2, 20)));
        assertTrue(manager.allowsLogin("Guest", UUID.randomUUID()));

        manager.setMaintenance(true);
        final UUID staffId = UUID.randomUUID();
        assertFalse(manager.allowsLogin("Guest", UUID.randomUUID()));
        manager.addMaintenanceWhitelist("StaffName");
        manager.addMaintenanceWhitelist(staffId.toString());
        assertTrue(manager.allowsLogin("staffname", UUID.randomUUID()));
        assertTrue(manager.allowsLogin("SomeoneElse", staffId));
        assertEquals("Salmon SMP\nMaintenance in progress",
            PlainTextComponentSerializer.plainText().serialize(manager.render(2, 20)));

        assertTrue(manager.removeMaintenanceWhitelist("STAFFNAME"));
        assertFalse(manager.allowsLogin("staffname", UUID.randomUUID()));
        manager.setMaintenance(false);
        assertTrue(manager.allowsLogin("Guest", UUID.randomUUID()));

        final SalmonMotdManager reloaded = new SalmonMotdManager(this.serverDirectory.resolve("salmon.yml"));
        assertFalse(reloaded.maintenanceEnabled());
        assertEquals(Set.of(staffId.toString()), reloaded.maintenanceWhitelist());
    }
}
