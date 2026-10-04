package io.github.sunthemoon.advancedrocketrycommunity.fluid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.Collections;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class CanisterStackSwapTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test
    void sixteenUnitsSplitOneAndRejoinWithoutMutatingInputs() {
        ItemStack held = new ItemStack(Items.GLASS_BOTTLE, 16);
        ItemStack filled = new ItemStack(Items.PAPER);
        ItemStack destination = new ItemStack(Items.PAPER, 15);
        var plan = CanisterStackSwap.plan(held, filled, List.of(destination)).orElseThrow();
        assertEquals(15, plan.held().getCount());
        assertEquals(0, plan.destinationSlot());
        assertEquals(16, plan.destination().getCount());
        assertEquals(16, held.getCount());
        assertEquals(15, destination.getCount());
        assertEquals(1, filled.getCount());
    }

    @Test
    void fullInventoryRefusesStackedSwapButOneHeldUnitCanBeReplaced() {
        var full = Collections.nCopies(36, new ItemStack(Items.PAPER, 16));
        assertTrue(CanisterStackSwap.plan(new ItemStack(Items.GLASS_BOTTLE, 16),
                new ItemStack(Items.PAPER), full).isEmpty());
        var single = CanisterStackSwap.plan(new ItemStack(Items.GLASS_BOTTLE),
                new ItemStack(Items.PAPER), full).orElseThrow();
        assertEquals(-1, single.destinationSlot());
        assertTrue(single.held().is(Items.PAPER));
        assertEquals(1, single.held().getCount());
    }

    @Test
    void emptySlotStoresTheResultAndDifferentMetadataDoesNotMerge() {
        ItemStack existing = new ItemStack(Items.PAPER, 15);
        existing.getOrCreateTag().putString("marker", "different");
        var plan = CanisterStackSwap.plan(new ItemStack(Items.GLASS_BOTTLE, 16),
                new ItemStack(Items.PAPER), List.of(existing, ItemStack.EMPTY)).orElseThrow();
        assertEquals(1, plan.destinationSlot());
        assertEquals(1, plan.destination().getCount());
        assertTrue(plan.destination().is(Items.PAPER));
        assertEquals(15, existing.getCount());
    }

    @Test
    void invalidCountsAndOversizedInventoryBoundsRefuseWithoutCopy() {
        assertTrue(CanisterStackSwap.plan(new ItemStack(Items.GLASS_BOTTLE, 17),
                new ItemStack(Items.PAPER), List.of(ItemStack.EMPTY)).isEmpty());
        assertTrue(CanisterStackSwap.plan(new ItemStack(Items.GLASS_BOTTLE),
                new ItemStack(Items.PAPER, 2), List.of(ItemStack.EMPTY)).isEmpty());
        assertTrue(CanisterStackSwap.plan(new ItemStack(Items.GLASS_BOTTLE),
                new ItemStack(Items.PAPER), Collections.nCopies(65, ItemStack.EMPTY)).isEmpty());
    }
}
