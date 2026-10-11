package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.machine.precision.PrecisionAssemblerPortBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.machine.precision.PrecisionAssemblerRecipe;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessMachineState;
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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.registries.ForgeRegistries;

/** Loaded precision producers with finite test-given FE; not a generator/survival witness. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CircuitPlateGameTests {
    private static final String BATCH = "circuit_plates";
    private static final int TOTAL_FE = 90000;
    private static final List<BlockPos> INPUTS = List.of(PrecisionAssemblerGameTests.INPUT_0,
            PrecisionAssemblerGameTests.INPUT_1, new BlockPos(1, 1, 2), new BlockPos(3, 1, 2), new BlockPos(1, 1, 3));
    private CircuitPlateGameTests() { }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 20)
    public static void plainItemsPreserveNativeIdentitiesCountsAndNoFuel(GameTestHelper helper) {
        for (boolean advanced : new boolean[] {false, true}) {
            ResourceLocation id = ModIdentity.id(advanced ? "advanced_circuit_plate" : "basic_circuit_plate");
            Item item = plate(advanced);
            helper.assertTrue(ForgeRegistries.ITEMS.containsKey(id) && ForgeRegistries.ITEMS.getValue(id) == item
                    && item.getClass() == Item.class, "Literal plain plate identity changed");
            for (int count : new int[] {1, 37, 64}) {
                ItemStack stack = new ItemStack(item, count);
                CompoundTag saved = stack.save(new CompoundTag());
                helper.assertTrue(stack.getMaxStackSize() == 64 && !stack.hasTag() && !stack.isDamageableItem()
                        && saved.getString("id").equals(id.toString()) && saved.getByte("Count") == count
                        && ItemStack.matches(stack, ItemStack.of(saved)), "Native plate ItemStack round trip changed");
            }
            helper.assertTrue(ForgeHooks.getBurnTime(new ItemStack(item), RecipeType.SMELTING) == 0, "Plate acquired built-in fuel");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 20)
    public static void loadedSelectorsResolveGoldAndRedstoneWithoutSubstitutingBlocks(GameTestHelper helper) {
        membership(helper, Items.GOLD_INGOT, "forge:ingots/gold", true);
        membership(helper, Items.GOLD_NUGGET, "forge:ingots/gold", false);
        membership(helper, Items.IRON_INGOT, "forge:ingots/gold", false);
        membership(helper, Items.REDSTONE, "forge:dusts/redstone", true);
        membership(helper, Items.REDSTONE_BLOCK, "forge:dusts/redstone", false);
        helper.succeed();
    }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 20)
    public static void loadedBasicProducerUsesUniqueExactChannels(GameTestHelper helper) {
        checkLoaded(helper, false); helper.succeed();
    }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 20)
    public static void loadedAdvancedProducerUsesUniqueExactChannels(GameTestHelper helper) {
        checkLoaded(helper, true); helper.succeed();
    }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 960)
    public static void formedBasicMachineConsumesOneBatchAndExactlyNinetyThousandFE(GameTestHelper helper) {
        formed(helper, false);
    }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 960)
    public static void formedAdvancedMachineConsumesOneBatchAndExactlyNinetyThousandFE(GameTestHelper helper) {
        formed(helper, true);
    }

    private static void checkLoaded(GameTestHelper helper, boolean advanced) {
        PrecisionAssemblerRecipe recipe = precision(helper, advanced);
        helper.assertTrue(recipe.available() && recipe.processingTicks() == 900 && recipe.energyPerTick() == 100
                && recipe.processDefinition().totalEnergy() == TOTAL_FE && recipe.inputs().size() == 3
                && recipe.inputs().stream().allMatch(input -> input.count() == 1)
                && recipe.outputs().size() == 1, "Loaded plate recipe accounting/availability changed");
        helper.assertTrue(recipe.ingredientAlternatives().stream().allMatch(values -> !values.isEmpty() && values.size() <= 32),
                "Installed plate selectors exceed their bounds");
        SimpleContainer inputs = inputs(advanced);
        List<ItemStack> before = new ArrayList<>();
        for (int slot = 0; slot < 5; slot++) { before.add(inputs.getItem(slot).copy()); }
        helper.assertTrue(recipe.matches(inputs, helper.getLevel()), "Valid plate inputs did not match");
        long matches = helper.getLevel().getRecipeManager().getAllRecipesFor(ModRecipes.PRECISION_ASSEMBLING_TYPE.get())
                .stream().filter(candidate -> candidate.matches(inputs, helper.getLevel())).count();
        helper.assertTrue(matches == 1 && helper.getLevel().getRecipeManager()
                .getRecipeFor(ModRecipes.PRECISION_ASSEMBLING_TYPE.get(), inputs, helper.getLevel())
                .orElseThrow().getId().equals(recipe.getId()), "Installed plate producer is ambiguous or selected incorrectly");
        ItemStack result = recipe.assemble(inputs, helper.getLevel().registryAccess());
        helper.assertTrue(result.is(plate(advanced)) && result.getCount() == 1 && !result.hasTag(), "Plate output changed");
        for (int slot = 0; slot < 5; slot++) {
            helper.assertTrue(ItemStack.matches(before.get(slot), inputs.getItem(slot)), "Plate query/assemble mutated inputs");
        }
        for (int slot = 0; slot < 3; slot++) {
            var missing = inputs(advanced); missing.setItem(slot, ItemStack.EMPTY);
            helper.assertTrue(!recipe.matches(missing, helper.getLevel()), "Missing plate channel matched");
            var tagged = inputs(advanced); tagged.getItem(slot).getOrCreateTag().putInt("probe", 1);
            helper.assertTrue(!recipe.matches(tagged, helper.getLevel()), "Tagged plate input matched");
            var wrong = inputs(advanced); wrong.setItem(slot, new ItemStack(Items.COAL));
            helper.assertTrue(!recipe.matches(wrong, helper.getLevel()), "Wrong plate channel matched");
        }
        for (int slot = 3; slot < 5; slot++) {
            var extra = inputs(advanced); extra.setItem(slot, new ItemStack(Items.COAL));
            helper.assertTrue(!recipe.matches(extra, helper.getLevel()), "Occupied trailing plate channel matched");
        }
        var swapped = inputs(advanced);
        ItemStack first = swapped.getItem(0); swapped.setItem(0, swapped.getItem(1)); swapped.setItem(1, first);
        helper.assertTrue(!recipe.matches(swapped, helper.getLevel()) && !recipe.matches(inputs(!advanced), helper.getLevel()),
                "Reordered or opposite plate producer matched");
    }

    private static void formed(GameTestHelper helper, boolean advanced) {
        PrecisionAssemblerGameTests.placeStructure(helper);
        int[] supplied = {0};
        boolean[] started = {false};
        helper.runAtTickTime(8, () -> {
            precision(helper, advanced);
            SimpleContainer inputs = inputs(advanced);
            for (int slot = 0; slot < 3; slot++) {
                helper.assertTrue(handler(helper, INPUTS.get(slot)).insertItem(0, inputs.getItem(slot), false).isEmpty(),
                        "Formed plate input rejected fixture");
            }
            IEnergyStorage energy = energy(helper);
            helper.assertTrue(energy.receiveEnergy(1000, false) == 1000 && energy.receiveEnergy(1000, false) == 1000
                    && energy.getEnergyStored() == 2000, "Plate machine rejected the finite starting buffer");
            supplied[0] = 2000; started[0] = true;
        });
        helper.onEachTick(() -> {
            if (started[0] && supplied[0] < TOTAL_FE) {
                int requested = Math.min(100, TOTAL_FE - supplied[0]);
                int accepted = energy(helper).receiveEnergy(requested, false);
                helper.assertTrue(accepted == requested, "Plate FE replenishment changed");
                supplied[0] = Math.addExact(supplied[0], accepted);
            }
        });
        helper.runAtTickTime(14, () -> helper.assertTrue(PrecisionAssemblerGameTests.controller(helper).processState() == ProcessMachineState.RUNNING,
                "Plate process did not start naturally"));
        helper.runAtTickTime(900, () -> helper.assertTrue(
                handler(helper, PrecisionAssemblerGameTests.OUTPUT_0).getStackInSlot(0).isEmpty()
                && PrecisionAssemblerGameTests.controller(helper).processProgress().orElseThrow().progressTicks() < 900,
                "Plate machine completed before 900 natural processing ticks"));
        helper.runAtTickTime(935, () -> completed(helper, advanced, supplied[0]));
        helper.runAtTickTime(955, () -> { completed(helper, advanced, supplied[0]); helper.succeed(); });
    }

    private static void completed(GameTestHelper helper, boolean advanced, int supplied) {
        for (BlockPos position : INPUTS) {
            helper.assertTrue(handler(helper, position).getStackInSlot(0).isEmpty(), "Plate input channel was not consumed or empty");
        }
        ItemStack result = handler(helper, PrecisionAssemblerGameTests.OUTPUT_0).getStackInSlot(0);
        helper.assertTrue(result.is(plate(advanced)) && result.getCount() == 1 && !result.hasTag()
                && handler(helper, PrecisionAssemblerGameTests.OUTPUT_1).getStackInSlot(0).isEmpty(), "Physical plate output changed/duplicated");
        helper.assertTrue(supplied == TOTAL_FE && energy(helper).getEnergyStored() == 0
                && PrecisionAssemblerGameTests.controller(helper).processProgress().isEmpty(), "Plate FE/progress accounting did not finish exactly");
    }

    private static PrecisionAssemblerRecipe precision(GameTestHelper helper, boolean advanced) {
        var recipe = helper.getLevel().getRecipeManager().byKey(ModIdentity.id(
                advanced ? "precision_advanced_circuit_plate" : "precision_basic_circuit_plate")).orElseThrow();
        helper.assertTrue(recipe instanceof PrecisionAssemblerRecipe, "Loaded plate producer type changed");
        return (PrecisionAssemblerRecipe) recipe;
    }

    private static SimpleContainer inputs(boolean advanced) {
        return new SimpleContainer(new ItemStack(Items.GOLD_INGOT), new ItemStack(advanced ? Items.REDSTONE_BLOCK : Items.REDSTONE),
                new ItemStack(ModItems.SILICON_WAFER.get()), ItemStack.EMPTY, ItemStack.EMPTY);
    }

    private static Item plate(boolean advanced) { return advanced ? ModItems.ADVANCED_CIRCUIT_PLATE.get() : ModItems.BASIC_CIRCUIT_PLATE.get(); }

    private static void membership(GameTestHelper helper, Item item, String id, boolean expected) {
        helper.assertTrue(new ItemStack(item).is(TagKey.create(Registries.ITEM, new ResourceLocation(id))) == expected,
                "Installed plate selector membership changed: " + id);
    }

    private static IItemHandler handler(GameTestHelper helper, BlockPos position) {
        return ((PrecisionAssemblerPortBlockEntity) helper.getBlockEntity(position))
                .getCapability(ForgeCapabilities.ITEM_HANDLER).resolve().orElseThrow();
    }

    private static IEnergyStorage energy(GameTestHelper helper) {
        return ((PrecisionAssemblerPortBlockEntity) helper.getBlockEntity(PrecisionAssemblerGameTests.ENERGY))
                .getCapability(ForgeCapabilities.ENERGY).resolve().orElseThrow();
    }
}
