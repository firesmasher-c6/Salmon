package dev.firesmasher.salmon.runtime;

import dev.firesmasher.salmon.PluginExecutionMetrics;

import dev.firesmasher.salmon.service.ModrinthService;

import dev.firesmasher.salmon.config.SalmonConfiguration;



import dev.firesmasher.salmon.PluginExecutionMetrics.Snapshot;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.plugin.Plugin;
import org.bukkit.craftbukkit.CraftServer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

public final class SalmonRuntime implements AutoCloseable {
    private static final Logger LOGGER = Logger.getLogger("Salmon");
    private static volatile SalmonRuntime instance;
    private static volatile boolean eventTimingEnabled;
    private static volatile boolean taskTimingEnabled;

    private final CraftServer server;
    private final SalmonConfiguration configuration;
    private final ScheduledExecutorService executor;
    private final ExecutorService modrinthExecutor;
    private final ExecutorService apiExecutor;
    private final ModrinthService modrinthService;
    private final SalmonMotdManager motdManager;
    private final Map<String, Long> lastSlowWarningNanos = new HashMap<>();

    private SalmonRuntime(final CraftServer server, final SalmonConfiguration configuration, final Path serverDirectory,
        final Path pluginDirectory) throws IOException, org.bukkit.configuration.InvalidConfigurationException {
        this.server = server;
        this.configuration = configuration;
        this.modrinthService = new ModrinthService(pluginDirectory, net.minecraft.SharedConstants.getCurrentVersion().name());
        this.motdManager = new SalmonMotdManager(serverDirectory.resolve("salmon.yml"));
        this.executor = Executors.newSingleThreadScheduledExecutor(task -> {
            final Thread thread = new Thread(task, "SalmonMC Thread");
            thread.setDaemon(true);
            return thread;
        });
        this.modrinthExecutor = Executors.newFixedThreadPool(2, task -> {
            final Thread thread = new Thread(task, "Salmon Modrinth Thread");
            thread.setDaemon(true);
            return thread;
        });
        this.apiExecutor = Executors.newFixedThreadPool(2, task -> {
            final Thread thread = new Thread(task, "Salmon API Worker");
            thread.setDaemon(true);
            return thread;
        });
    }

    public static SalmonRuntime start(final CraftServer server, final SalmonConfiguration configuration,
        final Path serverDirectory, final Path pluginDirectory)
        throws IOException, org.bukkit.configuration.InvalidConfigurationException {
        final SalmonRuntime runtime = new SalmonRuntime(server, configuration, serverDirectory, pluginDirectory);
        instance = runtime;
        SalmonAPIImpl.install(runtime);
        final SalmonConfiguration.Options options = configuration.options();

        final boolean firstStart = !Files.exists(serverDirectory.resolve(".salmon-first-start"));
        if (firstStart) {
            try {
                Files.writeString(serverDirectory.resolve(".salmon-first-start"), "created at server start");
            } catch (java.io.IOException ignored) {
                // Marker file is optional guidance; never fail startup over it.
            }
        }
        PluginExecutionMetrics.setEnabled(configuration.enabled() && options.pluginMetricsEnabled());
        eventTimingEnabled = configuration.enabled() && options.pluginMetricsEnabled() && options.pluginTrackEventHandlers();
        taskTimingEnabled = configuration.enabled() && options.pluginMetricsEnabled() && options.pluginTrackSchedulerTasks();

        if (configuration.enabled()) {
            runtime.executor.execute(() -> {
                LOGGER.info("Salmon optimizer started on " + Thread.currentThread().getName());
                LOGGER.info("Salmon settings: chunk caps " + (options.chunkCapsEnabled() ? "enabled" : "disabled")
                    + " (max " + options.maxPrimedTntPerChunk() + " primed TNT and "
                    + options.maxHostileEntitiesPerChunk() + " hostile, "
                    + options.maxPassiveEntitiesPerChunk() + " passive, "
                    + options.maxWaterEntitiesPerChunk() + " water, and "
                    + options.maxAmbientEntitiesPerChunk() + " ambient entities per chunk), persistent-mob cleanup "
                    + (options.cleanupEnabled() ? "enabled" : "disabled") + ", plugin metrics "
                    + (options.pluginMetricsEnabled() ? "enabled" : "disabled") + ", redstone "
                    + options.redstoneImplementation().name().toLowerCase(java.util.Locale.ROOT));
            });
        } else {
            LOGGER.warning("Salmon is disabled in salmon.yml; Salmon optimizations will not run");
        }
        if (configuration.enabled() && firstStart) {
            runtime.executor.execute(() -> {
                LOGGER.warning("Salmon tip: this looks like a fresh server. Before inviting players, pregenerate chunks (for example with the bundled Chunky plugin via /chunky or 'chunky world <world> add ...') so the world is ready and avoid generation lag spikes during play.");
            });
        }

        if (configuration.enabled() && options.pluginMetricsEnabled()) {
            runtime.executor.scheduleWithFixedDelay(
                runtime::reportPluginMetrics,
                options.pluginReportIntervalSeconds(),
                options.pluginReportIntervalSeconds(),
                TimeUnit.SECONDS
            );
        }
        if (configuration.enabled() && options.cleanupEnabled()) {
            runtime.executor.scheduleWithFixedDelay(
                runtime::requestPersistentMobScan,
                options.cleanupIntervalSeconds(),
                options.cleanupIntervalSeconds(),
                TimeUnit.SECONDS
            );
        }
        return runtime;
    }

