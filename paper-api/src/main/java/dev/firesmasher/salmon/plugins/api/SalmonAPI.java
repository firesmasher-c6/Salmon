package dev.firesmasher.salmon.plugins.api;

import java.util.Set;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Salmon-specific API available to Paper and Bukkit plugins.
 *
 * <p>Compile against the Paper API that ships with Salmon. The API is installed
 * during Salmon startup; calling {@link #get()} on another server fails
 * explicitly.</p>
 */
public abstract class SalmonAPI {
    private static volatile SalmonAPI instance;

    /**
     * Gets the API instance installed by Salmon.
     *
     * @return Salmon API
     */
    public static SalmonAPI get() {
        final SalmonAPI api = instance;
        if (api == null || Bukkit.getServer() == null) {
            throw new IllegalStateException("SalmonAPI is available only while running on Salmon");
        }
        return api;
    }

    /**
     * Checks whether Salmon installed its API in this server process.
     *
     * @return whether the API is available
     */
    public static boolean isAvailable() {
        return instance != null && Bukkit.getServer() != null;
    }

    /**
     * Gets the running Minecraft version.
     *
     * @return Minecraft version
     */
    public abstract String minecraftVersion();

    /**
     * Gets the active Bukkit/Paper server API.
     *
     * @return running server
     */
    public abstract Server server();

    /**
     * Runs blocking or CPU-intensive plugin work away from the server thread.
     * The task must not access live Bukkit objects; return immutable data and
     * hand game-state changes back with {@link #runOnServerThread(Plugin, Consumer)}.
     *
     * @param owner plugin that scheduled the work
     * @param task background computation
     * @param <T> result type
     * @return completion stage completed by the background worker
     */
    public abstract <T> CompletableFuture<T> supplyAsync(Plugin owner, Supplier<T> task);

    /**
     * Schedules a callback on the Minecraft server thread.
     *
     * @param owner plugin that scheduled the work
     * @param task callback receiving the Bukkit/Paper server
     */
    public abstract void runOnServerThread(Plugin owner, Consumer<Server> task);

    /**
     * Dispatches a command using the server console sender on the server thread.
     *
     * @param owner requesting plugin
     * @param command command text without a leading slash
     * @return future containing the command dispatch result
     */
    public abstract CompletableFuture<Boolean> dispatchConsoleCommand(Plugin owner, String command);

    /**
     * Schedules work on the next server tick.
     *
     * @param owner owning plugin
     * @param task task body
     * @return scheduled task handle
     */
    public abstract BukkitTask runTask(Plugin owner, Runnable task);

    /**
     * Schedules work after a delay measured in server ticks.
     *
     * @param owner owning plugin
     * @param task task body
     * @param delayTicks delay, zero or greater
     * @return scheduled task handle
     */
    public abstract BukkitTask runTaskLater(Plugin owner, Runnable task, long delayTicks);

    /**
     * Schedules repeating work using server ticks.
     *
     * @param owner owning plugin
     * @param task task body
     * @param delayTicks initial delay, zero or greater
     * @param periodTicks interval, one or greater
     * @return scheduled task handle
     */
    public abstract BukkitTask runTaskTimer(Plugin owner, Runnable task, long delayTicks, long periodTicks);

    /**
     * Looks up a currently loaded plugin by name.
     *
     * @param name plugin name
     * @return plugin, or {@code null} when it is not loaded
     */
    public abstract Plugin plugin(String name);

    /**
     * Gets a snapshot of currently loaded plugins.
     *
     * @return immutable plugin list
     */
    public abstract List<Plugin> plugins();

    /**
     * Registers a function that Salmon Scripts can call by name.
     *
     * <p>Function names are case-sensitive identifiers. Names already used by
     * the language or another plugin cannot be registered. Functions execute
     * synchronously on the server thread and must return a supported script
     * value.</p>
     *
     * @param owner owning plugin
     * @param name script-callable identifier
     * @param function function implementation
     */
    public abstract void registerScriptFunction(Plugin owner, String name, SalmonScriptFunction function);

    /**
     * Removes all script functions registered by a plugin.
     *
     * @param owner owning plugin
     */
    public abstract void unregisterScriptFunctions(Plugin owner);

    /**
     * Gets the immutable set of currently registered script function names.
     *
     * @return function names
     */
    public abstract Set<String> scriptFunctionNames();

    /**
     * Invokes a registered script function.
     *
     * @param name registered function name
     * @param context script call location
     * @param arguments immutable script arguments
     * @return supported script value
     */
    public abstract Object invokeScriptFunction(String name, SalmonScriptContext context, List<Object> arguments);

    /**
     * @param api Salmon implementation
     */
    protected static void install(final SalmonAPI api) {
        instance = api;
    }
}
