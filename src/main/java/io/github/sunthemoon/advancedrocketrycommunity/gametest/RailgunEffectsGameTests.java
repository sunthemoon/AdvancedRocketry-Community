package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.railgun.RailgunBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RailgunEffectsGameTests {
    private RailgunEffectsGameTests() { }

    @GameTest(template = "empty", batch = "railgun_effect_events")
    public static void serverAcceptsOnlyLaunchAndArrivalEvents(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos, ModBlocks.RAILGUN.get());
        var level = helper.getLevel();
        BlockPos absolute = helper.absolutePos(pos);
        var state = level.getBlockState(absolute);
        helper.assertTrue(level.getBlockEntity(absolute) instanceof RailgunBlockEntity, "Missing railgun");
        // Exercise the same block -> block entity dispatch used by ServerLevel's broadcast admission.
        helper.assertTrue(state.triggerEvent(level, absolute, RailgunBlockEntity.EVENT_LAUNCH, 0),
                "Server rejected the launch event");
        helper.assertTrue(state.triggerEvent(level, absolute, RailgunBlockEntity.EVENT_ARRIVAL, 0),
                "Server rejected the arrival event");
        helper.assertTrue(!state.triggerEvent(level, absolute, 99, 0), "Server accepted an unknown event");
        helper.succeed();
    }
}
