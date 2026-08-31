package io.github.sunthemoon.advancedrocketrycommunity.atmosphere.scan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Set;
import org.junit.jupiter.api.Test;

class VolumePositionTest {
    @Test
    void neighborsAreExactlyTheSixOrthogonalCells() {
        VolumePosition origin = new VolumePosition(4, -2, 9);

        assertEquals(Set.of(
                new VolumePosition(5, -2, 9),
                new VolumePosition(3, -2, 9),
                new VolumePosition(4, -1, 9),
                new VolumePosition(4, -3, 9),
                new VolumePosition(4, -2, 10),
                new VolumePosition(4, -2, 8)
        ), Set.copyOf(origin.neighbors()));
    }

    @Test
    void offsetRejectsIntegerOverflow() {
        assertThrows(
                ArithmeticException.class,
                () -> new VolumePosition(Integer.MAX_VALUE, 0, 0).offset(1, 0, 0)
        );
    }
}
