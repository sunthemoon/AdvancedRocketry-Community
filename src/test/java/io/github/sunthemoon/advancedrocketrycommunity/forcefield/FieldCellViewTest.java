package io.github.sunthemoon.advancedrocketrycommunity.forcefield;

import static org.junit.jupiter.api.Assertions.*;
import java.util.HashSet;
import java.util.UUID;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class FieldCellViewTest {
    @BeforeAll static void bootstrap() {
        io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap.initialize();
    }
    private static final BlockPos SOURCE = new BlockPos(8, 100, 8);
    private static FieldCellView view(BlockPos callbackPosition, BlockState callback, AtomicInteger reads) {
        return new FieldCellView(LevelHeightAccessor.create(0, 256), pos -> {
            reads.incrementAndGet();
            return new FieldCellView.Cell(pos.equals(callbackPosition) ? callback : Blocks.AIR.defaultBlockState(), null);
        }, null);
    }
    @Test void throwingSignalCallbacksGiveNoDecisionAndRetainTheReadCharge() {
        for (RuntimeException failure : new RuntimeException[]{new ClassCastException("not a Level"),
                new IllegalStateException("x".repeat(8192))}) {
            Block callback = new Block(BlockBehaviour.Properties.of()) {
                @Override public int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction side) { throw failure; }
            };
            var reads = new AtomicInteger();
            var cells = view(SOURCE.below(), callback.defaultBlockState(), reads);
            assertEquals(Optional.empty(), cells.powered(SOURCE));
            assertSame(failure, cells.powerFailure());
            assertEquals(25, reads.get());
            assertEquals(25, cells.inspectedAndMutatedCells());
        }
    }
    @Test void throwingDirectSignalAndConductorCallbacksAlsoGiveNoDecision() {
        var failure = new IllegalArgumentException("third-party callback");
        Block direct = new Block(BlockBehaviour.Properties.of()) {
            @Override public int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction side) { throw failure; }
        };
        var cells = new FieldCellView(LevelHeightAccessor.create(0, 256), pos -> new FieldCellView.Cell(
                pos.equals(SOURCE.below()) ? Blocks.STONE.defaultBlockState()
                        : pos.equals(SOURCE.below(2)) ? direct.defaultBlockState() : Blocks.AIR.defaultBlockState(), null), null);
        assertEquals(Optional.empty(), cells.powered(SOURCE));
        assertSame(failure, cells.powerFailure());
        Block conductor = new Block(BlockBehaviour.Properties.of().dynamicShape().isRedstoneConductor((state, level, pos) -> { throw failure; }));
        cells = view(SOURCE.below(), conductor.defaultBlockState(), new AtomicInteger());
        assertEquals(Optional.empty(), cells.powered(SOURCE));
        assertSame(failure, cells.powerFailure());
    }
    @Test void swallowedRefusedReadsCannotProduceEitherPowerDecision() {
        for (int result : new int[]{0, 15}) {
            var refuse = new AtomicBoolean(true);
            Block callback = new Block(BlockBehaviour.Properties.of().isRedstoneConductor((state, level, pos) -> false)) {
                @Override public int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction side) {
                    if (refuse.get()) {
                        try { level.getBlockState(SOURCE.east(3)); } catch (RuntimeException ignored) { }
                    }
                    return result;
                }
            };
            var reads = new AtomicInteger();
            var cells = view(SOURCE.below(), callback.defaultBlockState(), reads);
            assertEquals(Optional.empty(), cells.powered(SOURCE));
            assertNotNull(cells.powerFailure());
            assertEquals(25, reads.get());
            assertEquals(25, cells.inspectedAndMutatedCells());
            refuse.set(false);
            assertEquals(Optional.of(result > 0), cells.powered(SOURCE));
            assertNull(cells.powerFailure());
            assertEquals(25, reads.get());
        }
    }
    @Test void fatalCallbackErrorsAreNotSwallowed() {
        var fatal = new OutOfMemoryError("test sentinel");
        Block callback = new Block(BlockBehaviour.Properties.of()) {
            @Override public int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction side) { throw fatal; }
        };
        var cells = view(SOURCE.below(), callback.defaultBlockState(), new AtomicInteger());
        assertSame(fatal, assertThrows(OutOfMemoryError.class, () -> cells.powered(SOURCE)));
    }
    @Test void stencilContainsEveryStockSecondHopExactlyOnce() {
        BlockPos source = new BlockPos(14, 100, 8);
        var positions = FieldCellView.powerPositions(source);
        var expected = new HashSet<BlockPos>(); expected.add(source);
        for (Direction first : Direction.values()) {
            expected.add(source.relative(first));
            for (Direction second : Direction.values()) { expected.add(source.relative(first).relative(second)); }
        }
        assertEquals(25, positions.size());
        assertEquals(expected, new HashSet<>(positions));
        assertTrue(positions.contains(new BlockPos(16, 100, 8)));
    }
    @Test void actualReadsRefundUnusedCapacityWithoutRefundingTheDeviceCooldown() {
        var budget = new FieldWorkBudget();
        var first = new FieldIdentity.Key(new UUID(40, 1), 1);
        assertTrue(budget.acquire(first, 100, FieldCellView.MAX_WORK));
        budget.refund(FieldCellView.MAX_WORK - 25);
        assertEquals(25, budget.cells());
        assertFalse(budget.acquire(first, 100, 1));
        var second = new FieldIdentity.Key(new UUID(40, 2), 1);
        assertTrue(budget.acquire(second, 100, FieldCellView.MAX_WORK));
        budget.refund(FieldCellView.MAX_WORK - 25);
        assertEquals(50, budget.cells());
        assertFalse(budget.hasCapacity(FieldCellView.MAX_WORK, 100));
        assertThrows(IllegalArgumentException.class, () -> budget.refund(51));
        assertThrows(IllegalArgumentException.class, () -> budget.refund(-1));
    }
}
