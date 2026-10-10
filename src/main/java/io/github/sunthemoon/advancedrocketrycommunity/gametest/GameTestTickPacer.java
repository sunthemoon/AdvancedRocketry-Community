package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import java.util.concurrent.locks.LockSupport;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/**
 * Gives a waiting GameTest tick the dedicated server's length. {@code GameTestServer.waitUntilNextTick()} only runs
 * queued tasks and never sleeps, so a busy GameTest run executes hundreds of ticks per second, while chunk generation,
 * entity-section loading and lighting progress on background threads in wall-clock time. A readiness wait counted in
 * ticks therefore measures the host's speed rather than the product. Each pacer call made from a waiting tick holds
 * that tick until {@link #SERVER_TICK_NANOS} after the previous call and, like {@code MinecraftServer} waiting for its
 * next tick, meanwhile runs the chunk sources' main-thread tasks, so an unchanged tick budget spans what it spans on a
 * 20 TPS server. A pacer belongs to one wait and is used only on the server thread.
 */
public final class GameTestTickPacer {
    /** The 50 ms tick of {@code MinecraftServer#runServer}. */
    public static final long SERVER_TICK_NANOS = 50_000_000L;
    private static final long IDLE_PARK_NANOS = 500_000L;

    private final MinecraftServer server;
    private int lastTick = Integer.MIN_VALUE;
    private long lastAt;

    public GameTestTickPacer(MinecraftServer server) {
        this.server = server;
    }

    /** Called once per waiting tick; a second call in the same tick, or the first call of a wait, does not wait. */
    public void pace() {
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
