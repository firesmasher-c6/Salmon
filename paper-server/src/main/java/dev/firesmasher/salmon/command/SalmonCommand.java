package dev.firesmasher.salmon.command;

import dev.firesmasher.salmon.service.ModrinthService;

import dev.firesmasher.salmon.runtime.SalmonRuntime;



import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

import static net.kyori.adventure.text.Component.text;

public final class SalmonCommand extends Command {
    private static final Logger LOGGER = Logger.getLogger("Salmon");
    private static final List<String> ROOT_ARGUMENTS = List.of("status", "modrinth", "lagreport");
    private final SalmonRuntime runtime;

    public SalmonCommand(final SalmonRuntime runtime) {
        super("salmon");
        this.runtime = runtime;
        this.description = "View Salmon status and manage Modrinth plugin services";
        this.usageMessage = "/salmon [status|lagreport|modrinth search <query>|modrinth install <slug>]";
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
            return true;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("lagreport")) {
            this.runtime.lagReport().forEach(sender::sendMessage);
            return true;
        }

        if (args.length >= 3 && args[0].equalsIgnoreCase("modrinth") && args[1].equalsIgnoreCase("search")) {
            final String query = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
            sender.sendMessage(text("Searching Modrinth for plugins matching '" + query + "'..."));
            this.runtime.searchModrinth(query, projects -> {
                if (projects.isEmpty()) {
                    sender.sendMessage(text("No Modrinth plugin projects matched that search.", NamedTextColor.YELLOW));
                    return;
                }
                sender.sendMessage(text("Modrinth plugin results:"));
                for (dev.firesmasher.salmon.service.ModrinthService.Project project : projects) {
                    sender.sendMessage(text(" - " + project.title() + " (" + project.slug() + "): "
                        + project.description()));
                }
                sender.sendMessage(text("Install a result with /salmon modrinth install <slug>"));
            }, exception -> this.reportModrinthFailure(sender, "search", exception));
            return true;
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("modrinth") && args[1].equalsIgnoreCase("install")) {
            sender.sendMessage(text("Resolving plugin and required dependencies from Modrinth..."));
            this.runtime.installModrinth(args[2], result -> {
                if (result.alreadyInstalled()) {
                    sender.sendMessage(text(result.project() + " is already installed and valid.", NamedTextColor.GREEN));
                    return;
                }
                if (result.installedFiles().isEmpty()) {
                    sender.sendMessage(text(result.project() + " and its required dependencies are already installed.",
                        NamedTextColor.GREEN));
                } else {
                    sender.sendMessage(text("Installed " + result.project() + " plugin file(s): "
                        + String.join(", ", result.installedFiles()), NamedTextColor.GREEN));
                }
                sender.sendMessage(text("Restart the server to load the plugin(s).", NamedTextColor.YELLOW));
            }, exception -> this.reportModrinthFailure(sender, "installation", exception));
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
        if (args.length == 2 && args[0].equalsIgnoreCase("modrinth")) {
            return matching(List.of("search", "install"), args[1]);
        }
        return List.of();
    }

    private static List<String> matching(final List<String> candidates, final String input) {
        final String prefix = input.toLowerCase(Locale.ROOT);
        return candidates.stream().filter(candidate -> candidate.startsWith(prefix)).toList();
    }

    private void reportModrinthFailure(final CommandSender sender, final String action, final Exception exception) {
        LOGGER.log(Level.WARNING, "Modrinth " + action + " failed: " + exception.getMessage(), exception);
        sender.sendMessage(text("Modrinth " + action + " failed: " + exception.getMessage(), NamedTextColor.RED));
    }
}
