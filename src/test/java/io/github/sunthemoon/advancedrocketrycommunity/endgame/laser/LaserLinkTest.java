package io.github.sunthemoon.advancedrocketrycommunity.endgame.laser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

/** Review C11R-M1: a link records the marker generation of its first contact and keeps it across saves. */
class LaserLinkTest {
    @Test
    void aNewLinkHasNotTouchedItsMarkerAndRecordsTheFirstContact() {
        UUID marker = UUID.randomUUID();
        LaserLink first = LaserLink.create(marker, false);
        LaserLink second = LaserLink.create(marker, false);
        assertNotEquals(first.linkId(), second.linkId());
        assertEquals(-1L, first.generation());
        assertEquals(0L, first.opsPaid());
        assertTrue(first.touched(3L));
        assertFalse(first.touched(3L));
        assertEquals(3L, first.generation());
        assertThrows(IllegalArgumentException.class, () -> first.touched(-1L));
    }

    @Test
    void theGenerationSurvivesASaveAndIsRequired() {
        LaserLink link = LaserLink.create(UUID.randomUUID(), true);
        link.touched(7L);
        link.paid();
        CompoundTag tag = link.write();
        LaserLink read = LaserLink.read(tag);
        assertEquals(link.marker(), read.marker());
        assertEquals(link.linkId(), read.linkId());
        assertEquals(1L, read.opsPaid());
        assertTrue(read.operatorLink());
        assertEquals(7L, read.generation());

        CompoundTag missing = tag.copy();
        missing.remove("generation");
        assertThrows(IllegalArgumentException.class, () -> LaserLink.read(missing));
        CompoundTag below = tag.copy();
        below.putLong("generation", -2L);
        assertThrows(IllegalArgumentException.class, () -> LaserLink.read(below));
    }
}
