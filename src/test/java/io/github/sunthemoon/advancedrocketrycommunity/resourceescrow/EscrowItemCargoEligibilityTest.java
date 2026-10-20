package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowTestFixtures.*;
import static io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.RocketCargoSelection.Status.*;
import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.BoundedNbt;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.ItemCargoTestFixtures;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.RocketItemCargoMutation;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFuelState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketFlightNbtCodec;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.VanillaContainerRocketAdapter;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketSnapshotNbtCodec;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.List;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EscrowItemCargoEligibilityTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    record Candidate(EscrowOperation op, CompoundTag before, CompoundTag after, CompoundTag deviceBefore, CompoundTag deviceAfter) { }

    @Test void fullCapturedMutationAndO1FitInBothDirectionsWithoutChangingInputs() {
        for (boolean load : new boolean[] {true, false}) {
            for (int count : new int[] {1, 37, 64}) {
                var c = sample(load, count, stone(-1));
                var before = c.before().copy(); var device = c.deviceBefore().copy(); var root = ledger(); var saved = EscrowCodec.encode(root);
                var result = check(c);
                assertEquals(ELIGIBLE, result.status()); assertNotNull(result.capture());
                assertEquals(before, c.before()); assertEquals(device, c.deviceBefore()); assertEquals(saved, EscrowCodec.encode(root));
                assertEquals(result.capture().operation(), EscrowCodec.operation(EscrowCodec.operation(result.capture().operation())));
                var shared = EscrowCargoEligibility.check(ledger(), List.of(), c.op(), c.before(), c.after(), c.deviceBefore(), c.deviceAfter(), 0);
                assertEquals(result.status(), shared.status()); assertEquals(result.capture(), shared.capture());
            }
        }
    }

    @Test void changedSnapshotDeviceRoleOrMarkerNeverReturnsAnAdmission() {
        for (boolean load : new boolean[] {true, false}) {
            var c = sample(load, 5, stone(-1));
            var bad = c.after().copy(); bad.getCompound("snapshot").putLong("created_at_game_time", 99);
            assertEquals(INVALID_DATA, check(new Candidate(c.op(), c.before(), bad, c.deviceBefore(), c.deviceAfter())).status());
            var count = c.deviceAfter().copy();
            var slots = count.getCompound(EscrowItemCargoEligibility.DEVICE_ROOT).getCompound("buffer").getList("slots", 10);
            slots.getCompound(0).putByte("Count", (byte) 7);
            assertEquals(INVALID_DATA, check(new Candidate(c.op(), c.before(), c.after(), c.deviceBefore(), count)).status());
            var role = c.deviceBefore().copy(); role.getCompound(EscrowItemCargoEligibility.DEVICE_ROOT).putString("role", load ? "UNLOAD" : "LOAD");
            assertEquals(INVALID_DATA, check(new Candidate(c.op(), c.before(), c.after(), role, c.deviceAfter())).status());
            var marker = c.deviceAfter().copy(); marker.getCompound(EscrowItemCargoEligibility.DEVICE_ROOT).put("resource_escrow",
                    new RocketEscrowSection(ledger().id(), 1, List.of(new EscrowPrefix.Marker(id(77), 1, "SOURCE", 1))).encode());
            assertEquals(INVALID_DATA, check(new Candidate(c.op(), c.before(), c.after(), c.deviceBefore(), marker)).status());
            var frame = c.deviceAfter().copy(); frame.getCompound(EscrowItemCargoEligibility.DEVICE_ROOT).putString("extra", "x");
            assertEquals(INVALID_DATA, check(new Candidate(c.op(), c.before(), c.after(), c.deviceBefore(), frame)).status());
        }
    }

    @Test void lootAndNonCargoStorageKeepTheirStatusAtAdmission() {
        var c = sample(true, 5, stone(-1));
        var lootData = new CompoundTag(); lootData.putString("LootTable", "minecraft:chests/simple_dungeon");
        var loot = ItemCargoTestFixtures.snapshot(ItemCargoTestFixtures.block(0, ItemCargoTestFixtures.state(ItemCargoTestFixtures.CHEST),
                new RocketBlockEntityPayload(VanillaContainerRocketAdapter.ID, lootData)));
        assertEquals(UNRESOLVED_LOOT, check(withBefore(c, loot)).status());
        var other = ItemCargoTestFixtures.snapshot(ItemCargoTestFixtures.block(0, new RocketBlockState(
                ResourceLocation.tryParse("fixture:copper_chest"), Map.of()), ItemCargoTestFixtures.container(null, null)));
        assertEquals(NO_CARGO_STORAGE, check(withBefore(c, other)).status());
    }

    /** R44: each half of a vanilla double chest is a 27-slot chest payload and a complete eligible sample. */
    @Test void aDoubleChestHalfIsACompleteEligibleSampleInBothDirections() {
        for (boolean load : new boolean[] {true, false}) {
            for (var side : List.of("left", "right")) {
                var c = sample(load, 5, stone(-1), new RocketBlockState(ItemCargoTestFixtures.CHEST,
                        Map.of("facing", "north", "type", side, "waterlogged", "false")));
                var result = check(c);
                assertEquals(ELIGIBLE, result.status(), side + " load=" + load); assertNotNull(result.capture());
            }
        }
    }

    @Test void theNativeStackCheckRunsAtAdmissionWithoutSideEffects() {
        for (boolean load : new boolean[] {true, false}) {
            var c = sample(load, 5, stone(-1));
            var missing = ItemCargoTestFixtures.stack(Items.STONE, 5); missing.putString("id", "missing:item");
            var pearls = ItemCargoTestFixtures.stack(Items.ENDER_PEARL, 16); pearls.putByte("Count", (byte) 17);
            for (var stack : List.of(missing, pearls)) {
                var payload = new EscrowItemCargoPayload(EscrowItemCargoPayload.decode(c.op().payload()).cursor(), stack, 0, 0).encode();
                var op = c.op();
                var changed = new EscrowOperation(op.id(), op.sequence(), op.owner(), op.createdTick(), op.phase(), op.reason(), payload,
                        io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNativeResources.signature(payload),
                        op.source(), op.destination(), op.guard(), op.kind(), op.settlement());
                var root = ledger(); var saved = EscrowCodec.encode(root);
                var result = EscrowItemCargoEligibility.check(root, List.of(), changed, c.before(), c.after(), c.deviceBefore(), c.deviceAfter());
                assertEquals(INVALID_DATA, result.status()); assertNull(result.capture()); assertEquals(saved, EscrowCodec.encode(root));
            }
        }
    }

    @Test void nativeEnvelopeDepthAndNodesRefuseEvenWhenInnerSnapshotPasses() {
        var c = sample(false, 1, stone(-1));
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
            assertEquals(CARGO_TOO_LARGE, check(new Candidate(c.op(), b, a, c.deviceBefore(), c.deviceAfter())).status());
        }
    }

    @Test void fullDeviceRootAndNativeEnvelopeByteOverheadAreCheckedBeforeCopies() {
        var c = sample(true, 1, stone(4_000)); var b = c.deviceBefore().copy(); var a = c.deviceAfter().copy();
        assertTrue(BoundedNbt.fits(b.getCompound(EscrowItemCargoEligibility.DEVICE_ROOT), 32_768, 20, 2_048));
        b.getCompound(EscrowItemCargoEligibility.DEVICE_ROOT).putByteArray("extra", new byte[28_800]);
        a.getCompound(EscrowItemCargoEligibility.DEVICE_ROOT).putByteArray("extra", new byte[28_800]);
        assertTrue(BoundedNbt.fits(b.getCompound(EscrowItemCargoEligibility.DEVICE_ROOT).getCompound("buffer"),
                4 * 4_096 + 16, 18, 4 * 512 + 2), "The buffer alone fits its own bound");
        assertFalse(BoundedNbt.fits(b.getCompound(EscrowItemCargoEligibility.DEVICE_ROOT), 32_768, 20, 2_048));
        assertEquals(CARGO_TOO_LARGE, check(new Candidate(c.op(), c.before(), c.after(), b, a)).status());
        var rb = c.before().copy(); var ra = c.after().copy();
        rb.putByteArray("extra", new byte[EscrowNativeEnvelope.MAX_BYTES]);
        ra.putByteArray("extra", new byte[EscrowNativeEnvelope.MAX_BYTES]);
        assertEquals(CARGO_TOO_LARGE, check(new Candidate(c.op(), rb, ra, c.deviceBefore(), c.deviceAfter())).status());
    }

    @Test void completeDeviceRootAddsDepthAndNodesToLocallyValidChildren() {
        var c = sample(true, 1, stone(-1));
        for (boolean depth : new boolean[] {true, false}) {
            var child = new CompoundTag();
            if (depth) {
                var cursor = child;
                for (int i = 0; i < 19; i++) { var next = new CompoundTag(); cursor.put("c", next); cursor = next; }
            } else { for (int i = 0; i < 2040; i++) { child.putByte("n" + i, (byte) 0); } }
            assertTrue(BoundedNbt.fits(child, 32_768, 20, 2_048));
            var b = c.deviceBefore().copy(); var a = c.deviceAfter().copy();
            b.getCompound(EscrowItemCargoEligibility.DEVICE_ROOT).put("extra", child);
            a.getCompound(EscrowItemCargoEligibility.DEVICE_ROOT).put("extra", child.copy());
            assertEquals(CARGO_TOO_LARGE, check(new Candidate(c.op(), c.before(), c.after(), b, a)).status());
        }
    }

    @Test void aLocallyValidStackAndFramedPayloadCanFailTheCombinedO1Reservation() {
        for (boolean load : new boolean[] {true, false}) {
            boolean refusal = false;
            for (int keys = 300; keys <= 372 && !refusal; keys++) {
                var contents = stone(-1); var tag = new CompoundTag();
                for (int i = 0; i < keys; i++) { tag.putByte("n" + i, (byte) 1); }
                contents.put("tag", tag);
                assertTrue(BoundedNbt.fits(contents, 4_096, 16, 512));
                var c = sample(load, 64, contents);
                assertTrue(BoundedNbt.fits(c.op().payload(), 8_192, 12, 384), "The framed payload fits at " + keys);
                var result = check(c);
                if (result.status() == CARGO_TOO_LARGE) {
                    System.out.println("W118 O1 refusal: load=" + load + " tag keys=" + keys);
                    assertNull(result.capture()); refusal = true;
                } else { assertEquals(ELIGIBLE, result.status(), "keys=" + keys); }
            }
            assertTrue(refusal, "Must exercise inner-passes/complete-O1-fails, not only a local limit");
        }
    }

    private static EscrowCargoEligibility.Result check(Candidate c) {
        return EscrowItemCargoEligibility.check(ledger(), List.of(), c.op(), c.before(), c.after(), c.deviceBefore(), c.deviceAfter());
    }
    private static Candidate withBefore(Candidate c, RocketStructureSnapshot snapshot) {
        var before = c.before().copy(); before.put("snapshot", RocketSnapshotNbtCodec.encode(snapshot));
        before.putString("content_hash", snapshot.contentHash());
        return new Candidate(c.op(), before, c.after(), c.deviceBefore(), c.deviceAfter());
    }
    static CompoundTag stone(int blob) {
        var saved = new ItemStack(Items.STONE, 64).save(new CompoundTag());
        if (blob >= 0) { var tag = new CompoundTag(); tag.putByteArray("blob", new byte[blob]); saved.put("tag", tag); }
        return saved;
    }

    /** A chest at the snapshot's x = 0 and a device whose buffer slot 0 is the moving end. */
    static Candidate sample(boolean load, int count, CompoundTag contents) {
        return sample(load, count, contents, ItemCargoTestFixtures.state(ItemCargoTestFixtures.CHEST));
    }
    static Candidate sample(boolean load, int count, CompoundTag contents, RocketBlockState state) {
        var full = contents.copy(); full.putByte("Count", (byte) 64);
        var moved = contents.copy(); moved.putByte("Count", (byte) count);
        var before = ItemCargoTestFixtures.snapshot(ItemCargoTestFixtures.block(0, state, load ? ItemCargoTestFixtures.container(null, null)
                : ItemCargoTestFixtures.container(null, null, ItemCargoTestFixtures.entry(0, full))));
        var oldBuffer = load ? ItemCargoTestFixtures.buffer(full) : ItemCargoTestFixtures.buffer();
        var delta = ItemCargoTestFixtures.delta(before, 0, moved, 0, 0);
        var after = RocketItemCargoMutation.apply(before, delta, load);
        var newBuffer = RocketItemCargoMutation.buffer(oldBuffer, delta, load);
        var nativeBefore = EscrowReconciliationBindingBoundaryTest.sample().before();
        var flight = RocketFlightNbtCodec.decode(nativeBefore.getCompound("flight")).data().orElseThrow();
        var fuel = RocketFuelState.empty(before.propulsion().orElseThrow());
        nativeBefore.put("flight", RocketFlightNbtCodec.encode(EscrowNativeEnvelope.fuelOnly(flight, fuel)));
        nativeBefore.putUUID("assembly_snapshot", before.snapshotId()); nativeBefore.put("snapshot", RocketSnapshotNbtCodec.encode(before));
        nativeBefore.putString("content_hash", before.contentHash()); nativeBefore.put("wrapper", RocketEscrowSection.neutral().encode());
        var chunk = nativeBefore.getIntArray("chunk"); var logical = nativeBefore.getUUID("logical");
        var rocket = new EscrowEndpoint(EscrowEndpoint.Kind.ENTITY, nativeBefore.getString("level"), nativeBefore.getUUID("physical"), logical,
                EscrowAdapterIds.ROCKET, chunk[0], 0, chunk[1], EscrowEndpoint.Unit.CONTAINER, 0,
                EscrowSignatureKind.composite(logical, before.contentHash(), fuel.resourceSignature()),
                EscrowSignatureKind.composite(logical, after.contentHash(), fuel.resourceSignature()));
        var deviceId = id(118_001); var type = "advancedrocketrycommunity:rocket_item_" + (load ? "loader" : "unloader");
        var device = new EscrowEndpoint(EscrowEndpoint.Kind.BLOCK_ENTITY, rocket.level(), deviceId, null, type,
                0, 64, 0, EscrowEndpoint.Unit.CONTAINER, 0, oldBuffer.signature(deviceId), newBuffer.signature(deviceId));
        var payload = delta.encode();
        var op = new EscrowOperation(id(1), 1, nativeBefore.getUUID("owner"), 0, EscrowOperation.Phase.PREPARED, EscrowReason.NONE,
                payload, RocketNativeResources.signature(payload), load ? device : rocket, load ? rocket : device,
                new EscrowGuard(logical, null, 1, hash(400)), load ? EscrowOperation.Kind.ITEM_CARGO_LOAD : EscrowOperation.Kind.ITEM_CARGO_UNLOAD,
                new CompoundTag());
        var nativeAfter = nativeBefore.copy(); nativeAfter.put("snapshot", RocketSnapshotNbtCodec.encode(after));
        nativeAfter.putString("content_hash", after.contentHash());
        nativeAfter.put("wrapper", new RocketEscrowSection(ledger().id(), 1, List.of(new EscrowPrefix.Link(op, rocket, load ? "DESTINATION" : "SOURCE").marker())).encode());
        var deviceBefore = new CompoundTag(); deviceBefore.putString("id", type); deviceBefore.putInt("x", 0); deviceBefore.putInt("y", 64); deviceBefore.putInt("z", 0);
        var root = new CompoundTag(); root.putInt("schema_version", 1); root.putUUID("owner", op.owner()); root.putUUID("device", deviceId);
        root.putString("role", load ? "LOAD" : "UNLOAD"); root.put("buffer", oldBuffer.encode()); root.put("resource_escrow", RocketEscrowSection.neutral().encode());
        deviceBefore.put(EscrowItemCargoEligibility.DEVICE_ROOT, root);
        var deviceAfter = deviceBefore.copy(); var afterRoot = deviceAfter.getCompound(EscrowItemCargoEligibility.DEVICE_ROOT);
        afterRoot.put("buffer", newBuffer.encode());
        afterRoot.put("resource_escrow", new RocketEscrowSection(ledger().id(), 1,
                List.of(new EscrowPrefix.Link(op, device, load ? "SOURCE" : "DESTINATION").marker())).encode());
        return new Candidate(op, nativeBefore, nativeAfter, deviceBefore, deviceAfter);
    }
}
