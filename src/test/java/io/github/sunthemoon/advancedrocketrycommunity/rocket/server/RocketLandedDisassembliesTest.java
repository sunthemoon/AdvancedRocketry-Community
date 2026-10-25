package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataPolicy;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightPlan;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightPlanner;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFuelState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketLegacyTransferFixture;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketPassengerManifest;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferPhase;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketJournalScheduleFixtures;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketJournalTestAccess;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlock;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlockState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketLandedDisassemblyRules.Presence;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketLandedDisassemblyRules.Stage;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketStats;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketDisassemblyTransaction;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketFailureInjector;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketFailurePoint;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketOperationLedger;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketRegion;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketRegionLockManager;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketTransactionAbortException;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketTransactionJournal;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketTransactionResult;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketTransactionWorld;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketWorldBlock;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.validation.RocketValidationCode;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.GovernedTransferFixture;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.UnaryOperator;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * W85 section 7 A0: P's stages and the removal and write-back continuations through a detached journal, with the
 * real v0.5 transaction on a fake world. The latch is unset before {@code execute} and set from then on.
 */
@Timeout(60)
class RocketLandedDisassembliesTest {
    private static final ManagedSavedDataType TYPE = ManagedSavedDataType.ROCKET_TRANSFERS;
    private static final RocketPosition EARTH_ORIGIN = new RocketPosition(12, 72, 12);
    @TempDir Path directory;
    private long tick;

    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    private Path file() { return directory.resolve(TYPE.fileName()); }

    @Test void proofHandoffDoesNotRefreshTheOriginalRequestAge() {
        var f = new Fixture(new RocketPosition(64, 80, 64));
        f.request.requestedAt = 10; f.port.now = 211;
        f.release(); f.acknowledge();
        assertEquals(0, f.request.restores, "M1 must retain M0's request age");
        assertSame(f.landed, f.journal.find(f.landed.transferId()).orElseThrow());
        f.acknowledge(); f.close();
    }

    @Test void asynchronousRestoreKeepsPendingUntilItsCompletionAndIgnoresLateCallbacks() {
        var f = new Fixture(new RocketPosition(64, 80, 64)); f.request.deferred = true;
        assertEquals(RocketValidationCode.DISASSEMBLY_PENDING, f.release()); f.acknowledge();
        assertTrue(f.disassemblies.pending(f.logical()), "M2 must wait for the retained executor");
        assertTrue(f.request.reports.isEmpty()); assertTrue(f.world.blocks.isEmpty());
        var callback = f.request.completion;
        assertNotNull(callback);
        var result = f.request.restore(); callback.accept(result); callback.accept(result);
        assertEquals(List.of("SUCCESS"), f.request.reports);
        assertFalse(f.disassemblies.pending(f.logical())); assertTrue(f.journal.entries().isEmpty()); f.close();
    }

    @Test void ramRollbackCountsWithoutNativeCertificationCannotWriteTBack() {
        var f = new Fixture(new RocketPosition(64, 80, 64)); f.request.certified = false;
        f.request.injector = (point, progress) -> {
            if (point == RocketFailurePoint.DURING_RESTORATION && progress == 2) {
                throw new RocketTransactionAbortException(RocketValidationCode.EXTRACTION_FAILED, "RAM rollback only");
            }
        };
        f.release(); f.acknowledge();
        assertTrue(f.world.blocks.isEmpty()); assertTrue(f.world.rockets.containsKey(f.entity));
        assertTrue(f.journal.find(f.landed.transferId()).isEmpty(), "RAM rollback is not M3 permission");
        assertFalse(f.disassemblies.pending(f.logical())); f.close();
    }

    @Test void successRestoresOnceAfterTheRemovalIsAcknowledgedAndNeverWritesBack() {
        var f = new Fixture(new RocketPosition(64, 80, 64));
        assertEquals(RocketValidationCode.DISASSEMBLY_PENDING, f.release());
        assertTrue(f.journal.find(f.landed.transferId()).isEmpty() && f.landed.equals(f.held()));
        assertEquals(1, f.disassemblies.heldSlots(f.journal));
        assertEquals(0, f.request.restores, "No block before the removal's acknowledgement");
        f.acknowledge();
        assertEquals(1, f.request.restores);
        assertEquals(List.of("SUCCESS"), f.request.reports);
        assertEquals(List.of("released"), f.port.events);
        assertFalse(f.disassemblies.pending(f.logical()));
        assertTrue(f.journal.find(f.landed.transferId()).isEmpty());
        assertTrue(f.world.rockets.isEmpty() && f.world.blocks.size() == f.snapshot().blocks().size());
        assertTrue(f.diskRecords().isEmpty());
        f.close();
    }

    @Test void cleanFailuresWriteTheIdenticalRecordBackAndHoldEverythingUntilItIsAcknowledged() {
        for (int mode = 0; mode < 3; mode++) {
            var f = new Fixture(new RocketPosition(64 + mode * 64, 80, 64));
            if (mode == 0) {
                f.world.blocks.put(f.absolute(1), new RocketWorldBlock(state("minecraft:stone"), null)); // 0 of 0
            } else if (mode == 1) {
                f.request.injector = (point, progress) -> {
                    if (point == RocketFailurePoint.DURING_RESTORATION && progress == 2) {
                        throw new RocketTransactionAbortException(RocketValidationCode.EXTRACTION_FAILED, "n of n");
                    }
                };
            } else {
                f.port.now = 10_000; // M2 runs after the confirmation lifetime: no restoration at all.
            }
            assertEquals(RocketValidationCode.DISASSEMBLY_PENDING, f.release());
            if (mode == 2) { f.port.now = 10_000 + RocketDisassemblyConfirmations.LIFETIME_TICKS + 1; }
            f.acknowledge();
            var pending = f.disassemblies.find(f.logical());
            assertNotNull(pending, "P keeps the rocket locked until the write-back is acknowledged");
            assertEquals(Stage.WRITE_BACK, pending.stage());
            assertSame(f.landed, f.journal.find(f.landed.transferId()).orElseThrow(), "The identical record");
            assertEquals(0, f.disassemblies.heldSlots(f.journal), "Not counted a second time in WRITE_BACK");
            assertTrue(f.disassemblies.reservations(f.journal).isEmpty());
            assertEquals(List.of(mode == 0 ? "TARGET_OCCUPIED" : mode == 1 ? "EXTRACTION_FAILED" : "REGION_BUSY"),
                    f.request.reports);
            assertEquals(mode == 2 ? 0 : 1, f.request.restores);
            assertTrue(f.world.rockets.containsKey(f.entity), "E survives a clean failure");
            assertTrue(f.journal.resourceMutationUnproved(f.logical()));
            f.acknowledge();
            assertFalse(f.disassemblies.pending(f.logical()));
            assertEquals(List.of("released", "restored settle=true"), f.port.events);
            assertEquals(List.of(f.landed.transferId()), f.diskRecords());
            assertFalse(f.journal.resourceMutationUnproved(f.logical()));
            f.close();
        }
    }

