package io.github.sunthemoon.advancedrocketrycommunity.fluid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class GasCanisterHandlerTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    private static GasCanisterCatalog catalog() {
        return new GasCanisterCatalog(Items.GLASS_BOTTLE, List.of(
                new GasCanisterCatalog.Entry(Items.PAPER, Fluids.WATER),
                new GasCanisterCatalog.Entry(Items.FEATHER, Fluids.LAVA)));
    }

    private static GasCanisterHandler handler(ItemStack stack) {
        return new GasCanisterHandler(stack, GasCanisterHandlerTest::catalog);
    }

    @Test
    void simulationDoesNotChangeTheContainerAndExecutionSwapsOnce() {
        ItemStack empty = new ItemStack(Items.GLASS_BOTTLE);
        empty.getOrCreateTag().putString("marker", "retained");
        GasCanisterHandler handler = handler(empty);
        FluidStack request = new FluidStack(Fluids.WATER, 2_000);
        assertEquals(1_000, handler.fill(request, FluidAction.SIMULATE));
        assertTrue(handler.getContainer().is(Items.GLASS_BOTTLE));
        assertEquals(1_000, handler.fill(request, FluidAction.EXECUTE));
        assertTrue(handler.getContainer().is(Items.PAPER));
        assertEquals("retained", handler.getContainer().getTag().getString("marker"));
        assertTrue(empty.is(Items.GLASS_BOTTLE));
        assertEquals(0, handler.fill(request, FluidAction.EXECUTE));
        assertEquals(1_000, handler.drain(2_000, FluidAction.SIMULATE).getAmount());
        assertTrue(handler.getContainer().is(Items.PAPER));
        assertEquals(1_000, handler.drain(2_000, FluidAction.EXECUTE).getAmount());
        assertTrue(handler.getContainer().is(Items.GLASS_BOTTLE));
        assertEquals("retained", handler.getContainer().getTag().getString("marker"));
        assertTrue(handler.drain(2_000, FluidAction.EXECUTE).isEmpty());
        assertEquals(2_000, request.getAmount());
    }

    @Test
    void allStackedContainersRejectBothActionsAndKeepTheirExactTags() {
        for (int count = 2; count <= 16; count++) {
            for (FluidAction action : FluidAction.values()) {
                ItemStack empty = new ItemStack(Items.GLASS_BOTTLE, count);
                empty.getOrCreateTag().putInt("original_count", count);
                var before = empty.save(new CompoundTag());
                var emptyHandler = handler(empty);
                assertEquals(0, emptyHandler.fill(new FluidStack(Fluids.WATER, 1_000), action));
                assertTrue(emptyHandler.getFluidInTank(0).isEmpty());
                assertEquals(before, empty.save(new CompoundTag()));
                ItemStack filled = new ItemStack(Items.PAPER, count);
                var filledHandler = handler(filled);
                assertTrue(filledHandler.drain(1_000, action).isEmpty());
                assertEquals(count, filledHandler.getContainer().getCount());
                assertTrue(filledHandler.getContainer().is(Items.PAPER));
            }
        }
    }

    @Test
    void partialAmountsAndFluidMetadataCannotBeDiscarded() {
        for (FluidAction action : FluidAction.values()) {
            var empty = handler(new ItemStack(Items.GLASS_BOTTLE));
            assertEquals(0, empty.fill(new FluidStack(Fluids.WATER, 999), action));
            var filled = handler(new ItemStack(Items.PAPER));
            assertTrue(filled.drain(999, action).isEmpty());
            assertTrue(filled.drain(new FluidStack(Fluids.LAVA, 1_000), action).isEmpty());
            FluidStack tagged = new FluidStack(Fluids.WATER, 1_000);
            tagged.setTag(new CompoundTag());
            tagged.getTag().putString("payload", "cannot-be-represented-by-canister-id");
            assertEquals(0, empty.fill(tagged, action));
            assertTrue(filled.drain(tagged, action).isEmpty());
        }
    }

    @Test
    void unsupportedItemsAndOversizedTagsAreRefusedBeforeCopy() {
        var unknown = handler(new ItemStack(Items.STONE));
        assertEquals(0, unknown.fill(new FluidStack(Fluids.WATER, 1_000), FluidAction.EXECUTE));
        assertTrue(unknown.drain(1_000, FluidAction.EXECUTE).isEmpty());
        ItemStack empty = new ItemStack(Items.GLASS_BOTTLE);
        empty.getOrCreateTag().putByteArray("huge", new byte[8_193]);
        var unsafe = handler(empty);
        assertEquals(0, unsafe.fill(new FluidStack(Fluids.WATER, 1_000), FluidAction.SIMULATE));
        assertEquals(0, unsafe.fill(new FluidStack(Fluids.WATER, 1_000), FluidAction.EXECUTE));
        assertEquals(8_193, empty.getTag().getByteArray("huge").length);
    }

    @Test
    void queriesReturnDetachedFluidAndInvalidationRevokesRetainedViews() {
        var handler = handler(new ItemStack(Items.PAPER));
        FluidStack view = handler.getFluidInTank(0);
        view.setAmount(1);
        assertEquals(1_000, handler.getFluidInTank(0).getAmount());
        assertThrows(IndexOutOfBoundsException.class, () -> handler.getFluidInTank(1));
        assertThrows(IndexOutOfBoundsException.class, () -> handler.getTankCapacity(-1));
        handler.invalidate();
        assertTrue(handler.getFluidInTank(0).isEmpty());
        assertTrue(handler.drain(1_000, FluidAction.EXECUTE).isEmpty());
    }

    @Test
    void catalogRefusesDuplicatedOrExcessiveBindings() {
        assertThrows(IllegalArgumentException.class,
                () -> new GasCanisterCatalog(Items.GLASS_BOTTLE, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new GasCanisterCatalog(Items.GLASS_BOTTLE,
                List.of(new GasCanisterCatalog.Entry(Items.PAPER, Fluids.WATER),
                        new GasCanisterCatalog.Entry(Items.PAPER, Fluids.LAVA))));
        assertFalse(handler(new ItemStack(Items.GLASS_BOTTLE))
                .isFluidValid(0, FluidStack.EMPTY));
    }
}
