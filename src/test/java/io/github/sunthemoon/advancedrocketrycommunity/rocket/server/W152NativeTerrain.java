package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.AnvilRecordReader;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

/** Fixture-selected incremental vanilla save. It never writes U/entities or supplies a production proof. */
final class W152NativeTerrain {
    static void save(ServerLevel level, BlockPos position, String prefix, Consumer<String> observe) {
        var chunkPos = new ChunkPos(position); var chunk = level.getChunkSource().getChunkNow(chunkPos.x, chunkPos.z);
        var target = level.getBlockEntity(position);
        if (chunk == null || target == null) { throw new IllegalStateException("Native incremental-save fixture is absent"); }
        // Match the actual ChunkSerializer frame, including vanilla's keepPacked=false metadata.
        CompoundTag expected = chunk.getBlockEntityNbtForSaving(position); var map = level.getChunkSource().chunkMap;
        if (expected == null) { throw new IllegalStateException("Vanilla member serialization refused"); }
        try {
            chunk.setUnsaved(true);
            // Version-pinned 1.20.1 mapping: ChunkMap.save(ChunkAccess), including its native serialization/save event.
            var save = ObfuscationReflectionHelper.findMethod(ChunkMap.class, "m_140258_", ChunkAccess.class);
            if (!(boolean) save.invoke(map, chunk)) { throw new IllegalStateException("Vanilla incremental chunk save refused"); }
            map.worker.synchronize(false).join(); observe.accept(prefix + "_STORE_AFTER");
            map.worker.synchronize(true).join(); observe.accept(prefix + "_FORCE_AFTER");
            var root = AnvilRecordReader.read(level.getServer().getWorldPath(LevelResource.ROOT).resolve("region"), chunkPos).root();
            boolean equal = root.getList("block_entities", Tag.TAG_COMPOUND).stream().anyMatch(expected::equals);
            if (!equal) { throw new IllegalStateException("Incremental native readback differs from its exact member"); }
            observe.accept(prefix + "_READBACK_AFTER");
        } catch (ReflectiveOperationException | java.io.IOException failure) { throw new IllegalStateException(failure); }
    }
    private W152NativeTerrain() { }
}