    @Test void aPendingTransactionRecoveryAtTheAcknowledgementWritesBackWithoutRestoring() {
        // W92-R2: a v0.5 record overlapping E (or a v0.5 journal that is not operational) appears between M0 and M2.
        for (int mode = 0; mode < 2; mode++) {
            var f = new Fixture(new RocketPosition(64 + mode * 64, 80, 64));
            assertEquals(RocketValidationCode.DISASSEMBLY_PENDING, f.release());
            if (mode == 0) { f.request.restorable = false; }
            if (mode == 1) { f.request.restorableThrows = new IllegalStateException("v0.5 journal unavailable"); }
            f.acknowledge();
            assertEquals(0, f.request.restores, "M2 restored over a pending v0.5 recovery");
            assertEquals(List.of("REGION_BUSY"), f.request.reports);
            assertEquals(Stage.WRITE_BACK, f.disassemblies.find(f.logical()).stage());
            assertSame(f.landed, f.journal.find(f.landed.transferId()).orElseThrow(), "The identical record");
            assertTrue(f.world.rockets.containsKey(f.entity) && f.world.blocks.isEmpty());
            f.acknowledge();
            assertFalse(f.disassemblies.pending(f.logical()));
            assertEquals(List.of("released", "restored settle=true"), f.port.events);
            assertEquals(List.of(f.landed.transferId()), f.diskRecords());
            f.close();
        }
    }

    @Test void aSettledSchemaOneRecordIsDisassembledInPlaceAndWrittenBackUnchanged() throws IOException {
        // W92-R4: the clause admits the schema-1 record; the governed record 3 stays refused.
        assertFalse(RocketLandedDisassemblyRules.landedRecordAdmitted(GovernedTransferFixture.create().record()
                .schemaVersion(), true));
        var clean = new Fixture(new RocketPosition(64, 80, 64), RocketLegacyTransferFixture::schemaOne);
        assertEquals(1, clean.landed.schemaVersion());
        assertTrue(RocketLandedDisassemblyRules.landedRecordAdmitted(clean.landed.schemaVersion(),
                clean.landed.nativeTransfer().isPresent()));
        CompoundTag before = clean.diskRecord();
        clean.world.blocks.put(clean.absolute(1), new RocketWorldBlock(state("minecraft:stone"), null)); // 0 of 0
        assertEquals(RocketValidationCode.DISASSEMBLY_PENDING, clean.release());
        clean.acknowledge();
        assertSame(clean.landed, clean.journal.find(clean.landed.transferId()).orElseThrow(), "The identical record");
        clean.acknowledge();
        assertFalse(clean.disassemblies.pending(clean.logical()));
        assertEquals(before, clean.diskRecord(), "The write-back is the byte-identical schema-1 record");
        assertEquals(1, before.getInt("schema_version"));
        clean.close();

        var success = new Fixture(new RocketPosition(128, 80, 64), RocketLegacyTransferFixture::schemaOne);
        assertEquals(RocketValidationCode.DISASSEMBLY_PENDING, success.release());
        success.acknowledge();
        assertEquals(List.of("SUCCESS"), success.request.reports);
        assertTrue(success.world.rockets.isEmpty() && success.diskRecords().isEmpty());
        success.close();
    }

    @Test void uncertainMutationNeverWritesTheRecordBack() {
        for (int mode = 0; mode < 3; mode++) {
            var f = new Fixture(new RocketPosition(64 + mode * 64, 80, 64));
            if (mode == 0) {
                f.world.failRemoveAt = f.absolute(0); // ROLLBACK_FAILED with unequal counts
                f.request.injector = (point, progress) -> {
                    if (point == RocketFailurePoint.DURING_RESTORATION && progress == 2) {
                        throw new RocketTransactionAbortException(RocketValidationCode.EXTRACTION_FAILED, "partial");
                    }
                };
            } else if (mode == 1) {
                f.request.restoreThrows = new IllegalStateException("thrown after the latch");
            } else {
                f.request.notifierThrows = true; // SUCCESS, then the notification throws
            }
            f.release();
            f.acknowledge();
            assertFalse(f.disassemblies.pending(f.logical()));
            assertTrue(f.journal.find(f.landed.transferId()).isEmpty(), "T never reappears after the latch");
            assertTrue(f.diskRecords().isEmpty());
            assertEquals(List.of(mode == 0 ? "ROLLBACK_FAILED" : mode == 1 ? "ROLLBACK_FAILED" : "SUCCESS"),
                    f.request.reports);
            f.close();
        }
    }

