package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import java.util.LinkedHashMap;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
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
        var original = new LinkedHashMap<BlockPos, BlockState>();
        // A luminous neighbouring structure, within its own empty template's actual cleared volume.
        for (BlockPos pos : BlockPos.betweenClosed(helper.absolutePos(new BlockPos(-2, 1, -2)),
                helper.absolutePos(new BlockPos(2, 4, 2)))) {
            helper.assertTrue(level.hasChunkAt(pos) && level.getBlockEntity(pos) == null,
                    "Neighbour light fixture is unloaded or contains a block entity");
            original.put(pos.immutable(), level.getBlockState(pos));
        }
        original.keySet().forEach(pos -> level.setBlock(pos, Blocks.GLOWSTONE.defaultBlockState(), Block.UPDATE_ALL));
        var propagation = LightPropagationBarrier.afterChanges(level, original.keySet());
        driver.at(90, () -> {
            try {
                helper.assertTrue(propagation.isDone(), "Neighbour source light did not finish propagating");
                propagation.join();
                BlockPos source = helper.absolutePos(new BlockPos(0, 2, 0));
                helper.assertTrue(level.getBrightness(LightLayer.BLOCK, source) == 15,
                        "Neighbour structure did not emit block light");
                LoggerFactory.getLogger(LightTransmissionNeighbours.class).info("Neighbour structure emitted 15 at {}", source);
                helper.succeed();
            } finally {
                original.forEach((pos, state) -> level.setBlock(pos, state, Block.UPDATE_ALL));
            }
        });
    }
}
