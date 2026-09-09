package io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.MultiblockPatternDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternBlock;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternMatcher;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternObservation;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternPosition;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternRotation;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternSize;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternTransform;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class MultiblockLifecycleCoordinatorTest {
    private static final BlockPos CONTROLLER = new BlockPos(20, 70, 20);
    private static final BlockPos PART = CONTROLLER.east();
    private static final PatternTransform TRANSFORM = new PatternTransform(PatternRotation.ZERO, false);

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        MinecraftBootstrap.initialize();
    }

    @Test
    void formBreakAndRebuildAdvancesExactlyOneGenerationPerFormation() {
        UUID instanceId = UUID.randomUUID();
        MultiblockControllerState state = MultiblockControllerState.initial(instanceId, TRANSFORM);
        InMemoryBindings bindings = new InMemoryBindings();
        Map<PatternPosition, PatternObservation> world = validWorld();

        MultiblockLifecycleResult first = revalidate(state, world, bindings);
        assertEquals(MultiblockFormationState.FORMED, first.controllerState().formationState());
        assertEquals(1, first.controllerState().generation());
        assertEquals(LifecycleMutation.BOUND, first.mutation());
        assertEquals(1, bindings.mutations);
        assertThrows(
                IllegalStateException.class,
                () -> first.controllerState().selectTransform(
                        new PatternTransform(PatternRotation.CLOCKWISE_90, false)
                )
        );

        MultiblockLifecycleResult unchanged = revalidate(first.controllerState(), world, bindings);
        assertEquals(1, unchanged.controllerState().generation());
        assertEquals(LifecycleMutation.NONE, unchanged.mutation());
        assertEquals(1, bindings.mutations);

        world.put(position(PART), loaded("minecraft:air", true, false));
        MultiblockLifecycleResult broken = revalidate(unchanged.controllerState(), world, bindings);
        assertEquals(MultiblockFormationState.UNFORMED, broken.controllerState().formationState());
        assertEquals(1, broken.controllerState().generation());
        assertEquals(LifecycleMutation.UNBOUND, broken.mutation());
        assertTrue(bindings.values.isEmpty());

        world.put(position(PART), loaded("minecraft:iron_block", false, false));
        MultiblockLifecycleResult rebuilt = revalidate(broken.controllerState(), world, bindings);
        assertEquals(MultiblockFormationState.FORMED, rebuilt.controllerState().formationState());
        assertEquals(2, rebuilt.controllerState().generation());
        assertEquals(LifecycleMutation.BOUND, rebuilt.mutation());
    }

    @Test
    void unloadAndReloadRetainsBindingAndGenerationWithoutMutation() {
        InMemoryBindings bindings = new InMemoryBindings();
        Map<PatternPosition, PatternObservation> world = validWorld();
        MultiblockControllerState formed = revalidate(
                MultiblockControllerState.initial(UUID.randomUUID(), TRANSFORM),
                world,
                bindings
        ).controllerState();
        int mutationsAfterFormation = bindings.mutations;

        world.put(position(PART), PatternObservation.unloaded());
        MultiblockLifecycleResult waiting = revalidate(formed, world, bindings);
        assertEquals(MultiblockFormationState.WAITING_UNLOADED, waiting.controllerState().formationState());
        assertEquals(formed.generation(), waiting.controllerState().generation());
        assertEquals(formed.partPositions(), waiting.controllerState().partPositions());
        assertEquals(mutationsAfterFormation, bindings.mutations);

        world.put(position(PART), loaded("minecraft:iron_block", false, false));
        MultiblockLifecycleResult restored = revalidate(waiting.controllerState(), world, bindings);
        assertEquals(MultiblockFormationState.FORMED, restored.controllerState().formationState());
        assertEquals(formed.generation(), restored.controllerState().generation());
        assertEquals(LifecycleMutation.NONE, restored.mutation());
        assertEquals(mutationsAfterFormation, bindings.mutations);
    }

    @Test
    void conflictIsAtomicAndControllerRemovalDoesNotTouchPartResources() {
        InMemoryBindings bindings = new InMemoryBindings();
        Map<BlockPos, Integer> resources = new HashMap<>(Map.of(PART, 42));
        bindings.values.put(PART, new MultiblockPartBinding(
                1,
                Level.OVERWORLD,
                new BlockPos(99, 70, 99),
                UUID.randomUUID(),
                1
        ));
        MultiblockControllerState initial = MultiblockControllerState.initial(UUID.randomUUID(), TRANSFORM);

        MultiblockLifecycleResult conflict = revalidate(initial, validWorld(), bindings);
        assertEquals(MultiblockFormationState.BINDING_CONFLICT, conflict.controllerState().formationState());
        assertEquals(LifecycleMutation.BINDING_CONFLICT, conflict.mutation());
        assertEquals(0, conflict.controllerState().generation());
        assertEquals(42, resources.get(PART));

        bindings.values.clear();
        MultiblockControllerState formed = revalidate(initial, validWorld(), bindings).controllerState();
        assertEquals(1, MultiblockLifecycleCoordinator.controllerRemoved(
                formed,
                Level.OVERWORLD,
                CONTROLLER,
                bindings
        ));
        assertTrue(bindings.values.isEmpty());
        assertEquals(42, resources.get(PART));
    }

    private static MultiblockLifecycleResult revalidate(
            MultiblockControllerState state,
            Map<PatternPosition, PatternObservation> world,
            InMemoryBindings bindings
    ) {
        return MultiblockLifecycleCoordinator.revalidate(
                state,
                definition(),
                Level.OVERWORLD,
                CONTROLLER,
                position -> world.getOrDefault(position, PatternObservation.unloaded()),
                bindings
        );
    }

    private static MultiblockPatternDefinition definition() {
        return new MultiblockPatternDefinition(
                "test:lifecycle",
                1,
                256,
                new PatternSize(2, 1, 1),
                new PatternPosition(0, 0, 0),
                Set.of(PatternRotation.ZERO),
                false,
                Map.of(
                        new PatternPosition(0, 0, 0), new PatternMatcher.Controller(),
                        new PatternPosition(1, 0, 0), new PatternMatcher.ExactBlock("minecraft:iron_block")
                )
        );
    }

    private static Map<PatternPosition, PatternObservation> validWorld() {
        Map<PatternPosition, PatternObservation> world = new HashMap<>();
        world.put(position(CONTROLLER), loaded("test:controller", false, true));
        world.put(position(PART), loaded("minecraft:iron_block", false, false));
        return world;
    }

    private static PatternObservation loaded(String id, boolean air, boolean controller) {
        return PatternObservation.loaded(new PatternBlock(id, Set.of(), air, controller, Optional.empty()));
    }

    private static PatternPosition position(BlockPos position) {
        return new PatternPosition(position.getX(), position.getY(), position.getZ());
    }

    private static final class InMemoryBindings implements MultiblockBindingGateway {
        private final Map<BlockPos, MultiblockPartBinding> values = new HashMap<>();
        private final Set<BlockPos> unloaded = new HashSet<>();
        private int mutations;

        @Override
        public Optional<Set<BlockPos>> discoverBindings(
                Set<BlockPos> candidatePositions,
                Set<BlockPos> requiredPositions
        ) {
            return Optional.of(candidatePositions);
        }

        @Override
        public boolean bindingsMatch(Set<BlockPos> positions, MultiblockPartBinding binding) {
            return positions.stream().allMatch(position -> binding.equals(values.get(position)));
        }

        @Override
        public BindingMutationResult replaceBindings(
                Set<BlockPos> previousPositions,
                Optional<MultiblockPartBinding> previousBinding,
                Set<BlockPos> candidatePositions,
                MultiblockPartBinding candidateBinding
        ) {
            boolean conflict = candidatePositions.stream().anyMatch(position -> {
                MultiblockPartBinding existing = values.get(position);
                return existing != null
                        && !existing.equals(candidateBinding)
                        && previousBinding.filter(existing::equals).isEmpty();
            });
            if (conflict) {
                return BindingMutationResult.CONFLICT;
            }
            previousBinding.ifPresent(expected -> previousPositions.forEach(position -> {
                if (expected.equals(values.get(position))) {
                    values.remove(position);
                }
            }));
            candidatePositions.forEach(position -> values.put(position, candidateBinding));
            mutations++;
            return BindingMutationResult.APPLIED;
        }

        @Override
        public int unbindLoaded(Set<BlockPos> positions, MultiblockPartBinding expectedBinding) {
            int changed = 0;
            for (BlockPos position : positions) {
                if (!unloaded.contains(position) && expectedBinding.equals(values.get(position))) {
                    values.remove(position);
                    changed++;
                }
            }
            mutations += changed > 0 ? 1 : 0;
            return changed;
        }
    }
}
