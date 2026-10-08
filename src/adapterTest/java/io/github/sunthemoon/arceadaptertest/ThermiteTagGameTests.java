package io.github.sunthemoon.arceadaptertest;

import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
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
import net.minecraft.world.level.Level;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

/** Independent API consumer: common tags, vanilla recipes and the actual ready-event environment. */
@GameTestHolder(AdapterTestMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ThermiteTagGameTests {
    private static final String HOST = "advancedrocketrycommunity";

    private ThermiteTagGameTests() { }

    @GameTest(templateNamespace = HOST, template = "empty", timeoutTicks = 20)
    public static void nativeRecipeAcceptsForeignDustWithoutMutatingInputs(GameTestHelper helper) {
        Item aluminum = FixtureThermiteItems.ALUMINUM.get();
        Item iron = FixtureThermiteItems.IRON.get();
        membership(helper, aluminum, "dusts/aluminum");
        membership(helper, iron, "dusts/iron");
        TransientCraftingContainer grid = grid(stack(aluminum, 7), stack(iron, 9));
        CraftingRecipe recipe = recipe(helper, "thermite");
        assertAssembly(helper, recipe, grid, item("thermite"), 1);
        // Also exercise the actual manager, not only a locally deserialized recipe.
        helper.assertTrue(helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, grid,
                helper.getLevel()).orElseThrow().getId().equals(id("thermite")), "Foreign inputs selected another recipe");
        assertAssembly(helper, recipe, grid(stack(item("aluminum_dust"), 1), stack(item("iron_dust"), 1)),
                item("thermite"), 1);
        helper.succeed();
    }

    @GameTest(templateNamespace = HOST, template = "empty", timeoutTicks = 20)
    public static void torchRecipeUsesLoadedCommonThermiteAndWoodenRodTags(GameTestHelper helper) {
        Item thermite = FixtureThermiteItems.THERMITE.get();
        membership(helper, thermite, "dusts/thermite");
        membership(helper, Items.STICK, "rods/wooden");
        membership(helper, item("thermite"), "dusts/thermite");
        helper.assertTrue(new ItemStack(item("thermite")).is(tag("dusts")), "Thermite is absent from the dust umbrella");
        TransientCraftingContainer grid = grid(stack(thermite, 11), stack(Items.STICK, 4));
        CraftingRecipe recipe = recipe(helper, "thermite_torch");
        assertAssembly(helper, recipe, grid, item("thermite_torch"), 4);
        helper.assertTrue(helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, grid,
                helper.getLevel()).orElseThrow().getId().equals(id("thermite_torch")), "Foreign thermite did not select torch recipe");
        assertAssembly(helper, recipe, grid(stack(item("thermite"), 1), stack(Items.STICK, 1)), item("thermite_torch"), 4);
        helper.succeed();
    }

    @GameTest(templateNamespace = HOST, template = "empty", timeoutTicks = 20)
    public static void vanillaMatchingRejectsWrongMissingAndExtraInputs(GameTestHelper helper) {
        CraftingRecipe dust = recipe(helper, "thermite");
        CraftingRecipe torch = recipe(helper, "thermite_torch");
        List<TransientCraftingContainer> invalidDust = List.of(
                grid(stack(FixtureThermiteItems.ALUMINUM.get(), 1)),
                grid(stack(FixtureThermiteItems.ALUMINUM.get(), 1), stack(Items.DIRT, 1)),
                grid(stack(FixtureThermiteItems.ALUMINUM.get(), 1), stack(FixtureThermiteItems.IRON.get(), 1),
                        stack(Items.STICK, 1)));
        List<TransientCraftingContainer> invalidTorch = List.of(
                grid(stack(FixtureThermiteItems.THERMITE.get(), 1)),
                grid(stack(FixtureThermiteItems.THERMITE.get(), 1), stack(Items.IRON_INGOT, 1)),
                grid(stack(FixtureThermiteItems.THERMITE.get(), 1), stack(Items.STICK, 1), stack(Items.STICK, 1)));
        for (var input : invalidDust) { rejectsUnchanged(helper, dust, input); }
        for (var input : invalidTorch) { rejectsUnchanged(helper, torch, input); }
        helper.succeed();
    }

    @GameTest(templateNamespace = HOST, template = "empty", timeoutTicks = 20)
    public static void actualEnvironmentHandleReportsConfiguredMoonVacuum(GameTestHelper helper) {
        ResourceKey<Level> moon = ResourceKey.create(Registries.DIMENSION, id("moon"));
        var snapshot = EnvironmentQueryFixture.queries().at(moon, net.minecraft.core.BlockPos.ZERO).orElseThrow();
        helper.assertTrue(snapshot.bodyId().equals(id("moon")) && snapshot.vacuum()
                && !snapshot.atmosphere().orElseThrow().breathable(), "Configured Moon vacuum is unavailable or differs");
        helper.assertTrue(ForgeRegistries.BLOCKS.getValue(id("thermite_torch")).defaultBlockState()
                        .getLightEmission(helper.getLevel(), net.minecraft.core.BlockPos.ZERO) == 14,
                "Registered passive light differs");
        helper.succeed();
    }

    private static void assertAssembly(GameTestHelper helper, CraftingRecipe recipe,
                                       TransientCraftingContainer grid, Item item, int count) {
        List<ItemStack> before = copies(grid);
        helper.assertTrue(recipe.matches(grid, helper.getLevel()), "Expected native recipe did not match");
        ItemStack result = recipe.assemble(grid, helper.getLevel().registryAccess());
        helper.assertTrue(result.is(item) && result.getCount() == count && !result.hasTag(), "Native recipe output differs");
        unchanged(helper, grid, before);
    }

    private static void rejectsUnchanged(GameTestHelper helper, CraftingRecipe recipe, TransientCraftingContainer grid) {
        List<ItemStack> before = copies(grid);
        helper.assertTrue(!recipe.matches(grid, helper.getLevel()), "Invalid inputs matched native recipe");
        unchanged(helper, grid, before);
    }

    private static void unchanged(GameTestHelper helper, TransientCraftingContainer grid, List<ItemStack> before) {
        for (int i = 0; i < grid.getContainerSize(); i++) {
            helper.assertTrue(ItemStack.matches(before.get(i), grid.getItem(i)), "Recipe mutated input slot " + i);
        }
    }

    private static List<ItemStack> copies(TransientCraftingContainer grid) {
        return java.util.stream.IntStream.range(0, grid.getContainerSize()).mapToObj(i -> grid.getItem(i).copy()).toList();
    }

    private static void membership(GameTestHelper helper, Item item, String path) {
        helper.assertTrue(new ItemStack(item).is(tag(path)), "Missing loaded Forge tag " + path);
    }

    private static TagKey<Item> tag(String path) { return TagKey.create(Registries.ITEM, ResourceLocation.tryParse("forge:" + path)); }
    private static ResourceLocation id(String path) { return ResourceLocation.tryParse(HOST + ":" + path); }
    private static Item item(String path) {
        Item item = ForgeRegistries.ITEMS.getValue(id(path));
        if (item == null || item == Items.AIR) { throw new IllegalStateException("Missing host item " + path); }
        return item;
    }

    private static CraftingRecipe recipe(GameTestHelper helper, String path) {
        return (CraftingRecipe) helper.getLevel().getRecipeManager().byKey(id(path)).orElseThrow();
    }

    private static ItemStack stack(Item item, int count) {
        ItemStack stack = new ItemStack(item, count);
        stack.getOrCreateTag().putString("adapter_thermite_witness", "preserve");
        return stack;
    }

    private static TransientCraftingContainer grid(ItemStack... inputs) {
        TransientCraftingContainer grid = new TransientCraftingContainer(new NoMenu(), 3, 3);
        for (int i = 0; i < inputs.length; i++) { grid.setItem(i, inputs[i]); }
        return grid;
    }

    private static final class NoMenu extends AbstractContainerMenu {
        NoMenu() { super(null, -1); }
        @Override public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }
        @Override public boolean stillValid(Player player) { return false; }
    }
}
