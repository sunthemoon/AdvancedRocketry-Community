package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecoveryAction;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * W116 (P1-N3 leaf N3-A2): the recovery-side holds of the ordinary source-retirement coordinator. Each case reuses W98's
 * detached fixture, so the record still leaves RAM only after its survivor's slot is on disk.
 */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RocketRetirementRecoveryGameTests {
    private static final String BATCH = "ordinary_source_retirement_recovery";
    /** W98-R3's case alone: a busy capture tick between its refused attempts would log its RETRY a second time. */
    private static final String COLUMN_BATCH = "ordinary_source_retirement_column";
    /** A cold Moon column of its own, away from the other tests' fixed Moon columns. */
    private static final BlockPos MOON_TARGET = new BlockPos(12_296, 200, 12_296);
    private RocketRetirementRecoveryGameTests() { }

    /**
     * W98-R3: a REMOVE_DESTINATION_KEEP_SOURCE return whose discarded copy is in a Moon column that only the flight's own
     * ticket keeps loaded. The passenger's reconnect starts the first attempt 150 ticks after recovery refreshed that
     * ticket, and a decoy naming L in the survivor's slot refuses the first two attempts, each with a 200-tick backoff.
     * The third attempt therefore runs after recovery's 400-tick ticket expired; it finds the column loaded only because
     * each attempt refreshed the ticket, proves the discarded copy absent and retires T.
     */
    @GameTest(template = "rocket_test", batch = COLUMN_BATCH, timeoutTicks = 900)
    public static void aDiscardedCopysColumnStaysLoadedAcrossFailedAttempts(GameTestHelper helper) {
        UUID passenger = UUID.randomUUID();
        new RocketSourceRetirementGameTests.Case(helper, List.of(passenger)) {
            ServerLevel moon; RocketEntity destination, decoy; ServerPlayer player;
            long recovered; int stage;
            int bound() { return 860; }
            boolean ready() {
                if (moon == null) {
                    moon = server().getLevel(CelestialIds.MOON_LEVEL);
                    helper.assertTrue(moon != null, "No Moon Level");
                    origin = helper.absolutePos(new BlockPos(3, 2, 3)); target = MOON_TARGET;
                    helper.assertTrue(moon.getChunkSource().getChunkNow(target.getX() >> 4, target.getZ() >> 4) == null,
                            "The Moon column was already loaded");
                    flight = RocketSourceRetirementFixtures.countdown(level(), origin, moon, target, crew, 10); record = flight.record();
                }
                // The flight's own origin ticket, as a flight in transit holds it; nothing else keeps the column loaded.
                RocketTransferEntities.keepLoaded(moon, record.destinationSnapshot(), record.transferId());
                return level().isPositionEntityTicking(origin) && moon.areEntitiesLoaded(flight.destinationSlot().toLong())
                        && moon.isPositionEntityTicking(target);
            }
            void act() {
                source = flight.spawnInFlight(helper, level());
                destination = flight.spawnDestination(helper, moon);
                decoy = flight.spawnDecoy(helper, level(), origin.above(5));
                prepare(false);
                completion = false;
            }
            boolean before() {
                var server = server(); long now = server.getTickCount();
                var retirements = recovery().retirements(); UUID logical = record.logicalRocketId();
                if (stage == 0) {
                    var result = recovery().recoverById(server, journal, record.transferId());
                    if (!retirements.pending(logical)) {
                        helper.assertTrue(result.status() == RocketTransferRecoveryService.Status.RETRY_LATER, "Recovery status " + result.status());
                        return false;
                    }
                    helper.assertTrue(result.status() == RocketTransferRecoveryService.Status.WAITING_FOR_PASSENGERS
                            && result.action() == RocketTransferRecoveryAction.REMOVE_DESTINATION_KEEP_SOURCE, "Recovery reported " + result.status());
                    helper.assertTrue(destination.isRemoved(), "The destination copy was not discarded");
                    recovered = now; stage = 1;
                    return false;
                }
                helper.assertTrue(journal.find(record.transferId()).orElse(null) == record, "The record left RAM before its proof");
                if (stage == 1) {
                    helper.assertTrue(retirements.attempts(logical) == 0, "The return attempted a proof before its crew was placed");
                    if (now - recovered < 150) { return false; }
                    player = join(passenger, origin.above(4));
                    recovery().onPlayerLoggedIn(player, journal);
                    helper.assertTrue(player.getVehicle() == source, "The reconnect did not place the passenger on the survivor");
                    stage = 2;
                    return false;
                }
                if (stage == 2) {
                    // Two attempts refused at the survivor's capture, each followed by the 200-tick backoff.
                    if (retirements.attempts(logical) < 2 || !"BARRIER_VETO".equals(retirements.reason(logical))) { return false; }
                    decoy.discard(); stage = 3;
                    return false;
                }
                return true;
            }
            void retired() {
                helper.assertTrue(server().getTickCount() - recovered > RocketFlightLimits.FLIGHT_TICKET_TIMEOUT_TICKS,
                        "The proof finished while recovery's own flight ticket was still alive");
                helper.assertTrue(RocketSourceRetirementFixtures.diskCopies(moon, flight.destinationSlot(), record.logicalRocketId()) == 0,
                        "The discarded destination copy is still saved");
                helper.assertTrue(player.getVehicle() == source || player.getVehicle() == null, "The passenger left the survivor's placement");
            }
            void restore() { if (decoy != null && !decoy.isRemoved()) { decoy.discard(); } }
        }.start();
    }

    /** W98-R6: a countdown cancellation stopped before its proof leaves a COUNTDOWN image; its offline seat does not hold recovery. */
    @GameTest(template = "rocket_test", batch = BATCH, timeoutTicks = 400)
    public static void aCountdownImageRecoversWithoutWaitingForItsOfflineSeat(GameTestHelper helper) {
        new PadRecovery(helper, Image.COUNTDOWN).start();
    }

    /** W98-R6: the cancellation's return was saved but T's removal was not, so the image is stationary at its origin. */
    @GameTest(template = "rocket_test", batch = BATCH, timeoutTicks = 400)
    public static void aStationaryImageAtItsOriginRecoversWithoutWaitingForItsOfflineSeat(GameTestHelper helper) {
        new PadRecovery(helper, Image.AT_ORIGIN).start();
    }

    /** W98-R6, the other side: a stationary image away from its origin may have flown, so its offline seat holds recovery. */
    @GameTest(template = "rocket_test", batch = BATCH, timeoutTicks = 400)
    public static void aStationaryImageAwayFromItsOriginWaitsForItsOfflineSeat(GameTestHelper helper) {
        new PadRecovery(helper, Image.DISPLACED).start();
    }

    private enum Image { COUNTDOWN, AT_ORIGIN, DISPLACED }

    /** I2 recovery of a PREPARED record from a loaded source image that did or did not leave its pad, with one offline seat. */
    private static final class PadRecovery extends RocketSourceRetirementGameTests.Case {
        final Image image;
        ServerPlayer player;
        boolean waited;
        int held;

        PadRecovery(GameTestHelper helper, Image image) {
            super(helper, List.of(UUID.randomUUID()));
            this.image = image;
        }

        void act() {
            source = image == Image.COUNTDOWN ? flight.spawnCountdown(helper)
                    : flight.spawn(helper, level(), RocketTransferEntities.stationarySource(record, level().getGameTime()), false);
            if (image == Image.DISPLACED) {
                // One block along x, inside the origin's chunk column, so the candidate stays on its origin column (R4).
                var at = record.sourceSnapshot().sourceOrigin();
                source.setPos(at.x() + ((at.x() & 15) == 15 ? -0.5D : 1.5D), at.y(), at.z() + 0.5D);
            }
            prepare(false);
            completion = false;
        }

        boolean before() {
            var server = server(); UUID logical = record.logicalRocketId();
            if (!waited) {
                var result = recovery().recoverById(server, journal, record.transferId());
                if (!recovery().retirements().pending(logical)) {
                    helper.assertTrue(result.status() == RocketTransferRecoveryService.Status.RETRY_LATER, "Recovery status " + result.status());
                    return false;
                }
                helper.assertTrue(image == Image.DISPLACED
                        ? result.status() == RocketTransferRecoveryService.Status.WAITING_FOR_PASSENGERS && result.action() == RocketTransferRecoveryAction.KEEP_SOURCE
                        : result.status() == RocketTransferRecoveryService.Status.RETRY_LATER, "Recovery reported " + result.status() + " (W98-R6)");
                waited = true;
                assertReturnedAndHeld(false);
                return false;
            }
            if (image != Image.DISPLACED) { return true; }
            if (held < 40) {
                helper.assertTrue(journal.find(record.transferId()).orElse(null) == record
                        && "CREW_WAITING".equals(recovery().retirements().reason(logical)),
                        "A source that may have left its pad did not wait for its offline seat (W98-R6)");
                if (++held == 40) {
                    player = join(crew.get(0), origin.above(4));
                    recovery().onPlayerLoggedIn(player, journal);
                    helper.assertTrue(player.getVehicle() == source, "The reconnect did not place the passenger on the survivor");
                }
                return false;
            }
            return true;
        }

        void retired() {
            if (image == Image.DISPLACED) {
                helper.assertTrue(player.getVehicle() == source, "The reconnected passenger left the survivor");
                return;
            }
            var report = recovery().recoverById(server(), journal, record.transferId());
            helper.assertTrue(report.status() == RocketTransferRecoveryService.Status.RECOVERED && report.action() == RocketTransferRecoveryAction.KEEP_SOURCE,
                    "The completed recovery was not reported: " + report.status());
            helper.assertTrue(source.flightData().orElseThrow().passengers().assignment(crew.get(0)).isPresent(),
                    "The offline passenger's seat was dropped");
        }
    }
}
