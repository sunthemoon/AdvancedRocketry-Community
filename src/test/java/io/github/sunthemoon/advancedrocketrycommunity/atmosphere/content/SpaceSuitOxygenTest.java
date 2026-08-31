package io.github.sunthemoon.advancedrocketrycommunity.atmosphere.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.AtmosphereLimits;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class SpaceSuitOxygenTest {
    @Test
    void chestOxygenStaysWithinCapacityAndTransfersAtomically() {
        CompoundTag root = new CompoundTag();

        assertEquals(SpaceSuitOxygen.DataStatus.VALID, SpaceSuitOxygen.readTag(root).status());
        assertTrue(SpaceSuitOxygen.setTag(root, 500));
        assertTrue(SpaceSuitOxygen.fillOneCanisterTag(root).accepted());
        assertEquals(1_500, SpaceSuitOxygen.readTag(root).oxygenUnits());
        assertFalse(SpaceSuitOxygen.fillOneCanisterTag(root).accepted());
        assertEquals(1_500, SpaceSuitOxygen.readTag(root).oxygenUnits());
        assertFalse(SpaceSuitOxygen.setTag(root, AtmosphereLimits.SUIT_OXYGEN_CAPACITY + 1));
    }

    @Test
    void futureSchemaIsPreservedAndCannotBeMutated() {
        CompoundTag root = new CompoundTag();
        CompoundTag data = new CompoundTag();
        data.putInt("schema_version", 2);
        data.putString("future_payload", "keep-exactly");
        root.put("arce_space_suit_oxygen", data);
        CompoundTag before = root.copy();

        assertEquals(SpaceSuitOxygen.DataStatus.FUTURE, SpaceSuitOxygen.readTag(root).status());
        assertFalse(SpaceSuitOxygen.fillOneCanisterTag(root).accepted());
        assertFalse(SpaceSuitOxygen.setTag(root, 0));
        assertEquals(before, root);
    }

    @Test
    void wrongItemOrInvalidPayloadFailsClosed() {
        CompoundTag root = new CompoundTag();
        CompoundTag data = new CompoundTag();
        data.putInt("schema_version", 1);
        data.putInt("oxygen_units", AtmosphereLimits.SUIT_OXYGEN_CAPACITY + 1);
        root.put("arce_space_suit_oxygen", data);

        assertEquals(SpaceSuitOxygen.DataStatus.INVALID, SpaceSuitOxygen.readTag(root).status());
        assertFalse(SpaceSuitOxygen.setTag(root, 0));
    }
}
