package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(AdvancedRocketryCommunity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BootstrapGameTests {
    private BootstrapGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void approvedIdentityIsAvailable(GameTestHelper helper) {
        helper.assertTrue(
                AdvancedRocketryCommunity.MOD_ID.equals(ModIdentity.MOD_ID),
                "The Forge entrypoint and approved project identity must use the same mod id"
        );
        helper.succeed();
    }
}
