package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketFlightNbtCodec;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** A countdown reload keeps the frozen ASSEMBLED source and re-proves its session-local clock. */
class EscrowAssembledCountdownReloadTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void journalReloadDuringGovernedCountdownPreservesAssembledSourceAndResumesClock() {
        var fixture = EscrowGovernedLaunchTest.assembled();
        var initial = flight(fixture.source());
        var plan = fixture.record().sourceFlightData().plan().orElseThrow();
        var countdown = initial.withFuel(initial.fuel(), 2).withPlan(plan).startCountdown(2);
        var armed = fixture.record().nativeTransfer().orElseThrow().armed();
        var baseline = EscrowLedger.empty(armed.ledger());
        var prepared = EscrowGovernedLaunch.prepare(baseline, fixture.source(), fixture.record().sourceSnapshot(), countdown,
                fixture.record().destinationSnapshot(), armed.destination(), armed.journal());
        var record = prepared.record(prepared.preflight(baseline, 1).armedFlights().get(0));
        var journal = new RocketTransferSavedData();
        try {
            journal.put(record);
            var saved = journal.save(new CompoundTag());
            // Each iteration discards the previous journal and RAM clock, like another process start.
            for (int restart = 0; restart < 2; restart++) {
                var loaded = RocketTransferSavedData.load(saved);
                try {
                    assertTrue(loaded.operational());
                    var reloaded = loaded.find(record.transferId()).orElseThrow();
                    var frozen = reloaded.nativeTransfer().orElseThrow().sourceEnvelope(reloaded);
                    assertEquals(record.nativeTransfer().orElseThrow().sourceEnvelope(record), frozen);
                    assertEquals(RocketFlightState.ASSEMBLED, flight(frozen).state());
                    assertEquals(initial, flight(frozen));
                    assertTrue(EscrowGovernedSourceRecovery.sourceMatches(reloaded, fixture.source().copy(), 2));
                    var observed = fixture.source().copy();
                    observed.put("flight", RocketFlightNbtCodec.encode(countdown));
                    var before = observed.copy();
                    assertTrue(EscrowGovernedSourceRecovery.sourceMatches(reloaded, observed, 2));
                    var clock = EscrowGovernedSourceClock.resume(reloaded, observed, 2);
                    assertEquals(2, clock.at(2));
                    assertEquals(22, clock.at(22));
                    assertTrue(EscrowGovernedSourceRecovery.sourceMatches(reloaded, observed, clock.at(22)));
                    var rolledBack = EscrowGovernedSourceClock.resume(reloaded, observed, 0);
                    assertEquals(2, rolledBack.at(0));
                    assertEquals(22, rolledBack.at(20));
                    assertEquals(before, observed);
                    assertEquals(saved, loaded.save(new CompoundTag()));
                } finally { loaded.stop(); }
            }
        } finally { journal.stop(); }
    }

    private static RocketFlightData flight(CompoundTag envelope) {
        return RocketFlightNbtCodec.decode(envelope.getCompound("flight")).data().orElseThrow();
    }
}
