package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.content.PlanetaryContent;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transfer.RocketLandingPadSelector;
import io.github.sunthemoon.advancedrocketrycommunity.travel.model.TravelTarget;
import java.util.LinkedHashMap;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Bounded support/fluid checks and a normal transfer whose reserved support is removed. */
@GameTestHolder(AdvancedRocketryCommunity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PlanetaryLandingGameTests {
    private PlanetaryLandingGameTests() {
    }

    @GameTest(template = "rocket_test", batch = "planetary_changed_pad", timeoutTicks = 240)
    public static void removedPlanetarySupportReturnsOneRocketWithOriginalFuelLedger(GameTestHelper helper) {
        changedSupport(helper, PlanetaryContent.MARS, Blocks.AIR.defaultBlockState());
    }

    @GameTest(template = "rocket_test", batch = "planetary_changed_pad", timeoutTicks = 240)
    public static void floodedPlanetarySupportIsRejectedBeforeFluidTicks(GameTestHelper helper) {
        changedSupport(helper, PlanetaryContent.VENUS, Blocks.WATER.defaultBlockState());
    }

    private static void changedSupport(GameTestHelper helper, ResourceLocation body, BlockState replacement) {
        helper.runAfterDelay(1, () -> {
            var source = RocketFlightGameTestFixtures.assembleFueledRocket(helper, new BlockPos(3, 2, 3), UUID.randomUUID());
            var server = helper.getLevel().getServer();
            var world = server.getLevel(PlanetaryContent.level(body));
            var logical = source.assemblyTransactionId().orElseThrow();
            var before = source.flightData().orElseThrow();
            var snapshot = source.snapshot().orElseThrow();
            var journal = RocketTransferSavedData.get(server);
            var restored = new LinkedHashMap<BlockPos, BlockState>();
            UUID transfer = UUID.randomUUID();
            Runnable cleanup = () -> {
                restored.forEach((pos, state) -> world.setBlock(pos, state, Block.UPDATE_ALL));
                PlanetaryWorldGameTests.cleanRocket(server, logical);
            };
            try {
                var result = RocketRuntime.requestAdminFlight(source, new TravelTarget.BodySurface(body), transfer);
                helper.assertTrue(result.success(), "Initial planetary pad was not admitted: " + result.code());
                var destination = journal.find(transfer).orElseThrow().destinationSnapshot();
                var selector = new RocketLandingPadSelector();
                helper.assertTrue(selector.available(world, destination, null, false), "Reserved pad was not available");
                for (var block : destination.blocks()) {
                    if (block.position().y() == destination.bounds().minimum().y()) {
                        var absolute = destination.sourceOrigin().add(block.position());
                        var below = new BlockPos(absolute.x(), absolute.y() - 1, absolute.z());
                        restored.put(below, world.getBlockState(below));
                        // Water is checked and restored synchronously below, before scheduled fluid ticks.
                        world.setBlock(below, replacement, Block.UPDATE_CLIENTS);
                    }
                }
                helper.assertTrue(!restored.isEmpty(), "Fixture did not change any support");
                helper.assertTrue(!selector.available(world, destination, null, false), "Changed support remained available");
                if (!replacement.isAir()) {
                    cleanup.run();
                    helper.succeed();
                    return;
                }
                helper.runAfterDelay(190, () -> {
                    try {
                        var state = source.flightData().orElseThrow();
                        helper.assertTrue(source.isAlive() && state.state() == RocketFlightState.FUELED,
                                "Changed pad did not return the live source");
                        helper.assertTrue(state.currentTarget().equals(before.currentTarget())
                                && state.currentOrigin().equals(before.currentOrigin())
                                && source.snapshot().orElseThrow().equals(snapshot), "Return changed source authority");
                        // PREPARED retains the source fuel; only destination authority owns the planned debit.
                        helper.assertTrue(state.fuel().equals(before.fuel()), "Rejected spawn changed the source fuel ledger");
                        helper.assertTrue(journal.find(transfer).isEmpty(), "Returned transfer retained its reservation");
                        int matches = 0;
                        for (var level : server.getAllLevels()) {
                            for (var entity : level.getAllEntities()) {
                                if (entity instanceof RocketEntity rocket
                                        && rocket.assemblyTransactionId().filter(logical::equals).isPresent()) {
                                    matches++;
                                }
                            }
                        }
                        helper.assertTrue(matches == 1, "Changed pad duplicated or lost the logical rocket");
                        helper.succeed();
                    } finally {
                        cleanup.run();
                    }
                });
            } catch (RuntimeException | Error exception) {
                cleanup.run();
                throw exception;
            }
        });
    }
}
