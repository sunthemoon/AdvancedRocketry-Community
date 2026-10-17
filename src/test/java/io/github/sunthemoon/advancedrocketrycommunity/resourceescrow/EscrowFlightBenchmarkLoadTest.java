package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.energy.EnergyStorage;
import net.minecraftforge.items.ItemStackHandler;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EscrowFlightBenchmarkLoadTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void refillUsesBoundedAutomationWithoutDuplicatingQueuedSupply() {
        var items = new ItemStackHandler(2);
        var energy = new EnergyStorage(40000);
        items.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 2));
        items.setStackInSlot(1, new ItemStack(Items.GLASS_BOTTLE, 4));
        for (int i = 0; i < 2; i++) { EscrowFlightBenchmarkLoad.refill(items, energy, Items.IRON_INGOT); }
        assertEquals(4, items.getStackInSlot(0).getCount());
        assertTrue(items.getStackInSlot(1).isEmpty());
        assertEquals(40000, energy.getEnergyStored());
    }

    @Test void refusedSupplyRemainsAFailure() {
        var items = new ItemStackHandler(2) {
            @Override public boolean isItemValid(int slot, ItemStack stack) { return false; }
        };
        assertThrows(IllegalArgumentException.class,
                () -> EscrowFlightBenchmarkLoad.refill(items, new EnergyStorage(40000), Items.IRON_INGOT));
    }
}