    @Test void theRemovalContinuationsFailureModesWithTheLatchUnsetAndSet() {
        // Unset: a refused action, a RuntimeException and an Error before execute all write T back.
        for (int mode = 0; mode < 3; mode++) {
            var f = new Fixture(new RocketPosition(64 + mode * 64, 80, 64));
            if (mode == 0) { f.port.wrap = action -> () -> false; }
            if (mode == 1) { f.request.matchesThrows = new IllegalStateException("before the latch"); }
            if (mode == 2) { f.request.matchesError = new AssertionError("Error before the latch"); }
            f.release();
            if (mode == 2) { assertThrows(AssertionError.class, f::acknowledge); } else { f.acknowledge(); }
            assertEquals(0, f.request.restores);
            assertSame(f.landed, f.journal.find(f.landed.transferId()).orElseThrow());
            assertEquals(Stage.WRITE_BACK, f.disassemblies.find(f.logical()).stage());
            f.request.matchesError = null;
            f.acknowledge();
            assertFalse(f.disassemblies.pending(f.logical()));
            assertEquals(List.of(f.landed.transferId()), f.diskRecords());
            f.close();
        }
        // Set: a refused action after a success, and an Error after the first placement, never write T back.
        for (int mode = 0; mode < 2; mode++) {
            var f = new Fixture(new RocketPosition(320 + mode * 64, 80, 64));
            if (mode == 0) { f.port.wrap = action -> () -> { action.getAsBoolean(); return false; }; }
            if (mode == 1) { f.world.errorAfterFirstPlacement = new AssertionError("Error after a placement"); }
            f.release();
            if (mode == 1) { assertThrows(AssertionError.class, f::acknowledge); } else { f.acknowledge(); }
            assertEquals(1, f.request.restores);
            assertFalse(f.disassemblies.pending(f.logical()));
            assertTrue(f.journal.find(f.landed.transferId()).isEmpty(), "T never reappears in RAM");
            f.world.errorAfterFirstPlacement = null;
            f.flushAndPoll();
            assertTrue(f.diskRecords().isEmpty(), "T never reappears in a capture");
            if (mode == 1) { assertEquals(1, f.world.blocks.size(), "The Error left a placed block behind"); }
            f.close();
        }
    }

    @Test void aLaterAcknowledgedGenerationAcknowledgesTheRemovalAndRestoresOnce() {
        // Root decision R28 (R23): a committed generation captured after T's removal lacks T, as the removal's own would,
        // so it acknowledges the removal. L's continuation runs once, restores, and T is never written back.
        var f = new Fixture(new RocketPosition(64, 80, 64));
        f.release();
        var failing = new CheckedSavedDataPolicy(TYPE, (staged, target) -> { throw new IOException("Injected removal failure"); });
        RocketJournalTestAccess.write(f.journal, file(), failing, tick += 1_000);
        assertEquals(RocketTransferSavedData.WriteOutcome.NOT_COMMITTED, f.journal.checkedWriteResult().outcome());
        f.disassemblies.reportUnacknowledged();
        f.disassemblies.reportUnacknowledged();
        assertEquals(0, f.request.restores, "The continuation ran after a failed write");
        assertEquals(Stage.RELEASED, f.disassemblies.find(f.logical()).stage());
        var other = landed(new RocketPosition(2_000, 80, 2_000));
        f.journal.put(other); // A disjoint successor generation.
        f.acknowledge();
        assertEquals(1, f.request.restores, "The acknowledged removal did not restore exactly once");
        assertTrue(f.journal.find(f.landed.transferId()).isEmpty(), "T was written back");
        assertEquals(List.of("SUCCESS"), f.request.reports);
        assertEquals(List.of("released"), f.port.events, "A write-back was queued or acknowledged");
        assertEquals(List.of(other.transferId()), f.diskRecords(), "T is in the committed file");
        assertTrue(f.world.rockets.isEmpty() && f.world.blocks.size() == f.snapshot().blocks().size());
        assertEquals(0, f.disassemblies.heldSlots(f.journal));
        assertTrue(f.disassemblies.reservations(f.journal).isEmpty());
        f.acknowledge();
        assertFalse(f.disassemblies.pending(f.logical()));
        assertEquals(List.of(other.transferId()), f.diskRecords(), "T came back on disk");
        assertEquals(1, f.request.restores);
        assertEquals(List.of("SUCCESS"), f.request.reports);
        f.close();
    }

    @Test void aGenerationCapturedBeforeTheRemovalDoesNotRunItsContinuation() {
        // R28 item 2, the boundary R23 relies on: the generation in flight when T is removed was captured with T. Its
        // commit does not acknowledge the removal; only a later generation, captured after it, does.
        var f = new Fixture(new RocketPosition(64, 80, 64));
        var other = landed(new RocketPosition(2_000, 80, 2_000));
        f.journal.put(other); // A disjoint change, so that the generation in flight is a new one.
        var removed = new CountDownLatch(1);
        var held = new CheckedSavedDataPolicy(TYPE, (staged, target) -> {
            try {
                if (!removed.await(30, TimeUnit.SECONDS)) { throw new IOException("The removal never happened"); }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IOException(interrupted);
            }
            CheckedSavedDataFile.atomicMove(staged, target);
        });
        RocketJournalTestAccess.request(f.journal, file(), held); // Captured now, with T; its move waits for the removal.
        assertEquals(RocketValidationCode.DISASSEMBLY_PENDING, f.release());
        removed.countDown();
        RocketJournalTestAccess.awaitWriter(f.journal);
        f.journal.pollWriter(tick += 1_000);
        assertEquals(RocketTransferSavedData.WriteOutcome.COMMITTED, f.journal.checkedWriteResult().outcome());
        assertFalse(f.journal.acknowledgedCurrent(), "The removal is in the generation in flight");
        assertEquals(Set.of(f.landed.transferId(), other.transferId()), Set.copyOf(f.diskRecords()));
        assertEquals(0, f.request.restores, "The continuation ran on a generation captured before the removal");
        assertEquals(Stage.RELEASED, f.disassemblies.find(f.logical()).stage());
        assertTrue(f.request.reports.isEmpty() && f.port.events.isEmpty());
        assertEquals(1, f.disassemblies.heldSlots(f.journal), "P released T's slot before the removal was acknowledged");
        assertEquals(List.of(RocketRegion.fromSnapshot(f.snapshot())), f.disassemblies.reservations(f.journal));
        assertTrue(f.world.rockets.containsKey(f.entity) && f.world.blocks.isEmpty());
        f.acknowledge(); // The first generation captured after the removal.
        assertEquals(1, f.request.restores);
        assertEquals(List.of("SUCCESS"), f.request.reports);
        assertEquals(List.of("released"), f.port.events);
        assertFalse(f.disassemblies.pending(f.logical()));
        assertTrue(f.journal.find(f.landed.transferId()).isEmpty(), "T was written back");
        assertEquals(List.of(other.transferId()), f.diskRecords());
        assertTrue(f.world.rockets.isEmpty() && f.world.blocks.size() == f.snapshot().blocks().size());
        f.close();
    }

