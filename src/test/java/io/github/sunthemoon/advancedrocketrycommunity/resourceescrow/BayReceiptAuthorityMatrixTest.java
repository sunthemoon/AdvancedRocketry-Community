package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.BayReceiptFixtures.receipt;
import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.LongStream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * W27c1 A0, W27A-DESIGN section 9's F3 matrix: W11b3's ledger coordinator and the bay receipt store on the very same
 * authority instance, each through its real delegate.
 */
final class BayReceiptAuthorityMatrixTest {
    @TempDir Path temp;
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    private static final UUID OWNER = BayReceiptFixtures.id(9);
    private static final EscrowAdmissionAuthority.Consumer BAY = EscrowAdmissionAuthority.Consumer.BAY;
    private static final EscrowAdmissionAuthority.Consumer PROPULSION = EscrowAdmissionAuthority.Consumer.PROPULSION;

    /** One server's authority with both delegates, as {@code EscrowRuntime} creates it. */
    private final class Server implements AutoCloseable {
        final EscrowAdmissionAuthority authority = new EscrowAdmissionAuthority();
        final EscrowAdmissionAuthority.Delegate propulsion = authority.register(PROPULSION);
        final BayReceiptHarness bay;
        EscrowLedgerWriter ledgerWriter; EscrowCoordinator coordinator;
        Server(String name) throws Exception { bay = new BayReceiptHarness(authority, temp.resolve(name)); }
        /** ServerStartingEvent: the ledger binds, then the receipt file loads or is proven absent. */
        Server start(EscrowLedger ledger) throws Exception {
            ledgerWriter = new EscrowLedgerWriter(Files.createDirectories(bay.file.getParent()).resolve(ManagedSavedDataType.RESOURCE_ESCROW.fileName()), 0);
            coordinator = new EscrowCoordinator(ResourceEscrowSavedData.load(EscrowCodec.encode(ledger)), ledgerWriter, propulsion);
            var payload = CheckedSavedDataFile.readPayload(bay.file, ManagedSavedDataType.BAY_RECEIPTS);
            bay.receipts.start(payload.map(BayReceiptSavedData::load).orElse(null), CheckedSavedDataFile.presence(bay.file), bay.file, 0);
            if (payload.isEmpty()) { bay.receipts.createForAdmission(bay.file, 0); }
            return this;
        }
        int pending() { return authority.pendingCount(); }
        EscrowAdmissionAuthority.Result fill(EscrowOperation op) { return authority.checkReserve(op.id(), op.owner(), op.guard().rocket(), PROPULSION); }
        @Override public void close() { bay.close(); coordinator.close(); ledgerWriter.close(); }
    }
    /** Durable fills, one rocket each, so every destination chain is contiguous. */
    private static EscrowLedger ledger(long count) {
        return EscrowTestFixtures.ledger(LongStream.rangeClosed(1, count)
                .mapToObj(n -> EscrowTestFixtures.operation(n, 1_000 + n, 0)).toArray(EscrowOperation[]::new));
    }
    private static BayReceipt intent(long n, UUID rocket) { return receipt(n, OWNER, rocket, BayReceipt.Phase.INTENT); }
    private static void queued(BayReceiptStore.Admission admission) { assertTrue(admission.queued(), admission::toString); }

