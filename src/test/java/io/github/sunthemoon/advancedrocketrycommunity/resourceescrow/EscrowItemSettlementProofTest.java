package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNativeResources;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketSnapshotNbtCodec;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.RegionFile;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** D-E2 item 3: O2 proves a cargo rocket survivor from one typed decode, and R39 holds before any native action. */
class EscrowItemSettlementProofTest {
    @TempDir java.nio.file.Path directory;
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    static EscrowSettlementIntent intent(EscrowLedger root, EscrowOperation op, List<EscrowSettlementIntent.Observation> observed) {
        boolean missing = observed.stream().anyMatch(EscrowSettlementIntent.Observation::missing);
        var actor = EscrowTestFixtures.id(700);
        var preview = EscrowSettlementIntent.preview(root, op.id(), missing ? EscrowSettlementIntent.Choice.ABANDON
                : EscrowSettlementIntent.Choice.ACCEPT_CURRENT, actor, "CargoO2", 10, observed);
        return preview.confirm(actor, preview.digest(), missing ? 210 : 11, observed);
    }
    static EscrowLedger held(EscrowItemReconciliationFixture f, EscrowOperation op) {
        return f.ledger().replace(List.of(op), f.ledger().floors(), f.ledger().tombstones());
    }
    static RocketStructureSnapshot snapshot(CompoundTag envelope) {
        return RocketSnapshotNbtCodec.decode(envelope.getCompound("snapshot")).snapshot().orElseThrow();
    }
    private CompoundTag stored(EscrowEndpoint endpoint, CompoundTag slot) throws Exception {
        var pos = new ChunkPos(endpoint.x(), endpoint.z());
        try (var region = new RegionFile(directory.resolve("r." + (pos.x >> 5) + "." + (pos.z >> 5) + ".mca"), directory, true);
             var output = region.getChunkDataOutputStream(pos)) { NbtIo.write(slot, output); }
        return CompletableFuture.supplyAsync(() -> {
            try { return AnvilRecordReader.read(directory, pos).root(); }
            catch (java.io.IOException failure) { throw new AssertionError(failure); }
        }).join();
    }

    @Test void cargoRocketSurvivorIsProvedFromOneOffThreadDecodeThatOnlyTheOwnerConfirms() throws Exception {
        for (boolean load : List.of(true, false)) {
            var f = EscrowItemReconciliationFixture.create(load); var op = f.op().withReason(EscrowReason.OTHER_STATE);
            var root = held(f, op); var after = snapshot(f.after());
            var rocket = EscrowItemSettlementObservationTest.rocket(f, f.after());
            var intent = intent(root, op, List.of(rocket, EscrowItemSettlementObservationTest.device(f, f.deviceAfter())));
            var slot = EscrowSettlementProofs.slot(intent, List.of(op), op.rocket(), endpoint -> after);
            assertEquals(1, slot.targets().size()); assertEquals(java.util.Set.of(op.rocket().key()), slot.readbacks().keySet());
            var target = slot.targets().get(0);
            assertEquals(rocket.state(), target.frontier(), "The proof frontier is the frozen fuel-vector choice");
            assertNotEquals(op.rocket().afterSignature(), target.frontier().signature(), "A composite is never a settlement frontier");
            var disk = EscrowReconciliationCheckpointTest.rocketSlot(op.rocket(), f.after());
            assertTrue(target.validator().test(disk), "The owner-thread capture compares against the immutable witness");
            assertFalse(slot.confirmed(), "A native capture is not a stable disk proof");
            var read = stored(op.rocket(), disk);
            assertTrue(CompletableFuture.supplyAsync(() -> target.validator().test(read)).join());
            assertThrows(java.util.concurrent.CompletionException.class,
                    () -> CompletableFuture.runAsync(slot::confirmed).join(), "Only the owner thread confirms");
            assertTrue(slot.confirmed());
            var before = stored(op.rocket(), EscrowReconciliationCheckpointTest.rocketSlot(op.rocket(), f.before()));
            assertTrue(CompletableFuture.supplyAsync(() -> target.validator().test(before)).join(), "A valid decode is not a verdict");
            assertFalse(slot.confirmed(), "A stale BEFORE record must not prove the frozen AFTER choice");
            assertEquals(EscrowReason.SUPERSEDING_SAVE, slot.reason(new EscrowBarrier.JointResult(java.util.Map.of(), EscrowReason.SUPERSEDING_SAVE)),
                    "Without an R39 refusal the barrier's own reason is kept");
            // The device has its own typed adapter and keeps the unchanged generic validator.
            var device = EscrowSettlementProofs.slot(intent, List.of(op), op.device(), endpoint -> { throw new AssertionError("No rocket"); });
            assertTrue(device.readbacks().isEmpty());
            assertTrue(CompletableFuture.supplyAsync(() -> device.targets().get(0).validator()
                    .test(EscrowItemReconciliationFixture.slot(f.deviceAfter()))).join());
            assertFalse(device.targets().get(0).validator().test(EscrowItemReconciliationFixture.slot(f.deviceBefore())));
            var typed = f.deviceAfter().copy();
            var slots = typed.getCompound("arce_rocket_item_cargo").getCompound("buffer").getList("slots", 10);
            slots.getCompound(0).getCompound("tag").putInt("b", 7);
            assertFalse(device.targets().get(0).validator().test(EscrowItemReconciliationFixture.slot(typed)),
                    "The same numeric value with a different native NBT type cannot prove the buffer");
        }
    }

