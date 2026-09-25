package io.github.sunthemoon.advancedrocketrycommunity.client.compat.jei;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.machine.precision.PrecisionAssemblerRecipe;
import io.github.sunthemoon.advancedrocketrycommunity.machine.rolling.RollingMachineRecipe;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluids;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class MachineRecipeViewTest {
    @BeforeAll
    static void bootstrap() {
        MinecraftBootstrap.initialize();
    }

    @Test
    void rollingViewUsesTheExecutableItemFluidAndEnergyDefinition() {
        RollingMachineRecipe recipe = new RollingMachineRecipe(
                id("rolling_view"), Ingredient.of(Items.IRON_INGOT), 2,
                Fluids.WATER, 100, new ItemStack(Items.IRON_BARS, 8),
                100, 20, 256
        );
        MachineRecipeView view = MachineRecipeView.from(recipe.processDefinition());

        assertEquals(1, view.itemInputs().size());
        assertEquals("item_input", view.itemInputs().get(0).channel());
        assertEquals(Items.IRON_INGOT, view.itemInputs().get(0).alternatives().get(0).getItem());
        assertEquals(2, view.itemInputs().get(0).alternatives().get(0).getCount());
        assertEquals(Fluids.WATER, view.fluidInputs().get(0).fluid());
        assertEquals(100, view.fluidInputs().get(0).amount());
        assertEquals(Items.IRON_BARS, view.itemOutputs().get(0).getItem());
        assertEquals(8, view.itemOutputs().get(0).getCount());
        assertEquals(100, view.durationTicks());
        assertEquals(20, view.energyPerTick());

        view.itemInputs().get(0).alternatives().get(0).shrink(1);
        view.itemOutputs().get(0).shrink(1);
        assertEquals(2, view.itemInputs().get(0).alternatives().get(0).getCount());
        assertEquals(8, view.itemOutputs().get(0).getCount());
        assertEquals(8, recipe.result().getCount());
    }

    @Test
    void precisionViewRetainsTwoInputTwoOutputSlotOrder() {
        PrecisionAssemblerRecipe recipe = new PrecisionAssemblerRecipe(
                id("precision_short_view"),
                List.of(
                        new PrecisionAssemblerRecipe.Input(
                                Ingredient.of(Items.IRON_INGOT, Items.GOLD_INGOT), 2),
                        input(Items.REDSTONE, 2)
                ),
                List.of(new ItemStack(Items.COMPARATOR), new ItemStack(Items.REDSTONE_TORCH, 2)),
                20, 40, 256
        );
        MachineRecipeView view = MachineRecipeView.from(recipe.processDefinition());

        assertEquals(2, view.itemInputs().size());
        assertEquals("item_input_0", view.itemInputs().get(0).channel());
        assertEquals("item_input_1", view.itemInputs().get(1).channel());
        assertEquals(2, view.itemInputs().get(0).alternatives().size());
        assertTrue(view.itemInputs().get(0).alternatives().stream()
                .allMatch(stack -> stack.getCount() == 2));
        assertTrue(view.fluidInputs().isEmpty());
        assertEquals(2, view.itemOutputs().size());
        assertEquals(Items.COMPARATOR, view.itemOutputs().get(0).getItem());
        assertEquals(Items.REDSTONE_TORCH, view.itemOutputs().get(1).getItem());
        assertEquals(2, view.itemOutputs().get(1).getCount());
        assertEquals(20, view.durationTicks());
        assertEquals(40, view.energyPerTick());
    }

    @Test
    void precisionViewRetainsFiveInputFixedChannels() {
        PrecisionAssemblerRecipe recipe = new PrecisionAssemblerRecipe(
                id("precision_long_view"),
                List.of(
                        input(Items.IRON_INGOT, 2), input(Items.REDSTONE, 2),
                        input(Items.GOLD_INGOT, 1), input(Items.QUARTZ, 1),
                        input(Items.COPPER_INGOT, 1)
                ),
                List.of(new ItemStack(Items.COMPARATOR)), 30, 50, 512
        );
        MachineRecipeView view = MachineRecipeView.from(recipe.processDefinition());

        assertEquals(5, view.itemInputs().size());
        for (int index = 0; index < view.itemInputs().size(); index++) {
            assertEquals("item_input_" + index, view.itemInputs().get(index).channel());
        }
        assertEquals(Items.COPPER_INGOT, view.itemInputs().get(4).alternatives().get(0).getItem());
        assertEquals(1, view.itemOutputs().size());
        assertEquals(30, view.durationTicks());
        assertEquals(50, view.energyPerTick());
    }

    private static PrecisionAssemblerRecipe.Input input(net.minecraft.world.item.Item item, int count) {
        return new PrecisionAssemblerRecipe.Input(Ingredient.of(item), count);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.tryBuild("advancedrocketrycommunity", path);
    }
}
