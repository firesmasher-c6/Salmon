package dev.firesmasher.salmon.runtime;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

public final class SalmonMotdManager {
    private static final Pattern PLAYER_NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private final Path configurationPath;
    private volatile State state;

    public SalmonMotdManager(final Path configurationPath) throws IOException, InvalidConfigurationException {
        this.configurationPath = configurationPath;
        this.state = readState();
    }

    public boolean maintenanceEnabled() {
        return this.state.maintenanceEnabled();
    }

    public Set<String> maintenanceWhitelist() {
        return this.state.maintenanceWhitelist();
    }

    public synchronized void setMaintenance(final boolean enabled) throws IOException, InvalidConfigurationException {
        this.updateConfiguration(config -> config.set("smp.maintenance.enabled", enabled));
    }

    public synchronized void addMaintenanceWhitelist(final String entry) throws IOException, InvalidConfigurationException {
        final String normalized = normalizeEntry(entry);
        this.updateConfiguration(config -> {
            final List<String> entries = new ArrayList<>(config.getStringList("smp.maintenance.whitelist"));
            if (entries.stream().noneMatch(existing -> existing.equalsIgnoreCase(normalized))) {
                entries.add(normalized);
            }
            config.set("smp.maintenance.whitelist", entries);
        });
    }

    public synchronized boolean removeMaintenanceWhitelist(final String entry) throws IOException, InvalidConfigurationException {
        final String normalized = normalizeEntry(entry);
        final boolean[] removed = {false};
        this.updateConfiguration(config -> {
            final List<String> entries = new ArrayList<>(config.getStringList("smp.maintenance.whitelist"));
            removed[0] = entries.removeIf(existing -> existing.equalsIgnoreCase(normalized));
            config.set("smp.maintenance.whitelist", entries);
        });
        return removed[0];
    }

    public synchronized void reload() throws IOException, InvalidConfigurationException {
        this.state = readState();
    }

    public boolean allowsLogin(final String playerName, final UUID playerId) {
        final State current = this.state;
        if (!current.maintenanceEnabled()) {
            return true;
        }
        return current.maintenanceWhitelist().contains(playerId.toString())
            || current.maintenanceWhitelist().contains(playerName.toLowerCase(Locale.ROOT));
    }

    public Component render(final int onlinePlayers, final int maxPlayers) {
        final State current = this.state;
        final List<String> lines = current.maintenanceEnabled() ? current.maintenanceMotd() : current.motd();
        final String rendered = lines.stream()
            .map(line -> line.replace("{online}", Integer.toString(onlinePlayers))
                .replace("{max}", Integer.toString(maxPlayers)))
            .collect(Collectors.joining("\n"));
        return LEGACY.deserialize(rendered);
    }

    private void updateConfiguration(final ConfigurationUpdate update) throws IOException, InvalidConfigurationException {
        final YamlConfiguration configuration = loadConfiguration();
        update.apply(configuration);
        configuration.save(this.configurationPath.toFile());
        this.state = readState();
    }

    private State readState() throws IOException, InvalidConfigurationException {
        final YamlConfiguration configuration = loadConfiguration();
        final List<String> motd = configuration.getStringList("motd.lines");
        final List<String> maintenanceMotd = configuration.getStringList("motd.maintenance-lines");
        return new State(
            motd.isEmpty() ? List.of("&bSalmon SMP", "&7A Salmon server &8({online}/{max})") : List.copyOf(motd),
            maintenanceMotd.isEmpty() ? List.of("&bSalmon SMP", "&cMaintenance in progress") : List.copyOf(maintenanceMotd),
            configuration.getBoolean("smp.maintenance.enabled", false),
            configuration.getStringList("smp.maintenance.whitelist").stream()
                .map(entry -> entry.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet())
        );
    }

    private YamlConfiguration loadConfiguration() throws IOException, InvalidConfigurationException {
        final YamlConfiguration configuration = new YamlConfiguration();
        configuration.load(this.configurationPath.toFile());
        return configuration;
    }

    private static String normalizeEntry(final String entry) {
        final String normalized = entry.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Enter a player name or UUID");
        }
        try {
            return UUID.fromString(normalized).toString();
        } catch (IllegalArgumentException ignored) {
            if (!PLAYER_NAME.matcher(normalized).matches()) {
                throw new IllegalArgumentException("Enter a valid player name (1-16 letters, numbers, or underscores) or UUID");
            }
            return normalized.toLowerCase(Locale.ROOT);
        }
    }

    @FunctionalInterface
    private interface ConfigurationUpdate {
        void apply(YamlConfiguration configuration);
    }

    private record State(List<String> motd, List<String> maintenanceMotd, boolean maintenanceEnabled,
        Set<String> maintenanceWhitelist) {
    }
}
