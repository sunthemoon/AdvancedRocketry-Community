package io.github.sunthemoon.advancedrocketrycommunity.machine.solar;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SolarArraySaveTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    @Test void independentRootRoundTripsEveryEnergyWithoutPersistingFormation() {
        assertNotEquals(SolarSave.ROOT, SolarSave.ARRAY_ROOT);
        for (int energy = 0; energy <= 20_000; energy++) {
            var root = SolarSave.encode(energy, SolarArrayRules.CAPACITY);
            assertEquals(energy, SolarSave.decode(root, SolarArrayRules.CAPACITY));
            assertEquals(2, root.size()); assertEquals(1, root.getInt("schema"));
        }
    }
    @Test void invalidEnergySchemaWidthAndUnknownFieldsAreNotNormalized() {
        for (int energy : new int[]{-1, 20_001, Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> SolarSave.encode(energy, SolarArrayRules.CAPACITY));
            var root = SolarSave.encode(0, SolarArrayRules.CAPACITY); root.putInt("energy", energy);
            var before = root.copy();
            assertThrows(IllegalArgumentException.class, () -> SolarSave.decode(root, SolarArrayRules.CAPACITY)); assertEquals(before, root);
        }
        var root = SolarSave.encode(777, SolarArrayRules.CAPACITY); root.putInt("schema", 2);
        assertTrue(SolarSave.bounded(root)); assertThrows(IllegalArgumentException.class, () -> SolarSave.decode(root, SolarArrayRules.CAPACITY));
        root.putInt("schema", 1); root.putShort("energy", (short) 777);
        assertThrows(IllegalArgumentException.class, () -> SolarSave.decode(root, SolarArrayRules.CAPACITY));
        root.putInt("energy", 777); root.putString("future", "keep");
        assertThrows(IllegalArgumentException.class, () -> SolarSave.decode(root, SolarArrayRules.CAPACITY));
    }
    @Test void arrayUsesTheSameRootPreflightAndDoesNotBroadenSingleCapacity() {
        var large = new CompoundTag(); large.putByteArray("payload", new byte[4_096]); assertFalse(SolarSave.bounded(large));
        assertThrows(IllegalArgumentException.class, () -> SolarSave.decode(SolarSave.encode(20_000, SolarArrayRules.CAPACITY)));
    }
}
