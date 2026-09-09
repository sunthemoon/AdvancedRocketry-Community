package io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle;

import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.MultiblockPatternDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.MultiblockPatternValidator;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternMatcher;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternPosition;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternValidationResult;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternValidationStatus;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternWorldView;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/** Server-authoritative formation transition coordinator. */
public final class MultiblockLifecycleCoordinator {
    private MultiblockLifecycleCoordinator() {
    }

    public static MultiblockLifecycleResult revalidate(
            MultiblockControllerState current,
            MultiblockPatternDefinition definition,
            ResourceKey<Level> controllerLevel,
            BlockPos controllerPosition,
            PatternWorldView world,
            MultiblockBindingGateway bindings
    ) {
        PatternPosition controllerWorld = position(controllerPosition);
        PatternValidationResult validation = MultiblockPatternValidator.validate(
                definition,
                current.selectedTransform(),
                controllerWorld,
                world
        );
        if (validation.status() == PatternValidationStatus.WAITING_UNLOADED) {
            return result(
                    current.withState(MultiblockFormationState.WAITING_UNLOADED, current.partPositions()),
                    validation,
                    LifecycleMutation.NONE,
                    0
            );
        }
        if (!validation.formed()) {
            MultiblockFormationState nextState = validation.status() == PatternValidationStatus.MISMATCH
                    ? MultiblockFormationState.UNFORMED
                    : MultiblockFormationState.INVALID_DEFINITION;
            if (current.partPositions().isEmpty() || current.generation() == 0) {
                return result(current.withState(nextState, Set.of()), validation, LifecycleMutation.NONE, 0);
            }
            MultiblockPartBinding expected = binding(current, controllerLevel, controllerPosition);
            int changed = bindings.unbindLoaded(current.partPositions(), expected);
            return result(
                    current.withState(nextState, Set.of()),
                    validation,
                    changed == 0 ? LifecycleMutation.NONE : LifecycleMutation.UNBOUND,
                    changed
            );
        }

        BindingCandidates candidates = bindingCandidates(definition, current, controllerPosition);
        Optional<Set<BlockPos>> discovered = bindings.discoverBindings(
                candidates.allPositions(),
                candidates.requiredPositions()
        );
        if (discovered.isEmpty()) {
            return result(
                    current.withState(MultiblockFormationState.BINDING_CONFLICT, current.partPositions()),
                    validation,
                    LifecycleMutation.BINDING_CONFLICT,
                    0
            );
        }
        Set<BlockPos> candidateParts = discovered.get();
        if (current.generation() > 0 && current.partPositions().equals(candidateParts)) {
            MultiblockPartBinding existing = binding(current, controllerLevel, controllerPosition);
            if (bindings.bindingsMatch(candidateParts, existing)) {
                return result(
                        current.withState(MultiblockFormationState.FORMED, candidateParts),
                        validation,
                        LifecycleMutation.NONE,
                        0
                );
            }
        }
        if (current.generation() == Long.MAX_VALUE) {
            return result(
                    current.withState(MultiblockFormationState.BINDING_CONFLICT, current.partPositions()),
                    validation,
                    LifecycleMutation.GENERATION_EXHAUSTED,
                    0
            );
        }

        long nextGeneration = current.generation() + 1;
        MultiblockPartBinding candidate = new MultiblockPartBinding(
                MultiblockPartBinding.SCHEMA_VERSION,
                controllerLevel,
                controllerPosition,
                current.machineInstanceId(),
                nextGeneration
        );
        Optional<MultiblockPartBinding> previous = current.generation() == 0
                ? Optional.empty()
                : Optional.of(binding(current, controllerLevel, controllerPosition));
        BindingMutationResult mutation = bindings.replaceBindings(
                current.partPositions(),
                previous,
                candidateParts,
                candidate
        );
        if (mutation == BindingMutationResult.CONFLICT) {
            return result(
                    current.withState(MultiblockFormationState.BINDING_CONFLICT, current.partPositions()),
                    validation,
                    LifecycleMutation.BINDING_CONFLICT,
                    0
            );
        }
        return result(
                current.formed(nextGeneration, candidateParts),
                validation,
                LifecycleMutation.BOUND,
                candidateParts.size()
        );
    }

    public static int controllerRemoved(
            MultiblockControllerState current,
            ResourceKey<Level> controllerLevel,
            BlockPos controllerPosition,
            MultiblockBindingGateway bindings
    ) {
        if (current.generation() == 0 || current.partPositions().isEmpty()) {
            return 0;
        }
        return bindings.unbindLoaded(
                current.partPositions(),
                binding(current, controllerLevel, controllerPosition)
        );
    }

    private static BindingCandidates bindingCandidates(
            MultiblockPatternDefinition definition,
            MultiblockControllerState state,
            BlockPos controllerPosition
    ) {
        PatternPosition controllerWorld = position(controllerPosition);
        Set<BlockPos> all = new LinkedHashSet<>();
        Set<BlockPos> required = new LinkedHashSet<>();
        definition.cells().forEach((local, matcher) -> {
            if (local.equals(definition.controllerAnchor()) || matcher instanceof PatternMatcher.Air) {
                return;
            }
            BlockPos world = blockPosition(state.selectedTransform().localToWorld(
                    local,
                    definition.controllerAnchor(),
                    controllerWorld
            ));
            all.add(world);
            if (isPort(matcher)) {
                required.add(world);
            }
        });
        return new BindingCandidates(Set.copyOf(all), Set.copyOf(required));
    }

    private static MultiblockPartBinding binding(
            MultiblockControllerState state,
            ResourceKey<Level> level,
            BlockPos position
    ) {
        return new MultiblockPartBinding(
                MultiblockPartBinding.SCHEMA_VERSION,
                level,
                position,
                state.machineInstanceId(),
                state.generation()
        );
    }

    private static MultiblockLifecycleResult result(
            MultiblockControllerState state,
            PatternValidationResult validation,
            LifecycleMutation mutation,
            int changedBindings
    ) {
        return new MultiblockLifecycleResult(state, validation, mutation, changedBindings);
    }

    private static PatternPosition position(BlockPos position) {
        return new PatternPosition(position.getX(), position.getY(), position.getZ());
    }

    private static BlockPos blockPosition(PatternPosition position) {
        return new BlockPos(position.x(), position.y(), position.z());
    }

    private static boolean isPort(PatternMatcher matcher) {
        return matcher instanceof PatternMatcher.Port
                || (matcher instanceof PatternMatcher.OptionalCell optional
                && optional.matcher() instanceof PatternMatcher.Port);
    }

    private record BindingCandidates(Set<BlockPos> allPositions, Set<BlockPos> requiredPositions) {
    }
}
