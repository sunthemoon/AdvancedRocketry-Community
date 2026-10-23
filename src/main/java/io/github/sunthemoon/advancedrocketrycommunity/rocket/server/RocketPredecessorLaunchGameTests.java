package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.gametest.GameTestTickPacer;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketDestination;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightRequestCode;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferPhase;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** W150: real ordinary relaunch through the installed service, with a refuelled unsaved arrival. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RocketPredecessorLaunchGameTests {
    private static final String BATCH = "ordinary_predecessor_launch";
    private static final List<Scenario> RUNNING = new ArrayList<>();
    private enum Mode { PROVE, CHANGED, CREW }
    private RocketPredecessorLaunchGameTests() { }

    @GameTest(template = "rocket_test", batch = BATCH, timeoutTicks = 1200)
    public static void relaunchRetainsThePredecessorUntilCurrentDestinationAndOldSourceAreProved(GameTestHelper helper) {
        start(helper, Mode.PROVE);
    }

    @GameTest(template = "rocket_test", batch = BATCH + "_changed", timeoutTicks = 1200)
    public static void aChangedAcceptedSourceRefusesRelaunchWithoutReplacingItsPredecessor(GameTestHelper helper) {
        start(helper, Mode.CHANGED);
    }

    @GameTest(template = "rocket_test", batch = BATCH + "_crew", timeoutTicks = 1200)
    public static void unresolvedOldCrewSpendsTheDemandWithoutErasingTheirAuthority(GameTestHelper helper) {
        start(helper, Mode.CREW);
    }

    private static void start(GameTestHelper helper, Mode mode) {
        var scenario = new Scenario(helper, mode); RUNNING.add(scenario); helper.onEachTick(scenario::tick);
    }

    @AfterBatch(batch = BATCH)
    public static void cleanup(ServerLevel level) { RUNNING.forEach(Scenario::close); RUNNING.clear(); }
    @AfterBatch(batch = BATCH + "_changed")
    public static void cleanupChanged(ServerLevel level) { cleanup(level); }
    @AfterBatch(batch = BATCH + "_crew")
    public static void cleanupCrew(ServerLevel level) { cleanup(level); }

    private static final class Scenario {
        final GameTestHelper helper;
        final ServerLevel source, destination;
        final GameTestTickPacer pacer;
        final Mode mode;
        final UUID request = UUID.randomUUID();
        RocketSourceRetirementFixtures.Flight fixture;
        RocketTransferRecord previous, next;
        RocketEntity rocket;
        CompoundTag frozen;
        int step;
        boolean closed;

        Scenario(GameTestHelper helper, Mode mode) {
            this.helper = helper; source = helper.getLevel();
            this.mode = mode;
            destination = source.getServer().getLevel(CelestialIds.MOON_LEVEL);
            pacer = new GameTestTickPacer(source.getServer());
        }

        void tick() {
            if (closed) { return; }
            pacer.pace();
            var server = source.getServer(); var journal = RocketTransferSavedData.get(server);
            if (step == 0) {
                if (!journal.retryChecked(server)) { return; }
                fixture = RocketSourceRetirementFixtures.countdown(source, helper.absolutePos(new BlockPos(3, 2, 3)),
                        destination, new BlockPos(880008 + mode.ordinal() * 128, 100, 880008),
                        mode == Mode.CREW ? List.of(UUID.randomUUID()) : List.of(), 0);
                RocketTransferEntities.keepLoaded(source, fixture.record().sourceSnapshot(), fixture.record().transferId());
                RocketTransferEntities.keepLoaded(destination, fixture.record().destinationSnapshot(), fixture.record().transferId());
                step = 1; return;
            }
            if (step == 1) {
                if (!RocketTransferEntities.recoveryEndsAlreadyLoaded(server, fixture.record())) { return; }
                rocket = fixture.spawnDestination(helper, destination);
                rocket.updateFlightData(rocket.flightData().orElseThrow().land(destination.getGameTime()));
                if (mode == Mode.CREW) {
                    rocket.updateFlightData(rocket.flightData().orElseThrow().withPassengers(
                            io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketPassengerManifest.empty(1)));
                }
                RocketTransferEntities.positionAtOrigin(rocket, fixture.record().destinationSnapshot().sourceOrigin());
                previous = fixture.record().destinationSpawned(rocket.getUUID()).advance(RocketTransferPhase.PASSENGERS_TRANSFERRED)
                        .advance(RocketTransferPhase.SOURCE_REMOVED).advance(RocketTransferPhase.COMMITTED);
                journal.put(previous); journal.flushChecked(server); step = 2; return;
            }
            if (step == 2) {
                if (!RocketRuntime.resourceMutationAllowed(rocket)) { return; }
                var flight = rocket.flightData().orElseThrow();
                rocket.updateFlightData(flight.withFuel(flight.fuel().fill(400).state(), destination.getGameTime()));
                frozen = new CompoundTag(); helper.assertTrue(rocket.save(frozen), "Could not freeze the current destination");
                var result = RocketRuntime.requestAdminFlight(rocket, RocketDestination.EARTH, request);
                helper.assertTrue(result.code() == RocketFlightRequestCode.JOURNAL_PENDING, "Relaunch refused: " + result.code());
                helper.assertTrue(journal.find(previous.transferId()).orElse(null) == previous,
                        "Relaunch replaced T before its predecessor proof");
                helper.assertTrue(rocket.flightData().orElseThrow().state() != RocketFlightState.COUNTDOWN,
                        "Countdown began before replacement acknowledgement");
                helper.assertTrue(!RocketRuntime.resourceMutationAllowed(rocket), "Pending relaunch allows resource mutation");
                if (mode == Mode.CHANGED) {
                    // A direct third-party mutation bypasses the admission interlock; it must invalidate the accepted quote.
                    var changed = rocket.flightData().orElseThrow();
                    rocket.updateFlightData(changed.withFuel(changed.fuel().fill(10).state(), destination.getGameTime()));
                }
                step = 3; return;
            }
            if (step == 3) {
                next = journal.find(request).orElse(null);
                if (mode != Mode.PROVE) {
                    helper.assertTrue(next == null && journal.find(previous.transferId()).orElse(null) == previous,
                            "Held or changed request replaced its predecessor");
                    if (RocketRuntime.resourceMutationAllowed(rocket)) {
                        helper.assertTrue(rocket.flightData().orElseThrow().state() != RocketFlightState.COUNTDOWN,
                                "Refused demand started countdown");
                        helper.assertTrue(mode != Mode.CREW || previous.sourceFlightData().passengers().assignments().size() == 1,
                                "Old unresolved crew was erased");
                        close(); helper.succeed();
                    }
                    return;
                }
                if (next == null) {
                    helper.assertTrue(journal.find(previous.transferId()).orElse(null) == previous, "Proof lost both transfer authorities");
                    return;
                }
                var image = RocketSourceRetirementFixtures.diskImage(destination, fixture.destinationSlot(), rocket.getUUID());
                helper.assertTrue(image != null && RocketRetirementProof.matches(image, frozen), "Replacement preceded durable current D1");
                helper.assertTrue(RocketSourceRetirementFixtures.diskCopies(source, fixture.originSlot(), previous.logicalRocketId()) == 0,
                        "Replacement forgot a saved predecessor");
                helper.assertTrue(next.sourceFlightData().fuel().equals(rocket.flightData().orElseThrow().fuel()),
                        "Replacement captured stale fuel or debit history");
                step = 4;
            }
            if (step == 4 && rocket.flightData().orElseThrow().state() == RocketFlightState.COUNTDOWN) {
                helper.assertTrue(journal.logicalAcknowledged(previous.logicalRocketId()), "Countdown before exact acknowledgement");
                close(); helper.succeed();
            }
        }

        void close() {
            if (closed) { return; } closed = true;
            if (rocket != null) { rocket.discard(); }
            var server = source.getServer(); var journal = RocketTransferSavedData.get(server);
            if (previous != null) { journal.remove(previous.transferId()); RocketTransferEntities.releaseFlightTickets(server, previous); }
            if (next != null) { journal.remove(next.transferId()); RocketTransferEntities.releaseFlightTickets(server, next); }
            if (fixture != null) { RocketTransferEntities.releaseFlightTickets(server, fixture.record()); }
            journal.flushChecked(server);
        }
    }
}
