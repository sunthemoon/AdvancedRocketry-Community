package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.machine.precision.PrecisionAssemblerPortBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.machine.precision.PrecisionAssemblerRecipe;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessMachineState;
import io.github.sunthemoon.advancedrocketrycommunity.material.MaterialContent;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModRecipes;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.registries.ForgeRegistries;

/** Test-given inputs qualify installed producers, not clean-world steel or player crafting consumption. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ControlBoardGameTests {
    private static final String BATCH = "control_board";
    private static final ResourceLocation ITEM = ModIdentity.id("control_board");
    private static final ResourceLocation PRECISION = ModIdentity.id("precision_control_board");
    private static final BlockPos INPUT_2 = new BlockPos(1, 1, 2);
    private ControlBoardGameTests() { }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 20)
    public static void plainItemPreservesNativeIdsCountsAndNoFuel(GameTestHelper helper) {
        helper.assertTrue(ForgeRegistries.ITEMS.containsKey(ITEM) && ForgeRegistries.ITEMS.getValue(ITEM) == ModItems.CONTROL_BOARD.get(),
                "Literal Control identity is not registered");
        Item item = ModItems.CONTROL_BOARD.get();
        helper.assertTrue(item.getClass() == Item.class, "Board acquired custom Item behavior");
        for (int count : new int[] {1, 37, 64}) {
            ItemStack stack = new ItemStack(item, count);
            CompoundTag saved = stack.save(new CompoundTag());
            helper.assertTrue(stack.getMaxStackSize() == 64 && !stack.hasTag() && !stack.isDamageableItem()
                    && saved.getString("id").equals(ITEM.toString()) && saved.getByte("Count") == count
                    && ItemStack.matches(stack, ItemStack.of(saved)), "Native board ItemStack round trip changed");
        }
        helper.assertTrue(ForgeHooks.getBurnTime(new ItemStack(item), RecipeType.SMELTING) == 0, "Board gained built-in fuel");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 20)
    public static void loadedSelectorsResolveActualMaterialAndWoodMembers(GameTestHelper helper) {
        membership(helper, Items.REDSTONE, "forge:dusts/redstone", true);
        membership(helper, Items.QUARTZ, "forge:gems/quartz", true);
        membership(helper, MaterialContent.item("copper_dust"), "forge:dusts/copper", true);
        membership(helper, Items.GOLD_INGOT, "forge:dusts/copper", false);
        membership(helper, MaterialContent.item("iron_plate"), "forge:plates/iron", true);
        membership(helper, Items.IRON_INGOT, "forge:plates/iron", false);
        membership(helper, MaterialContent.item("copper_plate"), "forge:dusts/copper", false);
        membership(helper, Items.COPPER_INGOT, "forge:dusts/copper", false);
        membership(helper, MaterialContent.item("copper_plate"), "forge:plates/copper", true);
        membership(helper, MaterialContent.item("copper_dust"), "forge:plates/copper", false);
        membership(helper, Items.COPPER_INGOT, "forge:plates/copper", false);
        membership(helper, MaterialContent.item("steel_plate"), "forge:plates/steel", true);
        membership(helper, Items.OAK_SLAB, "minecraft:wooden_slabs", true);
        membership(helper, Items.WARPED_SLAB, "minecraft:wooden_slabs", true);
        membership(helper, Items.STONE_SLAB, "minecraft:wooden_slabs", false);
        helper.succeed();
    }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 20)
    public static void loadedCraftingAssemblesOneBoardWithWoodAlternatives(GameTestHelper helper) {
        ShapedRecipe recipe = crafting(helper);
        helper.assertTrue(recipe.getWidth() == 3 && recipe.getHeight() == 3 && recipe.getGroup().isEmpty(), "Crafting shape/group changed");
        checkCraft(helper, recipe, grid(), true);
        var warped = grid();
        warped.setItem(4, new ItemStack(Items.WARPED_SLAB, 64));
        checkCraft(helper, recipe, warped, true);
        helper.succeed();
    }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 20)
    public static void loadedCraftingRejectsEachMissingWrongSlotAndSmallGrid(GameTestHelper helper) {
        ShapedRecipe recipe = crafting(helper);
        for (int slot = 0; slot < 9; slot++) {
            var missing = grid(); missing.setItem(slot, ItemStack.EMPTY);
            checkCraft(helper, recipe, missing, false);
            var wrong = grid(); wrong.setItem(slot, new ItemStack(Items.COAL));
            checkCraft(helper, recipe, wrong, false);
        }
        checkCraft(helper, recipe, new TransientCraftingContainer(new NoMenu(), 2, 2), false);
        helper.succeed();
    }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 20)
    public static void loadedPrecisionUsesUniqueBoundedExactChannels(GameTestHelper helper) {
        PrecisionAssemblerRecipe recipe = precision(helper);
        var inputs = precisionInputs();
        helper.assertTrue(recipe.available() && recipe.processingTicks() == 200 && recipe.energyPerTick() == 10
                && recipe.processDefinition().totalEnergy() == 2000 && recipe.inputs().size() == 3
                && recipe.outputs().size() == 1, "Loaded precision accounting/availability changed");
        helper.assertTrue(recipe.ingredientAlternatives().stream().allMatch(values -> !values.isEmpty() && values.size() <= 32),
                "Installed precision selectors exceed bounds");
        helper.assertTrue(recipe.matches(inputs, helper.getLevel()), "Precision inputs did not match");
        long matches = helper.getLevel().getRecipeManager().getAllRecipesFor(ModRecipes.PRECISION_ASSEMBLING_TYPE.get())
                .stream().filter(candidate -> candidate.matches(inputs, helper.getLevel())).count();
        helper.assertTrue(matches == 1, "Installed precision catalog has an ambiguous producer");
        ItemStack result = recipe.assemble(inputs, helper.getLevel().registryAccess());
        helper.assertTrue(result.is(ModItems.CONTROL_BOARD.get()) && result.getCount() == 1 && !result.hasTag(), "Precision result changed");
        for (int slot = 0; slot < 3; slot++) {
            var missing = precisionInputs(); missing.setItem(slot, ItemStack.EMPTY);
            helper.assertTrue(!recipe.matches(missing, helper.getLevel()), "Missing precision channel matched");
            var tagged = precisionInputs(); tagged.getItem(slot).getOrCreateTag().putInt("probe", 1);
            helper.assertTrue(!recipe.matches(tagged, helper.getLevel()), "Tagged precision input matched");
        }
        for (int slot = 3; slot < 5; slot++) {
            var extra = precisionInputs(); extra.setItem(slot, new ItemStack(Items.COAL));
            helper.assertTrue(!recipe.matches(extra, helper.getLevel()), "Occupied trailing precision channel matched");
        }
        var swapped = precisionInputs();
        ItemStack first = swapped.getItem(0); swapped.setItem(0, swapped.getItem(1)); swapped.setItem(1, first);
        helper.assertTrue(!recipe.matches(swapped, helper.getLevel()), "Reordered precision channels matched");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 260)
    public static void formedMachineConsumesOneBatchAndExactlyTwoThousandFE(GameTestHelper helper) {
        PrecisionAssemblerGameTests.placeStructure(helper);
        helper.runAtTickTime(8, () -> {
            precision(helper);
            Item[] items = {Items.REDSTONE, MaterialContent.item("copper_plate"), MaterialContent.item("steel_plate")};
            BlockPos[] positions = {PrecisionAssemblerGameTests.INPUT_0, PrecisionAssemblerGameTests.INPUT_1, INPUT_2};
            for (int index = 0; index < items.length; index++) {
                helper.assertTrue(handler(helper, positions[index]).insertItem(0, new ItemStack(items[index]), false).isEmpty(),
                        "Formed precision input rejected fixture");
            }
            IEnergyStorage energy = energy(helper);
            helper.assertTrue(energy.receiveEnergy(1000, false) == 1000 && energy.receiveEnergy(1000, false) == 1000
                    && energy.getEnergyStored() == 2000, "Formed machine did not receive exactly 2000 FE");
        });
        helper.runAtTickTime(14, () -> helper.assertTrue(PrecisionAssemblerGameTests.controller(helper).processState() == ProcessMachineState.RUNNING,
                "Board process did not start in the formed machine"));
        helper.runAtTickTime(225, () -> completed(helper));
        helper.runAtTickTime(245, () -> { completed(helper); helper.succeed(); });
    }

    private static void completed(GameTestHelper helper) {
        for (BlockPos pos : List.of(PrecisionAssemblerGameTests.INPUT_0, PrecisionAssemblerGameTests.INPUT_1, INPUT_2)) {
            helper.assertTrue(handler(helper, pos).getStackInSlot(0).isEmpty(), "Committed precision input was not consumed");
        }
        ItemStack result = handler(helper, PrecisionAssemblerGameTests.OUTPUT_0).getStackInSlot(0);
        helper.assertTrue(result.is(ModItems.CONTROL_BOARD.get()) && result.getCount() == 1 && !result.hasTag()
                && handler(helper, PrecisionAssemblerGameTests.OUTPUT_1).getStackInSlot(0).isEmpty(), "Physical board output duplicated/changed");
        helper.assertTrue(energy(helper).getEnergyStored() == 0
                && PrecisionAssemblerGameTests.controller(helper).processProgress().isEmpty(), "Physical precision energy/progress did not finish");
    }

    private static ShapedRecipe crafting(GameTestHelper helper) {
        var recipe = helper.getLevel().getRecipeManager().byKey(ITEM).orElseThrow();
        helper.assertTrue(recipe instanceof ShapedRecipe, "Loaded board crafting type changed");
        return (ShapedRecipe) recipe;
    }

    private static PrecisionAssemblerRecipe precision(GameTestHelper helper) {
        var recipe = helper.getLevel().getRecipeManager().byKey(PRECISION).orElseThrow();
        helper.assertTrue(recipe instanceof PrecisionAssemblerRecipe, "Loaded board precision type changed");
        return (PrecisionAssemblerRecipe) recipe;
    }

    private static TransientCraftingContainer grid() {
        var grid = new TransientCraftingContainer(new NoMenu(), 3, 3);
        Item copperDust = MaterialContent.item("copper_dust");
        Item[] items = {Items.REDSTONE, Items.QUARTZ, Items.REDSTONE, copperDust, Items.OAK_SLAB, copperDust,
                copperDust, MaterialContent.item("iron_plate"), copperDust};
        for (int slot = 0; slot < items.length; slot++) { grid.setItem(slot, new ItemStack(items[slot], slot + 2)); }
        return grid;
    }

    private static SimpleContainer precisionInputs() {
        return new SimpleContainer(new ItemStack(Items.REDSTONE), new ItemStack(MaterialContent.item("copper_plate")),
                new ItemStack(MaterialContent.item("steel_plate")), ItemStack.EMPTY, ItemStack.EMPTY);
    }

    private static void checkCraft(GameTestHelper helper, ShapedRecipe recipe, TransientCraftingContainer grid, boolean expected) {
        List<ItemStack> before = new ArrayList<>();
        for (int slot = 0; slot < grid.getContainerSize(); slot++) { before.add(grid.getItem(slot).copy()); }
        helper.assertTrue(recipe.matches(grid, helper.getLevel()) == expected, "Loaded board crafting match changed");
        var selected = helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, grid, helper.getLevel());
        if (expected) {
            helper.assertTrue(selected.isPresent() && selected.get().getId().equals(ITEM), "Crafting lookup selected another recipe");
            ItemStack result = recipe.assemble(grid, helper.getLevel().registryAccess());
            helper.assertTrue(result.is(ModItems.CONTROL_BOARD.get()) && result.getCount() == 1 && !result.hasTag(), "Crafting output changed");
            helper.assertTrue(recipe.getRemainingItems(grid).stream().allMatch(ItemStack::isEmpty), "Board gained a crafting remainder");
        } else {
            helper.assertTrue(selected.isEmpty() || !selected.get().getId().equals(ITEM), "Crafting lookup accepted an invalid grid");
        }
        for (int slot = 0; slot < before.size(); slot++) {
            helper.assertTrue(ItemStack.matches(before.get(slot), grid.getItem(slot)), "Query/assemble mutated crafting inputs");
        }
    }

    private static void membership(GameTestHelper helper, Item item, String id, boolean expected) {
        helper.assertTrue(new ItemStack(item).is(TagKey.create(Registries.ITEM, new ResourceLocation(id))) == expected,
                "Installed selector membership changed: " + id);
    }

    private static IItemHandler handler(GameTestHelper helper, BlockPos pos) {
        return ((PrecisionAssemblerPortBlockEntity) helper.getBlockEntity(pos)).getCapability(ForgeCapabilities.ITEM_HANDLER).resolve().orElseThrow();
    }

    private static IEnergyStorage energy(GameTestHelper helper) {
        return ((PrecisionAssemblerPortBlockEntity) helper.getBlockEntity(PrecisionAssemblerGameTests.ENERGY))
                .getCapability(ForgeCapabilities.ENERGY).resolve().orElseThrow();
    }

    private static final class NoMenu extends AbstractContainerMenu {
        NoMenu() { super(null, 0); }
        @Override public boolean stillValid(Player player) { return true; }
        @Override public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }
    }
}