    public static boolean allowSpawn(final Level level, final net.minecraft.world.entity.Entity entity) {
        final SalmonRuntime runtime = instance;
        if (runtime == null || !runtime.configuration.enabled()) {
            return true;
        }

        final SalmonConfiguration.Options options = runtime.configuration.options();
        if (!options.chunkCapsEnabled()) {
            return true;
        }
        final Level world = level;
        final int maxTnt = options.maxPrimedTntPerChunk();
        final int maxHostiles = options.maxHostileEntitiesPerChunk();
        final int maxPassive = options.maxPassiveEntitiesPerChunk();
        final int maxWater = options.maxWaterEntitiesPerChunk();
        final int maxAmbient = options.maxAmbientEntitiesPerChunk();
        final MobCategory category = entity.getType().getCategory();
        final boolean isTnt = entity.getType() == EntityTypes.TNT;
        final boolean isHostile = category == MobCategory.MONSTER;
        final boolean isPassive = category == MobCategory.CREATURE;
        final boolean isWater = isWaterCategory(category);
        final boolean isAmbient = category == MobCategory.AMBIENT;
        if ((maxTnt == 0 || !isTnt)
            && (maxHostiles == 0 || !isHostile)
            && (maxPassive == 0 || !isPassive)
            && (maxWater == 0 || !isWater)
            && (maxAmbient == 0 || !isAmbient)) {
            return true;
        }

        final int chunkX = entity.blockPosition().getX() >> 4;
        final int chunkZ = entity.blockPosition().getZ() >> 4;
        final int minX = chunkX << 4;
        final int minZ = chunkZ << 4;
        final AABB chunkBounds = new AABB(minX, world.getMinY(), minZ, minX + 16, world.getMaxY(), minZ + 16);
        int tntCount = 0;
        int hostileCount = 0;
        int passiveCount = 0;
        int waterCount = 0;
        int ambientCount = 0;
        for (net.minecraft.world.entity.Entity existing : world.getEntities(entity, chunkBounds)) {
            if (maxTnt > 0 && isTnt && existing.getType() == EntityTypes.TNT) {
                tntCount++;
            }
            final MobCategory existingCategory = existing.getType().getCategory();
            if (maxHostiles > 0 && isHostile && existingCategory == MobCategory.MONSTER
                && (options.countNamedHostiles() || existing.getCustomName() == null)) {
                hostileCount++;
            }
            if (maxPassive > 0 && isPassive && existingCategory == MobCategory.CREATURE) {
                passiveCount++;
            }
            if (maxWater > 0 && isWater && isWaterCategory(existingCategory)) {
                waterCount++;
            }
            if (maxAmbient > 0 && isAmbient && existingCategory == MobCategory.AMBIENT) {
                ambientCount++;
            }
        }

        return !(isTnt && maxTnt > 0 && tntCount >= maxTnt)
            && !(isHostile && maxHostiles > 0 && (options.countNamedHostiles() || entity.getCustomName() == null)
                && hostileCount >= maxHostiles)
            && !(isPassive && maxPassive > 0 && passiveCount >= maxPassive)
            && !(isWater && maxWater > 0 && waterCount >= maxWater)
            && !(isAmbient && maxAmbient > 0 && ambientCount >= maxAmbient);
    }

    private static boolean isWaterCategory(final MobCategory category) {
        return category == MobCategory.AXOLOTLS
            || category == MobCategory.UNDERGROUND_WATER_CREATURE
            || category == MobCategory.WATER_AMBIENT
            || category == MobCategory.WATER_CREATURE;
    }

