package io.github.sunthemoon.advancedrocketrycommunity.forcefield;

import static org.junit.jupiter.api.Assertions.*;
import java.util.HashSet;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

class FieldCellViewTest {
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
