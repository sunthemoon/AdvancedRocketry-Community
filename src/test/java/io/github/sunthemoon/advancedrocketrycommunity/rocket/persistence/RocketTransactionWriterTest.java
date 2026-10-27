package io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence;

import static io.github.sunthemoon.advancedrocketrycommunity.persistence.CheckedJournalCheckpoint.Outcome.*;
import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataPolicy;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.RocketLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketStats;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.*;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** ADR-074 items 2, 3 and 8: the journal's capture, its IO encoder and owner-thread publication. */
class RocketTransactionWriterTest {
    private static final ManagedSavedDataType TYPE = ManagedSavedDataType.ROCKET_TRANSACTIONS;
    private static final CheckedSavedDataPolicy ATOMIC = CheckedSavedDataPolicy.atomic(TYPE);
    private static final ResourceLocation OVERWORLD = new ResourceLocation("minecraft:overworld");
    private static final UUID OWNER = UUID.randomUUID();
    @TempDir Path directory;
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void captureEncodingIsByteIdenticalToTheSynchronousSaveOnSmallFixtures() throws IOException {
        var empty = new RocketTransactionSavedData();
        assertArrayEquals(bytes(empty.save(new CompoundTag())), bytes(encode(empty)));
        var mixed = new RocketTransactionSavedData();
        // Insertion order differs from transaction-ID order, so the IO-side sort is exercised.
        for (int i = 9; i >= 0; i--) {
            var type = i % 3 == 0 ? RocketTransactionType.DISASSEMBLY : RocketTransactionType.ASSEMBLY;
            insert(mixed, new UUID(i * 7919L % 11, i), type, i % 2 == 0 ? UUID.randomUUID() : null, i);
        }
        assertNotEquals(mixed.entries().get(0).record().transactionId(),
                mixed.captureForTests().entries().get(0).record().transactionId(), "The fixture does not exercise sorting");
        assertArrayEquals(bytes(mixed.save(new CompoundTag())), bytes(encode(mixed)));
        assertEquals(mixed.save(new CompoundTag()), encode(mixed));
    }

    @Test void captureEncodingIsByteIdenticalToTheSynchronousSaveWithBoundPaddedRecords() throws Exception {
        // W128's maximum records (2,048 blocks, bound padding), 16 of them in reverse transaction-ID order. The whole
        // 256-record 64 MiB fixture does not fit this suite's 512 MB test heap; B2 (Xmx4G) asserts it byte-identical.
        int padding = RocketTransactionCheckpointBenchmark.padding();
        var data = new RocketTransactionSavedData();
        for (int i = 15; i >= 0; i--) {
            var snapshot = RocketTransactionCheckpointBenchmark.snapshot(padding, i);
            data.journalFor(snapshot, new UUID(131, i + 1)).write(new RocketTransactionRecord(new UUID(130, i + 1),
                    RocketTransactionType.ASSEMBLY, RocketTransactionPhase.SNAPSHOT_VALIDATED, snapshot.snapshotId(),
                    snapshot.contentHash(), RocketRegion.fromSnapshot(snapshot), 0, null));
        }
        String synchronous = digest(data.save(new CompoundTag()));
        String captured = digest(encode(data));
        assertEquals(synchronous, captured);
        long bytes = Long.parseLong(captured.substring(captured.indexOf(':') + 1));
        assertTrue(bytes > 16L * padding, "The fixture lost its bound padding: " + bytes);
    }

    @Test void aCaptureHoldsReferencesThatLaterRamAndSuppliedObjectsCannotChange() {
        var blocks = new ArrayList<RocketBlock>(); var properties = new HashMap<String, String>();
        properties.put("facing", "north");
        var supplied = new CompoundTag(); supplied.putString("probe", "before");
        blocks.add(new RocketBlock(new RocketPosition(0, 0, 0), new RocketBlockState(
                new ResourceLocation("minecraft:furnace"), properties),
                new RocketBlockEntityPayload(new ResourceLocation("minecraft:furnace"), supplied)));
        var anchors = new ArrayList<>(List.of(new RocketPosition(0, 0, 0)));
        var snapshot = RocketStructureSnapshot.create(UUID.randomUUID(), OVERWORLD, new RocketPosition(8, 64, -2), blocks,
                anchors, new RocketStats(1, 10, 0, 0, 0, 1, 0, 1), 42L);
        var data = new RocketTransactionSavedData();
        var journal = data.journalFor(snapshot, OWNER);
        var record = record(UUID.randomUUID(), RocketTransactionType.ASSEMBLY, snapshot, UUID.randomUUID(), 1);
        journal.write(record);
        var capture = data.captureForTests();
        CompoundTag before = RocketTransactionWriter.encode(capture.entries(), new CompoundTag());
        blocks.clear(); anchors.clear(); properties.put("facing", "south"); supplied.putString("probe", "supplied");
        snapshot.blocks().get(0).blockEntityPayload().orElseThrow().data().putString("probe", "accessor");
        assertThrows(UnsupportedOperationException.class, () -> capture.entries().clear());
        journal.write(new RocketTransactionRecord(record.transactionId(), record.type(), RocketTransactionPhase.COMMITTED,
                record.snapshotId(), record.contentHash(), record.region(), 2, record.rocketEntityIdOptional().orElseThrow()));
        insert(data, UUID.randomUUID(), RocketTransactionType.DISASSEMBLY, null, 3);
        assertEquals(1, capture.entries().size());
        assertEquals(before, RocketTransactionWriter.encode(capture.entries(), new CompoundTag()),
                "RAM or supplied objects changed a capture's encoding");
        assertThrows(IllegalArgumentException.class, () -> new RocketTransactionWriter.Capture(1,
                java.util.Collections.nCopies(RocketLimits.MAX_ACTIVE_TRANSACTIONS + 1, capture.entries().get(0)), null,
                UNACKNOWLEDGED));
    }

