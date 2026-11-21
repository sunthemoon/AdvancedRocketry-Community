package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.LoggerFactory;

/** Bounded test fixture: sealed source, side probe and transmission cell, including roof and floor. */
final class SealedLightColumn {
    final BlockState expectedState;
    final boolean transmits;
    final BlockPos beneath, tested, source, probe;
    private final Map<BlockPos, BlockState> original = new LinkedHashMap<>();

    SealedLightColumn(GameTestHelper helper, BlockState state, int index, boolean transmits) {
        this.expectedState = state;
        this.transmits = transmits;
        beneath = helper.absolutePos(base(index));
        tested = beneath.above();
        source = tested.above();
        probe = tested.west();
    }

    static BlockPos base(int index) {
        if (index < 0 || index > 6) throw new IllegalArgumentException("Expected column index zero to six");
        // All seven complete enclosures fit the empty template's cleared x[-2,3], z[-3,3], y[-3,20].
        // Stack three per deck, sharing only identical stone walls/floors, never air or measured cells.
        return new BlockPos(2, 2 + 4 * (index / 3), 2 * (index % 3) - 2);
    }

    static Map<BlockPos, BlockState> cells(BlockPos beneath, BlockState testedState) {
        var cells = new LinkedHashMap<BlockPos, BlockState>();
        for (BlockPos pos : BlockPos.betweenClosed(beneath.offset(-2, -1, -1), beneath.offset(1, 3, 1))) {
            cells.put(pos.immutable(), Blocks.STONE.defaultBlockState());
        }
        cells.put(beneath, Blocks.AIR.defaultBlockState());
        cells.put(beneath.above(), testedState);
        cells.put(beneath.above(2), Blocks.GLOWSTONE.defaultBlockState());
        cells.put(beneath.above().west(), Blocks.AIR.defaultBlockState());
        cells.put(beneath.above(2).west(), Blocks.AIR.defaultBlockState());
        return cells;
    }

    void place(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Map<BlockPos, BlockState> cells = cells(beneath, expectedState);
        for (BlockPos pos : cells.keySet()) {
            helper.assertTrue(level.hasChunkAt(pos) && level.getBlockEntity(pos) == null,
                    "Fixture cell is unloaded or holds a BlockEntity: " + pos);
        }
        cells.forEach((pos, state) -> {
            original.put(pos, level.getBlockState(pos));
            level.setBlock(pos, state, Block.UPDATE_ALL);
        });
    }

    String name() {
        return ForgeRegistries.BLOCKS.getKey(expectedState.getBlock()) + " " + expectedState.getValues();
    }

    private boolean published(ServerLevel level) {
        return level.getBrightness(LightLayer.BLOCK, source) == 15
                && level.getBrightness(LightLayer.BLOCK, probe) == 13
                && (!transmits || level.getBrightness(LightLayer.BLOCK, beneath) == 13);
    }

    private String describe(ServerLevel level) {
        return name() + "{source=" + level.getBrightness(LightLayer.BLOCK, source)
                + " probe=" + level.getBrightness(LightLayer.BLOCK, probe)
                + " tested=" + level.getBrightness(LightLayer.BLOCK, tested)
                + " beneath=" + level.getBrightness(LightLayer.BLOCK, beneath) + "}";
    }

    static void await(GameTestHelper helper, GameTestTickDriver driver, List<SealedLightColumn> columns) {
        List<BlockPos> changed = columns.stream().flatMap(column -> column.original.keySet().stream()).toList();
        await(helper, driver, columns, LightPropagationBarrier.afterChanges(helper.getLevel(), changed));
    }

    private static void await(GameTestHelper helper, GameTestTickDriver driver, List<SealedLightColumn> columns,
                              CompletableFuture<Void> propagation) {
        driver.after(1, () -> {
            boolean pending = false;
            ServerLevel level = helper.getLevel();
            try {
                level.getChunkSource().getLightEngine().tryScheduleUpdate();
                boolean settled = propagation.isDone();
                if (settled) propagation.join(); // Never block the server thread, but do expose worker failures.
                boolean published = settled && columns.stream().allMatch(column -> column.published(level));
                if (!published && helper.getTick() < 99) {
                    await(helper, driver, columns, propagation);
                    pending = true;
                    return;
                }
                String observed = columns.stream().map(column -> column.describe(level))
                        .reduce("", (left, right) -> left + " " + right);
                helper.assertTrue(published, "Block light did not settle within the deadline; fence=" + settled + observed);
                for (SealedLightColumn column : columns) {
                    for (var cell : cells(column.beneath, column.expectedState).entrySet()) {
                        helper.assertTrue(level.hasChunkAt(cell.getKey())
                                        && level.getBlockState(cell.getKey()).equals(cell.getValue()),
                                "Light enclosure was changed at " + cell.getKey() + observed);
                    }
                    helper.assertTrue(level.getBrightness(LightLayer.SKY, column.beneath) == 0,
                            "Sky light entered sealed column:" + observed);
                    int beneath = level.getBrightness(LightLayer.BLOCK, column.beneath);
                    helper.assertTrue(beneath == (column.transmits ? 13 : 0),
                            "Unexpected block light beneath " + column.name() + ":" + observed);
                }
                LoggerFactory.getLogger(SealedLightColumn.class).info("Sealed light columns tick={} fence=complete:{}",
                        helper.getTick(), observed);
                helper.succeed();
            } finally {
                if (!pending) clear(level, columns);
            }
        });
    }

    static void clear(ServerLevel level, List<SealedLightColumn> columns) {
        // A later column snapshots shared stone walls placed by the previous one. Restore in reverse order.
        var reverse = new ArrayList<>(columns);
        java.util.Collections.reverse(reverse);
        reverse.forEach(column -> {
            column.original.forEach((pos, state) -> {
                if (level.hasChunkAt(pos)) level.setBlock(pos, state, Block.UPDATE_ALL);
            });
            column.original.clear();
        });
    }
}