    @Test void anAcknowledgedRemovalRestoresWhileADisjointGenerationFailsDefinitely() {
        // Root decision R30 (W91-R1): M2 opts in to run during a failed write. The removal's own generation commits, and
        // a disjoint generation ends NOT_COMMITTED before the visit, as when both complete in one writer tick. M2 still
        // runs within the confirmation lifetime. Nothing else opts in: the ordinary continuation that the same
        // acknowledgement covers waits for the retry, and so does mode 1's write-back after a clean failure.
        for (int mode = 0; mode < 2; mode++) {
            var f = new Fixture(new RocketPosition(64 + mode * 64, 80, 64));
            if (mode == 1) { f.world.blocks.put(f.absolute(1), new RocketWorldBlock(state("minecraft:stone"), null)); } // 0 of 0
            assertEquals(RocketValidationCode.DISASSEMBLY_PENDING, f.release());
            var covered = landed(new RocketPosition(2_000, 80, 2_000));
            var ran = new ArrayList<String>();
            try (var token = f.journal.reserve(covered.logicalRocketId()).orElseThrow()) {
                f.journal.put(covered);
                assertTrue(RocketJournalTestAccess.queue(f.journal, token, () -> ran.add("covered"), () -> ran.add("discarded")));
            }
            RocketJournalTestAccess.request(f.journal, file(), CheckedSavedDataPolicy.atomic(TYPE)); // T absent, covered present
            RocketJournalTestAccess.awaitWriter(f.journal); // Published; no visit has run.
            assertEquals(RocketTransferSavedData.WriteOutcome.COMMITTED, f.journal.checkedWriteResult().outcome());
            var disjoint = landed(new RocketPosition(3_000, 80, 3_000));
            f.journal.put(disjoint);
            var failing = new CheckedSavedDataPolicy(TYPE, (staged, target) -> { throw new IOException("Injected disjoint failure"); });
            RocketJournalTestAccess.request(f.journal, file(), failing);
            RocketJournalTestAccess.awaitWriter(f.journal);
            assertEquals(RocketTransferSavedData.WriteOutcome.NOT_COMMITTED, f.journal.checkedWriteResult().outcome());
            assertEquals(0, f.request.restores, "M2 ran before the visit");
            f.port.now = RocketDisassemblyConfirmations.LIFETIME_TICKS / 2;
            f.journal.pollWriter(tick += 1);
            assertEquals(RocketTransferSavedData.WriteOutcome.NOT_COMMITTED, f.journal.checkedWriteResult().outcome());
            assertEquals(1, f.request.restores, "The acknowledged removal did not restore during the disjoint failure");
            assertEquals(List.of(), ran, "An ordinary continuation ran during the failed write");
            assertFalse(f.journal.actionable(covered.logicalRocketId()), "The waiting continuation was dropped");
            assertEquals(List.of("released"), f.port.events);
            assertEquals(List.of(covered.transferId()), f.diskRecords(), "The failed generation reached the disk");
            if (mode == 0) {
                assertEquals(List.of("SUCCESS"), f.request.reports);
                assertFalse(f.disassemblies.pending(f.logical()));
                assertTrue(f.journal.find(f.landed.transferId()).isEmpty(), "T was written back");
                assertTrue(f.world.rockets.isEmpty() && f.world.blocks.size() == f.snapshot().blocks().size());
            } else {
                assertEquals(List.of("TARGET_OCCUPIED"), f.request.reports);
                assertEquals(Stage.WRITE_BACK, f.disassemblies.find(f.logical()).stage());
                assertSame(f.landed, f.journal.find(f.landed.transferId()).orElseThrow(), "The identical record");
                assertTrue(f.world.rockets.containsKey(f.entity), "E survives a clean failure");
                assertTrue(f.journal.resourceMutationUnproved(f.logical()));
            }
            f.acknowledge(); // The retry commits.
            assertEquals(List.of("covered"), ran);
            assertFalse(f.disassemblies.pending(f.logical()));
            assertEquals(1, f.request.restores);
            assertEquals(List.of(mode == 0 ? "SUCCESS" : "TARGET_OCCUPIED"), f.request.reports, "Reported once");
            var disk = new ArrayList<>(List.of(covered.transferId(), disjoint.transferId()));
            if (mode == 1) {
                assertEquals(List.of("released", "restored settle=true"), f.port.events);
                assertFalse(f.journal.resourceMutationUnproved(f.logical()));
                disk.add(f.landed.transferId());
            } else {
                assertEquals(List.of("released"), f.port.events, "A write-back was queued or acknowledged");
            }
            assertEquals(Set.copyOf(disk), Set.copyOf(f.diskRecords()));
            f.close();
        }
    }

    @Test void theRemovalsOwnRefusalsAndAChangeOfItsRecordStillWriteTheRecordBack() {
        // R28 item 3: the causes that still reach M3 without restoring under R23, where no other test had them.
        for (int mode = 0; mode < 4; mode++) {
            var f = new Fixture(new RocketPosition(64 + mode * 64, 80, 64));
            assertEquals(RocketValidationCode.DISASSEMBLY_PENDING, f.release());
            if (mode == 0) { f.world.hash = "0".repeat(64); } // E no longer equals S but stays landed authority (G14).
            if (mode == 1) { f.port.bay = false; } // The bay gate refuses at M2.
            if (mode == 2) { // L's own record changes before the acknowledgement: the journal discards the continuation.
                var changed = landed(new RocketPosition(2_000, 80, 2_000), f.logical());
                f.journal.put(changed);
                f.journal.remove(changed.transferId());
                // That discard runs inside pollWriter outside any handler; a notifier failure stays contained (W92-R3).
                f.request.notifierFailure = new IllegalStateException("Injected notifier failure in a discard");
            }
            if (mode == 3) { // More than RocketJournalLeases' 1,024 other rockets change after L: W91's history floor.
                for (int index = 1; index <= 1_100; index++) { RocketJournalScheduleFixtures.pendingAbsence(f.journal, index); }
            }
            assertDoesNotThrow(f::acknowledge, "The failure escaped pollWriter");
            assertEquals(0, f.request.restores, "Restored although the removal's continuation refused or was discarded");
            assertEquals(List.of("REGION_BUSY"), f.request.reports, "Reported once");
            assertEquals(Stage.WRITE_BACK, f.disassemblies.find(f.logical()).stage());
            assertSame(f.landed, f.journal.find(f.landed.transferId()).orElseThrow(), "The identical record");
            assertEquals(0, f.disassemblies.heldSlots(f.journal), "Not counted a second time in WRITE_BACK");
            assertTrue(f.disassemblies.reservations(f.journal).isEmpty());
            assertTrue(f.world.rockets.containsKey(f.entity) && f.world.blocks.isEmpty());
            assertTrue(f.journal.resourceMutationUnproved(f.logical()));
            // M2 audits the release before it refuses; a discarded continuation never reaches M2.
            List<String> released = mode < 2 ? List.of("released") : List.of();
            assertEquals(released, f.port.events);
            f.acknowledge();
            assertFalse(f.disassemblies.pending(f.logical()));
            var events = new ArrayList<>(released);
            events.add("restored settle=true");
            assertEquals(events, f.port.events);
            assertEquals(List.of(f.landed.transferId()), f.diskRecords());
            assertFalse(f.journal.resourceMutationUnproved(f.logical()));
            f.close();
        }
    }

