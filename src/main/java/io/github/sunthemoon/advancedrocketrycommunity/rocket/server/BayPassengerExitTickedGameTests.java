package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static io.github.sunthemoon.advancedrocketrycommunity.rocket.server.BayPassengerExitGameTestFixture.Case.*;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
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

    static void pump(BayPassengerExitGameTestFixture fixture) {
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
        // Match X4's ten-block packet descent, rather than spending seventeen ticks at its three-block packet speed.
        fixture.gantry = net.minecraft.core.BlockPos.containing(fixture.player.getX(),
                Math.floor(fixture.player.getY()) - 10, fixture.player.getZ());
        fixture.pad(fixture.gantry);
        BayPassengerExitReleaseDrive.listen();
        BayPassengerExitReleaseDrive.landAt(fixture.player, logical, fixture.gantry.getY());
        var drive = BayPassengerExitReleaseDrive.drive(fixture.player, logical);
        drive.acked = fixture.acknowledgedCorrection; // The fixture already accepted the exit correction once.
        drive.tracked = fixture.offer; drive.trace = BayPassengerExitReleaseDrive.TRACE_TICKS;
    }

    static void checkPacketLanding(BayPassengerExitGameTestFixture fixture) {
        if (fixture.falls == 0) {
            fixture.helper.assertTrue(fixture.exits.state.live.get(fixture.player) == fixture.offer
                    && !fixture.offer.descent.closed, "Packet descent ended before its landing");
            if (!fixture.injected) {
                fixture.injected = true;
                BayPassengerExitReleaseDrive.descend(fixture.player, fixture.flight.record().logicalRocketId(), 0);
            }
            return;
        }
        fixture.helper.assertTrue(fixture.player.onGround() && fixture.player.getY() == fixture.gantry.getY()
                && fixture.player.getHealth() == 20
                && fixture.attacks.stream().filter(net.minecraftforge.event.entity.living.LivingAttackEvent::isCanceled).count() == 1,
                "Packet landing did not cancel exactly one fall: health=" + fixture.player.getHealth()
                        + "; attacks=" + fixture.attacks.stream().map(event -> event.getSource().type().msgId() + ":" + event.isCanceled()).toList());
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
    @GameTest(template = "rocket_test", batch = "bay_exit_ticked_packet_landing", timeoutTicks = 400)
    public static void tickingConnectionPacketDescentCompletesItsLanding(GameTestHelper helper) {
        new BayPassengerExitGameTestFixture(helper, PACKET_LANDING, true).start();
    }

    @AfterBatch(batch = "bay_exit_ticked_descent") public static void afterDescent(ServerLevel level) { BayPassengerExitGameTestFixture.closeOpen(level); }
    @AfterBatch(batch = "bay_exit_ticked_landing") public static void afterLanding(ServerLevel level) { BayPassengerExitGameTestFixture.closeOpen(level); }
    @AfterBatch(batch = "bay_exit_ticked_expiry") public static void afterExpiry(ServerLevel level) { BayPassengerExitGameTestFixture.closeOpen(level); }
    @AfterBatch(batch = "bay_exit_ticked_correction") public static void afterCorrection(ServerLevel level) { BayPassengerExitGameTestFixture.closeOpen(level); }
    @AfterBatch(batch = "bay_exit_ticked_packet_landing") public static void afterPacketLanding(ServerLevel level) { BayPassengerExitGameTestFixture.closeOpen(level); }
}
