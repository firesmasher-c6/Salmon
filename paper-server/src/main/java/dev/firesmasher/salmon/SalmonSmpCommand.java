package dev.firesmasher.salmon;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.InvalidConfigurationException;

import static net.kyori.adventure.text.Component.text;

public final class SalmonSmpCommand extends Command {
    private static final Logger LOGGER = Logger.getLogger("Salmon");
    private static final List<String> ROOT_ARGUMENTS = List.of("maintenance", "motd");
    private static final List<String> MAINTENANCE_ARGUMENTS = List.of("on", "off", "whitelist");
    private static final List<String> WHITELIST_ARGUMENTS = List.of("add", "rm", "remove", "list");
    private final SalmonRuntime runtime;

    public SalmonSmpCommand(final SalmonRuntime runtime) {
        super("smp");
        this.runtime = runtime;
        this.description = "Manage Salmon SMP settings and maintenance mode";
        this.usageMessage = "/smp [maintenance on|off|whitelist add|rm|list <uuid|playername>|motd reload]";
        this.setPermission("salmon.command");
    }

    @Override
    public boolean execute(final CommandSender sender, final String commandLabel, final String[] args) {
        if (!this.testPermission(sender)) {
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage(text("Maintenance mode is "
                + (this.runtime.motdManager().maintenanceEnabled() ? "ON" : "OFF") + ".",
                this.runtime.motdManager().maintenanceEnabled() ? NamedTextColor.YELLOW : NamedTextColor.GREEN));
            sender.sendMessage(text("MOTD settings are in salmon.yml. Use /smp motd reload after editing them."));
            sender.sendMessage(text("Usage: " + this.usageMessage));
            return true;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("maintenance")) {
            if (args[1].equalsIgnoreCase("on")) {
                return this.setMaintenance(sender, true);
            }
            if (args[1].equalsIgnoreCase("off")) {
                return this.setMaintenance(sender, false);
            }
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("maintenance")
            && args[1].equalsIgnoreCase("whitelist") && args[2].equalsIgnoreCase("list")) {
            final var entries = this.runtime.motdManager().maintenanceWhitelist();
            sender.sendMessage(text(entries.isEmpty() ? "Maintenance whitelist is empty."
                : "Maintenance whitelist: " + String.join(", ", entries), NamedTextColor.AQUA));
            return true;
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("maintenance")
            && args[1].equalsIgnoreCase("whitelist")) {
            try {
                if (args[2].equalsIgnoreCase("add")) {
                    this.runtime.motdManager().addMaintenanceWhitelist(args[3]);
                    sender.sendMessage(text("Added " + args[3] + " to the maintenance whitelist.", NamedTextColor.GREEN));
                    return true;
                }
                if (args[2].equalsIgnoreCase("rm") || args[2].equalsIgnoreCase("remove")) {
                    final boolean removed = this.runtime.motdManager().removeMaintenanceWhitelist(args[3]);
                    sender.sendMessage(text(removed ? "Removed " + args[3] + " from the maintenance whitelist."
                        : args[3] + " was not on the maintenance whitelist.",
                        removed ? NamedTextColor.GREEN : NamedTextColor.YELLOW));
                    return true;
                }
            } catch (IllegalArgumentException exception) {
                sender.sendMessage(text(exception.getMessage(), NamedTextColor.RED));
                return true;
            } catch (IOException | InvalidConfigurationException exception) {
                this.reportSaveFailure(sender, exception);
                return true;
            }
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("motd") && args[1].equalsIgnoreCase("reload")) {
            try {
                this.runtime.motdManager().reload();
                sender.sendMessage(text("MOTD and maintenance settings reloaded from salmon.yml.", NamedTextColor.GREEN));
            } catch (IOException | InvalidConfigurationException exception) {
                LOGGER.log(Level.WARNING, "Failed to reload the Salmon MOTD configuration", exception);
                sender.sendMessage(text("Could not reload salmon.yml: " + exception.getMessage(), NamedTextColor.RED));
            }
            return true;
        }
        sender.sendMessage(text("Usage: " + this.usageMessage, NamedTextColor.RED));
        return true;
    }

    @Override
    public List<String> tabComplete(final CommandSender sender, final String alias, final String[] args) {
        if (args.length == 1) {
            return matching(ROOT_ARGUMENTS, args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("maintenance")) {
            return matching(MAINTENANCE_ARGUMENTS, args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("maintenance")
            && args[1].equalsIgnoreCase("whitelist")) {
            return matching(WHITELIST_ARGUMENTS, args[2]);
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("maintenance")
            && args[1].equalsIgnoreCase("whitelist")
            && (args[2].equalsIgnoreCase("add") || args[2].equalsIgnoreCase("rm")
                || args[2].equalsIgnoreCase("remove"))) {
            return super.tabComplete(sender, alias, args);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("motd")) {
            return matching(List.of("reload"), args[1]);
        }
        return List.of();
    }

    private static List<String> matching(final List<String> candidates, final String input) {
        final String prefix = input.toLowerCase(Locale.ROOT);
        return candidates.stream().filter(candidate -> candidate.startsWith(prefix)).toList();
    }

    private boolean setMaintenance(final CommandSender sender, final boolean enabled) {
        try {
            this.runtime.motdManager().setMaintenance(enabled);
            sender.sendMessage(text("Maintenance mode " + (enabled ? "enabled" : "disabled") + ".",
                enabled ? NamedTextColor.YELLOW : NamedTextColor.GREEN));
        } catch (IOException | InvalidConfigurationException exception) {
            this.reportSaveFailure(sender, exception);
        }
        return true;
    }

    private void reportSaveFailure(final CommandSender sender, final Exception exception) {
        LOGGER.log(Level.SEVERE, "Failed to save Salmon maintenance settings", exception);
        sender.sendMessage(text("Could not save salmon.yml: " + exception.getMessage(), NamedTextColor.RED));
    }
}
