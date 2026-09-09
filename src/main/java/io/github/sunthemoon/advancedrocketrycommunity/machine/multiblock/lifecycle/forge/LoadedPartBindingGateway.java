package io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.forge;

import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.BindingMutationResult;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.MultiblockBindingGateway;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.MultiblockControllerState;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.MultiblockPartBinding;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;

/** Loaded-only, preflighted binding transaction for part BlockEntities. */
public final class LoadedPartBindingGateway implements MultiblockBindingGateway {
    private final LoadedPartBindingAccess access;

    public LoadedPartBindingGateway(LoadedPartBindingAccess access) {
        this.access = Objects.requireNonNull(access, "access");
    }

    @Override
    public Optional<Set<BlockPos>> discoverBindings(
            Set<BlockPos> candidatePositions,
            Set<BlockPos> requiredPositions
    ) {
        requireBounded(candidatePositions);
        requireBounded(requiredPositions);
        if (!candidatePositions.containsAll(requiredPositions)) {
            throw new IllegalArgumentException("required binding positions must be pattern candidates");
        }
        Set<BlockPos> discovered = new LinkedHashSet<>();
        for (BlockPos position : candidatePositions) {
            if (!access.isLoaded(position)) {
                return Optional.empty();
            }
            Optional<MultiblockPartBindingTarget> target = access.loadedTarget(position);
            if (target.isPresent()) {
                discovered.add(position.immutable());
            } else if (requiredPositions.contains(position)) {
                return Optional.empty();
            }
        }
        return Optional.of(Set.copyOf(discovered));
    }

    @Override
    public boolean bindingsMatch(Set<BlockPos> positions, MultiblockPartBinding binding) {
        requireBounded(positions);
        if (!binding.controllerLevel().equals(access.levelKey())) {
            return false;
        }
        for (BlockPos position : positions) {
            if (!access.isLoaded(position)) {
                return false;
            }
            Optional<MultiblockPartBindingTarget> target = access.loadedTarget(position);
            if (target.isEmpty() || !target.get().multiblockBinding().filter(binding::equals).isPresent()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public BindingMutationResult replaceBindings(
            Set<BlockPos> previousPositions,
            Optional<MultiblockPartBinding> previousBinding,
            Set<BlockPos> candidatePositions,
            MultiblockPartBinding candidateBinding
    ) {
        requireBounded(previousPositions);
        requireBounded(candidatePositions);
        Objects.requireNonNull(previousBinding, "previousBinding");
        Objects.requireNonNull(candidateBinding, "candidateBinding");
        if (!candidateBinding.controllerLevel().equals(access.levelKey())
                || candidatePositions.contains(candidateBinding.controllerPosition())
                || previousBinding.filter(binding -> !isDirectSuccessor(binding, candidateBinding)).isPresent()) {
            return BindingMutationResult.CONFLICT;
        }

        LinkedHashMap<BlockPos, MultiblockPartBindingTarget> targets = new LinkedHashMap<>();
        for (BlockPos position : candidatePositions) {
            if (!access.isLoaded(position)) {
                return BindingMutationResult.CONFLICT;
            }
            Optional<MultiblockPartBindingTarget> target = access.loadedTarget(position);
            if (target.isEmpty() || conflicts(target.get().multiblockBinding(), previousBinding, candidateBinding)) {
                return BindingMutationResult.CONFLICT;
            }
            targets.put(position.immutable(), target.get());
        }
        for (BlockPos position : previousPositions) {
            if (access.isLoaded(position)) {
                access.loadedTarget(position).ifPresent(target -> targets.putIfAbsent(position.immutable(), target));
            }
        }

        Map<BlockPos, Optional<MultiblockPartBinding>> before = new LinkedHashMap<>();
        targets.forEach((position, target) -> before.put(position, target.multiblockBinding()));
        try {
            previousBinding.ifPresent(expected -> previousPositions.forEach(position -> {
                MultiblockPartBindingTarget target = targets.get(position);
                if (target != null && target.multiblockBinding().filter(expected::equals).isPresent()) {
                    target.setMultiblockBinding(Optional.empty());
                }
            }));
            candidatePositions.forEach(position -> targets.get(position)
                    .setMultiblockBinding(Optional.of(candidateBinding)));
        } catch (RuntimeException exception) {
            rollback(targets, before, exception);
            throw exception;
        }
        return BindingMutationResult.APPLIED;
    }

    @Override
    public int unbindLoaded(Set<BlockPos> positions, MultiblockPartBinding expectedBinding) {
        requireBounded(positions);
        Objects.requireNonNull(expectedBinding, "expectedBinding");
        if (!expectedBinding.controllerLevel().equals(access.levelKey())) {
            return 0;
        }
        int changed = 0;
        for (BlockPos position : positions) {
            if (!access.isLoaded(position)) {
                continue;
            }
            Optional<MultiblockPartBindingTarget> target = access.loadedTarget(position);
            if (target.isPresent()
                    && target.get().multiblockBinding().filter(expectedBinding::equals).isPresent()) {
                target.get().setMultiblockBinding(Optional.empty());
                changed++;
            }
        }
        return changed;
    }

    private static boolean conflicts(
            Optional<MultiblockPartBinding> existing,
            Optional<MultiblockPartBinding> previous,
            MultiblockPartBinding candidate
    ) {
        return existing.isPresent()
                && !existing.filter(candidate::equals).isPresent()
                && previous.filter(existing.get()::equals).isEmpty();
    }

    private static boolean isDirectSuccessor(
            MultiblockPartBinding previous,
            MultiblockPartBinding candidate
    ) {
        return previous.controllerLevel().equals(candidate.controllerLevel())
                && previous.controllerPosition().equals(candidate.controllerPosition())
                && previous.machineInstanceId().equals(candidate.machineInstanceId())
                && previous.generation() < Long.MAX_VALUE
                && candidate.generation() == previous.generation() + 1;
    }

    private static void rollback(
            Map<BlockPos, MultiblockPartBindingTarget> targets,
            Map<BlockPos, Optional<MultiblockPartBinding>> before,
            RuntimeException original
    ) {
        targets.forEach((position, target) -> {
            try {
                target.setMultiblockBinding(before.get(position));
            } catch (RuntimeException rollbackFailure) {
                original.addSuppressed(rollbackFailure);
            }
        });
    }

    private static void requireBounded(Set<BlockPos> positions) {
        Objects.requireNonNull(positions, "positions");
        if (positions.size() > MultiblockControllerState.MAX_PARTS
                || positions.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("binding position set exceeds its hard limit");
        }
    }
}
