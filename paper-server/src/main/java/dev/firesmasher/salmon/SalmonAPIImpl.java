package dev.firesmasher.salmon;

import dev.firesmasher.salmon.plugins.api.SalmonAPI;
import java.util.concurrent.CompletableFuture;
import net.minecraft.SharedConstants;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.plugin.Plugin;
import java.util.function.Consumer;
import java.util.function.Supplier;

final class SalmonAPIImpl extends SalmonAPI {
    private final SalmonRuntime runtime;

    private SalmonAPIImpl(final SalmonRuntime runtime) {
        this.runtime = runtime;
    }

    static SalmonAPIImpl install(final SalmonRuntime runtime) {
        final SalmonAPIImpl api = new SalmonAPIImpl(runtime);
        install(api);
        return api;
    }

    @Override
    public String minecraftVersion() {
        return SharedConstants.getCurrentVersion().name();
    }

    @Override
    public Server server() {
        return Bukkit.getServer();
    }

    @Override
    public <T> CompletableFuture<T> supplyAsync(final Plugin owner, final Supplier<T> task) {
        requireEnabledOwner(owner);
        if (task == null) {
            throw new IllegalArgumentException("background task is required");
        }
        return this.runtime.supplyApiAsync(task);
    }

    @Override
    public void runOnServerThread(final Plugin owner, final Consumer<Server> task) {
        requireEnabledOwner(owner);
        if (task == null) {
            throw new IllegalArgumentException("server-thread task is required");
        }
        this.runtime.runApiOnServerThread(owner, task);
    }

    @Override
    public CompletableFuture<Boolean> dispatchConsoleCommand(final Plugin owner, final String command) {
        requireEnabledOwner(owner);
        if (command == null || command.isBlank() || command.startsWith("/")) {
            throw new IllegalArgumentException("command must be non-empty and must not start with '/'");
        }
        final CompletableFuture<Boolean> result = new CompletableFuture<>();
        this.runtime.runApiOnServerThread(owner, server -> {
            try {
                result.complete(server.dispatchCommand(server.getConsoleSender(), command));
            } catch (RuntimeException ex) {
                result.completeExceptionally(ex);
            }
        });
        return result;
    }

    @Override
    public BukkitTask runTask(final Plugin owner, final Runnable task) {
        requireEnabledOwner(owner);
        requireTask(task);
        return Bukkit.getScheduler().runTask(owner, task);
    }

    @Override
    public BukkitTask runTaskLater(final Plugin owner, final Runnable task, final long delayTicks) {
        requireEnabledOwner(owner);
        requireTask(task);
        requireTicks(delayTicks, "delay");
        return Bukkit.getScheduler().runTaskLater(owner, task, delayTicks);
    }

    @Override
    public BukkitTask runTaskTimer(final Plugin owner, final Runnable task, final long delayTicks, final long periodTicks) {
        requireEnabledOwner(owner);
        requireTask(task);
        requireTicks(delayTicks, "delay");
        if (periodTicks < 1L) {
            throw new IllegalArgumentException("period must be at least one tick");
        }
        return Bukkit.getScheduler().runTaskTimer(owner, task, delayTicks, periodTicks);
    }

    @Override
    public Plugin plugin(final String name) {
        requireServerThread();
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("plugin name is required");
        }
        return Bukkit.getPluginManager().getPlugin(name);
    }

    @Override
    public java.util.List<Plugin> plugins() {
        requireServerThread();
        return java.util.List.of(Bukkit.getPluginManager().getPlugins());
    }

    private static void requireEnabledOwner(final Plugin owner) {
        if (owner == null || !owner.isEnabled()) {
            throw new IllegalStateException("an enabled plugin owner is required");
        }
    }

    private static void requireTask(final Runnable task) {
        if (task == null) {
            throw new IllegalArgumentException("task is required");
        }
    }

    private static void requireTicks(final long ticks, final String name) {
        if (ticks < 0L) {
            throw new IllegalArgumentException(name + " ticks must be zero or greater");
        }
    }

    private static void requireServerThread() {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("plugin lookup must run on the Minecraft server thread");
        }
    }
}
