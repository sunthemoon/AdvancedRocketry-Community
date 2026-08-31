package io.github.sunthemoon.advancedrocketrycommunity.atmosphere.scan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class VolumeIdentityTest {
    @Test
    void identityIsIndependentOfTraversalOrder() {
        Set<VolumePosition> forward = new LinkedHashSet<>();
        forward.add(new VolumePosition(0, 0, 0));
        forward.add(new VolumePosition(1, 0, 0));
        Set<VolumePosition> reverse = new LinkedHashSet<>();
        reverse.add(new VolumePosition(1, 0, 0));
        reverse.add(new VolumePosition(0, 0, 0));

        assertEquals(VolumeIdentity.fromCells(forward), VolumeIdentity.fromCells(reverse));
        assertNotEquals(
                VolumeIdentity.fromCells(forward),
                VolumeIdentity.fromCells(Set.of(new VolumePosition(0, 0, 0)))
        );
    }

    @Test
    void identityRejectsEmptyOrMalformedValues() {
        assertThrows(IllegalArgumentException.class, () -> VolumeIdentity.fromCells(Set.of()));
        assertThrows(IllegalArgumentException.class, () -> new VolumeId("not-a-hash"));
    }
}
