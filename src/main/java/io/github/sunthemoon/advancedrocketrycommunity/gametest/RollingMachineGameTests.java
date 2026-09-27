package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import static io.github.sunthemoon.advancedrocketrycommunity.gametest.RollingMachineGameTestFixtures.CONTROLLER;
import static io.github.sunthemoon.advancedrocketrycommunity.gametest.RollingMachineGameTestFixtures.ENERGY_INPUT;
import static io.github.sunthemoon.advancedrocketrycommunity.gametest.RollingMachineGameTestFixtures.FLUID_INPUT;
import static io.github.sunthemoon.advancedrocketrycommunity.gametest.RollingMachineGameTestFixtures.ITEM_INPUT;
import static io.github.sunthemoon.advancedrocketrycommunity.gametest.RollingMachineGameTestFixtures.ITEM_OUTPUT;
import static io.github.sunthemoon.advancedrocketrycommunity.gametest.RollingMachineGameTestFixtures.allPorts;
import static io.github.sunthemoon.advancedrocketrycommunity.gametest.RollingMachineGameTestFixtures.assertPortsValid;
import static io.github.sunthemoon.advancedrocketrycommunity.gametest.RollingMachineGameTestFixtures.controller;
import static io.github.sunthemoon.advancedrocketrycommunity.gametest.RollingMachineGameTestFixtures.futureRoot;
import static io.github.sunthemoon.advancedrocketrycommunity.gametest.RollingMachineGameTestFixtures.placeStructure;
import static io.github.sunthemoon.advancedrocketrycommunity.gametest.RollingMachineGameTestFixtures.port;
import static io.github.sunthemoon.advancedrocketrycommunity.gametest.RollingMachineGameTestFixtures.remainingPorts;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.MultiblockFormationState;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.MultiblockPartBinding;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.persistence.MultiblockControllerNbtCodec;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.persistence.MultiblockPartBindingNbtCodec;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternDiagnosticReason;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternPosition;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessMachineState;
import io.github.sunthemoon.advancedrocketrycommunity.machine.rolling.RollingMachineBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.machine.rolling.RollingMachineMenu;
import io.github.sunthemoon.advancedrocketrycommunity.machine.rolling.RollingMachinePortBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.machine.rolling.RollingMachineRecipe;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;