    @Test void stopAndAbortCarryTheRecordBackInRamAndCleanupIsIdempotent() throws IOException {
        for (boolean abort : List.of(false, true)) {
            var f = new Fixture(new RocketPosition(64, 80, 64));
            f.release(); // The removal's generation is captured by neither a write nor a poll before the stop.
            if (abort) { f.journal.abort(); } else { f.journal.stop(); }
            assertFalse(f.disassemblies.pending(f.logical()));
            assertSame(f.landed, f.journal.find(f.landed.transferId()).orElseThrow());
            if (!abort) { assertEquals(List.of(f.landed.transferId()), f.diskRecords(), "T rides the final capture"); }
            f.journal.stop();
            f.disassemblies.clear();
            assertEquals(List.of(), f.port.events);
            Files.deleteIfExists(file());
        }
    }

    @Test void aNotifierFailureInTheDiscardPathsIsContainedAndTheWriteBackGoesOn() throws IOException {
        // W92-R3: the journal runs discarded callbacks outside any handler, in stop() and in pollWriter.
        var stopped = new Fixture(new RocketPosition(64, 80, 64));
        stopped.release();
        stopped.request.notifierFailure = new IllegalStateException("Injected notifier failure in a discard");
        assertDoesNotThrow(stopped.journal::stop, "The failure left stop() before its drain and final capture");
        assertFalse(stopped.disassemblies.pending(stopped.logical()));
        assertSame(stopped.landed, stopped.journal.find(stopped.landed.transferId()).orElseThrow());
        assertEquals(List.of(stopped.landed.transferId()), stopped.diskRecords(), "T rides the final capture");
        assertEquals(List.of("REGION_BUSY"), stopped.request.reports, "Reported once");
        Files.deleteIfExists(file());

        // Root decision R28 (R23): the later generation acknowledges the removal, so M2 runs in pollWriter instead of the
        // discard. The notifier then fails after SUCCESS, which M2 contains as an uncertain mutation: no write-back.
        // theRemovalsOwnRefusalsAndAChangeOfItsRecordStillWriteTheRecordBack keeps a discard inside pollWriter.
        var acknowledged = new Fixture(new RocketPosition(128, 80, 64));
        acknowledged.release();
        acknowledged.request.notifierFailure = new IllegalStateException("Injected notifier failure in a discard");
        var failing = new CheckedSavedDataPolicy(TYPE, (staged, target) -> { throw new IOException("Injected removal failure"); });
        RocketJournalTestAccess.write(acknowledged.journal, file(), failing, tick += 1_000);
        acknowledged.journal.put(landed(new RocketPosition(2_000, 80, 2_000))); // A disjoint successor generation.
        assertDoesNotThrow(acknowledged::acknowledge, "The failure escaped pollWriter");
        assertEquals(1, acknowledged.request.restores);
        assertFalse(acknowledged.disassemblies.pending(acknowledged.logical()));
        assertTrue(acknowledged.journal.find(acknowledged.landed.transferId()).isEmpty(), "T was written back");
        acknowledged.acknowledge();
        assertFalse(acknowledged.disassemblies.pending(acknowledged.logical()));
        assertFalse(acknowledged.diskRecords().contains(acknowledged.landed.transferId()), "T is in the committed file");
        assertEquals(List.of("SUCCESS"), acknowledged.request.reports, "Reported once");
        assertEquals(List.of("released"), acknowledged.port.events, "A write-back was queued or acknowledged");
        assertTrue(acknowledged.world.rockets.isEmpty()
                && acknowledged.world.blocks.size() == acknowledged.snapshot().blocks().size());
        acknowledged.close();
    }

    @Test void refusedInsertsEndPendingWithoutTheRecord() {
        for (int mode = 0; mode < 3; mode++) {
            var f = new Fixture(new RocketPosition(64 + mode * 64, 80, 64));
            f.world.blocks.put(f.absolute(1), new RocketWorldBlock(state("minecraft:stone"), null));
            f.release();
            if (mode == 0) { f.request.presence = Presence.CHANGED; }
            if (mode == 1) { f.journal.put(landed(f.landed.destinationSnapshot().sourceOrigin())); }
            if (mode == 2) { f.request.presence = Presence.UNLOADED; }
            f.acknowledge();
            if (mode == 2) {
                assertSame(f.landed, f.journal.find(f.landed.transferId()).orElseThrow(), "An unloaded E gets T back");
            } else {
                assertFalse(f.disassemblies.pending(f.logical()));
                assertTrue(f.journal.find(f.landed.transferId()).isEmpty(), "mismatch and overlap are refused");
            }
            f.close();
        }
    }

