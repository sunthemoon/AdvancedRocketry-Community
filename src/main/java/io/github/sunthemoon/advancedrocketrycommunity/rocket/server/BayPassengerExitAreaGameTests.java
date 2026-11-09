package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Pre-existing block entities are never erased, whether a window can be placed or must be refused. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BayPassengerExitAreaGameTests {
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    private BayPassengerExitAreaGameTests() { }

    @GameTest(template = "rocket_test", batch = "w334_bay_exit_area", timeoutTicks = 20)
    public static void leftoverAboveVanillaClearanceIsAvoidedWithoutLosingItsInventory(GameTestHelper helper) {
        var level = helper.getLevel();
        // The historical cell is at relative (1, 58, 7), well above the template's +35 cleared ceiling.
        var leftover = helper.absolutePos(new BlockPos(1, 58, 7));
        var marker = helper.absolutePos(new BlockPos(0, 1, 0));
        var before = level.getBlockState(leftover);
        var markerBefore = level.getBlockState(marker);
        helper.assertTrue(level.getBlockEntity(leftover) == null, "Regression seed would replace a block entity");
        try {
            level.setBlock(leftover, Blocks.CHEST.defaultBlockState(), FLAGS);
            var chest = (ChestBlockEntity) level.getBlockEntity(leftover);
            helper.assertTrue(chest != null, "Regression chest is absent");
            chest.setItem(0, new ItemStack(Items.DIAMOND, 3));
            int chunks = level.getChunkSource().getLoadedChunksCount();
            var forced = new java.util.HashSet<>(level.getForcedChunks());
            boolean refused = false;
            try (var ignored = BayPassengerExitGameTestArea.prepare(level, helper.absolutePos(new BlockPos(5, 4, 5)))) {
                helper.fail("Occupied window was accepted");
            } catch (IllegalStateException expected) {
                refused = expected.getMessage().contains("would replace a block entity at " + leftover);
            }
            helper.assertTrue(refused && level.getBlockState(marker).equals(markerBefore), "Refusal changed a cell or lost its diagnostic");
            var origin = BayPassengerExitGameTestArea.origin(helper);
            var minimum = helper.absolutePos(new BlockPos(5, 4, 5));
            var maximum = helper.absolutePos(new BlockPos(10, 4, 10));
            helper.assertTrue(origin.getX() >= Math.min(minimum.getX(), maximum.getX())
                    && origin.getX() <= Math.max(minimum.getX(), maximum.getX())
                    && origin.getZ() >= Math.min(minimum.getZ(), maximum.getZ())
                    && origin.getZ() <= Math.max(minimum.getZ(), maximum.getZ())
                    && origin.getY() == minimum.getY(), "Chosen window leaves its allocation");
            helper.assertTrue(Math.abs(origin.getX() - leftover.getX()) > 5 || Math.abs(origin.getZ() - leftover.getZ()) > 5,
                    "Window did not avoid the seeded leftover");
            try (var ignored = BayPassengerExitGameTestArea.prepare(level, origin)) {
                helper.assertTrue(level.getBlockState(origin).isAir(), "Chosen window was not prepared");
                helper.assertTrue(level.getBlockEntity(leftover) == chest && chest.getItem(0).getCount() == 3,
                        "Preparation changed the avoided block entity");
            }
            helper.assertTrue(level.getBlockEntity(leftover) == chest && chest.getItem(0).is(Items.DIAMOND)
                    && chest.getItem(0).getCount() == 3, "Restoration changed the avoided inventory");
            helper.assertTrue(level.getChunkSource().getLoadedChunksCount() == chunks && level.getForcedChunks().equals(forced),
                    "Window selection or preparation loaded or forced a chunk");
        } finally {
            if (level.getBlockEntity(leftover) instanceof ChestBlockEntity chest) { chest.clearContent(); }
            level.setBlock(leftover, before, FLAGS);
        }
        helper.succeed();
    }

    @GameTest(template = "rocket_test", batch = "w334_bay_exit_area", timeoutTicks = 20)
    public static void unsealedDescentAvoidsRejectingBlockEntitiesOutsideItsSmallerFootprint(GameTestHelper helper) {
        var level = helper.getLevel();
        var leftover = helper.absolutePos(new BlockPos(5, 56, 8));
        var before = level.getBlockState(leftover);
        helper.assertTrue(level.getBlockEntity(leftover) == null, "Regression seed would replace a block entity");
        try {
            level.setBlock(leftover, Blocks.CHEST.defaultBlockState(), FLAGS);
            var chest = level.getBlockEntity(leftover);
            boolean refused = false;
            try { BayPassengerExitGameTestArea.origin(helper); }
            catch (IllegalStateException expected) { refused = expected.getMessage().contains("no BE-free window"); }
            helper.assertTrue(refused, "Control BE must obstruct every full-size window");
            var origin = BayPassengerExitGameTestArea.origin(helper, 3);
            var minimum = helper.absolutePos(new BlockPos(3, 4, 3));
            var maximum = helper.absolutePos(new BlockPos(12, 4, 12));
            helper.assertTrue(origin.getX() >= minimum.getX() && origin.getX() <= maximum.getX()
                    && origin.getZ() >= minimum.getZ() && origin.getZ() <= maximum.getZ() && origin.getY() == minimum.getY(),
                    "Descent window leaves its allocation");
            helper.assertTrue((Math.abs(origin.getX() - leftover.getX()) > 3 || Math.abs(origin.getZ() - leftover.getZ()) > 3)
                    && level.getBlockEntity(leftover) == chest, "Descent selection touched or retained the BE in its footprint");
        } finally {
            level.setBlock(leftover, before, FLAGS);
        }
        helper.succeed();
    }

    @GameTest(template = "rocket_test", batch = "w334_bay_exit_area", timeoutTicks = 20)
    public static void blockEntitySharedByEveryCandidateRefusesWithoutAnyWrite(GameTestHelper helper) {
        var level = helper.getLevel();
        var leftover = helper.absolutePos(new BlockPos(8, 58, 8));
        var marker = helper.absolutePos(new BlockPos(0, 1, 0));
        var before = level.getBlockState(leftover);
        var markerBefore = level.getBlockState(marker);
        helper.assertTrue(level.getBlockEntity(leftover) == null, "Regression seed would replace a block entity");
        try {
            level.setBlock(leftover, Blocks.CHEST.defaultBlockState(), FLAGS);
            var chest = level.getBlockEntity(leftover);
            boolean refused = false;
            try { BayPassengerExitGameTestArea.origin(helper); }
            catch (IllegalStateException expected) { refused = expected.getMessage().contains("no BE-free window"); }
            helper.assertTrue(refused && level.getBlockEntity(leftover) == chest && level.getBlockState(marker).equals(markerBefore),
                    "Exhausted selection accepted or changed an occupied window");
        } finally {
            level.setBlock(leftover, before, FLAGS);
        }
        helper.succeed();
    }
}
