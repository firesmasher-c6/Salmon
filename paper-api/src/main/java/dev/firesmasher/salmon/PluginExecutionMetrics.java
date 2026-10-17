package dev.firesmasher.salmon;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.atomic.LongAccumulator;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

public final class PluginExecutionMetrics {
    private static final AtomicReference<ConcurrentHashMap<String, MutableSnapshot>> CURRENT = new AtomicReference<>(new ConcurrentHashMap<>());
    private static volatile boolean enabled;

    private PluginExecutionMetrics() {
    }

    public static void setEnabled(final boolean enabled) {
        PluginExecutionMetrics.enabled = enabled;
        if (!enabled) {
            CURRENT.set(new ConcurrentHashMap<>());
        }
    }

    public static void recordEvent(@Nullable final Plugin plugin, final long elapsedNanos) {
        record(plugin, elapsedNanos, true);
    }

    public static void recordTask(@Nullable final Plugin plugin, final long elapsedNanos) {
        record(plugin, elapsedNanos, false);
    }

    private static void record(@Nullable final Plugin plugin, final long elapsedNanos, final boolean event) {
        if (!enabled || plugin == null) {
            return;
        }

        CURRENT.get().computeIfAbsent(plugin.getName(), ignored -> new MutableSnapshot()).record(elapsedNanos, event);
    }

    public static List<Snapshot> drain() {
        final ConcurrentHashMap<String, MutableSnapshot> previous = CURRENT.getAndSet(new ConcurrentHashMap<>());
        final List<Snapshot> snapshots = new ArrayList<>(previous.size());
        previous.forEach((plugin, metrics) -> snapshots.add(metrics.snapshot(plugin)));
        snapshots.sort(Comparator.comparingLong(Snapshot::totalNanos).reversed());
        return List.copyOf(snapshots);
    }

    public record Snapshot(String plugin, long totalNanos, long calls, long eventNanos, long eventCalls, long taskNanos, long taskCalls, long maxCallNanos) {
    }

    private static final class MutableSnapshot {
        private final LongAdder totalNanos = new LongAdder();
        private final LongAdder calls = new LongAdder();
        private final LongAdder eventNanos = new LongAdder();
        private final LongAdder eventCalls = new LongAdder();
        private final LongAdder taskNanos = new LongAdder();
        private final LongAdder taskCalls = new LongAdder();
        private final LongAccumulator maxCallNanos = new LongAccumulator(Long::max, 0L);

        private void record(final long elapsedNanos, final boolean event) {
            this.totalNanos.add(elapsedNanos);
            this.calls.increment();
            if (event) {
                this.eventNanos.add(elapsedNanos);
                this.eventCalls.increment();
            } else {
                this.taskNanos.add(elapsedNanos);
                this.taskCalls.increment();
            }
            this.maxCallNanos.accumulate(elapsedNanos);
        }

        private Snapshot snapshot(final String plugin) {
            return new Snapshot(
                plugin,
                this.totalNanos.sum(),
                this.calls.sum(),
                this.eventNanos.sum(),
                this.eventCalls.sum(),
                this.taskNanos.sum(),
                this.taskCalls.sum(),
                this.maxCallNanos.get()
            );
        }
    }
}