    @Test void pendingIsBoundedAndCountsOnlyWhileTheRecordIsAbsent() {
        var journal = new RocketTransferSavedData();
        var disassemblies = new RocketLandedDisassemblies();
        var port = new FakePort(journal);
        port.detached = true; // Continuations are not run here; only P's bound and its inputs are.
        var records = new ArrayList<RocketTransferRecord>();
        for (int index = 0; index < RocketLandedDisassemblies.MAX_PENDING; index++) {
            var record = landed(new RocketPosition(100 + 40 * index, 80, 8));
            journal.put(record); records.add(record);
        }
        for (var record : records) {
            assertEquals(RocketValidationCode.DISASSEMBLY_PENDING, release(disassemblies, journal, record,
                    record.destinationEntityId().orElseThrow(), new FakeRequest(new FakeWorld(), record), port));
            if (disassemblies.size() == 1) {
                assertEquals(RocketValidationCode.REGION_BUSY, release(disassemblies, journal, record,
                        record.destinationEntityId().orElseThrow(), new FakeRequest(new FakeWorld(), record), port),
                        "One P per logical rocket");
            }
        }
        assertTrue(journal.entries().isEmpty());
        assertFalse(disassemblies.hasCapacity());
        assertEquals(RocketLandedDisassemblies.MAX_PENDING, disassemblies.size());
        assertEquals(RocketLandedDisassemblies.MAX_PENDING, disassemblies.heldSlots(journal));
        assertEquals(records.stream().map(record -> RocketRegion.fromSnapshot(record.destinationSnapshot())).toList(),
                disassemblies.reservations(journal));
        var extra = landed(new RocketPosition(9_000, 80, 8));
        assertEquals(RocketValidationCode.REGION_BUSY, release(disassemblies, journal, extra,
                extra.destinationEntityId().orElseThrow(), new FakeRequest(new FakeWorld(), extra), port));
        disassemblies.clear();
        assertEquals(0, disassemblies.size());
        assertEquals(0, disassemblies.heldSlots(journal));
        journal.stop();
    }

    @Test void aMissingReservationRefusesTheReleaseBeforeAnyChange() {
        // W91H (R23, N2): without L's own canQueueEffect(L) reservation, M1 changes nothing.
        for (int mode = 0; mode < 3; mode++) {
            var f = new Fixture(new RocketPosition(64 + mode * 64, 80, 64));
            var held = new ArrayList<RocketTransferSavedData.Reservation>();
            if (mode == 0) { held.add(f.journal.reserve(f.logical()).orElseThrow()); } // L's slot is busy.
            if (mode == 1) { // Other rockets hold the whole per-record bound.
                for (var next = f.journal.reserve(UUID.randomUUID()); next.isPresent(); next = f.journal.reserve(UUID.randomUUID())) {
                    held.add(next.get());
                }
            }
            if (mode == 2) { // A failed write of a disjoint generation; L's own slot stays acknowledged.
                f.journal.put(landed(new RocketPosition(2_000, 80, 2_000)));
                var failing = new CheckedSavedDataPolicy(TYPE, (staged, target) -> { throw new IOException("Injected failure"); });
                RocketJournalTestAccess.write(f.journal, file(), failing, tick += 1_000);
                assertEquals(RocketTransferSavedData.WriteOutcome.NOT_COMMITTED, f.journal.checkedWriteResult().outcome());
            }
            long sequence = f.journal.mutationSequence();
            int outstanding = f.journal.outstandingEffectCount();
            assertEquals(RocketValidationCode.REGION_BUSY, f.release());
            assertSame(f.landed, f.journal.find(f.landed.transferId()).orElseThrow(), "T was removed");
            assertEquals(sequence, f.journal.mutationSequence());
            assertEquals(outstanding, f.journal.outstandingEffectCount(), "Something was queued or reserved");
            assertFalse(f.disassemblies.pending(f.logical()));
            assertEquals(0, f.disassemblies.heldSlots(f.journal));
            assertTrue(f.request.reports.isEmpty() && f.request.restores == 0 && f.port.events.isEmpty());
            assertTrue(f.world.rockets.containsKey(f.entity) && f.world.blocks.isEmpty());
            held.forEach(RocketTransferSavedData.Reservation::close);
            if (mode == 2) { f.acknowledge(); }
            assertEquals(RocketValidationCode.DISASSEMBLY_PENDING, f.release(), "The refusal left the rocket stranded");
            f.acknowledge();
            assertEquals(List.of("SUCCESS"), f.request.reports);
            assertFalse(f.diskRecords().contains(f.landed.transferId()));
            f.close();
        }
    }

    @Test void thePlainRemovalRefusesBeforeAnyChangeUnlessTheRocketIsActionableAndReserved() {
        // W91H: W91's checks in the FM12a hook, moved into releasePlain with W92.
        for (int mode = 0; mode < 2; mode++) {
            var f = new Fixture(new RocketPosition(64 + mode * 64, 80, 64));
            var held = new ArrayList<RocketTransferSavedData.Reservation>();
            if (mode == 0) { f.journal.put(f.landed); } // An unacknowledged change of L.
            if (mode == 1) { // L is actionable, but other rockets hold the whole per-record bound.
                for (var next = f.journal.reserve(UUID.randomUUID()); next.isPresent(); next = f.journal.reserve(UUID.randomUUID())) {
                    held.add(next.get());
                }
                assertTrue(f.journal.actionable(f.logical()));
            }
            long sequence = f.journal.mutationSequence();
            int outstanding = f.journal.outstandingEffectCount();
            assertFalse(RocketLandedDisassemblies.removePlain(f.journal, f.landed, f.entity, f.port));
            assertSame(f.landed, f.journal.find(f.landed.transferId()).orElseThrow(), "T was removed");
            assertEquals(sequence, f.journal.mutationSequence());
            assertEquals(outstanding, f.journal.outstandingEffectCount(), "Something was queued or reserved");
            assertTrue(f.port.events.isEmpty());
            held.forEach(RocketTransferSavedData.Reservation::close);
            if (mode == 0) { f.acknowledge(); }
            assertTrue(RocketLandedDisassemblies.removePlain(f.journal, f.landed, f.entity, f.port));
            assertTrue(f.journal.find(f.landed.transferId()).isEmpty());
            assertTrue(f.port.events.isEmpty(), "Released before the removal's acknowledgement");
            f.acknowledge();
            assertEquals(List.of("released"), f.port.events);
            assertTrue(f.diskRecords().isEmpty());
            f.close();
        }
    }

    @Test void aWriteBackWithoutAReservationKeepsTheRecordInRamAndEndsPending() {
        // W91H: M3 reserves before put(T). Should L's reservation be absent while the journal still schedules work,
        // T is still put back, as at a stop, and only P ends; nothing releases T.
        var f = new Fixture(new RocketPosition(64, 80, 64));
        f.world.blocks.put(f.absolute(1), new RocketWorldBlock(state("minecraft:stone"), null)); // 0 of 0
        var held = new RocketTransferSavedData.Reservation[1];
        f.port.wrap = action -> () -> { held[0] = f.journal.reserve(f.logical()).orElseThrow(); return action.getAsBoolean(); };
        assertEquals(RocketValidationCode.DISASSEMBLY_PENDING, f.release());
        f.acknowledge();
        assertNotNull(held[0], "The removal continuation did not run");
        assertFalse(f.disassemblies.pending(f.logical()));
        assertSame(f.landed, f.journal.find(f.landed.transferId()).orElseThrow(), "T was not put back");
        assertEquals(List.of("TARGET_OCCUPIED"), f.request.reports);
        assertEquals(List.of("released"), f.port.events, "No write-back continuation was queued");
        assertTrue(f.world.rockets.containsKey(f.entity));
        assertTrue(f.journal.resourceMutationUnproved(f.logical()));
        held[0].close();
        f.acknowledge();
        assertEquals(List.of(f.landed.transferId()), f.diskRecords());
        assertFalse(f.journal.resourceMutationUnproved(f.logical()));
        f.close();
    }

