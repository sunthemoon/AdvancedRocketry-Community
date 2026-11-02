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

    @AfterBatch(batch = "bay_exit_ticked_descent") public static void afterDescent(ServerLevel level) { BayPassengerExitGameTestFixture.closeOpen(level); }
    @AfterBatch(batch = "bay_exit_ticked_landing") public static void afterLanding(ServerLevel level) { BayPassengerExitGameTestFixture.closeOpen(level); }
    @AfterBatch(batch = "bay_exit_ticked_expiry") public static void afterExpiry(ServerLevel level) { BayPassengerExitGameTestFixture.closeOpen(level); }
    @AfterBatch(batch = "bay_exit_ticked_correction") public static void afterCorrection(ServerLevel level) { BayPassengerExitGameTestFixture.closeOpen(level); }
}
