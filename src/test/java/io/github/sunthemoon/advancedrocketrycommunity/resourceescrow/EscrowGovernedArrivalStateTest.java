package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketFlightNbtCodec;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.GovernedTransferFixture;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EscrowGovernedArrivalStateTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void completeDescentPreservesCreditAtEveryBoundedTick() {
        var record = record(); var frozen = record.nativeTransfer().orElseThrow().destinationEnvelope(record);
        double previous = 128;
        for (int elapsed = 0; elapsed <= RocketFlightLimits.DESCENT_TICKS + 1; elapsed++) {
            var observed = descent(record, 500, elapsed);
            assertTrue(EscrowGovernedArrivalState.matches(record, observed, 500 + elapsed));
            double y = observed.getList("pos", 6).getDouble(1);
            assertTrue(y <= previous && y >= 80); previous = y;
            assertEquals(record.destinationFlightData().fuel(), EscrowGovernedArrivalState.flight(observed).fuel());
            assertEquals(frozen.get("wrapper"), observed.get("wrapper"));
            assertEquals(elapsed >= RocketFlightLimits.DESCENT_TICKS, EscrowGovernedArrival.ready(record, observed, 500 + elapsed));
        }
        assertEquals(frozen, record.nativeTransfer().orElseThrow().destinationEnvelope(record));
    }

    @Test void landingUsesTheSameDebitedFlightAndDoesNotCompleteItsArm() {
        var record = record(); var envelope = descent(record, 500, RocketFlightLimits.DESCENT_TICKS);
        envelope.put("flight", RocketFlightNbtCodec.encode(EscrowGovernedArrivalState.flight(envelope).land(600)));
        assertTrue(EscrowGovernedArrivalState.matches(record, envelope, 600));
        assertFalse(EscrowGovernedArrival.ready(record, envelope, 600));
        assertEquals(record.destinationFlightData().fuel(), EscrowGovernedArrivalState.flight(envelope).fuel());
        assertEquals(record.destinationFlightData().passengers(), EscrowGovernedArrivalState.flight(envelope).passengers());
        assertEquals(EscrowFlightTables.Outcome.ARMED, record.nativeTransfer().orElseThrow().armed().outcome());
    }

    @Test void earlierPhasesCannotDescendOrLand() {
        var committed = record(); var observed = descent(committed, 500, 40);
        var original = fixtureRecord();
        for (var phase : RocketTransferPhase.values()) {
            if (phase == RocketTransferPhase.COMMITTED) { continue; }
            if (phase == RocketTransferPhase.DESTINATION_SPAWNED) {
                original = original.destinationSpawned(original.nativeTransfer().orElseThrow().armed().destination());
            } else if (phase != RocketTransferPhase.PREPARED) { original = original.advance(phase); }
            assertFalse(EscrowGovernedArrivalState.matches(original, observed, 540));
        }
    }

    @Test void invalidNativeFieldsCannotHideBehindTheAllowedPositionProjection() {
        var record = record(); var envelope = descent(record, 500, 40);
        for (var key : envelope.getAllKeys()) {
            var changed = envelope.copy(); changed.remove(key);
            boolean accepted;
            try { accepted = EscrowGovernedArrivalState.matches(record, changed, 540); }
            catch (RuntimeException refused) { accepted = false; }
            assertFalse(accepted, key);
        }
        var extra = envelope.copy(); extra.putInt("extra", 1);
        assertFalse(EscrowGovernedArrivalState.matches(record, extra, 540));
        var resource = envelope.copy(); resource.getCompound("wrapper").putLong("resource_epoch", 1);
        assertFalse(EscrowGovernedArrivalState.matches(record, resource, 540));
    }

    @Test void clockRollbackResumesTheStoredPoseWithoutASecondDebitOrUpwardJump() {
        var record = record(); var observed = descent(record, 500, 40);
        long now = EscrowGovernedArrivalState.resumeTime(record, observed, 3);
        assertEquals(540, now);
        assertEquals(observed.getList("pos", 6).getDouble(1), EscrowGovernedArrivalState.height(record, 500, now));
        assertEquals(700, EscrowGovernedArrivalState.resumeTime(record, observed, 700));
        assertEquals(500, EscrowGovernedArrivalState.resumeTime(record, descent(record, 500, 0), 0));
        assertThrows(IllegalArgumentException.class, () -> EscrowGovernedArrivalState.resumeTime(record, observed, -1));
    }

    @Test void outOfColumnAndPrematurePosesOrLandingsAreNotProgressions() {
        var record = record(); var observed = descent(record, 500, 40);
        for (double y : new double[] {79.99, 128.01, Double.NaN, Double.POSITIVE_INFINITY}) {
            var changed = observed.copy(); changed.put("pos", GovernedTransferFixture.vector(72.5, y, 8.5));
            assertFalse(EscrowGovernedArrivalState.matches(record, changed, 540));
            assertThrows(IllegalArgumentException.class, () -> EscrowGovernedArrivalState.resumeTime(record, changed, 540));
        }
        assertFalse(EscrowGovernedArrivalState.matches(record, observed, 539));
        var sideways = observed.copy(); sideways.put("pos", GovernedTransferFixture.vector(72.6, 104, 8.5));
        assertFalse(EscrowGovernedArrivalState.matches(record, sideways, 540));
        var early = descent(record, 500, 80); early.put("flight", RocketFlightNbtCodec.encode(record.destinationFlightData().land(162)));
        assertFalse(EscrowGovernedArrivalState.matches(record, early, 600));
        assertThrows(IllegalArgumentException.class, () -> EscrowGovernedArrivalState.descent(record, 0));
        assertThrows(IllegalArgumentException.class, () -> EscrowGovernedArrivalState.height(record, 500, 499));
    }

    @Test void physicalDismountIsAllowedButAssignmentsAndUnknownOrDuplicateRidersAreNot() {
        var record = record(); var observed = descent(record, 500, 40);
        assertTrue(EscrowGovernedArrivalState.matches(record, observed, 540));
        var rider = new CompoundTag(); rider.putUUID("physical", record.destinationFlightData().passengers().assignments().get(0).passengerId());
        observed.getList("passengers", 10).add(rider);
        assertTrue(EscrowGovernedArrivalState.matches(record, observed, 540));
        observed.getList("passengers", 10).add(rider.copy());
        assertThrows(IllegalArgumentException.class, () -> EscrowGovernedArrivalState.matches(record, observed, 540));
        observed.getList("passengers", 10).clear(); rider.putUUID("physical", java.util.UUID.randomUUID());
        observed.getList("passengers", 10).add(rider);
        assertThrows(IllegalArgumentException.class, () -> EscrowGovernedArrivalState.matches(record, observed, 540));
    }

    @Test void arrivalCacheCanHoldExactlyTheActiveFlightLimitAndRefreshItsExistingEntries() {
        var bindings = new java.util.HashMap<java.util.UUID, Object>();
        for (int i = 0; i < RocketFlightLimits.MAX_ACTIVE_TRANSFERS; i++) {
            var id = GovernedTransferFixture.id(i);
            EscrowGovernedArrival.requireCapacity(bindings, id); bindings.put(id, new Object());
        }
        assertDoesNotThrow(() -> EscrowGovernedArrival.requireCapacity(bindings, GovernedTransferFixture.id(0)));
        assertThrows(IllegalArgumentException.class, () -> EscrowGovernedArrival.requireCapacity(bindings, GovernedTransferFixture.id(64)));
    }

    static RocketTransferRecord record() {
        var record = fixtureRecord();
        return record.destinationSpawned(record.nativeTransfer().orElseThrow().armed().destination())
                .advance(RocketTransferPhase.PASSENGERS_TRANSFERRED).advance(RocketTransferPhase.SOURCE_REMOVED).advance(RocketTransferPhase.COMMITTED);
    }

    private static RocketTransferRecord fixtureRecord() {
        var fixture = GovernedTransferFixture.create(); var arm = fixture.record().nativeTransfer().orElseThrow().armed();
        var root = EscrowLedger.empty(arm.ledger());
        var countdown = EscrowGovernedArrivalState.flight(fixture.source()).withPlan(fixture.record().sourceFlightData().plan().orElseThrow()).startCountdown(1);
        var launch = EscrowGovernedLaunch.prepare(root, fixture.source(), fixture.record().sourceSnapshot(), countdown,
                fixture.record().destinationSnapshot(), arm.destination(), arm.journal());
        return launch.record(launch.preflight(root, 1).armedFlights().get(0));
    }

    static CompoundTag descent(RocketTransferRecord record, long started, long elapsed) {
        var envelope = record.nativeTransfer().orElseThrow().destinationEnvelope(record);
        envelope.put("flight", RocketFlightNbtCodec.encode(EscrowGovernedArrivalState.descent(record, started)));
        var origin = record.destinationSnapshot().sourceOrigin();
        envelope.put("pos", GovernedTransferFixture.vector(origin.x() + 0.5,
                EscrowGovernedArrivalState.height(record, started, started + elapsed), origin.z() + 0.5));
        return envelope;
    }
}
