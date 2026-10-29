package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFuelState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketFlightNbtCodec;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.GovernedTransferFixture;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EscrowGovernedLaunchTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void equalArmedTupleReusesOneImmutableProjectionButDifferentGenerationDoesNot() {
        var fixture = GovernedTransferFixture.create(); var root = root(fixture);
        var prepared = prepare(root, fixture, countdown(fixture));
        var firstArm = prepared.preflight(root, 1).armedFlights().get(0);
        var firstRecord = prepared.record(firstArm);
        var sameArm = prepared.preflight(root, 1).armedFlights().get(0);
        assertEquals(firstArm, sameArm);
        assertSame(firstRecord, prepared.record(sameArm), "One exact tuple should not repeatedly project and hash frozen bodies");
        var later = new EscrowLedger(root.id(), 0, 20, List.of(), List.of(), List.of(), 30, List.of(), List.of());
        var nextArm = prepared.preflight(later, 21).armedFlights().get(0);
        var nextRecord = prepared.record(nextArm);
        assertNotSame(firstRecord, nextRecord);
        assertEquals(nextArm, nextRecord.nativeTransfer().orElseThrow().armed());
        assertEquals(firstRecord.nativeTransfer().orElseThrow().sourceEnvelope(firstRecord),
                nextRecord.nativeTransfer().orElseThrow().sourceEnvelope(nextRecord));
        assertSame(nextRecord, prepared.record(nextArm));
        assertNotSame(firstRecord, prepared.record(firstArm), "The cache retains only one tuple");
    }

    @Test void preparationIsImmutableAndDebitsOnlyThePrescribedDestination() {
        var fixture = GovernedTransferFixture.create(); var original = fixture.source().copy();
        var root = root(fixture); var beforeRoot = EscrowCodec.encode(root);
        var prepared = prepare(root, fixture, countdown(fixture));
        var candidate = prepared.preflight(root, 1); var arm = candidate.armedFlights().get(0);
        var record = prepared.record(arm);
        assertEquals(beforeRoot, EscrowCodec.encode(root)); assertEquals(original, fixture.source());
        assertEquals(1, arm.serial()); assertEquals(1, arm.generation());
        assertEquals(3, record.schemaVersion()); assertEquals(EscrowGovernedEnvelope.comparison(original), record.nativeTransfer().orElseThrow().sourceEnvelope(record));
        assertEquals(flight(original).fuel(), record.sourceFlightData().fuel());
        assertEquals(fixture.record().destinationFlightData().fuel(), record.destinationFlightData().fuel());
        assertEquals(List.of(record.transferId()), record.destinationFlightData().fuel().committedDebits());
        assertTrue(record.sourceFlightData().fuel().committedDebits().isEmpty());
        var destination = record.nativeTransfer().orElseThrow().destinationEnvelope(record);
        assertEquals(0, destination.getCompound("wrapper").getLong("resource_epoch"));
        assertEquals(arm.ledger(), destination.getCompound("wrapper").getUUID("ledger_id"));
        assertTrue(destination.getList("passengers", 10).isEmpty());
        assertEquals(flight(original).passengers(), record.destinationFlightData().passengers());
        assertEquals(record.destinationSnapshot().sourceOrigin().y() + RocketFlightLimits.FLIGHT_ALTITUDE_BLOCKS,
                destination.getList("pos", 6).getDouble(1));
        fixture.source().putBoolean("on_ground", !fixture.source().getBoolean("on_ground"));
        assertEquals(EscrowGovernedEnvelope.comparison(original), prepared.record(arm).nativeTransfer().orElseThrow().sourceEnvelope(record));
    }

    @Test void landedFueledSuccessorKeepsItsCheckpointStateUntilF2() {
        var fixture = landed(); var initial = flight(fixture.source());
        var countdown = initial.withFuel(initial.fuel(), initial.stateStartedGameTime())
                .withPlan(fixture.record().sourceFlightData().plan().orElseThrow()).startCountdown(2);
        var prepared = prepare(root(fixture), fixture, countdown);
        var record = prepared.record(prepared.preflight(root(fixture), 1).armedFlights().get(0));
        assertEquals(RocketFlightState.LANDED, flight(record.nativeTransfer().orElseThrow().sourceEnvelope(record)).state());
        assertEquals(initial.fuel(), record.sourceFlightData().fuel());
        assertEquals(7, record.nativeTransfer().orElseThrow().armed().epoch());
        assertEquals(RocketFlightState.COUNTDOWN, EscrowGovernedLaunch.planned(initial, record.sourceFlightData()).startCountdown(2).state());
    }

    @Test void assembledEscrowFilledSourceKeepsItsCheckpointStateUntilF2() {
        var fixture = assembled(); var original = fixture.source().copy(); var initial = flight(original);
        assertEquals(RocketFlightState.ASSEMBLED, initial.state());
        // The ordinary launch gate promotes at the launch's own tick (2), not at the checkpoint's FUELED time.
        var countdown = initial.withFuel(initial.fuel(), 2).withPlan(fixture.record().sourceFlightData().plan().orElseThrow())
                .startCountdown(2);
        var prepared = prepare(root(fixture), fixture, countdown);
        var record = prepared.record(prepared.preflight(root(fixture), 1).armedFlights().get(0));
        var source = record.nativeTransfer().orElseThrow().sourceEnvelope(record);
        assertEquals(EscrowGovernedEnvelope.comparison(original), source);
        assertEquals(RocketFlightState.ASSEMBLED, flight(source).state());
        assertEquals(initial.fuel(), record.sourceFlightData().fuel());
        assertEquals(RocketFlightNbtCodec.encode(countdown),
                RocketFlightNbtCodec.encode(EscrowGovernedLaunch.planned(initial, record.sourceFlightData()).startCountdown(2)));
        assertEquals(original, fixture.source());
    }

    @Test void emptyAssembledSourceIsStillRefused() {
        var fixture = assembled(); var initial = flight(fixture.source());
        var empty = RocketFuelState.empty(initial.fuel().propulsion().orElseThrow().descriptor());
        var emptied = RocketFlightData.restore(2, initial.logicalRocketId(), RocketFlightState.ASSEMBLED, empty, null,
                initial.passengers(), initial.currentBody(), initial.currentDimension(), initial.currentOrigin(), 1, null,
                initial.currentTarget().orElseThrow());
        assertFalse(emptied.launchReady());
        var error = assertThrows(IllegalArgumentException.class,
                () -> EscrowGovernedLaunch.planned(emptied, fixture.record().sourceFlightData()));
        assertEquals("Launch requires stationary native fuel", error.getMessage());
        assertThrows(IllegalStateException.class,
                () -> emptied.withPlan(fixture.record().sourceFlightData().plan().orElseThrow()));
    }

    @Test void actualCaptureCanAssignANewerSerialWithoutChangingTheFrozenEnvelopes() {
        var fixture = GovernedTransferFixture.create(); var root = root(fixture);
        var prepared = prepare(root, fixture, countdown(fixture));
        var later = new EscrowLedger(root.id(), 0, 20, List.of(), List.of(), List.of(), 30, List.of(), List.of());
        var arm = prepared.preflight(later, 21).armedFlights().get(0);
        assertEquals(31, arm.serial()); assertEquals(21, arm.generation());
        assertEquals(prepared.launch().sourceEnvelope(), arm.sourceEnvelope());
        assertEquals(prepared.launch().destinationEnvelope(), arm.destinationEnvelope());
        assertEquals(arm, prepared.record(arm).nativeTransfer().orElseThrow().armed());
    }

    @Test void invalidIdentityPoseSnapshotAndWrapperAreRejectedBeforeAnyArm() {
        for (String key : List.of("physical", "logical", "owner", "assembly_snapshot", "snapshot", "wrapper", "type", "chunk", "motion", "passengers")) {
            var fixture = GovernedTransferFixture.create(); var root = root(fixture); var countdown = countdown(fixture);
            fixture.source().remove(key);
            assertThrows(IllegalArgumentException.class, () -> prepare(root, fixture, countdown), key);
            assertTrue(root.armedFlights().isEmpty()); assertEquals(0, root.issuedLaunchSerial());
        }
        var fixture = GovernedTransferFixture.create(); var root = root(fixture); var countdown = countdown(fixture);
        fixture.source().put("pos", GovernedTransferFixture.vector(9.5, 80, 8.5));
        assertThrows(IllegalArgumentException.class, () -> prepare(root, fixture, countdown));
    }

    @Test void sourceFieldsCannotBeSmuggledThroughTheCountdown() {
        var fixture = GovernedTransferFixture.create(); var root = root(fixture); var countdown = countdown(fixture);
        var changed = RocketFlightNbtCodec.encode(countdown); changed.getCompound("passengers").put("assignments", new net.minecraft.nbt.ListTag());
        var withoutPassenger = RocketFlightNbtCodec.decode(changed).data().orElseThrow();
        assertThrows(IllegalArgumentException.class, () -> prepare(root, fixture, withoutPassenger));
        var nativeFuel = countdown.fuel().propulsion().orElseThrow();
        var otherFuel = countdown.fuel().withFluidBank(false, nativeFuel.primary().fluidId(), nativeFuel.primary().fluidTag(), 999);
        var changedFuel = EscrowNativeEnvelope.fuelOnly(countdown, otherFuel);
        assertThrows(IllegalArgumentException.class, () -> prepare(root, fixture, changedFuel));
        var inFlight = fixture.source().copy(); inFlight.put("flight", RocketFlightNbtCodec.encode(fixture.record().sourceFlightData()));
        var moving = new GovernedTransferFixture.Fixture(fixture.record(), inFlight, fixture.destination());
        assertThrows(IllegalArgumentException.class, () -> prepare(root, moving, countdown));
    }

    @Test void foreignLedgerUnprunedMarkersAndNamedJournalNeverReserve() {
        var fixture = GovernedTransferFixture.create(); var root = root(fixture); var countdown = countdown(fixture);
        assertThrows(IllegalArgumentException.class, () -> prepare(EscrowLedger.empty(GovernedTransferFixture.id(900)), fixture, countdown));
        var marked = fixture.source().copy();
        marked.put("wrapper", new RocketEscrowSection(root.id(), 7,
                List.of(new EscrowPrefix.Marker(GovernedTransferFixture.id(900), 1, "DESTINATION", 7))).encode());
        assertThrows(IllegalArgumentException.class, () -> prepare(root,
                new GovernedTransferFixture.Fixture(fixture.record(), marked, fixture.destination()), countdown));
        var oldGuard = fixture.record().nativeTransfer().orElseThrow().armed().journal();
        var named = new EscrowGuard(oldGuard.rocket(), GovernedTransferFixture.id(901), oldGuard.journalGeneration(), oldGuard.journalDigest());
        assertThrows(IllegalArgumentException.class, () -> EscrowGovernedLaunch.prepare(root, fixture.source(),
                fixture.record().sourceSnapshot(), countdown, fixture.record().destinationSnapshot(), GovernedTransferFixture.id(6), named));
    }

    @Test void armCapacityAndSerialOverflowRefuseWithoutMutatingThePreparedSource() {
        var fixture = GovernedTransferFixture.create(); var root = root(fixture); var source = fixture.source().copy();
        var prepared = prepare(root, fixture, countdown(fixture)); var full = root;
        for (int i = 0; i < 64; i++) { full = EscrowFlightAuthority.arm(full, EscrowFlightAuthorityTest.launch(full, i), 1); }
        var exhausted = full;
        assertThrows(IllegalArgumentException.class, () -> prepared.preflight(exhausted, 1));
        var overflow = new EscrowLedger(root.id(), 0, 0, List.of(), List.of(), List.of(), Long.MAX_VALUE, List.of(), List.of());
        assertThrows(ArithmeticException.class, () -> prepared.preflight(overflow, 1));
        assertEquals(source, fixture.source());
    }

    @Test void wrongOrTerminalArmCannotBeProjectedIntoF2() {
        var fixture = GovernedTransferFixture.create(); var root = root(fixture);
        var prepared = prepare(root, fixture, countdown(fixture)); var arm = prepared.preflight(root, 1).armedFlights().get(0);
        for (String key : List.of("binding", "launch", "source", "dest")) {
            var tag = arm.encode(); tag.putByteArray(key, new byte[32]);
            assertThrows(IllegalArgumentException.class, () -> prepared.record(EscrowFlightTables.Armed.decode(tag)), key);
        }
        var tag = arm.encode(); tag.putString("outcome", "CANCELLED");
        assertThrows(IllegalArgumentException.class, () -> prepared.record(EscrowFlightTables.Armed.decode(tag)));
    }

    @Test void previousSourcePlanAndMaximumNativeTagsAndPassengersStillFit() {
        var fixture = GovernedTransferFixture.maximal();
        // The old fixture's arbitrary serial is not copied; preparation uses this root's next serial.
        var prepared = prepare(root(fixture), fixture, countdown(fixture));
        var record = prepared.record(prepared.preflight(root(fixture), 1).armedFlights().get(0));
        assertEquals(16, record.sourceFlightData().passengers().assignments().size());
        assertEquals(64, record.destinationFlightData().fuel().committedDebits().size());
        assertEquals(EscrowGovernedEnvelope.comparison(fixture.source()), record.nativeTransfer().orElseThrow().sourceEnvelope(record));
        assertTrue(io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNbtSize.uncompressedBytes(
                record.nativeTransfer().orElseThrow().encode()) <= EscrowTransferEnvelope.MAX_BYTES);
    }

    static GovernedTransferFixture.Fixture landed() {
        var fixture = GovernedTransferFixture.create(); var initial = flight(fixture.source());
        var landed = RocketFlightData.restore(2, initial.logicalRocketId(), RocketFlightState.LANDED, initial.fuel(), null,
                initial.passengers(), initial.currentBody(), initial.currentDimension(), initial.currentOrigin(), 1, null,
                initial.currentTarget().orElseThrow());
        fixture.source().put("flight", RocketFlightNbtCodec.encode(landed)); return fixture;
    }
    /** W133: an escrow fill's after flight keeps an assembled rocket ASSEMBLED with only its new fuel (fuelOnly). */
    static GovernedTransferFixture.Fixture assembled() {
        var fixture = GovernedTransferFixture.create(); var initial = flight(fixture.source());
        var empty = RocketFlightData.restore(2, initial.logicalRocketId(), RocketFlightState.ASSEMBLED,
                RocketFuelState.empty(initial.fuel().propulsion().orElseThrow().descriptor()), null, initial.passengers(),
                initial.currentBody(), initial.currentDimension(), initial.currentOrigin(), 1, null, initial.currentTarget().orElseThrow());
        fixture.source().put("flight", RocketFlightNbtCodec.encode(EscrowNativeEnvelope.fuelOnly(empty, initial.fuel()))); return fixture;
    }
    private static RocketFlightData flight(CompoundTag envelope) { return RocketFlightNbtCodec.decode(envelope.getCompound("flight")).data().orElseThrow(); }
    private static EscrowLedger root(GovernedTransferFixture.Fixture fixture) { return EscrowLedger.empty(fixture.record().nativeTransfer().orElseThrow().armed().ledger()); }
    private static RocketFlightData countdown(GovernedTransferFixture.Fixture fixture) {
        return flight(fixture.source()).withPlan(fixture.record().sourceFlightData().plan().orElseThrow()).startCountdown(1);
    }
    private static EscrowGovernedLaunch prepare(EscrowLedger root, GovernedTransferFixture.Fixture fixture, RocketFlightData countdown) {
        return EscrowGovernedLaunch.prepare(root, fixture.source(), fixture.record().sourceSnapshot(), countdown,
                fixture.record().destinationSnapshot(), fixture.record().nativeTransfer().orElseThrow().armed().destination(),
                fixture.record().nativeTransfer().orElseThrow().armed().journal());
    }
}
