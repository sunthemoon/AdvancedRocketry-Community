package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNbtSize;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** R8/R9: occupied table slots cannot lend retirement headroom to new admissions. */
class EscrowFlightTableReservationTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    @TempDir Path directory;

    @Test void terminalCleanupPreservesAcknowledgedFloorRetirementAtCapacity() throws Exception {
        var base = EscrowCombinedReservationTest.retainedFloors(29);
        var data = ResourceEscrowSavedData.load(EscrowCodec.encode(base));
        var file = directory.resolve(ManagedSavedDataType.RESOURCE_ESCROW.fileName());
        var io = new AwaitedLedgerIo();
        try (var writer = io.writer(file, 0, CheckedSavedDataFile::atomicMove);
                var coordinator = new EscrowCoordinator(data, writer)) {
            var results = new ArrayList<Boolean>();
            // A0 retained-proof/lease stubs exercise table accounting and the real checked
            // writer. These are not native endpoint, F1 checkpoint or F6 proof observations.
            for (int i = 1; i <= 64; i++) {
                var launch = EscrowFlightAuthorityTest.launch(base, i);
                assertTrue(coordinator.flightWrite(launch.logical(), (root, generation) -> EscrowFlightAuthority.arm(root, launch, generation),
                        () -> true, results::add));
            }
            coordinator.tick(0); acknowledge(io, coordinator, file, 1, 1);
            committed(results, 64); results.clear();
            for (var arm : coordinator.ledger().armedFlights()) {
                assertTrue(coordinator.flightWrite(arm.logical(), (root, generation) -> EscrowFlightAuthority.finish(root, arm,
                        EscrowFlightTables.Outcome.CANCELLED, arm.source(), arm.epoch(), EscrowTestFixtures.hash(2), EscrowTestFixtures.hash(3)),
                        () -> true, results::add));
            }
            coordinator.tick(10); acknowledge(io, coordinator, file, 2, 11);
            committed(results, 64); results.clear();
            assertTrue(coordinator.ledger().armedFlights().stream().allMatch(arm -> arm.outcome() == EscrowFlightTables.Outcome.CANCELLED));

            for (int i = 30; i <= 89; i++) { coordinator.prepareTest(EscrowTestFixtures.operation(i), results::add); }
            assertThrows(IllegalArgumentException.class,
                    () -> coordinator.prepareTest(EscrowTestFixtures.operation(90), ignored -> fail()));
            coordinator.tick(20); assertTrue(coordinator.commitPreparations());
            acknowledge(io, coordinator, file, 3, 21); committed(results, 60); results.clear();
            var before = coordinator.ledger(); var intent = floorIntent(before);
            assertEquals(60, before.records().size()); assertEquals(64, before.armedFlights().size());
            var retirementBefore = assertDoesNotThrow(() -> EscrowSettlementCapacity.floorRetirement(before, intent, coordinator.nextSequence(), 220));
            // An independently assembled all-ceiling total includes root framing once.
            assertEquals(bytes(EscrowTestFixtures.ledger()) + 128 * 1_024L + 29 * 1_024L + 60 * 14_336L, reserved(before));

            for (var arm : before.armedFlights()) {
                assertTrue(coordinator.flightWrite(arm.logical(), (root, generation) -> EscrowFlightAuthority.prune(root, arm),
                        () -> true, results::add));
            }
            coordinator.tick(30); acknowledge(io, coordinator, file, 4, 31);
            committed(results, 64); results.clear();
            var after = coordinator.ledger();
            assertDoesNotThrow(() -> EscrowSettlementCapacity.preflight(after));
            assertEquals(before.records(), after.records()); assertEquals(before.floors(), after.floors());
            assertEquals(before.tombstones(), after.tombstones()); assertTrue(after.armedFlights().isEmpty());
            assertEquals(before.issuedLaunchSerial(), after.issuedLaunchSerial());
            assertTrue(bytes(after) < bytes(before), "Cleanup shrinks the actual encoded root");
            assertEquals(reserved(before), reserved(after));
            var retirementAfter = after.append(List.of(intent.floorOperation(coordinator.nextSequence(), 220)));
            assertEquals(reserved(retirementBefore), reserved(retirementAfter));
            assertTrue(reserved(retirementAfter) <= EscrowLimits.ROOT_BYTES);

            coordinator.tick(220);
            assertTrue(coordinator.beginSettlement(intent, results::add));
            assertTrue(results.isEmpty()); assertEquals(after, coordinator.ledger());
            acknowledge(io, coordinator, file, 5, 221); committed(results, 1);
            assertEquals(retirementAfter.records(), coordinator.ledger().records());
            assertEquals(after.floors(), coordinator.ledger().floors());
            assertEquals(after.tombstones(), coordinator.ledger().tombstones());
            assertEquals(after.issuedLaunchSerial(), coordinator.ledger().issuedLaunchSerial());
            assertFalse(data.isDirty()); assertEquals(61, coordinator.admissionAuthority().pendingCount());
            assertTrue(writer.idle()); assertTrue(writer.capturedOperations().isEmpty());
            System.out.println("table-cleanup reservation fills=60 floors=29 terminal_entries=64 before=" + reserved(before)
                    + " retirement_before=" + reserved(retirementBefore) + " after=" + reserved(after)
                    + " retirement_after=" + reserved(retirementAfter) + " increase=" + (reserved(after) - reserved(before)));
        }
    }

    private static void acknowledge(AwaitedLedgerIo io, EscrowCoordinator coordinator, Path file, long generation, long tick) throws Exception {
        io.awaitWrites(); coordinator.tick(tick); assertEquals(generation, coordinator.ledger().writerGeneration());
        assertEquals(coordinator.ledger(), EscrowCodec.decode(CheckedSavedDataFile.readPayload(file, ManagedSavedDataType.RESOURCE_ESCROW).orElseThrow()));
    }
    private static void committed(List<Boolean> results, int count) {
        assertEquals(java.util.Collections.nCopies(count, true), results);
    }
    private static EscrowSettlementIntent floorIntent(EscrowLedger root) {
        var floor = root.floors().get(0); var original = EscrowTestFixtures.operation(1, 10_001, 0).destination();
        var endpoint = new EscrowEndpoint(original.kind(), original.level(), original.identity(), original.logicalRocket(), original.type(),
                original.x(), original.y(), original.z(), original.unit(), floor.getLong("resource_epoch"), floor.getString("signature"), floor.getString("signature"));
        var observations = List.of(new EscrowSettlementIntent.Observation(endpoint, null, false, 0));
        var preview = EscrowSettlementIntent.floorPreview(root, EscrowTestFixtures.id(30_000), floor,
                EscrowTestFixtures.id(700), "operator", 20, observations);
        return preview.confirm(preview.actor(), preview.digest(), 220, observations);
    }
    private static long reserved(EscrowLedger root) { return EscrowSettlementCapacity.reservedBytes(root); }
    private static int bytes(EscrowLedger root) { return RocketNbtSize.uncompressedBytes(EscrowCodec.encode(root)); }
}
