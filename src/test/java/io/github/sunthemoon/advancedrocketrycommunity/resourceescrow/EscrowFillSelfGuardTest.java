package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EscrowFillSelfGuardTest {
    @TempDir Path directory;
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void preparedAndEveryRetryableStallSurviveCodecReloadWithoutClearingTheirReason() {
        var op = fill(1, 0);
        for (var reason : EscrowReason.values()) {
            if (reason != EscrowReason.NONE && !reason.retryableProofFailure()) { continue; }
            var candidate = op.withReason(reason);
            for (int restart = 0; restart < 3; restart++) {
                var root = EscrowCodec.decode(EscrowCodec.encode(EscrowTestFixtures.ledger(candidate)));
                assertTrue(matches(root, op, root.records(), Map.of(), RocketEscrowSection.neutral()), reason.name());
                assertEquals(reason, root.records().get(0).reason());
                assertEquals(reason == EscrowReason.NONE ? EscrowOperation.Phase.PREPARED : EscrowOperation.Phase.STALLED,
                        root.records().get(0).phase());
            }
        }
    }

    @Test void coordinatorSeesPendingStallsAndManualTransitionsAndClosedLifecycle() {
        var op = fill(1, 0);
        var data = ResourceEscrowSavedData.load(EscrowCodec.encode(EscrowTestFixtures.ledger(op)));
        var io = new AwaitedLedgerIo();
        try (var writer = io.writer(directory.resolve("reasons.dat"), 0, CheckedSavedDataFile::atomicMove);
             var coordinator = new EscrowCoordinator(data, writer)) {
            assertTrue(coordinator.fillSelfGuard(op.guard(), op.destination().key(), RocketEscrowSection.neutral()));
            assertTrue(coordinator.ordinaryFillsOnly(op.guard().rocket(), op.destination().key()));
            coordinator.transition(op.id(), EscrowReason.BARRIER_IO);
            assertTrue(coordinator.fillSelfGuard(op.guard(), op.destination().key(), RocketEscrowSection.neutral()));
            assertFalse(coordinator.ordinaryFillsOnly(op.guard().rocket(), op.destination().key()));
            assertEquals(EscrowReason.NONE, coordinator.ledger().records().get(0).reason(), "Pending transition is not a durable clear");
            coordinator.transition(op.id(), EscrowReason.OTHER_STATE);
            assertFalse(coordinator.fillSelfGuard(op.guard(), op.destination().key(), RocketEscrowSection.neutral()));
            coordinator.close();
            assertFalse(coordinator.fillSelfGuard(op.guard(), op.destination().key(), RocketEscrowSection.neutral()));
        }
    }

    @Test void queuedAndCapturedOperationsRemainPartOfTheSelfGuard() {
        var op = EscrowTestFixtures.nativeModel(fill(1, 0), RocketEscrowSection.neutral());
        var data = ResourceEscrowSavedData.load(EscrowCodec.encode(EscrowTestFixtures.ledger()));
        var io = new AwaitedLedgerIo();
        try (var writer = io.writer(directory.resolve(ManagedSavedDataType.RESOURCE_ESCROW.fileName()),
                0, CheckedSavedDataFile::atomicMove);
             var coordinator = new EscrowCoordinator(data, writer)) {
            assertFalse(coordinator.fillSelfGuard(op.guard(), op.destination().key(), RocketEscrowSection.neutral()));
            coordinator.prepareTest(op, RocketEscrowSection.neutral(), ignored -> { });
            assertTrue(coordinator.fillSelfGuard(op.guard(), op.destination().key(), RocketEscrowSection.neutral()));
            coordinator.tick(10); assertTrue(coordinator.commitPreparations());
            assertTrue(coordinator.fillSelfGuard(op.guard(), op.destination().key(), RocketEscrowSection.neutral()));
            io.tickUntil(coordinator::tick, 11, () -> coordinator.durable(op.id()));
            assertTrue(coordinator.durable(op.id()));
            assertTrue(coordinator.fillSelfGuard(op.guard(), op.destination().key(), RocketEscrowSection.neutral()));
        }
    }

    @Test void excludesManualSettlementTransientKindsAndForeignGuards() {
        var op = fill(1, 0); var root = EscrowTestFixtures.ledger(op);
        for (var reason : EscrowReason.values()) {
            if (reason == EscrowReason.NONE || reason.retryableProofFailure()) { continue; }
            assertFalse(matches(root, op, List.of(op.withReason(reason)), Map.of(), RocketEscrowSection.neutral()), reason.name());
        }
        for (var phase : List.of(EscrowOperation.Phase.SETTLING_ACCEPT, EscrowOperation.Phase.SETTLING_ABANDON)) {
            var candidate = new EscrowOperation(op.id(), op.sequence(), op.owner(), op.createdTick(), phase, op.reason(),
                    op.payload(), op.payloadHash(), op.source(), op.destination(), op.guard(), op.kind(), op.settlement(), op.nativeBinding());
            assertFalse(matches(root, op, List.of(candidate), Map.of(), RocketEscrowSection.neutral()));
        }
        for (var guard : List.of(new EscrowGuard(op.guard().rocket(), null, 1, op.guard().journalDigest()),
                new EscrowGuard(op.guard().rocket(), EscrowTestFixtures.id(7001), 1, op.guard().journalDigest()),
                new EscrowGuard(op.guard().rocket(), op.guard().transfer(), 1, EscrowTestFixtures.hash(7001)))) {
            var changed = new EscrowOperation(op.id(), op.sequence(), op.owner(), op.createdTick(), op.phase(), op.reason(),
                    op.payload(), op.payloadHash(), op.source(), op.destination(), guard, op.kind(), op.settlement(), op.nativeBinding());
            assertFalse(matches(root, op, List.of(changed), Map.of(), RocketEscrowSection.neutral()));
        }
        var model = new EscrowOperation(op.id(), op.sequence(), op.owner(), op.createdTick(), op.phase(), op.reason(),
                op.payload(), op.payloadHash(), op.source(), op.destination(), op.guard());
        assertFalse(matches(root, op, List.of(model), Map.of(), RocketEscrowSection.neutral()), "Incomplete native binding");
        assertFalse(matches(root, op, List.of(EscrowTestFixtures.operation(1)), Map.of(), RocketEscrowSection.neutral()), "Not a loader/native rocket");
    }

    @Test void matchesOnlyOwnMarkersAndFrozenFrontiersAndKeepsAllMembersOfAChain() {
        var a = fill(1, 0); var b = fill(2, 1); var root = EscrowTestFixtures.ledger(a, b);
        var chains = EscrowPrefix.chains(root.records());
        var markers = chains.get(a.destination().key()).stream().map(EscrowPrefix.Link::marker).toList();
        var wrapper = new RocketEscrowSection(root.id(), 2, markers);
        var frontier = new EscrowPrefix.State(root.id(), 2, b.destination().afterSignature(), markers);
        assertTrue(matches(root, a, root.records(), Map.of(a.destination().key(), frontier), wrapper));
        assertFalse(matches(root, a, List.of(a), Map.of(a.destination().key(), frontier), wrapper), "Partial work cannot own later markers");
        var foreign = new RocketEscrowSection(root.id(), 2, List.of(new EscrowPrefix.Marker(EscrowTestFixtures.id(5000), 2, "DESTINATION", 2)));
        assertFalse(matches(root, a, root.records(), Map.of(), foreign));
        assertFalse(matches(root, a, root.records(), Map.of(), new RocketEscrowSection(EscrowTestFixtures.id(5001), 2, markers)));
        assertFalse(matches(root, a, root.records(), Map.of(a.destination().key(),
                new EscrowPrefix.State(root.id(), 2, EscrowTestFixtures.hash(9000), markers)), wrapper));
        var wrong = new EscrowEndpoint.Key(a.destination().kind(), a.destination().level(), EscrowTestFixtures.id(9000),
                a.guard().rocket(), a.destination().type(), 0, 0, 0, a.destination().unit());
        assertFalse(matches(root, a, root.records(), Map.of(wrong, frontier), wrapper));
        assertFalse(EscrowFillSelfGuard.matches(true, false, root, a.guard(), wrong, wrapper, root.records(), EscrowOperation::reason, Map.of()));
        assertFalse(matches(root, a, List.of(a, b.withReason(EscrowReason.FLOOR_CONFLICT)), Map.of(), wrapper));
    }

    @Test void missingWorkShutdownFlightAndTombstoneNeverGrantSelfGuard() {
        var op = fill(1, 0); var root = EscrowTestFixtures.ledger(op);
        assertFalse(matches(root, op, List.of(), Map.of(), RocketEscrowSection.neutral()));
        assertFalse(EscrowFillSelfGuard.matches(false, false, root, op.guard(), op.destination().key(), RocketEscrowSection.neutral(),
                root.records(), EscrowOperation::reason, Map.of()));
        assertFalse(EscrowFillSelfGuard.matches(true, true, root, op.guard(), op.destination().key(), RocketEscrowSection.neutral(),
                root.records(), EscrowOperation::reason, Map.of()));
        var receipt = EscrowTestFixtures.receipt(1);
        var guards = new net.minecraft.nbt.ListTag(); guards.add(EscrowCodec.guard(op.guard())); receipt.put("guards", guards);
        var tombstoned = root.replace(root.records(), root.floors(), List.of(receipt));
        assertFalse(matches(tombstoned, op, root.records(), Map.of(), RocketEscrowSection.neutral()));
        var arm = new EscrowFlightTables.Armed(root.id(), op.guard().rocket(), op.owner(), op.destination().identity(), 0,
                EscrowTestFixtures.id(8000), 1, 1, EscrowTestFixtures.hash(1), EscrowTestFixtures.hash(2),
                EscrowTestFixtures.hash(3), EscrowTestFixtures.hash(4), EscrowTestFixtures.id(8001), op.guard(), null, 0,
                EscrowFlightTables.Outcome.ARMED);
        var armed = new EscrowLedger(root.id(), root.issuedThrough(), root.writerGeneration(), root.records(), root.floors(),
                root.tombstones(), 1, List.of(), List.of(arm));
        assertFalse(matches(armed, op, root.records(), Map.of(), RocketEscrowSection.neutral()));
    }

    private static boolean matches(EscrowLedger root, EscrowOperation op, List<EscrowOperation> unresolved,
                                   Map<EscrowEndpoint.Key, EscrowPrefix.State> frontiers, RocketEscrowSection wrapper) {
        return EscrowFillSelfGuard.matches(true, false, root, op.guard(), op.destination().key(), wrapper,
                unresolved, EscrowOperation::reason, frontiers);
    }

    static EscrowOperation fill(long sequence, long epoch) {
        var original = EscrowTestFixtures.operation(sequence, 1, epoch); var s = original.source(); var d = original.destination();
        var source = new EscrowEndpoint(s.kind(), s.level(), s.identity(), null, EscrowAdapterIds.LOADER,
                s.x(), s.y(), s.z(), s.unit(), s.beforeEpoch(), s.beforeSignature(), s.afterSignature());
        var destination = new EscrowEndpoint(d.kind(), d.level(), d.identity(), d.logicalRocket(), EscrowAdapterIds.ROCKET,
                d.x(), d.y(), d.z(), d.unit(), d.beforeEpoch(), d.beforeSignature(), d.afterSignature());
        return EscrowTestFixtures.nativeModel(new EscrowOperation(original.id(), sequence, original.owner(), original.createdTick(),
                original.phase(), original.reason(), original.payload(), original.payloadHash(), source, destination,
                new EscrowGuard(d.logicalRocket(), EscrowTestFixtures.id(7000), 1, EscrowTestFixtures.hash(7000))));
    }
}
