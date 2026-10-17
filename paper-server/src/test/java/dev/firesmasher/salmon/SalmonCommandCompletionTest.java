package dev.firesmasher.salmon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import java.util.List;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;

class SalmonCommandCompletionTest {
    private final CommandSender sender = mock(CommandSender.class);

    @Test
    void completesSalmonCommandsAndFiltersCaseInsensitively() {
        final SalmonCommand command = new SalmonCommand(null);

        assertEquals(List.of("status"), command.tabComplete(this.sender, "salmon", new String[] {"s"}));
        assertEquals(List.of("lagreport"), command.tabComplete(this.sender, "salmon", new String[] {"lag"}));
        assertEquals(List.of("search"), command.tabComplete(this.sender, "salmon", new String[] {"modrinth", "S"}));
    }

    @Test
    void completesSmpCommandsAndFiltersCaseInsensitively() {
        final SalmonSmpCommand command = new SalmonSmpCommand(null);

        assertEquals(List.of("maintenance"), command.tabComplete(this.sender, "smp", new String[] {"MAI"}));
        assertEquals(List.of("on", "off"), command.tabComplete(this.sender, "smp", new String[] {"maintenance", "o"}));
        assertEquals(List.of("add", "rm", "remove", "list"), command.tabComplete(this.sender, "smp",
            new String[] {"maintenance", "whitelist", ""}));
        assertEquals(List.of("reload"), command.tabComplete(this.sender, "smp", new String[] {"motd", "r"}));
    }
}
