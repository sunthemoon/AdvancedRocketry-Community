package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import com.mojang.datafixers.util.Either;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReferenceArray;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

/** Synchronous native test fixture: eligible visible holder, but an unfinished FULL future and no cached chunk. */
final class IncompleteFullChunk implements AutoCloseable {
    private static final Method VISIBLE = ObfuscationReflectionHelper.findMethod(ChunkMap.class, "m_140327_", long.class);
    private static final Field FUTURES = ObfuscationReflectionHelper.findField(ChunkHolder.class, "f_140001_");
    private static final Method CLEAR_CACHE = ObfuscationReflectionHelper.findMethod(ServerChunkCache.class, "m_8488_");
    private final ServerChunkCache source;
    private final AtomicReferenceArray<CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>>> futures;
    private final CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>> original;
    private final CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>> pending = new CompletableFuture<>();

    @SuppressWarnings("unchecked")
    IncompleteFullChunk(ServerLevel level, BlockPos position) {
        source = level.getChunkSource();
        try {
            ChunkHolder holder = (ChunkHolder) VISIBLE.invoke(source.chunkMap, new ChunkPos(position).toLong());
            if (holder == null) { throw new IllegalStateException("No visible holder"); }
            futures = (AtomicReferenceArray<CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>>>) FUTURES.get(holder);
            original = futures.get(ChunkStatus.FULL.getIndex());
            if (original == null || !original.isDone() || original.getNow(null) == null) {
                throw new IllegalStateException("Original FULL future not complete");
            }
            futures.set(ChunkStatus.FULL.getIndex(), pending);
            CLEAR_CACHE.invoke(source);
            // Safety completion prevents a regression's blocking native getChunk read from hanging the server.
            // Successful loaded-only reads must return before this completion and leave pending unfinished.
            CompletableFuture.delayedExecutor(2, TimeUnit.SECONDS).execute(() -> pending.complete(original.join()));
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Incomplete FULL chunk fixture unavailable", failure);
        }
    }

    boolean completed() { return pending.isDone(); }

    @Override public void close() {
        futures.set(ChunkStatus.FULL.getIndex(), original);
        pending.complete(original.join());
        try { CLEAR_CACHE.invoke(source); } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Could not restore FULL chunk fixture", failure);
        }
    }
}
