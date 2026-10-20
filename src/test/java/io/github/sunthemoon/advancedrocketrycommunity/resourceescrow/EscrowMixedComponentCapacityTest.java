package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowTestFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** R41: a cross-kind UUID alias joins components, not their per-rocket marker allowances. */
class EscrowMixedComponentCapacityTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    @TempDir Path temp;

    @Test void crossKindUuidAliasKeepsTheFuelRocketsFiveMarkerAllowance() {
        for (var kind : List.of(EscrowOperation.Kind.FLUID_CARGO_LOAD, EscrowOperation.Kind.FLUID_CARGO_UNLOAD,
                EscrowOperation.Kind.ITEM_CARGO_LOAD, EscrowOperation.Kind.ITEM_CARGO_UNLOAD)) {
            var cargo = kind.itemCargo() ? EscrowItemFixtures.operation(kind, 101, 99, 0, -1, "minecraft:overworld")
                    : EscrowCargoFixtures.operation(kind, 101, 99);
            for (int retained = 1; retained <= 5; retained++) {
                var before = EscrowCargoFixtures.wrapper(retained);
                var original = EscrowReceiptChargeTest.fill(100, 98, retained, retained, "minecraft:overworld", false, -1);
                var fuel = aliasDeviceToCargoRocket(original, cargo, before);
                var root = ledger(fuel, cargo);
                assertEquals(root, EscrowCodec.decode(EscrowCodec.encode(root)));
                assertEquals(List.of(fuel, cargo), EscrowSettlementIntent.component(root.records(), cargo.id()));
                assertEquals(fuel.device().identity(), cargo.rocket().identity());
                assertNotEquals(fuel.device().key(), cargo.rocket().key());
                assertNotEquals(fuel.guard().rocket(), cargo.guard().rocket());
                assertDoesNotThrow(() -> EscrowSettlementCapacity.requireAdmission(root, cargo, RocketEscrowSection.neutral()));
                if (retained < 5) {
                    assertDoesNotThrow(() -> EscrowSettlementCapacity.requireAdmission(ledger(fuel), fuel, before));
                    assertDoesNotThrow(() -> EscrowSettlementCapacity.requireAdmission(root, fuel, before));
                } else {
                    // The sixth total marker still refuses, with and without the aliased cargo member.
                    for (var prospective : List.of(ledger(fuel), root)) {
                        assertEquals("Rocket settlement admission limit exceeded", assertThrows(IllegalArgumentException.class,
                                () -> EscrowSettlementCapacity.requireAdmission(prospective, fuel, before)).getMessage());
                    }
                }
            }
        }
    }

    @Test void admittedMixedComponentEncodesActualO1WithAllFuelMarkers() {
        // Find the admission edge with real codecs, independently of the actual O1 observations below.
        int low = 0, high = 8_000;
        while (low < high) {
            int mid = (low + high + 1) / 2;
            try {
                var root = mixedRoot(mid);
                EscrowSettlementCapacity.preflight(root, root.records().get(1).id());
                low = mid;
            } catch (IllegalArgumentException refused) { high = mid - 1; }
        }
        assertTrue(low > 0 && low < 8_000);
        var root = mixedRoot(low); var fuel = root.records().get(0); var cargo = root.records().get(1);
        assertEquals(root, EscrowCodec.decode(EscrowCodec.encode(root)));
        assertDoesNotThrow(() -> EscrowSettlementCapacity.requireAdmission(root, fuel, EscrowCargoFixtures.wrapper(4)));
        assertDoesNotThrow(() -> EscrowSettlementCapacity.requireAdmission(root, cargo, RocketEscrowSection.neutral()));
        assertDoesNotThrow(() -> EscrowSettlementCapacity.preflight(root, cargo.id()));
        assertDoesNotThrow(() -> EscrowSettlementCapacity.preflight(root));
        assertPreparation(root, EscrowCoordinator.Transfer.QUEUED, "admitted");

        var held = root.replace(root.records().stream().map(op -> op.withReason(op.kind().cargo()
                ? EscrowReason.JOURNAL_GUARD_MISMATCH : EscrowReason.OTHER_STATE)).toList(), root.floors(), root.tombstones());
        var observed = new ArrayList<EscrowSettlementIntent.Observation>();
        observed.addAll(EscrowCargoFixtures.observations(held, held.records().get(0), 4, 0, false));
        observed.addAll(EscrowCargoFixtures.observations(held, held.records().get(1), 0, 0, false));
        var preview = EscrowSettlementIntent.preview(held, cargo.id(), EscrowSettlementIntent.Choice.ACCEPT_CURRENT,
                id(700), "~".repeat(16), 10, observed);
        var intent = preview.confirm(preview.actor(), preview.digest(), 11, observed);
        var o1 = assertDoesNotThrow(() -> intent.freeze(held));
        for (var member : o1.records()) {
            var stored = EscrowSettlementIntent.decode(member.settlement());
            assertEquals(intent, stored);
            var fuelMarkers = stored.observations().stream().filter(o -> o.endpoint().key().equals(fuel.rocket().key()))
                    .findFirst().orElseThrow().state().markers();
            assertEquals(5, fuelMarkers.size());
            assertEquals(EscrowCargoFixtures.wrapper(4).markers(), fuelMarkers.subList(0, 4));
            assertEquals(new EscrowPrefix.Link(fuel, fuel.rocket(), "DESTINATION").marker(), fuelMarkers.get(4));
            var cargoMarkers = stored.observations().stream().filter(o -> o.endpoint().key().equals(cargo.rocket().key()))
                    .findFirst().orElseThrow().state().markers();
            assertEquals(List.of(new EscrowPrefix.Link(cargo, cargo.rocket(), "SOURCE").marker()), cargoMarkers);
            assertDoesNotThrow(() -> EscrowCodec.operation(member));
            assertDoesNotThrow(() -> EscrowReceiptCodec.encode(EscrowSettlementTombstones.create(member, stored)));
        }
        var encoded = EscrowCodec.encode(o1);
        assertEquals(o1, EscrowCodec.decode(encoded));
        assertEquals(encoded, EscrowCodec.encode(EscrowCodec.decode(encoded)));

        var next = mixedRoot(low + 1);
        assertDoesNotThrow(() -> EscrowSettlementCapacity.requireAdmission(next, next.records().get(1), RocketEscrowSection.neutral()));
        assertThrows(IllegalArgumentException.class, () -> EscrowSettlementCapacity.preflight(next, next.records().get(1).id()));
        assertThrows(IllegalArgumentException.class, () -> EscrowSettlementCapacity.preflight(next));
        assertPreparation(next, EscrowCoordinator.Transfer.ESCROW_FULL, "next-byte");
        System.out.println("MIXED_COMPONENT admitted_blob=" + low + " fuel_markers=5 cargo_markers=1 actual_o1_encodes=true");
    }

    @Test void formerlyUnderchargedMixedComponentRefusesBeforeAdmissionMutation() {
        // W114-R1's accepted 5,690-byte payload could not freeze with the fuel rocket's retained markers.
        var root = mixedRoot(5_690); var cargo = root.records().get(1);
        assertEquals(root, EscrowCodec.decode(EscrowCodec.encode(root)));
        assertDoesNotThrow(() -> EscrowSettlementCapacity.requireAdmission(root, cargo, RocketEscrowSection.neutral()));
        assertEquals("Escrow NBT envelope exceeded", assertThrows(IllegalArgumentException.class,
                () -> EscrowSettlementCapacity.preflight(root, cargo.id())).getMessage());
        assertThrows(IllegalArgumentException.class, () -> EscrowSettlementCapacity.preflight(root));
        assertPreparation(root, EscrowCoordinator.Transfer.ESCROW_FULL, "review-case");
    }

    private void assertPreparation(EscrowLedger prospective, EscrowCoordinator.Transfer expected, String name) {
        var base = ledger(prospective.records().get(0)); var cargo = prospective.records().get(1);
        var data = ResourceEscrowSavedData.load(EscrowCodec.encode(base));
        try (var writer = new EscrowLedgerWriter(temp.resolve(name), 0); var coordinator = new EscrowCoordinator(data, writer)) {
            long sequence = coordinator.nextSequence();
            int pending = coordinator.admissionAuthority().pendingCount();
            assertFalse(data.isDirty());
            assertEquals(expected, coordinator.prepareTransfer(new EscrowNativeBinding.Admission(cargo,
                    RocketEscrowSection.neutral()), ignored -> fail("No write submitted")));
            boolean queued = expected == EscrowCoordinator.Transfer.QUEUED;
            assertEquals(sequence + (queued ? 1 : 0), coordinator.nextSequence());
            assertEquals(pending + (queued ? 1 : 0), coordinator.admissionAuthority().pendingCount());
            assertEquals(queued, data.isDirty());
            assertEquals(queued, coordinator.queued(cargo.source().key()));
            assertEquals(queued, coordinator.queued(cargo.destination().key()));
            assertEquals(base, coordinator.ledger());
            assertTrue(writer.idle()); assertTrue(writer.capturedOperations().isEmpty());
            if (!queued) { assertFalse(coordinator.commitPreparations()); }
        }
    }

    private static EscrowLedger mixedRoot(int blob) {
        var cargo = EscrowCargoFixtures.guarded(EscrowCargoFixtures.operation(EscrowOperation.Kind.FLUID_CARGO_UNLOAD,
                101, 99, 0, blob, "advancedrocketrycommunity:tau_ceti_g"));
        var original = EscrowReceiptChargeTest.fill(100, 98, 4, 4, "advancedrocketrycommunity:tau_ceti_g", true, -1);
        return ledger(aliasDeviceToCargoRocket(original, cargo, EscrowCargoFixtures.wrapper(4)), cargo);
    }

    private static EscrowOperation aliasDeviceToCargoRocket(EscrowOperation fuel, EscrowOperation cargo, RocketEscrowSection before) {
        var old = fuel.source();
        var device = new EscrowEndpoint(old.kind(), old.level(), cargo.rocket().identity(), old.logicalRocket(), old.type(),
                old.x(), old.y(), old.z(), old.unit(), old.beforeEpoch(), old.beforeSignature(), old.afterSignature());
        return nativeModel(new EscrowOperation(fuel.id(), fuel.sequence(), fuel.owner(), fuel.createdTick(), fuel.phase(), fuel.reason(),
                fuel.payload(), fuel.payloadHash(), device, fuel.destination(), fuel.guard(), fuel.kind(), fuel.settlement()), before);
    }
}
