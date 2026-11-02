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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * W249-R2: the mono motors' baked OBJ meshes do not fill their cube, so the blocks must not occlude, as upstream's
 * {@code isOpaqueCube} returned false. Block light passes through a motor into the cell beneath it. Collision, the
 * sturdy top face and atmosphere sealing stay a full cube, and sky light is still attenuated by one level, so the
 * server rules that read those properties see no change.
 */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MonoMotorOcclusionGameTests {
    /** Glowstone (15) lights the tested block at 14, and a transmitting block passes 13 to the cell beneath it. */
    private static final int BENEATH_TRANSMITTED = 13;
    private static final int DEADLINE_TICK = 99;

    private MonoMotorOcclusionGameTests() { }

    @GameTest(template = "empty", batch = "motor_occlusion", timeoutTicks = 100)
    public static void monoMotorsDoNotOccludeAndPassLightToTheCellBeneath(GameTestHelper helper) {
        assertNonOccludingColumns(helper, List.of(ModBlocks.ROCKET_MOTOR.get().defaultBlockState(),
                ModBlocks.ADVANCED_ROCKET_MOTOR.get().defaultBlockState()));
    }

    /** Shared bounded fixture; each supplied state has its own enclosed light column and an opaque control. */
    static void assertNonOccludingColumns(GameTestHelper helper, List<BlockState> states) {
        if (states.isEmpty() || states.size() > 6) {
            throw new IllegalArgumentException("Expected one to six motor states");
        }
        var driver = new GameTestTickDriver(helper);
        ServerLevel level = helper.getLevel();
        List<Column> columns = new ArrayList<>();
        for (int i = 0; i < states.size(); i++) {
            columns.add(new Column(helper, states.get(i), i * 2 - 2, true));
        }
        columns.add(new Column(helper, Blocks.IRON_BLOCK.defaultBlockState(), states.size() * 2 - 2, false));
        Runnable clear = () -> columns.forEach(column -> column.clear(helper));
        try {
            for (Column column : columns) {
                column.place(helper);
                column.assertProperties(helper, level);
            }
            await(helper, driver, level, columns, clear);
        } catch (RuntimeException | Error failure) {
            clear.run();
            throw failure;
        }
    }

    private static void await(GameTestHelper helper, GameTestTickDriver driver, ServerLevel level, List<Column> columns,
                              Runnable clear) {
        driver.after(1, () -> {
            boolean pending = false;
            try {
                boolean published = columns.stream().allMatch(column -> column.published(level));
                if (!published && helper.getTick() < DEADLINE_TICK) {
                    await(helper, driver, level, columns, clear);
                    pending = true;
                    return;
                }
                StringBuilder observed = new StringBuilder();
                columns.forEach(column -> observed.append(' ').append(column.describe(level)));
                helper.assertTrue(published, "Block light did not settle within the deadline:" + observed);
                for (Column column : columns) {
                    int beneath = level.getBrightness(LightLayer.BLOCK, column.beneath);
                    helper.assertTrue(beneath == (column.transmits ? BENEATH_TRANSMITTED : 0),
                            "Unexpected block light beneath " + column.name() + ":" + observed);
                }
                helper.succeed();
            } finally {
                if (!pending) {
                    clear.run();
                }
            }
        });
    }

    /**
     * One enclosed column: the tested block above an air cell whose other five faces are stone, so that cell can
     * receive block light only through the tested block, and glowstone above. A probe cell beside the tested block
     * shows that the same light wave has arrived there.
     */
    private static final class Column {
        private final BlockState expectedState;
        private final boolean transmits;
        private final BlockPos tested;
        private final BlockPos beneath;
        private final BlockPos source;
        private final BlockPos probe;
        private final List<BlockPos> placed = new ArrayList<>();

        private Column(GameTestHelper helper, BlockState expectedState, int z, boolean transmits) {
            this.expectedState = expectedState;
            this.transmits = transmits;
            this.beneath = helper.absolutePos(new BlockPos(2, 2, z));
            this.tested = beneath.above();
            this.source = tested.above();
            this.probe = helper.absolutePos(new BlockPos(1, 3, z));
        }

        private String name() {
            return ForgeRegistries.BLOCKS.getKey(expectedState.getBlock()) + " " + expectedState.getValues();
        }

        private void place(GameTestHelper helper) {
            ServerLevel level = helper.getLevel();
            List<BlockPos> stone = List.of(beneath.below(), beneath.north(), beneath.south(), beneath.east(),
                    beneath.west());
            List<BlockPos> air = List.of(beneath, probe, probe.above());
            for (BlockPos pos : concat(stone, air, List.of(tested, source))) {
                helper.assertTrue(level.hasChunkAt(pos) && level.getBlockEntity(pos) == null,
                        "Fixture cell is unloaded or holds a BlockEntity: " + pos);
            }
            stone.forEach(pos -> set(level, pos, Blocks.STONE.defaultBlockState()));
            air.forEach(pos -> set(level, pos, Blocks.AIR.defaultBlockState()));
            set(level, tested, expectedState);
            set(level, source, Blocks.GLOWSTONE.defaultBlockState());
        }

        private void set(ServerLevel level, BlockPos pos, BlockState state) {
            level.setBlock(pos, state, Block.UPDATE_ALL);
            placed.add(pos);
        }

        private void assertProperties(GameTestHelper helper, ServerLevel level) {
            BlockState state = level.getBlockState(tested);
            helper.assertTrue(state.equals(expectedState), "Fixture lost " + name());
            if (!transmits) {
                helper.assertTrue(state.canOcclude() && state.isSolidRender(level, tested)
                        && state.getLightBlock(level, tested) == 15, "Opaque control no longer blocks light");
                return;
            }
            helper.assertTrue(!state.canOcclude() && !state.isSolidRender(level, tested),
                    name() + " still occludes as an opaque cube");
            helper.assertTrue(state.getLightBlock(level, tested) == 1 && !state.propagatesSkylightDown(level, tested),
                    name() + " light attenuation changed: " + state.getLightBlock(level, tested));
            helper.assertTrue(state.isCollisionShapeFullBlock(level, tested) && state.isSolid() && state.blocksMotion()
                            && state.isFaceSturdy(level, tested, Direction.UP)
                            && state.isRedstoneConductor(level, tested) && state.isSuffocating(level, tested),
                    name() + " collision or support properties changed");
            VolumePosition cell = new VolumePosition(tested.getX(), tested.getY(), tested.getZ());
            helper.assertTrue(new ServerLevelVolumeWorldView(level, true).observe(cell) == CellObservation.SEALED,
                    name() + " no longer seals an atmosphere");
        }

        private boolean published(ServerLevel level) {
            return level.getBrightness(LightLayer.BLOCK, source) == 15
                    && level.getBrightness(LightLayer.BLOCK, probe) == BENEATH_TRANSMITTED
                    && (!transmits || level.getBrightness(LightLayer.BLOCK, beneath) == BENEATH_TRANSMITTED);
        }

        private String describe(ServerLevel level) {
            return name() + "{source=" + level.getBrightness(LightLayer.BLOCK, source)
                    + " probe=" + level.getBrightness(LightLayer.BLOCK, probe)
                    + " tested=" + level.getBrightness(LightLayer.BLOCK, tested)
                    + " beneath=" + level.getBrightness(LightLayer.BLOCK, beneath) + "}";
        }

        private void clear(GameTestHelper helper) {
            ServerLevel level = helper.getLevel();
            for (BlockPos pos : placed) {
                if (level.hasChunkAt(pos)) {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                }
            }
            placed.clear();
        }

        @SafeVarargs
        private static List<BlockPos> concat(List<BlockPos>... parts) {
            List<BlockPos> all = new ArrayList<>();
            for (List<BlockPos> part : parts) {
                all.addAll(part);
            }
            return all;
        }
    }
}
