package io.github.sunthemoon.advancedrocketrycommunity.atmosphere.scan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

class VolumeIndexTest {
    @Test
    void lookupInvalidationAndOverlapFailClosed() {
        VolumeIndex index = new VolumeIndex(3, 8);
        AtmosphereVolume first = volume(new VolumePosition(0, 0, 0), new VolumePosition(1, 0, 0));
        AtmosphereVolume overlapping = volume(new VolumePosition(1, 0, 0), new VolumePosition(2, 0, 0));

        assertTrue(index.put(first).isEmpty());
        assertEquals(first.id(), index.find(new VolumePosition(0, 0, 0)).orElseThrow().id());
        assertEquals(Set.of(first.id()), Set.copyOf(index.put(overlapping)));
        assertFalse(index.find(new VolumePosition(0, 0, 0)).isPresent());
        assertEquals(overlapping.id(), index.find(new VolumePosition(2, 0, 0)).orElseThrow().id());

        assertEquals(
                Set.of(overlapping.id()),
                index.invalidateAround(Set.of(new VolumePosition(1, 0, 0)))
        );
        assertEquals(0, index.volumeCount());
        assertEquals(0, index.cellCount());
    }

    @Test
    void leastRecentlyUsedVolumeIsEvictedAtHardLimits() {
        VolumeIndex index = new VolumeIndex(2, 4);
        AtmosphereVolume first = volume(new VolumePosition(0, 0, 0));
        AtmosphereVolume second = volume(new VolumePosition(10, 0, 0));
        AtmosphereVolume third = volume(new VolumePosition(20, 0, 0));

        index.put(first);
        index.put(second);
        index.find(first.id());
        assertEquals(Set.of(second.id()), Set.copyOf(index.put(third)));

        assertTrue(index.find(first.id()).isPresent());
        assertFalse(index.find(second.id()).isPresent());
        assertTrue(index.find(third.id()).isPresent());
        assertEquals(2, index.volumeCount());
    }

    @Test
    void indexRejectsConfigurationOrVolumeBeyondItsBudget() {
        assertThrows(IllegalArgumentException.class, () -> new VolumeIndex(0, 1));
        VolumeIndex index = new VolumeIndex(1, 1);
        assertThrows(IllegalArgumentException.class, () -> index.put(volume(
                new VolumePosition(0, 0, 0),
                new VolumePosition(1, 0, 0)
        )));
    }

    @Test
    void predicateInvalidationRemovesOnlyIntersectingVolumes() {
        VolumeIndex index = new VolumeIndex(3, 8);
        AtmosphereVolume first = volume(new VolumePosition(1, 0, 1));
        AtmosphereVolume second = volume(new VolumePosition(17, 0, 1));
        index.put(first);
        index.put(second);

        assertEquals(
                Set.of(first.id()),
                index.invalidateWhere(position -> position.x() >> 4 == 0)
        );
        assertFalse(index.find(first.id()).isPresent());
        assertTrue(index.find(second.id()).isPresent());
    }

    private static AtmosphereVolume volume(VolumePosition... positions) {
        Set<VolumePosition> cells = Set.of(positions);
        VolumeBounds bounds = null;
        for (VolumePosition position : cells) {
            bounds = bounds == null ? VolumeBounds.single(position) : bounds.include(position);
        }
        return new AtmosphereVolume(VolumeIdentity.fromCells(cells), cells, bounds);
    }
}
