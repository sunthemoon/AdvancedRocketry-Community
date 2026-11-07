package io.github.sunthemoon.arceadaptertest;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.registries.ForgeRegistries;

/** Fixture-only control retirement and empty pre-operation regions with unfiltered drop observations. */
final class AdapterDropFixture {
    static void prepare(GameTestHelper helper) {
        var world = new AdapterWorldFixture(helper);
        // GameTestInfo places this control outside the template. Water can destroy it while its
        // command-block support remains intact, and its item can drift into a zero-drop region.
        BlockPos button = helper.absolutePos(new BlockPos(1, 0, -2));
        if (helper.getLevel().getBlockState(button).is(Blocks.STONE_BUTTON)
                && helper.getLevel().getBlockState(button.south()).is(Blocks.COMMAND_BLOCK)) {
            world.setOutside(button, Blocks.AIR.defaultBlockState());
            helper.assertTrue(helper.getLevel().getBlockState(button).isAir(), "Could not retire fixture start button");
        }
        world.isolateTerrain();
        // Template placement can drop decorations, and earlier tests can leave items here.
        // Clear before placing members/cargo; all later observations still require zero items.
        clearPreexistingDrops(helper, new AABB(helper.absolutePos(new BlockPos(3, 2, 3))).inflate(3));
    }

    static void withFlowingStartControlWater(GameTestHelper helper, Runnable setupAndOperation) {
        BlockPos button = helper.absolutePos(new BlockPos(1, 0, -2));
        var level = helper.getLevel();
        helper.assertTrue(level.getBlockState(button).is(Blocks.STONE_BUTTON)
                        && level.getBlockState(button.south()).is(Blocks.COMMAND_BLOCK),
                "Framework start control was not placed at the expected position");
        prepareStartControlObservation(helper, button);
        setupAndOperation.run();
        flowAcrossStartControl(helper, button);
        helper.runAtTickTime(8, () -> {
            helper.assertTrue(!level.getFluidState(button).isEmpty(), "Water did not reach the start-control position");
            helper.assertTrue(level.getBlockState(button.south()).is(Blocks.COMMAND_BLOCK),
                    "Water test removed the start control's support");
            assertNoDrops(helper, new AABB(button).inflate(2), "Water dropped a fixture start button");
        });
    }

    static void flowAcrossStartControl(GameTestHelper helper, BlockPos button) {
        var level = helper.getLevel();
        var world = new AdapterWorldFixture(helper);
        BlockPos source = button.north();
        helper.assertTrue(level.getBlockState(button).isAir(), "Start control must be retired before water setup");
        // A bounded channel removes terrain-dependent downhill alternatives. Reset the source so
        // an already-present water block also gets a fresh fluid tick; never replace the button.
        world.setOutside(source, Blocks.STONE.defaultBlockState());
        var walls = java.util.List.of(button.below(), source.below(), button.east(), button.west(),
                source.east(), source.west(), source.north(), button.above(), source.above());
        for (BlockPos wall : walls) {
            world.setOutside(wall, Blocks.STONE.defaultBlockState());
        }
        level.setBlockAndUpdate(source, Blocks.WATER.defaultBlockState());
        helper.runAtTickTime(8, () -> {
            AdapterWorldFixture.assertTerrainIntact(helper);
            for (BlockPos wall : walls) {
                helper.assertTrue(level.getBlockState(wall).is(Blocks.STONE) && level.getFluidState(wall).isEmpty(),
                        "Fixture water channel opened at " + wall);
            }
        });
    }

    static void prepareStartControlObservation(GameTestHelper helper, BlockPos button) {
        // This region extends outside the template and does not match the member/cargo region.
        // Clean it before retiring the control or flowing water, never after either operation.
        clearPreexistingDrops(helper, new AABB(button).inflate(2));
    }

    static void clearPreexistingDrops(GameTestHelper helper, AABB region) {
        for (var item : helper.getLevel().getEntitiesOfClass(ItemEntity.class, region)) {
            LogUtils.getLogger().info("ARCE_ADAPTER_FIXTURE_PREEXISTING_DROP uuid={} item={} position={} age={}",
                    item.getUUID(), item.getItem(), item.position(), item.getAge());
            item.discard();
        }
        assertNoDrops(helper, region, "Could not clear pre-operation drop region");
    }

    static void assertNoDrops(GameTestHelper helper, AABB region, String message) {
        var drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, region);
        helper.assertTrue(drops.isEmpty(), message + ": " + drops.stream()
                .map(item -> item.getItem() + " type=" + ForgeRegistries.ITEMS.getKey(item.getItem().getItem())
                        + " count=" + item.getItem().getCount() + " at " + item.position() + " age=" + item.getAge())
                .toList());
    }

    private AdapterDropFixture() { }
}
