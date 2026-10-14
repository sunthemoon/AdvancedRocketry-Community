package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.machine.precision.PrecisionAssemblerPortBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.machine.precision.PrecisionAssemblerRecipe;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessMachineState;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModRecipes;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.registries.ForgeRegistries;

/** Real loaded recipes and physical machine execution; fixture inputs do not claim a player progression run. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TrackingCircuitGameTests {
    private static final String BATCH = "tracking_circuit";
    private static final List<BlockPos> INPUTS = List.of(PrecisionAssemblerGameTests.INPUT_0,
            PrecisionAssemblerGameTests.INPUT_1, new BlockPos(1, 1, 2));
    private static final int TOTAL_FE = 45_000;
    private TrackingCircuitGameTests() { }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 20)
    public static void plainItemKeepsItsStableIdentityAndNativeStackRoundTrip(GameTestHelper helper) {
        Item item = ModItems.TRACKING_CIRCUIT.get();
        helper.assertTrue(item.getClass() == Item.class && ForgeRegistries.ITEMS.getValue(
                ModIdentity.id("tracking_circuit")) == item, "Tracking circuit identity or behavior changed");
        ItemStack stack = new ItemStack(item, 64);
        CompoundTag saved = stack.save(new CompoundTag());
        helper.assertTrue(stack.getMaxStackSize() == 64 && !stack.hasTag() && !stack.isDamageableItem()
                && saved.getString("id").equals(ModIdentity.id("tracking_circuit").toString())
                && ItemStack.matches(stack, ItemStack.of(saved)), "Tracking circuit stack round trip changed");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 20)
    public static void loadedMachineRecipeMatchesExactlyItsOrderedChannels(GameTestHelper helper) {
        PrecisionAssemblerRecipe recipe = recipe(helper);
        helper.assertTrue(recipe.available() && recipe.processingTicks() == 900 && recipe.energyPerTick() == 50
                && recipe.processDefinition().totalEnergy() == TOTAL_FE && recipe.inputs().size() == 3
                && recipe.outputs().size() == 1, "Tracking recipe accounting changed");
        helper.assertTrue(recipe.matches(inputs(), helper.getLevel()), "Valid tracking inputs did not match");
        helper.assertTrue(helper.getLevel().getRecipeManager().getAllRecipesFor(ModRecipes.PRECISION_ASSEMBLING_TYPE.get())
                .stream().filter(candidate -> candidate.matches(inputs(), helper.getLevel())).count() == 1,
                "Tracking producer is ambiguous");
        ItemStack result = recipe.assemble(inputs(), helper.getLevel().registryAccess());
        helper.assertTrue(result.is(ModItems.TRACKING_CIRCUIT.get()) && result.getCount() == 1 && !result.hasTag(),
                "Loaded tracking output changed");
        for (int slot = 0; slot < 3; slot++) {
            var missing = inputs(); missing.setItem(slot, ItemStack.EMPTY);
            helper.assertTrue(!recipe.matches(missing, helper.getLevel()), "Missing channel matched");
            var tagged = inputs(); tagged.getItem(slot).getOrCreateTag().putInt("probe", 1);
            helper.assertTrue(!recipe.matches(tagged, helper.getLevel()), "Tagged channel matched");
            var wrong = inputs(); wrong.setItem(slot, new ItemStack(Items.COAL));
            helper.assertTrue(!recipe.matches(wrong, helper.getLevel()), "Wrong channel matched");
        }
        for (int slot = 3; slot < 5; slot++) {
            var extra = inputs(); extra.setItem(slot, new ItemStack(Items.COAL));
            helper.assertTrue(!recipe.matches(extra, helper.getLevel()), "Occupied trailing channel matched");
        }
        var swapped = inputs(); swapped.setItem(0, new ItemStack(Items.ENDER_EYE)); swapped.setItem(1, new ItemStack(Items.REDSTONE));
        helper.assertTrue(!recipe.matches(swapped, helper.getLevel()), "Swapped channels matched");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 980)
    public static void formedMachineConsumesOneBatchAndExactlyFortyFiveThousandFE(GameTestHelper helper) {
        PrecisionAssemblerGameTests.placeStructure(helper);
        int[] supplied = {0};
        helper.runAtTickTime(8, () -> {
            recipe(helper);
            SimpleContainer inputs = inputs();
            for (int slot = 0; slot < 3; slot++) {
                helper.assertTrue(handler(helper, INPUTS.get(slot)).insertItem(0, inputs.getItem(slot), false).isEmpty(),
                        "Tracking input fixture rejected");
            }
            helper.assertTrue(energy(helper).receiveEnergy(1000, false) == 1000
                    && energy(helper).receiveEnergy(1000, false) == 1000, "Finite starting FE buffer rejected");
            supplied[0] = 2000;
        });
        helper.onEachTick(() -> {
            if (supplied[0] > 0 && supplied[0] < TOTAL_FE) {
                int request = Math.min(50, TOTAL_FE - supplied[0]);
                helper.assertTrue(energy(helper).receiveEnergy(request, false) == request, "FE replenishment rejected");
                supplied[0] += request;
            }
        });
        helper.runAtTickTime(14, () -> helper.assertTrue(PrecisionAssemblerGameTests.controller(helper).processState()
                == ProcessMachineState.RUNNING, "Physical tracking process did not start"));
        helper.runAtTickTime(900, () -> helper.assertTrue(handler(helper, PrecisionAssemblerGameTests.OUTPUT_0)
                .getStackInSlot(0).isEmpty() && PrecisionAssemblerGameTests.controller(helper).processProgress()
                .orElseThrow().progressTicks() < 900, "Tracking circuit finished before 900 natural ticks"));
        helper.runAtTickTime(935, () -> completed(helper, supplied[0]));
        helper.runAtTickTime(955, () -> { completed(helper, supplied[0]); helper.succeed(); });
    }

    private static void completed(GameTestHelper helper, int supplied) {
        for (BlockPos input : INPUTS) { helper.assertTrue(handler(helper, input).getStackInSlot(0).isEmpty(), "Input not consumed"); }
        ItemStack output = handler(helper, PrecisionAssemblerGameTests.OUTPUT_0).getStackInSlot(0);
        helper.assertTrue(output.is(ModItems.TRACKING_CIRCUIT.get()) && output.getCount() == 1 && !output.hasTag()
                && handler(helper, PrecisionAssemblerGameTests.OUTPUT_1).getStackInSlot(0).isEmpty(), "Output changed or duplicated");
        helper.assertTrue(supplied == TOTAL_FE && energy(helper).getEnergyStored() == 0
                && PrecisionAssemblerGameTests.controller(helper).processProgress().isEmpty(), "FE/progress accounting changed");
    }

    private static PrecisionAssemblerRecipe recipe(GameTestHelper helper) {
        var recipe = helper.getLevel().getRecipeManager().byKey(ModIdentity.id("precision_tracking_circuit")).orElseThrow();
        helper.assertTrue(recipe instanceof PrecisionAssemblerRecipe, "Tracking recipe type changed");
        return (PrecisionAssemblerRecipe) recipe;
    }

    private static SimpleContainer inputs() {
        return new SimpleContainer(new ItemStack(Items.REDSTONE), new ItemStack(Items.ENDER_EYE),
                new ItemStack(ModItems.BASIC_CIRCUIT_PLATE.get()), ItemStack.EMPTY, ItemStack.EMPTY);
    }

    private static IItemHandler handler(GameTestHelper helper, BlockPos pos) {
        return ((PrecisionAssemblerPortBlockEntity) helper.getBlockEntity(pos)).getCapability(ForgeCapabilities.ITEM_HANDLER)
                .resolve().orElseThrow();
    }

    private static IEnergyStorage energy(GameTestHelper helper) {
        return ((PrecisionAssemblerPortBlockEntity) helper.getBlockEntity(PrecisionAssemblerGameTests.ENERGY))
                .getCapability(ForgeCapabilities.ENERGY).resolve().orElseThrow();
    }
}