    @Test void oneDecodeSuppliesBothProjectionsAndAcceptCurrentKeepsAnOtherChoice() throws Exception {
        for (boolean load : List.of(true, false)) {
            var f = EscrowItemReconciliationFixture.create(load); var op = f.op().withReason(EscrowReason.OTHER_STATE);
            var root = held(f, op); var chain = EscrowPrefix.chains(root.records()).get(op.rocket().key());
            // AFTER cargo content under an epoch-0 bound wrapper: neither the operation's BEFORE nor its AFTER.
            var other = f.after().copy(); other.put("wrapper", new RocketEscrowSection(root.id(), 0, List.of()).encode());
            assertEquals(EscrowPrefix.Classification.OTHER, EscrowReconciliationValidation.rocket(root.id(), chain, other));
            var disk = EscrowReconciliationCheckpointTest.rocketSlot(op.rocket(), other);
            var decoded = EscrowSettlementDisk.ordinary(disk, op.rocket(), root.id());
            assertEquals(EscrowSignatureKind.FUEL_VECTOR_V1, decoded.settlement().signatureKind());
            assertEquals(EscrowSignatureKind.composite(op.rocket().logicalRocket(), snapshot(other).contentHash(),
                    decoded.settlement().state().signature()), decoded.state().signature());
            assertEquals(op.rocket().afterSignature(), decoded.state().signature(), "The same decode recomputes the after composite");
            assertEquals(EscrowPrefix.Classification.OTHER, EscrowPrefix.classify(root.id(), chain, decoded.state()));
            var intent = intent(root, op, List.of(decoded.settlement(), EscrowItemSettlementObservationTest.device(f, f.deviceBefore())));
            var slot = EscrowSettlementProofs.slot(intent, List.of(op), op.rocket(), endpoint -> snapshot(other));
            var read = stored(op.rocket(), disk);
            assertTrue(CompletableFuture.supplyAsync(() -> slot.targets().get(0).validator().test(read)).join());
            assertTrue(slot.confirmed(), "ACCEPT_CURRENT proves the frozen OTHER choice, not the operation's AFTER state");
            var wrongContent = stored(op.rocket(), EscrowReconciliationCheckpointTest.rocketSlot(op.rocket(), f.after()));
            assertTrue(CompletableFuture.supplyAsync(() -> slot.targets().get(0).validator().test(wrongContent)).join());
            assertFalse(slot.confirmed(), "The operation's AFTER state does not prove an OTHER choice");
        }
    }

