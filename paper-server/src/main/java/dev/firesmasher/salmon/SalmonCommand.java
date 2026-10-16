package dev.firesmasher.salmon;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

import static net.kyori.adventure.text.Component.text;

public final class SalmonCommand extends Command {
    private final SalmonRuntime runtime;

    public SalmonCommand(final SalmonRuntime runtime) {
        super("salmon");
        this.runtime = runtime;
        this.description = "View Salmon status and manage Salmon scripts";
        this.usageMessage = "/salmon [status|scripts reload]";
        this.setPermission("salmon.command");
    }

    @Override
    public boolean execute(final CommandSender sender, final String commandLabel, final String[] args) {
        if (!this.testPermission(sender)) {
            return true;
        }

        if (args.length == 0 || (args.length == 1 && args[0].equalsIgnoreCase("status"))) {
            final boolean enabled = this.runtime.isEnabled();
            sender.sendMessage(text("Salmon is " + (enabled ? "enabled" : "disabled") + ".",
                enabled ? NamedTextColor.GREEN : NamedTextColor.RED));
            sender.sendMessage(text("Optimizer worker: " + (enabled ? "SalmonMC Thread (active)" : "disabled")));
            sender.sendMessage(text("Use /salmon scripts reload to reload and run scripts."));
            return true;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("scripts") && args[1].equalsIgnoreCase("reload")) {
            if (!this.runtime.reloadScripts()) {
                sender.sendMessage(text("Salmon is disabled; scripts were not reloaded.", NamedTextColor.RED));
                return true;
            }
            sender.sendMessage(text("Salmon script reload queued. See the server log for loading and execution results.",
                NamedTextColor.GREEN));
            return true;
        }

        sender.sendMessage(text("Usage: " + this.usageMessage, NamedTextColor.RED));
        return true;
    }
}
