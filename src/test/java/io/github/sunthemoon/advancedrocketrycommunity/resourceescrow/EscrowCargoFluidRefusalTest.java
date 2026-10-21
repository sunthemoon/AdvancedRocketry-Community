package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNativeResources;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EscrowCargoFluidRefusalTest {
    @org.junit.jupiter.api.io.TempDir java.nio.file.Path directory;
    @BeforeAll static void bootstrap() {
        MinecraftBootstrap.initialize();
        @SuppressWarnings("unchecked") var fluids = (net.minecraftforge.registries.ForgeRegistry<net.minecraft.world.level.material.Fluid>)
                net.minecraftforge.registries.ForgeRegistries.FLUIDS;
        fluids.addAlias(net.minecraft.resources.ResourceLocation.tryParse("w124:renamed_water"),
                net.minecraft.resources.ResourceLocation.tryParse("minecraft:water"));
    }
    static EscrowOperation unavailable(EscrowOperation op, String name) {
        var encoded = EscrowCodec.operation(op);
        encoded.getCompound("payload").getCompound("fluid").putString("FluidName", name);
        encoded.putString("payload_hash", RocketNativeResources.signature(encoded.getCompound("payload")));
        return EscrowCodec.operation(encoded);
    }
    @Test void deviceC2RefusesAsOtherStateBeforeChangingEitherRole() {
        for (boolean load : List.of(true, false)) {
          for (String name : List.of("w124:missing", "w124:renamed_water")) {
            var f = EscrowCargoReconciliationFixture.create(load); var op = unavailable(f.op(), name);
            var root = new EscrowLedger(f.ledger().id(), 1, 1, List.of(op), List.of(), List.of());
            var before = f.deviceBefore().copy();
            var refused = assertThrows(EscrowReconciliationView.Refusal.class,
                    () -> EscrowFluidCargoPublication.issue(root, op, before, 1, () -> true));
            assertEquals(EscrowReason.OTHER_STATE, refused.reason);
            assertEquals(f.deviceBefore(), before); assertEquals(op.payload(), root.records().get(0).payload());
            assertEquals(EscrowReason.OTHER_STATE, assertThrows(EscrowReconciliationView.Refusal.class,
                    () -> new EscrowCargoReconciliation().applyNext(null, op, null, Map.of())).reason,
                    "R39 must run before a replay ticket or capture is even consulted");
          }
        }
    }

    @Test void typedReadbackRefusesUnavailablePayloadBeforeDecodingTheNativeRecord() {
        for (boolean load : List.of(true, false)) {
          for (String name : List.of("w124:missing", "w124:renamed_water")) {
            var f = EscrowCargoReconciliationFixture.create(load); var held = unavailable(f.op(), name);
            for (var endpoint : List.of(held.rocket(), held.device())) {
                var slot = endpoint.kind() == EscrowEndpoint.Kind.ENTITY
                        ? EscrowReconciliationCheckpointTest.rocketSlot(endpoint, f.after())
                        : EscrowFluidCargoEndpointTest.slot(f.deviceAfter());
                var reader = new EscrowCargoReadback(f.ledger().id(), EscrowSettlementDisk.ordinary(slot, endpoint, f.ledger().id()),
                        null, List.of(held));
                assertFalse(CompletableFuture.supplyAsync(() -> reader.test(slot)).join());
                assertFalse(reader.confirmed()); assertEquals(EscrowReason.OTHER_STATE, reader.reason());
                assertEquals(EscrowReason.OTHER_STATE, reader.reason());
            }
          }
        }
    }

    @Test void stickyRefusalPreservesPayloadAndOtherComponentsAcrossCheckedWrite() throws Exception {
        var good = EscrowCargoFixtures.operation(EscrowOperation.Kind.FLUID_CARGO_LOAD, 2, 799);
        var broken = EscrowCargoReconciliationFixture.create(false);
        var held = unavailable(broken.op(), "w124:missing");
        var root = new EscrowLedger(broken.ledger().id(), 2, 1, List.of(held, good), List.of(), List.of());
        var data = ResourceEscrowSavedData.load(EscrowCodec.encode(root));
        var awaited = new AwaitedLedgerIo(); var file = directory.resolve(
                io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType.RESOURCE_ESCROW.fileName());
        var visited = new java.util.ArrayList<java.util.UUID>(); var published = new java.util.ArrayList<java.util.UUID>();
        try (var writer = awaited.writer(file, 1, io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile::atomicMove);
             var coordinator = new EscrowCoordinator(data, writer)) {
            var scheduler = new EscrowReconciliationScheduler(coordinator::ledger, coordinator::transition, (op, records, done) -> {
                visited.add(op.id());
                try { EscrowReconciliationAction.requireNativeFluid(op); published.add(op.id()); done.accept(EscrowReconciliationScheduler.Result.deferred()); }
                catch (EscrowReconciliationView.Refusal refused) { done.accept(new EscrowReconciliationScheduler.Result(refused.reason, false)); }
            });
            scheduler.tick(1);
            assertEquals(List.of(good.id()), published);
            assertEquals(EscrowReason.OTHER_STATE, coordinator.currentReason(held)); assertTrue(coordinator.operational());
            coordinator.persistTransitions(); awaited.tickUntil(coordinator::tick, 1, writer::idle);
            var persisted = EscrowCodec.decode(io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile
                    .readPayload(file, io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType.RESOURCE_ESCROW).orElseThrow());
            assertEquals(List.of(held.withReason(EscrowReason.OTHER_STATE), good), persisted.records());
            assertEquals(held.payload(), persisted.records().get(0).payload());
            var heldId = held.id(); visited.clear(); scheduler.tick(2_000); scheduler.tick(4_000);
            assertFalse(visited.contains(heldId)); assertTrue(visited.contains(good.id()));
        }
    }
}
