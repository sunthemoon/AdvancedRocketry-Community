package io.github.sunthemoon.advancedrocketrycommunity.material.press;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** ADR-063 section 3 (revision 4): one matching recipe presses; two are ambiguous and the press refuses. */
class SmallPlatePressRecipeTest {
    @BeforeAll
    static void bootstrap() {
        MinecraftBootstrap.initialize();
    }

    @Test
    void oneMatchIsTheRecipeAndTwoMatchesAreAmbiguous() {
        SmallPlatePressRecipe iron = recipe("iron", Ingredient.of(Items.IRON_BLOCK), Items.IRON_INGOT);
        SmallPlatePressRecipe gold = recipe("gold", Ingredient.of(Items.GOLD_BLOCK), Items.GOLD_INGOT);
        SmallPlatePressRecipe either = recipe("either", Ingredient.of(Items.IRON_BLOCK, Items.COPPER_BLOCK),
                Items.COPPER_INGOT);
        SmallPlatePressRecipe third = recipe("third", Ingredient.of(Items.IRON_BLOCK), Items.IRON_NUGGET);

        assertEquals(List.of(), SmallPlatePressRecipe.matching(List.of(iron, gold), new ItemStack(Items.STONE)));
        assertEquals(List.of(gold), SmallPlatePressRecipe.matching(List.of(iron, gold), new ItemStack(Items.GOLD_BLOCK)));
        assertEquals(List.of(iron, either),
                SmallPlatePressRecipe.matching(List.of(iron, gold, either, third), new ItemStack(Items.IRON_BLOCK)));
        assertEquals(List.of(either),
                SmallPlatePressRecipe.matching(List.of(iron, either), new ItemStack(Items.COPPER_BLOCK)));
        assertTrue(SmallPlatePressRecipe.matching(List.of(), new ItemStack(Items.IRON_BLOCK)).isEmpty());
    }

    /** C15aR1-L1: the press uses exactly one match and refuses two (ADR-063 section 3, revision 4). */
    @Test
    void selectionUsesOneMatchAndRefusesTwo() {
        SmallPlatePressRecipe iron = recipe("iron", Ingredient.of(Items.IRON_BLOCK), Items.IRON_INGOT);
        SmallPlatePressRecipe overlap = recipe("overlap", Ingredient.of(Items.IRON_BLOCK), Items.IRON_NUGGET);
        SmallPlatePressRecipe gold = recipe("gold", Ingredient.of(Items.GOLD_BLOCK), Items.GOLD_INGOT);

        SmallPlatePressRecipe.Selection none = SmallPlatePressRecipe.select(List.of(iron, gold),
                new ItemStack(Items.STONE));
        assertTrue(none.recipe().isEmpty() && !none.ambiguous());
        SmallPlatePressRecipe.Selection one = SmallPlatePressRecipe.select(List.of(iron, gold),
                new ItemStack(Items.IRON_BLOCK));
        assertEquals(iron, one.recipe().orElseThrow());
        assertTrue(!one.ambiguous());
        SmallPlatePressRecipe.Selection two = SmallPlatePressRecipe.select(List.of(iron, gold, overlap),
                new ItemStack(Items.IRON_BLOCK));
        assertTrue(two.recipe().isEmpty() && two.ambiguous(), "two matches must be refused, not the first used");
    }

    private static SmallPlatePressRecipe recipe(String name, Ingredient ingredient, net.minecraft.world.item.Item result) {
        return new SmallPlatePressRecipe(ResourceLocation.tryBuild("advancedrocketrycommunity", name), ingredient,
                new ItemStack(result, 4));
    }
}
