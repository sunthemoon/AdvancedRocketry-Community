package io.github.sunthemoon.arceadaptertest;

import java.util.concurrent.locks.LockSupport;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Test-only controls: source-instance probes and a synchronous, finally-cleared restore scope. */
final class FixtureAdapterFaults {
    static final String PRIVATE_DETAIL = "private fixture callback detail";
    static final long SLOW_NANOS = 20_000_000L;
    private static final ThreadLocal<Probe> RESTORE = new ThreadLocal<>();
    // Per-test positions, registered/removed on the server thread. Never used by production or persisted.
    private static final Map<BlockPos, Probe> DETACHED_RESTORE = new HashMap<>();

    enum Mode {
        CAN_MOVE_THROW, CAN_MOVE_SLOW, CAPTURE_THROW, CAPTURE_OVERSIZED, CAPTURE_SLOW,
        RESTORE_THROW, RESTORE_FALSE, RESTORE_SLOW, RESTORE_MISMATCH, RESTORE_NEEDS_LEVEL, RESTORE_NEEDS_INSTALLED
    }

    static final class Probe {
        final Mode mode;
        int calls;
        int readbacks;
        long delayedNanos;
        BlockEntity target;

        Probe(Mode mode) { this.mode = java.util.Objects.requireNonNull(mode); }

        void enter(String phase) {
            if (!mode.name().startsWith(phase + "_")) { return; }
            calls++;
            if (mode.name().endsWith("_SLOW")) {
                long started = System.nanoTime();
                long elapsed;
                while ((elapsed = System.nanoTime() - started) < SLOW_NANOS) {
                    LockSupport.parkNanos(SLOW_NANOS - elapsed);
                }
                delayedNanos = elapsed;
            }
            if (mode.name().endsWith("_THROW")) { throw new IllegalStateException(PRIVATE_DETAIL); }
        }
    }

    static Probe restoring() { return RESTORE.get(); }

    static Probe restoring(BlockEntity target) {
        Probe immediate = restoring();
        return immediate != null ? immediate : target.hasLevel() ? null : DETACHED_RESTORE.get(target.getBlockPos());
    }

    static AutoCloseable detachedRestore(BlockPos at, Probe probe) {
        if (!probe.mode.name().startsWith("RESTORE_") || DETACHED_RESTORE.putIfAbsent(at.immutable(), probe) != null) {
            throw new IllegalStateException("Invalid or duplicate asynchronous fixture restore scope");
        }
        return () -> DETACHED_RESTORE.remove(at, probe);
    }

    static void duringRestore(Probe probe, Runnable action) {
        if (!probe.mode.name().startsWith("RESTORE_") || RESTORE.get() != null) {
            throw new IllegalStateException("Invalid or nested fixture restore scope");
        }
        RESTORE.set(probe);
        try { action.run(); } finally { RESTORE.remove(); }
    }

    private FixtureAdapterFaults() { }
}