    @Test void r39RejectsUnavailableNativeItemsBeforeAnySettlementAction() throws Exception {
        for (boolean load : List.of(true, false)) {
            var f = EscrowItemReconciliationFixture.create(load); var op = f.op().withReason(EscrowReason.OTHER_STATE);
            var encoded = EscrowCodec.operation(op);
            var payload = EscrowItemCargoPayload.decode(op.payload()); var stack = payload.stack();
            stack.putString("id", "w134:missing");
            encoded.put("payload", new EscrowItemCargoPayload(payload.cursor(), stack, payload.from(), payload.to()).encode());
            encoded.putString("payload_hash", RocketNativeResources.signature(encoded.getCompound("payload")));
            var missing = EscrowCodec.operation(encoded);
            assertNull(EscrowSettlementProofs.unavailableItem(List.of(op)));
            assertSame(missing, EscrowSettlementProofs.unavailableItem(List.of(op, missing)));
            assertNull(EscrowSettlementProofs.unavailableItem(List.of(EscrowTestFixtures.operation(7))));
            var root = held(f, missing);
            var intent = intent(root, missing, List.of(EscrowItemSettlementObservationTest.rocket(f, f.after()),
                    EscrowItemSettlementObservationTest.device(f, f.deviceAfter())));
            var slot = EscrowSettlementProofs.slot(intent, List.of(missing), missing.rocket(), endpoint -> snapshot(f.after()));
            var read = stored(missing.rocket(), EscrowReconciliationCheckpointTest.rocketSlot(missing.rocket(), f.after()));
            assertFalse(CompletableFuture.supplyAsync(() -> slot.targets().get(0).validator().test(read)).join());
            assertFalse(slot.confirmed());
            assertEquals(EscrowReason.OTHER_STATE, slot.reason(new EscrowBarrier.JointResult(java.util.Map.of(), EscrowReason.BARRIER_UNSTABLE)));
        }
    }

    @Test void abandonProvesTheExactAbsenceAndSurvivorWithTheGenericValidators() {
        var f = EscrowItemReconciliationFixture.create(false); var op = f.op().withReason(EscrowReason.OTHER_STATE); var root = held(f, op);
        var absent = new EscrowSettlementIntent.Observation(op.rocket(), null, false, 0);
        var intent = intent(root, op, List.of(absent, EscrowItemSettlementObservationTest.device(f, f.deviceBefore())));
        var slot = EscrowSettlementProofs.slot(intent, List.of(op), op.rocket(), endpoint -> { throw new AssertionError("Absence needs no witness"); });
        assertTrue(slot.readbacks().isEmpty());
        var target = slot.targets().get(0);
        assertEquals(new EscrowPrefix.State(root.id(), 0, EscrowSettlementTombstones.observationDigest(absent), List.of()), target.frontier());
        var empty = new CompoundTag(); empty.putIntArray("Position", new int[] {op.rocket().x(), op.rocket().z()}); empty.put("Entities", new ListTag());
        assertTrue(target.validator().test(empty), "An empty entity slot proves the frozen absence");
        assertFalse(target.validator().test(EscrowReconciliationCheckpointTest.rocketSlot(op.rocket(), f.before())),
                "A present rocket does not prove its absence");
        var device = EscrowSettlementProofs.slot(intent, List.of(op), op.device(), endpoint -> { throw new AssertionError("No rocket"); });
        assertTrue(device.readbacks().isEmpty() && device.targets().get(0).validator().test(EscrowItemReconciliationFixture.slot(f.deviceBefore())));
    }

    @Test void abandonAlsoProvesAnAbsentDeviceAndTheExactRocketSurvivor() {
        var f = EscrowItemReconciliationFixture.create(false); var op = f.op().withReason(EscrowReason.OTHER_STATE);
        var absent = new EscrowSettlementIntent.Observation(op.device(), null, false, 0);
        var intent = intent(held(f, op), op, List.of(absent, EscrowItemSettlementObservationTest.rocket(f, f.after())));
        var device = EscrowSettlementProofs.slot(intent, List.of(op), op.device(), endpoint -> { throw new AssertionError("Device"); });
        assertTrue(device.targets().get(0).validator().test(EscrowItemReconciliationFixture.slot()));
        assertFalse(device.targets().get(0).validator().test(EscrowItemReconciliationFixture.slot(f.deviceAfter())));
        var rocket = EscrowSettlementProofs.slot(intent, List.of(op), op.rocket(), endpoint -> snapshot(f.after()));
        var saved = EscrowReconciliationCheckpointTest.rocketSlot(op.rocket(), f.after());
        assertTrue(CompletableFuture.supplyAsync(() -> rocket.targets().get(0).validator().test(saved)).join());
        assertTrue(rocket.confirmed());
        var old = EscrowReconciliationCheckpointTest.rocketSlot(op.rocket(), f.before());
        assertTrue(CompletableFuture.supplyAsync(() -> rocket.targets().get(0).validator().test(old)).join());
        assertFalse(rocket.confirmed());
    }
}
