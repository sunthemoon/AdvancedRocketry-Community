package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

/** Loaded matches/assemble and native stack round trips, not player consumption or dedicated restart. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CraftingComponentGameTests {
    private static final String BATCH = "crafting_components";
    private static final ResourceLocation CARBON = ModIdentity.id("carbon_brick");
    private static final ResourceLocation UI = ModIdentity.id("user_interface");

    private CraftingComponentGameTests() { }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 20)
    public static void ordinaryRegisteredItemsPreserveNativeStackIdsAndCounts(GameTestHelper helper) {
        helper.assertTrue(item(helper, CARBON) == ModItems.CARBON_BRICK.get()
                && item(helper, UI) == ModItems.USER_INTERFACE.get(), "Registry objects do not resolve the literal IDs");
        for (ResourceLocation id : List.of(CARBON, UI)) {
            Item item = item(helper, id);
            helper.assertTrue(item.getClass() == Item.class, "Component is not an ordinary Item: " + id);
            for (int count : new int[] {1, 37, 64}) {
                ItemStack original = new ItemStack(item, count);
                helper.assertTrue(original.getMaxStackSize() == 64 && !original.hasTag()
                        && !original.isDamageableItem(), "Component defaults or payload changed: " + id);
                CompoundTag saved = original.save(new CompoundTag());
                helper.assertTrue(saved.getString("id").equals(id.toString()) && saved.getByte("Count") == count,
                        "Native saved identity/count changed: " + id);
                ItemStack restored = ItemStack.of(saved);
                helper.assertTrue(ItemStack.matches(original, restored) && restored.is(item)
                        && restored.getCount() == count && !restored.hasTag(), "Native stack round trip changed: " + id);
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 20)
    public static void loadedSelectorsAndDefaultFuelUseActualRuntimeMembership(GameTestHelper helper) {
        membership(helper, Items.LIME_DYE, "dyes/lime", true);
        membership(helper, Items.RED_DYE, "dyes/lime", false);
        membership(helper, Items.GREEN_DYE, "dyes/lime", false);
        membership(helper, Items.REDSTONE, "dusts/redstone", true);
        membership(helper, Items.COAL, "dusts/redstone", false);
        membership(helper, Items.GLOWSTONE_DUST, "dusts/glowstone", true);
        membership(helper, Items.GLOWSTONE, "dusts/glowstone", false);
        membership(helper, item(helper, CARBON), "ingots/carbon", true);
        membership(helper, item(helper, UI), "ingots/carbon", false);
        membership(helper, Items.CHARCOAL, "ingots/carbon", false);
        helper.assertTrue(ForgeHooks.getBurnTime(new ItemStack(Items.CHARCOAL), RecipeType.SMELTING) == 1600,
                "Vanilla charcoal fuel control changed");
        for (ResourceLocation id : List.of(CARBON, UI)) {
            helper.assertTrue(ForgeHooks.getBurnTime(new ItemStack(item(helper, id)), RecipeType.SMELTING) == 0,
                    "Ordinary component gained default furnace fuel behavior: " + id);
        }
        // This is the clean built-in runtime, not a prohibition on another mod's fuel-event overrides.
        helper.succeed();
    }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 20)
    public static void loadedCarbonShapelessRecipeAssemblesFromSixSeparateSlots(GameTestHelper helper) {
        ShapelessRecipe recipe = carbonRecipe(helper);
        helper.assertTrue(recipe.getIngredients().size() == 6 && recipe.getGroup().isEmpty(),
                "Carbon recipe slot count/group changed");
        check(helper, recipe, charcoal(0, 1, 2, 3, 4, 5), CARBON, true);
        check(helper, recipe, charcoal(0, 2, 4, 6, 7, 8), CARBON, true);
        // Query/assemble leaves these marked, non-unit input stacks untouched; no player consumes them here.
        TransientCraftingContainer fullStacks = charcoal(0, 1, 2, 3, 4, 5);
        for (int slot = 0; slot < 6; slot++) { fullStacks.getItem(slot).setCount(64); }
        check(helper, recipe, fullStacks, CARBON, true);
        helper.succeed();
    }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 20)
    public static void loadedCarbonRejectsCoalMissingExtraAndSingleLargeStack(GameTestHelper helper) {
        ShapelessRecipe recipe = carbonRecipe(helper);
        for (int slot = 0; slot < 6; slot++) {
            TransientCraftingContainer missing = charcoal(0, 1, 2, 3, 4, 5);
            missing.setItem(slot, ItemStack.EMPTY);
            check(helper, recipe, missing, CARBON, false);
            TransientCraftingContainer wrong = charcoal(0, 1, 2, 3, 4, 5);
            wrong.setItem(slot, marked(Items.COAL, 1));
            check(helper, recipe, wrong, CARBON, false);
        }
        check(helper, recipe, charcoal(0, 1, 2, 3, 4, 5, 6), CARBON, false);
        TransientCraftingContainer single = grid(3, 3);
        single.setItem(4, marked(Items.CHARCOAL, 6));
        check(helper, recipe, single, CARBON, false);
        single.getItem(4).setCount(64);
        check(helper, recipe, single, CARBON, false);
        check(helper, recipe, grid(3, 3), CARBON, false);
        TransientCraftingContainer small = grid(2, 2);
        for (int slot = 0; slot < 4; slot++) { small.setItem(slot, marked(Items.CHARCOAL, 64)); }
        check(helper, recipe, small, CARBON, false);
        helper.succeed();
    }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 20)
    public static void loadedUiShapedRecipeAssemblesAtEitherVerticalOffset(GameTestHelper helper) {
        ShapedRecipe recipe = uiRecipe(helper);
        helper.assertTrue(recipe.getWidth() == 3 && recipe.getHeight() == 2 && recipe.getGroup().isEmpty(),
                "UI recipe dimensions/group changed");
        check(helper, recipe, panel(0), UI, true);
        check(helper, recipe, panel(1), UI, true);
        // lrl/fgf is horizontally symmetric: reversing columns cannot distinguish the mirror branch.
        TransientCraftingContainer exactSize = grid(3, 2);
        TransientCraftingContainer source = panel(0);
        for (int slot = 0; slot < 6; slot++) { exactSize.setItem(slot, source.getItem(slot).copy()); }
        check(helper, recipe, exactSize, UI, true);
        helper.succeed();
    }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 20)
    public static void loadedUiRejectsWrongMissingExtraAndMisplacedIngredients(GameTestHelper helper) {
        ShapedRecipe recipe = uiRecipe(helper);
        for (int offset = 0; offset <= 1; offset++) {
            int first = offset * 3;
            for (int slot = first; slot < first + 6; slot++) {
                TransientCraftingContainer missing = panel(offset);
                missing.setItem(slot, ItemStack.EMPTY);
                check(helper, recipe, missing, UI, false);
            }
            for (int slot : new int[] {first, first + 2}) {
                for (Item dye : List.of(Items.RED_DYE, Items.GREEN_DYE, Items.WHITE_DYE)) {
                    TransientCraftingContainer wrong = panel(offset);
                    wrong.setItem(slot, marked(dye, 1));
                    check(helper, recipe, wrong, UI, false);
                }
            }
            for (Item glass : List.of(Items.GLASS, Items.TINTED_GLASS)) {
                TransientCraftingContainer wrong = panel(offset);
                wrong.setItem(first + 4, marked(glass, 1));
                check(helper, recipe, wrong, UI, false);
            }
            for (int slot : new int[] {first + 1, first + 3, first + 5}) {
                TransientCraftingContainer wrong = panel(offset);
                wrong.setItem(slot, marked(slot == first + 1 ? Items.COAL : Items.GLOWSTONE, 1));
                check(helper, recipe, wrong, UI, false);
            }
            int emptyRow = offset == 0 ? 6 : 0;
            for (int slot = emptyRow; slot < emptyRow + 3; slot++) {
                TransientCraftingContainer extra = panel(offset);
                extra.setItem(slot, marked(Items.CHARCOAL, 1));
                check(helper, recipe, extra, UI, false);
            }
        }
        TransientCraftingContainer reversedRows = panel(0);
        for (int column = 0; column < 3; column++) {
            ItemStack first = reversedRows.getItem(column).copy();
            reversedRows.setItem(column, reversedRows.getItem(column + 3).copy());
            reversedRows.setItem(column + 3, first);
        }
        check(helper, recipe, reversedRows, UI, false);
        check(helper, recipe, grid(3, 3), UI, false);
        check(helper, recipe, grid(2, 2), UI, false);
        helper.succeed();
    }

    private static ShapelessRecipe carbonRecipe(GameTestHelper helper) {
        var found = helper.getLevel().getRecipeManager().byKey(CARBON);
        helper.assertTrue(found.orElse(null) instanceof ShapelessRecipe, "Loaded carbon recipe is absent/not shapeless");
        return (ShapelessRecipe) found.orElseThrow();
    }

    private static ShapedRecipe uiRecipe(GameTestHelper helper) {
        var found = helper.getLevel().getRecipeManager().byKey(UI);
        helper.assertTrue(found.orElse(null) instanceof ShapedRecipe, "Loaded UI recipe is absent/not shaped");
        return (ShapedRecipe) found.orElseThrow();
    }

    private static Item item(GameTestHelper helper, ResourceLocation id) {
        helper.assertTrue(ForgeRegistries.ITEMS.containsKey(id), "Literal component ID is absent: " + id);
        Item item = ForgeRegistries.ITEMS.getValue(id);
        helper.assertTrue(item != null && item != Items.AIR, "Component resolved to missing/AIR fallback: " + id);
        return item;
    }

    private static void membership(GameTestHelper helper, Item item, String path, boolean expected) {
        var tag = TagKey.create(Registries.ITEM, new ResourceLocation("forge", path));
        helper.assertTrue(new ItemStack(item).is(tag) == expected, "Loaded forge:" + path + " membership changed: " + item);
    }

    private static TransientCraftingContainer charcoal(int... slots) {
        TransientCraftingContainer grid = grid(3, 3);
        for (int slot : slots) { grid.setItem(slot, marked(Items.CHARCOAL, slot + 2)); }
        return grid;
    }

    private static TransientCraftingContainer panel(int offset) {
        TransientCraftingContainer grid = grid(3, 3);
        Item[] ingredients = {Items.LIME_DYE, Items.REDSTONE, Items.LIME_DYE,
                Items.GLOWSTONE_DUST, Items.GLASS_PANE, Items.GLOWSTONE_DUST};
        for (int slot = 0; slot < ingredients.length; slot++) {
            grid.setItem(offset * 3 + slot, marked(ingredients[slot], slot + 2));
        }
        return grid;
    }

    private static TransientCraftingContainer grid(int width, int height) {
        return new TransientCraftingContainer(new NoMenu(), width, height);
    }

    private static ItemStack marked(Item item, int count) {
        ItemStack stack = new ItemStack(item, count);
        stack.getOrCreateTag().putString("crafting_component_input_probe", "retained");
        return stack;
    }

    private static void check(GameTestHelper helper, CraftingRecipe recipe,
            TransientCraftingContainer grid, ResourceLocation expected, boolean accepted) {
        List<ItemStack> before = new ArrayList<>();
        for (int slot = 0; slot < grid.getContainerSize(); slot++) { before.add(grid.getItem(slot).copy()); }
        helper.assertTrue(recipe.getId().equals(expected), "Loaded recipe has another ID");
        helper.assertTrue(recipe.matches(grid, helper.getLevel()) == accepted, "Unexpected recipe match: " + expected);
        var selected = helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, grid, helper.getLevel());
        if (accepted) {
            helper.assertTrue(selected.isPresent() && selected.get().getId().equals(expected),
                    "Native crafting lookup selected another recipe: " + expected);
            ItemStack result = recipe.assemble(grid, helper.getLevel().registryAccess());
            helper.assertTrue(result.is(item(helper, expected)) && result.getCount() == 1 && !result.hasTag(),
                    "Native assembly identity/count/payload changed: " + expected);
            var remainders = recipe.getRemainingItems(grid);
            helper.assertTrue(remainders.size() == grid.getContainerSize() && remainders.stream().allMatch(ItemStack::isEmpty),
                    "Component recipe acquired a native crafting remainder");
        } else {
            helper.assertTrue(selected.isEmpty() || !selected.get().getId().equals(expected),
                    "Native crafting lookup accepted a refused grid: " + expected);
        }
        for (int slot = 0; slot < before.size(); slot++) {
            helper.assertTrue(ItemStack.matches(before.get(slot), grid.getItem(slot)), "Query/assembly mutated input " + slot);
        }
    }

    private static final class NoMenu extends AbstractContainerMenu {
        private NoMenu() { super(null, -1); }
        @Override public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }
        @Override public boolean stillValid(Player player) { return false; }
    }
}
