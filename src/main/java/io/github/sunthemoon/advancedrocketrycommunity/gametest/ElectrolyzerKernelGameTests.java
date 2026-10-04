package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.machine.electrolyzer.ElectrolyzerBlock;
import io.github.sunthemoon.advancedrocketrycommunity.machine.electrolyzer.ElectrolyzerBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.machine.electrolyzer.ElectrolyzerMenu;
import io.github.sunthemoon.advancedrocketrycommunity.machine.electrolyzer.ElectrolyzerRecipe;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessMachineLogic;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessResourceBalance;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessResourceKey;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessResourceKind;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessResourceSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessTransactionJournal;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.persistence.ProcessJournalPersistence;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlockEntities;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModMenuTypes;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModRecipes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.registries.ForgeRegistries;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

/** v1.2 compatibility checks kept separate from the accepted v0.2 Electrolyzer tests. */
@GameTestHolder(AdvancedRocketryCommunity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ElectrolyzerKernelGameTests {
    private static final ResourceLocation MACHINE_ID = ModIdentity.id("electrolyzer");
    private static final ResourceLocation RECIPE_TYPE_ID = ModIdentity.id("electrolyzing");
    private static final ResourceLocation RECIPE_ID = ModIdentity.id("electrolyzer_water");

    private ElectrolyzerKernelGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void v120Elec001PublicIdsAndLiveRecipeAdapterRemainStable(GameTestHelper helper) {
        helper.assertTrue(MACHINE_ID.equals(ForgeRegistries.BLOCKS.getKey(ModBlocks.ELECTROLYZER.get())),
                "Electrolyzer block ID changed");
        helper.assertTrue(MACHINE_ID.equals(ForgeRegistries.ITEMS.getKey(ModItems.ELECTROLYZER.get())),
                "Electrolyzer item ID changed");
        helper.assertTrue(MACHINE_ID.equals(ForgeRegistries.BLOCK_ENTITY_TYPES.getKey(
                        ModBlockEntities.ELECTROLYZER.get())),
                "Electrolyzer BlockEntity ID changed");
        helper.assertTrue(MACHINE_ID.equals(ForgeRegistries.MENU_TYPES.getKey(ModMenuTypes.ELECTROLYZER.get())),
                "Electrolyzer menu ID changed");
        helper.assertTrue(RECIPE_TYPE_ID.equals(ForgeRegistries.RECIPE_TYPES.getKey(
                        ModRecipes.ELECTROLYZING_TYPE.get())),
                "Electrolyzer recipe type ID changed");
        helper.assertTrue(RECIPE_TYPE_ID.equals(ForgeRegistries.RECIPE_SERIALIZERS.getKey(
                        ModRecipes.ELECTROLYZING_SERIALIZER.get())),
                "Electrolyzer recipe serializer ID changed");

        Object loaded = helper.getLevel().getRecipeManager().byKey(RECIPE_ID).orElse(null);
        helper.assertTrue(loaded instanceof ElectrolyzerRecipe, "Live Electrolyzer recipe is missing");
        ElectrolyzerRecipe recipe = (ElectrolyzerRecipe) loaded;
        ProcessDefinition process = recipe.processDefinition();
        helper.assertTrue(RECIPE_ID.toString().equals(process.id()), "Process definition ID changed");
        helper.assertTrue(process.schemaVersion() == ProcessDefinition.SCHEMA_VERSION,
                "Process definition schema changed");
        helper.assertTrue(process.durationTicks() == 100 && process.energyPerTick() == 20,
                "Process timing or FE/t changed");
        helper.assertTrue(process.totalEnergy() == 2_000, "Process energy total changed");
        helper.assertTrue(process.inputs().size() == 2 && process.outputs().size() == 2,
                "Process resource arity changed");
        helper.assertTrue(process.inputs().get(0).kind() == ProcessResourceKind.ITEM
                        && process.inputs().get(0).amount() == 2,
                "Process canister input changed");
        helper.assertTrue(process.inputs().get(1).kind() == ProcessResourceKind.FLUID
                        && process.inputs().get(1).amount() == 1_000,
                "Process water input changed");
        helper.assertTrue(recipe.signature().matches("[0-9a-f]{64}"), "Recipe signature is not canonical SHA-256");
        boolean wrongOutputRejected = false;
        try {
            new ElectrolyzerRecipe(
                    RECIPE_ID,
                    recipe.ingredient(),
                    recipe.fluid(),
                    new ItemStack(Items.IRON_INGOT),
                    recipe.oxygenResult(),
                    recipe.spec()
            );
        } catch (IllegalArgumentException expected) {
            wrongOutputRejected = true;
        }
        helper.assertTrue(wrongOutputRejected, "A recipe could target an incompatible stable output slot");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void v120Elec004InvalidatedCapabilityViewsRemainMutationInert(GameTestHelper helper) {
        ElectrolyzerBlockEntity machine = placeMachine(helper, BlockPos.ZERO);
        IItemHandler retained = machine.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.NORTH)
                .orElseThrow(() -> new IllegalStateException("Side item capability is missing"));

        machine.invalidateCaps();
        helper.assertFalse(machine.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.NORTH).isPresent(),
                "Invalidated capability remained discoverable");
        helper.assertTrue(retained.insertItem(0, new ItemStack(Items.REDSTONE), false).getCount() == 1,
                "Retained invalidated capability mutated storage");

        machine.reviveCaps();
        helper.assertTrue(retained.insertItem(0, new ItemStack(Items.REDSTONE), false).getCount() == 1,
                "Old capability revived after a new epoch");
        IItemHandler fresh = machine.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.NORTH)
                .orElseThrow(() -> new IllegalStateException("Revived side item capability is missing"));
        helper.assertTrue(fresh.insertItem(0, new ItemStack(Items.REDSTONE), false).isEmpty(),
                "Fresh capability could not mutate storage");
        helper.assertTrue(machine.menuInventory().getStackInSlot(ElectrolyzerBlockEntity.SLOT_CHARGE)
                        .is(Items.REDSTONE),
                "Fresh capability wrote the wrong resource");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void lockedInputRemainsAuthoritativeAcrossMenuMutationPaths(GameTestHelper helper) {
        ElectrolyzerBlockEntity machine = placeMachine(helper, BlockPos.ZERO);
        IItemHandler input = machine.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP)
                .orElseThrow(() -> new IllegalStateException("Top item capability is missing"));
        helper.assertTrue(input.insertItem(
                0,
                new ItemStack(ModItems.EMPTY_CANISTER.get()),
                false
        ).isEmpty(), "Could not seed locked-input fixture");
        Player player = helper.makeMockPlayer();
        ElectrolyzerMenu menu = (ElectrolyzerMenu) machine.createMenu(1, player.getInventory(), player);
        helper.assertTrue(menu != null, "Electrolyzer menu construction failed");
        player.getInventory().setItem(9, new ItemStack(ModItems.EMPTY_CANISTER.get()));
        helper.assertTrue(menu.quickMoveStack(player, ElectrolyzerBlockEntity.SLOT_COUNT)
                        .is(ModItems.EMPTY_CANISTER.get()),
                "Menu could not merge a canister into occupied input");
        helper.assertTrue(machine.menuInventory()
                        .getStackInSlot(ElectrolyzerBlockEntity.SLOT_INPUT).getCount() == 2,
                "Menu merge lost or duplicated an input canister");
        helper.assertTrue(player.getInventory().getItem(9).isEmpty(),
                "Menu merge did not consume the player source stack");
        helper.assertTrue(machine.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.NORTH)
                        .orElseThrow(() -> new IllegalStateException("Side fluid capability is missing"))
                        .fill(new FluidStack(Fluids.WATER, 1_000), IFluidHandler.FluidAction.EXECUTE) == 1_000,
                "Could not seed fixture water");
        IEnergyStorage energy = machine.getCapability(ForgeCapabilities.ENERGY, Direction.NORTH)
                .orElseThrow(() -> new IllegalStateException("Side energy capability is missing"));
        helper.assertTrue(energy.receiveEnergy(1_000, false) == 1_000
                        && energy.receiveEnergy(1_000, false) == 1_000,
                "Could not seed fixture energy");

        helper.runAtTickTime(5, () -> {
            helper.assertTrue(machine.progress() > 0, "Fixture did not enter an input-locked process");
            helper.assertTrue(menu.quickMoveStack(player, ElectrolyzerBlockEntity.SLOT_INPUT).isEmpty(),
                    "Menu quick-move extracted locked input");
            helper.assertFalse(player.getInventory().contains(new ItemStack(ModItems.EMPTY_CANISTER.get())),
                    "Locked input was copied into the player inventory");

            menu.getSlot(ElectrolyzerBlockEntity.SLOT_INPUT).set(ItemStack.EMPTY);
            helper.assertTrue(machine.menuInventory()
                            .getStackInSlot(ElectrolyzerBlockEntity.SLOT_INPUT).getCount() == 2,
                    "Direct menu replacement cleared locked input");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void unsignedLegacyStateStaysVerbatimAndLockedWithoutResourceEffects(GameTestHelper helper) {
        ElectrolyzerBlockEntity source = placeMachine(helper, BlockPos.ZERO);
        IItemHandler input = source.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP)
                .orElseThrow(() -> new IllegalStateException("Top item capability is missing"));
        helper.assertTrue(input.insertItem(
                0,
                new ItemStack(ModItems.EMPTY_CANISTER.get(), 2),
                false
        ).isEmpty(), "Could not seed legacy canisters");
        IFluidHandler fluid = source.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.NORTH)
                .orElseThrow(() -> new IllegalStateException("Side fluid capability is missing"));
        helper.assertTrue(fluid.fill(
                new FluidStack(Fluids.WATER, 1_000),
                IFluidHandler.FluidAction.EXECUTE
        ) == 1_000, "Could not seed legacy water");
        IEnergyStorage energy = source.getCapability(ForgeCapabilities.ENERGY, Direction.NORTH)
                .orElseThrow(() -> new IllegalStateException("Side energy capability is missing"));
        helper.assertTrue(energy.receiveEnergy(1_000, false) == 1_000
                        && energy.receiveEnergy(1_000, false) == 1_000,
                "Could not seed legacy energy");

        CompoundTag legacyParent = source.saveWithoutMetadata();
        legacyParent.remove("arce_process");
        legacyParent.remove("arce_process_journal");
        legacyParent.remove("arce_recipe_signature");
        CompoundTag legacyRoot = legacyParent.getCompound("arce_machine");
        legacyRoot.putInt("progress", 40);
        legacyRoot.putString("active_recipe", RECIPE_ID.toString());
        CompoundTag legacyBefore = legacyRoot.copy();

        ElectrolyzerBlockEntity first = new ElectrolyzerBlockEntity(source.getBlockPos(), source.getBlockState());
        first.load(legacyParent);
        helper.assertTrue(first.progress() == 40, "Migration changed legacy progress");
        assertResourcesUnchanged(helper, first);
        helper.assertTrue(first.recipeLookupCount() == 0, "Migration selected a recipe");
        CompoundTag firstSave = first.saveWithoutMetadata();
        helper.assertTrue(legacyBefore.equals(firstSave.getCompound("arce_machine")),
                "Migration rewrote the accepted schema-1 root");
        helper.assertFalse(firstSave.contains("arce_process"),
                "Unsigned work was reinterpreted as verified process progress");
        helper.assertFalse(firstSave.contains("arce_recipe_signature"),
                "Unsigned work received a verified signature marker");
        helper.assertFalse(firstSave.contains("arce_process_journal"),
                "Migration executed a resource transaction");
        ElectrolyzerBlockEntity second = new ElectrolyzerBlockEntity(source.getBlockPos(), source.getBlockState());
        second.load(firstSave);
        CompoundTag secondSave = second.saveWithoutMetadata();
        helper.assertFalse(secondSave.contains("arce_process") || secondSave.contains("arce_recipe_signature"),
                "A second load invented signature proof");
        helper.assertTrue(legacyBefore.equals(secondSave.getCompound("arce_machine")),
                "A second load changed the legacy compatibility root");
        assertResourcesUnchanged(helper, second);
        helper.assertTrue(second.recipeLookupCount() == 0, "A second load selected a recipe");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void journalWithoutMatchingCompletedProgressFailsClosed(GameTestHelper helper) {
        ElectrolyzerBlockEntity machine = placeMachine(helper, BlockPos.ZERO);
        IItemHandler input = machine.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP)
                .orElseThrow(() -> new IllegalStateException("Top item capability is missing"));
        input.insertItem(0, new ItemStack(ModItems.EMPTY_CANISTER.get(), 2), false);
        machine.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.NORTH)
                .orElseThrow(() -> new IllegalStateException("Side fluid capability is missing"))
                .fill(new FluidStack(Fluids.WATER, 1_000), IFluidHandler.FluidAction.EXECUTE);
        IEnergyStorage energy = machine.getCapability(ForgeCapabilities.ENERGY, Direction.NORTH)
                .orElseThrow(() -> new IllegalStateException("Side energy capability is missing"));
        energy.receiveEnergy(1_000, false);
        energy.receiveEnergy(1_000, false);

        ElectrolyzerRecipe recipe = (ElectrolyzerRecipe) helper.getLevel().getRecipeManager()
                .byKey(RECIPE_ID).orElseThrow();
        CompoundTag persisted = machine.saveWithoutMetadata();
        long revision = persisted.getCompound("arce_process").getLong("resource_revision");
        ProcessResourceSnapshot before = new ProcessResourceSnapshot(revision, Map.of(
                new ProcessResourceKey(ProcessResourceKind.ITEM, "item_input",
                        "advancedrocketrycommunity:empty_canister"),
                new ProcessResourceBalance(2, 64),
                new ProcessResourceKey(ProcessResourceKind.FLUID, "fluid_input", "minecraft:water"),
                new ProcessResourceBalance(1_000, 4_000),
                new ProcessResourceKey(ProcessResourceKind.ITEM, "item_output",
                        "advancedrocketrycommunity:hydrogen_canister"),
                new ProcessResourceBalance(0, 64),
                new ProcessResourceKey(ProcessResourceKind.ITEM, "item_output",
                        "advancedrocketrycommunity:oxygen_canister"),
                new ProcessResourceBalance(0, 64)
        ));
        ProcessTransactionJournal forged = ProcessTransactionJournal.prepared(
                UUID.randomUUID(),
                machineId(helper, machine.getBlockPos()),
                ProcessMachineLogic.simulate(recipe.processDefinition(), before).plan().orElseThrow()
        );
        persisted.put(ProcessJournalPersistence.ROOT, ProcessJournalPersistence.encode(forged));
        new io.github.sunthemoon.advancedrocketrycommunity.machine.recipe.RecipeSignatureMigration()
                .save(persisted, recipe.getId().toString());
        machine.load(persisted);

        IItemHandler charge = machine.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.NORTH)
                .orElseThrow(() -> new IllegalStateException("Side item capability is missing"));
        helper.assertTrue(charge.insertItem(0, new ItemStack(Items.REDSTONE), false).getCount() == 1,
                "Recovery-required machine accepted an external item mutation");
        IEnergyStorage blockedEnergy = machine.getCapability(ForgeCapabilities.ENERGY, Direction.NORTH)
                .orElseThrow(() -> new IllegalStateException("Side energy capability is missing"));
        helper.assertTrue(blockedEnergy.receiveEnergy(1, false) == 0,
                "Recovery-required machine accepted an external energy mutation");

        helper.runAtTickTime(2, () -> {
            helper.assertTrue(machine.status()
                            == io.github.sunthemoon.advancedrocketrycommunity.machine.electrolyzer.ElectrolyzerStatus.INVALID_RECIPE,
                    "Orphan journal did not enter recovery-required display state");
            assertResourcesUnchanged(helper, machine);
            helper.assertTrue(machine.saveWithoutMetadata().contains(ProcessJournalPersistence.ROOT),
                    "Orphan journal was cleared or applied");
            helper.succeed();
        });
    }

    private static ElectrolyzerBlockEntity placeMachine(GameTestHelper helper, BlockPos position) {
        BlockState state = ModBlocks.ELECTROLYZER.get().defaultBlockState()
                .setValue(ElectrolyzerBlock.FACING, Direction.NORTH)
                .setValue(ElectrolyzerBlock.POWERED, false);
        helper.setBlock(position, state);
        return (ElectrolyzerBlockEntity) helper.getBlockEntity(position);
    }

    private static void assertResourcesUnchanged(GameTestHelper helper, ElectrolyzerBlockEntity machine) {
        helper.assertTrue(machine.menuInventory().getStackInSlot(ElectrolyzerBlockEntity.SLOT_INPUT).getCount() == 2,
                "Migration changed input canisters");
        helper.assertTrue(machine.menuInventory().getStackInSlot(ElectrolyzerBlockEntity.SLOT_HYDROGEN).isEmpty()
                        && machine.menuInventory().getStackInSlot(ElectrolyzerBlockEntity.SLOT_OXYGEN).isEmpty(),
                "Migration produced output");
        helper.assertTrue(machine.waterAmount() == 1_000, "Migration changed water");
        helper.assertTrue(machine.energyStored() == 2_000, "Migration changed energy");
    }

    private static UUID machineId(GameTestHelper helper, BlockPos position) {
        String identity = AdvancedRocketryCommunity.MOD_ID
                + ":electrolyzer|"
                + helper.getLevel().dimension().location()
                + "|"
                + position.asLong();
        return UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8));
    }
}
