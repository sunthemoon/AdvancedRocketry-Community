package io.github.sunthemoon.arceadaptertest;

import java.util.concurrent.locks.LockSupport;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/**
 * W166 (R123): this mod's copy of the main mod's GameTest tick pacer, because this source set compiles against the
 * platform and the API only. {@code GameTestServer.waitUntilNextTick()} never sleeps, so a waiting tick counted against
 * wall-clock proof work measures the host's speed. Each call from a waiting tick holds that tick until
 * {@link #SERVER_TICK_NANOS} after the previous call and meanwhile runs the chunk sources' main-thread tasks, so an
 * unchanged tick budget spans what it spans on a 20 TPS server. A pacer belongs to one wait and runs on the server thread.
 */
final class FixtureTickPacer {
    /** The 50 ms tick of {@code MinecraftServer#runServer}. */
    static final long SERVER_TICK_NANOS = 50_000_000L;
    private static final long IDLE_PARK_NANOS = 500_000L;

    private final MinecraftServer server;
    private int lastTick = Integer.MIN_VALUE;
    private long lastAt;

    FixtureTickPacer(MinecraftServer server) {
        this.server = server;
    }

    /** Called once per waiting tick; a second call in the same tick, or the first call of a wait, does not wait. */
    void pace() {
        if (!server.isSameThread()) {
            throw new IllegalStateException("GameTest pacing runs on the server thread");
        }
        int tick = server.getTickCount();
        if (tick == lastTick) {
            return;
        }
        if (lastTick != Integer.MIN_VALUE) {
            long until = lastAt + SERVER_TICK_NANOS;
            for (long remaining = until - System.nanoTime(); remaining > 0; remaining = until - System.nanoTime()) {
                if (!pollChunkTasks()) {
                    LockSupport.parkNanos(Math.min(remaining, IDLE_PARK_NANOS));
                }
            }
        }
        lastTick = tick;
        lastAt = System.nanoTime();
    }

    private boolean pollChunkTasks() {
        boolean ran = false;
        for (ServerLevel level : server.getAllLevels()) {
            ran |= level.getChunkSource().pollTask();
        }
        return ran;
    }
}
