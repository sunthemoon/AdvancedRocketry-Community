package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.RegionFile;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EscrowItemReadbackTest {
    @TempDir java.nio.file.Path directory;
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    static EscrowReconciliationCheckpoint checkpoint(EscrowItemReconciliationFixture f) {
        var rocket = EscrowSettlementDisk.ordinary(EscrowReconciliationCheckpointTest.rocketSlot(f.op().rocket(), f.after()), f.op().rocket(), f.ledger().id());
        var device = EscrowSettlementDisk.ordinary(EscrowItemReconciliationFixture.slot(f.deviceAfter()), f.op().device(), f.ledger().id());
        return new EscrowReconciliationCheckpoint(f.ledger(), Set.of(f.op().id()), List.of(rocket.settlement(), device.settlement()), () -> true,
                Map.of(f.op().rocket().key(), rocket.state(), f.op().device().key(), device.state()));
    }
    @Test void readbackCannotConfirmDigestsOffTheOwnerThread() {
        try (var checkpoint = checkpoint(EscrowItemReconciliationFixture.create(true))) {
            CompletableFuture.runAsync(() -> assertThrows(IllegalArgumentException.class, () -> checkpoint.accept(List.of(),
                    new EscrowBarrier.JointResult(Map.of(), EscrowReason.BARRIER_UNSTABLE)))).join();
        }
    }

    @Test void boundedAnvilDecodeRecomputesBothDirectionsAndOnlyOwnerConfirms() throws Exception {
        for (boolean load : List.of(true, false)) {
            var f = EscrowItemReconciliationFixture.create(load);
            for (var endpoint : List.of(f.op().rocket(), f.op().device())) {
                boolean rocket = endpoint.kind() == EscrowEndpoint.Kind.ENTITY;
                var slot = rocket ? EscrowReconciliationCheckpointTest.rocketSlot(endpoint, f.after())
                        : EscrowItemReconciliationFixture.slot(f.deviceAfter());
                var expected = EscrowSettlementDisk.ordinary(slot, endpoint, f.ledger().id());
                var witness = rocket ? io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketSnapshotNbtCodec
                        .decode(f.after().getCompound("snapshot")).snapshot().orElseThrow() : null;
                var reader = new EscrowCargoReadback(f.ledger().id(), expected, witness);
                assertTrue(reader.test(slot)); assertFalse(reader.confirmed(), "A native capture is not disk proof");
                var pos = rocket ? new ChunkPos(endpoint.x(), endpoint.z()) : new ChunkPos(endpoint.x() >> 4, endpoint.z() >> 4);
                try (var region = new RegionFile(directory.resolve("r." + (pos.x >> 5) + "." + (pos.z >> 5) + ".mca"), directory, true);
                     var output = region.getChunkDataOutputStream(pos)) { NbtIo.write(slot, output); }
                CompletableFuture.runAsync(() -> {
                    try { assertTrue(reader.test(AnvilRecordReader.read(directory, pos).root())); }
                    catch (java.io.IOException error) { throw new AssertionError(error); }
                    assertThrows(IllegalArgumentException.class, reader::confirmed);
                }).join();
                slot.putString("mutated_after_decode", "not shared");
                assertTrue(reader.confirmed());
                var before = rocket ? EscrowReconciliationCheckpointTest.rocketSlot(endpoint, f.before())
                        : EscrowItemReconciliationFixture.slot(f.deviceBefore());
                assertFalse(reader.test(before), "Capture compares the exact frozen observation");
                assertTrue(CompletableFuture.supplyAsync(() -> reader.test(before)).join(), "A valid decode is not a digest verdict");
                assertFalse(reader.confirmed(), "A stale native BEFORE must not confirm AFTER");
            }
        }
    }

    @Test void wrongSnapshotWitnessAndMalformedNativeRecordsCannotConfirm() {
        var f = EscrowItemReconciliationFixture.create(true); var endpoint = f.op().rocket();
        var after = EscrowReconciliationCheckpointTest.rocketSlot(endpoint, f.after());
        var old = io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketSnapshotNbtCodec
                .decode(f.before().getCompound("snapshot")).snapshot().orElseThrow();
        var reader = new EscrowCargoReadback(f.ledger().id(), EscrowSettlementDisk.ordinary(after, endpoint, f.ledger().id()), old);
        assertFalse(reader.test(after), "A server capture cannot skip its immutable witness comparison");
        var malformed = after.copy(); malformed.getList("Entities", 10).getCompound(0).getCompound("RocketEntityData").remove("snapshot");
        assertFalse(CompletableFuture.supplyAsync(() -> reader.test(malformed)).join());
        assertFalse(reader.confirmed());
    }
}
