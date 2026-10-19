package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowCargoFixtures.*;
import static io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowTestFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketFlightNbtCodec;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketSnapshotNbtCodec;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketStats;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EscrowCargoCaptureTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    record Capture(EscrowOperation op, CompoundTag before, CompoundTag after, CompoundTag deviceBefore, CompoundTag deviceAfter) { }

    @Test void aSingleCaptureBindsAfterInvariantCompleteEnvelopesAndDirectionSpecificMarkers() {
        for (var kind : KINDS) {
            var c = sample(kind); var admission = capture(c, c.before(), c.after()); var binding = admission.operation().nativeBinding();
            assertEquals(EscrowNativeEnvelope.digest(c.before().getCompound("wrapper")), binding.beforeWrapper());
            assertEquals(EscrowNativeEnvelope.digest(c.after().getCompound("wrapper")), binding.afterWrapper());
            boolean load = kind == EscrowOperation.Kind.FLUID_CARGO_LOAD;
            assertEquals(EscrowNativeEnvelope.digest(c.before()), load ? binding.destinationBefore() : binding.sourceBefore());
            assertEquals(EscrowNativeEnvelope.digest(c.after()), load ? binding.destinationAfter() : binding.sourceAfter());
            assertEquals(EscrowNativeEnvelope.digest(c.deviceBefore()), load ? binding.sourceBefore() : binding.destinationBefore());
            assertEquals(EscrowNativeEnvelope.digest(c.deviceAfter()), load ? binding.sourceAfter() : binding.destinationAfter());
            var before = RocketSnapshotNbtCodec.decode(c.before().getCompound("snapshot")).snapshot().orElseThrow();
            var after = RocketSnapshotNbtCodec.decode(c.after().getCompound("snapshot")).snapshot().orElseThrow();
            assertEquals(after.contentHash(), binding.snapshotHash()); assertNotEquals(before.contentHash(), binding.snapshotHash());
            assertEquals(EscrowInvariant.rocket(c.op().rocket(), c.op().owner(), after), binding.invariant());
            assertNotEquals(EscrowInvariant.rocket(c.op().rocket(), c.op().owner(), before), binding.invariant());
            assertEquals(c.before().getCompound("flight"), c.after().getCompound("flight"));
            assertEquals(admission.operation(), EscrowCodec.operation(EscrowCodec.operation(admission.operation())));
            assertDoesNotThrow(() -> EscrowSettlementCapacity.preflight(ledger(admission.operation()), c.op().id()));
            var substituted = new RocketEscrowSection(id(500), 1, List.of());
            assertThrows(IllegalArgumentException.class, () -> new EscrowNativeBinding.Admission(admission.operation(), substituted));
        }
    }

    @Test void staleCursorsChangedPropulsionFrameIdentityAndMarkersNeverProduceACapture() {
        for (var kind : KINDS) {
            var c = sample(kind);
            for (String field : List.of("physical", "logical", "owner", "assembly_snapshot")) {
                var wrong = c.after().copy(); wrong.putUUID(field, id(9_999));
                assertThrows(IllegalArgumentException.class, () -> capture(c, c.before(), wrong));
            }
            var frame = c.after().copy(); frame.putFloat("yaw", 91);
            assertThrows(IllegalArgumentException.class, () -> capture(c, c.before(), frame));
            var marker = c.after().copy(); marker.put("wrapper", new RocketEscrowSection(id(500), 1,
                    List.of(new EscrowPrefix.Marker(c.op().id(), 1, kind == EscrowOperation.Kind.FLUID_CARGO_LOAD ? "SOURCE" : "DESTINATION", 1))).encode());
            assertThrows(IllegalArgumentException.class, () -> capture(c, c.before(), marker));
            var changedFuel = c.after().copy(); var flight = RocketFlightNbtCodec.decode(changedFuel.getCompound("flight")).data().orElseThrow();
            changedFuel.put("flight", RocketFlightNbtCodec.encode(EscrowNativeEnvelope.fuelOnly(flight,
                    flight.fuel().withFluidBank(false, ResourceLocation.tryParse("minecraft:water"), null, 100))));
            assertThrows(IllegalArgumentException.class, () -> capture(c, c.before(), changedFuel));
            var snapshot = RocketSnapshotNbtCodec.decode(c.before().getCompound("snapshot")).snapshot().orElseThrow();
            var cursor = EscrowCargoPayload.decode(c.op().payload()).cursor();
            assertThrows(IllegalArgumentException.class, () -> new EscrowCargoPayload.Cursor(id(1), cursor.x(), cursor.y(), cursor.z(), cursor.adapter()).requirePayload(snapshot));
            assertThrows(IllegalArgumentException.class, () -> new EscrowCargoPayload.Cursor(cursor.snapshot(), 999, 999, 999, cursor.adapter()).requirePayload(snapshot));
            assertThrows(IllegalArgumentException.class, () -> new EscrowCargoPayload.Cursor(cursor.snapshot(), cursor.x(), cursor.y(), cursor.z(), "missing:adapter").requirePayload(snapshot));
        }
    }
    private static EscrowNativeBinding.Admission capture(Capture c, CompoundTag before, CompoundTag after) {
        return EscrowCargoCapture.capture(id(500), c.op(), before, after, c.deviceBefore(), c.deviceAfter());
    }
    private static Capture sample(EscrowOperation.Kind kind) {
        var envelope = EscrowReconciliationBindingBoundaryTest.sample().before();
        var original = RocketSnapshotNbtCodec.decode(envelope.getCompound("snapshot")).snapshot().orElseThrow();
        var before = snapshot(original, 10); var after = snapshot(original, 11);
        var beforeEnvelope = envelope.copy(); beforeEnvelope.put("snapshot", RocketSnapshotNbtCodec.encode(before));
        beforeEnvelope.putString("content_hash", before.contentHash());
        var fuel = RocketFlightNbtCodec.decode(envelope.getCompound("flight")).data().orElseThrow().fuel();
        var chunk = envelope.getIntArray("chunk"); var logical = envelope.getUUID("logical");
        var rocket = new EscrowEndpoint(EscrowEndpoint.Kind.ENTITY, envelope.getString("level"), envelope.getUUID("physical"), logical,
                EscrowAdapterIds.ROCKET, chunk[0], 0, chunk[1], EscrowEndpoint.Unit.TANK, 0,
                EscrowSignatureKind.composite(logical, before.contentHash(), fuel.resourceSignature()),
                EscrowSignatureKind.composite(logical, after.contentHash(), fuel.resourceSignature()));
        var model = operation(kind, 1, 99); var old = model.device();
        var device = new EscrowEndpoint(old.kind(), rocket.level(), old.identity(), null, old.type(), 0, 64, 0, old.unit(), 0, old.beforeSignature(), old.afterSignature());
        var pos = before.blocks().get(0).position();
        var payload = new EscrowCargoPayload(new EscrowCargoPayload.Cursor(before.snapshotId(), pos.x(), pos.y(), pos.z(), ADAPTER), fluid(-1)).encode();
        boolean load = kind == EscrowOperation.Kind.FLUID_CARGO_LOAD;
        var op = new EscrowOperation(id(1), 1, envelope.getUUID("owner"), 0, EscrowOperation.Phase.PREPARED, EscrowReason.NONE,
                payload, RocketNativeResources.signature(payload), load ? device : rocket, load ? rocket : device,
                new EscrowGuard(logical, null, 1, hash(400)), kind, new CompoundTag());
        var afterEnvelope = beforeEnvelope.copy(); afterEnvelope.put("snapshot", RocketSnapshotNbtCodec.encode(after));
        afterEnvelope.putString("content_hash", after.contentHash());
        afterEnvelope.put("wrapper", new RocketEscrowSection(id(500), 1,
                List.of(new EscrowPrefix.Link(op, rocket, load ? "DESTINATION" : "SOURCE").marker())).encode());
        var deviceBefore = new CompoundTag(); deviceBefore.putInt("Amount", 2_000);
        var deviceAfter = new CompoundTag(); deviceAfter.putInt("Amount", 1_000);
        return new Capture(op, beforeEnvelope, afterEnvelope, deviceBefore, deviceAfter);
    }
    private static RocketStructureSnapshot snapshot(RocketStructureSnapshot original, int amount) {
        var blocks = new ArrayList<>(original.blocks()); var block = blocks.get(0); var tag = new CompoundTag(); tag.putInt("amount", amount);
        blocks.set(0, new RocketBlock(block.position(), block.state(), new RocketBlockEntityPayload(ResourceLocation.tryParse(ADAPTER), tag)));
        var old = original.stats();
        var stats = new RocketStats(old.blockCount(), old.mass(), old.thrust(), old.fuelCapacity(), old.engineCount(), old.seatCount(), old.guidanceCount(),
                (int) blocks.stream().filter(value -> value.blockEntityPayload().isPresent()).count());
        return RocketStructureSnapshot.create(original.snapshotId(), original.sourceDimension(), original.sourceOrigin(), blocks,
                original.passengerAnchors(), stats, original.createdAtGameTime(), original.propulsion().orElseThrow());
    }
}
