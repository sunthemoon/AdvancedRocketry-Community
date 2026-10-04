package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.resource;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.List;
import java.util.UUID;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.fluids.FluidStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ClassicBankTransfersTest {
    private static final UUID OWNER = new UUID(41, 42);
    private static final ClassicBankKey ITEM_IN = key(ClassicBankKind.ITEM_INPUT);
    private static final ClassicBankKey ITEM_OUT = key(ClassicBankKind.ITEM_OUTPUT);
    private static final ClassicBankKey FLUID_IN = key(ClassicBankKind.FLUID_INPUT);
    private static final ClassicBankKey FLUID_OUT = key(ClassicBankKind.FLUID_OUTPUT);
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void itemInsertionCapsRemainderConservesCountsAndOwnsTags() {
        ClassicResources before = resources(60, 0, 0, 0);
        ItemStack offer = item(12, "same");
        ClassicBankTransfers.ItemInsert result = ClassicBankTransfers.insertItem(before, ITEM_IN, 0, offer, false);
        assertEquals(4, result.accepted()); assertEquals(8, result.remainder().getCount());
        assertEquals(60 + 12, itemCount(result.resources(), ITEM_IN) + result.remainder().getCount());
        assertEquals(64, itemCount(result.resources(), ITEM_IN)); assertEquals(60, itemCount(before, ITEM_IN));
        assertEquals(before.revision() + 1, result.resources().revision()); assertEquals(12, offer.getCount());
        offer.getTag().putString("batch", "caller");
        result.remainder().getTag().putString("batch", "getter");
        assertEquals("same", result.resources().bank(ITEM_IN).orElseThrow().item(0).getTag().getString("batch"));
        assertEquals("same", result.remainder().getTag().getString("batch"));
    }

    @Test void itemSimulationAndTagMismatchDoNotMutateOrAdvanceRevision() {
        ClassicResources before = resources(12, 0, 0, 0);
        ClassicBankTransfers.ItemInsert simulation = ClassicBankTransfers.insertItem(before, ITEM_IN, 0, item(4, "same"), true);
        assertSame(before, simulation.resources()); assertEquals(4, simulation.accepted()); assertTrue(simulation.remainder().isEmpty());
        ClassicBankTransfers.ItemInsert differentTag = ClassicBankTransfers.insertItem(before, ITEM_IN, 0, item(4, "other"), false);
        assertSame(before, differentTag.resources()); assertEquals(0, differentTag.accepted()); assertEquals(4, differentTag.remainder().getCount());
        ClassicBankTransfers.ItemInsert differentItem = ClassicBankTransfers.insertItem(before, ITEM_IN, 0, new ItemStack(Items.DIRT, 4), false);
        assertSame(before, differentItem.resources()); assertEquals(0, differentItem.accepted());
        assertEquals(12, itemCount(before, ITEM_IN));
    }

    @Test void outputExtractionConservesCountsAndSimulationReturnsDetachedPlan() {
        ClassicResources before = resources(0, 13, 0, 0);
        ClassicBankTransfers.ItemExtract simulation = ClassicBankTransfers.extractItem(before, ITEM_OUT, 0, 5, true);
        assertSame(before, simulation.resources()); assertEquals(5, simulation.extracted().getCount());
        ClassicBankTransfers.ItemExtract actual = ClassicBankTransfers.extractItem(before, ITEM_OUT, 0, Integer.MAX_VALUE, false);
        assertEquals(13, actual.extracted().getCount()); assertEquals(0, itemCount(actual.resources(), ITEM_OUT));
        assertEquals(13, actual.extracted().getCount() + itemCount(actual.resources(), ITEM_OUT));
        assertEquals(before.revision() + 1, actual.resources().revision());
        actual.extracted().getTag().putString("batch", "getter");
        assertEquals("same", actual.extracted().getTag().getString("batch"));
        assertSame(actual.resources(), ClassicBankTransfers.extractItem(actual.resources(), ITEM_OUT, 0, 1, false).resources());
    }

    @Test void fluidFillCapsAcceptedAmountConservesBalanceAndRequiresMetadataEquality() {
        ClassicResources before = resources(0, 0, 14_000, 0);
        FluidStack offer = fluid(3_000, "same");
        ClassicBankTransfers.FluidFill actual = ClassicBankTransfers.fillFluid(before, FLUID_IN, offer, false);
        assertEquals(2_000, actual.accepted()); assertEquals(16_000, fluidAmount(actual.resources(), FLUID_IN));
        assertEquals(14_000 + 3_000, fluidAmount(actual.resources(), FLUID_IN) + offer.getAmount() - actual.accepted());
        assertEquals(before.revision() + 1, actual.resources().revision()); assertEquals(3_000, offer.getAmount());
        offer.getTag().putString("batch", "caller");
        assertEquals("same", actual.resources().bank(FLUID_IN).orElseThrow().fluid().getTag().getString("batch"));
        assertSame(before, ClassicBankTransfers.fillFluid(before, FLUID_IN, fluid(3_000, "other"), false).resources());
        assertSame(before, ClassicBankTransfers.fillFluid(before, FLUID_IN, fluid(3_000, "same"), true).resources());
        assertEquals(14_000, fluidAmount(before, FLUID_IN));
    }

    @Test void fluidDrainConservesTagsAndBalancesAndSimulationDoesNotMutate() {
        ClassicResources before = resources(0, 0, 0, 12_000);
        ClassicBankTransfers.FluidDrain simulation = ClassicBankTransfers.drainFluid(before, FLUID_OUT, fluid(8_000, "same"), true);
        assertSame(before, simulation.resources()); assertEquals(8_000, simulation.extracted().getAmount());
        assertSame(before, ClassicBankTransfers.drainFluid(before, FLUID_OUT, fluid(8_000, "other"), false).resources());
        ClassicBankTransfers.FluidDrain actual = ClassicBankTransfers.drainFluid(before, FLUID_OUT, fluid(16_000, "same"), false);
        assertEquals(12_000, actual.extracted().getAmount()); assertEquals(0, fluidAmount(actual.resources(), FLUID_OUT));
        assertEquals(12_000, actual.extracted().getAmount() + fluidAmount(actual.resources(), FLUID_OUT));
        actual.extracted().getTag().putString("batch", "getter");
        assertEquals("same", actual.extracted().getTag().getString("batch"));
        assertEquals(before.revision() + 1, actual.resources().revision());
    }

    @Test void automationCannotReverseRolesAddressMissingBanksOrNormalizeInvalidOffers() {
        ClassicResources before = resources(0, 1, 0, 1);
        assertThrows(IllegalArgumentException.class, () -> ClassicBankTransfers.insertItem(before, ITEM_OUT, 0, item(1, "same"), false));
        assertThrows(IllegalArgumentException.class, () -> ClassicBankTransfers.extractItem(before, ITEM_IN, 0, 1, false));
        assertThrows(IllegalArgumentException.class, () -> ClassicBankTransfers.fillFluid(before, FLUID_OUT, fluid(1, "same"), false));
        assertThrows(IllegalArgumentException.class, () -> ClassicBankTransfers.drainFluid(before, FLUID_IN, fluid(1, "same"), false));
        assertThrows(IllegalArgumentException.class, () -> ClassicBankTransfers.insertItem(before,
                new ClassicBankKey(ClassicBankKind.ITEM_INPUT, 9, 9, 9), 0, item(1, "same"), false));
        assertThrows(IllegalArgumentException.class, () -> ClassicBankTransfers.extractItem(before, ITEM_OUT, 0, -1, false));
        assertThrows(IllegalArgumentException.class, () -> ClassicBankTransfers.insertItem(before, ITEM_IN, 0, item(65, "same"), false));
        assertThrows(IllegalArgumentException.class, () -> ClassicBankTransfers.fillFluid(before, FLUID_IN, fluid(16_001, "same"), false));
        assertEquals(1, itemCount(before, ITEM_OUT)); assertEquals(1, fluidAmount(before, FLUID_OUT));
    }

    @Test void internalTwoBankReplacementPreservesNativeMetadataAndAdvancesOnlyOnce() {
        ClassicResources before = resources(8, 4, 0, 0);
        ClassicResourceBank input = before.bank(ITEM_IN).orElseThrow();
        ClassicResourceBank output = before.bank(ITEM_OUT).orElseThrow();
        ClassicResources after = before.replace(List.of(input.withItem(0, item(3, "same")), output.withItem(0, item(9, "same"))), false);
        assertEquals(itemCount(before, ITEM_IN) + itemCount(before, ITEM_OUT), itemCount(after, ITEM_IN) + itemCount(after, ITEM_OUT));
        assertEquals(before.revision() + 1, after.revision());
        assertEquals("same", after.bank(ITEM_OUT).orElseThrow().item(0).getTag().getString("batch"));
        assertEquals(8, itemCount(before, ITEM_IN)); assertEquals(4, itemCount(before, ITEM_OUT));
    }

    @Test void exhaustedRevisionAndFullBanksRefuseOrNoOpWithoutChangingResources() {
        ClassicResources full = resources(64, 3, 16_000, 1000);
        assertSame(full, ClassicBankTransfers.insertItem(full, ITEM_IN, 0, item(1, "same"), false).resources());
        assertSame(full, ClassicBankTransfers.fillFluid(full, FLUID_IN, fluid(1, "same"), false).resources());
        ClassicResources exhausted = ClassicResources.restore(OWNER, Long.MAX_VALUE, full.banks());
        assertThrows(IllegalStateException.class, () -> ClassicBankTransfers.extractItem(exhausted, ITEM_OUT, 0, 1, false));
        assertThrows(IllegalStateException.class, () -> ClassicBankTransfers.drainFluid(exhausted, FLUID_OUT, fluid(1, "same"), true));
        assertEquals(3, itemCount(exhausted, ITEM_OUT)); assertEquals(1000, fluidAmount(exhausted, FLUID_OUT));
    }

    private static ClassicBankKey key(ClassicBankKind kind) { return new ClassicBankKey(kind, 4, 64, -4); }
    private static ItemStack item(int amount, String batch) { return ClassicResourceBankTest.taggedItem(Items.STONE, amount, batch); }
    private static FluidStack fluid(int amount, String batch) { return ClassicResourceBankTest.taggedFluid(amount, batch); }
    private static int itemCount(ClassicResources value, ClassicBankKey key) { return value.bank(key).orElseThrow().item(0).getCount(); }
    private static int fluidAmount(ClassicResources value, ClassicBankKey key) { return value.bank(key).orElseThrow().fluid().getAmount(); }
    private static ClassicResources resources(int itemInput, int itemOutput, int fluidInput, int fluidOutput) {
        return ClassicResources.empty(OWNER).replace(List.of(
                ClassicResourceBank.items(ITEM_IN, ClassicResourceBankTest.slots(item(itemInput, "same"))),
                ClassicResourceBank.items(ITEM_OUT, ClassicResourceBankTest.slots(item(itemOutput, "same"))),
                ClassicResourceBank.fluid(FLUID_IN, fluid(fluidInput, "same")),
                ClassicResourceBank.fluid(FLUID_OUT, fluid(fluidOutput, "same"))), false);
    }
}
