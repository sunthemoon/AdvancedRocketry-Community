package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import java.util.Queue;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

/**
 * Test-only progress for vanilla's ready unload callbacks, independent of a GameTest tick's spare time.
 * ChunkMap schedules these only after tickets expire and the holder's chunk-to-save future completes.
 * The ordinary chunk-task poller does not drain this queue. Running its callbacks retains vanilla's
 * save, Forge event, block-entity removal and changed-future retry; it does not manufacture an unload.
 * Only an explicit unload wait calls this helper. Other tests and ordinary servers are unchanged.
 */
public final class GameTestChunkUnloadPump {
    static final int MAX_TASKS_PER_TICK = 64;

    private GameTestChunkUnloadPump() { }

    /** One bounded pass per waiting tick, without ticking the level or advancing ticket time again. */
    public static void drainReady(ServerLevel level) {
        if (!level.getServer().isSameThread()) {
            throw new IllegalStateException("GameTest unload pumping runs on the server thread");
        }
        if (level.noSave()) {
            throw new IllegalStateException("GameTest unload pumping requires a save-enabled level");
        }
        // ChunkMap.unloadQueue in 1.20.1. Development-only access, like VanillaChunkSave; no AT or mixin.
        Queue<Runnable> queue = ObfuscationReflectionHelper.getPrivateValue(
                ChunkMap.class, level.getChunkSource().chunkMap, "f_140125_");
        drainReady(queue);
    }

    static int drainReady(Queue<Runnable> queue) {
        int ran = 0;
        while (ran < MAX_TASKS_PER_TICK) {
            Runnable task = queue.poll();
            if (task == null) { break; }
            task.run();
            ran++;
        }
        return ran;
    }
}