    private final class Fixture {
        final RocketTransferSavedData journal = new RocketTransferSavedData();
        final RocketLandedDisassemblies disassemblies = new RocketLandedDisassemblies();
        final FakePort port = new FakePort(journal);
        final RocketTransferRecord landed;
        final UUID entity;
        final FakeWorld world = new FakeWorld();
        final FakeRequest request;

        Fixture(RocketPosition arrival) {
            this(arrival, UnaryOperator.identity());
        }

        Fixture(RocketPosition arrival, UnaryOperator<RocketTransferRecord> form) {
            landed = form.apply(landed(arrival));
            entity = landed.destinationEntityId().orElseThrow();
            world.rockets.put(entity, snapshot().snapshotId());
            world.hash = snapshot().contentHash();
            request = new FakeRequest(world, landed);
            journal.put(landed);
            RocketJournalTestAccess.write(journal, file(), CheckedSavedDataPolicy.atomic(TYPE), tick += 1);
            assertTrue(journal.acknowledgedCurrent());
        }

        RocketStructureSnapshot snapshot() { return landed.destinationSnapshot(); }
        UUID logical() { return landed.logicalRocketId(); }
        RocketPosition absolute(int index) { return snapshot().sourceOrigin().add(snapshot().blocks().get(index).position()); }
        RocketValidationCode release() { return RocketLandedDisassembliesTest.release(disassemblies, journal, landed, entity, request, port); }

        RocketTransferRecord held() {
            return disassemblies.reservations(journal).isEmpty() ? null : landed;
        }

        void acknowledge() {
            RocketJournalTestAccess.write(journal, file(), CheckedSavedDataPolicy.atomic(TYPE), tick += 1_000);
        }

        void flushAndPoll() {
            if (!journal.acknowledgedCurrent()) { journal.setDirty(); acknowledge(); }
        }

        List<UUID> diskRecords() {
            var payload = CheckedSavedDataFile.readPayload(file(), TYPE).orElseThrow();
            return RocketTransferSavedData.load(payload).entries().stream().map(RocketTransferRecord::transferId).toList();
        }

        /** T's encoded record on disk, or an empty tag. */
        CompoundTag diskRecord() {
            for (var raw : CheckedSavedDataFile.readPayload(file(), TYPE).orElseThrow().getList("transfers", 10)) {
                var tag = (CompoundTag) raw;
                if (tag.getUUID("transfer_id").equals(landed.transferId())) { return tag.copy(); }
            }
            return new CompoundTag();
        }

        void close() {
            journal.stop();
            try { Files.deleteIfExists(file()); } catch (IOException exception) { throw new IllegalStateException(exception); }
        }
    }

    private static final class FakePort implements RocketLandedDisassemblies.Port {
        final RocketTransferSavedData journal;
        final List<String> events = new ArrayList<>();
        long now = 0;
        boolean bay = true;
        boolean detached;
        UnaryOperator<BooleanSupplier> wrap = UnaryOperator.identity();

        FakePort(RocketTransferSavedData journal) { this.journal = journal; }
        @Override public long now() { return now; }
        @Override public boolean bayAllowsRemoval(RocketTransferRecord record) { return bay; }
        @Override public boolean queue(RocketTransferSavedData.Reservation reservation, BooleanSupplier action,
                                       Runnable discarded) {
            return detached || RocketJournalTestAccess.queue(journal, reservation, wrap.apply(action), discarded);
        }
        @Override public boolean queueRemoval(RocketTransferSavedData.Reservation reservation, BooleanSupplier action,
                                              Runnable discarded) {
            return detached || RocketJournalTestAccess.queue(journal, reservation, true, wrap.apply(action), discarded);
        }
        @Override public void released(RocketTransferRecord record, UUID entity) { events.add("released"); }
        @Override public void restored(RocketTransferRecord record, UUID entity, boolean settle) {
            events.add("restored settle=" + settle);
        }
        @Override public void probe(String boundary, RocketTransferRecord record) { }
    }

    private static final class FakeRequest implements RocketLandedDisassemblies.Request {
        final FakeWorld world;
        final RocketTransferRecord record;
        final List<String> reports = new ArrayList<>();
        Presence presence = Presence.MATCHES;
        RocketFailureInjector injector = RocketFailureInjector.NONE;
        RuntimeException matchesThrows;
        Error matchesError;
        RuntimeException restoreThrows;
        boolean notifierThrows;
        RuntimeException notifierFailure;
        boolean restorable = true;
        RuntimeException restorableThrows;
        int restores;
        boolean deferred, certified = true;
        long requestedAt = Long.MIN_VALUE;
        java.util.function.Consumer<RocketTransactionResult> completion;

        FakeRequest(FakeWorld world, RocketTransferRecord record) { this.world = world; this.record = record; }

        @Override public boolean matches() {
            if (matchesError != null) { throw matchesError; }
            if (matchesThrows != null) { throw matchesThrows; }
            var snapshot = record.destinationSnapshot();
            return world.rocketMatches(record.destinationEntityId().orElseThrow(), snapshot.snapshotId(), snapshot.contentHash());
        }
        @Override public boolean restorable() {
            if (restorableThrows != null) { throw restorableThrows; }
            return restorable;
        }
        @Override public Presence presence(RocketTransferRecord ignored) { return presence; }
        @Override public RocketTransactionResult restore() {
            restores++;
            if (restoreThrows != null) { throw restoreThrows; }
            return new RocketDisassemblyTransaction(world, new RocketRegionLockManager(), new RocketOperationLedger(),
                    RocketTransactionJournal.NO_OP, injector).execute(UUID.randomUUID(),
                    record.destinationEntityId().orElseThrow(), record.destinationSnapshot());
        }
        // This overload intentionally compiles against the old synchronous contract for the red run.
        public void restore(java.util.function.Consumer<RocketTransactionResult> completion) {
            if (deferred) { this.completion = completion; } else { completion.accept(restore()); }
        }
        public boolean rollbackCertified() { return certified; }
        public boolean requiresNativeRollbackProof() { return true; }
        public long requestedAt() { return requestedAt; }
        @Override public void finished(RocketValidationCode code, RocketTransactionResult result) {
            reports.add(code.name());
            if (notifierThrows && code == RocketValidationCode.SUCCESS) {
                throw new IllegalStateException("Injected notifier failure after SUCCESS");
            }
            if (notifierFailure != null) { throw notifierFailure; }
        }
    }

