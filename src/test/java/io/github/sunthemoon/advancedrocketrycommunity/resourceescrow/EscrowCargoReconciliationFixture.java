package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.RocketCargoMutation;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketFluidCargoRoot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightPlanner;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFuelState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketFlightNbtCodec;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.PressurizedTankRocketAdapter;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNativeResources;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketSnapshotNbtCodec;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;

record EscrowCargoReconciliationFixture(EscrowLedger ledger, EscrowOperation op, CompoundTag before, CompoundTag after,
                                        CompoundTag deviceBefore, CompoundTag deviceAfter) {
    static EscrowCargoReconciliationFixture create(boolean load) {
        var before = EscrowReconciliationBindingBoundaryTest.sample().before();
        var old = RocketSnapshotNbtCodec.decode(before.getCompound("snapshot")).snapshot().orElseThrow(); var origin = old.sourceOrigin();
        var snapshot = EscrowCargoPreparationGameTests.snapshot(old.sourceDimension(), new BlockPos(origin.x(), origin.y(), origin.z()),
                false, List.of(load ? FluidStack.EMPTY : new FluidStack(Fluids.WATER, 5_000)));
        var logical = before.getUUID("logical");
        var fuel = RocketFuelState.empty(snapshot.propulsion().orElseThrow());
        var flight = RocketFlightData.initial(logical, 2_000, 1, RocketFlightPlanner.EARTH.bodyId(), snapshot.sourceDimension(), origin, 0)
                .withFuel(fuel, 0);
        before.put("flight", RocketFlightNbtCodec.encode(flight));
        before.put("snapshot", RocketSnapshotNbtCodec.encode(snapshot)); before.putString("content_hash", snapshot.contentHash());
        before.putUUID("assembly_snapshot", snapshot.snapshotId()); before.put("wrapper", RocketEscrowSection.neutral().encode());
        var payload = new EscrowCargoPayload(new EscrowCargoPayload.Cursor(snapshot.snapshotId(), 0, 0, 0,
                PressurizedTankRocketAdapter.ID.toString()), new FluidStack(Fluids.WATER, 1_000).writeToNBT(new CompoundTag()));
        var changed = RocketCargoMutation.apply(snapshot, payload, load); var chunk = before.getIntArray("chunk");
        var endpoint = new EscrowEndpoint(EscrowEndpoint.Kind.ENTITY, before.getString("level"), before.getUUID("physical"), logical,
                EscrowAdapterIds.ROCKET, chunk[0], 0, chunk[1], EscrowEndpoint.Unit.TANK, 0,
                EscrowSignatureKind.composite(logical, snapshot.contentHash(), fuel.resourceSignature()),
                EscrowSignatureKind.composite(logical, changed.contentHash(), fuel.resourceSignature()));
        var model = EscrowFluidCargoEndpointTest.operation(load); var device = model.device();
        var op = new EscrowOperation(model.id(), 1, before.getUUID("owner"), 0, model.phase(), model.reason(), payload.encode(),
                RocketNativeResources.signature(payload.encode()), load ? device : endpoint, load ? endpoint : device,
                new EscrowGuard(logical, null, 1, EscrowTestFixtures.hash(400)), model.kind(), new CompoundTag());
        var root = EscrowFluidCargoEndpointTest.root(load, load ? 5_000 : 0);
        var deviceRoot = new RocketFluidCargoRoot(op.owner(), root.device(), root.role(), root.binding(), root.tank(), root.escrow());
        var deviceBefore = EscrowFluidCargoEndpointTest.envelope(deviceRoot);
        var ledger = EscrowLedger.empty(EscrowFluidCargoEndpointTest.LEDGER);
        var deviceAfter = EscrowFluidCargoEndpoint.change(ledger.id(), op, deviceBefore);
        var after = before.copy(); after.put("snapshot", RocketSnapshotNbtCodec.encode(changed)); after.putString("content_hash", changed.contentHash());
        after.put("wrapper", new RocketEscrowSection(ledger.id(), 1, List.of(new EscrowPrefix.Link(op, endpoint,
                load ? "DESTINATION" : "SOURCE").marker())).encode());
        var captured = EscrowCargoCapture.capture(ledger.id(), op, before, after, deviceBefore, deviceAfter);
        var accounted = EscrowAccountingCapture.bind(captured.operation(), captured.before(),
                before, after, deviceBefore, deviceAfter, snapshot, changed);
        return new EscrowCargoReconciliationFixture(EscrowAccountingCapture.append(ledger, List.of(), accounted),
                accounted.operation(), before, after, deviceBefore, deviceAfter);
    }

    java.util.Map<EscrowEndpoint.Key, java.util.Map<java.util.UUID, EscrowFluidAccount.Vector>> numbers() {
        return java.util.Map.of(op.rocket().key(), java.util.Map.of(op.id(), EscrowAccountingCapture.nativeVector(op, op.rocket(), after)),
                op.device().key(), java.util.Map.of(op.id(), EscrowAccountingCapture.nativeVector(op, op.device(), deviceAfter)));
    }
    /** The unavailable-fluid compatibility tests read a world saved before accounting existed. */
    static EscrowCargoReconciliationFixture createLegacy(boolean load) {
        var f = create(load); var o = f.op();
        var old = new EscrowOperation(o.id(), o.sequence(), o.owner(), o.createdTick(), o.phase(), o.reason(), o.payload(),
                o.payloadHash(), o.source(), o.destination(), o.guard(), o.kind(), o.settlement(), o.nativeBinding());
        var root = EscrowTestFixtures.root2(new EscrowLedger(f.ledger().id(), 1, 0, List.of(old), List.of(), List.of()));
        return new EscrowCargoReconciliationFixture(root, old, f.before(), f.after(), f.deviceBefore(), f.deviceAfter());
    }
    /** Freeze actual native cargo quantities, including OTHER wrappers whose content is still AFTER. */
    List<EscrowFluidAccount.Row> settlementRows(EscrowLedger root, List<EscrowSettlementIntent.Observation> observations) {
        if (root.fluidAccounts().isEmpty()) { return List.of(); }
        var values = new java.util.HashMap<EscrowEndpoint.Key, java.util.Map<java.util.UUID, EscrowFluidAccount.Vector>>();
        for (var observed : observations) {
            if (observed.missing()) { continue; }
            var endpoint = observed.endpoint(); CompoundTag envelope;
            if (endpoint.equals(op.rocket())) {
                envelope = observed.invariantDigest().equals(EscrowCargoSettlementObservationTest.rocket(this, after).invariantDigest()) ? after : before;
            } else {
                envelope = observed.state().signature().equals(op.device().afterSignature()) ? deviceAfter : deviceBefore;
            }
            values.put(endpoint.key(), java.util.Map.of(op.id(), EscrowAccountingCapture.nativeVector(op, endpoint, envelope)));
        }
        return EscrowSettlementAccounting.project(root, root.records(), observations, values);
    }
}
