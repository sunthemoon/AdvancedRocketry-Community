package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class EscrowLaunchDiscardTest {
    @Test void reportsChangedMissingAndAddedFieldsWithoutMutatingOrPrintingTheirValues() {
        var expected = new CompoundTag();
        expected.putString("motion", "old-private-value"); expected.putInt("missing", 1); expected.putInt("same", 2);
        var observed = expected.copy();
        observed.putString("motion", "new-private-value"); observed.remove("missing"); observed.putInt("added", 3);
        var before = expected.copy(); var after = observed.copy();
        assertEquals("added,missing,motion", EscrowLaunchDiscard.differences(expected, observed));
        assertEquals(before, expected); assertEquals(after, observed);
        assertEquals("", EscrowLaunchDiscard.differences(expected, expected.copy()));
        assertEquals("unavailable", EscrowLaunchDiscard.differences(expected, null));
    }
}
