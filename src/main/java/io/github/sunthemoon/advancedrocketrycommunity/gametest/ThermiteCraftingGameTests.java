package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.config.CommonConfig;
import io.github.sunthemoon.advancedrocketrycommunity.config.SwitchOverrides;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

/** Existing material remains craftable when the active press is disabled. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ThermiteCraftingGameTests {
    private ThermiteCraftingGameTests() { }

    @GameTest(template = "empty", batch = "thermite_press_disabled", timeoutTicks = 20)
    public static void disabledPressDoesNotDisableExistingDustCrafting(GameTestHelper helper) {
        SwitchOverrides.set(CommonConfig.SMALL_PLATE_PRESS_ENABLED, false);
        try {
            helper.assertTrue(!CommonConfig.smallPlatePressEnabled(), "Press override is not disabled");
            assertRecipe(helper, "thermite", 1, "aluminum_dust", "iron_dust");
            assertRecipe(helper, "thermite_torch", 4, "thermite", "minecraft:stick");
        } finally {
            SwitchOverrides.clear(CommonConfig.SMALL_PLATE_PRESS_ENABLED);
        }
        helper.succeed();
    }

    private static void assertRecipe(GameTestHelper helper, String result, int count, String... inputs) {
        TransientCraftingContainer grid = new TransientCraftingContainer(new NoMenu(), 3, 3);
        for (int slot = 0; slot < inputs.length; slot++) {
            String key = inputs[slot].contains(":") ? inputs[slot] : ModIdentity.MOD_ID + ":" + inputs[slot];
            var item = ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(key));
            helper.assertTrue(item != null && item != Items.AIR, "Missing crafting input " + key);
            ItemStack stack = new ItemStack(item, slot + 2);
            stack.getOrCreateTag().putString("thermite_switch_witness", "retained");
            grid.setItem(slot, stack);
        }
        List<ItemStack> before = java.util.stream.IntStream.range(0, grid.getContainerSize())
                .mapToObj(slot -> grid.getItem(slot).copy()).toList();
        ResourceLocation id = ResourceLocation.tryParse(ModIdentity.MOD_ID + ":" + result);
        var recipe = helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, grid, helper.getLevel())
                .orElseThrow();
        helper.assertTrue(recipe.getId().equals(id), "Disabled press selected another crafting recipe");
        ItemStack output = recipe.assemble(grid, helper.getLevel().registryAccess());
        helper.assertTrue(output.is(ForgeRegistries.ITEMS.getValue(id)) && output.getCount() == count
                && !output.hasTag(), "Disabled press changed ordinary crafting output");
        for (int slot = 0; slot < grid.getContainerSize(); slot++) {
            helper.assertTrue(ItemStack.matches(before.get(slot), grid.getItem(slot)), "Matching/assembly mutated input " + slot);
        }
    }

    private static final class NoMenu extends AbstractContainerMenu {
        private NoMenu() { super(null, -1); }
        @Override public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }
        @Override public boolean stillValid(Player player) { return false; }
    }
}
