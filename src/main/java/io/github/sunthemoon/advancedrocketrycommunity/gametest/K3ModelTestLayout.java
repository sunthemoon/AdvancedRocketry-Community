package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.machine.classic.runtime.ClassicModelProjection;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternPosition;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternTransform;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;

/** W248: four isolated facings sharing the smallest square of ticketed chunks. */
public enum K3ModelTestLayout {
    ANALYSER(0, 0), OBSERVATORY(1, 0), SOLAR(2, 1), BEACON(3, 0);

    private final int row;
    private final int radius;

    K3ModelTestLayout(int row, int radius) { this.row = row; this.radius = radius; }

    public int radius() { return radius; }

    public BlockPos center() {
        return new BlockPos(-2_360_000, 100, Math.floorDiv(-2_360_000 - row * 1000, 16) * 16);
    }

    public BlockPos origin(int index) {
        if (index < 0 || index >= 4) { throw new IllegalArgumentException("K3 facing index must be 0 to 3"); }
        return switch (this) {
            case ANALYSER, BEACON -> center().offset(4 + index % 2 * 8, 0, 4 + index / 2 * 8);
            // These fixtures run sequentially. Separate their full validation reaches vertically to avoid
            // extra chunks without allowing a rotation to read another facing's cells (or casting shadows).
            case OBSERVATORY -> center().offset(8, index * 8, 8);
            case SOLAR -> center().offset(8, index * 4, 8);
        };
    }

    /** Includes optional/air cells, all four rotations and both mirrors, not just the installed facing. */
    public Set<BlockPos> reach(int index, Collection<PatternPosition> cells, PatternPosition anchor) {
        var origin = origin(index);
        var world = new PatternPosition(origin.getX(), origin.getY(), origin.getZ());
        var reach = new HashSet<BlockPos>();
        for (Direction facing : Direction.Plane.HORIZONTAL) { for (boolean mirror : new boolean[] {false, true}) {
            var transform = new PatternTransform(ClassicModelProjection.transform(facing).rotation(), mirror);
            for (var local : cells) {
                var position = transform.localToWorld(local, anchor, world);
                reach.add(new BlockPos(position.x(), position.y(), position.z()));
            }
        } }
        return Set.copyOf(reach);
    }

    /** Assert before placing any fixture so a future pattern expansion cannot read another facing's cells. */
    public void assertIsolated(GameTestHelper helper, K3ModelTestRegion region,
                               Collection<PatternPosition> cells, PatternPosition anchor) {
        var claimed = new HashSet<BlockPos>();
        for (int index = 0; index < 4; index++) {
            var reach = reach(index, cells, anchor);
            helper.assertTrue(reach.stream().allMatch(p -> region.ticketed(p) && !claimed.contains(p)),
                    "K3 fixture leaves its shared region or overlaps another facing: " + this);
            claimed.addAll(reach);
        }
    }
}
