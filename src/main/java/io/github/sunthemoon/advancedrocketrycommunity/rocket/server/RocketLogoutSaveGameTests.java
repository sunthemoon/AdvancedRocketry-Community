package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightState;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** W312: observe the native logout-written file and a real login, not a synthetic relocation. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RocketLogoutSaveGameTests {
    private RocketLogoutSaveGameTests() { }

    @GameTest(template = "rocket_test", batch = "rocket_logout_save", timeoutTicks = 160)
    public static void offlineOperatorReleaseRejoinsOutsideTheTallRocketUnhurt(GameTestHelper helper) {
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(helper.getLevel().areEntitiesLoaded(
                new ChunkPos(helper.absolutePos(RocketLogoutSaveFixture.ORIGIN)).toLong()), "Logout fixture is not loaded"))
                .thenExecute(() -> {
                    try (var f = new RocketLogoutSaveFixture(helper, RocketFlightState.ASSEMBLED)) {
                        f.logout();
                        var source = helper.getLevel().getServer().createCommandSourceStack().withLevel(helper.getLevel()).withPermission(2);
                        int released = source.getServer().getCommands().performPrefixedCommand(source,
                                "arce rocket release-seat " + f.rocket.getStringUUID() + " " + f.playerId);
                        helper.assertTrue(released == 1 && f.rocket.flightData().orElseThrow().passengers().assignment(f.playerId).isEmpty(),
                                "Operator did not release the offline seat");
                        f.player = f.join();
                        helper.assertTrue(!f.player.isPassenger() && f.player.position().distanceTo(f.exit) < 1e-7,
                                "Released rider did not rejoin at the exit: expected=" + f.exit + " saved="
                                        + RocketLogoutSaveFixture.position(f.disk()) + " actual=" + f.player.position());
                        helper.assertTrue(!f.player.getBoundingBox().intersects(f.rocket.getBoundingBox()), "Rejoined inside the rocket");
                        helper.assertTrue(f.player.getHealth() == f.player.getMaxHealth(), "Released rider lost health");
                    } catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
                }).thenSucceed();
    }
}
