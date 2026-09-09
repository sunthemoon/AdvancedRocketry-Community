package io.github.sunthemoon.advancedrocketrycommunity.machine.rolling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternPosition;
import java.util.Map;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

class RollingMachineTransformsTest {
    @Test
    void localBackAlwaysPointsBehindTheControllerFacing() {
        PatternPosition localBack = new PatternPosition(0, 0, 1);
        Map<Direction, PatternPosition> expected = Map.of(
                Direction.NORTH, new PatternPosition(0, 0, 1),
                Direction.EAST, new PatternPosition(-1, 0, 0),
                Direction.SOUTH, new PatternPosition(0, 0, -1),
                Direction.WEST, new PatternPosition(1, 0, 0)
        );

        expected.forEach((facing, offset) -> assertEquals(
                offset,
                RollingMachineTransforms.forFacing(facing).applyOffset(localBack)
        ));
    }

    @Test
    void verticalFacingIsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> RollingMachineTransforms.forFacing(Direction.UP)
        );
    }
}
