package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RailBlock;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Terrain cleanup must end before automation starts; every later loose item is still a spill. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AutomationRoomGameTests {
    private static final int TIMEOUT = 40;

    @GameTest(template = "empty", batch = "w68_room_carving", timeoutTicks = TIMEOUT)
    public static void carvingSupportedRailsClearsShellDropsBeforeTheyEnterRoom(GameTestHelper helper) {
        var rig = new AutomationRig(helper);
        try {
            BlockPos min = helper.absolutePos(new BlockPos(1, 2, 1)), max = min.offset(4, 2, 4);
            rig.room(min, max);
            BlockPos lower = min.offset(2, 2, 2), upper = lower.west().above();
            // The ascending rail is removed after both supports, but before the upper rail in the ceiling shell.
            // Keep the upper rail west: betweenClosed visits X first, so its support is carved before the lower rail.
            // Its onRemove notifies above it even with UPDATE_CLIENTS, dropping the now unsupported upper rail.
            rig.place(lower.below(), Blocks.STONE.defaultBlockState());
            rig.place(upper.below(), Blocks.STONE.defaultBlockState());
            rig.place(upper, Blocks.RAIL.defaultBlockState().setValue(RailBlock.SHAPE, RailShape.EAST_WEST));
            rig.place(lower, Blocks.RAIL.defaultBlockState().setValue(RailBlock.SHAPE, RailShape.ASCENDING_WEST));
            helper.assertTrue(rig.level.getBlockState(lower).getValue(RailBlock.SHAPE) == RailShape.ASCENDING_WEST
                    && rig.level.getBlockState(upper).is(Blocks.RAIL)
                    && rig.level.getBlockState(upper).canSurvive(rig.level, upper), "Supported rail fixture differs");
            var carved = new AABB(min.offset(-1, -1, -1), max.offset(2, 2, 2));
            helper.assertTrue(rig.level.getEntitiesOfClass(ItemEntity.class, carved).isEmpty(),
                    "Rail placement itself dropped an item");

            rig.room(min, max);
            helper.assertTrue(rig.level.getBlockState(upper).is(Blocks.STONE), "Room ceiling was not carved");
            var remaining = rig.level.getEntitiesOfClass(ItemEntity.class, carved);
            helper.assertTrue(remaining.isEmpty(), "Room carving left terrain drops in the shell: "
                    + remaining.stream().map(entity -> entity.getItem() + " at " + entity.blockPosition().toShortString()).toList());
            rig.drive(TIMEOUT, () -> {
                helper.assertTrue(rig.spilled(min, 1).isEmpty(), "A terrain drop entered the room after carving");
                helper.assertTrue(rig.level.getEntitiesOfClass(ItemEntity.class, carved).isEmpty(),
                        "A terrain drop remained in the carved volume");
                return helper.getTick() >= 20;
            }, () -> { });
        } catch (RuntimeException | Error failure) { rig.close(); throw failure; }
    }

    @GameTest(template = "empty", batch = "w68_room_carving", timeoutTicks = TIMEOUT)
    public static void roomCleanupStillReportsImmediateAndLaterRealSpills(GameTestHelper helper) {
        var rig = new AutomationRig(helper);
        try {
            BlockPos min = helper.absolutePos(new BlockPos(1, 2, 1)), max = min.offset(4, 2, 4);
            rig.room(min, max);
            // An item produced immediately after room() must not be erased by a deferred terrain cleanup.
            var immediate = spill(rig, min.offset(1, 0, 1), new ItemStack(Items.IRON_INGOT));
            assertSpill(rig, min, immediate);
            ItemEntity[] later = {null};
            rig.drive(TIMEOUT, () -> {
                assertSpill(rig, min, later[0] == null ? immediate : later[0]);
                if (helper.getTick() >= 2 && later[0] == null) {
                    immediate.discard();
                    // Rails are not excluded by item type either, even though terrain can produce them.
                    later[0] = spill(rig, min.offset(3, 0, 3), new ItemStack(Items.RAIL));
                }
                return helper.getTick() >= 20;
            }, () -> assertSpill(rig, min, later[0]));
        } catch (RuntimeException | Error failure) { rig.close(); throw failure; }
    }

    private static void assertSpill(AutomationRig rig, BlockPos center, ItemEntity entity) {
        String expected = entity.getItem() + " at " + entity.blockPosition().toShortString();
        rig.helper.assertTrue(entity.isAlive() && rig.spilled(center, 1).equals(expected), "Real spill was hidden: " + expected);
    }

    private static ItemEntity spill(AutomationRig rig, BlockPos at, ItemStack stack) {
        var entity = new ItemEntity(rig.level, at.getX() + 0.5, at.getY() + 0.25, at.getZ() + 0.5, stack);
        entity.setNoGravity(true);
        entity.setDeltaMovement(0, 0, 0);
        rig.helper.assertTrue(rig.level.addFreshEntity(entity), "Could not create spill fixture");
        return entity;
    }

    private AutomationRoomGameTests() { }
}
