package io.github.sunthemoon.advancedrocketrycommunity.persistence.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationLimits;
import io.github.sunthemoon.advancedrocketrycommunity.station.persistence.StationRegistrySavedData;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A registry at its own bounds (4,096 stations, 64 invitations-free records) must survive the bounded
 * disk codec: the raw decompressed-byte bound is the size limit; the heap accounting quota must not
 * reject data that the payload bound accepts.
 */
final class BoundedSavedDataIoCapacityTest {
    @TempDir Path root;

    @BeforeAll
    static void bootstrap() {
        MinecraftBootstrap.initialize();
    }

    @Test
    void fullStationRegistryRoundTripsThroughTheBoundedCodec() throws Exception {
        StationRegistrySavedData data = new StationRegistrySavedData();
        for (int index = 0; index < StationLimits.MAX_STATIONS; index++) {
            UUID id = UUID.randomUUID();
            data.reserve(id, UUID.randomUUID(), "Capacity station " + index, ModIdentity.id("earth"), index);
            data.commit(id);
        }
        CompoundTag payload = data.save(new CompoundTag());
        CompoundTag outer = new CompoundTag();
        outer.put("data", payload);
        long raw = serialized(outer);
        NbtAccounter heap = new NbtAccounter(Long.MAX_VALUE);
        NbtIo.read(new DataInputStream(new ByteArrayInputStream(bytes(outer))), heap);
        System.out.printf("ARCE_BOUNDED_IO_CAPACITY stations=%d raw_bytes=%d heap_accounted=%d ratio=%.2f%n",
                StationLimits.MAX_STATIONS, raw, heap.getUsage(), heap.getUsage() / (double) raw);
        assertTrue(raw <= ManagedSavedDataType.STATIONS.maxCompressedBytes(), "Payload exceeds its raw bound");

        Path file = root.resolve(ManagedSavedDataType.STATIONS.fileName());
        CheckedSavedDataFile.replace(file, ManagedSavedDataType.STATIONS, () -> payload);
        CompoundTag read = BoundedSavedDataIo.read(file, ManagedSavedDataType.STATIONS);
        assertEquals(payload, read.getCompound("data"));
        assertEquals(StationLimits.MAX_STATIONS, CheckedSavedDataFile.readPayload(file, ManagedSavedDataType.STATIONS)
                .orElseThrow().getList("stations", 10).size());
    }

    @Test
    void rawBytesBeyondTheBoundAreStillRejected() throws Exception {
        CompoundTag payload = new CompoundTag();
        ListTag filler = new ListTag();
        // Repetitive filler just over the raw bound; it compresses well, which only has to pass the
        // compressed-size precheck so that the raw quota is what refuses it.
        long limit = ManagedSavedDataType.STATIONS.maxCompressedBytes();
        StringBuilder chunk = new StringBuilder();
        for (int index = 0; index < 30_000; index++) {
            chunk.append((char) ('a' + (index * 7919) % 26));
        }
        for (long written = 0; written <= limit; written += chunk.length()) {
            filler.add(net.minecraft.nbt.StringTag.valueOf(chunk.toString()));
        }
        payload.put("stations", filler);
        CompoundTag outer = new CompoundTag();
        outer.put("data", payload);
        Path file = root.resolve(ManagedSavedDataType.STATIONS.fileName());
        try (var output = Files.newOutputStream(file)) {
            NbtIo.writeCompressed(outer, output);
        }
        assertTrue(Files.size(file) < limit, "Fixture must pass the compressed-size precheck");
        SavedDataMigrationException refused = assertThrows(SavedDataMigrationException.class,
                () -> BoundedSavedDataIo.read(file, ManagedSavedDataType.STATIONS));
        assertEquals(MigrationDiagnosticId.OVERSIZED_DATA, refused.diagnosticId());
    }

    /**
     * Review F1: a tiny crafted file declaring one huge array or list is refused as oversized before
     * anything is allocated when the declaration exceeds what the file can back (deflate expands at
     * most about 1032:1) or the type's heap quota. A smaller declaration still fails closed and can
     * allocate at most that small amount. Checked for every managed type in the default test heap.
     */
    @Test
    void aTinyFileDeclaringAHugeElementIsRefusedBeforeAllocation() throws Exception {
        for (ManagedSavedDataType type : ManagedSavedDataType.values()) {
            long quota = Math.multiplyExact(type.maxCompressedBytes(), type.heapAccountingFactor());
            for (long declared : new long[]{quota - 1_024L, quota + 1L, 64L * 1_024L * 1_024L}) {
                for (byte tagType : new byte[]{7, 11, 12, 9}) {
                    Path file = root.resolve(type.fileName() + "-" + tagType + "-" + declared);
                    writeDeclaration(file, tagType, Math.min(declared, Integer.MAX_VALUE - 64L));
                    assertTrue(Files.size(file) < 128, "The fixture must be tiny");
                    long backed = ((Files.size(file) + BoundedSavedDataIo.COMPRESSED_SLACK_BYTES)
                            * BoundedSavedDataIo.MAX_INFLATION_RATIO + BoundedSavedDataIo.INFLATED_SLACK_BYTES)
                            * BoundedSavedDataIo.DECLARED_BYTES_PER_REMAINING_RAW_BYTE
                            + BoundedSavedDataIo.DECLARED_BYTES_SLACK;
                    String label = type + " tag " + tagType + " declaring " + declared;
                    SavedDataMigrationException refused = assertThrows(SavedDataMigrationException.class,
                            () -> BoundedSavedDataIo.read(file, type), label);
                    if (declared > backed || declared > quota) {
                        assertEquals(MigrationDiagnosticId.OVERSIZED_DATA, refused.diagnosticId(), label);
                    }
                }
            }
        }
        // The gzip-backed bound is what keeps the large types safe: it is far below their quotas.
        assertTrue(Math.multiplyExact(ManagedSavedDataType.ROCKET_TRANSFERS.maxCompressedBytes(),
                ManagedSavedDataType.ROCKET_TRANSFERS.heapAccountingFactor()) > 10L * 1_024L * 1_024L);
    }

    /** An outer compound whose "data" entry declares {@code declaredBytes} of accounted content. */
    private static void writeDeclaration(Path file, byte tagType, long declaredBytes) throws Exception {
        try (var output = new DataOutputStream(new java.util.zip.GZIPOutputStream(Files.newOutputStream(file)))) {
            output.writeByte(10);
            output.writeUTF("");
            output.writeByte(tagType);
            output.writeUTF("data");
            if (tagType == 9) {
                output.writeByte(1); // a list of bytes: 4 accounted bytes per element
            }
            long perElement = switch (tagType) {
                case 11, 9 -> 4L;
                case 12 -> 8L;
                default -> 1L;
            };
            output.writeInt((int) (declaredBytes / perElement));
        }
    }

    private static byte[] bytes(CompoundTag tag) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (DataOutputStream data = new DataOutputStream(output)) {
            NbtIo.write(tag, data);
        }
        return output.toByteArray();
    }

    private static long serialized(CompoundTag tag) throws Exception {
        return bytes(tag).length;
    }
}
