package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.ChunkDataEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * GameTest support: records the game time of every save of the watched chunks, real or posted by a test. Vanilla
 * saves dirty chunks on its own between ticks, so a test that relies on the absence of a save checks this record
 * instead of assuming one. Register with {@link #start}, and {@link #stop} at the end.
 */
public final class ChunkSaveWatcher {
    private final ServerLevel level;
    private final Map<Long, List<Long>> saves = new HashMap<>();

    private ChunkSaveWatcher(ServerLevel level, BlockPos... watched) {
        this.level = level;
        for (BlockPos pos : watched) {
            saves.put(new ChunkPos(pos).toLong(), new ArrayList<>());
        }
    }

    static ChunkSaveWatcher start(ServerLevel level, BlockPos... watched) {
        ChunkSaveWatcher watcher = new ChunkSaveWatcher(level, watched);
        MinecraftForge.EVENT_BUS.register(watcher);
        return watcher;
    }

    void stop() {
        MinecraftForge.EVENT_BUS.unregister(this);
    }

    @SubscribeEvent
    public void onSave(ChunkDataEvent.Save event) {
        List<Long> ticks = saves.get(event.getChunk().getPos().toLong());
        if (event.getLevel() == level && ticks != null) {
            ticks.add(level.getGameTime());
        }
    }

    /** A save of this position's chunk at or after {@code from} and at or before {@code to}. */
    boolean between(BlockPos pos, long from, long to) {
        return saves.get(new ChunkPos(pos).toLong()).stream().anyMatch(tick -> tick >= from && tick <= to);
    }

    /** Whether any save of this position's chunk was recorded. */
    boolean any(BlockPos pos) {
        return !saves.get(new ChunkPos(pos).toLong()).isEmpty();
    }

    @Override
    public String toString() {
        return saves.toString();
    }
}