    public static Component renderMotd(final Component fallback, final int onlinePlayers, final int maxPlayers) {
        final SalmonRuntime runtime = instance;
        return runtime == null ? fallback : runtime.motdManager.render(onlinePlayers, maxPlayers);
    }

    public static void checkMaintenanceLogin(final AsyncPlayerPreLoginEvent event) {
        final SalmonRuntime runtime = instance;
        if (runtime != null && !runtime.motdManager.allowsLogin(event.getName(), event.getUniqueId())) {
            event.setLoginResult(AsyncPlayerPreLoginEvent.Result.KICK_WHITELIST);
            event.kickMessage(maintenanceKickMessage());
        }
    }

    public static boolean allowsMaintenanceLogin(final String playerName, final UUID playerId) {
        final SalmonRuntime runtime = instance;
        return runtime == null || runtime.motdManager.allowsLogin(playerName, playerId);
    }

    public static Component maintenanceKickMessage() {
        return Component.text("This server is in maintenance mode.");
    }

    public SalmonMotdManager motdManager() {
        return this.motdManager;
    }

    public Component motd(final int onlinePlayers, final int maxPlayers) {
        return this.motdManager.render(onlinePlayers, maxPlayers);
    }

    public boolean isEnabled() {
        return this.configuration.enabled();
    }

    public List<Component> lagReport() {
        final double[] tps = this.server.getTPS();
        final double averageMspt = this.server.getAverageTickTime();
        int entities = 0;
        int chunks = 0;
        for (World world : this.server.getWorlds()) {
            entities += world.getEntityCount();
            chunks += world.getChunkCount();
        }

        final List<Component> report = new ArrayList<>();
        report.add(Component.text("Salmon Lag Report (on-demand snapshot)", NamedTextColor.GOLD));
        report.add(Component.text(String.format(Locale.ROOT, "TPS (1m / 5m / 15m): %.2f / %.2f / %.2f",
            tps[0], tps[1], tps[2])));
        report.add(Component.text(String.format(Locale.ROOT, "Average MSPT (5s): %.2f ms", averageMspt)));
        report.add(Component.text("Worlds: " + this.server.getWorlds().size()
            + " | Players: " + this.server.getOnlinePlayers().size()
            + " | Loaded chunks: " + chunks
            + " | Entities: " + entities));

        if (!PluginExecutionMetrics.isEnabled()) {
            report.add(Component.text("Plugin metrics: disabled (enable in config/salmon-opts.yml to collect)",
                NamedTextColor.GRAY));
        } else {
            final List<Snapshot> pluginMetrics = PluginExecutionMetrics.snapshot();
            report.add(Component.text("Plugin metrics: enabled; current collection window", NamedTextColor.AQUA));
            if (pluginMetrics.isEmpty()) {
                report.add(Component.text("No plugin timing samples are available yet.", NamedTextColor.GRAY));
            } else {
                pluginMetrics.stream().limit(5).forEach(snapshot -> report.add(Component.text(String.format(
                    Locale.ROOT, " - %s: %d calls, %.2f ms total (max %.2f ms)",
                    snapshot.plugin(), snapshot.calls(), snapshot.totalNanos() / 1_000_000.0,
                    snapshot.maxCallNanos() / 1_000_000.0))));
            }
        }
        return List.copyOf(report);
    }

    <T> CompletableFuture<T> supplyApiAsync(final Supplier<T> task) {
        return CompletableFuture.supplyAsync(task, this.apiExecutor);
    }

    void runApiOnServerThread(final Plugin owner, final Consumer<Server> task) {
        this.server.getServer().execute(() -> {
            if (owner.isEnabled()) {
                task.accept(this.server);
            }
        });
    }

