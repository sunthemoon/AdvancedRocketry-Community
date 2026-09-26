package io.github.sunthemoon.advancedrocketrycommunity.machine.precision;

import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternPosition;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternRotation;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternTransform;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Loaded-only destruction checks; never infer ownership from formation alone. */
final class PrecisionAssemblerRemovalPolicy {
    private PrecisionAssemblerRemovalPolicy() {
    }

    static boolean blocksRemoval(BlockGetter world, BlockPos position) {
        if (!(world instanceof ServerLevel level) || !level.hasChunkAt(position)) {
            return false;
        }
        BlockEntity blockEntity = level.getBlockEntity(position);
        if (blockEntity instanceof PrecisionAssemblerBlockEntity controller) {
            return controller.blocksResourceRemoval();
        }
        if (!(blockEntity instanceof PrecisionAssemblerPortBlockEntity port)) {
            return false;
        }
        if (!port.acceptsBindingMutations()) {
            return true;
        }
        var binding = port.multiblockBinding();
        if (binding.isEmpty()) {
            return unresolvedUnboundOwner(level, port);
        }
        var expected = binding.orElseThrow();
        // An unloaded owner could still be PREPARING, even without a port marker.
        if (!expected.controllerLevel().equals(level.dimension())
                || !level.hasChunkAt(expected.controllerPosition())) {
            return true;
        }
        return level.getBlockEntity(expected.controllerPosition())
                instanceof PrecisionAssemblerBlockEntity controller
                // Invalid controller identity decodes to a fresh UUID, not proof
                // that the preserved owner is gone. Keep ambiguous data intact.
                && controller.blocksResourceRemoval();
    }

    private static boolean unresolvedUnboundOwner(ServerLevel level, PrecisionAssemblerPortBlockEntity port) {
        // A new empty facade can be replaced without losing a resource copy.
        // Legacy contents/markers may predate their saved binding instead.
        if (port.migrationMarker().isEmpty() && port.legacyStoredItemCopy().isEmpty()) {
            return false;
        }
        List<PatternPosition> cells = switch (port.portType()) {
            case ITEM_INPUT -> PrecisionAssemblerPortLayout.INPUT_CELLS;
            case ITEM_OUTPUT -> PrecisionAssemblerPortLayout.OUTPUT_CELLS;
            case ENERGY_INPUT -> List.of(PrecisionAssemblerPortLayout.ENERGY_CELL);
        };
        boolean unavailable = false;
        // At most 5 cells * 4 rotations * 2 mirrors = 40 candidate positions;
        // never a pattern/world scan or a chunk load.
        for (PatternPosition cell : cells) {
            for (PatternRotation rotation : PatternRotation.values()) {
                for (boolean mirror : new boolean[]{false, true}) {
                    PatternPosition offset = new PatternTransform(rotation, mirror).localToWorld(
                            cell, PrecisionAssemblerPortLayout.CONTROLLER_ANCHOR, new PatternPosition(0, 0, 0));
                    BlockPos candidate = port.getBlockPos().offset(-offset.x(), -offset.y(), -offset.z());
                    if (!level.hasChunkAt(candidate)) {
                        unavailable = true;
                    } else if (level.getBlockEntity(candidate) instanceof PrecisionAssemblerBlockEntity controller
                            && controller.blocksResourceRemoval()
                            && (controller.controllerState().partPositions().contains(port.getBlockPos())
                            || controller.controllerState().partPositions().isEmpty())) {
                        return true;
                    }
                }
            }
        }
        return unavailable;
    }
}
