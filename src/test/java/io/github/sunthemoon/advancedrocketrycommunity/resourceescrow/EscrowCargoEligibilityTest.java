package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;
import static io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowTestFixtures.*;
import static io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.RocketCargoSelection.Status.*;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.BoundedNbt;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFuelState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketFlightNbtCodec;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.LoaderTank;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNativeResources;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketSnapshotNbtCodec;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EscrowCargoEligibilityTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    record Candidate(EscrowOperation op, CompoundTag before, CompoundTag after, CompoundTag deviceBefore, CompoundTag deviceAfter) { }

    @Test void fullCapturedMutationAndO1FitInBothDirectionsWithoutChangingInputs() {
        for (boolean load : new boolean[] {true, false}) {
            for (int amount : new int[] {1, 749, 1000}) {
                var c = sample(load, amount, -1);
                var before = c.before().copy(); var device = c.deviceBefore().copy(); var root = ledger(); var saved = EscrowCodec.encode(root);
                var result = check(c, 64_000);
                assertEquals(ELIGIBLE, result.status()); assertNotNull(result.capture());
                assertEquals(before, c.before()); assertEquals(device, c.deviceBefore()); assertEquals(saved, EscrowCodec.encode(root));
                assertEquals(result.capture().operation(), EscrowCodec.operation(EscrowCodec.operation(result.capture().operation())));
            }
        }
    }

    @Test void changedFinalCapacitySnapshotOrDeviceNeverReturnsAnAdmission() {
        var c = sample(true, 1000, -1);
        assertEquals(INVALID_DATA, check(c, 500).status());
        var bad = c.after().copy(); bad.getCompound("snapshot").putLong("created_at_game_time", 99);
        assertEquals(INVALID_DATA, check(new Candidate(c.op(), c.before(), bad, c.deviceBefore(), c.deviceAfter()), 64_000).status());
        var device = c.deviceAfter().copy(); device.getCompound(EscrowCargoEligibility.DEVICE_ROOT).getCompound("tank").putInt("Amount", 7);
        assertEquals(INVALID_DATA, check(new Candidate(c.op(), c.before(), c.after(), c.deviceBefore(), device), 64_000).status());
        var unknown = c.deviceBefore().copy(); unknown.getCompound(EscrowCargoEligibility.DEVICE_ROOT).getCompound("tank").putString("FluidName", "missing:fluid");
        assertEquals(INVALID_DATA, check(new Candidate(c.op(), c.before(), c.after(), unknown, c.deviceAfter()), 64_000).status());
    }

    @Test void nativeEnvelopeDepthAndNodesRefuseEvenWhenInnerSnapshotPasses() {
        var c = sample(true, 1, -1);
        for (boolean depth : new boolean[] {true, false}) {
            var b = c.before().copy(); var a = c.after().copy();
            var extra = new CompoundTag();
            if (depth) {
                var cursor = extra;
                for (int i = 0; i < 23; i++) { var child = new CompoundTag(); cursor.put("c", child); cursor = child; }
            } else {
                var list = new net.minecraft.nbt.ListTag();
                for (int i = 0; i < 131_000; i++) { list.add(net.minecraft.nbt.ByteTag.valueOf((byte) 1)); }
                extra.put("n", list);
            }
            b.put("extra", extra); a.put("extra", extra.copy());
            assertTrue(BoundedNbt.fits(b.getCompound("snapshot"), 1_048_576, 32, 65_536));
            assertEquals(CARGO_TOO_LARGE, check(new Candidate(c.op(), b, a, c.deviceBefore(), c.deviceAfter()), 64_000).status());
        }
    }

    @Test void fullDeviceRootAndNativeEnvelopeByteOverheadAreCheckedBeforeCopies() {
        var c = sample(true, 1, -1); var b = c.deviceBefore().copy(); var a = c.deviceAfter().copy();
        b.getCompound(EscrowCargoEligibility.DEVICE_ROOT).putByteArray("extra", new byte[32_700]);
        a.getCompound(EscrowCargoEligibility.DEVICE_ROOT).putByteArray("extra", new byte[32_700]);
        assertTrue(LoaderTank.bounded(b.getCompound(EscrowCargoEligibility.DEVICE_ROOT).getCompound("tank")));
        assertEquals(CARGO_TOO_LARGE, check(new Candidate(c.op(), c.before(), c.after(), b, a), 64_000).status());
        var rb = c.before().copy(); var ra = c.after().copy();
        rb.putByteArray("extra", new byte[EscrowNativeEnvelope.MAX_BYTES]);
        ra.putByteArray("extra", new byte[EscrowNativeEnvelope.MAX_BYTES]);
        assertEquals(CARGO_TOO_LARGE, check(new Candidate(c.op(), rb, ra, c.deviceBefore(), c.deviceAfter()), 64_000).status());
    }

    @Test void aLocallyValidFluidAndCursorCanFailCombinedO1Reservation() {
        for (boolean load : new boolean[] {true, false}) {
            boolean refusal = false;
            for (int blob = 6500; blob <= 7900; blob += 100) {
                var c = sample(load, 1000, blob);
                assertTrue(BoundedNbt.fits(c.op().payload(), 8192, 12, 384));
                var result = check(c, 64_000);
                if (result.status() == CARGO_TOO_LARGE) {
                    System.out.println("W106 O1 refusal: load=" + load + " blob=" + blob);
                    assertNull(result.capture()); refusal = true; break;
                }
                assertEquals(ELIGIBLE, result.status());
            }
            assertTrue(refusal, "Must exercise inner-passes/complete-O1-fails, not only a local limit");
        }
    }

    @Test void completeDeviceRootAddsDepthAndNodesToLocallyValidChildren() {
        var c = sample(true, 1, -1);
        for (boolean depth : new boolean[] {true, false}) {
            var child = new CompoundTag();
            if (depth) {
                var cursor = child;
                for (int i = 0; i < 19; i++) { var next = new CompoundTag(); cursor.put("c", next); cursor = next; }
            } else { for (int i = 0; i < 2040; i++) { child.putByte("n" + i, (byte) 0); } }
            assertTrue(BoundedNbt.fits(child, 32_768, 20, 2_048));
            var b = c.deviceBefore().copy(); var a = c.deviceAfter().copy();
            b.getCompound(EscrowCargoEligibility.DEVICE_ROOT).put("extra", child);
            a.getCompound(EscrowCargoEligibility.DEVICE_ROOT).put("extra", child.copy());
            assertEquals(CARGO_TOO_LARGE, check(new Candidate(c.op(), c.before(), c.after(), b, a), 64_000).status());
        }
    }

    private static EscrowCargoEligibility.Result check(Candidate c, int capacity) {
        return EscrowCargoEligibility.check(ledger(), List.of(), c.op(), c.before(), c.after(), c.deviceBefore(), c.deviceAfter(), capacity);
    }

    static Candidate sample(boolean load, int amount, int blob) {
        var contents = new FluidStack(Fluids.WATER, 2000);
        if (blob >= 0) { contents.getOrCreateTag().putByteArray("blob", new byte[blob]); }
        var before = CargoTestFixtures.snapshot(load ? FluidStack.EMPTY : contents);
        var moved = contents.copy(); moved.setAmount(amount);
        var delta = CargoTestFixtures.delta(before, 0, moved);
        var after = RocketCargoMutation.apply(before, delta, load);
        var nativeBefore = EscrowReconciliationBindingBoundaryTest.sample().before();
        var flight = RocketFlightNbtCodec.decode(nativeBefore.getCompound("flight")).data().orElseThrow();
        var fuel = RocketFuelState.empty(before.propulsion().orElseThrow());
        nativeBefore.put("flight", RocketFlightNbtCodec.encode(EscrowNativeEnvelope.fuelOnly(flight, fuel)));
        nativeBefore.putUUID("assembly_snapshot", before.snapshotId()); nativeBefore.put("snapshot", RocketSnapshotNbtCodec.encode(before));
        nativeBefore.putString("content_hash", before.contentHash()); nativeBefore.put("wrapper", RocketEscrowSection.neutral().encode());
        var chunk = nativeBefore.getIntArray("chunk"); var logical = nativeBefore.getUUID("logical");
        var rocket = new EscrowEndpoint(EscrowEndpoint.Kind.ENTITY, nativeBefore.getString("level"), nativeBefore.getUUID("physical"), logical,
                EscrowAdapterIds.ROCKET, chunk[0], 0, chunk[1], EscrowEndpoint.Unit.TANK, 0,
                EscrowSignatureKind.composite(logical, before.contentHash(), fuel.resourceSignature()),
                EscrowSignatureKind.composite(logical, after.contentHash(), fuel.resourceSignature()));
        var oldDevice = load ? contents : FluidStack.EMPTY;
        var newDevice = RocketCargoMutation.change(oldDevice, moved, !load);
        var deviceId = id(106_001); var type = "advancedrocketrycommunity:rocket_fluid_" + (load ? "loader" : "unloader");
        var device = new EscrowEndpoint(EscrowEndpoint.Kind.BLOCK_ENTITY, rocket.level(), deviceId, null, type,
                0, 64, 0, EscrowEndpoint.Unit.TANK, 0, LoaderTank.of(oldDevice).signature(deviceId), LoaderTank.of(newDevice).signature(deviceId));
        var payload = delta.encode();
        var op = new EscrowOperation(id(1), 1, nativeBefore.getUUID("owner"), 0, EscrowOperation.Phase.PREPARED, EscrowReason.NONE,
                payload, RocketNativeResources.signature(payload), load ? device : rocket, load ? rocket : device,
                new EscrowGuard(logical, null, 1, hash(400)), load ? EscrowOperation.Kind.FLUID_CARGO_LOAD : EscrowOperation.Kind.FLUID_CARGO_UNLOAD,
                new CompoundTag());
        var nativeAfter = nativeBefore.copy(); nativeAfter.put("snapshot", RocketSnapshotNbtCodec.encode(after));
        nativeAfter.putString("content_hash", after.contentHash());
        nativeAfter.put("wrapper", new RocketEscrowSection(ledger().id(), 1, List.of(new EscrowPrefix.Link(op, rocket, load ? "DESTINATION" : "SOURCE").marker())).encode());
        var deviceBefore = new CompoundTag(); deviceBefore.putString("id", type); deviceBefore.putInt("x", 0); deviceBefore.putInt("y", 64); deviceBefore.putInt("z", 0);
        var root = new CompoundTag(); root.putInt("schema_version", 1); root.putUUID("owner", op.owner()); root.putUUID("device", deviceId);
        root.putString("role", load ? "LOAD" : "UNLOAD"); root.put("tank", LoaderTank.of(oldDevice).encode()); root.put("resource_escrow", RocketEscrowSection.neutral().encode());
        deviceBefore.put(EscrowCargoEligibility.DEVICE_ROOT, root);
        var deviceAfter = deviceBefore.copy(); var afterRoot = deviceAfter.getCompound(EscrowCargoEligibility.DEVICE_ROOT);
        afterRoot.put("tank", LoaderTank.of(newDevice).encode());
        afterRoot.put("resource_escrow", new RocketEscrowSection(ledger().id(), 1,
                List.of(new EscrowPrefix.Link(op, device, load ? "SOURCE" : "DESTINATION").marker())).encode());
        return new Candidate(op, nativeBefore, nativeAfter, deviceBefore, deviceAfter);
    }
}
