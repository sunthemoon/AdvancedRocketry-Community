package io.github.sunthemoon.advancedrocketrycommunity.machine.precision;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PrecisionAssemblerPortStorageTest {
    @BeforeAll
    static void bootstrap() {
        MinecraftBootstrap.initialize();
    }

    @Test
    void itemStorageCopiesLoadedDataAndRejectsTaggedAutomation() {
        AtomicInteger changes = new AtomicInteger();
        PrecisionAssemblerItemStorage storage = new PrecisionAssemblerItemStorage(changes::incrementAndGet);
        ItemStack source = new ItemStack(Items.IRON_INGOT, 3);
        storage.loadStored(source);
        source.shrink(2);
        assertEquals(3, storage.storedCopy().getCount());
        assertEquals(0, changes.get());

        ItemStack tagged = new ItemStack(Items.GOLD_INGOT);
        tagged.getOrCreateTag().putString("custom", "blocked");
        assertFalse(storage.isItemValid(0, tagged));
        assertTrue(ItemStack.matches(tagged, storage.insertItem(0, tagged, false)));
        assertEquals(3, storage.storedCopy().getCount());
        storage.replaceStored(new ItemStack(Items.GOLD_INGOT, 2));
        assertEquals(2, storage.storedCopy().getCount());
        assertEquals(1, changes.get());
        assertThrows(IllegalArgumentException.class, () -> storage.replaceStored(tagged));
    }

    @Test
    void energyStorageBoundsReceiveRateAndInternalConsumption() {
        AtomicInteger changes = new AtomicInteger();
        PrecisionAssemblerEnergyStorage storage = new PrecisionAssemblerEnergyStorage(changes::incrementAndGet);
        assertEquals(PrecisionAssemblerEnergyStorage.RECEIVE_LIMIT,
                storage.receiveEnergy(5_000, true));
        assertEquals(0, storage.getEnergyStored());
        assertEquals(0, changes.get());
        assertEquals(PrecisionAssemblerEnergyStorage.RECEIVE_LIMIT,
                storage.receiveEnergy(5_000, false));
        assertEquals(1, changes.get());
        assertFalse(storage.consumeInternal(1_001));
        assertTrue(storage.consumeInternal(750));
        assertEquals(250, storage.getEnergyStored());
        assertEquals(2, changes.get());
        assertThrows(IllegalArgumentException.class, () -> storage.loadStored(-1));
        assertThrows(IllegalArgumentException.class, () -> storage.loadStored(
                PrecisionAssemblerPortPersistence.ENERGY_CAPACITY + 1));
    }
}
