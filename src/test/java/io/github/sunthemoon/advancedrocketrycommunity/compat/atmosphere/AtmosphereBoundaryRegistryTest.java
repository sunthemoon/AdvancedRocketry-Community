package io.github.sunthemoon.advancedrocketrycommunity.compat.atmosphere;

import io.github.sunthemoon.advancedrocketrycommunity.api.atmosphere.AtmosphereBoundary;
import io.github.sunthemoon.advancedrocketrycommunity.api.atmosphere.AtmosphereBoundaryRegistrar;
import io.github.sunthemoon.advancedrocketrycommunity.api.atmosphere.RegisterAtmosphereBoundariesEvent;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AtmosphereBoundaryRegistryTest {
    @BeforeAll
    static void bootstrap() {
        MinecraftBootstrap.initialize();
    }

    @Test
    void compilesEveryStateOnceAndNeverCallsTheProviderAtRuntime() {
        Block block = Blocks.OAK_TRAPDOOR;
        AtomicInteger calls = new AtomicInteger();
        AtmosphereBoundaryRegistry registry = registry(Map.of(id("block"), block));
        registry.forOwner("fixture").register(id("rule"), Set.of(id("block")), state -> {
            calls.incrementAndGet();
            return state.getValue(BlockStateProperties.OPEN)
                    ? AtmosphereBoundary.PERMEABLE : AtmosphereBoundary.SEALED;
        });
        AtmosphereBoundaryCatalog catalog = registry.freeze();
        int count = block.getStateDefinition().getPossibleStates().size();
        assertEquals(count, calls.get());
        assertEquals(count, catalog.stateCount());
        for (int iteration = 0; iteration < 100; iteration++) {
            assertEquals(AtmosphereBoundary.SEALED, catalog.classify(block.defaultBlockState()));
            assertEquals(AtmosphereBoundary.PERMEABLE, catalog.classify(
                    block.defaultBlockState().setValue(BlockStateProperties.OPEN, true)));
        }
        assertEquals(count, calls.get());
        assertEquals(AtmosphereBoundary.DEFAULT, catalog.classify(Blocks.DIRT.defaultBlockState()));
        assertFalse(catalog.isRegistered(Blocks.DIRT.defaultBlockState()));
    }

    @Test
    void registeredDefaultIsDistinctFromAnEmptyCatalog() {
        AtmosphereBoundaryRegistry registry = registry(Map.of(id("block"), Blocks.STONE));
        registry.forOwner("fixture").register(id("rule"), Set.of(id("block")), state -> AtmosphereBoundary.DEFAULT);
        AtmosphereBoundaryCatalog catalog = registry.freeze();
        assertFalse(catalog.isEmpty());
        assertTrue(catalog.isRegistered(Blocks.STONE.defaultBlockState()));
        assertEquals(AtmosphereBoundary.DEFAULT, catalog.classify(Blocks.STONE.defaultBlockState()));
        assertTrue(AtmosphereBoundaryCatalog.empty().isEmpty());
    }

    @Test
    void validatesOwnershipIdsAndKnownBlocksBeforeCallbacks() {
        AtomicInteger calls = new AtomicInteger();
        try (AtmosphereBoundaryRegistry registry = registry(Map.of(id("block"), Blocks.STONE))) {
            assertThrows(IllegalArgumentException.class, () -> registry.forOwner("wrong:owner"));
            AtmosphereBoundaryRegistrar handle = registry.forOwner("fixture");
            for (ResourceLocation provider : List.of(ResourceLocation.tryParse("foreign:rule"), id("a".repeat(256)))) {
                assertThrows(IllegalArgumentException.class, () -> handle.register(provider, Set.of(id("block")), state -> {
                    calls.incrementAndGet();
                    return AtmosphereBoundary.SEALED;
                }));
            }
            assertThrows(IllegalArgumentException.class, () -> handle.register(id("rule"), Set.of(id("missing")),
                    state -> { calls.incrementAndGet(); return AtmosphereBoundary.SEALED; }));
            assertThrows(IllegalArgumentException.class, () -> handle.register(id("rule"), Set.of(),
                    state -> AtmosphereBoundary.SEALED));
            assertThrows(NullPointerException.class, () -> handle.register(null, Set.of(id("block")),
                    state -> AtmosphereBoundary.SEALED));
            assertThrows(NullPointerException.class, () -> handle.register(id("rule"), null,
                    state -> AtmosphereBoundary.SEALED));
            assertThrows(NullPointerException.class, () -> handle.register(id("rule"), Set.of(id("block")), null));
            assertEquals(0, calls.get());
        }
    }

    @Test
    void duplicateIdsAndOverlappingBlocksDoNotStealOtherClaims() {
        AtmosphereBoundaryRegistry registry = registry(Map.of(id("a"), Blocks.STONE, id("b"), Blocks.DIRT));
        AtmosphereBoundaryRegistrar handle = registry.forOwner("fixture");
        handle.register(id("one"), Set.of(id("a")), state -> AtmosphereBoundary.SEALED);
        assertThrows(IllegalArgumentException.class, () -> handle.register(id("one"), Set.of(id("b")),
                state -> AtmosphereBoundary.PERMEABLE));
        assertThrows(IllegalArgumentException.class, () -> handle.register(id("two"), Set.of(id("a"), id("b")),
                state -> AtmosphereBoundary.PERMEABLE));
        handle.register(id("two"), Set.of(id("b")), state -> AtmosphereBoundary.PERMEABLE);
        AtmosphereBoundaryCatalog catalog = registry.freeze();
        assertEquals(2, catalog.stateCount());
        assertEquals(AtmosphereBoundary.SEALED, catalog.classify(Blocks.STONE.defaultBlockState()));
        assertEquals(AtmosphereBoundary.PERMEABLE, catalog.classify(Blocks.DIRT.defaultBlockState()));
    }

    @Test
    void callbackFailureOrNullRejectsTheEntireCompilationAndAllowsRetry() {
        for (boolean throwFailure : List.of(false, true)) {
            AtmosphereBoundaryRegistry registry = registry(Map.of(id("block"), Blocks.OAK_TRAPDOOR));
            AtmosphereBoundaryRegistrar handle = registry.forOwner("fixture");
            AtomicInteger calls = new AtomicInteger();
            assertThrows(IllegalArgumentException.class, () -> handle.register(id("rule"), Set.of(id("block")), state -> {
                if (calls.incrementAndGet() == 2) {
                    if (throwFailure) {
                        throw new IllegalStateException("private provider details");
                    }
                    return null;
                }
                return AtmosphereBoundary.SEALED;
            }));
            handle.register(id("rule"), Set.of(id("block")), state -> AtmosphereBoundary.PERMEABLE);
            AtmosphereBoundaryCatalog catalog = registry.freeze();
            assertEquals(Blocks.OAK_TRAPDOOR.getStateDefinition().getPossibleStates().size(), catalog.stateCount());
            assertEquals(AtmosphereBoundary.PERMEABLE, catalog.classify(Blocks.OAK_TRAPDOOR.defaultBlockState()));
        }
    }

    @Test
    void reentrantRegistrationCannotPublishClaimsOrBypassAtomicity() {
        AtmosphereBoundaryRegistry registry = registry(Map.of(id("block"), Blocks.STONE));
        AtmosphereBoundaryRegistrar handle = registry.forOwner("fixture");
        assertThrows(IllegalArgumentException.class, () -> handle.register(id("outer"), Set.of(id("block")), state -> {
            handle.register(id("nested"), Set.of(id("block")), ignored -> AtmosphereBoundary.PERMEABLE);
            return AtmosphereBoundary.SEALED;
        }));
        handle.register(id("nested"), Set.of(id("block")), state -> AtmosphereBoundary.DEFAULT);
        assertEquals(1, registry.freeze().stateCount());
    }

    @Test
    void staleClosedAndOffThreadHandlesAreRejected() throws InterruptedException {
        AtmosphereBoundaryRegistry registry = registry(Map.of(id("block"), Blocks.STONE));
        AtmosphereBoundaryRegistrar stale = registry.forOwner("fixture");
        AtmosphereBoundaryRegistrar active = registry.forOwner("other");
        assertThrows(IllegalStateException.class, () -> stale.register(id("rule"), Set.of(id("block")),
                state -> AtmosphereBoundary.SEALED));
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread thread = new Thread(() -> {
            try {
                active.register(ResourceLocation.tryParse("other:rule"), Set.of(id("block")),
                        state -> AtmosphereBoundary.SEALED);
            } catch (Throwable exception) {
                failure.set(exception);
            }
        });
        thread.start();
        thread.join();
        assertInstanceOf(IllegalStateException.class, failure.get());
        assertTrue(registry.freeze().isEmpty());
        assertThrows(IllegalStateException.class, registry::freeze);
        assertThrows(IllegalStateException.class, () -> registry.forOwner("fixture"));
        assertThrows(IllegalStateException.class, () -> active.register(ResourceLocation.tryParse("other:rule"),
                Set.of(id("block")), state -> AtmosphereBoundary.SEALED));
        registry.close();
    }

    @Test
    void returnedTimeLimitIsAtomicAndExactBoundaryIsAccepted() {
        AtomicLong time = new AtomicLong();
        AtmosphereBoundaryRegistry registry = new AtmosphereBoundaryRegistry(
                Map.of(id("block"), Blocks.STONE)::get, time::get);
        AtmosphereBoundaryRegistrar handle = registry.forOwner("fixture");
        assertThrows(IllegalArgumentException.class, () -> handle.register(id("rule"), Set.of(id("block")), state -> {
            time.addAndGet(5_000_001L);
            return AtmosphereBoundary.SEALED;
        }));
        handle.register(id("rule"), Set.of(id("block")), state -> {
            time.addAndGet(5_000_000L);
            return AtmosphereBoundary.DEFAULT;
        });
        assertEquals(1, registry.freeze().stateCount());
    }

    @Test
    void cumulativeTimeBudgetRejectsManyIndividuallyValidCallbacks() {
        AtomicLong time = new AtomicLong();
        AtomicInteger calls = new AtomicInteger();
        AtmosphereBoundaryRegistry registry = new AtmosphereBoundaryRegistry(
                Map.of(id("block"), new ManyStatesBlock())::get, time::get);
        AtmosphereBoundaryRegistrar handle = registry.forOwner("fixture");
        assertThrows(IllegalArgumentException.class, () -> handle.register(id("rule"), Set.of(id("block")), state -> {
            time.addAndGet(5_000_000L);
            calls.incrementAndGet();
            return AtmosphereBoundary.SEALED;
        }));
        assertEquals(201, calls.get());
        assertTrue(registry.freeze().isEmpty());
    }

    @Test
    void providerAndPerProviderBlockLimitsCannotBeExceeded() {
        Map<ResourceLocation, Block> blocks = blocks(257);
        AtmosphereBoundaryRegistry registry = registry(blocks);
        AtmosphereBoundaryRegistrar handle = registry.forOwner("fixture");
        assertThrows(IllegalArgumentException.class, () -> handle.register(id("wide"),
                new LinkedHashSet<>(blocks.keySet().stream().limit(65).toList()), state -> AtmosphereBoundary.SEALED));
        for (int index = 0; index < 256; index++) {
            handle.register(id("r" + index), Set.of(id("b" + index)), state -> AtmosphereBoundary.SEALED);
        }
        assertThrows(IllegalArgumentException.class, () -> handle.register(id("r256"), Set.of(id("b256")),
                state -> AtmosphereBoundary.SEALED));
        assertEquals(256, registry.freeze().stateCount());
    }

    @Test
    void totalBlockLimitIsCheckedBeforeEvaluatingTheNextProvider() {
        Map<ResourceLocation, Block> blocks = blocks(1025);
        AtmosphereBoundaryRegistry registry = registry(blocks);
        AtmosphereBoundaryRegistrar handle = registry.forOwner("fixture");
        for (int group = 0; group < 16; group++) {
            Set<ResourceLocation> ids = new LinkedHashSet<>();
            for (int offset = 0; offset < 64; offset++) {
                ids.add(id("b" + (group * 64 + offset)));
            }
            handle.register(id("r" + group), ids, state -> AtmosphereBoundary.SEALED);
        }
        assertThrows(IllegalArgumentException.class, () -> handle.register(id("extra"), Set.of(id("b1024")),
                state -> { fail("No callback after preflight rejection"); return AtmosphereBoundary.DEFAULT; }));
        assertEquals(1024, registry.freeze().stateCount());
    }

    @Test
    void perRegistrationAndTotalStateCapsAreEnforcedBeforeCompilation() {
        Map<ResourceLocation, Block> blocks = new HashMap<>();
        for (int index = 0; index < 5; index++) {
            blocks.put(id("b" + index), new ManyStatesBlock());
        }
        AtmosphereBoundaryRegistry registry = registry(blocks);
        AtmosphereBoundaryRegistrar handle = registry.forOwner("fixture");
        assertThrows(IllegalArgumentException.class, () -> handle.register(id("wide"), Set.of(id("b0"), id("b1")),
                state -> { fail("State cap checked before callbacks"); return AtmosphereBoundary.DEFAULT; }));
        for (int index = 0; index < 4; index++) {
            handle.register(id("r" + index), Set.of(id("b" + index)), state -> AtmosphereBoundary.SEALED);
        }
        assertThrows(IllegalArgumentException.class, () -> handle.register(id("last"), Set.of(id("b4")),
                state -> AtmosphereBoundary.DEFAULT));
        assertEquals(16384, registry.freeze().stateCount());
    }

    @Test
    void eventForwardsOnlyToItsSuppliedHandleInStableBlockOrder() {
        List<Block> seen = new ArrayList<>();
        AtmosphereBoundaryRegistry registry = registry(Map.of(id("a"), Blocks.STONE, id("z"), Blocks.DIRT));
        RegisterAtmosphereBoundariesEvent event = new RegisterAtmosphereBoundariesEvent(registry.forOwner("fixture"));
        event.register(id("rule"), Set.of(id("z"), id("a")), state -> {
            seen.add(state.getBlock());
            return AtmosphereBoundary.DEFAULT;
        });
        assertEquals(List.of(Blocks.STONE, Blocks.DIRT), seen);
        registry.close();
        assertThrows(IllegalStateException.class, () -> event.register(id("late"), Set.of(id("a")),
                state -> AtmosphereBoundary.SEALED));
        assertThrows(NullPointerException.class, () -> new RegisterAtmosphereBoundariesEvent(null));
    }

    private static AtmosphereBoundaryRegistry registry(Map<ResourceLocation, Block> blocks) {
        return new AtmosphereBoundaryRegistry(blocks::get, () -> 0L);
    }

    private static Map<ResourceLocation, Block> blocks(int count) {
        Map<ResourceLocation, Block> result = new HashMap<>();
        for (int index = 0; index < count; index++) {
            result.put(id("b" + index), new Block(BlockBehaviour.Properties.of()));
        }
        return result;
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.tryParse("fixture:" + path);
    }

    private static final class ManyStatesBlock extends Block {
        // Twelve binary properties have 4,096 states without a quadratic 4,096-value neighbor table.
        private static final BooleanProperty[] FLAGS = java.util.stream.IntStream.range(0, 12)
                .mapToObj(index -> BooleanProperty.create("flag" + index)).toArray(BooleanProperty[]::new);

        private ManyStatesBlock() {
            super(Properties.of());
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(FLAGS);
        }
    }
}
