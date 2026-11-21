package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.slf4j.LoggerFactory;

/** Opt-in external-light perturbation. It never changes a measured structure or any expected light value. */
final class LightTransmissionNeighbours {
    static final String PROPERTY = "advancedrocketrycommunity.lightTransmissionNeighbours";

    private LightTransmissionNeighbours() { }

    static List<TestFunction> tests() {
        if (!Boolean.getBoolean(PROPERTY)) return List.of();
        return List.of("motor_occlusion", "bipropellant_motor_occlusion", "nuclear_motor_occlusion",
                "fuel_tank_occlusion", "saw_occlusion").stream().map(batch -> new TestFunction(batch,
                "lighttransmissionneighbours." + batch, ModIdentity.MOD_ID + ":empty", 100, 0L, true,
                LightTransmissionNeighbours::illuminate)).toList();
    }

    private static void illuminate(GameTestHelper helper) {
        var driver = new GameTestTickDriver(helper);
        var level = helper.getLevel();
        BlockPos source = helper.absolutePos(new BlockPos(0, 2, 0));
        BlockPos openProbe = source.east();
        // Use the structure's centre, not the overlapping clearance margins of neighbouring empty templates.
        // Like ordinary GameTest structures, this opt-in source remains until the disposable world is cleared.
        // Removing it on a timer could let the measured test pass only AFTER the perturbation disappeared.
        for (BlockPos pos : List.of(source, openProbe)) {
            helper.assertTrue(level.hasChunkAt(pos) && level.getBlockEntity(pos) == null,
                    "Neighbour light fixture is unloaded or contains a block entity");
        }
        helper.assertTrue(level.getBlockState(openProbe).isAir(), "Neighbour's open light probe is obstructed");
        level.setBlock(source, Blocks.GLOWSTONE.defaultBlockState(), Block.UPDATE_ALL);
        var propagation = LightPropagationBarrier.afterChanges(level, List.of(source));
        driver.at(90, () -> {
            helper.assertTrue(propagation.isDone(), "Neighbour source light did not finish propagating");
            propagation.join();
            helper.assertTrue(level.getBlockState(source).is(Blocks.GLOWSTONE)
                            && level.getBrightness(LightLayer.BLOCK, source) == 15
                            && level.getBrightness(LightLayer.BLOCK, openProbe) == 14,
                    "Neighbour structure did not emit block light into the open air");
            LoggerFactory.getLogger(LightTransmissionNeighbours.class).info(
                    "Neighbour structure emitted 15 at {} with open probe=14; source retained until world cleanup", source);
            helper.succeed();
        });
    }
}
