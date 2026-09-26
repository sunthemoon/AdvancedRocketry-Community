package io.github.sunthemoon.advancedrocketrycommunity.machine.precision;

import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.BindingMutationResult;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.MultiblockBindingGateway;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.MultiblockPartBinding;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternPosition;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

/** Keep legacy ownership discoverable until every migration marker is durable. */
final class PrecisionAssemblerMigrationBindings implements MultiblockBindingGateway {
    private final MultiblockBindingGateway delegate;

    PrecisionAssemblerMigrationBindings(MultiblockBindingGateway delegate) {
        this.delegate = java.util.Objects.requireNonNull(delegate, "delegate");
    }

    /** Reconcile only the same prepared owner's older/missing saved bindings. */
    static void restorePendingBindings(ServerLevel level, PrecisionAssemblerBlockEntity controller) {
        var state = controller.controllerState();
        if (state.generation() == 0 || state.partPositions().size() != 8) {
            return;
        }
        BlockPos origin = controller.getBlockPos();
        List<PatternPosition> cells = Stream.concat(Stream.concat(
                PrecisionAssemblerPortLayout.INPUT_CELLS.stream(),
                PrecisionAssemblerPortLayout.OUTPUT_CELLS.stream()),
                Stream.of(PrecisionAssemblerPortLayout.ENERGY_CELL)).toList();
        List<PrecisionAssemblerPortBlockEntity> ports = new ArrayList<>(8);
        MultiblockPartBinding expected = new MultiblockPartBinding(MultiblockPartBinding.SCHEMA_VERSION,
                level.dimension(), origin, state.machineInstanceId(), state.generation());
        for (int index = 0; index < cells.size(); index++) {
            PatternPosition world = state.selectedTransform().localToWorld(cells.get(index),
                    PrecisionAssemblerPortLayout.CONTROLLER_ANCHOR,
                    new PatternPosition(origin.getX(), origin.getY(), origin.getZ()));
            BlockPos position = new BlockPos(world.x(), world.y(), world.z());
            var assignment = PrecisionAssemblerPortLayout.atLocal(cells.get(index)).orElseThrow();
            if (!state.partPositions().contains(position) || !level.hasChunkAt(position)
                    || !(level.getBlockEntity(position) instanceof PrecisionAssemblerPortBlockEntity port)
                    || !port.acceptsBindingMutations() || !port.portType().accepts(assignment)) {
                return;
            }
            var binding = port.multiblockBinding();
            if (binding.isPresent() && (!binding.orElseThrow().controllerLevel().equals(expected.controllerLevel())
                    || !binding.orElseThrow().controllerPosition().equals(origin)
                    || !binding.orElseThrow().machineInstanceId().equals(expected.machineInstanceId())
                    || binding.orElseThrow().generation() > expected.generation())) {
                return;
            }
            if (index < PrecisionAssemblerResourcePersistence.ITEM_COUNT
                    && (!ItemStack.matches(controller.storedItemCopy(index), port.legacyStoredItemCopy())
                    || port.migrationMarker().filter(marker -> !marker.machineId().equals(expected.machineInstanceId())
                            || !marker.channel().equals(assignment.channel())).isPresent())) {
                return;
            }
            ports.add(port);
        }
        // Preflight every fixed port before changing any binding. Repeating a
        // partially saved repair is safe; no Item roots or markers are changed.
        ports.forEach(port -> port.setMultiblockBinding(Optional.of(expected)));
    }

    @Override
    public Optional<Set<BlockPos>> discoverBindings(Set<BlockPos> candidates, Set<BlockPos> required) {
        return delegate.discoverBindings(candidates, required);
    }

    @Override
    public boolean bindingsMatch(Set<BlockPos> positions, MultiblockPartBinding binding) {
        return delegate.bindingsMatch(positions, binding);
    }

    @Override
    public BindingMutationResult replaceBindings(
            Set<BlockPos> previousPositions,
            Optional<MultiblockPartBinding> previousBinding,
            Set<BlockPos> candidatePositions,
            MultiblockPartBinding candidateBinding
    ) {
        return BindingMutationResult.CONFLICT;
    }

    @Override
    public int unbindLoaded(Set<BlockPos> positions, MultiblockPartBinding expectedBinding) {
        return 0;
    }
}
