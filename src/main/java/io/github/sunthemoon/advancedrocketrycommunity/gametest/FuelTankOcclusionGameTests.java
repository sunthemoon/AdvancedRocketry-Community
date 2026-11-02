package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** W260: standalone tank meshes transmit light, while collision/support and atmosphere sealing stay solid. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FuelTankOcclusionGameTests {
    private FuelTankOcclusionGameTests() { }

    @GameTest(template = "empty", batch = "fuel_tank_occlusion", timeoutTicks = 100)
    public static void allThreeFuelTanksKeepSupportAndSealingWithoutOccluding(GameTestHelper helper) {
        MonoMotorOcclusionGameTests.assertNonOccludingColumns(helper, List.of(
                ModBlocks.ROCKET_FUEL_TANK.get().defaultBlockState(),
                ModBlocks.BIPROPELLANT_FUEL_TANK.get().defaultBlockState(),
                ModBlocks.OXIDIZER_FUEL_TANK.get().defaultBlockState()));
    }
}
