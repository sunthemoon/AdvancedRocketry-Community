package io.github.sunthemoon.advancedrocketrycommunity.compat.rocket.component;

import io.github.sunthemoon.advancedrocketrycommunity.api.rocket.RegisterRocketComponentsEvent;
import io.github.sunthemoon.advancedrocketrycommunity.api.rocket.RocketComponentDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.api.rocket.RocketComponentRegistrar;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.RocketForgeMetrics;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketBlockMetrics;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RocketComponentRegistryTest {
    private static final RocketComponentDefinition VALUE = new RocketComponentDefinition(37, 2400, 1500, true, true, true);

    @BeforeAll
    static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test
    void freezesForeignBlocksAndCopiesMutableInputForEveryBlockState() {
        ResourceLocation foreign = ResourceLocation.tryParse("minecraft:oak_stairs");
        var registry = new RocketComponentRegistry(Map.of(foreign, Blocks.OAK_STAIRS)::get);
        var blocks = new HashSet<>(Set.of(foreign));
        new RegisterRocketComponentsEvent(registry.forOwner("fixture")).register(id("combined"), blocks, VALUE);
        blocks.clear();
        RocketComponentCatalog catalog = registry.freeze();
        registry.close();
        assertEquals(1, catalog.size());
        for (var state : Blocks.OAK_STAIRS.getStateDefinition().getPossibleStates()) {
            assertEquals(new RocketBlockMetrics(37, 2400, 1500, true, true, true), catalog.find(state));
            assertEquals(catalog.find(state), RocketForgeMetrics.resolve(state, catalog));
        }
        assertNull(catalog.find(Blocks.IRON_BLOCK.defaultBlockState()));
        assertEquals(RocketBlockMetrics.structural(10),
                RocketForgeMetrics.resolve(Blocks.IRON_BLOCK.defaultBlockState(), catalog));
        assertNull(RocketComponentCatalog.empty().find(Blocks.OAK_STAIRS.defaultBlockState()));
        assertThrows(NullPointerException.class, () -> catalog.find(null));
    }

    @Test
    void rejectsInvalidOwnerUnknownBlocksAirReservedIdsAndNulls() {
        Map<ResourceLocation, Block> lookup = Map.of(id("block"), Blocks.IRON_BLOCK,
                id("air"), Blocks.AIR, id("cave"), Blocks.CAVE_AIR, id("void"), Blocks.VOID_AIR);
        try (var registry = new RocketComponentRegistry(lookup::get)) {
            for (String owner : new String[]{"", "bad:owner", "Upper", "x".repeat(256)}) {
                assertThrows(IllegalArgumentException.class, () -> registry.forOwner(owner));
            }
            assertThrows(NullPointerException.class, () -> registry.forOwner(null));
            var handle = registry.forOwner("fixture");
            for (ResourceLocation definition : new ResourceLocation[]{id("x".repeat(256)),
                    ResourceLocation.tryParse("other:definition")}) {
                assertThrows(IllegalArgumentException.class, () -> handle.register(definition, Set.of(id("block")), VALUE));
            }
            Set<ResourceLocation> rejected = new HashSet<>(Set.of(id("missing"), id("air"), id("cave"),
                    id("void"), id("x".repeat(256))));
            for (String name : new String[]{"rocket_motor", "rocket_fuel_tank", "rocket_seat", "guidance_computer"}) {
                rejected.add(ResourceLocation.tryParse("advancedrocketrycommunity:" + name));
            }
            for (ResourceLocation block : rejected) {
                assertThrows(IllegalArgumentException.class, () -> handle.register(id("value"), Set.of(block), VALUE));
            }
            assertThrows(IllegalArgumentException.class, () -> handle.register(id("value"), Set.of(), VALUE));
            assertThrows(NullPointerException.class, () -> handle.register(null, Set.of(id("block")), VALUE));
            assertThrows(NullPointerException.class, () -> handle.register(id("value"), null, VALUE));
            assertThrows(NullPointerException.class, () -> handle.register(id("value"), Set.of(id("block")), null));
            Set<ResourceLocation> withNull = new HashSet<>();
            withNull.add(null);
            assertThrows(NullPointerException.class, () -> handle.register(id("value"), withNull, VALUE));
            add(handle, "value", "block");
            assertEquals(1, registry.freeze().size());
        }
        for (String name : new String[]{"rocket_motor", "rocket_fuel_tank", "rocket_seat", "guidance_computer"}) {
            // A known lookup result must not make a reserved host ID overridable.
            try (var registry = new RocketComponentRegistry(ignored -> Blocks.IRON_BLOCK)) {
                assertThrows(IllegalArgumentException.class, () -> registry.forOwner("fixture").register(id("value"),
                        Set.of(ResourceLocation.tryParse("advancedrocketrycommunity:" + name)), VALUE));
            }
        }
    }

    @Test
    void duplicateAndOverlappingClaimsRejectWithoutReservingAnyOtherBlock() {
        var registry = new RocketComponentRegistry(Map.of(id("a"), Blocks.IRON_BLOCK, id("b"), Blocks.COPPER_BLOCK)::get);
        var handle = registry.forOwner("fixture");
        add(handle, "one", "a");
        assertThrows(IllegalArgumentException.class, () -> add(handle, "one", "b"));
        assertThrows(IllegalArgumentException.class, () -> handle.register(id("two"), Set.of(id("a"), id("b")), VALUE));
        add(handle, "two", "b");
        assertEquals(2, registry.freeze().size());
    }

    @Test
    void aliasesForTheSameResolvedBlockCannotBypassOverlapChecks() {
        try (var registry = new RocketComponentRegistry(ignored -> Blocks.IRON_BLOCK)) {
            var handle = registry.forOwner("fixture");
            assertThrows(IllegalArgumentException.class, () -> handle.register(id("one"), Set.of(id("a"), id("b")), VALUE));
            add(handle, "one", "a");
            assertThrows(IllegalArgumentException.class, () -> add(handle, "two", "b"));
            assertEquals(1, registry.freeze().size());
        }
    }

    @Test
    void loadingHandlesRejectWrongThreadRetainedAndPostFreezeCalls() throws InterruptedException {
        var registry = new RocketComponentRegistry(ignored -> Blocks.IRON_BLOCK);
        var stale = registry.forOwner("fixture");
        var live = registry.forOwner("fixture");
        assertThrows(IllegalStateException.class, () -> add(stale, "one", "a"));
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread worker = new Thread(() -> {
            try { add(live, "one", "a"); } catch (Throwable exception) { failure.set(exception); }
        });
        worker.start();
        worker.join(5000);
        assertFalse(worker.isAlive());
        assertInstanceOf(IllegalStateException.class, failure.get());
        add(live, "one", "a");
        registry.freeze();
        assertThrows(IllegalStateException.class, () -> add(live, "two", "b"));
        assertThrows(IllegalStateException.class, () -> registry.forOwner("fixture"));
        assertThrows(IllegalStateException.class, registry::freeze);
    }

    @Test
    void reentrantRegistrationCloseAndFreezeCannotPublishOrInvalidateOuterState() {
        for (String action : new String[]{"register", "owner", "close", "freeze"}) {
            AtomicReference<RocketComponentRegistry> registryRef = new AtomicReference<>();
            AtomicReference<RocketComponentRegistrar> handleRef = new AtomicReference<>();
            AtomicInteger calls = new AtomicInteger();
            try (var registry = new RocketComponentRegistry(ignored -> {
                if (calls.getAndIncrement() == 0) {
                    switch (action) {
                        case "register" -> add(handleRef.get(), "recursive", "b");
                        case "owner" -> registryRef.get().forOwner("fixture");
                        case "close" -> registryRef.get().close();
                        case "freeze" -> registryRef.get().freeze();
                        default -> throw new AssertionError(action);
                    }
                }
                return Blocks.IRON_BLOCK;
            })) {
                registryRef.set(registry);
                var handle = registry.forOwner("fixture");
                handleRef.set(handle);
                assertThrows(IllegalStateException.class, () -> add(handle, "one", "a"));
                add(handle, "one", "a");
                assertEquals(1, registry.freeze().size());
            }
        }
    }

    @Test
    void limitsPerDefinitionAndTotalBlocksBeforePublishing() {
        Map<ResourceLocation, Block> lookup = blocks(1025);
        try (var registry = new RocketComponentRegistry(lookup::get)) {
            var handle = registry.forOwner("fixture");
            Set<ResourceLocation> tooMany = new HashSet<>();
            for (int index = 0; index < 65; index++) { tooMany.add(id("block" + index)); }
            assertThrows(IllegalArgumentException.class, () -> handle.register(id("large"), tooMany, VALUE));
            for (int batch = 0; batch < 16; batch++) {
                Set<ResourceLocation> entries = new HashSet<>();
                for (int index = 0; index < 64; index++) { entries.add(id("block" + (batch * 64 + index))); }
                handle.register(id("definition" + batch), entries, VALUE);
            }
            assertThrows(IllegalArgumentException.class, () -> add(handle, "overflow", "block1024"));
            assertEquals(1024, registry.freeze().size());
        }
    }

    @Test
    void definitionCountIsBoundedIndependentlyOfBlockCount() {
        Map<ResourceLocation, Block> lookup = blocks(257);
        try (var registry = new RocketComponentRegistry(lookup::get)) {
            var handle = registry.forOwner("fixture");
            for (int index = 0; index < 256; index++) { add(handle, "definition" + index, "block" + index); }
            assertThrows(IllegalArgumentException.class, () -> add(handle, "overflow", "block256"));
            assertEquals(256, registry.freeze().size());
        }
    }

    private static Map<ResourceLocation, Block> blocks(int count) {
        Map<ResourceLocation, Block> result = new HashMap<>();
        for (int index = 0; index < count; index++) {
            result.put(id("block" + index), new Block(BlockBehaviour.Properties.of()));
        }
        return result;
    }

    private static void add(RocketComponentRegistrar handle, String definition, String block) {
        handle.register(id(definition), Set.of(id(block)), VALUE);
    }

    private static ResourceLocation id(String path) { return ResourceLocation.tryParse("fixture:" + path); }
}