@GameTestHolder(AdvancedRocketryCommunity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RollingMachineGameTests {
    private RollingMachineGameTests() {
    }

    @GameTest(template = "rocket_test", timeoutTicks = 40)
    public static void registeredMachineFormsBreaksAndRebuildsThroughBlockLifecycle(
            GameTestHelper helper
    ) {
        placeStructure(helper);

        helper.runAtTickTime(6, () -> {
            RollingMachineBlockEntity controller = controller(helper);
            helper.assertTrue(
                    controller.formationState() == MultiblockFormationState.FORMED,
                    "Registered Rolling Machine did not form"
            );
            helper.assertTrue(controller.generation() == 1L, "First formation was not generation one");
            assertPortsValid(helper);
            helper.setBlock(FLUID_INPUT, Blocks.AIR);
        });

        helper.runAtTickTime(14, () -> {
            RollingMachineBlockEntity controller = controller(helper);
            helper.assertTrue(
                    controller.formationState() == MultiblockFormationState.UNFORMED,
                    "Removed typed port did not invalidate the structure"
            );
            helper.assertTrue(
                    remainingPorts(helper).stream().allMatch(port -> port.multiblockBinding().isEmpty()),
                    "Disassembly retained a loaded port binding"
            );
            helper.setBlock(FLUID_INPUT, ModBlocks.ROLLING_MACHINE_FLUID_INPUT_PORT.get());
        });

        helper.runAtTickTime(22, () -> {
            RollingMachineBlockEntity controller = controller(helper);
            helper.assertTrue(
                    controller.formationState() == MultiblockFormationState.FORMED,
                    "Restored typed port did not rebuild the structure"
            );
            helper.assertTrue(controller.generation() == 2L, "Rebuild did not advance generation exactly once");
            assertPortsValid(helper);
            helper.succeed();
        });
    }

    @GameTest(template = "rocket_test", timeoutTicks = 20)
    public static void futureControllerAndBindingRootsArePreservedAndBlocked(GameTestHelper helper) {
        BlockPos controllerPosition = new BlockPos(2, 2, 2);
        BlockPos portPosition = controllerPosition.east();
        helper.setBlock(controllerPosition, ModBlocks.ROLLING_MACHINE.get());
        helper.setBlock(portPosition, ModBlocks.ROLLING_MACHINE_ITEM_INPUT_PORT.get());
        RollingMachineBlockEntity controller = (RollingMachineBlockEntity) helper.getBlockEntity(
                controllerPosition
        );
        RollingMachinePortBlockEntity port = (RollingMachinePortBlockEntity) helper.getBlockEntity(
                portPosition
        );

        CompoundTag futureController = futureRoot("controller-marker");
        CompoundTag controllerParent = new CompoundTag();
        controllerParent.put(MultiblockControllerNbtCodec.ROOT, futureController.copy());
        controller.load(controllerParent);
        helper.assertTrue(
                controller.formationState() == MultiblockFormationState.UNSUPPORTED_DATA,
                "Future controller schema did not fail closed"
        );
        helper.assertTrue(
                futureController.equals(controller.saveWithFullMetadata().get(MultiblockControllerNbtCodec.ROOT)),
                "Future controller root was not preserved exactly"
        );

        CompoundTag futureBinding = futureRoot("binding-marker");
        CompoundTag portParent = new CompoundTag();
        portParent.put(MultiblockPartBindingNbtCodec.ROOT, futureBinding.copy());
        port.load(portParent);
        boolean rejected = false;
        try {
            port.setMultiblockBinding(Optional.of(new MultiblockPartBinding(
                    MultiblockPartBinding.SCHEMA_VERSION,
                    helper.getLevel().dimension(),
                    helper.absolutePos(controllerPosition),
                    UUID.randomUUID(),
                    1L
            )));
        } catch (IllegalStateException expected) {
            rejected = true;
        }
        helper.assertTrue(rejected, "Future part binding accepted a lifecycle mutation");
        helper.assertTrue(
                futureBinding.equals(port.saveWithFullMetadata().get(MultiblockPartBindingNbtCodec.ROOT)),
                "Future part binding root was not preserved exactly"
        );
        helper.succeed();
    }

    @GameTest(template = "rocket_test", timeoutTicks = 20)
    public static void malformedCurrentControllerAndBindingRootsArePreservedAndBlocked(GameTestHelper helper) {
        BlockPos controllerPosition = new BlockPos(2, 2, 2);
        BlockPos portPosition = controllerPosition.east();
        helper.setBlock(controllerPosition, ModBlocks.ROLLING_MACHINE.get());
        helper.setBlock(portPosition, ModBlocks.ROLLING_MACHINE_ITEM_INPUT_PORT.get());
        RollingMachineBlockEntity controller = (RollingMachineBlockEntity) helper.getBlockEntity(controllerPosition);
        RollingMachinePortBlockEntity port = (RollingMachinePortBlockEntity) helper.getBlockEntity(portPosition);

        CompoundTag malformedController = MultiblockControllerNbtCodec.encode(controller.controllerState());
        ListTag wrongParts = new ListTag();
        wrongParts.add(IntTag.valueOf(42));
        malformedController.put("parts", wrongParts);
        CompoundTag controllerParent = new CompoundTag();
        controllerParent.put(MultiblockControllerNbtCodec.ROOT, malformedController.copy());
        controller.load(controllerParent);
        helper.assertTrue(controller.formationState() == MultiblockFormationState.UNSUPPORTED_DATA,
                "Malformed current controller schema did not fail closed");
        helper.assertTrue(malformedController.equals(
                        controller.saveWithFullMetadata().get(MultiblockControllerNbtCodec.ROOT)),
                "Malformed current controller root was not preserved exactly");

        CompoundTag malformedBinding = MultiblockPartBindingNbtCodec.encode(new MultiblockPartBinding(
                MultiblockPartBinding.SCHEMA_VERSION,
                helper.getLevel().dimension(),
                helper.absolutePos(controllerPosition),
                UUID.randomUUID(),
                1L
        ));
        malformedBinding.getCompound("controller").putString("unknown_position_data", "keep");
        CompoundTag portParent = new CompoundTag();
        portParent.put(MultiblockPartBindingNbtCodec.ROOT, malformedBinding.copy());
        port.load(portParent);
        boolean rejected = false;
        try {
            port.setMultiblockBinding(Optional.empty());
        } catch (IllegalStateException expected) {
            rejected = true;
        }
        helper.assertTrue(rejected, "Malformed current part binding accepted a lifecycle mutation");
        helper.assertTrue(malformedBinding.equals(
                        port.saveWithFullMetadata().get(MultiblockPartBindingNbtCodec.ROOT)),
                "Malformed current part binding root was not preserved exactly");
        helper.succeed();
    }

    @GameTest(template = "rocket_test", timeoutTicks = 40)
    public static void typedCapabilitiesRequireFormationAndRetainResourcesAcrossRebuild(
            GameTestHelper helper
    ) {
        AtomicReference<IItemHandler> retiredItems = new AtomicReference<>();
        AtomicReference<IFluidHandler> retiredFluid = new AtomicReference<>();
        AtomicReference<IEnergyStorage> retiredEnergy = new AtomicReference<>();
        BlockPos brokenCasing = new BlockPos(2, 3, 4);
        placeStructure(helper);

        helper.runAtTickTime(6, () -> {
            RollingMachinePortBlockEntity itemInput = port(helper, ITEM_INPUT);
            RollingMachinePortBlockEntity fluidInput = port(helper, FLUID_INPUT);
            RollingMachinePortBlockEntity energyInput = port(helper, ENERGY_INPUT);
            RollingMachinePortBlockEntity itemOutput = port(helper, ITEM_OUTPUT);
            IItemHandler items = itemInput.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP)
                    .resolve().orElseThrow();
            IFluidHandler fluid = fluidInput.getCapability(ForgeCapabilities.FLUID_HANDLER)
                    .resolve().orElseThrow();
            IEnergyStorage energy = energyInput.getCapability(ForgeCapabilities.ENERGY, Direction.DOWN)
                    .resolve().orElseThrow();

            helper.assertTrue(
                    !itemInput.getCapability(ForgeCapabilities.FLUID_HANDLER).isPresent(),
                    "Item input exposed the wrong capability"
            );
            helper.assertTrue(
                    !fluidInput.getCapability(ForgeCapabilities.ITEM_HANDLER).isPresent(),
                    "Fluid input exposed the wrong capability"
            );
            helper.assertTrue(
                    !energyInput.getCapability(ForgeCapabilities.ITEM_HANDLER).isPresent(),
                    "Energy input exposed the wrong capability"
            );
            helper.assertTrue(
                    itemOutput.getCapability(ForgeCapabilities.ITEM_HANDLER).isPresent(),
                    "Item output did not expose its typed capability"
            );
            BlockPos forgedPortPosition = new BlockPos(8, 2, 4);
            helper.setBlock(forgedPortPosition, ModBlocks.ROLLING_MACHINE_ITEM_INPUT_PORT.get());
            RollingMachinePortBlockEntity forgedPort = port(helper, forgedPortPosition);
            forgedPort.setMultiblockBinding(itemInput.multiblockBinding());
            helper.assertTrue(
                    !forgedPort.getCapability(ForgeCapabilities.ITEM_HANDLER).isPresent(),
                    "Port outside the controller part set accepted a copied binding"
            );
            helper.assertTrue(
                    items.insertItem(0, new ItemStack(Items.GOLD_INGOT, 3), false).isEmpty(),
                    "Formed Item input rejected a valid stack"
            );
            helper.assertTrue(
                    fluid.fill(new FluidStack(Fluids.WATER, 750), IFluidHandler.FluidAction.EXECUTE) == 750,
                    "Formed Fluid input rejected water"
            );
            helper.assertTrue(
                    energy.receiveEnergy(1_000, false) == 1_000,
                    "Formed Energy input rejected FE"
            );
            retiredItems.set(items);
            retiredFluid.set(fluid);
            retiredEnergy.set(energy);
            helper.setBlock(brokenCasing, Blocks.AIR);
        });

        helper.runAtTickTime(14, () -> {
            helper.assertTrue(
                    controller(helper).formationState() == MultiblockFormationState.UNFORMED,
                    "Broken casing did not unform the capability test machine"
            );
            helper.assertTrue(
                    !port(helper, ITEM_INPUT).getCapability(ForgeCapabilities.ITEM_HANDLER).isPresent(),
                    "Unformed Item port still advertised a capability"
            );
            helper.assertTrue(
                    retiredItems.get().insertItem(0, new ItemStack(Items.IRON_INGOT), false).getCount() == 1,
                    "Retired Item handler mutated an unformed port"
            );
            helper.assertTrue(
                    retiredFluid.get().fill(
                            new FluidStack(Fluids.WATER, 100),
                            IFluidHandler.FluidAction.EXECUTE
                    ) == 0,
                    "Retired Fluid handler mutated an unformed port"
            );
            helper.assertTrue(
                    retiredEnergy.get().receiveEnergy(100, false) == 0,
                    "Retired Energy handler mutated an unformed port"
            );
            helper.setBlock(brokenCasing, ModBlocks.MACHINE_CASING.get());
        });

        helper.runAtTickTime(22, () -> {
            helper.assertTrue(
                    controller(helper).formationState() == MultiblockFormationState.FORMED,
                    "Restored casing did not reform the capability test machine"
            );
            IItemHandler items = port(helper, ITEM_INPUT)
                    .getCapability(ForgeCapabilities.ITEM_HANDLER).resolve().orElseThrow();
            IFluidHandler fluid = port(helper, FLUID_INPUT)
                    .getCapability(ForgeCapabilities.FLUID_HANDLER).resolve().orElseThrow();
            IEnergyStorage energy = port(helper, ENERGY_INPUT)
                    .getCapability(ForgeCapabilities.ENERGY).resolve().orElseThrow();
            helper.assertTrue(items.getStackInSlot(0).getCount() == 3, "Item resource was lost on disassembly");
            helper.assertTrue(fluid.getFluidInTank(0).getAmount() == 750, "Fluid was lost on disassembly");
            helper.assertTrue(energy.getEnergyStored() == 1_000, "Energy was lost on disassembly");
            helper.assertTrue(
                    retiredItems.get().getStackInSlot(0).isEmpty()
                            && retiredItems.get().insertItem(
                            0,
                            new ItemStack(Items.IRON_INGOT),
                            false
                    ).getCount() == 1,
                    "Invalidated Item handler became live again after rebuild"
            );
            helper.succeed();
        });
    }

    @GameTest(template = "rocket_test", timeoutTicks = 20)
    public static void futurePortResourceRootIsPreservedAndBlocksFormation(GameTestHelper helper) {
        placeStructure(helper);
        RollingMachinePortBlockEntity port = port(helper, ITEM_INPUT);
        CompoundTag futureResource = futureRoot("resource-marker");
        CompoundTag parent = new CompoundTag();
        parent.put("arce_rolling_port", futureResource.copy());
        port.load(parent);

        helper.runAtTickTime(8, () -> {
            helper.assertTrue(
                    controller(helper).formationState() != MultiblockFormationState.FORMED,
                    "Future port resource schema participated in formation"
            );
            helper.assertTrue(
                    !port.getCapability(ForgeCapabilities.ITEM_HANDLER).isPresent(),
                    "Future port resource schema exposed a capability"
            );
            helper.assertTrue(
                    futureResource.equals(port.saveWithFullMetadata().get("arce_rolling_port")),
                    "Future port resource root was not preserved exactly"
            );
            helper.succeed();
        });
    }

    @GameTest(template = "rocket_test", timeoutTicks = 30)
    public static void itemPortDropsItsStoredStackWhenRemoved(GameTestHelper helper) {
        placeStructure(helper);
        helper.runAtTickTime(6, () -> {
            IItemHandler input = port(helper, ITEM_INPUT)
                    .getCapability(ForgeCapabilities.ITEM_HANDLER).resolve().orElseThrow();
            helper.assertTrue(
                    input.insertItem(0, new ItemStack(Items.IRON_INGOT, 3), false).isEmpty(),
                    "Item input did not accept the drop fixture"
            );
            helper.assertTrue(
                    helper.getLevel().destroyBlock(helper.absolutePos(ITEM_INPUT), true),
                    "Item input port could not be removed"
            );
        });
        helper.succeedWhen(() -> helper.assertItemEntityCountIs(
                Items.IRON_INGOT,
                ITEM_INPUT,
                1.0D,
                3
        ));
    }

    @GameTest(template = "rocket_test", timeoutTicks = 150)
    public static void rollingRecipeConsumesEnergyAndCommitsResourcesExactlyOnce(
            GameTestHelper helper
    ) {
        placeStructure(helper);
        helper.runAtTickTime(6, () -> {
            IItemHandler input = port(helper, ITEM_INPUT)
                    .getCapability(ForgeCapabilities.ITEM_HANDLER).resolve().orElseThrow();
            IFluidHandler fluid = port(helper, FLUID_INPUT)
                    .getCapability(ForgeCapabilities.FLUID_HANDLER).resolve().orElseThrow();
            IEnergyStorage energy = port(helper, ENERGY_INPUT)
                    .getCapability(ForgeCapabilities.ENERGY).resolve().orElseThrow();
            helper.assertTrue(
                    input.insertItem(0, new ItemStack(Items.IRON_INGOT, 2), false).isEmpty(),
                    "Rolling recipe input was rejected"
            );
            helper.assertTrue(
                    fluid.fill(new FluidStack(Fluids.WATER, 500), IFluidHandler.FluidAction.EXECUTE) == 500,
                    "Rolling recipe water was rejected"
            );
            for (int transfer = 0; transfer < 4; transfer++) {
                helper.assertTrue(
                        energy.receiveEnergy(1_000, false) == 1_000,
                        "Rolling recipe energy transfer was rejected"
                );
            }
        });
        helper.runAtTickTime(12, () -> {
            RollingMachineBlockEntity controller = controller(helper);
            helper.assertTrue(
                    controller.processState() == ProcessMachineState.RUNNING,
                    "Rolling recipe did not enter RUNNING"
            );
            helper.assertTrue(
                    controller.processProgress().map(progress -> progress.progressTicks() > 0).orElse(false),
                    "Rolling recipe did not persist positive progress"
            );
            IItemHandler input = port(helper, ITEM_INPUT)
                    .getCapability(ForgeCapabilities.ITEM_HANDLER).resolve().orElseThrow();
            helper.assertTrue(
                    input.insertItem(0, new ItemStack(Items.IRON_INGOT), false).getCount() == 1,
                    "Active Rolling recipe did not lock external Item insertion"
            );
            Player viewer = helper.makeMockPlayer();
            BlockPos controllerWorld = helper.absolutePos(CONTROLLER);
            viewer.setPos(
                    controllerWorld.getX() + 0.5D,
                    controllerWorld.getY() + 0.5D,
                    controllerWorld.getZ() + 0.5D
            );
            RollingMachineMenu menu = (RollingMachineMenu) controller.createMenu(
                    11,
                    viewer.getInventory(),
                    viewer
            );
            helper.assertTrue(menu.progress() == controller.processProgress().orElseThrow().progressTicks(),
                    "Menu progress diverged from the server process");
            helper.assertTrue(menu.totalProcessingTicks() == 100,
                    "Menu did not expose the active recipe duration");
            helper.assertTrue(menu.energyStored() == port(helper, ENERGY_INPUT)
                            .getCapability(ForgeCapabilities.ENERGY).resolve().orElseThrow().getEnergyStored(),
                    "Menu energy diverged from the bound energy port");
            helper.assertTrue(menu.waterAmount() == 500,
                    "Menu water diverged from the bound fluid port");
        });
        helper.runAtTickTime(130, () -> {
            RollingMachineBlockEntity controller = controller(helper);
            IItemHandler input = port(helper, ITEM_INPUT)
                    .getCapability(ForgeCapabilities.ITEM_HANDLER).resolve().orElseThrow();
            IFluidHandler fluid = port(helper, FLUID_INPUT)
                    .getCapability(ForgeCapabilities.FLUID_HANDLER).resolve().orElseThrow();
            IEnergyStorage energy = port(helper, ENERGY_INPUT)
                    .getCapability(ForgeCapabilities.ENERGY).resolve().orElseThrow();
            IItemHandler output = port(helper, ITEM_OUTPUT)
                    .getCapability(ForgeCapabilities.ITEM_HANDLER).resolve().orElseThrow();
            CompoundTag saved = controller.saveWithFullMetadata();
            helper.assertTrue(input.getStackInSlot(0).isEmpty(), "Rolling batch did not consume two ingots");
            helper.assertTrue(fluid.getFluidInTank(0).getAmount() == 400, "Rolling batch did not consume 100 mB");
            helper.assertTrue(energy.getEnergyStored() == 2_000, "Rolling batch did not consume exactly 2,000 FE");
            helper.assertTrue(
                    output.getStackInSlot(0).is(Items.IRON_BARS)
                            && output.getStackInSlot(0).getCount() == 8,
                    "Rolling batch did not produce exactly eight iron bars"
            );
            helper.assertTrue(controller.processState() == ProcessMachineState.IDLE, "Completed batch did not idle");
            helper.assertTrue(controller.processProgress().isEmpty(), "Completed batch retained active progress");
            helper.assertTrue(controller.resourceRevision() == 7, "Resource revision did not advance exactly once");
            helper.assertTrue(saved.contains("arce_process"), "Process state root was not persisted");
            helper.assertTrue(!saved.contains("arce_process_journal"), "Completed batch retained a journal");
            helper.assertTrue(
                    !saved.getCompound("arce_process").getString("last_applied_transaction").isEmpty(),
                    "Completed batch did not persist its replay marker"
            );
            Player viewer = helper.makeMockPlayer();
            BlockPos controllerWorld = helper.absolutePos(CONTROLLER);
            viewer.setPos(
                    controllerWorld.getX() + 0.5D,
                    controllerWorld.getY() + 0.5D,
                    controllerWorld.getZ() + 0.5D
            );
            RollingMachineMenu menu = (RollingMachineMenu) controller.createMenu(
                    13,
                    viewer.getInventory(),
                    viewer
            );
            for (int slot = 0; slot < 36; slot++) {
                viewer.getInventory().setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
            }
            viewer.getInventory().setItem(8, new ItemStack(Items.IRON_BARS, 63));
            helper.assertTrue(
                    menu.quickMoveStack(viewer, RollingMachineMenu.SLOT_OUTPUT).getCount() == 8,
                    "Menu output did not begin the bounded partial quick-move"
            );
            helper.assertTrue(
                    output.getStackInSlot(0).getCount() == 7
                            && viewer.getInventory().getItem(8).getCount() == 64,
                    "Partial output quick-move duplicated or lost an item"
            );
            viewer.getInventory().setItem(7, ItemStack.EMPTY);
            helper.assertTrue(
                    menu.quickMoveStack(viewer, RollingMachineMenu.SLOT_OUTPUT).getCount() == 7,
                    "Menu output did not quick-move the remaining batch"
            );
            helper.assertTrue(output.getStackInSlot(0).isEmpty(),
                    "Menu output quick-move did not clear the authoritative port");
            helper.assertTrue(
                    viewer.getInventory().getItem(7).is(Items.IRON_BARS)
                            && viewer.getInventory().getItem(7).getCount() == 7,
                    "Remaining output quick-move did not reach the player inventory"
            );
            helper.succeed();
        });
    }

    @GameTest(template = "rocket_test", timeoutTicks = 30)
    public static void futureProcessRootIsPreservedAndHidesAllPortCapabilities(GameTestHelper helper) {
        placeStructure(helper);
        RollingMachineBlockEntity controller = controller(helper);
        CompoundTag futureProcess = futureRoot("process-marker");
        CompoundTag parent = new CompoundTag();
        parent.put("arce_process", futureProcess.copy());
        controller.load(parent);

        helper.runAtTickTime(10, () -> {
            helper.assertTrue(
                    controller.formationState() == MultiblockFormationState.FORMED,
                    "Future process root incorrectly prevented structural formation"
            );
            helper.assertTrue(
                    controller.processState() == ProcessMachineState.UNSUPPORTED_DATA,
                    "Future process root did not block process execution"
            );
            helper.assertTrue(
                    allPorts(helper).stream().noneMatch(port ->
                            port.getCapability(ForgeCapabilities.ITEM_HANDLER).isPresent()
                                    || port.getCapability(ForgeCapabilities.FLUID_HANDLER).isPresent()
                                    || port.getCapability(ForgeCapabilities.ENERGY).isPresent()),
                    "Future process root exposed a resource capability"
            );
            helper.assertTrue(
                    futureProcess.equals(controller.saveWithFullMetadata().get("arce_process")),
                    "Future process root was not preserved exactly"
            );
            helper.succeed();
        });
    }

    @GameTest(template = "rocket_test", timeoutTicks = 150)
    public static void activeRollingProgressResumesAfterNbtReload(GameTestHelper helper) {
        placeStructure(helper);
        helper.runAtTickTime(6, () -> {
            IItemHandler input = port(helper, ITEM_INPUT)
                    .getCapability(ForgeCapabilities.ITEM_HANDLER).resolve().orElseThrow();
            IFluidHandler fluid = port(helper, FLUID_INPUT)
                    .getCapability(ForgeCapabilities.FLUID_HANDLER).resolve().orElseThrow();
            IEnergyStorage energy = port(helper, ENERGY_INPUT)
                    .getCapability(ForgeCapabilities.ENERGY).resolve().orElseThrow();
            helper.assertTrue(
                    input.insertItem(0, new ItemStack(Items.IRON_INGOT, 2), false).isEmpty(),
                    "Reload fixture input was rejected"
            );
            helper.assertTrue(
                    fluid.fill(new FluidStack(Fluids.WATER, 500), IFluidHandler.FluidAction.EXECUTE) == 500,
                    "Reload fixture water was rejected"
            );
            for (int transfer = 0; transfer < 4; transfer++) {
                energy.receiveEnergy(1_000, false);
            }
        });
        helper.runAtTickTime(35, () -> {
            RollingMachineBlockEntity controller = controller(helper);
            int progressBefore = controller.processProgress().orElseThrow().progressTicks();
            int energyBefore = port(helper, ENERGY_INPUT)
                    .getCapability(ForgeCapabilities.ENERGY).resolve().orElseThrow().getEnergyStored();
            CompoundTag controllerData = controller.saveWithFullMetadata();
            List<CompoundTag> portData = allPorts(helper).stream()
                    .map(RollingMachinePortBlockEntity::saveWithFullMetadata)
                    .toList();

            controller.load(controllerData);
            List<RollingMachinePortBlockEntity> ports = allPorts(helper);
            for (int index = 0; index < ports.size(); index++) {
                ports.get(index).load(portData.get(index));
            }
            helper.assertTrue(
                    controller.processProgress().orElseThrow().progressTicks() == progressBefore,
                    "NBT reload changed active Rolling progress"
            );
            helper.assertTrue(
                    port(helper, ENERGY_INPUT).getCapability(ForgeCapabilities.ENERGY)
                            .resolve().orElseThrow().getEnergyStored() == energyBefore,
                    "NBT reload changed stored Rolling energy"
            );
        });
        helper.runAtTickTime(135, () -> {
            RollingMachineBlockEntity controller = controller(helper);
            ItemStack output = port(helper, ITEM_OUTPUT)
                    .getCapability(ForgeCapabilities.ITEM_HANDLER).resolve().orElseThrow()
                    .getStackInSlot(0);
            helper.assertTrue(
                    controller.processState() == ProcessMachineState.IDLE
                            && controller.processProgress().isEmpty(),
                    "Reloaded Rolling process did not finish"
            );
            helper.assertTrue(
                    output.is(Items.IRON_BARS) && output.getCount() == 8,
                    "Reloaded Rolling process duplicated or lost its output"
            );
            helper.assertTrue(
                    port(helper, ENERGY_INPUT).getCapability(ForgeCapabilities.ENERGY)
                            .resolve().orElseThrow().getEnergyStored() == 2_000,
                    "Reloaded Rolling process consumed energy more than once"
            );
            helper.succeed();
        });
    }

    @GameTest(template = "rocket_test", timeoutTicks = 30)
    public static void completedProgressWithoutJournalFailsClosed(GameTestHelper helper) {
        placeStructure(helper);
        helper.runAtTickTime(6, () -> {
            IItemHandler input = port(helper, ITEM_INPUT)
                    .getCapability(ForgeCapabilities.ITEM_HANDLER).resolve().orElseThrow();
            IFluidHandler fluid = port(helper, FLUID_INPUT)
                    .getCapability(ForgeCapabilities.FLUID_HANDLER).resolve().orElseThrow();
            IEnergyStorage energy = port(helper, ENERGY_INPUT)
                    .getCapability(ForgeCapabilities.ENERGY).resolve().orElseThrow();
            input.insertItem(0, new ItemStack(Items.IRON_INGOT, 4), false);
            fluid.fill(new FluidStack(Fluids.WATER, 500), IFluidHandler.FluidAction.EXECUTE);
            for (int transfer = 0; transfer < 4; transfer++) {
                energy.receiveEnergy(1_000, false);
            }

            ResourceLocation recipeId = ResourceLocation.tryBuild(
                    AdvancedRocketryCommunity.MOD_ID,
                    "rolling_iron_bars"
            );
            RollingMachineRecipe recipe = (RollingMachineRecipe) helper.getLevel()
                    .getRecipeManager().byKey(recipeId).orElseThrow();
            RollingMachineBlockEntity controller = controller(helper);
            CompoundTag saved = controller.saveWithFullMetadata();
            CompoundTag process = saved.getCompound("arce_process");
            process.putString("state", "running");
            process.putString("definition_id", recipeId.toString());
            process.putString("recipe_signature", recipe.signature());
            process.putInt("progress_ticks", recipe.processDefinition().durationTicks());
            process.putLong("consumed_energy", recipe.processDefinition().totalEnergy());
            controller.load(saved);
        });
        helper.runAtTickTime(15, () -> {
            RollingMachineBlockEntity controller = controller(helper);
            CompoundTag input = port(helper, ITEM_INPUT).saveWithFullMetadata()
                    .getCompound("arce_rolling_port").getCompound("item");
            CompoundTag output = port(helper, ITEM_OUTPUT).saveWithFullMetadata()
                    .getCompound("arce_rolling_port").getCompound("item");
            CompoundTag fluid = port(helper, FLUID_INPUT).saveWithFullMetadata()
                    .getCompound("arce_rolling_port").getCompound("fluid");
            CompoundTag energy = port(helper, ENERGY_INPUT).saveWithFullMetadata()
                    .getCompound("arce_rolling_port");
            helper.assertTrue(
                    controller.processState() == ProcessMachineState.RECOVERY_REQUIRED,
                    "Completed progress without a journal was not stopped for recovery"
            );
            helper.assertTrue(
                    input.getByte("Count") == 4,
                    "Ambiguous completed progress consumed another input batch"
            );
            helper.assertTrue(output.isEmpty(), "Ambiguous completed progress produced duplicate output");
            helper.assertTrue(fluid.getInt("Amount") == 500, "Ambiguous completed progress consumed water");
            helper.assertTrue(energy.getInt("energy") == 4_000, "Ambiguous completed progress consumed energy");
            IItemHandler retainedInput = port(helper, ITEM_INPUT)
                    .getCapability(ForgeCapabilities.ITEM_HANDLER).resolve().orElseThrow();
            helper.assertTrue(
                    retainedInput.insertItem(0, new ItemStack(Items.IRON_INGOT), false).getCount() == 1,
                    "Recovery-required process accepted an external resource mutation"
            );
            helper.succeed();
        });
    }

    @GameTest(template = "rocket_test", timeoutTicks = 40)
    public static void rollingMenuUsesBoundPortsAndLocatesFirstStructureDiagnostic(
            GameTestHelper helper
    ) {
        placeStructure(helper);
        AtomicReference<RollingMachineMenu> openedMenu = new AtomicReference<>();
        AtomicReference<Player> openedPlayer = new AtomicReference<>();
        BlockPos brokenCasing = new BlockPos(2, 3, 4);

        helper.runAtTickTime(6, () -> {
            RollingMachineBlockEntity controller = controller(helper);
            Player player = helper.makeMockPlayer();
            BlockPos controllerWorld = helper.absolutePos(CONTROLLER);
            player.setPos(
                    controllerWorld.getX() + 0.5D,
                    controllerWorld.getY() + 0.5D,
                    controllerWorld.getZ() + 0.5D
            );
            RollingMachineMenu menu = (RollingMachineMenu) controller.createMenu(
                    12,
                    player.getInventory(),
                    player
            );
            helper.assertTrue(menu != null, "Rolling Machine menu construction failed");
            helper.assertTrue(menu.stillValid(player), "Nearby Rolling Machine menu was invalid");
            helper.assertTrue(
                    menu.formationState() == MultiblockFormationState.FORMED,
                    "Formed state was not exposed to the menu"
            );

            player.getInventory().setItem(9, new ItemStack(Items.IRON_INGOT, 2));
            helper.assertTrue(
                    menu.quickMoveStack(player, RollingMachineMenu.MACHINE_SLOT_COUNT)
                            .is(Items.IRON_INGOT),
                    "Player inventory did not quick-move into the bound input port"
            );
            IItemHandler input = port(helper, ITEM_INPUT)
                    .getCapability(ForgeCapabilities.ITEM_HANDLER).resolve().orElseThrow();
            helper.assertTrue(
                    input.getStackInSlot(0).getCount() == 2,
                    "Menu input did not reach the authoritative port"
            );
            player.getInventory().setItem(10, new ItemStack(Items.IRON_INGOT));
            helper.assertTrue(
                    menu.quickMoveStack(player, RollingMachineMenu.MACHINE_SLOT_COUNT + 1)
                            .is(Items.IRON_INGOT),
                    "Player inventory did not quick-move into the occupied input port"
            );
            helper.assertTrue(
                    input.getStackInSlot(0).getCount() == 3
                            && player.getInventory().getItem(10).isEmpty(),
                    "Occupied input merge duplicated or lost an item"
            );
            helper.assertTrue(
                    menu.quickMoveStack(player, RollingMachineMenu.SLOT_INPUT).getCount() == 3,
                    "Bound input did not quick-move back to the player"
            );
            helper.assertTrue(
                    input.getStackInSlot(0).isEmpty(),
                    "Menu extraction did not clear the authoritative port"
            );

            openedMenu.set(menu);
            openedPlayer.set(player);
            helper.setBlock(brokenCasing, Blocks.AIR);
        });

        helper.runAtTickTime(14, () -> {
            RollingMachineMenu menu = openedMenu.get();
            Player player = openedPlayer.get();
            helper.assertTrue(
                    menu.formationState() == MultiblockFormationState.UNFORMED,
                    "Broken structure remained formed in the menu"
            );
            helper.assertTrue(
                    menu.diagnosticReason().orElse(null) == PatternDiagnosticReason.BLOCK_MISMATCH,
                    "Menu did not expose the first structured mismatch"
            );
            helper.assertTrue(
                    menu.diagnosticLocalPosition().orElseThrow().equals(new PatternPosition(0, 1, 0)),
                    "Menu diagnostic reported the wrong local cell"
            );
            helper.assertTrue(
                    menu.diagnosticWorldPosition().orElseThrow().equals(helper.absolutePos(brokenCasing)),
                    "Menu diagnostic reported the wrong world block"
            );
            helper.assertTrue(
                    !menu.getSlot(RollingMachineMenu.SLOT_INPUT)
                            .mayPlace(new ItemStack(Items.IRON_INGOT)),
                    "Unformed menu retained input access"
            );

            BlockPos controllerWorld = helper.absolutePos(CONTROLLER);
            player.setPos(controllerWorld.getX() + 100.0D, controllerWorld.getY(), controllerWorld.getZ());
            helper.assertTrue(!menu.stillValid(player), "Distant player retained Rolling Machine menu validity");
            helper.succeed();
        });
    }
}