    @Test void sixtyThreeOfOneConsumerPlusOneOfTheOtherFillTheSameSlotsAndBothRefuseTheNext() throws Exception {
        try (var server = new Server("escrow-major").start(ledger(63))) {
            assertEquals(63, server.pending());
            queued(server.bay.put(intent(1, BayReceiptFixtures.id(500))));
            assertEquals(64, server.pending());
            var secondBay = server.bay.put(intent(2, BayReceiptFixtures.id(501)));
            assertEquals(EscrowAdmissionAuthority.Result.SERVER_FULL, secondBay.reservation());
            var loader = EscrowTestFixtures.operation(64, 2_000, 0);
            assertEquals(EscrowAdmissionAuthority.Result.SERVER_FULL, server.fill(loader));
            assertThrows(IllegalArgumentException.class, () -> server.coordinator.prepareTest(loader, ignored -> fail()));
            assertEquals(64, server.pending()); assertEquals(64, server.coordinator.nextSequence());
            assertEquals(1, server.bay.store().receipts().size(), "refusals change nothing");
            server.bay.settle(); assertEquals(64, server.pending());
        }
        try (var server = new Server("bay-major").start(EscrowTestFixtures.ledger())) {
            for (long n = 1; n <= 63; n++) { queued(server.bay.put(intent(n, BayReceiptFixtures.id(600 + n / 8)))); }
            var replies = new ArrayList<Boolean>();
            server.coordinator.prepareTest(EscrowTestFixtures.operation(1, 2_000, 0), replies::add);
            assertEquals(64, server.pending());
            var refused = assertThrows(IllegalArgumentException.class,
                    () -> server.coordinator.prepareTest(EscrowTestFixtures.operation(2, 2_001, 0), ignored -> fail()));
            assertEquals("Shared admission refused: SERVER_FULL", refused.getMessage());
            assertEquals(EscrowAdmissionAuthority.Result.SERVER_FULL, server.bay.put(intent(64, BayReceiptFixtures.id(700))).reservation());
            assertEquals(64, server.pending());
        }
    }

    @Test void oneBayOperationInEveryWriterStateStillCostsOneOfTheSharedSlots() throws Exception {
        try (var server = new Server("states").start(ledger(63))) {
            var x = intent(1, BayReceiptFixtures.id(500)); queued(server.bay.put(x)); server.bay.settle();
            server.bay.committer.set(BayReceiptHarness.AMBIGUOUS);
            queued(server.bay.put(x.withPhase(BayReceipt.Phase.REPAIR, "BAY_CONFLICT", 3)));
            server.bay.until(server.bay.store().writer()::unknown);
            queued(server.bay.put(x.withPhase(BayReceipt.Phase.REPAIR, "BAY_CONFLICT", 4).withReleased()));
            assertTrue(server.bay.store().writer().hasSuccessor());
            assertEquals(64, server.pending(), "durable, captured, UNKNOWN and successor copies cost one slot");
            assertEquals(EscrowAdmissionAuthority.Result.ALREADY_HELD, server.bay.reserve(x));
            assertEquals(EscrowAdmissionAuthority.Result.SERVER_FULL, server.fill(EscrowTestFixtures.operation(64, 2_000, 0)));
            server.bay.committer.set(CheckedSavedDataFile::atomicMove); server.bay.settle();
            assertEquals(64, server.pending(), "a released REPAIR receipt keeps its slot");
        }
    }

    @Test void allSixtyFourInRepairWithReleasedFlightWaitsStayFullAcrossARestart() throws Exception {
        var repair = EscrowTestFixtures.operation(1, 7, 0).withReason(EscrowReason.OTHER_STATE);
        assertEquals(EscrowOperation.Phase.REPAIR, repair.phase());
        try (var server = new Server("restart").start(EscrowTestFixtures.ledger(repair))) {
            for (long n = 1; n <= 63; n++) {
                var receipt = intent(n, BayReceiptFixtures.id(800 + n / 8)).withPhase(BayReceipt.Phase.REPAIR, "REGISTRY_SIGNATURE_CONFLICT", 2);
                queued(server.bay.put(n % 2 == 0 ? receipt.withReleased() : receipt));
            }
            server.bay.settle(); assertEquals(64, server.pending());
        }
        try (var restarted = new Server("restart").start(EscrowTestFixtures.ledger(repair))) {
            assertEquals(EscrowAdmissionAuthority.Delegate.State.LOADED, restarted.bay.receipts.state());
            assertEquals(63, restarted.bay.store().durable().receipts().size());
            assertEquals(64, restarted.pending(), "indexed at startup, before any tick or admission");
            assertEquals(EscrowAdmissionAuthority.Result.SERVER_FULL, restarted.bay.reserve(intent(100, BayReceiptFixtures.id(900))));
            assertEquals(EscrowAdmissionAuthority.Result.SERVER_FULL, restarted.fill(EscrowTestFixtures.operation(2, 2_000, 0)));
        }
    }

