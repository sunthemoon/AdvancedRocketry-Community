package io.github.sunthemoon.advancedrocketrycommunity.machine.precision;

import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.MultiblockFormationState;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternPosition;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** Exact, loaded port identities for one formed controller generation. */
record PrecisionAssemblerPortSet(
        List<PrecisionAssemblerPortBlockEntity> inputs,
        List<PrecisionAssemblerPortBlockEntity> outputs,
        PrecisionAssemblerPortBlockEntity energy
) {
    PrecisionAssemblerPortSet {
        inputs = List.copyOf(inputs);
        outputs = List.copyOf(outputs);
        if (inputs.size() != PrecisionAssemblerChannels.INPUT_COUNT
                || outputs.size() != PrecisionAssemblerChannels.OUTPUT_COUNT) {
            throw new IllegalArgumentException("precision port set has the wrong channel count");
        }
    }

    static Optional<PrecisionAssemblerPortSet> resolve(
            ServerLevel level,
            PrecisionAssemblerBlockEntity controller
    ) {
        return resolve(level, controller, false);
    }

    static Optional<PrecisionAssemblerPortSet> resolveForMigration(
            ServerLevel level,
            PrecisionAssemblerBlockEntity controller
    ) {
        return resolve(level, controller, true);
    }

    private static Optional<PrecisionAssemblerPortSet> resolve(
            ServerLevel level,
            PrecisionAssemblerBlockEntity controller,
            boolean migration
    ) {
        if (controller.formationState() != MultiblockFormationState.FORMED
                || !(migration ? controller.acceptsPortBindingAccess()
                : controller.acceptsResourceAccess())) {
            return Optional.empty();
        }
        List<PrecisionAssemblerPortBlockEntity> inputs = new ArrayList<>();
        List<PrecisionAssemblerPortBlockEntity> outputs = new ArrayList<>();
        for (int index = 0; index < PrecisionAssemblerChannels.INPUT_COUNT; index++) {
            Optional<PrecisionAssemblerPortBlockEntity> port = atLocal(
                    level, controller, PrecisionAssemblerPortLayout.INPUT_CELLS.get(index),
                    PrecisionAssemblerPortType.ITEM_INPUT, PrecisionAssemblerChannels.input(index)
            );
            if (port.isEmpty()) {
                return Optional.empty();
            }
            inputs.add(port.orElseThrow());
        }
        for (int index = 0; index < PrecisionAssemblerChannels.OUTPUT_COUNT; index++) {
            Optional<PrecisionAssemblerPortBlockEntity> port = atLocal(
                    level, controller, PrecisionAssemblerPortLayout.OUTPUT_CELLS.get(index),
                    PrecisionAssemblerPortType.ITEM_OUTPUT, PrecisionAssemblerChannels.output(index)
            );
            if (port.isEmpty()) {
                return Optional.empty();
            }
            outputs.add(port.orElseThrow());
        }
        Optional<PrecisionAssemblerPortBlockEntity> energy = atLocal(
                level, controller, PrecisionAssemblerPortLayout.ENERGY_CELL,
                PrecisionAssemblerPortType.ENERGY_INPUT, PrecisionAssemblerChannels.ENERGY_INPUT
        );
        return energy.map(value -> new PrecisionAssemblerPortSet(inputs, outputs, value));
    }

    boolean remainsUsable(ServerLevel level, PrecisionAssemblerBlockEntity controller) {
        if (controller.formationState() != MultiblockFormationState.FORMED) {
            return false;
        }
        for (int index = 0; index < inputs.size(); index++) {
            if (!samePort(level, controller, inputs.get(index),
                    PrecisionAssemblerPortLayout.INPUT_CELLS.get(index),
                    PrecisionAssemblerPortType.ITEM_INPUT, PrecisionAssemblerChannels.input(index))) {
                return false;
            }
        }
        for (int index = 0; index < outputs.size(); index++) {
            if (!samePort(level, controller, outputs.get(index),
                    PrecisionAssemblerPortLayout.OUTPUT_CELLS.get(index),
                    PrecisionAssemblerPortType.ITEM_OUTPUT, PrecisionAssemblerChannels.output(index))) {
                return false;
            }
        }
        return samePort(level, controller, energy, PrecisionAssemblerPortLayout.ENERGY_CELL,
                PrecisionAssemblerPortType.ENERGY_INPUT, PrecisionAssemblerChannels.ENERGY_INPUT);
    }

    private static Optional<PrecisionAssemblerPortBlockEntity> atLocal(
            ServerLevel level,
            PrecisionAssemblerBlockEntity controller,
            PatternPosition local,
            PrecisionAssemblerPortType type,
            String channel
    ) {
        BlockPos position = worldPosition(controller, local);
        if (!level.hasChunkAt(position)
                || !(level.getBlockEntity(position) instanceof PrecisionAssemblerPortBlockEntity port)
                || !samePort(level, controller, port, local, type, channel)) {
            return Optional.empty();
        }
        return Optional.of(port);
    }

    private static boolean samePort(
            ServerLevel level,
            PrecisionAssemblerBlockEntity controller,
            PrecisionAssemblerPortBlockEntity port,
            PatternPosition local,
            PrecisionAssemblerPortType type,
            String channel
    ) {
        BlockPos position = worldPosition(controller, local);
        return !port.isRemoved()
                && port.getLevel() == level
                && port.getBlockPos().equals(position)
                && level.hasChunkAt(position)
                && level.getBlockEntity(position) == port
                && port.acceptsBindingMutations()
                && port.portType() == type
                && controller.controllerState().partPositions().contains(position)
                && port.assignedChannel().filter(channel::equals).isPresent();
    }

    private static BlockPos worldPosition(
            PrecisionAssemblerBlockEntity controller,
            PatternPosition local
    ) {
        BlockPos origin = controller.getBlockPos();
        PatternPosition world = controller.controllerState().selectedTransform().localToWorld(
                local,
                PrecisionAssemblerPortLayout.CONTROLLER_ANCHOR,
                new PatternPosition(origin.getX(), origin.getY(), origin.getZ())
        );
        return new BlockPos(world.x(), world.y(), world.z());
    }
}
