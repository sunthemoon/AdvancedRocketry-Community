package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightPlanner;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketLandedDisassemblyRules.Admission;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketLandedDisassemblyRules.Outcome;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketLandedDisassemblyRules.Presence;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketLandedDisassemblyRules.Progress;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketLandedDisassemblyRules.Stage;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketLandedDisassemblyRules.WriteBack;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.validation.RocketValidationCode;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** W85 section 7 A0: the pure M0, M2 and M3 decisions, the result classification, P's stages and S. */
class RocketLandedDisassemblyRulesTest {
    private static final Optional<RocketValidationCode> CLEAR = Optional.empty();

    @Test void withoutALandedRecordTodaysPlainPathRunsUnlessTheLastChangeIsUnproved() {
        var probes = new AtomicInteger();
        var plain = RocketLandedDisassemblyRules.admit(false, false, () -> fail("authority"), () -> fail("bay"),
                false, false, () -> { probes.incrementAndGet(); return CLEAR; });
        assertEquals(Admission.PLAIN, plain.admission());
        assertEquals(RocketValidationCode.SUCCESS, plain.code());
        var busy = RocketLandedDisassemblyRules.admit(false, true, () -> true, () -> true, true, true, () -> CLEAR);
        assertEquals(Admission.REFUSE, busy.admission());
        assertEquals(RocketValidationCode.REGION_BUSY, busy.code());
        assertEquals(0, probes.get(), "The plain path never runs the landed preflight");
    }

    @Test void onlyAnAdmittedLandedRecordWithACleanPreflightIsReleased() {
        var release = RocketLandedDisassemblyRules.admit(true, false, () -> true, () -> true, true, true, () -> CLEAR);
        assertEquals(Admission.RELEASE, release.admission());
        assertEquals(RocketValidationCode.DISASSEMBLY_PENDING, release.code());
        // Record 3, a native transfer or a changed arrival; the bay gate; a full effect queue (G7); 64 entries.
        for (int refused = 0; refused < 4; refused++) {
            int index = refused;
            var decision = RocketLandedDisassemblyRules.admit(true, false, () -> index != 0, () -> index != 1,
                    index != 2, index != 3, () -> fail("A refused record never runs the preflight"));
            assertEquals(Admission.REFUSE, decision.admission());
            assertEquals(RocketValidationCode.REGION_BUSY, decision.code());
        }
        for (var code : new RocketValidationCode[] {RocketValidationCode.TARGET_OCCUPIED,
                RocketValidationCode.UNLOADED_CHUNK, RocketValidationCode.UNSUPPORTED_BLOCK_ENTITY,
                RocketValidationCode.ENTITY_STATE_INVALID}) {
            var decision = RocketLandedDisassemblyRules.admit(true, false, () -> true, () -> true, true, true,
                    () -> Optional.of(code));
            assertEquals(new RocketLandedDisassemblyRules.Decision(Admission.REFUSE, code), decision);
        }
    }

    @Test void pendingStatusRunsActorRangeIdentityAndPermissionInThatOrder() {
        assertEquals(RocketValidationCode.ENTITY_STATE_INVALID, RocketLandedDisassemblyRules.status(false, false,
                RocketValidationCode.UNSUPPORTED_SCHEMA, false));
        assertEquals(RocketValidationCode.OUT_OF_RANGE, RocketLandedDisassemblyRules.status(true, false,
                RocketValidationCode.UNSUPPORTED_SCHEMA, false));
        assertEquals(RocketValidationCode.UNSUPPORTED_SCHEMA, RocketLandedDisassemblyRules.status(true, true,
                RocketValidationCode.UNSUPPORTED_SCHEMA, false));
        assertEquals(RocketValidationCode.UNAUTHORIZED, RocketLandedDisassemblyRules.status(true, true,
                RocketValidationCode.SUCCESS, false));
        assertEquals(RocketValidationCode.DISASSEMBLY_PENDING, RocketLandedDisassemblyRules.status(true, true,
                RocketValidationCode.SUCCESS, true));
    }

