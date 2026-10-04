package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.material.MaterialContent;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Actual registered crafting/tag matching, not the press's block-processing route. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SmallPlatePressCraftingGameTests {
    private SmallPlatePressCraftingGameTests() { }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void pistonAndThreeIronIngotsCraftOnePressWithoutChangingInputs(GameTestHelper helper) {
        var found = helper.getLevel().getRecipeManager().byKey(ModIdentity.id("small_plate_press"));
        helper.assertTrue(found.orElse(null) instanceof ShapedRecipe, "Missing shaped press acquisition recipe");
        ShapedRecipe recipe = (ShapedRecipe) found.orElseThrow();
        helper.assertTrue(recipe.getWidth() == 3 && recipe.getHeight() == 2, "Unexpected normalized press grid");
        for (int offset = 0; offset <= 1; offset++) {
            TransientCraftingContainer base = grid(offset);
            check(helper, recipe, base, true);
            TransientCraftingContainer mirror = grid(offset);
            for (int row = 0; row < 3; row++) {
                ItemStack left = mirror.getItem(row * 3).copy();
                mirror.setItem(row * 3, mirror.getItem(row * 3 + 2).copy());
                mirror.setItem(row * 3 + 2, left);
            }
            check(helper, recipe, mirror, true);
            for (int slot = 0; slot < 9; slot++) {
                TransientCraftingContainer changed = grid(offset);
                if (changed.getItem(slot).isEmpty()) {
                    changed.setItem(slot, marked(Items.REDSTONE));
                } else {
                    changed.setItem(slot, ItemStack.EMPTY);
                }
                check(helper, recipe, changed, false);
            }
            TransientCraftingContainer sticky = grid(offset);
            sticky.setItem(offset * 3 + 1, marked(Items.STICKY_PISTON));
            check(helper, recipe, sticky, false);
            for (int column = 0; column < 3; column++) {
                TransientCraftingContainer copper = grid(offset);
                copper.setItem((offset + 1) * 3 + column, marked(Items.COPPER_INGOT));
                check(helper, recipe, copper, false);
                TransientCraftingContainer plate = grid(offset);
                plate.setItem((offset + 1) * 3 + column, marked(MaterialContent.item("iron_plate")));
                check(helper, recipe, plate, false);
            }
        }
        TransientCraftingContainer wrongCenter = grid(0);
        wrongCenter.setItem(0, wrongCenter.getItem(1).copy());
        wrongCenter.setItem(1, ItemStack.EMPTY);
        check(helper, recipe, wrongCenter, false);
        TransientCraftingContainer inverted = emptyGrid();
        for (int column = 0; column < 3; column++) { inverted.setItem(column, marked(Items.IRON_INGOT)); }
        inverted.setItem(4, marked(Items.PISTON));
        check(helper, recipe, inverted, false);
        helper.succeed();
    }

    private static TransientCraftingContainer grid(int verticalOffset) {
        TransientCraftingContainer grid = emptyGrid();
        grid.setItem(verticalOffset * 3 + 1, marked(Items.PISTON));
        for (int column = 0; column < 3; column++) {
            grid.setItem((verticalOffset + 1) * 3 + column, marked(Items.IRON_INGOT));
        }
        return grid;
    }

    private static TransientCraftingContainer emptyGrid() {
        return new TransientCraftingContainer(new NoMenu(), 3, 3);
    }

    private static ItemStack marked(Item item) {
        ItemStack stack = new ItemStack(item);
        stack.getOrCreateTag().putString("press_acquisition_check", "retained");
        return stack;
    }

    private static void check(GameTestHelper helper, ShapedRecipe recipe,
            TransientCraftingContainer grid, boolean accepted) {
        List<ItemStack> before = new ArrayList<>();
        for (int slot = 0; slot < 9; slot++) { before.add(grid.getItem(slot).copy()); }
        helper.assertTrue(recipe.matches(grid, helper.getLevel()) == accepted,
                "Unexpected press recipe match for " + before);
        if (accepted) {
            var selected = helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, grid, helper.getLevel());
            helper.assertTrue(selected.isPresent() && selected.get().getId().equals(recipe.getId()),
                    "Actual crafting lookup did not select the press recipe");
            ItemStack result = recipe.assemble(grid, helper.getLevel().registryAccess());
            helper.assertTrue(result.is(MaterialContent.SMALL_PLATE_PRESS_ITEM.get()) && result.getCount() == 1
                            && !result.hasTag(), "Press output identity/count/payload changed");
        }
        for (int slot = 0; slot < 9; slot++) {
            helper.assertTrue(ItemStack.matches(before.get(slot), grid.getItem(slot)),
                    "Matching/assembly changed an input at slot " + slot);
        }
    }

    private static final class NoMenu extends AbstractContainerMenu {
        private NoMenu() { super(null, -1); }
        @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
        @Override public boolean stillValid(Player player) { return false; }
    }
}
