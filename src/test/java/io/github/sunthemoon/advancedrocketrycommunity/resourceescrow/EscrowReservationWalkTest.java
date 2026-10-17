package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowTestFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** A0 accounting walk; endpoint observations are fixtures, not native durability proofs. */
class EscrowReservationWalkTest {
    @org.junit.jupiter.api.io.TempDir java.nio.file.Path temp;
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void everyNonAdmissionTransitionPreservesOrReleasesCapacity() {
        var root = EscrowCombinedReservationTest.retainedFloors(29);
        for (int i = 30; i < 90; i++) {
            var op = i == 30 ? EscrowSettlementCapacityTest.operation(i, i, 0, true) : operation(i);
            if (i == 31) {
                op = new EscrowOperation(op.id(), op.sequence(), op.owner(), op.createdTick(), op.phase(), op.reason(),
                        op.payload(), op.payloadHash(), op.source(), op.destination(), new EscrowGuard(op.guard().rocket(), id(80_000), 1, hash(400)));
            }
            root = root.append(List.of(op));
            EscrowSettlementCapacity.requireAdmission(root, op, i == 30 ? RocketEscrowSection.neutral() : null);
            EscrowSettlementCapacity.preflight(root, op.id());
        }
        var atCapacity = root;
        assertEquals(60, root.records().size());
        assertThrows(IllegalArgumentException.class, () -> EscrowSettlementCapacity.preflight(atCapacity.append(List.of(operation(90))), id(90)));
        assertEquals(14_336L * 60 + 1_024L * (128 + 30)
                + io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNbtSize.uncompressedBytes(EscrowCodec.encode(ledger())),
                EscrowSettlementCapacity.reservedBytes(root));

        root = transition("sticky reason", root, root.replace(root.records().stream()
                .map(op -> op.withReason(EscrowReason.JOURNAL_GUARD_MISMATCH)).toList(), root.floors(), root.tombstones()), true);
        var selected = root.records().get(0);
        var marker = new EscrowPrefix.Link(selected, selected.destination(), "DESTINATION").marker();
        var ledgerId = root.id();
        var observations = EscrowSettlementIntentTest.observations(root, false).stream().map(observed ->
                observed.endpoint().equals(selected.destination()) ? new EscrowSettlementIntent.Observation(observed.endpoint(),
                        new EscrowPrefix.State(ledgerId, 1, selected.destination().afterSignature(), List.of(marker)), true, 0,
                        observed.signatureKind(), selected.nativeBinding().invariant()) : observed).toList();
        var preview = EscrowSettlementIntent.preview(root, selected.id(), EscrowSettlementIntent.Choice.ACCEPT_CURRENT,
                id(700), "operator", 10, observations);
        var intent = preview.confirm(preview.actor(), preview.digest(), 11, observations);
        root = transition("O1", root, intent.freeze(root), false);
        root = transition("O3 receipt installation", root, EscrowSettlementTombstones.install(root, intent), true);
        assertEquals(1, root.tombstones().size());
        root = transition("O4 release and READY", root, EscrowSettlementTombstones.release(root, intent), false);
        assertEquals(1, root.rocketAuthorities().size()); assertTrue(root.tombstones().get(0).getBoolean("released"));

        var beforePruning = EscrowCodec.encode(root);
        var pruned = EscrowSettlementPruning.prune(List.of(marker), intent.ids(), Set.of());
        assertTrue(pruned.isEmpty()); assertEquals(beforePruning, EscrowCodec.encode(root));
        // Final review A, I1: compare with the root of a real pruning generation, not the same root.
        var pruning = pruningGeneration(root, selected.destination(), marker);
        assertEquals(root.writerGeneration() + 1, pruning.writerGeneration());
        assertEquals(root.generation(pruning.writerGeneration()), pruning, "Pruning changed the root beyond its generation");
        root = transition("retired marker pruning", root, pruning, true);
        var head = root.rocketAuthorities().get(0);
        var launch = new EscrowFlightAuthority.Launch(head.logical(), head.owner(), head.physical(),
                new RocketEscrowSection(root.id(), head.epoch(), pruned), head.signature(), head.invariant(), id(80_001), id(80_002),
                hash(4), hash(5), hash(6), hash(7), new EscrowGuard(head.logical(), null, 1, hash(8)), Set.of());
        // ARMED is the next writer generation, after the pruning generation above.
        root = transition("ARMED", root, EscrowFlightAuthority.arm(root, launch, root.writerGeneration() + 1), true);
        var arm = root.armedFlights().get(0);
        root = transition("terminal handoff", root, EscrowFlightAuthority.finish(root, arm, EscrowFlightTables.Outcome.HANDED_OFF,
                arm.destination(), 0, hash(9), head.invariant()), true);
        assertEquals(EscrowFlightTables.Outcome.HANDED_OFF, root.rocketAuthorities().get(0).outcome());
        root = transition("terminal cleanup", root, EscrowFlightAuthority.prune(root, root.armedFlights().get(0)), true);
        assertTrue(root.armedFlights().isEmpty()); assertEquals(1, root.issuedLaunchSerial());

        var guarded = root.records().stream().filter(op -> op.id().equals(id(31))).findFirst().orElseThrow();
        var frontier = new EscrowPrefix.State(root.id(), 1, guarded.destination().afterSignature(),
                List.of(new EscrowPrefix.Link(guarded, guarded.destination(), "DESTINATION").marker()));
        root = transition("L2 named floor installation", root, EscrowFloors.retire(root, Set.of(guarded.id()),
                java.util.Map.of(guarded.destination().key(), frontier)), false);
        assertEquals(30, root.floors().size());
        root = transition("floor collection", root, EscrowFloors.collect(root, List.of(), transfer -> true), false);
        assertTrue(root.floors().isEmpty()); assertEquals(1, root.tombstones().size());
    }

