package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketCargoRole;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketFluidCargoRoot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.LoaderTank;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNativeResources;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EscrowFluidCargoEndpointTest {
    static final UUID LEDGER = EscrowTestFixtures.id(124), OWNER = EscrowTestFixtures.id(125), DEVICE = EscrowTestFixtures.id(126);
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    static RocketFluidCargoRoot root(boolean load, int amount) {
        return RocketFluidCargoRoot.placed(OWNER, DEVICE, load ? RocketCargoRole.LOAD : RocketCargoRole.UNLOAD,
                LoaderTank.of(ResourceLocation.tryParse("minecraft:water"), null, amount));
    }
    static CompoundTag envelope(RocketFluidCargoRoot root) {
        var tag = new CompoundTag(); tag.putString("id", "advancedrocketrycommunity:" + root.role().id());
        tag.putInt("x", 10); tag.putInt("y", 64); tag.putInt("z", -3); tag.put(RocketFluidCargoRoot.KEY, root.encode());
        return tag;
    }
    static EscrowOperation operation(boolean load) {
        var kind = load ? EscrowOperation.Kind.FLUID_CARGO_LOAD : EscrowOperation.Kind.FLUID_CARGO_UNLOAD;
        var model = EscrowCargoFixtures.operation(kind, 1, 124);
        var before = root(load, load ? 5_000 : 0); var after = root(load, load ? 4_000 : 1_000);
        var device = new EscrowEndpoint(EscrowEndpoint.Kind.BLOCK_ENTITY, "minecraft:overworld", DEVICE, null,
                "advancedrocketrycommunity:" + before.role().id(), 10, 64, -3, EscrowEndpoint.Unit.TANK,
                0, before.signature(), after.signature());
        return new EscrowOperation(model.id(), model.sequence(), OWNER, 0, model.phase(), model.reason(), model.payload(),
                RocketNativeResources.signature(model.payload()), load ? device : model.rocket(), load ? model.rocket() : device,
                model.guard(), kind, new CompoundTag());
    }
    static CompoundTag slot(CompoundTag... entries) {
        var slot = new CompoundTag(); slot.putInt("xPos", 0); slot.putInt("zPos", -1);
        var list = new ListTag(); for (var entry : entries) { list.add(entry); } slot.put("block_entities", list); return slot;
    }

    @Test void nativeDiskDeviceUsesItsOwnRootInBothDirections() {
        for (boolean load : List.of(true, false)) {
            var root = root(load, load ? 5_000 : 0); var op = operation(load);
            var observation = EscrowSettlementDisk.observe(slot(envelope(root)), op.device(), LEDGER);
            assertEquals(root.signature(), observation.state().signature());
            assertEquals(root.tank().amount(), observation.quantity());
            assertEquals(EscrowSignatureKind.FLUID_TANK_V1, observation.signatureKind());
            assertFalse(observation.bound()); assertTrue(observation.state().markers().isEmpty());
        }
    }

    @Test void fixedDeltaBindsTheRoleAndIsNotReplayableAgainstAfter() {
        for (boolean load : List.of(true, false)) {
            var before = root(load, load ? 5_000 : 0); var op = operation(load); var capture = envelope(before);
            var saved = capture.copy(); var after = EscrowFluidCargoEndpoint.change(LEDGER, op, capture);
            var changed = EscrowFluidCargoEndpoint.root(after);
            assertEquals(saved, capture); assertEquals(load ? 4_000 : 1_000, changed.tank().amount());
            assertEquals(before.owner(), changed.owner()); assertEquals(before.device(), changed.device());
            assertEquals(before.binding(), changed.binding()); assertEquals(before.role(), changed.role());
            assertEquals(new RocketEscrowSection(LEDGER, 1, List.of(new EscrowPrefix.Link(op, op.device(),
                    load ? "SOURCE" : "DESTINATION").marker())), changed.escrow());
            assertEquals(op.device().afterSignature(), changed.signature());
            assertThrows(IllegalArgumentException.class, () -> EscrowFluidCargoEndpoint.change(LEDGER, op, after));
            var other = envelope(root(!load, 5_000));
            assertThrows(IllegalArgumentException.class, () -> EscrowFluidCargoEndpoint.change(LEDGER, op, other));
            var moved = capture.copy(); moved.putInt("x", 11);
            assertThrows(IllegalArgumentException.class, () -> EscrowFluidCargoEndpoint.change(LEDGER, op, moved));
            var stale = envelope(root(load, load ? 4_999 : 1));
            assertThrows(IllegalArgumentException.class, () -> EscrowFluidCargoEndpoint.change(LEDGER, op, stale));
            var owned = capture.copy(); owned.getCompound(RocketFluidCargoRoot.KEY).putUUID("owner", UUID.randomUUID());
            assertThrows(IllegalArgumentException.class, () -> EscrowFluidCargoEndpoint.change(LEDGER, op, owned));
            var foreign = capture.copy(); foreign.getCompound(RocketFluidCargoRoot.KEY).put(RocketFluidCargoRoot.ESCROW,
                    new RocketEscrowSection(UUID.randomUUID(), 0, List.of()).encode());
            assertThrows(IllegalArgumentException.class, () -> EscrowFluidCargoEndpoint.change(LEDGER, op, foreign));
        }
    }

    @Test void diskCensusRejectsRelocatedDuplicatedWrongRoleAndUnreadableExactRoots() {
        for (boolean load : List.of(true, false)) {
            var op = operation(load); var exact = envelope(root(load, 5_000));
            var moved = exact.copy(); moved.putInt("x", 11);
            assertThrows(IllegalArgumentException.class, () -> EscrowSettlementDisk.observe(slot(exact, moved), op.device(), LEDGER));
            assertThrows(IllegalArgumentException.class, () -> EscrowSettlementDisk.observe(slot(moved), op.device(), LEDGER));
            assertThrows(IllegalArgumentException.class, () -> EscrowSettlementDisk.observe(slot(exact, exact), op.device(), LEDGER));
            var future = exact.copy(); future.getCompound(RocketFluidCargoRoot.KEY).putInt("schema_version", 2);
            assertNull(EscrowFluidCargoEndpoint.device(future));
            assertThrows(IllegalArgumentException.class, () -> EscrowSettlementDisk.observe(slot(future), op.device(), LEDGER));
            assertThrows(IllegalArgumentException.class, () -> EscrowSettlementDisk.observe(slot(envelope(root(!load, 5_000))), op.device(), LEDGER));
            assertTrue(EscrowSettlementDisk.observe(slot(), op.device(), LEDGER).missing());
        }
    }

    @Test void pruningCannotChangeResourcesEpochIdentityOrAddAMarker() {
        for (boolean load : List.of(true, false)) {
            var op = operation(load); var after = EscrowFluidCargoEndpoint.root(EscrowFluidCargoEndpoint.change(
                    LEDGER, op, envelope(root(load, load ? 5_000 : 0))));
            assertEquals(new RocketEscrowSection(LEDGER, 1, List.of()), EscrowFluidCargoEndpoint.pruned(after,
                    new EscrowPrefix.State(LEDGER, 1, after.signature(), List.of())));
            for (var state : List.of(new EscrowPrefix.State(LEDGER, 0, after.signature(), List.of()),
                    new EscrowPrefix.State(UUID.randomUUID(), 1, after.signature(), List.of()),
                    new EscrowPrefix.State(LEDGER, 1, op.device().beforeSignature(), List.of()),
                    new EscrowPrefix.State(LEDGER, 1, after.signature(), List.of(
                            new EscrowPrefix.Marker(UUID.randomUUID(), 1, load ? "SOURCE" : "DESTINATION", 1))))) {
                assertThrows(IllegalArgumentException.class, () -> EscrowFluidCargoEndpoint.pruned(after, state));
            }
        }
    }
}