    private static final class FakeWorld implements RocketTransactionWorld {
        final Map<RocketPosition, RocketWorldBlock> blocks = new HashMap<>();
        final Map<UUID, UUID> rockets = new HashMap<>();
        String hash;
        RocketPosition failRemoveAt;
        Error errorAfterFirstPlacement;

        @Override public ResourceLocation dimension() { return RocketFlightPlanner.MOON.dimensionId(); }
        @Override public boolean isRegionLoaded(RocketRegion region) { return true; }
        @Override public Optional<RocketWorldBlock> readBlock(RocketPosition position) { return Optional.ofNullable(blocks.get(position)); }
        @Override public boolean removeBlockNoDrops(RocketPosition position, RocketWorldBlock expected) {
            if (position.equals(failRemoveAt)) { return false; }
            return blocks.remove(position) != null;
        }
        @Override public boolean placeBlockIfEmpty(RocketPosition position, RocketWorldBlock block) {
            if (errorAfterFirstPlacement != null && blocks.size() == 1) { throw errorAfterFirstPlacement; }
            if (blocks.containsKey(position)) { return false; }
            blocks.put(position, block);
            return true;
        }
        @Override public Optional<UUID> spawnRocket(RocketStructureSnapshot snapshot, UUID transactionId) { return Optional.empty(); }
        @Override public boolean rocketMatches(UUID rocketId, UUID snapshotId, String contentHash) {
            return snapshotId.equals(rockets.get(rocketId)) && contentHash.equals(hash);
        }
        @Override public boolean removeRocket(UUID rocketId, UUID snapshotId) { return rockets.remove(rocketId, snapshotId); }
    }

    /** M1 as {@code begin} calls it, with L's own {@code canQueueEffect(L)} reservation held through the call (R23). */
    private static RocketValidationCode release(RocketLandedDisassemblies disassemblies, RocketTransferSavedData journal,
                                                RocketTransferRecord record, UUID entity,
                                                RocketLandedDisassemblies.Request request, RocketLandedDisassemblies.Port port) {
        try (var reservation = journal.canQueueEffect(record.logicalRocketId()).orElse(null)) {
            return disassemblies.release(journal, record, entity, request, port, reservation);
        }
    }

    private static RocketBlockState state(String id) { return new RocketBlockState(ResourceLocation.tryParse(id), Map.of()); }

    /** A COMMITTED schema-2 landed record T for a three-block rocket at {@code arrival} on the Moon. */
    private static RocketTransferRecord landed(RocketPosition arrival) {
        return landed(arrival, UUID.randomUUID());
    }

    /** {@link #landed(RocketPosition)} for the given logical rocket. */
    private static RocketTransferRecord landed(RocketPosition arrival, UUID logical) {
        UUID transfer = UUID.randomUUID();
        List<RocketBlock> blocks = List.of(new RocketBlock(new RocketPosition(0, 0, 0), state("minecraft:iron_block")),
                new RocketBlock(new RocketPosition(0, 1, 0), state("minecraft:iron_block")),
                new RocketBlock(new RocketPosition(0, 2, 0), state("minecraft:iron_block")));
        RocketStats stats = new RocketStats(blocks.size(), 200L, 1_000L, 1_000L, 1, 1, 1, 0);
        RocketStructureSnapshot source = RocketStructureSnapshot.create(UUID.randomUUID(),
                RocketFlightPlanner.EARTH.dimensionId(), EARTH_ORIGIN, blocks, List.of(new RocketPosition(0, 1, 0)),
                stats, 0L);
        RocketStructureSnapshot destination = source.relocated(UUID.randomUUID(), RocketFlightPlanner.MOON.dimensionId(),
                arrival, 160L);
        RocketFuelState fuel = RocketFuelState.empty(1_000L).fill(1_000L).state();
        RocketFlightPlan plan = RocketFlightPlanner.plan(stats, fuel, RocketFlightPlanner.EARTH, RocketFlightPlanner.MOON,
                transfer, 0L).plan();
        RocketFlightData sourceFlight = RocketFlightData.initial(logical, stats.fuelCapacity(), stats.seatCount(),
                        RocketFlightPlanner.EARTH.bodyId(), RocketFlightPlanner.EARTH.dimensionId(), EARTH_ORIGIN, 0L)
                .withFuel(fuel, 0L).withPassengers(RocketPassengerManifest.empty(1)).withPlan(plan).startCountdown(0L)
                .completeCountdown(RocketFlightLimits.COUNTDOWN_TICKS)
                .beginTransit(transfer, RocketFlightLimits.COUNTDOWN_TICKS + RocketFlightLimits.ASCENT_TICKS);
        RocketFlightData destinationFlight = sourceFlight.arriveAtDestination(
                sourceFlight.fuel().debit(transfer, plan.requiredFuel()).state(), RocketFlightPlanner.MOON.bodyId(),
                RocketFlightPlanner.MOON.dimensionId(), arrival, RocketFlightLimits.COUNTDOWN_TICKS
                        + RocketFlightLimits.ASCENT_TICKS + RocketFlightLimits.TRANSIT_TICKS);
        return RocketTransferRecord.create(transfer, logical, UUID.randomUUID(), UUID.randomUUID(), source,
                        destination, sourceFlight, destinationFlight, plan.requiredFuel(), 0L)
                .destinationSpawned(UUID.randomUUID()).advance(RocketTransferPhase.PASSENGERS_TRANSFERRED)
                .advance(RocketTransferPhase.SOURCE_REMOVED).advance(RocketTransferPhase.COMMITTED);
    }
}
