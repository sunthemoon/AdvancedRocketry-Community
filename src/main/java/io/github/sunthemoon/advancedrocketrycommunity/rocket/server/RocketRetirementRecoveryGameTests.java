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
 * W116 (P1-N3 leaf N3-A2, W98-R3): a recovery-side hold of the ordinary source-retirement coordinator. The case reuses W98's
 * detached fixture, so the record still leaves RAM only after its survivor's slot is on disk.
 */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RocketRetirementRecoveryGameTests {
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
                // Case calls this on every tick: once the decoy is gone, the retirement wait owns the record.
                if (stage == 3) { return true; }
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
                }
                return false;
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
}
