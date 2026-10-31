package io.github.sunthemoon.advancedrocketrycommunity.fluid;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class CanisterInventoryExchangeTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    private static List<ItemStack> inventory(int count, boolean full) {
        var slots = new ArrayList<ItemStack>(Collections.nCopies(36, ItemStack.EMPTY));
        if (full) { for (int i = 0; i < 36; i++) { slots.set(i, new ItemStack(Items.STONE, 64)); } }
        slots.set(0, new ItemStack(Items.SNOWBALL, count)); return slots;
    }
    @Test void stackedCanistersReturnIntoAReservedSlotOrMerge() {
        var slots = inventory(3, false); slots.set(7, new ItemStack(Items.GLASS_BOTTLE, 2));
        var plan = CanisterInventoryExchange.prepare(slots, 0, new ItemStack(Items.GLASS_BOTTLE));
        assertNotNull(plan); assertEquals(3, slots.get(0).getCount()); plan.publish(slots);
        assertEquals(2, slots.get(0).getCount()); assertEquals(3, slots.get(7).getCount());
        assertFalse(plan.matches(slots)); assertThrows(IllegalStateException.class, () -> plan.publish(slots));
    }
    @Test void fullReturnInventoryRefusesStackedButSingleReusesTheHand() {
        var slots = inventory(2, true); assertNull(CanisterInventoryExchange.prepare(slots, 0, new ItemStack(Items.GLASS_BOTTLE)));
        assertEquals(2, slots.get(0).getCount());
        slots = inventory(1, true); var plan = CanisterInventoryExchange.prepare(slots, 0, new ItemStack(Items.GLASS_BOTTLE));
        assertNotNull(plan); plan.publish(slots); assertTrue(slots.get(0).is(Items.GLASS_BOTTLE));
    }
    @Test void changedSourceIdentityCountTagAndReturnSlotInvalidatePreparation() {
        for (int change = 0; change < 4; change++) {
            var slots = inventory(2, false); var plan = CanisterInventoryExchange.prepare(slots, 0, new ItemStack(Items.GLASS_BOTTLE));
            assertNotNull(plan);
            switch (change) {
                case 0 -> slots.set(0, slots.get(0).copy()); case 1 -> slots.get(0).shrink(1);
                case 2 -> slots.get(0).getOrCreateTag().putString("marker", "changed");
                case 3 -> slots.set(1, new ItemStack(Items.STONE)); default -> throw new AssertionError();
            }
            assertFalse(plan.matches(slots)); assertThrows(IllegalStateException.class, () -> plan.publish(slots));
        }
    }
    @Test void offhandCanisterReturnsOnlyIntoMainStorageAndPreservesMetadata() {
        var slots = inventory(1, true); while (slots.size() < 41) { slots.add(ItemStack.EMPTY); }
        slots.set(40, new ItemStack(Items.SNOWBALL, 2));
        assertNull(CanisterInventoryExchange.prepare(slots, 40, new ItemStack(Items.GLASS_BOTTLE)));
        slots.set(4, ItemStack.EMPTY); ItemStack empty = new ItemStack(Items.GLASS_BOTTLE); empty.getOrCreateTag().putString("marker", "keep");
        var plan = CanisterInventoryExchange.prepare(slots, 40, empty); assertNotNull(plan); plan.publish(slots);
        assertEquals(1, slots.get(40).getCount()); assertEquals("keep", slots.get(4).getTag().getString("marker"));
    }
}
