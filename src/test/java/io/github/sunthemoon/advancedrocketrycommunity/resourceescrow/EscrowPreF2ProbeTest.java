package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketFlightNbtCodec;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.GovernedTransferFixture;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EscrowPreF2ProbeTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    private record Fixture(RocketTransferRecord record, CompoundTag countdown) { }

    private Fixture fixture() {
        var base = GovernedTransferFixture.create();
        var source = base.source().copy();
        var initial = RocketFlightNbtCodec.decode(source.getCompound("flight")).data().orElseThrow();
        var vector = initial.fuel().propulsion().orElseThrow();
        var fuel = initial.fuel().withFluidBank(false, vector.primary().fluidId(), vector.primary().fluidTag(), 500)
                .withFluidBank(true, vector.oxidizer().fluidId(), vector.oxidizer().fluidTag(), 500);
        initial = initial.withFuel(fuel, 0);
        source.put("flight", RocketFlightNbtCodec.encode(initial));
        var oldArm = base.record().nativeTransfer().orElseThrow().armed();
        source.put("wrapper", new RocketEscrowSection(oldArm.ledger(), 1, List.of()).encode());
        var plan = RocketFlightPlanner.plan(base.record().sourceSnapshot().stats(), fuel,
                RocketFlightPlanner.EARTH, RocketFlightPlanner.MOON, oldArm.transfer(), 1).plan();
        var countdown = initial.withPlan(plan).startCountdown(1);
        var root = EscrowLedger.empty(oldArm.ledger());
        var launch = EscrowGovernedLaunch.prepare(root, source, base.record().sourceSnapshot(), countdown,
                base.record().destinationSnapshot(), oldArm.destination(), oldArm.journal());
        var armed = launch.preflight(root, 1);
        var record = launch.record(armed.armedFlights().get(0));
        var observed = source.copy(); observed.put("flight", RocketFlightNbtCodec.encode(countdown));
        return new Fixture(record, observed);
    }

    @Test void countdownChecksCompleteProgressionBeforeComparingCheckpointDigest() {
        var fixture = fixture(); var before = fixture.countdown().copy();
        var digest = EscrowPreF2Probe.countdownCheckpoint(fixture.record(), fixture.countdown(), 1);
        assertEquals(fixture.record().nativeTransfer().orElseThrow().armed().sourceEnvelope(), digest);
        assertNotEquals(digest, EscrowGovernedEnvelope.digest(fixture.countdown()));
        assertEquals(before, fixture.countdown());
        assertEquals(digest, EscrowPreF2Probe.countdownCheckpoint(fixture.record(), fixture.countdown(), 0));
    }

    @Test void checkpointProjectionCannotHideChangedSourceOrFlightFields() {
        for (var field : List.of("owner", "physical", "logical", "snapshot", "wrapper", "fuel", "manifest", "plan", "clock", "pose", "motion", "yaw")) {
            var fixture = fixture(); var changed = fixture.countdown().copy();
            switch (field) {
                case "owner", "physical", "logical" -> changed.putUUID(field, GovernedTransferFixture.id(999));
                case "snapshot" -> changed.getCompound("snapshot").putString("unexpected", "mutation");
                case "wrapper" -> changed.put("wrapper", new RocketEscrowSection(GovernedTransferFixture.id(7), 2, List.of()).encode());
                case "fuel" -> changed.getCompound("flight").getCompound("fuel").putLong("amount", 499);
                case "manifest" -> changed.getCompound("flight").put("passengers", new CompoundTag());
                case "plan" -> changed.getCompound("flight").remove("plan");
                case "clock" -> changed.getCompound("flight").putLong("state_started_game_time", 0);
                case "pose" -> changed.put("pos", GovernedTransferFixture.vector(8.5, 81, 8.5));
                case "motion" -> changed.put("motion", GovernedTransferFixture.vector(1, 0, 0));
                case "yaw" -> changed.putFloat("yaw", 1);
                default -> throw new AssertionError(field);
            }
            assertThrows(RuntimeException.class, () -> EscrowPreF2Probe.countdownCheckpoint(fixture.record(), changed, 1), field);
        }
    }

    @Test void firstObservationRequiresCountdownRatherThanStationaryOrLaterFlight() {
        var fixture = fixture();
        var stationary = fixture.record().nativeTransfer().orElseThrow().sourceEnvelope(fixture.record());
        assertThrows(RuntimeException.class, () -> EscrowPreF2Probe.countdownCheckpoint(fixture.record(), stationary, 1));
        var later = fixture.countdown().copy();
        var flight = RocketFlightNbtCodec.decode(later.getCompound("flight")).data().orElseThrow().completeCountdown(61);
        later.put("flight", RocketFlightNbtCodec.encode(flight));
        assertThrows(RuntimeException.class, () -> EscrowPreF2Probe.countdownCheckpoint(fixture.record(), later, 61));
    }
}