    /**
     * The product cursor schedules the released survivor and the product frontier/prefix rule prunes its wrapper.
     * Ordinary saving persists that wrapper; the escrow root's next generation is an ordinary checked autosave.
     */
    private EscrowLedger pruningGeneration(EscrowLedger root, EscrowEndpoint survivor, EscrowPrefix.Marker marker) {
        var data = ResourceEscrowSavedData.load(EscrowCodec.encode(root)); var ledgerIo = new AwaitedLedgerIo();
        try (var writer = ledgerIo.writer(temp.resolve(ManagedSavedDataType.RESOURCE_ESCROW.fileName()), root.writerGeneration(),
                CheckedSavedDataFile::atomicMove);
             var coordinator = new EscrowCoordinator(data, writer)) {
            data.attach(coordinator, () -> true); coordinator.tick(10);
            var cursor = new EscrowSettlementPruning.Cursor(); var unresolved = coordinator.unresolvedIds();
            EscrowSettlementPruning.Visit visit = null;
            for (int i = 0; i < 4 && (visit == null || !visit.endpoint().reference().key().equals(survivor.key())); i++) {
                visit = cursor.next(data.ledger(), unresolved, coordinator::pruningBusy);
            }
            assertNotNull(visit); assertEquals(survivor.key(), visit.endpoint().reference().key());
            var current = new EscrowPrefix.State(root.id(), 1, survivor.afterSignature(), List.of(marker));
            assertTrue(EscrowSettlementPruning.frontier(data.ledger(), cursor.released(data.ledger()), survivor, current, 0,
                    current.signature(), unresolved));
            assertEquals(List.of(), EscrowSettlementPruning.prune(current.markers(), visit.group().members(), unresolved));
            data.setDirty(); coordinator.autosave(); assertFalse(writer.idle(), "No pruning generation was requested");
            ledgerIo.tickUntil(coordinator::tick, 11, writer::idle);
            assertTrue(writer.idle(), "The pruning generation was not acknowledged within the bounded owner ticks");
            var durable = CheckedSavedDataFile.readPayload(temp.resolve(ManagedSavedDataType.RESOURCE_ESCROW.fileName()),
                    ManagedSavedDataType.RESOURCE_ESCROW).orElseThrow();
            assertEquals(data.ledger(), EscrowCodec.decode(durable), "The acknowledged pruning generation is not the durable root");
            return data.ledger();
        }
    }
    private static EscrowLedger transition(String label, EscrowLedger before, EscrowLedger after, boolean equal) {
        long previous = EscrowSettlementCapacity.reservedBytes(before), next = EscrowSettlementCapacity.reservedBytes(after);
        assertTrue(next <= previous, () -> label + " increased reservation from " + previous + " to " + next);
        if (equal) { assertEquals(previous, next, label); }
        else { assertTrue(next < previous, label + " must release capacity"); }
        assertDoesNotThrow(() -> EscrowSettlementCapacity.preflight(after), label);
        assertEquals(after, EscrowCodec.decode(EscrowCodec.encode(after)), label);
        System.out.println("reservation-walk " + label + " before=" + previous + " after=" + next);
        return after;
    }
}
