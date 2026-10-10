package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import java.lang.reflect.Field;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.ChunkDataEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * GameTest support: records the game times of saves of the watched chunks, real or posted by a test. Vanilla
 * saves dirty chunks on its own between ticks, so a test that relies on the absence of a save checks this record
 * instead of assuming one. The owning test's terminal listener and matching server stop detach the observer even
 * after a timeout. Explicit {@link #stop} also checks that the bounded history remained complete.
 */
public final class ChunkSaveWatcher implements GameTestListener {
    private final ServerLevel level;
    private final ChunkSaveWatchState observations;

    private ChunkSaveWatcher(ServerLevel level, BlockPos... watched) {
        this.level = level;
        if (watched.length == 0 || watched.length > ChunkSaveWatchState.MAX_WATCHED_CHUNKS) {
            throw new IllegalArgumentException("The fixture must watch one or two chunk positions");
        }
        long[] chunks = new long[watched.length];
        for (int i = 0; i < watched.length; i++) {
            chunks[i] = new ChunkPos(watched[i]).toLong();
        }
        observations = new ChunkSaveWatchState(() -> MinecraftForge.EVENT_BUS.unregister(this), chunks);
    }

    static ChunkSaveWatcher start(GameTestHelper helper, BlockPos... watched) {
        ChunkSaveWatcher watcher = new ChunkSaveWatcher(helper.getLevel(), watched);
        try {
            // Same development-only terminal-listener access as the adjacent native fixtures; no AT needed.
            Field field = GameTestHelper.class.getDeclaredField("testInfo");
            field.setAccessible(true);
            ((GameTestInfo) field.get(helper)).addListener(watcher);
            MinecraftForge.EVENT_BUS.register(watcher);
            return watcher;
        } catch (ReflectiveOperationException cause) {
            IllegalStateException failure = new IllegalStateException("Save watcher terminal listener unavailable", cause);
            watcher.observations.close(failure);
            throw failure;
        } catch (RuntimeException | Error failure) {
            watcher.observations.close(failure);
            throw failure;
        }
    }

    void stop() {
        observations.stop();
    }

    @SubscribeEvent
    public void onSave(ChunkDataEvent.Save event) {
        if (!observations.isClosed() && event.getLevel() == level) {
            observations.record(event.getChunk().getPos().toLong(), level.getGameTime());
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onStopping(ServerStoppingEvent event) {
        if (event.getServer() == level.getServer()) {
            observations.close(null);
        }
    }

    @Override public void testStructureLoaded(GameTestInfo info) { }
    @Override public void testPassed(GameTestInfo info) { observations.close(null); }
    @Override public void testFailed(GameTestInfo info) { observations.close(info.getError()); }

    /** A save of this position's chunk at or after {@code from} and at or before {@code to}. */
    boolean between(BlockPos pos, long from, long to) {
        return observations.between(new ChunkPos(pos).toLong(), from, to);
    }

    /** Whether any save of this position's chunk was recorded. */
    boolean any(BlockPos pos) {
        return observations.any(new ChunkPos(pos).toLong());
    }

    @Override
    public String toString() {
        return observations.toString();
    }
}
