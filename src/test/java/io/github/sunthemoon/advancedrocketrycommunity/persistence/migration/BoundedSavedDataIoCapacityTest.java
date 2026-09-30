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
        // Incompressible-enough filler just over the raw bound for the smallest managed type.
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
        assertThrows(SavedDataMigrationException.class, () -> BoundedSavedDataIo.read(file, ManagedSavedDataType.STATIONS));
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
