package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.scan.CellObservation;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.scan.VolumePosition;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.scan.VolumeScanOutcome;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.scan.VolumeScanTask;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.server.ServerLevelVolumeWorldView;
import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Native geometry and lighting are exercised separately; no light-engine mutation. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AtmosphereExposureGameTests {
    private AtmosphereExposureGameTests() { }

    @GameTest(template = "empty", batch = "atmosphere_exposure", timeoutTicks = 40)
    public static void transparentRoofSealsWhileNativeSkyLightRemainsFull(GameTestHelper helper) {
        Room room = new Room(helper);
        room.set(room.cell.above(), Blocks.GLASS.defaultBlockState());
        helper.startSequence().thenWaitUntil(() -> {
            helper.assertTrue(room.level.canSeeSky(room.cell), "Native full sky light not established");
        }).thenExecute(() -> {
            room.assertRoofHeight();
            room.assertScan(true, VolumeScanOutcome.SEALED, 7);
        }).thenSucceed();
    }

    @GameTest(template = "empty", batch = "atmosphere_exposure", timeoutTicks = 40)
    public static void newlyClosedOpaqueRoofSealsBeforeLightingCatchesUp(GameTestHelper helper) {
        Room room = new Room(helper);
        helper.startSequence().thenWaitUntil(() -> {
            helper.assertTrue(room.level.canSeeSky(room.cell), "Native full sky light not established");
        }).thenExecute(() -> {
            room.assertScan(true, VolumeScanOutcome.OPEN, 1);
            room.set(room.cell.above(), Blocks.IRON_BLOCK.defaultBlockState());
            room.assertRoofHeight();
            // No world/light tick is inserted between the roof write and scan.
            room.assertScan(true, VolumeScanOutcome.SEALED, 7);
        }).thenSucceed();
    }

    @GameTest(template = "empty", batch = "atmosphere_exposure", timeoutTicks = 40)
    public static void roofAndSideOpeningsRemainLeaksImmediatelyAfterEdits(GameTestHelper helper) {
        Room room = new Room(helper);
        room.set(room.cell.above(), Blocks.IRON_BLOCK.defaultBlockState());
        room.assertScan(false, VolumeScanOutcome.SEALED, 7);
        room.set(room.cell.above(), Blocks.AIR.defaultBlockState());
        room.assertScan(true, VolumeScanOutcome.OPEN, 1);
        room.set(room.cell.above(), Blocks.IRON_BLOCK.defaultBlockState());
        room.set(room.cell.east(), Blocks.AIR.defaultBlockState());
        room.set(room.cell.east().above(), Blocks.AIR.defaultBlockState());
        room.assertScan(true, VolumeScanOutcome.OPEN, 8);
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "atmosphere_exposure", timeoutTicks = 40)
    public static void skyPolicyDisabledStillScansGeometricBoundaries(GameTestHelper helper) {
        Room room = new Room(helper);
        room.set(room.cell.above(), Blocks.GLASS.defaultBlockState());
        room.assertScan(false, VolumeScanOutcome.SEALED, 7);
        room.set(room.cell.above(), Blocks.AIR.defaultBlockState());
        var view = new ServerLevelVolumeWorldView(room.level, false);
        helper.assertTrue(view.observe(room.seed()) == CellObservation.TRAVERSABLE,
                "Disabled sky shortcut rejected an air cell");
        var task = new VolumeScanTask(room.seed(), 1);
        helper.assertTrue(task.step(view, 8) <= 8 && task.outcome() == VolumeScanOutcome.TOO_LARGE,
                "Disabled sky shortcut bypassed the bounded geometry scan");
        helper.succeed();
    }

    /** Owns at most 27 loaded, BE-free cells; the listener also restores timed-out tests. */
    private static final class Room implements GameTestListener {
        private final GameTestHelper helper;
        private final ServerLevel level;
        private final BlockPos cell;
        private final Map<BlockPos, BlockState> before = new LinkedHashMap<>();
        private boolean closed;

        private Room(GameTestHelper helper) {
            this.helper = helper;
            level = helper.getLevel();
            BlockPos allocation = helper.absolutePos(new BlockPos(1, 1, 1));
            cell = new BlockPos(allocation.getX(), 180, allocation.getZ());
            try {
                Field field = GameTestHelper.class.getDeclaredField("testInfo");
                field.setAccessible(true);
                ((GameTestInfo) field.get(helper)).addListener(this);
                for (int x = -1; x <= 1; x++) {
                    for (int y = -1; y <= 1; y++) {
                        for (int z = -1; z <= 1; z++) {
                            set(cell.offset(x, y, z), Blocks.IRON_BLOCK.defaultBlockState());
                        }
                    }
                }
                set(cell, Blocks.AIR.defaultBlockState());
                set(cell.above(), Blocks.AIR.defaultBlockState());
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("Fixture terminal listener unavailable", failure);
            } catch (RuntimeException | Error failure) {
                close(failure);
                throw failure;
            }
        }

        private void set(BlockPos position, BlockState state) {
            helper.assertTrue(before.size() < 27 || before.containsKey(position), "Fixture cell limit exceeded");
            helper.assertTrue(!level.isOutsideBuildHeight(position) && level.hasChunkAt(position)
                    && level.getBlockEntity(position) == null, "Fixture requires loaded BE-free cells");
            before.putIfAbsent(position.immutable(), level.getBlockState(position));
            helper.assertTrue(level.setBlock(position, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE)
                    || level.getBlockState(position) == state, "Fixture block write failed");
        }

        private VolumePosition seed() { return new VolumePosition(cell.getX(), cell.getY(), cell.getZ()); }

        private void assertRoofHeight() {
            helper.assertTrue(cell.getY() < level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    cell.getX(), cell.getZ()), "Current native heightmap has no roof");
        }

        private void assertScan(boolean skyIsOpen, VolumeScanOutcome expected, int budget) {
            var task = new VolumeScanTask(seed());
            int inspections = task.step(new ServerLevelVolumeWorldView(level, skyIsOpen), budget);
            helper.assertTrue(inspections <= budget && task.outcome() == expected,
                    "Bounded native scan expected " + expected + " but was " + task.outcome()
                            + ", inspections=" + inspections);
        }

        private void close(Throwable primary) {
            if (closed) { return; }
            closed = true;
            Throwable first = primary;
            for (var entry : before.entrySet()) {
                try {
                    level.setBlock(entry.getKey(), entry.getValue(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                } catch (RuntimeException | Error failure) {
                    if (first == null) { first = failure; }
                    else if (first != failure) { first.addSuppressed(failure); }
                }
            }
            if (primary == null) {
                if (first instanceof RuntimeException failure) { throw failure; }
                if (first instanceof Error failure) { throw failure; }
            }
        }

        @Override public void testStructureLoaded(GameTestInfo info) { }
        @Override public void testPassed(GameTestInfo info) { close(null); }
        @Override public void testFailed(GameTestInfo info) { close(info.getError()); }
    }
}
