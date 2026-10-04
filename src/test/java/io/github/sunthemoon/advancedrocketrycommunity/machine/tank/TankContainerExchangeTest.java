package io.github.sunthemoon.advancedrocketrycommunity.machine.tank;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.fluid.GasCanisterCatalog;
import io.github.sunthemoon.advancedrocketrycommunity.fluid.GasCanisterHandler;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.Collections;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.fluids.capability.wrappers.FluidBucketWrapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class TankContainerExchangeTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    private static IFluidHandlerItem gas(ItemStack unit) {
        return new GasCanisterHandler(unit, () -> new GasCanisterCatalog(Items.GLASS_BOTTLE,
                List.of(new GasCanisterCatalog.Entry(Items.PAPER, Fluids.WATER))));
    }

    @Test void sixteenUnitCanisterPourAndFillStowOnlyOneWithMetadata() {
        ItemStack held = new ItemStack(Items.PAPER, 16);
        held.getOrCreateTag().putString("marker", "retained");
        ItemStack empty = new ItemStack(Items.GLASS_BOTTLE, 15); empty.setTag(held.getTag().copy());
        var pour = TankContainerExchange.plan(held, List.of(empty), FluidStack.EMPTY, 64_000,
                TankContainerExchangeTest::gas).orElseThrow();
        assertEquals(1_000, pour.fluid().getAmount());
        assertEquals(15, pour.slots().held().getCount()); assertEquals(16, pour.slots().destination().getCount());
        assertEquals("retained", pour.slots().destination().getTag().getString("marker"));
        assertEquals(16, held.getCount()); assertEquals(15, empty.getCount());
        ItemStack empties = new ItemStack(Items.GLASS_BOTTLE, 16); empties.setTag(held.getTag().copy());
        FluidStack fluid = new FluidStack(Fluids.WATER, 1_000);
        var fill = TankContainerExchange.plan(empties, List.of(ItemStack.EMPTY), fluid, 64_000,
                TankContainerExchangeTest::gas).orElseThrow();
        assertTrue(fill.fluid().isEmpty()); assertEquals(15, fill.slots().held().getCount());
        assertTrue(fill.slots().destination().is(Items.PAPER));
        assertEquals(1_000, fluid.getAmount()); assertEquals(16, empties.getCount());
    }

    @Test void fullStorageAndPartialFluidCapacityRefuseWithoutChangingAnyInputs() {
        ItemStack held = new ItemStack(Items.PAPER, 16);
        var full = Collections.nCopies(35, new ItemStack(Items.STONE, 64));
        FluidStack fluid = new FluidStack(Fluids.WATER, 63_000);
        assertTrue(TankContainerExchange.plan(held, full, fluid, 64_000, TankContainerExchangeTest::gas).isEmpty());
        assertEquals(16, held.getCount()); assertEquals(63_000, fluid.getAmount());
        assertTrue(TankContainerExchange.plan(held, List.of(ItemStack.EMPTY), new FluidStack(Fluids.WATER, 63_001),
                64_000, TankContainerExchangeTest::gas).isEmpty());
        assertTrue(TankContainerExchange.plan(new ItemStack(Items.GLASS_BOTTLE), full,
                new FluidStack(Fluids.WATER, 999), 64_000, TankContainerExchangeTest::gas).isEmpty());
        var single = TankContainerExchange.plan(new ItemStack(Items.PAPER), full, FluidStack.EMPTY, 64_000,
                TankContainerExchangeTest::gas).orElseThrow();
        assertEquals(-1, single.slots().destinationSlot()); assertTrue(single.slots().held().is(Items.GLASS_BOTTLE));
    }

    @Test void realWholeBucketWrapperPreservesBoundedMetadataAndDoesNotChangeInput() {
        ItemStack lava = new ItemStack(Items.LAVA_BUCKET); lava.getOrCreateTag().putString("marker", "bucket");
        var pour = TankContainerExchange.plan(lava, List.of(), FluidStack.EMPTY, 64_000,
                FluidBucketWrapper::new).orElseThrow();
        assertTrue(pour.slots().held().is(Items.BUCKET)); assertEquals(1_000, pour.fluid().getAmount());
        assertEquals("bucket", pour.slots().held().getTag().getString("marker")); assertTrue(lava.is(Items.LAVA_BUCKET));
        var refill = TankContainerExchange.plan(pour.slots().held(), List.of(), pour.fluid(), 64_000,
                FluidBucketWrapper::new).orElseThrow();
        assertTrue(refill.fluid().isEmpty()); assertTrue(refill.slots().held().is(Items.LAVA_BUCKET));
        assertEquals("bucket", refill.slots().held().getTag().getString("marker"));
    }

    @Test void incompatibleTaggedOrOversizedPayloadsNeverBecomeUntypedContainers() {
        assertTrue(TankContainerExchange.plan(new ItemStack(Items.PAPER), List.of(), new FluidStack(Fluids.LAVA, 1),
                64_000, TankContainerExchangeTest::gas).isEmpty());
        FluidStack tagged = new FluidStack(Fluids.WATER, 1_000); tagged.setTag(new CompoundTag());
        tagged.getTag().putString("identity", "keep");
        assertTrue(TankContainerExchange.plan(new ItemStack(Items.GLASS_BOTTLE), List.of(), tagged,
                64_000, TankContainerExchangeTest::gas).isEmpty());
        ItemStack huge = new ItemStack(Items.PAPER); huge.getOrCreateTag().putByteArray("huge", new byte[8_193]);
        assertTrue(TankContainerExchange.plan(huge, List.of(), FluidStack.EMPTY,
                64_000, TankContainerExchangeTest::gas).isEmpty());
    }
}
