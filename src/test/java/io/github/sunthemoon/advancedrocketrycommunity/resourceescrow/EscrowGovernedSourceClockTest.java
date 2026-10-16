package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketFlightNbtCodec;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.GovernedTransferFixture;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EscrowGovernedSourceClockTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void restartRebasesCheckpointCountdownAscentAndTransitWithoutRewritingAnEnvelope() {
        var fixture = GovernedTransferFixture.create(); var record = fixture.record();
        var initial = flight(fixture.source());
        var countdown = initial.withPlan(record.sourceFlightData().plan().orElseThrow()).startCountdown(1000);
        var ascent = countdown.completeCountdown(1060); var transit = ascent.beginTransit(record.transferId(), 1140);
        for (var state : List.of(initial, countdown, ascent, transit)) {
            var observed = fixture.source().copy(); observed.put("flight", RocketFlightNbtCodec.encode(state));
            double y = state == ascent ? 104 : state == transit ? 128 : 80;
            observed.put("pos", GovernedTransferFixture.vector(8.5, y, 8.5));
            var before = observed.copy(); var clock = EscrowGovernedSourceClock.resume(record, observed, 0);
            long expected = state == initial ? 1 : state == ascent ? 1100 : state.stateStartedGameTime();
            assertEquals(expected, clock.at(0)); assertEquals(expected + 20, clock.at(20));
            assertTrue(EscrowGovernedSourceRecovery.sourceMatches(record, observed, clock.at(0)));
            assertEquals(before, observed); assertEquals(fixture.record(), record);
            // A second process starts with no first-session clock and proves the same persisted envelope again.
            assertEquals(expected, EscrowGovernedSourceClock.resume(record, observed, 0).at(0));
        }
    }

    @Test void healthyClockIsUnchangedAndRollbackInsideALiveBindingIsRefused() {
        var fixture = GovernedTransferFixture.create();
        var clock = EscrowGovernedSourceClock.resume(fixture.record(), fixture.source(), 200);
        assertEquals(200, clock.at(200)); assertEquals(340, clock.at(340));
        assertThrows(IllegalArgumentException.class, () -> clock.at(199));
        assertThrows(IllegalArgumentException.class, () -> EscrowGovernedSourceClock.resume(fixture.record(), fixture.source(), -1));
    }

    @Test void rebasingDoesNotAcceptPrematurePhaseOrderOrAnyChangedEnvelopeField() {
        var fixture = GovernedTransferFixture.create(); var record = fixture.record(); var initial = flight(fixture.source());
        var observed = fixture.source().copy();
        var early = initial.withPlan(record.sourceFlightData().plan().orElseThrow()).startCountdown(1).completeCountdown(2);
        observed.put("flight", RocketFlightNbtCodec.encode(early));
        assertThrows(IllegalArgumentException.class, () -> EscrowGovernedSourceClock.resume(record, observed, 0));
        var future = initial.withPlan(record.sourceFlightData().plan().orElseThrow()).startCountdown(1000);
        observed.put("flight", RocketFlightNbtCodec.encode(future));
        for (String key : observed.getAllKeys()) {
            var missing = observed.copy(); missing.remove(key);
            assertThrows(RuntimeException.class, () -> EscrowGovernedSourceClock.resume(record, missing, 0), key);
        }
        var changed = observed.copy(); changed.putBoolean("on_ground", true);
        assertThrows(IllegalArgumentException.class, () -> EscrowGovernedSourceClock.resume(record, changed, 0));
        var extra = observed.copy(); extra.putInt("extra", 1);
        assertThrows(IllegalArgumentException.class, () -> EscrowGovernedSourceClock.resume(record, extra, 0));
    }

    @Test void ascentRebaseIsBoundedAndNeverMovesBelowItsSavedHeight() {
        var fixture = GovernedTransferFixture.create(); var record = fixture.record();
        var ascent = flight(fixture.source()).withPlan(record.sourceFlightData().plan().orElseThrow())
                .startCountdown(1000).completeCountdown(1060);
        var observed = fixture.source().copy(); observed.put("flight", RocketFlightNbtCodec.encode(ascent));
        for (double height : new double[] {80, 80.01, 104, 127.99, 128}) {
            observed.put("pos", GovernedTransferFixture.vector(8.5, height, 8.5));
            var clock = EscrowGovernedSourceClock.resume(record, observed, 0);
            assertTrue(EscrowGovernedSourceFlight.ascentHeight(80, 1060, clock.at(0)) >= height);
            assertTrue(clock.at(0) >= 1060 && clock.at(0) <= 1140);
        }
        for (double height : new double[] {79.99, 128.01, Double.NaN, Double.POSITIVE_INFINITY}) {
            observed.put("pos", GovernedTransferFixture.vector(8.5, height, 8.5));
            assertThrows(IllegalArgumentException.class, () -> EscrowGovernedSourceClock.resume(record, observed, 0));
        }
    }

    @Test void clockOverflowCannotWrapOrAuthorizeAnotherPhase() {
        var fixture = GovernedTransferFixture.create(); var record = fixture.record();
        var future = flight(fixture.source()).withPlan(record.sourceFlightData().plan().orElseThrow()).startCountdown(Long.MAX_VALUE);
        var observed = fixture.source().copy(); observed.put("flight", RocketFlightNbtCodec.encode(future));
        var clock = EscrowGovernedSourceClock.resume(record, observed, 0);
        assertEquals(Long.MAX_VALUE, clock.at(0));
        assertThrows(ArithmeticException.class, () -> clock.at(1));
        observed.put("flight", RocketFlightNbtCodec.encode(future.completeCountdown(Long.MAX_VALUE)));
        observed.put("pos", GovernedTransferFixture.vector(8.5, 81, 8.5));
        assertThrows(ArithmeticException.class, () -> EscrowGovernedSourceClock.resume(record, observed, 0));
    }

    private static RocketFlightData flight(CompoundTag envelope) {
        return RocketFlightNbtCodec.decode(envelope.getCompound("flight")).data().orElseThrow();
    }
}
