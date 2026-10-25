package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.sealed.SealedRocketBlockEntity;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

/** Resumable existing loaded-map cursors. No tickets, whole-world copies or eager BE enumeration. */
final class SealedLoadedDiscovery {
    record Hint(ResourceKey<Level> dimension, BlockPos position) {
        Hint { position = position.immutable(); }
    }
    private final MinecraftServer server;
    private Iterator<ServerLevel> levels = Collections.emptyIterator();
    private Iterator<ChunkHolder> chunks = Collections.emptyIterator();
    private Iterator<BlockEntity> entities;
    private ServerLevel level;
    private LevelChunk chunk;

    SealedLoadedDiscovery(MinecraftServer server) { this.server = server; }

    /** Exactly one visited entity, chunk or Level advance; the caller charges it before entry. */
    Hint advance() {
        if (!server.isSameThread()) { throw new IllegalStateException("Loaded discovery requires the server thread"); }
        if (chunk != null) {
            if (server.getLevel(level.dimension()) != level
                    || level.getChunkSource().getChunkNow(chunk.getPos().x, chunk.getPos().z) != chunk) {
                chunk = null; entities = null; return null;
            }
            try {
                if (entities.hasNext()) {
                    var entity = entities.next();
                    return entity instanceof SealedRocketBlockEntity && !entity.isRemoved()
                            ? new Hint(level.dimension(), entity.getBlockPos()) : null;
                }
            } catch (ConcurrentModificationException changed) {
                // A publication or unrelated BE edit invalidated this cursor. Move to the next chunk,
                // not the first chunk; the next complete sweep revisits any remaining members fairly.
            }
            chunk = null; entities = null; return null;
        }
        if (chunks.hasNext()) {
            var at = chunks.next().getPos();
            chunk = server.getLevel(level.dimension()) == level ? level.getChunkSource().getChunkNow(at.x, at.z) : null;
            if (chunk != null) { entities = chunk.getBlockEntities().values().iterator(); }
            return null;
        }
        try {
            if (levels.hasNext()) {
                level = levels.next();
                // Pinned ChunkMap.getChunks returns an unmodifiable view of its promoted map, not a new copy.
                chunks = level.getChunkSource().chunkMap.getChunks().iterator();
                return null;
            }
        } catch (ConcurrentModificationException changed) {
            // Dimension lifecycle changes do not justify retaining or loading a removed Level.
        }
        levels = server.getAllLevels().iterator(); level = null; chunks = Collections.emptyIterator();
        return null;
    }
}
