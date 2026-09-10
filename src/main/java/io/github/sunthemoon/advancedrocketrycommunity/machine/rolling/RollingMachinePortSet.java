package io.github.sunthemoon.advancedrocketrycommunity.machine.rolling;

import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.MultiblockFormationState;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.MultiblockPartBinding;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Exact loaded typed-port set for one formed Rolling Machine generation. */
record RollingMachinePortSet(
        RollingMachinePortBlockEntity itemInput,
        RollingMachinePortBlockEntity fluidInput,
        RollingMachinePortBlockEntity energyInput,
        RollingMachinePortBlockEntity itemOutput
) {
    static Optional<RollingMachinePortSet> resolve(
            ServerLevel level,
            RollingMachineBlockEntity controller
    ) {
        if (controller.formationState() != MultiblockFormationState.FORMED) {
            return Optional.empty();
        }
        Map<RollingMachinePortType, RollingMachinePortBlockEntity> found =
                new EnumMap<>(RollingMachinePortType.class);
        for (BlockPos position : controller.controllerState().partPositions()) {
            if (!level.hasChunkAt(position)) {
                return Optional.empty();
            }
            BlockEntity blockEntity = level.getBlockEntity(position);
            if (!(blockEntity instanceof RollingMachinePortBlockEntity port)) {
                continue;
            }
            if (!belongsTo(level, controller, port)
                    || found.putIfAbsent(port.portType(), port) != null) {
                return Optional.empty();
            }
        }
        if (found.size() != RollingMachinePortType.values().length) {
            return Optional.empty();
        }
        return Optional.of(new RollingMachinePortSet(
                found.get(RollingMachinePortType.ITEM_INPUT),
                found.get(RollingMachinePortType.FLUID_INPUT),
                found.get(RollingMachinePortType.ENERGY_INPUT),
                found.get(RollingMachinePortType.ITEM_OUTPUT)
        ));
    }

    boolean remainsUsable(ServerLevel level, RollingMachineBlockEntity controller) {
        return !itemInput.isRemoved()
                && !fluidInput.isRemoved()
                && !energyInput.isRemoved()
                && !itemOutput.isRemoved()
                && itemInput.getLevel() == level
                && fluidInput.getLevel() == level
                && energyInput.getLevel() == level
                && itemOutput.getLevel() == level
                && belongsTo(level, controller, itemInput)
                && belongsTo(level, controller, fluidInput)
                && belongsTo(level, controller, energyInput)
                && belongsTo(level, controller, itemOutput);
    }

    private static boolean belongsTo(
            ServerLevel level,
            RollingMachineBlockEntity controller,
            RollingMachinePortBlockEntity port
    ) {
        Optional<MultiblockPartBinding> binding = port.multiblockBinding();
        if (binding.isEmpty()) {
            return false;
        }
        MultiblockPartBinding actual = binding.orElseThrow();
        return actual.controllerLevel().equals(level.dimension())
                && actual.controllerPosition().equals(controller.getBlockPos())
                && actual.machineInstanceId().equals(controller.controllerState().machineInstanceId())
                && actual.generation() == controller.generation()
                && controller.controllerState().partPositions().contains(port.getBlockPos());
    }
}
