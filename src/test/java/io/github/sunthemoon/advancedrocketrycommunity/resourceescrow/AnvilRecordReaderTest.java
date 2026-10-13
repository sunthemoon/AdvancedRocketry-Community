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
import java.util.Arrays;
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
    private static final ChunkPos NEIGHBOUR = new ChunkPos(-2, -1);
    private static final java.util.UUID LEDGER = EscrowTestFixtures.id(500);
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
    @Test void externalProbeReportsBoundedNativePayloadSizesWithoutAuthorizingProof() throws Exception {
        CompoundTag root = new CompoundTag(); root.putInt("value", 42); write(root);
        var bytes = new java.io.ByteArrayOutputStream();
        try (var output = new java.io.DataOutputStream(new java.util.zip.DeflaterOutputStream(bytes))) { NbtIo.write(root, output); }
        Files.write(directory.resolve("c.-1.-1.mcc"), bytes.toByteArray());
        try (FileChannel channel = FileChannel.open(file(), StandardOpenOption.WRITE, StandardOpenOption.READ)) {
            ByteBuffer slot = ByteBuffer.allocate(4); channel.read(slot, 1_023 * 4L); slot.flip(); long start = (slot.getInt() >>> 8) * 4_096L;
            channel.write(ByteBuffer.wrap(new byte[]{(byte) 130}), start + 4);
        }
        var probe = AnvilRecordReader.probe(directory, POSITION);
        assertTrue(probe.external()); assertEquals(bytes.size(), probe.compressedBytes());
        assertEquals(RocketNbtSize.uncompressedBytes(root), probe.decodedBytes()); assertFalse(probe.admissionRoom());
        assertThrows(IOException.class, () -> AnvilRecordReader.read(directory, POSITION));
    }
    /** Review W11b1 finding 7: external and oversized records are refusals with their own reason, not unstable reads. */
    @Test void externalAndOversizedRecordsAreRefusalsWithTheirBarrierReason() throws Exception {
        CompoundTag oversized = new CompoundTag(); oversized.putByteArray("x", new byte[AnvilRecordReader.DECODED_BYTES - 11]); write(oversized);
        var decoded = assertThrows(AnvilRecordReader.Refusal.class, () -> AnvilRecordReader.read(directory, POSITION));
        assertEquals(EscrowReason.BARRIER_BOUNDS, decoded.reason()); assertEquals(EscrowReason.BARRIER_BOUNDS, EscrowBarrier.readbackReason(decoded));
        CompoundTag nodes = new CompoundTag(); var list = new net.minecraft.nbt.ListTag();
        for (int i = 0; i < 262_143; i++) { list.add(net.minecraft.nbt.IntTag.valueOf(i)); }
        nodes.put("n", list); write(nodes); // 262,145 nodes in about 1 MiB.
        assertEquals(EscrowReason.BARRIER_BOUNDS, assertThrows(AnvilRecordReader.Refusal.class, () -> AnvilRecordReader.read(directory, POSITION)).reason());
        CompoundTag root = new CompoundTag(); root.putInt("value", 42); write(root);
        var unstable = assertThrows(IOException.class, () -> AnvilRecordReader.read(directory, POSITION, () -> changeTimestamp()));
        assertFalse(unstable instanceof AnvilRecordReader.Refusal); assertEquals(EscrowReason.BARRIER_UNSTABLE, EscrowBarrier.readbackReason(unstable));
        write(root);
        try (FileChannel channel = FileChannel.open(file(), StandardOpenOption.WRITE, StandardOpenOption.READ)) {
            ByteBuffer slot = ByteBuffer.allocate(4); channel.read(slot, 1_023 * 4L); slot.flip(); long start = (slot.getInt() >>> 8) * 4_096L;
            channel.write(ByteBuffer.wrap(new byte[]{(byte) 130}), start + 4);
        }
        var external = assertThrows(AnvilRecordReader.Refusal.class, () -> AnvilRecordReader.read(directory, POSITION));
        assertEquals(EscrowReason.BARRIER_EXTERNAL, external.reason()); assertEquals(EscrowReason.BARRIER_EXTERNAL, EscrowBarrier.readbackReason(external));
    }
    /** W29 interleaving 1: another chunk's save changes the region file's size and time, not this slot. */
    @Test void aNeighbourSaveInTheSameRegionDuringTheReadIsStable() throws Exception {
        CompoundTag root = new CompoundTag(); root.putString("value", "endpoint"); write(root);
        long size = Files.size(file()); int[] slot = slot(POSITION);
        var record = AnvilRecordReader.read(directory, POSITION, step(() -> write(NEIGHBOUR, noise(20_000, 1))));
        assertEquals(root, record.root()); assertTrue(Files.size(file()) > size, "The neighbour's save grew the region file");
        assertArrayEquals(slot, slot(POSITION));
        // Rewriting the neighbour frees and reallocates its sectors; this slot is still untouched.
        assertEquals(record.size(), AnvilRecordReader.probe(directory, POSITION, step(() -> write(NEIGHBOUR, noise(40_000, 2)))));
        assertEquals(root, AnvilRecordReader.read(directory, POSITION, step(() -> write(NEIGHBOUR, noise(100, 3)))).root());
    }
    /** W29 interleaving 2, replaying vanilla's steps: new sectors, then the header, then freeing the old sectors. */
    @Test void aRewriteOfThisChunkIsRefusedOrReadsTheConsistentOldRecordWhichTheValidatorRejects() throws Exception {
        var endpoint = new EscrowEndpoint(EscrowEndpoint.Kind.BLOCK_ENTITY, "minecraft:overworld", EscrowTestFixtures.id(1), null,
                "minecraft:chest", -8, 64, -8, EscrowEndpoint.Unit.TANK, 0, EscrowTestFixtures.hash(1), EscrowTestFixtures.hash(2));
        CompoundTag before = chunk(endpoint, 5_000, 0, null), after = chunk(endpoint, 4_000, 1, EscrowTestFixtures.id(7));
        var validator = EscrowTestEndpoints.validator(endpoint, EscrowTestEndpoints.diskState(after, endpoint, LEDGER));
        assertFalse(validator.test(before)); assertTrue(validator.test(after));
        // New sectors written, header not yet: the old slot still names the old record, which the validator rejects.
        Rewrite rewrite = rewrite(before, after);
        var old = AnvilRecordReader.read(directory, POSITION, step(rewrite::writeSectors)).root();
        assertEquals(before, old); assertFalse(validator.test(old));
        rewrite.writeHeader(); assertEquals(after, AnvilRecordReader.read(directory, POSITION).root(), "The replay is vanilla's write");
        // The header written between the two reads: refused, whether the sectors came before the first read or not.
        Rewrite both = rewrite(before, after);
        assertUnstable(() -> AnvilRecordReader.read(directory, POSITION, step(() -> { both.writeSectors(); both.writeHeader(); })));
        Rewrite split = rewrite(before, after); split.writeSectors();
        assertUnstable(() -> AnvilRecordReader.read(directory, POSITION, step(split::writeHeader)));
        // The whole vanilla write through RegionFile between the reads: refused.
        rewrite(before, after);
        assertUnstable(() -> AnvilRecordReader.read(directory, POSITION, step(() -> write(after))));
        assertTrue(validator.test(AnvilRecordReader.read(directory, POSITION).root()));
    }
    /** W29 interleaving 3: this chunk moves, and another chunk's save reuses the freed sectors. */
    @Test void oldSectorsFreedAndReusedBetweenTheReadsAreRefused() throws Exception {
        CompoundTag root = new CompoundTag(); root.putInt("value", 42); write(root); int[] slot = slot(POSITION);
        byte[] record = record(slot[0]);
        // The neighbour's record has identical bytes, so only the moved header entry can refuse it.
        assertUnstable(() -> AnvilRecordReader.read(directory, POSITION, step(() -> {
            write(noise(6_000, 4)); write(NEIGHBOUR, root);
            assertEquals(slot[0], slot(NEIGHBOUR)[0], "The neighbour reuses the freed sectors");
            assertArrayEquals(record, record(slot[0]));
        })));
        // The slot returns to the same sectors within one second (same timestamp): the record bytes refuse it.
        Files.delete(file()); write(root); int[] again = slot(POSITION);
        CompoundTag changed = new CompoundTag(); changed.putInt("value", 43);
        assertUnstable(() -> AnvilRecordReader.read(directory, POSITION, step(() -> {
            write(noise(6_000, 5)); write(changed); patch(4_096 + 1_023 * 4L, ByteBuffer.allocate(4).putInt(again[1]).array());
            assertArrayEquals(again, slot(POSITION), "The slot's header entries returned to their first values");
        })));
    }
    /** W29 interleaving 4: shrinking below the record's end refuses; the file may end at the record. */
    @Test void aTruncatedRegionFileIsRefused() throws Exception {
        CompoundTag root = new CompoundTag(); root.putInt("value", 42); write(root);
        int location = slot(POSITION)[0]; long end = (location >>> 8) * 4_096L + record(location).length;
        assertEquals(root, AnvilRecordReader.read(directory, POSITION, step(() -> truncate(end))).root());
        for (long size : new long[]{end - 1, 8_192, 0}) {
            Files.delete(file()); write(root);
            assertUnstable(() -> AnvilRecordReader.read(directory, POSITION, step(() -> truncate(size))));
        }
    }
    /** W29: the external probe applies the same slot rule to the region header; the .mcc file is this record alone. */
    @Test void externalProbeKeepsTheSlotRule() throws Exception {
        CompoundTag root = new CompoundTag(); root.putInt("value", 42); write(root);
        var bytes = new java.io.ByteArrayOutputStream();
        try (var output = new java.io.DataOutputStream(new java.util.zip.DeflaterOutputStream(bytes))) { NbtIo.write(root, output); }
        Files.write(directory.resolve("c.-1.-1.mcc"), bytes.toByteArray());
        patch((slot(POSITION)[0] >>> 8) * 4_096L + 4, new byte[]{(byte) 130});
        var probe = AnvilRecordReader.probe(directory, POSITION);
        assertTrue(probe.external()); assertEquals(probe, AnvilRecordReader.probe(directory, POSITION, step(() -> write(NEIGHBOUR, noise(20_000, 6)))));
        assertUnstable(() -> AnvilRecordReader.probe(directory, POSITION, this::changeTimestamp));
    }
    private interface Step { void run() throws Exception; }
    private static Runnable step(Step step) {
        return () -> { try { step.run(); } catch (Exception failure) { throw new IllegalStateException(failure); } };
    }
    private static void assertUnstable(org.junit.jupiter.api.function.Executable read) {
        var failure = assertThrows(IOException.class, read);
        assertFalse(failure instanceof AnvilRecordReader.Refusal); assertEquals(EscrowReason.BARRIER_UNSTABLE, EscrowBarrier.readbackReason(failure));
    }
    /** Vanilla's rewrite of this chunk, performed on a copy and split into its two file writes. */
    private record Rewrite(Path file, long start, byte[] record, byte[] header) {
        void writeSectors() throws IOException { patch(file, start, record); }
        void writeHeader() throws IOException { patch(file, 0, header); }
    }
    private Rewrite rewrite(CompoundTag first, CompoundTag second) throws Exception {
        Files.deleteIfExists(file()); write(first); int[] old = slot(POSITION);
        Path copy = Files.createTempDirectory(directory, "copy"), region = copy.resolve(file().getFileName());
        Files.copy(file(), region);
        try (RegionFile vanilla = new RegionFile(region, copy, false)) {
            try (var output = vanilla.getChunkDataOutputStream(POSITION)) { NbtIo.write(second, output); }
        }
        byte[] all = Files.readAllBytes(region); int location = ByteBuffer.wrap(all, 1_023 * 4, 4).getInt();
        int start = (location >>> 8) * 4_096, length = ByteBuffer.wrap(all, start, 4).getInt();
        assertTrue((location >>> 8) >= (old[0] >>> 8) + (old[0] & 255), "Vanilla allocates new sectors while the old ones are in use");
        return new Rewrite(file(), start, Arrays.copyOfRange(all, start, start + 4 + length), Arrays.copyOf(all, 8_192));
    }
    private static CompoundTag chunk(EscrowEndpoint endpoint, int amount, long epoch, java.util.UUID operation) {
        CompoundTag fixture = new CompoundTag(); fixture.putUUID("identity", endpoint.identity());
        fixture.putInt("amount", amount); fixture.putInt("capacity", 16_000);
        CompoundTag section = new CompoundTag(); section.putInt("schema_version", 1); section.putUUID("ledger_id", LEDGER);
        section.putLong("resource_epoch", epoch); var markers = new net.minecraft.nbt.ListTag();
        if (operation != null) {
            CompoundTag marker = new CompoundTag(); marker.putUUID("op_id", operation); marker.putLong("seq", 1);
            marker.putString("role", "SOURCE"); marker.putLong("epoch_after", epoch); markers.add(marker);
        }
        section.put("markers", markers);
        CompoundTag forge = new CompoundTag(); forge.put("arce_escrow_fixture", fixture); forge.put("resource_escrow", section);
        CompoundTag chest = new CompoundTag(); chest.putString("id", endpoint.type()); chest.putInt("x", endpoint.x());
        chest.putInt("y", endpoint.y()); chest.putInt("z", endpoint.z()); chest.put("ForgeData", forge);
        CompoundTag root = new CompoundTag(); root.putInt("xPos", -1); root.putInt("zPos", -1);
        var entities = new net.minecraft.nbt.ListTag(); entities.add(chest); root.put("block_entities", entities); return root;
    }
    private static CompoundTag noise(int bytes, long seed) {
        byte[] random = new byte[bytes]; new java.util.Random(seed).nextBytes(random);
        CompoundTag tag = new CompoundTag(); tag.putByteArray("noise", random); return tag;
    }
    /** The slot's location and timestamp header entries. */
    private int[] slot(ChunkPos pos) throws IOException {
        int index = (pos.x & 31) + ((pos.z & 31) << 5); var header = ByteBuffer.wrap(Files.readAllBytes(file()), 0, 8_192);
        return new int[]{header.getInt(index * 4), header.getInt(4_096 + index * 4)};
    }
    /** The 4-byte length, compression byte and payload that a location entry names. */
    private byte[] record(int location) throws IOException {
        byte[] all = Files.readAllBytes(file()); int start = (location >>> 8) * 4_096;
        return Arrays.copyOfRange(all, start, start + 4 + ByteBuffer.wrap(all, start, 4).getInt());
    }
    private void patch(long offset, byte[] bytes) throws IOException { patch(file(), offset, bytes); }
    private static void patch(Path file, long offset, byte[] bytes) throws IOException {
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.WRITE)) { channel.write(ByteBuffer.wrap(bytes), offset); }
    }
    private void truncate(long size) throws IOException {
        try (FileChannel channel = FileChannel.open(file(), StandardOpenOption.WRITE)) { channel.truncate(size); }
    }
    private void write(CompoundTag tag) throws Exception { write(POSITION, tag); }
    private void write(ChunkPos pos, CompoundTag tag) throws Exception {
        try (RegionFile region = new RegionFile(file(), directory, false)) {
            try (var output = region.getChunkDataOutputStream(pos)) { NbtIo.write(tag, output); }
            region.flush();
        }
    }
    private void changeTimestamp() {
        try (FileChannel channel = FileChannel.open(file(), StandardOpenOption.WRITE)) {
            channel.write(ByteBuffer.wrap(new byte[]{0, 0, 0, 1}), 4_096 + 1_023 * 4L);
        } catch (IOException failure) { throw new IllegalStateException(failure); }
    }
}
