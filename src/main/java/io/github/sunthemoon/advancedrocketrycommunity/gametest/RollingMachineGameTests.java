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
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

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
