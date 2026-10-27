package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.SafeCelestialTravel;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.surface.SurfaceContent;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlock;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * The Moon's fixed pads (around (8, 80, 8), 64 blocks apart) are shared by every test that lands there or prepares the
 * developer platform. Each such test first gives the area a known shape: moon turf from y 5 up to a chosen top, open air
 * from there to y 100, so no test depends on what another left behind. ADR-075 tests record every block they change and
 * restore it, and the cases of one batch take turns on the pads.
 */
public final class MoonPadArea {
    /** Clear above a rocket arriving at y 80. */
    private static final int CLEAR_TO = 100;
    /** The selector's eight fixed candidates around the Moon base, in its order. */
    private static final int[][] OFFSETS = {
            {0, 0}, {64, 0}, {-64, 0}, {0, 64}, {0, -64}, {64, 64}, {-64, 64}, {64, -64}
    };
    public static final int CANDIDATES = OFFSETS.length;
    private static Object turn;

    private MoonPadArea() {
    }

    /** Fills a square of the given reach around the first pad with turf up to {@code top} and clears everything above. */
    static void shape(ServerLevel moon, int reach, int top) {
        BlockPos pad = SafeCelestialTravel.FIXED_FEET_POSITION;
        BlockState turf = SurfaceContent.MOON_TURF.get().defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                for (int y = 5; y <= CLEAR_TO; y++) {
                    moon.setBlock(position.set(pad.getX() + dx, y, pad.getZ() + dz), y <= top ? turf : air,
                            Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    /** The centre of fixed candidate {@code index}, at the old floor's height. */
    public static BlockPos pad(int index) {
        return SafeCelestialTravel.FIXED_FEET_POSITION.offset(OFFSETS[index][0], 0, OFFSETS[index][1]);
    }

    /** The origin x and z that the selector gives this rocket at candidate {@code index}; y is 0. */
    public static RocketPosition origin(int index, RocketStructureSnapshot snapshot) {
        var bounds = snapshot.bounds();
        BlockPos pad = pad(index);
        return new RocketPosition(pad.getX() - Math.floorDiv(bounds.minimum().x() + bounds.maximum().x(), 2), 0,
                pad.getZ() - Math.floorDiv(bounds.minimum().z() + bounds.maximum().z(), 2));
    }

    /** The absolute columns (y 0) under the rocket's blocks at candidate {@code index}, or under its lowest layer only. */
    public static List<BlockPos> columns(int index, RocketStructureSnapshot snapshot, boolean lowestLayer) {
        RocketPosition origin = origin(index, snapshot);
        var columns = new LinkedHashSet<BlockPos>();
        for (RocketBlock block : snapshot.blocks()) {
            if (!lowestLayer || block.position().y() == snapshot.bounds().minimum().y()) {
                columns.add(new BlockPos(origin.x() + block.position().x(), 0, origin.z() + block.position().z()));
            }
        }
        return List.copyOf(columns);
    }

    /** The largest {@code MOTION_BLOCKING_NO_LEAVES} value over the footprint columns of a placed snapshot. */
    public static int highestColumn(ServerLevel moon, RocketStructureSnapshot placed) {
        int highest = moon.getMinBuildHeight();
        for (RocketBlock block : placed.blocks()) {
            RocketPosition at = placed.sourceOrigin().add(block.position());
            highest = Math.max(highest, moon.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.x(), at.z()));
        }
        return highest;
    }

    /** The y of the placed snapshot's lowest layer. */
    public static int lowest(RocketStructureSnapshot placed) {
        return placed.sourceOrigin().y() + placed.bounds().minimum().y();
    }

    /** The blocks directly below the lowest layer of a placed snapshot. */
    public static List<BlockPos> below(RocketStructureSnapshot placed) {
        var below = new ArrayList<BlockPos>();
        for (RocketBlock block : placed.blocks()) {
            if (block.position().y() == placed.bounds().minimum().y()) {
                RocketPosition at = placed.sourceOrigin().add(block.position());
                below.add(new BlockPos(at.x(), at.y() - 1, at.z()));
            }
        }
        return below;
    }

    /** ADR-075's support rule, read from the world: one sturdy top below the lowest layer and no fluid below it. */
    public static boolean supported(ServerLevel level, RocketStructureSnapshot placed) {
        boolean sturdy = false;
        for (BlockPos at : below(placed)) {
            BlockState state = level.getBlockState(at);
            if (!state.getFluidState().isEmpty()) {
                return false;
            }
            sturdy |= state.isFaceSturdy(level, at, Direction.UP);
        }
        return sturdy;
    }

    /** Records each block before it first changes, and restores them all in reverse order. */
    public static final class Changes {
        private final ServerLevel level;
        private final Map<BlockPos, BlockState> original = new LinkedHashMap<>();

        public Changes(ServerLevel level) {
            this.level = level;
        }

        public void record(BlockPos position) {
            original.putIfAbsent(position.immutable(), level.getBlockState(position));
        }

        public void set(BlockPos position, BlockState state) {
            record(position);
            level.setBlock(position, state, Block.UPDATE_CLIENTS);
        }

        /** Candidate {@code index}: turf from y 5 to {@code top} and air above, to y 100, in a square of {@code reach}. */
        public void shape(int index, int reach, int top) {
            BlockPos pad = pad(index);
            BlockState turf = SurfaceContent.MOON_TURF.get().defaultBlockState();
            for (int dx = -reach; dx <= reach; dx++) {
                for (int dz = -reach; dz <= reach; dz++) {
                    for (int y = 5; y <= CLEAR_TO; y++) {
                        set(new BlockPos(pad.getX() + dx, y, pad.getZ() + dz), y <= top ? turf : Blocks.AIR.defaultBlockState());
                    }
                }
            }
        }

        /** Empties the given columns from the build minimum to y 100. */
        public void empty(Collection<BlockPos> columns) {
            for (BlockPos column : columns) {
                for (int y = level.getMinBuildHeight(); y <= CLEAR_TO; y++) {
                    set(new BlockPos(column.getX(), y, column.getZ()), Blocks.AIR.defaultBlockState());
                }
            }
        }

        /** Places a sturdy, fluid-free block directly below each lowest-layer block of a placed snapshot. */
        public void support(RocketStructureSnapshot placed) {
            for (BlockPos at : below(placed)) {
                set(at, Blocks.SMOOTH_STONE.defaultBlockState());
            }
        }

        public void restore() {
            var entries = new ArrayList<>(original.entrySet());
            for (int i = entries.size() - 1; i >= 0; i--) {
                level.setBlock(entries.get(i).getKey(), entries.get(i).getValue(), Block.UPDATE_CLIENTS);
            }
            original.clear();
        }
    }

    /**
     * The cases of one batch start together and would race for the same fixed pads, so they take turns. A case holds the
     * turn from its first successful {@link #turn} until it passes or fails; {@code cleanup} then runs once and the turn
     * passes on.
     */
    public static boolean turn(GameTestHelper helper, Runnable cleanup) {
        GameTestInfo info = info(helper);
        if (turn == info) {
            return true;
        }
        if (turn != null) {
            return false;
        }
        turn = info;
        info.addListener(new GameTestListener() {
            private boolean released;

            @Override public void testStructureLoaded(GameTestInfo test) { }
            @Override public void testPassed(GameTestInfo test) { release(); }
            @Override public void testFailed(GameTestInfo test) { release(); }

            private void release() {
                if (released) {
                    return;
                }
                released = true;
                try {
                    cleanup.run();
                } finally {
                    if (turn == info) {
                        turn = null;
                    }
                }
            }
        });
        return true;
    }

    private static GameTestInfo info(GameTestHelper helper) {
        try {
            var field = GameTestHelper.class.getDeclaredField("testInfo");
            field.setAccessible(true);
            return (GameTestInfo) field.get(helper);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot read the GameTest's info", failure);
        }
    }
}
