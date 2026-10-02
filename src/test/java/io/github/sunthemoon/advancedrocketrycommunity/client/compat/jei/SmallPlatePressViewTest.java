package io.github.sunthemoon.advancedrocketrycommunity.client.compat.jei;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.material.press.SmallPlatePressRecipe;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** ADR-063 section 9 (A0, {@code integration:jei/platePresser}): the press category's view of one recipe. */
class SmallPlatePressViewTest {
    @BeforeAll
    static void bootstrap() {
        MinecraftBootstrap.initialize();
    }

    @Test
    void theViewListsEveryBlockFormTheAnvilAndTheOutput() {
        SmallPlatePressRecipe recipe = new SmallPlatePressRecipe(
                ResourceLocation.tryBuild("advancedrocketrycommunity", "press_view"),
                Ingredient.of(Items.IRON_BLOCK, Items.GOLD_BLOCK), new ItemStack(Items.IRON_INGOT, 4));
        SmallPlatePressView view = SmallPlatePressView.of(recipe);

        assertEquals(List.of(Items.IRON_BLOCK, Items.GOLD_BLOCK),
                view.inputs().stream().map(ItemStack::getItem).toList());
        assertTrue(view.anvil().is(Items.OBSIDIAN));
        assertTrue(view.output().is(Items.IRON_INGOT));
        assertEquals(4, view.output().getCount());

        view.inputs().get(0).shrink(1);
        view.output().shrink(1);
        assertEquals(4, recipe.result().getCount());
        assertEquals(1, recipe.ingredient().getItems()[0].getCount());
    }

    @Test
    void unboundedResultsAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> new SmallPlatePressRecipe(
                ResourceLocation.tryBuild("advancedrocketrycommunity", "press_large"),
                Ingredient.of(Items.IRON_BLOCK), new ItemStack(Items.IRON_INGOT, 65)));
        assertThrows(IllegalArgumentException.class, () -> new SmallPlatePressRecipe(
                ResourceLocation.tryBuild("advancedrocketrycommunity", "press_empty"),
                Ingredient.EMPTY, new ItemStack(Items.IRON_INGOT, 1)));
    }
}
