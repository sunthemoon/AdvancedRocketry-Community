package io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataPolicy;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedRootSupplier;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketStats;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.*;
import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

/** Test-export-only ballast and package-local writer observations; no production registration. */
public final class W208JournalFixture {
    public static final int BALLAST = 255;
    public static final long BOUND = 64L * 1024 * 1024;
    public static final ResourceLocation ABSENT = ResourceLocation.tryParse("advancedrocketrycommunity:w208_absent");
    private static final ManagedSavedDataType TYPE = ManagedSavedDataType.ROCKET_TRANSACTIONS;

    public static UUID id(String name) {
        return UUID.nameUUIDFromBytes(("w208:" + name).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    public static RocketPersistedTransaction ballast(int index, int padding) {
        if (index < 0 || index >= BALLAST || padding < 0 || padding > 500_000) {
            throw new IllegalArgumentException("Invalid ballast");
        }
        var blocks = new ArrayList<RocketBlock>();
        byte[] entropy = new byte[(padding + 3) / 4]; new Random(20261030L + index).nextBytes(entropy);
        for (int i = 0; i < 2048; i++) {
            RocketBlockEntityPayload payload = null;
            if (i < 4 && padding > 0) {
                var tag = new CompoundTag();
                tag.putByteArray("benchmark", Arrays.copyOf(entropy, padding / 4 + (i < padding % 4 ? 1 : 0)));
                payload = new RocketBlockEntityPayload(ResourceLocation.tryParse("advancedrocketrycommunity:benchmark"), tag);
            }
            blocks.add(new RocketBlock(new RocketPosition(i % 16, i / 256, (i / 16) % 16),
                    new RocketBlockState(ResourceLocation.tryParse("minecraft:iron_block"), Map.of()), payload));
        }
        var snapshot = RocketStructureSnapshot.create(id("ballast-snapshot-" + index), ABSENT,
                new RocketPosition(index * 64, 80, 0), blocks, List.of(new RocketPosition(0, 1, 0)),
                new RocketStats(2048, 1000, 2000, 1000, 1, 1, 1, padding > 0 ? 4 : 0), 0);
        var record = new RocketTransactionRecord(id("ballast-" + index), RocketTransactionType.ASSEMBLY,
                RocketTransactionPhase.SNAPSHOT_VALIDATED, snapshot.snapshotId(), snapshot.contentHash(),
                RocketRegion.fromSnapshot(snapshot), 0, null);
        return new RocketPersistedTransaction(record, snapshot, id("ballast-owner"));
    }

    public static long projectedBytes(int padding) {
        long header = RocketNbtSize.uncompressedBytes(new RocketTransactionSavedData().save(new CompoundTag()));
        return header + BALLAST * (long) (RocketNbtSize.uncompressedBytes(RocketTransactionCodec.encodeEntry(ballast(0, padding))) - 3);
    }

    public static int padding() {
        int low = 1, high = 500_000;
        while (low < high) {
            int mid = (low + high + 1) >>> 1;
            if (projectedBytes(mid) <= BOUND - 8192) { low = mid; } else { high = mid - 1; }
        }
        return low;
    }

    public static long seed(RocketTransactionSavedData data) {
        int padding = padding();
        for (int i = 0; i < BALLAST; i++) {
            var entry = ballast(i, padding);
            data.journalFor(entry.snapshot(), entry.ownerId()).write(entry.record());
        }
        long bytes = RocketNbtSize.uncompressedBytes(data.save(new CompoundTag()));
        if (bytes < .99 * BOUND || bytes > BOUND) { throw new IllegalStateException("Unqualified near-limit root: " + bytes); }
        return bytes;
    }

    public static void traffic(RocketTransactionSavedData data, int progress) {
        var entry = data.entries().stream().filter(e -> e.record().transactionId().equals(id("ballast-0"))).findFirst().orElseThrow();
        var r = entry.record();
        data.journalFor(entry.snapshot(), entry.ownerId()).write(new RocketTransactionRecord(r.transactionId(), r.type(), r.phase(),
                r.snapshotId(), r.contentHash(), r.region(), progress, null));
    }

    public static boolean request(MinecraftServer server, CheckedSavedDataPolicy policy) {
        return RocketTransactionSavedData.get(server).requestWrite(ManagedRootSupplier.dataFile(server, TYPE), policy);
    }

    public static Map<String, Object> observation(RocketTransactionSavedData data) {
        return Map.of("writes", data.completedWriteCount(), "writer_ns", data.lastWriterNanos(),
                "writer_cpu_ns", data.lastWriterCpuNanos(), "sequence", data.mutationSequence(),
                "ack", data.acknowledgement().map(a -> a.sequence()).orElse(-1L),
                "outcome", data.writeOutcome().name(), "current", data.acknowledgedCurrent(), "busy", data.writerBusy());
    }

    public static Map<String, Object> close(RocketTransactionSavedData data) {
        long start = System.nanoTime(); data.latchShutdown(); data.close();
        var report = data.drainReport().orElseThrow();
        return Map.of("close_ns", System.nanoTime() - start, "success", report.success(), "reason", report.reason(),
                "interrupt_preserved", Thread.currentThread().isInterrupted(), "report", report.toString());
    }
    private W208JournalFixture() { }
}
