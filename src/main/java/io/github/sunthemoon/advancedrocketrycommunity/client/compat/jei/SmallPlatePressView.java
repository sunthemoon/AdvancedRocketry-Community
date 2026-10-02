package io.github.sunthemoon.advancedrocketrycommunity.client.compat.jei;

import io.github.sunthemoon.advancedrocketrycommunity.material.press.SmallPlatePressRecipe;
import java.util.Arrays;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** What the JEI press category shows for one recipe: the block forms pressed, the anvil block and the output. */
record SmallPlatePressView(List<ItemStack> inputs, ItemStack anvil, ItemStack output) {
    /** Copies, so the view never changes the recipe. */
    static SmallPlatePressView of(SmallPlatePressRecipe recipe) {
        List<ItemStack> inputs = Arrays.stream(recipe.ingredient().getItems()).map(ItemStack::copy).toList();
        return new SmallPlatePressView(inputs, new ItemStack(Items.OBSIDIAN), recipe.result());
    }
}
