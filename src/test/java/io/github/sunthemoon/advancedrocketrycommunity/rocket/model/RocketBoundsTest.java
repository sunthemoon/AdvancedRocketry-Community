package io.github.sunthemoon.advancedrocketrycommunity.rocket.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.validation.RocketValidationCode;
import java.util.List;
import org.junit.jupiter.api.Test;

final class RocketBoundsTest {
    @Test
    void computesInclusiveDimensionsAndContainment() {
        RocketBounds bounds = RocketBounds.enclosing(List.of(
                new RocketPosition(-1, 2, 4),
                new RocketPosition(2, 4, 5)
        ));

        assertEquals(4, bounds.sizeX());
        assertEquals(3, bounds.sizeY());
        assertEquals(2, bounds.sizeZ());
        assertEquals(24, bounds.volume());
        assertTrue(bounds.contains(new RocketPosition(0, 3, 4)));
        assertFalse(bounds.contains(new RocketPosition(3, 3, 4)));
    }

    @Test
    void emptyBoundsUseTheSnapshotDiagnostic() {
        RocketSnapshotException failure = assertThrows(
                RocketSnapshotException.class,
                () -> RocketBounds.enclosing(List.of())
        );
        assertEquals(RocketValidationCode.EMPTY_STRUCTURE, failure.code());
    }

    @Test
    void extremeCoordinatesFailAsOversizedWithoutOverflowing() {
        RocketSnapshotException failure = assertThrows(
                RocketSnapshotException.class,
                () -> new RocketBounds(
                        new RocketPosition(Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE),
                        new RocketPosition(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE)
                )
        );
        assertEquals(RocketValidationCode.BOUNDING_VOLUME_EXCEEDED, failure.code());
    }
}
