package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class RocketEscrowAuditTest {
    @TempDir Path temp;
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    private static RocketEscrowAudit.Placement at(String level, int cx, double x, double y, boolean occupied) {
        var endpoint = EscrowTestFixtures.operation(1).destination();
        return new RocketEscrowAudit.Placement(endpoint.identity(), endpoint.logicalRocket(), endpoint.type(), level, cx, 0,
                x, y, .5D, .5D, 64D, .5D, endpoint.level(), occupied);
    }

    @Test void exactLoadedPlacementHasAControlAndEachBypassIsDetected() {
        var endpoint = EscrowTestFixtures.operation(1).destination();
        assertEquals(EscrowReason.NONE, RocketEscrowAudit.placement(endpoint, at(endpoint.level(), 0, .5D, 64D, false)));
        for (var observation : List.of(at("minecraft:the_nether", 0, .5D, 64D, false),
                at(endpoint.level(), 1, 16.5D, 64D, false), at(endpoint.level(), 0, 1D, 64D, false),
                at(endpoint.level(), 0, .5D, 65D, false), at(endpoint.level(), 0, .5D, 64D, true),
                at(endpoint.level(), 0, Double.NaN, 64D, false))) {
            assertEquals(EscrowReason.RELOCATED_ENDPOINT, RocketEscrowAudit.placement(endpoint, observation));
        }
        var valid = at(endpoint.level(), 0, .5D, 64D, false);
        assertEquals(EscrowReason.INVALID_ENDPOINT, RocketEscrowAudit.placement(endpoint,
                new RocketEscrowAudit.Placement(UUID.randomUUID(), valid.logical(), valid.type(), valid.level(), 0, 0,
                        .5D, 64D, .5D, .5D, 64D, .5D, valid.level(), false)));
    }

    @Test void noneGuardsIgnoreUnrelatedRootChangesButNotANewTransfer() {
        var guard = EscrowTestFixtures.operation(1).guard();
        assertEquals(EscrowReason.TRANSFER_JOURNAL_UNPROVED, RocketEscrowAudit.journal(guard, null));
        assertEquals(EscrowReason.NONE, RocketEscrowAudit.journal(guard,
                new RocketTransferSavedData.JournalGuard(guard.rocket(), null, null, EscrowTestFixtures.hash(900), 99)));
        assertEquals(EscrowReason.JOURNAL_GUARD_MISMATCH, RocketEscrowAudit.journal(guard,
                new RocketTransferSavedData.JournalGuard(guard.rocket(), UUID.randomUUID(), guard.journalDigest(), guard.journalDigest(), 1)));
    }

    @Test void namedGuardRequiresTheExactRecordDigestNotASequenceFromAnotherSession() {
        var guard = new EscrowGuard(EscrowTestFixtures.id(1), EscrowTestFixtures.id(2), 99, EscrowTestFixtures.hash(3));
        assertEquals(EscrowReason.NONE, RocketEscrowAudit.journal(guard,
                new RocketTransferSavedData.JournalGuard(guard.rocket(), guard.transfer(), guard.journalDigest(), EscrowTestFixtures.hash(4), 1)));
        assertEquals(EscrowReason.JOURNAL_GUARD_MISMATCH, RocketEscrowAudit.journal(guard,
                new RocketTransferSavedData.JournalGuard(guard.rocket(), guard.transfer(), EscrowTestFixtures.hash(5), guard.journalDigest(), 99)));
    }

    @Test void checkedManualTransitionSurvivesTwoFileReloadsAndCannotAutoClear() throws Exception {
        for (var reason : List.of(EscrowReason.RELOCATED_ENDPOINT, EscrowReason.JOURNAL_GUARD_MISMATCH)) {
            var initial = EscrowTestFixtures.ledger(EscrowTestFixtures.operation(1));
            var data = ResourceEscrowSavedData.load(EscrowCodec.encode(initial));
            Path directory = java.nio.file.Files.createDirectory(temp.resolve(reason.name()));
            Path file = directory.resolve(ManagedSavedDataType.RESOURCE_ESCROW.fileName());
            try (var writer = new EscrowLedgerWriter(file, 0); var coordinator = new EscrowCoordinator(data, writer)) {
                coordinator.transition(initial.records().get(0).id(), reason); coordinator.persistTransitions();
                assertTrue(coordinator.logicalLocked(initial.records().get(0).guard().rocket()));
                assertFalse(coordinator.retire(java.util.Set.of(initial.records().get(0).id()), java.util.Map.of(),
                        result -> fail("Manual repair cannot use ordinary L2")));
                assertThrows(IllegalArgumentException.class, () -> coordinator.transition(initial.records().get(0).id(), EscrowReason.NONE));
                long deadline = System.nanoTime() + 5_000_000_000L; int tick = 1;
                while (!writer.idle() && System.nanoTime() < deadline) { coordinator.tick(tick++); Thread.sleep(5); }
                assertTrue(writer.idle()); assertFalse(data.isDirty());
                for (int restart = 0; restart < 2; restart++) {
                    var restored = ResourceEscrowSavedData.load(CheckedSavedDataFile.readPayload(file, ManagedSavedDataType.RESOURCE_ESCROW).orElseThrow());
                    var op = restored.ledger().records().get(0);
                    assertEquals(EscrowOperation.Phase.REPAIR, op.phase()); assertEquals(reason, op.reason());
                    assertThrows(IllegalArgumentException.class, () -> op.withReason(EscrowReason.NONE));
                }
            }
        }
    }

    @Test void failedRepairWriteRetainsAll64LockedOperationsUntilAnExactRetry() throws Exception {
        var records = java.util.stream.LongStream.rangeClosed(1, EscrowLimits.OPERATIONS)
                .mapToObj(EscrowTestFixtures::operation).toList();
        var initial = new EscrowLedger(EscrowTestFixtures.id(500), EscrowLimits.OPERATIONS, 0, records, List.of(), List.of());
        var data = ResourceEscrowSavedData.load(EscrowCodec.encode(initial));
        Path file = temp.resolve(ManagedSavedDataType.RESOURCE_ESCROW.fileName());
        var failed = new java.util.concurrent.atomic.AtomicBoolean(true);
        try (var writer = new EscrowLedgerWriter(file, 0, (staged, target) -> {
            if (failed.get()) { throw new java.io.IOException("Injected repair write failure"); }
            CheckedSavedDataFile.atomicMove(staged, target);
        }); var coordinator = new EscrowCoordinator(data, writer)) {
            records.forEach(op -> coordinator.transition(op.id(), EscrowReason.RELOCATED_ENDPOINT));
            coordinator.persistTransitions();
            long deadline = System.nanoTime() + 5_000_000_000L; int tick = 1;
            try {
                while (!writer.unavailable() && System.nanoTime() < deadline) { coordinator.tick(tick++); Thread.sleep(5); }
                assertTrue(writer.unavailable()); assertTrue(data.isDirty());
                records.forEach(op -> {
                    assertEquals(EscrowReason.RELOCATED_ENDPOINT, coordinator.currentReason(op));
                    assertTrue(coordinator.logicalLocked(op.guard().rocket()));
                });
                assertEquals(EscrowLimits.OPERATIONS, data.ledger().records().size());
            } finally {
                failed.set(false); long retryDeadline = System.nanoTime() + 5_000_000_000L;
                while (!writer.idle() && System.nanoTime() < retryDeadline) { coordinator.tick(tick++); Thread.sleep(5); }
            }
            assertTrue(writer.idle());
            var disk = EscrowCodec.decode(CheckedSavedDataFile.readPayload(file, ManagedSavedDataType.RESOURCE_ESCROW).orElseThrow());
            assertEquals(EscrowLimits.OPERATIONS, disk.records().size());
            assertTrue(disk.records().stream().allMatch(op -> op.reason() == EscrowReason.RELOCATED_ENDPOINT));
        }
    }
}