    @Test void restorationNeedsTheLifetimeAnUnchangedArrivalAndTheBayGate() {
        long limit = RocketDisassemblyConfirmations.LIFETIME_TICKS;
        assertTrue(RocketLandedDisassemblyRules.restore(100, 100, true, true));
        assertTrue(RocketLandedDisassemblyRules.restore(100, 100 + limit, true, true));
        assertFalse(RocketLandedDisassemblyRules.restore(100, 101 + limit, true, true), "Late acknowledgement");
        assertFalse(RocketLandedDisassemblyRules.restore(100, 99, true, true), "Clock moved backwards");
        assertFalse(RocketLandedDisassemblyRules.restore(100, 101, false, true), "E no longer equals S");
        assertFalse(RocketLandedDisassemblyRules.restore(100, 101, true, false), "Bay gate refused");
    }

    @Test void onlyAFullyRolledBackFailureWithAnUnchangedArrivalIsCertifiedClean() {
        assertEquals(Outcome.SUCCESS, RocketLandedDisassemblyRules.classify(true, RocketValidationCode.SUCCESS, 5, 0, false));
        assertEquals(Outcome.CLEAN_ROLLBACK, RocketLandedDisassemblyRules.classify(false,
                RocketValidationCode.TARGET_OCCUPIED, 0, 0, true), "Failure without mutation");
        assertEquals(Outcome.CLEAN_ROLLBACK, RocketLandedDisassemblyRules.classify(false,
                RocketValidationCode.EXTRACTION_FAILED, 3, 3, true), "Complete rollback");
        assertEquals(Outcome.UNCERTAIN, RocketLandedDisassemblyRules.classify(false,
                RocketValidationCode.EXTRACTION_FAILED, 3, 2, true), "Unequal counts");
        assertEquals(Outcome.UNCERTAIN, RocketLandedDisassemblyRules.classify(false,
                RocketValidationCode.ROLLBACK_FAILED, 3, 3, true));
        assertEquals(Outcome.UNCERTAIN, RocketLandedDisassemblyRules.classify(false,
                RocketValidationCode.TARGET_OCCUPIED, 0, 0, false), "E no longer matching");
    }

    @Test void writeBackNamesEveryRefusalBeforeAnyChange() {
        assertEquals(WriteBack.INSERT, RocketLandedDisassemblyRules.writeBack(false, Presence.MATCHES, true, false, false, false));
        assertEquals(WriteBack.INSERT, RocketLandedDisassemblyRules.writeBack(false, Presence.UNLOADED, true, false, false, false));
        assertEquals(WriteBack.JOURNAL, RocketLandedDisassemblyRules.writeBack(false, Presence.MATCHES, false, false, false, false));
        assertEquals(WriteBack.MISMATCH, RocketLandedDisassemblyRules.writeBack(true, Presence.MATCHES, true, false, false, false));
        assertEquals(WriteBack.MISMATCH, RocketLandedDisassemblyRules.writeBack(false, Presence.CHANGED, true, false, false, false));
        assertEquals(WriteBack.MISMATCH, RocketLandedDisassemblyRules.writeBack(false, Presence.MATCHES, true, true, false, false));
        assertEquals(WriteBack.FULL, RocketLandedDisassemblyRules.writeBack(false, Presence.MATCHES, true, false, true, true));
        assertEquals(WriteBack.OVERLAP, RocketLandedDisassemblyRules.writeBack(false, Presence.MATCHES, true, false, false, true));
    }

