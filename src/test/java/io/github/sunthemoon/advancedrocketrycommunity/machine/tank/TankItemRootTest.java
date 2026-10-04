package io.github.sunthemoon.advancedrocketrycommunity.machine.tank;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class TankItemRootTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    @Test void nativeItemRoundTripKeepsTheExactSingleTankRootIncludingOverflow() {
        ItemStack item = new ItemStack(Items.IRON_BLOCK);
        CompoundTag root = TankSave.encode(new FluidStack(Fluids.WATER, Integer.MAX_VALUE));
        item.getOrCreateTag().put(TankSave.ROOT, root);
        ItemStack restored = ItemStack.of(item.save(new CompoundTag()));
        assertEquals(root, restored.getTag().get(TankSave.ROOT)); assertTrue(PressurizedTankItem.placeable(restored));
        assertFalse(restored.getTag().contains("BlockEntityTag"));
        assertEquals(Integer.MAX_VALUE, TankSave.decode(restored.getTag().get(TankSave.ROOT)).getAmount());
    }
    @Test void unsupportedItemRootsAndSecondResourceCarrierRefusePlacementWithoutRewriting() {
        ItemStack item = new ItemStack(Items.IRON_BLOCK);
        CompoundTag root = TankSave.encode(new FluidStack(Fluids.WATER, 64_001)); root.putInt("schema", 2);
        item.getOrCreateTag().put(TankSave.ROOT, root);
        CompoundTag before = item.save(new CompoundTag());
        assertFalse(PressurizedTankItem.placeable(item)); assertEquals(before, item.save(new CompoundTag()));
        item.getOrCreateTag().put(TankSave.ROOT, TankSave.encode(FluidStack.EMPTY));
        item.getOrCreateTag().put("BlockEntityTag", new CompoundTag());
        assertFalse(PressurizedTankItem.placeable(item));
    }
}
