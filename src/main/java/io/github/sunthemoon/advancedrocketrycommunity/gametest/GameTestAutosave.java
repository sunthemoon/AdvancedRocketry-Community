package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import net.minecraft.server.MinecraftServer;

/**
 * W166: vanilla 1.20.1's periodic autosave. {@code MinecraftServer#tickServer} increments {@code tickCount}, runs
 * {@code tickChildren}, where the levels and the GameTests tick, and then calls {@code saveEverything(true, false, false)}
 * when {@code tickCount % 6000 == 0}. That save stores every loaded entity section, so it supersedes any native capture
 * still pending. A fixture whose capture must not be superseded starts it only when the next autosave lies beyond the
 * capture's window.
 *
 * <p>W347: the same save also writes every player's file, so a fixture that blocks a player file or counts its failed
 * saves waits the same way. A GameTest callback runs in {@code tickChildren}, before its own tick's autosave. A fixture
 * that waits from the first tick whose window holds the autosave therefore opens one tick after the autosave, at most
 * {@code window + 1} ticks later.
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
        return within(server.getTickCount(), window);
    }

    /** {@link #within(MinecraftServer, int)} for a callback in tick {@code tickCount}. */
    public static boolean within(int tickCount, int window) {
        return ticksUntil(tickCount) <= window;
    }
}
