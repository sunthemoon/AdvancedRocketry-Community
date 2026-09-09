package io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.forge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.BindingMutationResult;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.MultiblockPartBinding;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class LoadedPartBindingGatewayTest {
    private static final BlockPos FIRST = new BlockPos(1, 64, 1);
    private static final BlockPos SECOND = new BlockPos(2, 64, 1);
    private static final BlockPos CONTROLLER = new BlockPos(0, 64, 1);

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        MinecraftBootstrap.initialize();
    }

    @Test
    void preflightConflictLeavesEveryBindingAndResourceUnchanged() {
        FakeAccess access = new FakeAccess();
        FakeTarget first = access.add(FIRST, 11);
        FakeTarget second = access.add(SECOND, 22);
        MultiblockPartBinding foreign = binding(UUID.randomUUID(), 1);
        second.binding = Optional.of(foreign);
        LoadedPartBindingGateway gateway = new LoadedPartBindingGateway(access);
        MultiblockPartBinding candidate = binding(UUID.randomUUID(), 1);

        BindingMutationResult result = gateway.replaceBindings(
                Set.of(),
                Optional.empty(),
                orderedParts(),
                candidate
        );

        assertEquals(BindingMutationResult.CONFLICT, result);
        assertTrue(first.binding.isEmpty());
        assertEquals(Optional.of(foreign), second.binding);
        assertEquals(11, first.resource);
        assertEquals(22, second.resource);
    }

    @Test
    void writeFailureRollsBackEveryTouchedTarget() {
        FakeAccess access = new FakeAccess();
        FakeTarget first = access.add(FIRST, 11);
        FakeTarget second = access.add(SECOND, 22);
        second.failNextWrite = true;
        LoadedPartBindingGateway gateway = new LoadedPartBindingGateway(access);

        assertThrows(IllegalStateException.class, () -> gateway.replaceBindings(
                Set.of(),
                Optional.empty(),
                orderedParts(),
                binding(UUID.randomUUID(), 1)
        ));

        assertTrue(first.binding.isEmpty());
        assertTrue(second.binding.isEmpty());
        assertEquals(11, first.resource);
        assertEquals(22, second.resource);
    }

    @Test
    void unloadedPositionsAreNeverResolvedOrMutated() {
        FakeAccess access = new FakeAccess();
        FakeTarget first = access.add(FIRST, 11);
        access.add(SECOND, 22);
        access.loaded.remove(SECOND);
        LoadedPartBindingGateway gateway = new LoadedPartBindingGateway(access);

        BindingMutationResult result = gateway.replaceBindings(
                Set.of(),
                Optional.empty(),
                orderedParts(),
                binding(UUID.randomUUID(), 1)
        );

        assertEquals(BindingMutationResult.CONFLICT, result);
        assertEquals(2, access.loadChecks.get());
        assertEquals(1, access.resolutions.get());
        assertTrue(first.binding.isEmpty());
    }

    @Test
    void discoveryOmitsLoadedPatternCellsWithoutBindingTargets() {
        FakeAccess access = new FakeAccess();
        access.add(FIRST, 11);
        access.loaded.add(SECOND);
        LoadedPartBindingGateway gateway = new LoadedPartBindingGateway(access);

        Optional<Set<BlockPos>> discovered = gateway.discoverBindings(
                orderedParts(),
                Set.of()
        );

        assertEquals(Optional.of(Set.of(FIRST)), discovered);
        assertEquals(2, access.loadChecks.get());
        assertEquals(2, access.resolutions.get());
    }

    @Test
    void discoveryRejectsRequiredPatternCellWithoutBindingTarget() {
        FakeAccess access = new FakeAccess();
        access.loaded.add(FIRST);
        LoadedPartBindingGateway gateway = new LoadedPartBindingGateway(access);

        Optional<Set<BlockPos>> discovered = gateway.discoverBindings(
                Set.of(FIRST),
                Set.of(FIRST)
        );

        assertTrue(discovered.isEmpty());
        assertEquals(1, access.loadChecks.get());
        assertEquals(1, access.resolutions.get());
    }

    @Test
    void discoveryNeverResolvesAnUnloadedPatternCell() {
        FakeAccess access = new FakeAccess();
        LoadedPartBindingGateway gateway = new LoadedPartBindingGateway(access);

        Optional<Set<BlockPos>> discovered = gateway.discoverBindings(
                Set.of(FIRST),
                Set.of()
        );

        assertTrue(discovered.isEmpty());
        assertEquals(1, access.loadChecks.get());
        assertEquals(0, access.resolutions.get());
    }

    @Test
    void matchingLoadedPartsBindAndUnbindWithoutChangingResources() {
        FakeAccess access = new FakeAccess();
        FakeTarget first = access.add(FIRST, 31);
        FakeTarget second = access.add(SECOND, 47);
        LoadedPartBindingGateway gateway = new LoadedPartBindingGateway(access);
        MultiblockPartBinding binding = binding(UUID.randomUUID(), 3);

        assertEquals(BindingMutationResult.APPLIED, gateway.replaceBindings(
                Set.of(),
                Optional.empty(),
                Set.of(FIRST, SECOND),
                binding
        ));
        assertTrue(gateway.bindingsMatch(Set.of(FIRST, SECOND), binding));
        access.loaded.remove(SECOND);
        assertEquals(1, gateway.unbindLoaded(Set.of(FIRST, SECOND), binding));

        assertTrue(first.binding.isEmpty());
        assertEquals(Optional.of(binding), second.binding);
        assertEquals(31, first.resource);
        assertEquals(47, second.resource);
    }

    @Test
    void rejectsControllerIdentityChangesAndControllerAsCandidatePart() {
        FakeAccess access = new FakeAccess();
        access.add(FIRST, 1);
        access.add(CONTROLLER, 2);
        LoadedPartBindingGateway gateway = new LoadedPartBindingGateway(access);
        UUID instance = UUID.randomUUID();
        MultiblockPartBinding previous = binding(instance, 2);

        assertEquals(BindingMutationResult.CONFLICT, gateway.replaceBindings(
                Set.of(FIRST),
                Optional.of(previous),
                Set.of(FIRST),
                binding(UUID.randomUUID(), 3)
        ));
        assertEquals(BindingMutationResult.CONFLICT, gateway.replaceBindings(
                Set.of(),
                Optional.empty(),
                Set.of(CONTROLLER),
                binding(instance, 1)
        ));
        assertTrue(access.targets.values().stream().allMatch(target -> target.binding.isEmpty()));
    }

    private static MultiblockPartBinding binding(UUID instance, long generation) {
        return new MultiblockPartBinding(1, Level.OVERWORLD, CONTROLLER, instance, generation);
    }

    private static Set<BlockPos> orderedParts() {
        return new LinkedHashSet<>(List.of(FIRST, SECOND));
    }

    private static final class FakeAccess implements LoadedPartBindingAccess {
        private final Map<BlockPos, FakeTarget> targets = new HashMap<>();
        private final Set<BlockPos> loaded = new HashSet<>();
        private final AtomicInteger loadChecks = new AtomicInteger();
        private final AtomicInteger resolutions = new AtomicInteger();

        private FakeTarget add(BlockPos position, int resource) {
            FakeTarget target = new FakeTarget(resource);
            targets.put(position, target);
            loaded.add(position);
            return target;
        }

        @Override
        public ResourceKey<Level> levelKey() {
            return Level.OVERWORLD;
        }

        @Override
        public boolean isLoaded(BlockPos position) {
            loadChecks.incrementAndGet();
            return loaded.contains(position);
        }

        @Override
        public Optional<MultiblockPartBindingTarget> loadedTarget(BlockPos position) {
            resolutions.incrementAndGet();
            if (!loaded.contains(position)) {
                throw new AssertionError("resolved an unloaded part");
            }
            return Optional.ofNullable(targets.get(position));
        }
    }

    private static final class FakeTarget implements MultiblockPartBindingTarget {
        private Optional<MultiblockPartBinding> binding = Optional.empty();
        private final int resource;
        private boolean failNextWrite;

        private FakeTarget(int resource) {
            this.resource = resource;
        }

        @Override
        public Optional<MultiblockPartBinding> multiblockBinding() {
            return binding;
        }

        @Override
        public void setMultiblockBinding(Optional<MultiblockPartBinding> binding) {
            if (failNextWrite) {
                failNextWrite = false;
                throw new IllegalStateException("simulated write failure");
            }
            this.binding = binding;
        }
    }
}
