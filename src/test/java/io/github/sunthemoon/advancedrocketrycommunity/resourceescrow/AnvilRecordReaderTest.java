package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNbtSize;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.RegionFile;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AnvilRecordReaderTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    private static final ChunkPos POSITION = new ChunkPos(-1, -1);
    @TempDir Path directory;
    private Path file() { return directory.resolve("r.-1.-1.mca"); }
    @Test void readsRealInlineAnvilFilesWithDetachedExactSizesAndAdmissionHeadroom() throws Exception {
        CompoundTag root = new CompoundTag(); root.putString("value", "native"); write(root);
        var record = AnvilRecordReader.read(directory, POSITION); assertEquals(root, record.root());
        assertEquals(RocketNbtSize.uncompressedBytes(root), record.size().decodedBytes()); assertFalse(record.size().external());
        assertTrue(record.size().admissionRoom()); record.root().putInt("changed", 1);
        assertEquals(root, record.root()); assertEquals(record.size(), AnvilRecordReader.probe(directory, POSITION));
    }
    @Test void acceptsTheDecodedLimitAndRefusesTheNextByteWithoutTruncation() throws Exception {
        CompoundTag root = new CompoundTag(); root.putByteArray("x", new byte[AnvilRecordReader.DECODED_BYTES - 12]); write(root);
        assertEquals(AnvilRecordReader.DECODED_BYTES, AnvilRecordReader.read(directory, POSITION).size().decodedBytes());
        assertFalse(AnvilRecordReader.probe(directory, POSITION).admissionRoom());
        root.putByteArray("x", new byte[AnvilRecordReader.DECODED_BYTES - 11]); write(root);
        assertThrows(IOException.class, () -> AnvilRecordReader.read(directory, POSITION));
        assertEquals(AnvilRecordReader.DECODED_BYTES + 1, AnvilRecordReader.probe(directory, POSITION).decodedBytes());
        assertFalse(AnvilRecordReader.probe(directory, POSITION).supported());
    }
    @Test void changingHeaderOrInPlaceRecordCannotConfirmAStableReadback() throws Exception {
        CompoundTag root = new CompoundTag(); root.putInt("value", 42); write(root);
        assertThrows(IOException.class, () -> AnvilRecordReader.read(directory, POSITION, () -> changeTimestamp()));
    }
    @Test void externalMissingAndMalformedRecordsNeverProveAnEndpoint() throws Exception {
        assertThrows(IOException.class, () -> AnvilRecordReader.read(directory, POSITION));
        CompoundTag root = new CompoundTag(); root.putInt("value", 42); write(root);
        try (FileChannel channel = FileChannel.open(file(), StandardOpenOption.WRITE, StandardOpenOption.READ)) {
            ByteBuffer slot = ByteBuffer.allocate(4); channel.read(slot, 1_023 * 4L); slot.flip(); long start = (slot.getInt() >>> 8) * 4_096L;
            channel.write(ByteBuffer.wrap(new byte[]{(byte) 130}), start + 4);
        }
        assertThrows(IOException.class, () -> AnvilRecordReader.read(directory, POSITION));
        var external = AnvilRecordReader.probe(directory, POSITION); assertTrue(external.external()); assertFalse(external.admissionRoom());
    }
    @Test void admissionThresholdsAreInclusiveAndNeverAcceptExternalRecords() {
        assertTrue(new AnvilRecordReader.SizeProbe(false, 786_432, 3_145_728, true).admissionRoom());
        assertFalse(new AnvilRecordReader.SizeProbe(false, 786_433, 3_145_728, true).admissionRoom());
        assertFalse(new AnvilRecordReader.SizeProbe(false, 786_432, 3_145_729, true).admissionRoom());
        assertFalse(new AnvilRecordReader.SizeProbe(true, 1, 1, true).admissionRoom());
    }
    private void write(CompoundTag tag) throws Exception {
        try (RegionFile region = new RegionFile(file(), directory, false)) {
            try (var output = region.getChunkDataOutputStream(POSITION)) { NbtIo.write(tag, output); }
            region.flush();
        }
    }
    private void changeTimestamp() {
        try (FileChannel channel = FileChannel.open(file(), StandardOpenOption.WRITE)) {
            channel.write(ByteBuffer.wrap(new byte[]{0, 0, 0, 1}), 4_096 + 1_023 * 4L);
        } catch (IOException failure) { throw new IllegalStateException(failure); }
    }
}
