package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.resource;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ClassicResourceBankTest {
    private static final ClassicBankKey ITEM = new ClassicBankKey(ClassicBankKind.ITEM_INPUT, 1, 2, 3);
    private static final ClassicBankKey FLUID = new ClassicBankKey(ClassicBankKind.FLUID_OUTPUT, 1, 2, 3);
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void itemBanksOwnConstructorAndGetterMetadataInBothDirections() {
        ItemStack stack = taggedItem(Items.STONE, 32, "original");
        ClassicResourceBank bank = ClassicResourceBank.items(ITEM, slots(stack));
        stack.getTag().putString("batch", "caller");
        stack.setCount(1);
        assertEquals("original", bank.item(0).getTag().getString("batch"));
        assertEquals(32, bank.item(0).getCount());
        ItemStack extractedView = bank.item(0);
        extractedView.getTag().putString("batch", "getter"); extractedView.setCount(2);
        bank.items().get(0).getTag().putString("batch", "list-getter");
        assertEquals("original", bank.item(0).getTag().getString("batch"));
        assertThrows(UnsupportedOperationException.class, () -> bank.items().clear());
        ClassicResourceBank replacement = bank.withItem(1, taggedItem(Items.STONE, 3, "second"));
        assertTrue(bank.item(1).isEmpty()); assertEquals(3, replacement.item(1).getCount());
    }

    @Test void fluidBanksOwnConstructorAndGetterMetadataInBothDirections() {
        FluidStack fluid = taggedFluid(12_000, "original");
        ClassicResourceBank bank = ClassicResourceBank.fluid(FLUID, fluid);
        fluid.getTag().putString("batch", "caller"); fluid.setAmount(1);
        FluidStack getter = bank.fluid(); getter.getTag().putString("batch", "getter"); getter.setAmount(2);
        assertEquals("original", bank.fluid().getTag().getString("batch"));
        assertEquals(12_000, bank.fluid().getAmount());
        ClassicResourceBank replacement = bank.withFluid(taggedFluid(1_000, "second"));
        assertEquals(12_000, bank.fluid().getAmount()); assertEquals(1_000, replacement.fluid().getAmount());
    }

    @Test void exactShapeRoleCapacityAndItemSpecificStackLimitAreRequired() {
        assertThrows(IllegalArgumentException.class, () -> ClassicResourceBank.items(ITEM, List.of(ItemStack.EMPTY)));
        assertThrows(IllegalArgumentException.class, () -> ClassicResourceBank.items(FLUID, slots(ItemStack.EMPTY)));
        assertThrows(IllegalArgumentException.class, () -> ClassicResourceBank.fluid(ITEM, FluidStack.EMPTY));
        assertThrows(IllegalArgumentException.class, () -> ClassicResourceBank.items(ITEM, slots(new ItemStack(Items.STONE, 65))));
        assertThrows(IllegalArgumentException.class, () -> ClassicResourceBank.items(ITEM, slots(new ItemStack(Items.ENDER_PEARL, 17))));
        assertThrows(IllegalArgumentException.class, () -> ClassicResourceBank.items(ITEM, slots(new ItemStack(Items.IRON_SWORD, 2))));
        assertThrows(IllegalArgumentException.class, () -> ClassicResourceBank.fluid(FLUID, new FluidStack(Fluids.WATER, 16_001)));
        assertEquals(16_000, ClassicResourceBank.fluid(FLUID, new FluidStack(Fluids.WATER, 16_000)).fluid().getAmount());
        assertEquals(16, ClassicResourceBank.items(ITEM, slots(new ItemStack(Items.ENDER_PEARL, 16))).item(0).getCount());
        assertThrows(IllegalArgumentException.class, () -> ClassicResourceBank.empty(ITEM).item(-1));
        assertThrows(IllegalArgumentException.class, () -> ClassicResourceBank.empty(ITEM).item(4));
        assertThrows(IllegalArgumentException.class, () -> ClassicResourceBank.empty(FLUID).item(0));
        assertThrows(IllegalStateException.class, () -> ClassicResourceBank.empty(ITEM).fluid());
    }

    @Test void deepOrHugeLiveMetadataRefusesBeforeRecursiveCopy() {
        CompoundTag deep = new CompoundTag(); CompoundTag cursor = deep;
        for (int depth = 0; depth < 30; depth++) {
            CompoundTag child = new CompoundTag(); cursor.put("child", child); cursor = child;
        }
        ItemStack item = new ItemStack(Items.STONE); item.setTag(deep);
        FluidStack fluid = new FluidStack(Fluids.WATER, 1_000); fluid.setTag(deep);
        assertThrows(IllegalArgumentException.class, () -> ClassicResourceBank.items(ITEM, slots(item)));
        assertThrows(IllegalArgumentException.class, () -> ClassicResourceBank.fluid(FLUID, fluid));
        CompoundTag huge = new NoCopyTag(); huge.putByteArray("bytes", new byte[ClassicResourcesCodec.MAX_BYTES]);
        item.setTag(huge); fluid.setTag(huge);
        assertThrows(IllegalArgumentException.class, () -> ClassicResourceBank.items(ITEM, slots(item)));
        assertThrows(IllegalArgumentException.class, () -> ClassicResourceBank.fluid(FLUID, fluid));
    }

    @Test void nativeEmptyGettersDoNotNormalizeHiddenNegativeSerializedCounts() {
        ItemStack negativeItem = new ItemStack(Items.STONE, -1);
        FluidStack negativeFluid = new FluidStack(Fluids.WATER, -1);
        assertTrue(negativeItem.isEmpty()); assertTrue(negativeFluid.isEmpty());
        assertThrows(IllegalArgumentException.class, () -> ClassicResourceBank.items(ITEM, slots(negativeItem)));
        assertThrows(IllegalArgumentException.class, () -> ClassicResourceBank.fluid(FLUID, negativeFluid));
        assertTrue(ClassicResourceBank.empty(ITEM).item(0).isEmpty());
        assertTrue(ClassicResourceBank.empty(FLUID).fluid().isEmpty());
    }

    static List<ItemStack> slots(ItemStack first) { return List.of(first, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY); }
    static ItemStack taggedItem(net.minecraft.world.item.Item item, int amount, String batch) {
        ItemStack stack = new ItemStack(item, amount); stack.getOrCreateTag().putString("batch", batch); return stack;
    }
    static FluidStack taggedFluid(int amount, String batch) {
        FluidStack stack = new FluidStack(Fluids.WATER, amount); stack.getOrCreateTag().putString("batch", batch); return stack;
    }
    static final class NoCopyTag extends CompoundTag {
        @Override public CompoundTag copy() { throw new AssertionError("Unbounded data must not be copied"); }
    }
}
