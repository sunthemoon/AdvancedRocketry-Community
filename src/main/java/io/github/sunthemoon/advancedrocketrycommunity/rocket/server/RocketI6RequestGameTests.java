package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.gametest.GameTestTickPacer;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.RocketBlockEntityAdapters;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** The real request adapter's authority, separate from native executor and journal observations. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RocketI6RequestGameTests {
    private static final List<RocketEntity> OWNED = new ArrayList<>();
    private RocketI6RequestGameTests() { }

    @GameTest(template = "rocket_test", batch = "w164_request", timeoutTicks = 400)
    public static void originalAgeAndExecutorRemainBoundToTheRequest(GameTestHelper helper) {
        var level = helper.getLevel(); var server = level.getServer(); var pacer = new GameTestTickPacer(server);
        var request = new RocketLandedDisassemblies.Request[1]; var rocket = new RocketEntity[1];
        helper.onEachTick(() -> {
            pacer.pace(); long now = server.overworld().getGameTime();
            if (request[0] == null) {
                var at = helper.absolutePos(new BlockPos(3, 2, 3));
                if (!level.areEntitiesLoaded(net.minecraft.world.level.ChunkPos.asLong(at.getX() >> 4, at.getZ() >> 4))) { return; }
                var fixture = RocketSourceRetirementFixtures.countdown(level, at.east(), at, List.of(), 0);
                rocket[0] = fixture.spawnDestination(helper, level); OWNED.add(rocket[0]);
                rocket[0].updateFlightData(rocket[0].flightData().orElseThrow().land(now));
                RocketTransferEntities.positionAtOrigin(rocket[0], fixture.record().destinationSnapshot().sourceOrigin());
                var flights = new RocketFlightService(null, null);
                var transactions = new RocketTransactionExecutor(RocketBlockEntityAdapters.defaults());
                var sealed = new SealedDisassemblyCoordinator(transactions, flights.transfers().ordinaryRetirements());
                var service = new RocketDisassemblyService(transactions, flights, sealed);
                var player = new RocketLandedDisassemblyGameTestSupport.Requester(level, fixture.record().ownerId());
                player.setPos(rocket[0].getX() + 2, rocket[0].getY(), rocket[0].getZ());
                request[0] = service.landedRequest(rocket[0], player, false);
                helper.assertTrue(request[0].requestedAt() == now && request[0].authorized()
                        && request[0].retainedExecutor(), "Request did not capture current ordinary authority");
                return;
            }
            long age = now - request[0].requestedAt();
            if (age <= RocketDisassemblyConfirmations.LIFETIME_TICKS) {
                helper.assertTrue(request[0].authorized(), "Authority expired before its existing deadline"); return;
            }
            helper.assertTrue(!request[0].authorized(), "B2 could refresh M0's original request age");
            rocket[0].discard();
            helper.assertTrue(request[0].retainedExecutor(), "A stale ordinary request fell back to synchronous execution");
            helper.assertTrue(!request[0].beginRetained(result -> { throw new AssertionError("Stale request completed"); }),
                    "Expired request admitted a new executor");
            helper.succeed();
        });
    }
    @AfterBatch(batch = "w164_request") public static void cleanup(ServerLevel level) {
        OWNED.forEach(rocket -> { if (rocket.isAlive()) { rocket.discard(); } }); OWNED.clear();
    }
}
