package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EscrowFlightQueueAdmissionTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    @TempDir Path directory;

    @Test void duplicateAndCapacityRefusalsDoNotReplaceQueuedRequestsOrCallTheirCallbacks() throws Exception {
        var data = ResourceEscrowSavedData.create(); var before = data.ledger(); var replies = new ArrayList<Boolean>();
        var io = new AwaitedLedgerIo();
        try (var writer = io.writer(directory.resolve(ManagedSavedDataType.RESOURCE_ESCROW.fileName()), 0, CheckedSavedDataFile::atomicMove);
             var coordinator = new EscrowCoordinator(data, writer)) {
            var first = EscrowFlightAuthorityTest.launch(before, 1);
            assertEquals(EscrowFlightWrites.Admission.QUEUED, coordinator.enqueueFlightWrite(first.logical(),
                    (root, generation) -> EscrowFlightAuthority.arm(root, first, generation), () -> true, replies::add));
            assertEquals(EscrowFlightWrites.Admission.LOGICAL_PENDING, coordinator.enqueueFlightWrite(first.logical(),
                    (root, generation) -> { fail("Duplicate captured"); return root; }, () -> true, ignored -> fail("Duplicate callback")));
            coordinator.tick(0); // One active flight counts toward the same 64-request bound.
            for (int i = 2; i <= 64; i++) {
                assertEquals(EscrowFlightWrites.Admission.QUEUED, coordinator.enqueueFlightWrite(EscrowTestFixtures.id(200 + i),
                        (root, generation) -> { fail("Invalid proof captured"); return root; }, () -> false, replies::add));
            }
            assertEquals(EscrowFlightWrites.Admission.CAPACITY, coordinator.enqueueFlightWrite(EscrowTestFixtures.id(999),
                    (root, generation) -> { fail("Overflow captured"); return root; }, () -> true, ignored -> fail("Overflow callback")));
            assertSame(before, coordinator.ledger()); assertTrue(replies.isEmpty());
            io.awaitWrites(); coordinator.tick(1); coordinator.tick(10);
            assertEquals(64, replies.size()); assertTrue(replies.get(0));
            assertEquals(1, replies.stream().filter(Boolean::booleanValue).count());
            assertEquals(List.of(first.logical()), coordinator.ledger().armedFlights().stream().map(EscrowFlightTables.Armed::logical).toList());
        }
    }

    @Test void closedQueueReturnsAnExplicitRefusalWithoutCapturingOrCallingBack() {
        var queue = new EscrowFlightWrites(); queue.close();
        assertEquals(EscrowFlightWrites.Admission.CLOSED, queue.request(EscrowTestFixtures.id(1),
                (root, generation) -> { fail("Closed capture"); return root; }, () -> true, ignored -> fail("Closed callback")));
        assertFalse(queue.pending()); assertFalse(queue.locked(EscrowTestFixtures.id(1)));
    }

    @Test void closedCoordinatorReturnsAnExplicitRefusalWithoutMutation() throws Exception {
        var data = ResourceEscrowSavedData.create(); var before = data.ledger();
        try (var writer = new EscrowLedgerWriter(directory.resolve(ManagedSavedDataType.RESOURCE_ESCROW.fileName()), 0);
             var coordinator = new EscrowCoordinator(data, writer)) {
            coordinator.close();
            assertEquals(EscrowFlightWrites.Admission.CLOSED, coordinator.enqueueFlightWrite(EscrowTestFixtures.id(1),
                    (root, generation) -> { fail("Closed capture"); return root; }, () -> true, ignored -> fail("Closed callback")));
            assertSame(before, coordinator.ledger()); assertTrue(writer.idle());
        }
    }

    @Test void unavailableWriterIsNotReportedAsOrdinaryQueueContention() throws Exception {
        var root = ResourceEscrowSavedData.create().ledger().generation(1);
        var data = ResourceEscrowSavedData.load(EscrowCodec.encode(root));
        try (var writer = new EscrowLedgerWriter(directory.resolve(ManagedSavedDataType.RESOURCE_ESCROW.fileName()), 1);
             var coordinator = new EscrowCoordinator(data, writer)) {
            assertFalse(writer.baseline(root)); // Generation 1 has no checked file in this fixture.
            assertEquals(EscrowFlightWrites.Admission.WRITER_UNAVAILABLE, coordinator.enqueueFlightWrite(EscrowTestFixtures.id(1),
                    (candidate, generation) -> { fail("Unavailable capture"); return candidate; }, () -> true,
                    ignored -> fail("Unavailable callback")));
            assertSame(data.ledger(), coordinator.ledger()); assertTrue(writer.idle());
        }
    }
}
