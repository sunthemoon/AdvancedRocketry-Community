package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.PartBindingValidationStatus;
import io.github.sunthemoon.advancedrocketrycommunity.machine.rolling.RollingMachineBlock;
import io.github.sunthemoon.advancedrocketrycommunity.machine.rolling.RollingMachineBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.machine.rolling.RollingMachinePortBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;

/** Shared world setup and assertion helpers for Rolling Machine GameTests. */
final class RollingMachineGameTestFixtures {
    static final BlockPos CONTROLLER = new BlockPos(4, 2, 4);
    static final BlockPos ITEM_INPUT = new BlockPos(2, 2, 4);
    static final BlockPos FLUID_INPUT = new BlockPos(3, 2, 4);
    static final BlockPos ENERGY_INPUT = new BlockPos(5, 2, 4);
    static final BlockPos ITEM_OUTPUT = new BlockPos(6, 2, 4);

    private RollingMachineGameTestFixtures() {
    }

    static void placeStructure(GameTestHelper helper) {
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

    static void assertPortsValid(GameTestHelper helper) {
        for (RollingMachinePortBlockEntity port : allPorts(helper)) {
            helper.assertTrue(
                    port.bindingStatus().orElse(null) == PartBindingValidationStatus.VALID,
                    "Typed port did not hold a valid active binding"
            );
        }
    }

    static List<RollingMachinePortBlockEntity> allPorts(GameTestHelper helper) {
        return List.of(
                port(helper, ITEM_INPUT),
                port(helper, FLUID_INPUT),
                port(helper, ENERGY_INPUT),
                port(helper, ITEM_OUTPUT)
        );
    }

    static List<RollingMachinePortBlockEntity> remainingPorts(GameTestHelper helper) {
        return List.of(
                port(helper, ITEM_INPUT),
                port(helper, ENERGY_INPUT),
                port(helper, ITEM_OUTPUT)
        );
    }

    static RollingMachineBlockEntity controller(GameTestHelper helper) {
        return (RollingMachineBlockEntity) helper.getBlockEntity(CONTROLLER);
    }

    static RollingMachinePortBlockEntity port(GameTestHelper helper, BlockPos position) {
        return (RollingMachinePortBlockEntity) helper.getBlockEntity(position);
    }

    static CompoundTag futureRoot(String marker) {
        CompoundTag root = new CompoundTag();
        root.putInt("schema_version", 2);
        root.putString("marker", marker);
        return root;
    }
}
