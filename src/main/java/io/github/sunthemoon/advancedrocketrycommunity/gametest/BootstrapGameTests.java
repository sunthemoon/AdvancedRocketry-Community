package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.content.MachineCasingBlock;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModCreativeTabs;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.registries.ForgeRegistries;
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

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void contentRegistriesContainExpectedEntries(GameTestHelper helper) {
        helper.assertTrue(ModBlocks.MACHINE_CASING.isPresent(), "Machine casing block is not registered");
        helper.assertTrue(ModItems.MACHINE_CASING.isPresent(), "Machine casing item is not registered");
        helper.assertTrue(ModItems.SILICON_WAFER.isPresent(), "Silicon wafer is not registered");
        helper.assertTrue(ModItems.BASIC_CIRCUIT.isPresent(), "Basic circuit is not registered");
        helper.assertTrue(ModItems.ADVANCED_CIRCUIT.isPresent(), "Advanced circuit is not registered");
        helper.assertTrue(ModItems.DATA_STORAGE_UNIT.isPresent(), "Data storage unit is not registered");
        helper.assertTrue(ModSounds.UI_SELECT.isPresent(), "UI select sound is not registered");
        helper.assertTrue(ModCreativeTabs.MAIN.isPresent(), "Creative tab is not registered");
        helper.assertTrue(
                ModIdentity.id("machine_casing").equals(
                        ForgeRegistries.BLOCKS.getKey(ModBlocks.MACHINE_CASING.get())
                ),
                "Machine casing block has an unexpected registry key"
        );
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void machineCasingPlacementAndDropAreValid(GameTestHelper helper) {
        BlockPos position = BlockPos.ZERO;
        helper.setBlock(
                position,
                ModBlocks.MACHINE_CASING.get().defaultBlockState()
                        .setValue(MachineCasingBlock.FACING, Direction.EAST)
        );
        helper.assertBlockPresent(ModBlocks.MACHINE_CASING.get(), position);
        helper.assertBlockProperty(position, MachineCasingBlock.FACING, Direction.EAST);
        helper.assertTrue(
                helper.getLevel().destroyBlock(helper.absolutePos(position), true),
                "Machine casing could not be destroyed with block drops enabled"
        );
        helper.succeedWhen(() -> helper.assertItemEntityPresent(
                ModItems.MACHINE_CASING.get(),
                position,
                1.0D
        ));
    }
}
