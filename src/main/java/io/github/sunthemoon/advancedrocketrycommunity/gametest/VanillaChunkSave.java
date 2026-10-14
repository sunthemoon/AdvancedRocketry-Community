package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

/**
 * GameTest support: vanilla's own eager save of one chunk, {@code ChunkMap.saveChunkIfNeeded}, which
 * {@code ChunkMap.processUnloads} runs for up to 20 chunks while a tick has time. Unlike
 * {@code ServerChunkCache.save(false)} it leaves the other tests' chunks alone. Like vanilla's sweep it does nothing
 * until the chunk's {@code ChunkHolder.chunkToSave} future is complete, which waits for the generation steps of every
 * chunk in range, and a save puts the chunk on that path's 10-second cooldown, which autosave also honours.
 */
final class VanillaChunkSave {
    // Development-only access, like the adjacent native fixtures; test code adds no access transformer.
    private static final Method VISIBLE = ObfuscationReflectionHelper.findMethod(ChunkMap.class, "m_140327_", long.class);
    private static final Method SAVE_IF_NEEDED = ObfuscationReflectionHelper.findMethod(ChunkMap.class, "m_198874_", ChunkHolder.class);

    private VanillaChunkSave() { }

    /** Whether vanilla saved the chunk now; false while it is not yet saveable, is clean, or is cooling down. */
    static boolean saveIfNeeded(ServerLevel level, ChunkPos pos) {
        ChunkMap map = level.getChunkSource().chunkMap;
        try {
            ChunkHolder holder = (ChunkHolder) VISIBLE.invoke(map, pos.toLong());
            return holder != null && (boolean) SAVE_IF_NEEDED.invoke(map, holder);
        } catch (IllegalAccessException | InvocationTargetException failure) {
            throw new IllegalStateException("Vanilla chunk save unavailable", failure);
        }
    }
}
