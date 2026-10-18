package dev.firesmasher.salmon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class PluginExecutionMetricsTest {
    @AfterEach
    void disableMetrics() {
        PluginExecutionMetrics.setEnabled(false);
    }

    @Test
    void snapshotReadsCurrentMetricsWithoutDrainingThem() {
        final Plugin plugin = mock(Plugin.class);
        when(plugin.getName()).thenReturn("TestPlugin");
        PluginExecutionMetrics.setEnabled(true);
        PluginExecutionMetrics.recordEvent(plugin, 2_000_000L);

        assertEquals(1L, PluginExecutionMetrics.snapshot().getFirst().calls());
        assertEquals(1L, PluginExecutionMetrics.snapshot().getFirst().calls());
        assertEquals(1L, PluginExecutionMetrics.drain().getFirst().calls());
        assertTrue(PluginExecutionMetrics.snapshot().isEmpty());
    }
}
