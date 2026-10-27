package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import net.minecraft.server.MinecraftServer;

/**
 * W166: vanilla 1.20.1's periodic autosave. {@code MinecraftServer#tickServer} increments {@code tickCount}, runs
 * {@code tickChildren}, where the levels and the GameTests tick, and then calls {@code saveEverything(true, false, false)}
 * when {@code tickCount % 6000 == 0}. That save stores every loaded entity section, so it supersedes any native capture
 * still pending. A fixture whose capture must not be superseded starts it only when the next autosave lies beyond the
 * capture's window.
 */
public final class GameTestAutosave {
    /** The literal period in {@code MinecraftServer#tickServer}, read from the mapped 1.20.1 jar. */
    public static final int PERIOD_TICKS = 6_000;

    private GameTestAutosave() {
    }

    /** Tick ends from a callback in tick {@code tickCount} to the next autosave; 0 means this tick ends with one. */
    public static int ticksUntil(int tickCount) {
        return Math.floorMod(-tickCount, PERIOD_TICKS);
    }

    /** True while the next autosave falls within {@code window} tick ends, counting the current one. */
    public static boolean within(MinecraftServer server, int window) {
        return ticksUntil(server.getTickCount()) <= window;
    }
}