    @Test void publicationIsAsynchronousAndHappensOnlyWhenTheOwnerPolls() throws Exception {
        var data = new RocketTransactionSavedData(); insert(data, UUID.randomUUID(), RocketTransactionType.ASSEMBLY, null, 1);
        var worker = new AtomicReference<Thread>();
        assertTrue(data.requestWrite(target(), ATOMIC.withCommitter((staged, file) -> {
            worker.set(Thread.currentThread()); CheckedSavedDataFile.atomicMove(staged, file);
        })));
        assertFalse(data.acknowledgedCurrent()); assertEquals(UNACKNOWLEDGED, data.writeOutcome());
        assertTrue(data.writerBusy());
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!data.writerCompletionReady()) {
            assertTrue(System.nanoTime() < deadline, "The IO thread did not complete"); Thread.sleep(1);
        }
        assertTrue(Files.exists(target()));
        assertFalse(data.acknowledgedCurrent(), "IO published its own completion");
        assertTrue(data.isDirty());
        data.pollWriter();
        assertTrue(data.acknowledgedCurrent()); assertFalse(data.isDirty()); assertFalse(data.writerBusy());
        assertNotSame(Thread.currentThread(), worker.get());
        assertEquals(data.mutationSequence(), data.acknowledgement().orElseThrow().sequence());
        assertThrows(IllegalStateException.class, () -> {
            var failure = new AtomicReference<RuntimeException>();
            Thread other = new Thread(() -> { try { data.pollWriter(); } catch (RuntimeException f) { failure.set(f); } });
            other.start(); other.join();
            if (failure.get() != null) { throw failure.get(); }
        });
    }

    private Path target() { return directory.resolve(TYPE.fileName()); }
    private static CompoundTag encode(RocketTransactionSavedData data) {
        return RocketTransactionWriter.encode(data.captureForTests().entries(), new CompoundTag());
    }
    private static byte[] bytes(CompoundTag tag) throws IOException {
        var buffer = new ByteArrayOutputStream();
        try (var out = new DataOutputStream(buffer)) { NbtIo.write(tag, out); }
        return buffer.toByteArray();
    }
    /** SHA-256 and length of the uncompressed NBT bytes, streamed so that only one tree is resident at a time. */
    private static String digest(CompoundTag tag) throws Exception {
        var sha = MessageDigest.getInstance("SHA-256");
        try (var out = new DataOutputStream(new DigestOutputStream(OutputStream.nullOutputStream(), sha))) {
            NbtIo.write(tag, out);
            return HexFormat.of().formatHex(sha.digest()) + ":" + Integer.toUnsignedLong(out.size());
        }
    }
    private static void insert(RocketTransactionSavedData data, UUID transaction, RocketTransactionType type, UUID entity,
                               int index) {
        var snapshot = RocketStructureSnapshot.create(UUID.randomUUID(), OVERWORLD, new RocketPosition(index * 32, 64, 0),
                List.of(new RocketBlock(new RocketPosition(0, 0, 0),
                        new RocketBlockState(new ResourceLocation("minecraft:iron_block"), Map.of()))), List.of(),
                new RocketStats(1, 10, 0, 0, 0, 0, 0, 0), index);
        data.journalFor(snapshot, OWNER).write(record(transaction, type, snapshot, entity, index));
    }
    private static RocketTransactionRecord record(UUID transaction, RocketTransactionType type,
                                                  RocketStructureSnapshot snapshot, UUID entity, int progress) {
        return new RocketTransactionRecord(transaction, type, RocketTransactionPhase.SPAWNED, snapshot.snapshotId(),
                snapshot.contentHash(), RocketRegion.fromSnapshot(snapshot), progress, entity);
    }
}
