package io.github.sunthemoon.advancedrocketrycommunity.machine.precision;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.RegionFile;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PrecisionAssemblerPersistedChunkReaderTest {
    @TempDir
    Path directory;

    @BeforeAll
    static void bootstrap() {
        MinecraftBootstrap.initialize();
    }

    @Test
    void readsMatchingBlockEntityFromNegativeChunkWithoutLoadingIt() throws IOException {
        BlockPos position = new BlockPos(-1, 80, -1);
        CompoundTag entity = entity(position);
        writeChunk(new ChunkPos(position), chunk(position, entity));

        assertEquals(entity, PrecisionAssemblerPersistedChunkReader.readBlockEntity(
                directory, position).orElseThrow());
        assertTrue(PrecisionAssemblerPersistedChunkReader.readBlockEntity(
                directory, new BlockPos(-2, 80, -1)).isEmpty());
        assertTrue(PrecisionAssemblerPersistedChunkReader.readBlockEntity(
                directory, new BlockPos(16, 80, 16)).isEmpty());
    }

    @Test
    void readsFlushedLiveRegionBeforeSectorPaddingWithoutChangingFile() throws IOException {
        BlockPos position = new BlockPos(2, -59, 155);
        ChunkPos chunkPosition = new ChunkPos(position);
        CompoundTag entity = entity(position);
        Path file = directory.resolve("r.0.0.mca");
        try (RegionFile region = new RegionFile(file, directory, false)) {
            try (var output = region.getChunkDataOutputStream(chunkPosition)) {
                NbtIo.write(chunk(position, entity), output);
            }
            region.flush();
            long flushedSize = Files.size(file);
            assertTrue(flushedSize % 4_096 != 0,
                    "Fixture must exercise a live region's unpadded final sector");
            assertEquals(entity, PrecisionAssemblerPersistedChunkReader.readBlockEntity(
                    directory, position).orElseThrow());
            assertEquals(flushedSize, Files.size(file), "Readback must not pad or rewrite the region");
        }
    }

    @Test
    void truncatedRecordHeaderOrPayloadFailsClosed() throws IOException {
        BlockPos position = new BlockPos(2, -59, 155);
        ChunkPos chunkPosition = new ChunkPos(position);
        Path file = directory.resolve("r.0.0.mca");
        writeChunk(chunkPosition, chunk(position, entity(position)));
        byte[] saved = Files.readAllBytes(file);
        int locationOffset = ((chunkPosition.x & 31) + ((chunkPosition.z & 31) << 5)) * 4;
        int start = (ByteBuffer.wrap(saved).getInt(locationOffset) >>> 8) * 4_096;
        int recordLength = ByteBuffer.wrap(saved).getInt(start);
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.WRITE)) {
            channel.truncate(start + 4L + recordLength - 1);
        }
        assertTrue(PrecisionAssemblerPersistedChunkReader.readBlockEntity(directory, position).isEmpty());
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.WRITE)) {
            channel.truncate(start + 4L);
        }
        assertTrue(PrecisionAssemblerPersistedChunkReader.readBlockEntity(directory, position).isEmpty());
    }

    @Test
    void duplicateOrWrongChunkIdentityIsRejected() throws IOException {
        BlockPos position = new BlockPos(143, 80, 143);
        CompoundTag wrong = chunk(position, entity(position));
        wrong.putInt("xPos", 7);
        writeChunk(new ChunkPos(position), wrong);
        assertTrue(PrecisionAssemblerPersistedChunkReader.readBlockEntity(directory, position).isEmpty());

        ListTag entities = wrong.getList("block_entities", 10);
        entities.add(entity(position));
        wrong.putInt("xPos", position.getX() >> 4);
        writeChunk(new ChunkPos(position), wrong);
        assertTrue(PrecisionAssemblerPersistedChunkReader.readBlockEntity(directory, position).isEmpty());
    }

    @Test
    void missingSectorAndOversizedDecodedChunkFailClosed() throws IOException {
        BlockPos position = new BlockPos(143, 80, 143);
        Path file = directory.resolve("r.0.0.mca");
        byte[] broken = new byte[8_192];
        int location = ((8 & 31) + ((8 & 31) << 5)) * 4;
        broken[location + 2] = 2;
        broken[location + 3] = 1;
        Files.write(file, broken);
        assertTrue(PrecisionAssemblerPersistedChunkReader.readBlockEntity(directory, position).isEmpty());

        CompoundTag oversized = chunk(position, entity(position));
        oversized.put("oversized", new ByteArrayTag(new byte[
                PrecisionAssemblerPersistedChunkReader.MAX_DECODED_CHUNK_BYTES + 1]));
        writeChunk(new ChunkPos(position), oversized);
        assertTrue(PrecisionAssemblerPersistedChunkReader.readBlockEntity(directory, position).isEmpty());
    }

    @Test
    void comparesExactMigrationRootsAndIdentity() {
        BlockPos position = new BlockPos(143, 80, 143);
        CompoundTag expected = entity(position);
        expected.put("arce_precision_resources", new CompoundTag());
        CompoundTag actual = expected.copy();
        assertTrue(PrecisionAssemblerMigrationSaveVerifier.matches(
                expected, Optional.of(actual), "arce_precision_resources"));

        actual.putString("unrelated", "allowed");
        assertTrue(PrecisionAssemblerMigrationSaveVerifier.matches(
                expected, Optional.of(actual), "arce_precision_resources"));
        actual.getCompound("arce_precision_resources").putInt("schema_version", 2);
        assertFalse(PrecisionAssemblerMigrationSaveVerifier.matches(
                expected, Optional.of(actual), "arce_precision_resources"));
        assertFalse(PrecisionAssemblerMigrationSaveVerifier.matches(
                expected, Optional.empty(), "arce_precision_resources"));
    }

    private void writeChunk(ChunkPos position, CompoundTag root) throws IOException {
        Files.createDirectories(directory);
        Path file = directory.resolve("r." + Math.floorDiv(position.x, 32)
                + "." + Math.floorDiv(position.z, 32) + ".mca");
        try (RegionFile region = new RegionFile(file, directory, false);
             var output = region.getChunkDataOutputStream(position)) {
            NbtIo.write(root, output);
        }
    }

    private static CompoundTag chunk(BlockPos position, CompoundTag entity) {
        CompoundTag root = new CompoundTag();
        root.putInt("xPos", position.getX() >> 4);
        root.putInt("zPos", position.getZ() >> 4);
        ListTag entities = new ListTag();
        entities.add(entity);
        root.put("block_entities", entities);
        return root;
    }

    private static CompoundTag entity(BlockPos position) {
        CompoundTag value = new CompoundTag();
        value.putString("id", "advancedrocketrycommunity:precision_assembler");
        value.putInt("x", position.getX());
        value.putInt("y", position.getY());
        value.putInt("z", position.getZ());
        return value;
    }
}
