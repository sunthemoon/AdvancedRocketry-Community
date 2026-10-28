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
    private int revisitsRemaining;
    private boolean publishedHere;

    SealedLoadedDiscovery(MinecraftServer server) { this.server = server; }

    /** Exactly one visited entity, iterator restart, chunk or Level advance; the caller charges it before entry. */
    Hint advance() {
        if (!server.isSameThread()) { throw new IllegalStateException("Loaded discovery requires the server thread"); }
        if (chunk != null) {
            if (server.getLevel(level.dimension()) != level
                    || level.getChunkSource().getChunkNow(chunk.getPos().x, chunk.getPos().z) != chunk) {
                chunk = null; entities = null; return null;
            }
            // A publication's own edit fails this very step; any later step is past it.
            boolean ownEdit = publishedHere; publishedHere = false;
            try {
                if (entities.hasNext()) {
                    var entity = entities.next();
                    return entity instanceof SealedRocketBlockEntity && !entity.isRemoved()
                            ? new Hint(level.dimension(), entity.getBlockPos()) : null;
                }
            } catch (ConcurrentModificationException changed) {
                // Our publication removes/replaces a carrier. Finish this chunk without waiting for another world
                // sweep. Any other edit (a piston, a placed chest) moves on as before W186, so a busy chunk cannot
                // hold the sweep (R155). Each restart consumes this already-charged step; inspecting the next
                // entity needs a new discovery turn.
                if (ownEdit && revisitsRemaining > 0) {
                    revisitsRemaining--;
                    entities = chunk.getBlockEntities().values().iterator();
                    return null;
                }
            }
            chunk = null; entities = null; return null;
        }
        if (chunks.hasNext()) {
            var at = chunks.next().getPos();
            chunk = server.getLevel(level.dimension()) == level ? level.getChunkSource().getChunkNow(at.x, at.z) : null;
            if (chunk != null) {
                // Capture once per visit: additions during a revisit must not replenish the allowance.
                revisitsRemaining = chunk.getBlockEntities().size(); publishedHere = false;
                entities = chunk.getBlockEntities().values().iterator();
            }
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

    /** A lazy attempt published the carrier here. Only a carrier in the visited chunk lets the next step restart. */
    void published(ResourceKey<Level> dimension, BlockPos position) {
        if (!server.isSameThread()) { throw new IllegalStateException("Loaded discovery requires the server thread"); }
        if (chunk != null && level.dimension().equals(dimension)
                && chunk.getPos().x == position.getX() >> 4 && chunk.getPos().z == position.getZ() >> 4) {
            publishedHere = true;
        }
    }
}
