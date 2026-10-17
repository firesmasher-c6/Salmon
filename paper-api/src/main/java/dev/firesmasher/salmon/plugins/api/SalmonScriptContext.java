package dev.firesmasher.salmon.plugins.api;

/**
 * Identifies the script invocation that called a Salmon-provided function.
 *
 * @param scriptName script metadata name
 * @param lineNumber source line of the call
 */
public record SalmonScriptContext(String scriptName, int lineNumber) {
}
