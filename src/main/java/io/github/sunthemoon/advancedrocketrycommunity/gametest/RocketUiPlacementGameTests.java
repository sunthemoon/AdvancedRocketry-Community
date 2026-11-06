package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import java.util.ArrayList;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RocketUiPlacementGameTests {
    private RocketUiPlacementGameTests() { }

    @GameTest(template = "rocket_test", batch = "w289_placement", timeoutTicks = 80)
    public static void assemblerAllowsUnsneakedBlockPlacementInEitherHand(GameTestHelper helper) {
        placement(helper, ModBlocks.ROCKET_ASSEMBLER.get());
    }

    @GameTest(template = "rocket_test", batch = "w289_placement", timeoutTicks = 80)
    public static void casingAllowsUnsneakedBlockPlacementInEitherHand(GameTestHelper helper) {
        placement(helper, ModBlocks.MACHINE_CASING.get());
    }

    private static void placement(GameTestHelper helper, Block base) {
        var level = helper.getLevel();
        var relative = new BlockPos(3, 2, 3);
        helper.setBlock(relative, base);
        var at = helper.absolutePos(relative);
        var replies = new ArrayList<String>();
        var player = ConnectedTestPlayers.join(level.getServer(), UUID.randomUUID(), "rocketBuilder", level, at.east(2), replies);
        try {
            player.setShiftKeyDown(false);
            for (var hand : InteractionHand.values()) {
                helper.setBlock(relative.above(), Blocks.AIR);
                player.setItemInHand(hand, new ItemStack(Items.STONE, 2));
                replies.clear();
                var hit = new BlockHitResult(Vec3.atCenterOf(at).add(0, .5, 0), Direction.UP, at, false);
                if (hand == InteractionHand.OFF_HAND) {
                    helper.assertTrue(player.gameMode.useItemOn(player, level, player.getMainHandItem(),
                            InteractionHand.MAIN_HAND, hit) == InteractionResult.PASS,
                            "Empty main hand consumed the off-hand block placement");
                }
                var result = player.gameMode.useItemOn(player, level, player.getItemInHand(hand), hand, hit);
                helper.assertTrue(result.consumesAction() && level.getBlockState(at.above()).is(Blocks.STONE),
                        "Unsneaked block placement was consumed by " + base + " in " + hand);
                helper.assertTrue(replies.isEmpty(), "Placement emitted machine feedback: " + replies);
                player.setItemInHand(hand, ItemStack.EMPTY);
            }
            for (var item : new ItemStack[]{ItemStack.EMPTY, new ItemStack(Items.STICK)}) {
                player.setItemInHand(InteractionHand.MAIN_HAND, item);
                var state = level.getBlockState(at);
                helper.assertTrue(state.use(level, player, InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atCenterOf(at), Direction.UP, at, false)) == InteractionResult.CONSUME,
                        "Empty-hand/other-item behavior changed for " + base);
            }
        } finally {
            level.getServer().getPlayerList().remove(player);
            FluidFuelLoaderFixtures.clear(level, at);
        }
        helper.succeed();
    }
}
