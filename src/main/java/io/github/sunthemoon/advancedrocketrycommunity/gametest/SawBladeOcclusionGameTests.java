package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.scan.CellObservation;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.scan.VolumePosition;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.server.ServerLevelVolumeWorldView;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** R282: rendering/light transmission changes without changing collision, support, sealing or state identity. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SawBladeOcclusionGameTests {
    private SawBladeOcclusionGameTests() { }

    @GameTest(template = "empty", batch = "saw_occlusion", timeoutTicks = 100)
    public static void sawDoesNotOccludeAndPassesLightWithoutChangingServerGeometry(GameTestHelper helper) {
        var driver = new GameTestTickDriver(helper);
        var columns = List.of(new Column(helper, ModBlocks.SAW_BLADE.get(), 0, true),
                new Column(helper, Blocks.IRON_BLOCK, 3, false));
        try {
            columns.forEach(column -> column.place(helper));
            await(helper, driver, columns);
        } catch (RuntimeException | Error failure) {
            columns.forEach(column -> column.clear(helper.getLevel()));
            throw failure;
        }
    }

    private static void await(GameTestHelper helper, GameTestTickDriver driver, List<Column> columns) {
        driver.after(1, () -> {
            boolean pending = false;
            try {
                boolean published = columns.stream().allMatch(column -> column.published(helper.getLevel()));
                if (!published && helper.getTick() < 99) {
                    await(helper, driver, columns);
                    pending = true;
                    return;
                }
                helper.assertTrue(published, "Saw/control block light did not settle within 99 ticks");
                for (Column column : columns) {
                    helper.assertTrue(helper.getLevel().getBrightness(LightLayer.BLOCK, column.beneath)
                            == (column.saw ? 13 : 0), "Saw transmission or opaque control failed");
                }
                helper.succeed();
            } finally {
                if (!pending) { columns.forEach(column -> column.clear(helper.getLevel())); }
            }
        });
    }

    private static final class Column {
        private final Block block;
        private final boolean saw;
        private final BlockPos beneath, tested, source, probe;
        private final List<BlockPos> placed = new ArrayList<>();

        private Column(GameTestHelper helper, Block block, int z, boolean saw) {
            this.block = block;
            this.saw = saw;
            beneath = helper.absolutePos(new BlockPos(2, 2, z));
            tested = beneath.above();
            source = tested.above();
            probe = tested.west();
        }

        private void place(GameTestHelper helper) {
            ServerLevel level = helper.getLevel();
            List<BlockPos> stone = List.of(beneath.below(), beneath.north(), beneath.south(), beneath.east(), beneath.west());
            List<BlockPos> air = List.of(beneath, probe, probe.above());
            var cells = new ArrayList<>(stone);
            cells.addAll(air); cells.add(tested); cells.add(source);
            for (BlockPos pos : cells) {
                helper.assertTrue(level.hasChunkAt(pos) && level.getBlockEntity(pos) == null,
                        "Fixture cell is unloaded or contains a block entity: " + pos);
            }
            // Only validated fixture cells may be cleared if setup fails.
            placed.addAll(cells);
            stone.forEach(pos -> level.setBlock(pos, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL));
            air.forEach(pos -> level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL));
            var state = block.defaultBlockState();
            level.setBlock(tested, state, Block.UPDATE_ALL);
            level.setBlock(source, Blocks.GLOWSTONE.defaultBlockState(), Block.UPDATE_ALL);
            helper.assertTrue(state.canOcclude() != saw && state.isSolidRender(level, tested) != saw
                    && state.getLightBlock(level, tested) == (saw ? 1 : 15), "Occlusion/light attenuation differs");
            if (!saw) { return; }
            helper.assertTrue(state.getProperties().isEmpty() && !state.hasBlockEntity() && !state.isAir(),
                    "Saw acquired state, a block entity or air semantics");
            helper.assertTrue(state.isCollisionShapeFullBlock(level, tested) && state.isSolid() && state.blocksMotion()
                    && state.isFaceSturdy(level, tested, Direction.UP) && state.isRedstoneConductor(level, tested)
                    && state.isSuffocating(level, tested) && !state.propagatesSkylightDown(level, tested),
                    "Saw collision, support, motion, conductor or skylight properties changed");
            helper.assertTrue(new ServerLevelVolumeWorldView(level, true).observe(
                    new VolumePosition(tested.getX(), tested.getY(), tested.getZ())) == CellObservation.SEALED,
                    "Saw no longer seals an atmosphere");
        }

        private boolean published(ServerLevel level) {
            return level.getBrightness(LightLayer.BLOCK, source) == 15
                    && level.getBrightness(LightLayer.BLOCK, probe) == 13
                    && (!saw || level.getBrightness(LightLayer.BLOCK, beneath) == 13);
        }

        private void clear(ServerLevel level) {
            placed.forEach(pos -> level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL));
        }
    }
}
