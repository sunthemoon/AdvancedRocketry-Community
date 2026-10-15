package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNbtSize;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Root's R1-R4 reservation requirements; ordinary repair persistence must not acquire a save veto. */
class EscrowReservationTransitionTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    @TempDir Path directory;

    @Test void admissionPinsEffectiveRootCapacityAndRefusesOneMoreWithoutMutation() {
        assertEquals(64, EscrowLimits.OPERATIONS, "The saved-operation decode bound is unchanged");
        for (boolean guarded : List.of(false, true)) {
            int capacity = guarded ? 59 : 63;
            var data = ResourceEscrowSavedData.load(EscrowCodec.encode(EscrowTestFixtures.ledger()));
            var initial = data.ledger();
            try (var writer = new EscrowLedgerWriter(directory.resolve("capacity-" + guarded), 0);
                    var coordinator = new EscrowCoordinator(data, writer)) {
                for (int i = 1; i <= capacity; i++) {
                    coordinator.prepareTest(operation(i, guarded), ignored -> { });
                }
                assertEquals(capacity, coordinator.admissionAuthority().pendingCount());
                assertEquals(capacity + 1, coordinator.nextSequence());
                var refused = assertThrows(IllegalArgumentException.class,
                        () -> coordinator.prepareTest(operation(capacity + 1, guarded), ignored -> fail()));
                assertEquals("Settlement/flight root reservation exhausted", refused.getMessage());
                assertEquals(capacity, coordinator.admissionAuthority().pendingCount());
                assertEquals(capacity + 1, coordinator.nextSequence());
                assertEquals(initial, coordinator.ledger()); assertTrue(writer.idle());
                assertTrue(writer.capturedOperations().isEmpty());
                System.out.println("reservation capacity guarded=" + guarded + " admitted=" + capacity + " refused=" + (capacity + 1));
            }
        }
    }

    @Test void admittedOperationsCanPersistTheirStickyRepairReasons() throws Exception {
        var data = ResourceEscrowSavedData.load(EscrowCodec.encode(EscrowTestFixtures.ledger()));
        var file = directory.resolve(ManagedSavedDataType.RESOURCE_ESCROW.fileName());
        var io = new AwaitedLedgerIo();
        try (var writer = io.writer(file, 0, CheckedSavedDataFile::atomicMove);
                var coordinator = new EscrowCoordinator(data, writer)) {
            var acknowledgements = new AtomicInteger();
            for (int i = 1; i <= 63; i++) {
                coordinator.prepareTest(operation(i, false), accepted -> {
                    assertTrue(accepted); acknowledgements.incrementAndGet();
                });
            }
            assertThrows(IllegalArgumentException.class, () -> coordinator.prepareTest(operation(64, false), ignored -> fail()));
            assertTrue(coordinator.commitPreparations());
            acknowledge(io, coordinator, 1, 1);
            assertEquals(63, acknowledgements.get());
            var admitted = coordinator.ledger();
            var longest = Arrays.stream(EscrowReason.values()).filter(EscrowReason::manual)
                    .max(Comparator.comparingInt(reason -> reason.name().length())).orElseThrow();
            assertEquals(EscrowReason.JOURNAL_GUARD_MISMATCH, longest);
            admitted.records().forEach(op -> coordinator.transition(op.id(), longest));
            coordinator.tick(10); coordinator.autosave();
            acknowledge(io, coordinator, 2, 11);
            var repaired = coordinator.ledger();
            assertEquals(admitted.records().stream().map(op -> op.withReason(longest)).toList(), repaired.records());
            assertPersisted(file, repaired);
            assertTrue(bytes(repaired) > bytes(admitted), "The longest sticky reason grows the encoded root");
            assertDoesNotThrow(() -> EscrowSettlementCapacity.preflight(repaired));

            var observations = EscrowSettlementIntentTest.observations(repaired, false);
            var preview = EscrowSettlementIntent.preview(repaired, repaired.records().get(0).id(),
                    EscrowSettlementIntent.Choice.ACCEPT_CURRENT, EscrowTestFixtures.id(700), "operator", 20, observations);
            var intent = preview.confirm(preview.actor(), preview.digest(), 20, observations);
            var o1Results = new ArrayList<Boolean>(); coordinator.tick(20);
            assertTrue(coordinator.beginSettlement(intent, o1Results::add));
            acknowledge(io, coordinator, 3, 21);
            assertEquals(List.of(true), o1Results);
            assertEquals(intent.encode(), coordinator.ledger().records().get(0).settlement());
            assertDoesNotThrow(() -> EscrowSettlementCapacity.preflight(coordinator.ledger()));
            assertPersisted(file, coordinator.ledger());

            var launch = EscrowFlightAuthorityTest.launch(coordinator.ledger(), 1);
            var armResults = new ArrayList<Boolean>();
            assertTrue(coordinator.flightWrite(launch.logical(), (root, generation) -> EscrowFlightAuthority.arm(root, launch, generation),
                    () -> true, armResults::add));
            coordinator.tick(30); acknowledge(io, coordinator, 4, 31);
            assertEquals(List.of(true), armResults);
            assertEquals(1, coordinator.ledger().armedFlights().size());
            assertEquals(EscrowFlightTables.Outcome.ARMED, coordinator.ledger().armedFlights().get(0).outcome());
            assertDoesNotThrow(() -> EscrowSettlementCapacity.preflight(coordinator.ledger()));
            assertPersisted(file, coordinator.ledger());
            System.out.println("repair-reservation operations=" + admitted.records().size() + " reason=" + longest
                    + " before_bytes=" + bytes(admitted) + " after_bytes=" + bytes(repaired) + " checked=repair,O1,ARMED");
        }
    }

    @Test void oldFixtureBeyondAdmissionCapacityStillLoadsAndPersistsRepairWithExistingPreflight() throws Exception {
        var operations = java.util.stream.LongStream.rangeClosed(1, 64).mapToObj(EscrowTestFixtures::operation).toList();
        var old = EscrowTestFixtures.ledger(operations.toArray(EscrowOperation[]::new));
        var data = ResourceEscrowSavedData.load(EscrowCodec.encode(old));
        assertEquals(old, data.ledger());
        var file = directory.resolve(ManagedSavedDataType.RESOURCE_ESCROW.fileName()); var io = new AwaitedLedgerIo();
        try (var writer = io.writer(file, 0, CheckedSavedDataFile::atomicMove);
                var coordinator = new EscrowCoordinator(data, writer)) {
            old.records().forEach(op -> coordinator.transition(op.id(), EscrowReason.JOURNAL_GUARD_MISMATCH));
            coordinator.autosave(); acknowledge(io, coordinator, 1, 1);
            var repaired = coordinator.ledger();
            assertEquals(64, repaired.records().size());
            assertTrue(repaired.records().stream().allMatch(op -> op.reason() == EscrowReason.JOURNAL_GUARD_MISMATCH));
            assertPersisted(file, repaired);
            assertThrows(IllegalArgumentException.class, () -> EscrowSettlementCapacity.preflight(repaired));
            var launch = EscrowFlightAuthorityTest.launch(repaired, 1); var results = new ArrayList<Boolean>();
            assertTrue(coordinator.flightWrite(launch.logical(), (root, generation) -> EscrowFlightAuthority.arm(root, launch, generation),
                    () -> true, results::add));
            coordinator.tick(10);
            assertTrue(results.isEmpty()); assertEquals(repaired, coordinator.ledger());
            // A concrete ARMED entry replaces its larger unused-table reservation. This
            // old fixture can fit again without settlement; report this R4 boundary to Root.
            acknowledge(io, coordinator, 2, 11);
            assertEquals(List.of(true), results); assertTrue(writer.idle());
            assertEquals(repaired.records(), coordinator.ledger().records());
            assertDoesNotThrow(() -> EscrowSettlementCapacity.preflight(coordinator.ledger()));
            assertPersisted(file, coordinator.ledger());
            System.out.println("old-fixture operations=64 repaired_preflight=REFUSED armed_preflight=ACCEPTED");
        }
    }

    private static EscrowOperation operation(long sequence, boolean guarded) {
        var op = EscrowTestFixtures.operation(sequence);
        return !guarded ? op : new EscrowOperation(op.id(), op.sequence(), op.owner(), op.createdTick(), op.phase(), op.reason(),
                op.payload(), op.payloadHash(), op.source(), op.destination(),
                new EscrowGuard(op.guard().rocket(), EscrowTestFixtures.id(9_000 + sequence), 1, op.guard().journalDigest()));
    }
    private static int bytes(EscrowLedger root) { return RocketNbtSize.uncompressedBytes(EscrowCodec.encode(root)); }
    private static void assertPersisted(Path file, EscrowLedger root) throws Exception {
        assertEquals(root, EscrowCodec.decode(CheckedSavedDataFile.readPayload(file, ManagedSavedDataType.RESOURCE_ESCROW).orElseThrow()));
    }
    private static void acknowledge(AwaitedLedgerIo io, EscrowCoordinator coordinator, long generation, long tick) {
        io.awaitWrites(); coordinator.tick(tick); assertEquals(generation, coordinator.ledger().writerGeneration());
    }
}
