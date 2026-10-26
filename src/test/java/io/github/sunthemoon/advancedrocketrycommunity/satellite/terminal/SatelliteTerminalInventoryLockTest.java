package io.github.sunthemoon.advancedrocketrycommunity.satellite.terminal;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * ADR-074 item 7: while a launch or decommission waits, the package and chip slots refuse insertion and extraction
 * through the item handler, simulated and actual, which menus (mayPickup/mayPlace), hoppers and droppers all use;
 * only the terminal's own completion extracts the package.
 */
class SatelliteTerminalInventoryLockTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void lockedSlotsRefuseEverySimulatedAndActualMoveAndUnlockedSlotsDoNot() {
        var lock = new AtomicBoolean(true);
        var inventory = new SatelliteTerminalInventory(() -> false, slot -> { },
                slot -> (slot == SatelliteTerminalBlockEntity.SLOT_PACKAGE || slot == SatelliteTerminalBlockEntity.SLOT_CONTROL_CHIP)
                        && lock.get());
        var held = new ItemStack(Items.PAPER);
        inventory.setStackInSlot(SatelliteTerminalBlockEntity.SLOT_PACKAGE, held.copy());
        inventory.setStackInSlot(SatelliteTerminalBlockEntity.SLOT_CHARGE, new ItemStack(Items.REDSTONE, 4));
        for (int slot : new int[] {SatelliteTerminalBlockEntity.SLOT_PACKAGE, SatelliteTerminalBlockEntity.SLOT_CONTROL_CHIP}) {
            for (boolean simulate : new boolean[] {true, false}) {
                assertTrue(inventory.extractItem(slot, 1, simulate).isEmpty(), "extract slot " + slot + " simulate " + simulate);
                var offered = new ItemStack(Items.PAPER);
                assertSame(offered, inventory.insertItem(slot, offered, simulate), "insert slot " + slot + " simulate " + simulate);
            }
            assertFalse(inventory.isItemValid(slot, new ItemStack(Items.PAPER)));
        }
        assertTrue(ItemStack.matches(held, inventory.getStackInSlot(SatelliteTerminalBlockEntity.SLOT_PACKAGE)));
        assertEquals(1, inventory.extractItem(SatelliteTerminalBlockEntity.SLOT_CHARGE, 1, false).getCount(),
                "An unlocked slot still works");
        assertTrue(ItemStack.matches(held, inventory.extractOwned(SatelliteTerminalBlockEntity.SLOT_PACKAGE)),
                "The terminal's own completion extracts past the lock");
        assertTrue(inventory.getStackInSlot(SatelliteTerminalBlockEntity.SLOT_PACKAGE).isEmpty());
        lock.set(false);
        inventory.setStackInSlot(SatelliteTerminalBlockEntity.SLOT_PACKAGE, held.copy());
        assertEquals(1, inventory.extractItem(SatelliteTerminalBlockEntity.SLOT_PACKAGE, 1, true).getCount(),
                "Released slots move again");
    }
}
