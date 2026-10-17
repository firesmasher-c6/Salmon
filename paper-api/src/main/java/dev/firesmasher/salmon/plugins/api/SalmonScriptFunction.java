package dev.firesmasher.salmon.plugins.api;

import java.util.List;

/**
 * A synchronous function callable from a Salmon Script.
 */
@FunctionalInterface
public interface SalmonScriptFunction {
    /**
     * Runs on the Minecraft server thread.
     *
     * @param context script and source location
     * @param arguments immutable arguments supplied by the script
     * @return a script value: {@code null}, {@link String}, {@link Number}, or {@link Boolean}
     * @throws Exception an invocation failure, which Salmon logs against the script line
     */
    Object invoke(SalmonScriptContext context, List<Object> arguments) throws Exception;
}
