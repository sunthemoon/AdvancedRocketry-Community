package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.MultiblockFormationState;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.MultiblockPartBinding;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.PartBindingValidationStatus;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.persistence.MultiblockControllerNbtCodec;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.persistence.MultiblockPartBindingNbtCodec;
import io.github.sunthemoon.advancedrocketrycommunity.machine.rolling.RollingMachineBlock;
import io.github.sunthemoon.advancedrocketrycommunity.machine.rolling.RollingMachineBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.machine.rolling.RollingMachinePortBlockEntity;
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
    private static final BlockPos CONTROLLER = new BlockPos(4, 2, 4);
    private static final BlockPos ITEM_INPUT = new BlockPos(2, 2, 4);
    private static final BlockPos FLUID_INPUT = new BlockPos(3, 2, 4);
    private static final BlockPos ENERGY_INPUT = new BlockPos(5, 2, 4);
    private static final BlockPos ITEM_OUTPUT = new BlockPos(6, 2, 4);

    private RollingMachineGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 40)
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

    @GameTest(template = "empty", timeoutTicks = 20)
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

    @GameTest(template = "empty", timeoutTicks = 40)
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
                    items.insertItem(0, new ItemStack(Items.IRON_INGOT, 3), false).isEmpty(),
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

    @GameTest(template = "empty", timeoutTicks = 20)
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

    @GameTest(template = "empty", timeoutTicks = 30)
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

    private static void placeStructure(GameTestHelper helper) {
        for (int z = 0; z < 2; z++) {
            for (int y = 0; y < 3; y++) {
                for (int x = 0; x < 5; x++) {
                    BlockPos position = new BlockPos(2 + x, 2 + y, 4 + z);
                    helper.setBlock(position, ModBlocks.MACHINE_CASING.get());
                }
            }
        }
        helper.setBlock(ITEM_INPUT, ModBlocks.ROLLING_MACHINE_ITEM_INPUT_PORT.get());
        helper.setBlock(FLUID_INPUT, ModBlocks.ROLLING_MACHINE_FLUID_INPUT_PORT.get());
        helper.setBlock(ENERGY_INPUT, ModBlocks.ROLLING_MACHINE_ENERGY_INPUT_PORT.get());
        helper.setBlock(ITEM_OUTPUT, ModBlocks.ROLLING_MACHINE_ITEM_OUTPUT_PORT.get());
        helper.setBlock(
                CONTROLLER,
                ModBlocks.ROLLING_MACHINE.get().defaultBlockState()
                        .setValue(RollingMachineBlock.FACING, Direction.NORTH)
        );
    }

    private static void assertPortsValid(GameTestHelper helper) {
        for (RollingMachinePortBlockEntity port : allPorts(helper)) {
            helper.assertTrue(
                    port.bindingStatus().orElse(null) == PartBindingValidationStatus.VALID,
                    "Typed port did not hold a valid active binding"
            );
        }
    }

    private static List<RollingMachinePortBlockEntity> allPorts(GameTestHelper helper) {
        return List.of(
                port(helper, ITEM_INPUT),
                port(helper, FLUID_INPUT),
                port(helper, ENERGY_INPUT),
                port(helper, ITEM_OUTPUT)
        );
    }

    private static List<RollingMachinePortBlockEntity> remainingPorts(GameTestHelper helper) {
        return List.of(
                port(helper, ITEM_INPUT),
                port(helper, ENERGY_INPUT),
                port(helper, ITEM_OUTPUT)
        );
    }

    private static RollingMachineBlockEntity controller(GameTestHelper helper) {
        return (RollingMachineBlockEntity) helper.getBlockEntity(CONTROLLER);
    }

    private static RollingMachinePortBlockEntity port(GameTestHelper helper, BlockPos position) {
        return (RollingMachinePortBlockEntity) helper.getBlockEntity(position);
    }

    private static CompoundTag futureRoot(String marker) {
        CompoundTag root = new CompoundTag();
        root.putInt("schema_version", 2);
        root.putString("marker", marker);
        return root;
    }
}