    public void searchModrinth(final String query, final java.util.function.Consumer<List<ModrinthService.Project>> success,
        final java.util.function.Consumer<Exception> failure) {
        if (!this.configuration.options().modrinthServicesEnabled()) {
            failure.accept(new IllegalStateException("Modrinth services are disabled in config/salmon-opts.yml"));
            return;
        }
        this.modrinthExecutor.execute(() -> {
            try {
                final List<ModrinthService.Project> projects = this.modrinthService.search(query);
                this.server.getServer().execute(() -> success.accept(projects));
            } catch (IOException | InterruptedException | RuntimeException ex) {
                if (ex instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                this.server.getServer().execute(() -> failure.accept(ex));
            }
        });
    }

    public void installModrinth(final String slugOrId, final java.util.function.Consumer<ModrinthService.InstallResult> success,
        final java.util.function.Consumer<Exception> failure) {
        if (!this.configuration.options().modrinthServicesEnabled()) {
            failure.accept(new IllegalStateException("Modrinth services are disabled in config/salmon-opts.yml"));
            return;
        }
        this.modrinthExecutor.execute(() -> {
            try {
                final ModrinthService.InstallResult result = this.modrinthService.install(slugOrId);
                this.server.getServer().execute(() -> success.accept(result));
            } catch (IOException | InterruptedException | RuntimeException ex) {
                if (ex instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                this.server.getServer().execute(() -> failure.accept(ex));
            }
        });
    }

    public static void recordPluginEvent(final Plugin plugin, final long elapsedNanos) {
        if (eventTimingEnabled) {
            PluginExecutionMetrics.recordEvent(plugin, elapsedNanos);
        }
    }

    public static void recordPluginTask(final Plugin plugin, final long elapsedNanos) {
        if (taskTimingEnabled) {
            PluginExecutionMetrics.recordTask(plugin, elapsedNanos);
        }
    }

    public static long beginPluginEventTiming() {
        return eventTimingEnabled ? System.nanoTime() : Long.MIN_VALUE;
    }

    public static void finishPluginEventTiming(final Plugin plugin, final long startedAt) {
        if (startedAt != Long.MIN_VALUE) {
            recordPluginEvent(plugin, System.nanoTime() - startedAt);
        }
    }

    public static long beginPluginTaskTiming() {
        return taskTimingEnabled ? System.nanoTime() : Long.MIN_VALUE;
    }

    public static void finishPluginTaskTiming(final Plugin plugin, final long startedAt) {
        if (startedAt != Long.MIN_VALUE) {
            recordPluginTask(plugin, System.nanoTime() - startedAt);
        }
    }

    private void reportPluginMetrics() {
        final SalmonConfiguration.Options options = this.configuration.options();
        final long minimumNanos = TimeUnit.MILLISECONDS.toNanos(options.pluginMinimumReportMillis());
        final long slowCallNanos = TimeUnit.MILLISECONDS.toNanos(options.pluginSlowCallWarningMillis());
        final List<Snapshot> snapshots = PluginExecutionMetrics.drain();
        final List<Snapshot> entries = snapshots.stream()
            .filter(snapshot -> snapshot.totalNanos() >= minimumNanos)
            .limit(options.pluginMaxEntries())
            .toList();
        if (!entries.isEmpty()) {
            final StringBuilder report = new StringBuilder("Plugin execution summary (last ")
                .append(options.pluginReportIntervalSeconds()).append("s):");
            for (Snapshot snapshot : entries) {
                report.append(System.lineSeparator())
                    .append(" - ").append(snapshot.plugin())
                    .append(": ").append(snapshot.calls()).append(" calls, ")
                    .append(String.format("%.2f", snapshot.totalNanos() / 1_000_000.0)).append(" ms total (")
                    .append(snapshot.eventCalls()).append(" events / ")
                    .append(String.format("%.2f", snapshot.eventNanos() / 1_000_000.0)).append(" ms, ")
                    .append(snapshot.taskCalls()).append(" tasks / ")
                    .append(String.format("%.2f", snapshot.taskNanos() / 1_000_000.0)).append(" ms), max call ")
                    .append(String.format("%.2f", snapshot.maxCallNanos() / 1_000_000.0)).append(" ms");
            }
            LOGGER.info(report.toString());
        }
        if (slowCallNanos > 0L) {
            final long now = System.nanoTime();
            final long cooldownNanos = TimeUnit.SECONDS.toNanos(options.pluginReportIntervalSeconds());
            for (Snapshot snapshot : snapshots) {
                if (snapshot.maxCallNanos() < slowCallNanos) {
                    continue;
                }
                final Long lastWarning = this.lastSlowWarningNanos.get(snapshot.plugin());
                if (lastWarning == null || now - lastWarning >= cooldownNanos) {
                    this.lastSlowWarningNanos.put(snapshot.plugin(), now);
                    LOGGER.warning("Slow plugin operation observed: " + snapshot.plugin()
                        + " took up to " + String.format("%.2f", snapshot.maxCallNanos() / 1_000_000.0) + " ms");
                }
            }
        }
    }

    private void requestPersistentMobScan() {
        this.server.getServer().execute(this::snapshotPersistentMobs);
    }

    private void snapshotPersistentMobs() {
        final SalmonConfiguration.Options options = this.configuration.options();
        if (!this.configuration.enabled() || !options.cleanupEnabled()) {
            return;
        }
        final long minimumAgeTicks = TimeUnit.SECONDS.toMillis(options.cleanupMinimumAgeSeconds()) / 50L;
        final long protectedRadiusSquared = (long) options.cleanupProtectPlayerRadius() * options.cleanupProtectPlayerRadius();
        final List<MobCandidate> candidates = new ArrayList<>();

        for (World world : this.server.getWorlds()) {
            final List<Player> players = world.getPlayers();
            for (Entity entity : world.getEntities()) {
                if (!(entity instanceof Monster) || !(entity instanceof Mob mob)
                    || mob.getRemoveWhenFarAway()
                    || (long) entity.getTicksLived() < minimumAgeTicks
                    || !matchesCleanupType(entity, options.cleanupEntityTypes())
                    || isProtected(entity, mob, options, players, protectedRadiusSquared)) {
                    continue;
                }
                candidates.add(new MobCandidate(world.getUID(), entity.getUniqueId(), entity.getTicksLived()));
            }
        }

        if (candidates.isEmpty()) {
            return;
        }

        this.executor.execute(() -> {
            candidates.sort(Comparator.comparingInt(MobCandidate::ticksLived).reversed());
            final List<MobCandidate> selected = List.copyOf(candidates.subList(0, Math.min(candidates.size(), options.cleanupMaxPerPass())));
            this.server.getServer().execute(() -> this.removePersistentMobs(selected, options));
        });
    }

    private static boolean matchesCleanupType(final Entity entity, final Set<String> entityTypes) {
        return entityTypes.isEmpty() || entityTypes.contains(entity.getType().getKey().toString().toLowerCase(java.util.Locale.ROOT));
    }

    private static boolean isProtected(
        final Entity entity,
        final Mob mob,
        final SalmonConfiguration.Options options,
        final List<Player> players,
        final long protectedRadiusSquared
    ) {
        if ((options.cleanupExcludeNamed() && entity.getCustomName() != null)
            || (options.cleanupExcludeTamed() && entity instanceof Tameable tameable && tameable.isTamed())
            || (options.cleanupExcludeLeashed() && mob.isLeashed())
            || (options.cleanupExcludePassengers() && (entity.isInsideVehicle() || !entity.getPassengers().isEmpty()))) {
            return true;
        }
        if (protectedRadiusSquared == 0L) {
            return false;
        }
        for (Player player : players) {
            final double dx = entity.getX() - player.getX();
            final double dy = entity.getY() - player.getY();
            final double dz = entity.getZ() - player.getZ();
            if (dx * dx + dy * dy + dz * dz <= protectedRadiusSquared) {
                return true;
            }
        }
        return false;
    }

    private void removePersistentMobs(final List<MobCandidate> candidates, final SalmonConfiguration.Options options) {
        int removed = 0;
        for (MobCandidate candidate : candidates) {
            final World world = this.server.getWorld(candidate.worldId());
            if (world == null) {
                continue;
            }
            final Entity entity = world.getEntity(candidate.entityId());
            if (entity == null) {
                continue;
            }
            if (!entity.isValid() || !(entity instanceof Monster) || !(entity instanceof Mob mob)
                || mob.getRemoveWhenFarAway()
                || (long) entity.getTicksLived() < TimeUnit.SECONDS.toMillis(options.cleanupMinimumAgeSeconds()) / 50L
                || !matchesCleanupType(entity, options.cleanupEntityTypes())
                || isProtected(entity, mob, options, world.getPlayers(),
                    (long) options.cleanupProtectPlayerRadius() * options.cleanupProtectPlayerRadius())) {
                continue;
            }
            entity.remove();
            removed++;
        }
        if (removed > 0) {
            LOGGER.info("Removed " + removed + " opted-in persistent hostile mob(s) during cleanup");
        }
    }

    @Override
    public void close() {
        eventTimingEnabled = false;
        taskTimingEnabled = false;
        PluginExecutionMetrics.setEnabled(false);
        this.executor.shutdown();
        this.modrinthExecutor.shutdown();
        this.apiExecutor.shutdown();
        instance = null;
    }

    private record MobCandidate(UUID worldId, UUID entityId, int ticksLived) {
    }
}