    @Test void stagesOnlyMoveForwardAndTheLatchNeverResets() {
        var progress = new Progress();
        assertTrue(progress.holdsReservation() && progress.mayWriteBack() && !progress.restorationStarted());
        assertTrue(progress.warnOnce());
        assertFalse(progress.warnOnce(), "One WARN per stage");
        assertTrue(progress.startRestoration());
        assertFalse(progress.startRestoration());
        assertEquals(Stage.RESTORING, progress.stage());
        assertTrue(progress.holdsReservation());
        assertFalse(progress.mayWriteBack(), "The latch forbids a write-back without a certified clean rollback");
        assertFalse(progress.beginWriteBack());
        assertFalse(progress.warnOnce(), "RESTORING waits for no write");
        progress.certifyCleanRollback();
        assertTrue(progress.mayWriteBack() && progress.restorationStarted());
        assertTrue(progress.beginWriteBack());
        assertEquals(Stage.WRITE_BACK, progress.stage());
        assertFalse(progress.holdsReservation(), "T itself is in RAM again; P is not counted twice");
        assertFalse(progress.beginWriteBack());
        assertTrue(progress.warnOnce());
        assertFalse(progress.warnOnce());
        progress.certifyCleanRollback();
        assertTrue(progress.end());
        assertFalse(progress.end(), "Repeated cleanup changes nothing");
        assertFalse(progress.startRestoration() || progress.beginWriteBack() || progress.holdsReservation()
                || progress.mayWriteBack() || progress.warnOnce());
        assertTrue(progress.restorationStarted(), "The latch survives the end of P");

        var unlatched = new Progress();
        assertTrue(unlatched.beginWriteBack(), "A write-back before restoration needs no certification");
        assertFalse(unlatched.startRestoration());
        var uncertain = new Progress();
        uncertain.startRestoration();
        assertTrue(uncertain.end());
        assertFalse(uncertain.mayWriteBack());
    }

    @Test void pendingSnapshotAcceptsZeroFuelAndBindsAConfirmedQuote() {
        var empty = RocketFlightData.initial(UUID.randomUUID(), 1000, 1, RocketFlightPlanner.EARTH.bodyId(),
                RocketFlightPlanner.EARTH.dimensionId(), new RocketPosition(0, 80, 0), 0);
        String signature = "c".repeat(64);
        var zero = new RocketLandedDisassemblyRules.Snapshot(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "a".repeat(64), empty, .5, 80, .5, signature);
        assertEquals(0L, zero.flight().fuel().totalAmount());
        assertEquals(signature, zero.catalogSignature());
        assertThrows(IllegalArgumentException.class, () -> new RocketDisassemblyConfirmations.Quote(zero.entityId(),
                zero.ownerId(), zero.snapshotId(), zero.snapshotHash(), empty, .5, 80, .5, signature),
                "The fueled quote still rejects zero fuel");
        var fueled = empty.withFuel(empty.fuel().fill(256).state(), 0);
        var snapshot = new RocketLandedDisassemblyRules.Snapshot(zero.entityId(), zero.ownerId(), zero.snapshotId(),
                zero.snapshotHash(), fueled, .5, 80, .5, signature);
        var quote = new RocketDisassemblyConfirmations.Quote(zero.entityId(), zero.ownerId(), zero.snapshotId(),
                zero.snapshotHash(), fueled, .5, 80, .5, signature);
        assertTrue(snapshot.consented(quote));
        assertFalse(snapshot.consented(new RocketDisassemblyConfirmations.Quote(zero.entityId(), zero.ownerId(),
                zero.snapshotId(), zero.snapshotHash(), empty.withFuel(empty.fuel().fill(255).state(), 0), .5, 80, .5,
                signature)), "Changed fuel");
        assertFalse(snapshot.consented(new RocketDisassemblyConfirmations.Quote(zero.entityId(), zero.ownerId(),
                zero.snapshotId(), zero.snapshotHash(), fueled, .5, 80, .5, "d".repeat(64))), "Changed catalog");
        assertThrows(IllegalArgumentException.class, () -> new RocketLandedDisassemblyRules.Snapshot(zero.entityId(),
                zero.ownerId(), zero.snapshotId(), "invalid", empty, 0, 0, 0, signature));
        assertThrows(IllegalArgumentException.class, () -> new RocketLandedDisassemblyRules.Snapshot(zero.entityId(),
                zero.ownerId(), zero.snapshotId(), zero.snapshotHash(), empty, Double.NaN, 0, 0, signature));
        assertThrows(IllegalArgumentException.class, () -> new RocketLandedDisassemblyRules.Snapshot(zero.entityId(),
                zero.ownerId(), zero.snapshotId(), zero.snapshotHash(), empty, 0, 0, 0, "short"));
    }
}
