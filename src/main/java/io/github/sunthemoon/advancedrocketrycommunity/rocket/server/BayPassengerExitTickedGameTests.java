package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static io.github.sunthemoon.advancedrocketrycommunity.rocket.server.BayPassengerExitGameTestFixture.Case.*;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import java.util.Set;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.game.ClientboundKeepAlivePacket;
import net.minecraft.network.protocol.game.ServerboundKeepAlivePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Embedded connections run vanilla doTick/absMoveTo, just as ReleaseTestPlayers.pump does on a native server. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BayPassengerExitTickedGameTests {
    private BayPassengerExitTickedGameTests() { }

    /** The cases a {@link BayPassengerExitReleaseDrive} client stand-in drives; they use the release-test rider's UUID. */
    static boolean drives(BayPassengerExitGameTestFixture.Case scenario) {
        return Set.of(PACKET_LANDING, PACKET_PAIR, FALL_2_1, FALL_3_9).contains(scenario);
    }

    /** W262 (R288): a fall at 2 to 3.9 blocks per tick, which vanilla free fall reaches after about 40 to 300 blocks. */
    static double speed(BayPassengerExitGameTestFixture.Case scenario) {
        return scenario == FALL_2_1 ? 2.1D : scenario == FALL_3_9 ? 3.9D : BayPassengerExitReleaseDrive.STEP;
    }

    static boolean fast(BayPassengerExitGameTestFixture.Case scenario) { return scenario == FALL_2_1 || scenario == FALL_3_9; }

    static void pump(BayPassengerExitGameTestFixture fixture) {
        if (fast(fixture.scenario) && fixture.injected && fixture.falls == 0 && fixture.player != null) {
            // The fixture's fifty blocks cannot hold the fall that brings a server to this speed, so it holds the server's
            // own velocity there before each server-side player tick; the client stand-in sends the same speed.
            fixture.player.setDeltaMovement(0, -speed(fixture.scenario), 0);
        }
        for (int i = 0; i < fixture.players.size(); i++) {
            var player = fixture.players.get(i);
            player.connection.tick();
            Object message;
            while ((message = fixture.channels.get(i).readOutbound()) != null) {
                if (message instanceof ClientboundKeepAlivePacket keepAlive) {
                    player.connection.handleKeepAlive(new ServerboundKeepAlivePacket(keepAlive.getId()));
                }
                io.netty.util.ReferenceCountUtil.release(message);
            }
        }
    }

    static void startPacketLanding(BayPassengerExitGameTestFixture fixture) {
        var logical = fixture.flight.record().logicalRocketId();
        if (fast(fixture.scenario)) {
            // The origin's pad, under the whole column, so the fast fall lasts several ticks.
            fixture.gantry = fixture.origin;
        } else {
            // Match X4's ten-block packet descent, rather than spending seventeen ticks at its three-block packet speed.
            fixture.gantry = net.minecraft.core.BlockPos.containing(fixture.player.getX(),
                    Math.floor(fixture.player.getY()) - 10, fixture.player.getZ());
            fixture.pad(fixture.gantry);
        }
        BayPassengerExitReleaseDrive.listen();
        BayPassengerExitReleaseDrive.landAt(fixture.player, logical, fixture.gantry.getY());
        var drive = BayPassengerExitReleaseDrive.drive(fixture.player, logical);
        drive.acked = fixture.acknowledgedCorrection; // The fixture already accepted the exit correction once.
        drive.tracked = fixture.offer; drive.trace = BayPassengerExitReleaseDrive.TRACE_TICKS;
    }

    static void checkPacketLanding(BayPassengerExitGameTestFixture fixture) {
        var logical = fixture.flight.record().logicalRocketId();
        var drive = BayPassengerExitReleaseDrive.drive(fixture.player, logical);
        if (fixture.falls == 0) {
            var descent = fixture.offer.descent;
            fixture.helper.assertTrue(fixture.exits.state.live.get(fixture.player) == fixture.offer && !descent.closed,
                    "Packet descent ended before its landing: case=" + fixture.scenario + "; tick=" + fixture.player.getServer().getTickCount()
                            + "; sent=" + drive.sent + "; staged=" + drive.staged + "; physicsY=" + drive.physicsY + "; last="
                            + descent.continuity.last + "; downward=" + descent.continuity.downward + "; contact=" + fixture.offer.world.firstContact);
            if (!fixture.injected) {
                fixture.injected = true;
                BayPassengerExitReleaseDrive.descend(fixture.player, logical, 0, speed(fixture.scenario), fixture.scenario == PACKET_PAIR);
            }
            return;
        }
        fixture.helper.assertTrue(fixture.player.onGround() && fixture.player.getY() == fixture.gantry.getY()
                && fixture.player.getHealth() == 20
                && fixture.attacks.stream().filter(net.minecraftforge.event.entity.living.LivingAttackEvent::isCanceled).count() == 1,
                "Packet landing did not cancel exactly one fall: health=" + fixture.player.getHealth()
                        + "; attacks=" + fixture.attacks.stream().map(event -> event.getSource().type().msgId() + ":" + event.isCanceled()).toList());
        // W262 (R288): the real client's timing, one full server tick whose temporary step reached the pad between the last
        // airborne packet and the landing packet; or the named artificial pair, both packets in one tick.
        boolean timed = fixture.scenario == PACKET_PAIR ? drive.landed == drive.staged
                : drive.landed == drive.staged + 1 && drive.physicsY == fixture.gantry.getY()
                        && drive.stagedY - fixture.gantry.getY() <= .25 + 1e-6;
        fixture.helper.assertTrue(timed, "Packet landing did not use its named timing: case=" + fixture.scenario + "; staged=" + drive.staged
                + "; landed=" + drive.landed + "; stagedY=" + drive.stagedY + "; physicsY=" + drive.physicsY + "; pad=" + fixture.gantry.getY());
        fixture.done();
    }

    @GameTest(template = "rocket_test", batch = "bay_exit_ticked_descent", timeoutTicks = 400)
    public static void tickingConnectionRetainsTheAirborneOffer(GameTestHelper helper) {
        new BayPassengerExitGameTestFixture(helper, DESCENT, true).start();
    }
    @GameTest(template = "rocket_test", batch = "bay_exit_ticked_landing", timeoutTicks = 400)
    public static void tickingConnectionLandingCancelsExactlyOneFall(GameTestHelper helper) {
        new BayPassengerExitGameTestFixture(helper, ORDINARY, true).start();
    }
    @GameTest(template = "rocket_test", batch = "bay_exit_ticked_expiry", timeoutTicks = 400)
    public static void tickingConnectionKeepsTheOriginalExpiry(GameTestHelper helper) {
        new BayPassengerExitGameTestFixture(helper, EXPIRY, true).start();
    }
    @GameTest(template = "rocket_test", batch = "bay_exit_ticked_correction", timeoutTicks = 400)
    public static void tickingConnectionLaterCorrectionRevokesALiveOffer(GameTestHelper helper) {
        new BayPassengerExitGameTestFixture(helper, TELEPORT, true).start();
    }
    /** W262 (R288): a real client's packet timing, the landing packet one full server tick after the last airborne one. */
    @GameTest(template = "rocket_test", batch = "bay_exit_ticked_packet_landing", timeoutTicks = 400)
    public static void tickingConnectionPacketDescentCompletesItsLanding(GameTestHelper helper) {
        new BayPassengerExitGameTestFixture(helper, PACKET_LANDING, true).start();
    }
    /** Artificial: both final packets bunched into one server tick, W257's stimulus kept under its own name. */
    @GameTest(template = "rocket_test", batch = "bay_exit_ticked_packet_pair", timeoutTicks = 400)
    public static void tickingConnectionSameTickPacketPairCompletesItsLanding(GameTestHelper helper) {
        new BayPassengerExitGameTestFixture(helper, PACKET_PAIR, true).start();
    }
    /** W262 (W257-R2): a real-client-timed landing after a fall at 2.1 blocks per tick. */
    @GameTest(template = "rocket_test", batch = "bay_exit_ticked_fall_2_1", timeoutTicks = 400)
    public static void tickingConnectionFallAt2Point1BlocksPerTickCompletesItsLanding(GameTestHelper helper) {
        new BayPassengerExitGameTestFixture(helper, FALL_2_1, true).start();
    }
    /** W262 (W257-R2): a real-client-timed landing after a fall at 3.9 blocks per tick, near vanilla's terminal speed. */
    @GameTest(template = "rocket_test", batch = "bay_exit_ticked_fall_3_9", timeoutTicks = 400)
    public static void tickingConnectionFallAt3Point9BlocksPerTickCompletesItsLanding(GameTestHelper helper) {
        new BayPassengerExitGameTestFixture(helper, FALL_3_9, true).start();
    }

    @AfterBatch(batch = "bay_exit_ticked_descent") public static void afterDescent(ServerLevel level) { BayPassengerExitGameTestFixture.closeOpen(level); }
    @AfterBatch(batch = "bay_exit_ticked_landing") public static void afterLanding(ServerLevel level) { BayPassengerExitGameTestFixture.closeOpen(level); }
    @AfterBatch(batch = "bay_exit_ticked_expiry") public static void afterExpiry(ServerLevel level) { BayPassengerExitGameTestFixture.closeOpen(level); }
    @AfterBatch(batch = "bay_exit_ticked_correction") public static void afterCorrection(ServerLevel level) { BayPassengerExitGameTestFixture.closeOpen(level); }
    @AfterBatch(batch = "bay_exit_ticked_packet_landing") public static void afterPacketLanding(ServerLevel level) { BayPassengerExitGameTestFixture.closeOpen(level); }
    @AfterBatch(batch = "bay_exit_ticked_packet_pair") public static void afterPacketPair(ServerLevel level) { BayPassengerExitGameTestFixture.closeOpen(level); }
    @AfterBatch(batch = "bay_exit_ticked_fall_2_1") public static void afterFall21(ServerLevel level) { BayPassengerExitGameTestFixture.closeOpen(level); }
    @AfterBatch(batch = "bay_exit_ticked_fall_3_9") public static void afterFall39(ServerLevel level) { BayPassengerExitGameTestFixture.closeOpen(level); }
}