    @Test void theRejectedReArmSequenceKeepsItsOperationAndWaitsForAnExactResolution() throws Exception {
        try (var server = new Server("re-arm").start(ledger(63))) {
            var x = intent(1, BayReceiptFixtures.id(500)); queued(server.bay.put(x)); server.bay.settle();
            var rejected = x.withPhase(BayReceipt.Phase.REJECTED, "LAUNCH_REFUSED", 3); queued(server.bay.put(rejected));
            assertEquals(64, server.pending()); server.bay.settle();
            assertEquals(63, server.pending(), "the exactly acknowledged refusal frees the pending slot");
            var y = intent(2, BayReceiptFixtures.id(501)); queued(server.bay.put(y)); server.bay.settle();
            assertEquals(64, server.pending(), "another bay reuses the freed slot");
            var reArm = rejected.withKey(BayReceiptFixtures.id(502), new RocketPosition(0, 2, 0)).withPhase(BayReceipt.Phase.INTENT, "NONE", 4);
            assertEquals(EscrowAdmissionAuthority.Result.SERVER_FULL, server.bay.put(reArm).reservation());
            assertEquals(List.of(rejected, y), server.bay.store().durable().receipts());
            assertEquals(List.of(rejected, y), server.bay.store().receipts(), "op, key and package unchanged");
            queued(server.bay.put(y.withPhase(BayReceipt.Phase.REJECTED, "INTERRUPTED", 5)));
            assertEquals(EscrowAdmissionAuthority.Result.SERVER_FULL, server.bay.reserve(reArm), "no release before the exact acknowledgement");
            server.bay.settle();
            queued(server.bay.put(reArm)); server.bay.settle();
            assertEquals(reArm.op(), x.op()); assertEquals(64, server.pending());
            assertEquals(1, server.authority.pendingFor(BayReceiptFixtures.id(502)));
        }
    }

    @Test void sevenFillsAndOneBayShareTheEightPerRocketCount() throws Exception {
        // One contiguous destination chain on the rocket: each fill starts at the previous fill's epoch.
        var ops = LongStream.rangeClosed(1, 7).mapToObj(n -> EscrowTestFixtures.operation(n, 3_000, n - 1)).toArray(EscrowOperation[]::new);
        try (var server = new Server("rocket").start(EscrowTestFixtures.ledger(ops))) {
            UUID rocket = ops[0].guard().rocket();
            queued(server.bay.put(intent(1, rocket)));
            assertEquals(8, server.authority.pendingFor(rocket));
            assertEquals(EscrowAdmissionAuthority.Result.ROCKET_FULL, server.bay.put(intent(2, rocket)).reservation());
            assertEquals(EscrowAdmissionAuthority.Result.ROCKET_FULL, server.fill(EscrowTestFixtures.operation(8, 3_000, 7)));
            queued(server.bay.put(intent(2, BayReceiptFixtures.id(501))));
            assertEquals(EscrowAdmissionAuthority.Result.RESERVED, server.fill(EscrowTestFixtures.operation(8, 3_001, 0)));
        }
    }

    @Test void bothStoresLoadFromRealFilesBeforeTheFirstAdmission() throws Exception {
        try (var writer = new Server("mixed").start(EscrowTestFixtures.ledger())) {
            for (long n = 1; n <= 5; n++) { queued(writer.bay.put(intent(n, BayReceiptFixtures.id(500 + n)))); }
            for (long n = 6; n <= 8; n++) { queued(writer.bay.put(receipt(n, OWNER, BayReceiptFixtures.id(500 + n), BayReceipt.Phase.REJECTED))); }
            writer.bay.settle();
        }
        try (var server = new Server("mixed").start(ledger(10))) {
            // No tick, no writer completion and no admission has run yet.
            assertEquals(15, server.pending()); assertEquals(8, server.authority.retainedCount());
            assertEquals(8, server.authority.retainedFor(OWNER), "REJECTED receipts are retained but not pending");
            assertTrue(server.bay.receipts.blocksInsertion(BayReceiptFixtures.id(200_001)));
            assertFalse(server.bay.receipts.blocksInsertion(BayReceiptFixtures.id(200_006)));
            assertEquals(EscrowAdmissionAuthority.Result.ALREADY_HELD, server.fill(EscrowTestFixtures.operation(3, 1_003, 0)));
        }
    }
}
