package dev.firesmasher.salmon;

import dev.firesmasher.salmon.plugins.api.SalmonAPI;
import dev.firesmasher.salmon.plugins.api.SalmonScriptContext;
import dev.firesmasher.salmon.plugins.api.SalmonScriptFunction;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import net.minecraft.SharedConstants;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.plugin.Plugin;
import java.util.function.Consumer;
import java.util.function.Supplier;

final class SalmonAPIImpl extends SalmonAPI {
    private static final Set<String> RESERVED_NAMES = Set.of(
        "print", "tostring", "tonumber", "type", "math.abs", "math.floor", "math.ceil", "math.sqrt",
        "math.min", "math.max", "string.lower", "string.upper", "string.len"
    );
    private final Map<String, Registration> functions = new ConcurrentHashMap<>();
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

    @Override
    public void registerScriptFunction(final Plugin owner, final String name, final SalmonScriptFunction function) {
        if (owner == null || function == null) {
            throw new IllegalArgumentException("plugin owner and script function are required");
        }
        if (!owner.isEnabled()) {
            throw new IllegalStateException("plugin '" + owner.getName() + "' must be enabled to register script functions");
        }
        if (name == null || !name.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new IllegalArgumentException("script function name must be a valid identifier");
        }
        final String key = name.toLowerCase(Locale.ROOT);
        if (RESERVED_NAMES.contains(key)) {
            throw new IllegalArgumentException("script function name '" + name + "' is reserved");
        }
        final Registration previous = this.functions.putIfAbsent(name, new Registration(owner, function));
        if (previous != null) {
            throw new IllegalArgumentException("script function '" + name + "' is already registered");
        }
    }

    @Override
    public void unregisterScriptFunctions(final Plugin owner) {
        this.functions.entrySet().removeIf(entry -> entry.getValue().owner() == owner);
    }

    @Override
    public Set<String> scriptFunctionNames() {
        return Set.copyOf(this.functions.keySet());
    }

    @Override
    public Object invokeScriptFunction(
        final String name,
        final SalmonScriptContext context,
        final java.util.List<Object> arguments
    ) {
        final Registration registration = this.functions.get(name);
        if (registration == null) {
            throw new IllegalArgumentException("unknown function '" + name + "'");
        }
        if (!registration.owner().isEnabled()) {
            this.functions.remove(name, registration);
            throw new IllegalStateException("function provider plugin '" + registration.owner().getName() + "' is disabled");
        }
        try {
            final Object result = registration.function().invoke(context, arguments);
            if (result == null || result instanceof String || result instanceof Boolean) {
                return result;
            }
            if (result instanceof Number number && Double.isFinite(number.doubleValue())) {
                return number.doubleValue();
            }
            throw new IllegalArgumentException("script functions must return nil, a string, a boolean, or a finite number");
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Salmon script function '" + name + "' failed", ex);
        }
    }

    private record Registration(Plugin owner, SalmonScriptFunction function) {
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
