package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.sunthemoon.advancedrocketrycommunity.machine.electrolyzer.ElectrolyzerBlock;
import io.github.sunthemoon.advancedrocketrycommunity.machine.electrolyzer.ElectrolyzerBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.machine.electrolyzer.ElectrolyzerMenu;
import io.github.sunthemoon.advancedrocketrycommunity.machine.electrolyzer.ElectrolyzerRecipe;
import io.github.sunthemoon.advancedrocketrycommunity.machine.electrolyzer.ElectrolyzerStatus;
import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.content.MachineCasingBlock;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlockEntities;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModCreativeTabs;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModMenuTypes;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModRecipes;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModSounds;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;

@GameTestHolder(AdvancedRocketryCommunity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BootstrapGameTests {
    private BootstrapGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void approvedIdentityIsAvailable(GameTestHelper helper) {
        helper.assertTrue(
                AdvancedRocketryCommunity.MOD_ID.equals(ModIdentity.MOD_ID),
                "The Forge entrypoint and approved project identity must use the same mod id"
        );
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void contentRegistriesContainExpectedEntries(GameTestHelper helper) {
        helper.assertTrue(ModBlocks.MACHINE_CASING.isPresent(), "Machine casing block is not registered");
        helper.assertTrue(ModItems.MACHINE_CASING.isPresent(), "Machine casing item is not registered");
        helper.assertTrue(ModItems.SILICON_WAFER.isPresent(), "Silicon wafer is not registered");
        helper.assertTrue(ModItems.BASIC_CIRCUIT.isPresent(), "Basic circuit is not registered");
        helper.assertTrue(ModItems.ADVANCED_CIRCUIT.isPresent(), "Advanced circuit is not registered");
        helper.assertTrue(ModItems.DATA_STORAGE_UNIT.isPresent(), "Data storage unit is not registered");
        helper.assertTrue(ModSounds.UI_SELECT.isPresent(), "UI select sound is not registered");
        helper.assertTrue(ModCreativeTabs.MAIN.isPresent(), "Creative tab is not registered");
        helper.assertTrue(ModBlocks.ELECTROLYZER.isPresent(), "Electrolyzer block is not registered");
        helper.assertTrue(ModItems.ELECTROLYZER.isPresent(), "Electrolyzer item is not registered");
        helper.assertTrue(ModItems.EMPTY_CANISTER.isPresent(), "Empty canister is not registered");
        helper.assertTrue(ModItems.HYDROGEN_CANISTER.isPresent(), "Hydrogen canister is not registered");
        helper.assertTrue(ModItems.OXYGEN_CANISTER.isPresent(), "Oxygen canister is not registered");
        helper.assertTrue(ModBlockEntities.ELECTROLYZER.isPresent(), "Electrolyzer BlockEntity is not registered");
        helper.assertTrue(ModMenuTypes.ELECTROLYZER.isPresent(), "Electrolyzer menu is not registered");
        helper.assertTrue(ModRecipes.ELECTROLYZING_TYPE.isPresent(), "Electrolyzer recipe type is not registered");
        helper.assertTrue(
                ModRecipes.ELECTROLYZING_SERIALIZER.isPresent(),
                "Electrolyzer recipe serializer is not registered"
        );
        helper.assertTrue(
                ModIdentity.id("machine_casing").equals(
                        ForgeRegistries.BLOCKS.getKey(ModBlocks.MACHINE_CASING.get())
                ),
                "Machine casing block has an unexpected registry key"
        );
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void machineCasingPlacementAndDropAreValid(GameTestHelper helper) {
        BlockPos position = BlockPos.ZERO;
        helper.setBlock(
                position,
                ModBlocks.MACHINE_CASING.get().defaultBlockState()
                        .setValue(MachineCasingBlock.FACING, Direction.EAST)
        );
        helper.assertBlockPresent(ModBlocks.MACHINE_CASING.get(), position);
        helper.assertBlockProperty(position, MachineCasingBlock.FACING, Direction.EAST);
        helper.assertTrue(
                helper.getLevel().destroyBlock(helper.absolutePos(position), true),
                "Machine casing could not be destroyed with block drops enabled"
        );
        helper.succeedWhen(() -> helper.assertItemEntityPresent(
                ModItems.MACHINE_CASING.get(),
                position,
                1.0D
        ));
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void electrolyzerRecipeJsonAndNetworkRoundTripAreBounded(GameTestHelper helper) {
        ResourceLocation id = ModIdentity.id("serializer_fixture");
        JsonObject json = JsonParser.parseString("""
                {
                  "schema_version": 1,
                  "ingredient": {"item": "advancedrocketrycommunity:empty_canister"},
                  "input_count": 2,
                  "fluid": {"fluid": "minecraft:water", "amount": 1000},
                  "processing_time": 100,
                  "energy_per_tick": 20,
                  "hydrogen_result": {"item": "advancedrocketrycommunity:hydrogen_canister"},
                  "oxygen_result": {"item": "advancedrocketrycommunity:oxygen_canister"}
                }
                """).getAsJsonObject();
        ElectrolyzerRecipe decoded = ModRecipes.ELECTROLYZING_SERIALIZER.get().fromJson(id, json);
        helper.assertTrue(decoded.spec().totalEnergy() == 2_000, "Valid recipe decoded the wrong energy total");

        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            ModRecipes.ELECTROLYZING_SERIALIZER.get().toNetwork(buffer, decoded);
            ElectrolyzerRecipe roundTripped = ModRecipes.ELECTROLYZING_SERIALIZER.get().fromNetwork(id, buffer);
            helper.assertTrue(roundTripped.spec().equals(decoded.spec()), "Network recipe changed bounded values");
            helper.assertTrue(
                    roundTripped.hydrogenResult().is(ModItems.HYDROGEN_CANISTER.get()),
                    "Network recipe changed the hydrogen output"
            );
            helper.assertTrue(
                    roundTripped.oxygenResult().is(ModItems.OXYGEN_CANISTER.get()),
                    "Network recipe changed the oxygen output"
            );
        } finally {
            buffer.release();
        }

        JsonObject invalid = json.deepCopy();
        invalid.addProperty("processing_time", 1_200);
        invalid.addProperty("energy_per_tick", 1_000);
        boolean rejected = false;
        try {
            ModRecipes.ELECTROLYZING_SERIALIZER.get().fromJson(id, invalid);
        } catch (RuntimeException expected) {
            rejected = true;
        }
        helper.assertTrue(rejected, "Over-capacity recipe JSON was not rejected");

        FriendlyByteBuf oversizedNetwork = new FriendlyByteBuf(Unpooled.buffer());
        try {
            oversizedNetwork.writeVarInt(8_193);
            oversizedNetwork.writeZero(8_193);
            boolean oversizedRejected = false;
            try {
                ModRecipes.ELECTROLYZING_SERIALIZER.get().fromNetwork(id, oversizedNetwork);
            } catch (RuntimeException expected) {
                oversizedRejected = true;
            }
            helper.assertTrue(oversizedRejected, "Oversized network ingredient was not rejected before decoding");
        } finally {
            oversizedNetwork.release();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 140)
    public static void electrolyzerProducesExactAtomicBatchAndLocksInputs(GameTestHelper helper) {
        BlockPos position = BlockPos.ZERO;
        ElectrolyzerBlockEntity machine = placeMachine(helper, position, false);
        insertCycleInputs(machine, 2_000);

        helper.runAtTickTime(5, () -> {
            helper.assertTrue(machine.progress() > 0, "Electrolyzer did not start processing");
            IItemHandler top = itemHandler(machine, Direction.UP);
            helper.assertTrue(
                    top.extractItem(0, 1, false).isEmpty(),
                    "Top automation extracted locked input during processing"
            );
            helper.assertTrue(machine.waterAmount() == 1_000, "Water was consumed before atomic completion");
            helper.assertTrue(
                    itemHandler(machine, Direction.DOWN).getStackInSlot(0).isEmpty(),
                    "Hydrogen appeared before atomic completion"
            );
        });
        helper.runAtTickTime(105, () -> {
            IItemHandler outputs = itemHandler(machine, Direction.DOWN);
            helper.assertTrue(
                    outputs.getStackInSlot(0).is(ModItems.HYDROGEN_CANISTER.get())
                            && outputs.getStackInSlot(0).getCount() == 1,
                    "Electrolyzer did not produce exactly one hydrogen canister"
            );
            helper.assertTrue(
                    outputs.getStackInSlot(1).is(ModItems.OXYGEN_CANISTER.get())
                            && outputs.getStackInSlot(1).getCount() == 1,
                    "Electrolyzer did not produce exactly one oxygen canister"
            );
            helper.assertTrue(itemHandler(machine, Direction.UP).getStackInSlot(0).isEmpty(), "Input was not consumed");
            helper.assertTrue(machine.waterAmount() == 0, "Cycle did not consume exactly 1000 mB water");
            helper.assertTrue(machine.energyStored() == 0, "Cycle did not consume exactly 2000 FE");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 140)
    public static void electrolyzerRedstonePausePreservesAndResumesProgress(GameTestHelper helper) {
        BlockPos position = BlockPos.ZERO;
        ElectrolyzerBlockEntity machine = placeMachine(helper, position, true);
        insertCycleInputs(machine, 2_000);

        helper.runAtTickTime(10, () -> {
            helper.assertTrue(machine.progress() == 0, "Powered Electrolyzer advanced progress");
            helper.assertTrue(machine.energyStored() == 2_000, "Powered Electrolyzer consumed energy");
            helper.assertTrue(machine.waterAmount() == 1_000, "Powered Electrolyzer consumed water");
            helper.assertTrue(
                    machine.status() == ElectrolyzerStatus.REDSTONE_DISABLED,
                    "Powered Electrolyzer did not expose its pause reason"
            );
            BlockState enabled = helper.getBlockState(position).setValue(ElectrolyzerBlock.POWERED, false);
            helper.getLevel().setBlock(helper.absolutePos(position), enabled, Block.UPDATE_CLIENTS);
        });
        helper.runAtTickTime(115, () -> {
            helper.assertTrue(
                    itemHandler(machine, Direction.DOWN).getStackInSlot(0).is(ModItems.HYDROGEN_CANISTER.get()),
                    "Electrolyzer did not resume after redstone was removed"
            );
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void electrolyzerFailureStatesPreserveMaterials(GameTestHelper helper) {
        BlockPos noEnergyPosition = BlockPos.ZERO;
        ElectrolyzerBlockEntity noEnergy = placeMachine(helper, noEnergyPosition, false);
        insertMaterials(noEnergy);

        BlockPos blockedPosition = new BlockPos(1, 0, 0);
        ElectrolyzerBlockEntity blocked = placeMachine(helper, blockedPosition, false);
        loadOutputStack(blocked, new ItemStack(ModItems.HYDROGEN_CANISTER.get(), 16));
        insertCycleInputs(blocked, 2_000);

        helper.runAtTickTime(20, () -> {
            helper.assertTrue(noEnergy.status() == ElectrolyzerStatus.NEEDS_ENERGY, "Missing-energy status was not exposed");
            helper.assertTrue(noEnergy.progress() == 0, "No-energy machine advanced progress");
            helper.assertTrue(noEnergy.waterAmount() == 1_000, "No-energy machine consumed water");
            helper.assertTrue(
                    itemHandler(noEnergy, Direction.UP).getStackInSlot(0).getCount() == 2,
                    "No-energy machine consumed input"
            );
            helper.assertTrue(blocked.status() == ElectrolyzerStatus.OUTPUT_BLOCKED, "Blocked-output status was not exposed");
            helper.assertTrue(blocked.progress() == 0, "Blocked-output machine advanced progress");
            helper.assertTrue(blocked.energyStored() == 2_000, "Blocked-output machine consumed energy");
            helper.assertTrue(blocked.waterAmount() == 1_000, "Blocked-output machine consumed water");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void electrolyzerCapabilitySidesAreStableAndFillOnly(GameTestHelper helper) {
        ElectrolyzerBlockEntity machine = placeMachine(helper, BlockPos.ZERO, false);
        helper.assertTrue(machine.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP).isPresent(), "Top item cap missing");
        helper.assertFalse(machine.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.UP).isPresent(), "Top fluid cap exposed");
        helper.assertFalse(machine.getCapability(ForgeCapabilities.ENERGY, Direction.UP).isPresent(), "Top energy cap exposed");
        helper.assertTrue(machine.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.NORTH).isPresent(), "Side item cap missing");
        helper.assertTrue(machine.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.NORTH).isPresent(), "Side fluid cap missing");
        helper.assertTrue(machine.getCapability(ForgeCapabilities.ENERGY, Direction.NORTH).isPresent(), "Side energy cap missing");

        IItemHandler top = itemHandler(machine, Direction.UP);
        helper.assertTrue(top.insertItem(0, new ItemStack(Items.REDSTONE), false).getCount() == 1, "Top accepted redstone");
        ItemStack taggedCanister = new ItemStack(ModItems.EMPTY_CANISTER.get());
        taggedCanister.getOrCreateTag().putInt("untrusted", 1);
        helper.assertTrue(
                top.insertItem(0, taggedCanister, false).getCount() == 1,
                "Input accepted a tagged canister that would lose data"
        );
        helper.assertTrue(
                top.insertItem(0, new ItemStack(ModItems.EMPTY_CANISTER.get(), 2), false).isEmpty(),
                "Top rejected empty canisters"
        );
        IItemHandler side = itemHandler(machine, Direction.NORTH);
        helper.assertTrue(side.insertItem(0, new ItemStack(Items.REDSTONE), false).isEmpty(), "Side rejected redstone");
        IItemHandler bottom = itemHandler(machine, Direction.DOWN);
        helper.assertTrue(
                bottom.insertItem(0, new ItemStack(ModItems.HYDROGEN_CANISTER.get()), false).getCount() == 1,
                "Bottom accepted an inserted output"
        );

        IFluidHandler fluid = fluidHandler(machine, Direction.NORTH);
        helper.assertTrue(
                fluid.fill(new FluidStack(Fluids.WATER, 1_000), IFluidHandler.FluidAction.EXECUTE) == 1_000,
                "Horizontal fluid cap rejected water"
        );
        helper.assertTrue(
                fluid.drain(1_000, IFluidHandler.FluidAction.EXECUTE).isEmpty(),
                "Horizontal fluid cap allowed external drain"
        );
        IEnergyStorage energy = energyHandler(machine, Direction.NORTH);
        helper.assertFalse(energy.canExtract(), "Horizontal energy cap allowed extraction");
        helper.assertTrue(energy.receiveEnergy(1_000, false) == 1_000, "Horizontal energy cap rejected FE");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 30)
    public static void electrolyzerPersistenceResumesAndPreservesFutureSchema(GameTestHelper helper) {
        ElectrolyzerBlockEntity machine = placeMachine(helper, BlockPos.ZERO, false);
        insertCycleInputs(machine, 2_000);

        helper.runAtTickTime(5, () -> {
            CompoundTag saved = machine.saveWithoutMetadata();
            ElectrolyzerBlockEntity restored = new ElectrolyzerBlockEntity(machine.getBlockPos(), machine.getBlockState());
            restored.load(saved);
            helper.assertTrue(restored.progress() == machine.progress(), "Reload changed in-flight progress");
            helper.assertTrue(restored.energyStored() == machine.energyStored(), "Reload changed stored energy");
            helper.assertTrue(restored.waterAmount() == machine.waterAmount(), "Reload changed stored water");
            helper.assertTrue(
                    restored.menuInventory().getStackInSlot(ElectrolyzerBlockEntity.SLOT_INPUT).getCount() == 2,
                    "Reload changed locked input"
            );

            CompoundTag missingRecipe = saved.copy();
            missingRecipe.getCompound("arce_machine").remove("active_recipe");
            ElectrolyzerBlockEntity recovered = new ElectrolyzerBlockEntity(machine.getBlockPos(), machine.getBlockState());
            recovered.load(missingRecipe);
            helper.assertTrue(recovered.progress() == 0, "Missing active recipe did not reset progress");
            helper.assertTrue(recovered.waterAmount() == 1_000, "Missing active recipe consumed water");
            helper.assertTrue(
                    recovered.menuInventory().getStackInSlot(ElectrolyzerBlockEntity.SLOT_INPUT).getCount() == 2,
                    "Missing active recipe consumed input"
            );

            CompoundTag futureMachine = new CompoundTag();
            futureMachine.putInt("schema_version", 2);
            futureMachine.putString("future_marker", "preserve-exactly");
            CompoundTag futureParent = new CompoundTag();
            futureParent.put("arce_machine", futureMachine.copy());
            ElectrolyzerBlockEntity future = new ElectrolyzerBlockEntity(machine.getBlockPos(), machine.getBlockState());
            future.load(futureParent);
            helper.assertTrue(
                    future.status() == ElectrolyzerStatus.UNSUPPORTED_DATA,
                    "Future schema did not disable processing"
            );
            helper.assertTrue(
                    future.saveWithoutMetadata().getCompound("arce_machine").equals(futureMachine),
                    "Future schema payload was not preserved exactly"
            );
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 30)
    public static void twoMenusShareAuthorityAndQuickMoveUsesMachineBoundaries(GameTestHelper helper) {
        ElectrolyzerBlockEntity machine = placeMachine(helper, BlockPos.ZERO, false);
        loadOutputStack(machine, new ItemStack(ModItems.HYDROGEN_CANISTER.get()));
        Player firstPlayer = helper.makeMockPlayer();
        Player secondPlayer = helper.makeMockPlayer();
        ElectrolyzerMenu firstMenu = (ElectrolyzerMenu) machine.createMenu(
                1,
                firstPlayer.getInventory(),
                firstPlayer
        );
        ElectrolyzerMenu secondMenu = (ElectrolyzerMenu) machine.createMenu(
                2,
                secondPlayer.getInventory(),
                secondPlayer
        );
        helper.assertTrue(firstMenu != null && secondMenu != null, "Electrolyzer menu construction failed");

        ItemStack movedOutput = firstMenu.quickMoveStack(firstPlayer, ElectrolyzerBlockEntity.SLOT_HYDROGEN);
        helper.assertTrue(movedOutput.is(ModItems.HYDROGEN_CANISTER.get()), "Output quick-move returned the wrong item");
        helper.assertTrue(
                itemHandler(machine, Direction.DOWN).getStackInSlot(0).isEmpty(),
                "Output quick-move did not clear the server slot"
        );
        helper.assertTrue(
                firstPlayer.getInventory().contains(new ItemStack(ModItems.HYDROGEN_CANISTER.get())),
                "Output quick-move did not reach the player inventory"
        );

        firstPlayer.getInventory().setItem(9, new ItemStack(ModItems.EMPTY_CANISTER.get(), 2));
        firstPlayer.getInventory().setItem(10, new ItemStack(Items.REDSTONE));
        helper.assertTrue(
                firstMenu.quickMoveStack(firstPlayer, ElectrolyzerBlockEntity.SLOT_COUNT)
                        .is(ModItems.EMPTY_CANISTER.get()),
                "Empty canister quick-move failed"
        );
        helper.assertTrue(
                firstMenu.quickMoveStack(firstPlayer, ElectrolyzerBlockEntity.SLOT_COUNT + 1).is(Items.REDSTONE),
                "Redstone quick-move failed"
        );
        helper.assertTrue(
                secondMenu.getSlot(ElectrolyzerBlockEntity.SLOT_INPUT).getItem().getCount() == 2,
                "Second viewer did not observe the authoritative input slot"
        );
        fluidHandler(machine, Direction.NORTH).fill(
                new FluidStack(Fluids.WATER, 1_000),
                IFluidHandler.FluidAction.EXECUTE
        );

        helper.runAtTickTime(5, () -> {
            helper.assertTrue(firstMenu.progress() > 0, "First viewer did not observe progress");
            helper.assertTrue(
                    firstMenu.progress() == secondMenu.progress(),
                    "Two viewers observed divergent server progress"
            );
            helper.assertTrue(
                    firstMenu.energyStored() == secondMenu.energyStored()
                            && firstMenu.waterAmount() == secondMenu.waterAmount()
                            && firstMenu.status() == secondMenu.status(),
                    "Two viewers observed divergent synchronized fields"
            );
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 30)
    public static void electrolyzerBreakDropsInventoryExactlyOnce(GameTestHelper helper) {
        BlockPos position = BlockPos.ZERO;
        ElectrolyzerBlockEntity machine = placeMachine(helper, position, false);
        itemHandler(machine, Direction.UP).insertItem(
                0,
                new ItemStack(ModItems.EMPTY_CANISTER.get(), 2),
                false
        );
        itemHandler(machine, Direction.NORTH).insertItem(0, new ItemStack(Items.REDSTONE), false);
        helper.assertTrue(
                helper.getLevel().destroyBlock(helper.absolutePos(position), true),
                "Electrolyzer could not be destroyed with drops enabled"
        );
        helper.succeedWhen(() -> {
            helper.assertItemEntityCountIs(ModItems.ELECTROLYZER.get(), position, 1.0D, 1);
            helper.assertItemEntityCountIs(ModItems.EMPTY_CANISTER.get(), position, 1.0D, 2);
            helper.assertItemEntityCountIs(Items.REDSTONE, position, 1.0D, 1);
        });
    }

    @GameTest(template = "empty", timeoutTicks = 120)
    public static void twentyIdleElectrolyzersPerformNoRecipeSearch(GameTestHelper helper) {
        ElectrolyzerBlockEntity[] machines = new ElectrolyzerBlockEntity[20];
        int index = 0;
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 4; z++) {
                machines[index++] = placeMachine(helper, new BlockPos(x, 0, z), false);
            }
        }
        helper.runAtTickTime(100, () -> {
            for (ElectrolyzerBlockEntity machine : machines) {
                helper.assertTrue(machine.recipeLookupCount() == 0, "Idle machine performed a recipe search");
            }
            helper.succeed();
        });
    }

    private static ElectrolyzerBlockEntity placeMachine(
            GameTestHelper helper,
            BlockPos position,
            boolean powered
    ) {
        helper.setBlock(
                position,
                ModBlocks.ELECTROLYZER.get().defaultBlockState()
                        .setValue(ElectrolyzerBlock.FACING, Direction.NORTH)
                        .setValue(ElectrolyzerBlock.POWERED, powered)
        );
        return (ElectrolyzerBlockEntity) helper.getBlockEntity(position);
    }

    private static void insertMaterials(ElectrolyzerBlockEntity machine) {
        IItemHandler top = itemHandler(machine, Direction.UP);
        ItemStack remainder = top.insertItem(
                0,
                new ItemStack(ModItems.EMPTY_CANISTER.get(), 2),
                false
        );
        if (!remainder.isEmpty()) {
            throw new IllegalStateException("Could not insert Electrolyzer test input");
        }
        int filled = fluidHandler(machine, Direction.NORTH).fill(
                new FluidStack(Fluids.WATER, 1_000),
                IFluidHandler.FluidAction.EXECUTE
        );
        if (filled != 1_000) {
            throw new IllegalStateException("Could not fill Electrolyzer test water");
        }
    }

    private static void loadOutputStack(ElectrolyzerBlockEntity machine, ItemStack output) {
        CompoundTag state = machine.saveWithoutMetadata();
        ItemStackHandler inventory = new ItemStackHandler(ElectrolyzerBlockEntity.SLOT_COUNT);
        inventory.setStackInSlot(ElectrolyzerBlockEntity.SLOT_HYDROGEN, output);
        state.getCompound("arce_machine").put("inventory", inventory.serializeNBT());
        machine.load(state);
    }

    private static void insertCycleInputs(ElectrolyzerBlockEntity machine, int energy) {
        insertMaterials(machine);
        IEnergyStorage storage = energyHandler(machine, Direction.NORTH);
        int remaining = energy;
        while (remaining > 0) {
            int received = storage.receiveEnergy(remaining, false);
            if (received <= 0) {
                throw new IllegalStateException("Could not fill Electrolyzer test energy");
            }
            remaining -= received;
        }
    }

    private static IItemHandler itemHandler(ElectrolyzerBlockEntity machine, Direction side) {
        return machine.getCapability(ForgeCapabilities.ITEM_HANDLER, side)
                .orElseThrow(() -> new IllegalStateException("Missing item capability on " + side));
    }

    private static IFluidHandler fluidHandler(ElectrolyzerBlockEntity machine, Direction side) {
        return machine.getCapability(ForgeCapabilities.FLUID_HANDLER, side)
                .orElseThrow(() -> new IllegalStateException("Missing fluid capability on " + side));
    }

    private static IEnergyStorage energyHandler(ElectrolyzerBlockEntity machine, Direction side) {
        return machine.getCapability(ForgeCapabilities.ENERGY, side)
                .orElseThrow(() -> new IllegalStateException("Missing energy capability on " + side));
    }
}
