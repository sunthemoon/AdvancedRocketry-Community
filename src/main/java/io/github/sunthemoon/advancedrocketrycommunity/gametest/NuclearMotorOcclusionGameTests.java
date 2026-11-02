package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.RocketNuclearNozzleBlock;
import java.util.Arrays;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** W258: every existing nuclear nozzle orientation retains support/sealing while transmitting light. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NuclearMotorOcclusionGameTests {
    private NuclearMotorOcclusionGameTests() { }

    @GameTest(template = "empty", batch = "nuclear_motor_occlusion", timeoutTicks = 100)
    public static void everyNuclearFacingPassesLightWithoutChangingCollisionOrSealing(GameTestHelper helper) {
        MonoMotorOcclusionGameTests.assertNonOccludingColumns(helper, Arrays.stream(Direction.values())
                .map(facing -> ModBlocks.NUCLEAR_ROCKET_MOTOR.get().defaultBlockState()
                        .setValue(RocketNuclearNozzleBlock.FACING, facing)).toList());
    }
}
