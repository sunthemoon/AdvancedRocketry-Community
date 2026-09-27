package io.github.sunthemoon.advancedrocketrycommunity.compat.rocket.fuel;

import io.github.sunthemoon.advancedrocketrycommunity.api.rocket.RegisterRocketFuelsEvent;
import io.github.sunthemoon.advancedrocketrycommunity.api.rocket.RocketFuelDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.api.rocket.RocketFuelRegistrar;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RocketFuelRegistryTest {
    private static final RocketFuelDefinition VALUE = new RocketFuelDefinition(73, Optional.of(id("remainder")));

    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void valueBoundsNullsAndRecordAccessorsAreExplicit() {
        assertEquals(1, new RocketFuelDefinition(1, Optional.empty()).units());
        assertEquals(2_048_000, new RocketFuelDefinition(2_048_000, Optional.empty()).units());
        assertEquals(VALUE, new RocketFuelDefinition(73, Optional.of(id("remainder"))));
        assertEquals(Optional.of(id("remainder")), VALUE.remainderItem());
        for (long invalid : new long[]{Long.MIN_VALUE, -1, 0, 2_048_001, Long.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> new RocketFuelDefinition(invalid, Optional.empty()));
        }
        assertThrows(NullPointerException.class, () -> new RocketFuelDefinition(1, null));
        assertThrows(IllegalArgumentException.class, () -> new RocketFuelDefinition(1, Optional.of(id("x".repeat(256)))));
        assertThrows(IllegalArgumentException.class, () -> new RocketFuelDefinition(1,
                Optional.of(ResourceLocation.tryParse("minecraft:air"))));
        assertThrows(NullPointerException.class, () -> new RegisterRocketFuelsEvent(null));
    }

    @Test void frozenCatalogReservesBuiltinAndCopiesExternalInputs() {
        var inputs = new HashSet<>(Set.of(id("item0")));
        try (var registry = new RocketFuelRegistry(items(1)::get)) {
            new RegisterRocketFuelsEvent(registry.forOwner("fixture")).register(id("fuel"), inputs, VALUE);
            inputs.clear();
            var catalog = registry.freeze();
            assertEquals(2, catalog.size());
            assertEquals(73, catalog.find(Items.COAL).units());
            assertEquals(id("fuel"), catalog.find(Items.COAL).id());
            assertEquals(Optional.of(Items.STICK), catalog.find(Items.COAL).remainder());
            assertEquals(500, catalog.find(Items.BLAZE_ROD).units());
            assertEquals(Optional.of(Items.BOWL), catalog.find(Items.BLAZE_ROD).remainder());
            assertNull(catalog.find(Items.DIAMOND));
        }
    }

    @Test void invalidClaimsAndUnknownRemaindersDoNotReserveIdsOrItems() {
        var lookup = items(2);
        lookup.put(id("air"), Items.AIR);
        try (var registry = new RocketFuelRegistry(lookup::get)) {
            for (String owner : new String[]{"", "Upper", "bad:owner", "x".repeat(256)}) {
                assertThrows(IllegalArgumentException.class, () -> registry.forOwner(owner));
            }
            assertThrows(NullPointerException.class, () -> registry.forOwner(null));
            var handle = registry.forOwner("fixture");
            for (ResourceLocation item : Set.of(id("absent"), id("air"), id("x".repeat(256)), RocketFuelRegistry.BUILTIN_ID)) {
                assertThrows(IllegalArgumentException.class, () -> handle.register(id("one"), Set.of(item), VALUE));
            }
            assertThrows(IllegalArgumentException.class, () -> handle.register(id("one"), Set.of(), VALUE));
            assertThrows(IllegalArgumentException.class, () -> handle.register(ResourceLocation.tryParse("foreign:one"), Set.of(id("item0")), VALUE));
            assertThrows(IllegalArgumentException.class, () -> handle.register(id("one"), Set.of(id("item0")),
                    new RocketFuelDefinition(1, Optional.of(id("absent")))));
            assertThrows(NullPointerException.class, () -> handle.register(null, Set.of(id("item0")), VALUE));
            assertThrows(NullPointerException.class, () -> handle.register(id("one"), null, VALUE));
            assertThrows(NullPointerException.class, () -> handle.register(id("one"), Set.of(id("item0")), null));
            add(handle, 0);
            assertThrows(IllegalArgumentException.class, () -> handle.register(id("fuel0"), Set.of(id("item1")), VALUE));
            assertThrows(IllegalArgumentException.class, () -> handle.register(id("fuel1"), Set.of(id("item0"), id("item1")), VALUE));
            add(handle, 1);
            assertEquals(3, registry.freeze().size());
        }
    }

    @Test void aliasesCannotBypassReservedOrDuplicateClaims() {
        var lookup = items(1);
        lookup.put(id("alias"), Items.COAL);
        lookup.put(id("builtin_alias"), Items.BLAZE_ROD);
        try (var registry = new RocketFuelRegistry(lookup::get)) {
            var handle = registry.forOwner("fixture");
            assertThrows(IllegalArgumentException.class, () -> handle.register(id("one"), Set.of(id("builtin_alias")), VALUE));
            assertThrows(IllegalArgumentException.class, () -> handle.register(id("one"), Set.of(id("item0"), id("alias")), VALUE));
            add(handle, 0);
            assertEquals(2, registry.freeze().size());
        }
    }

    @Test void expiredAndWrongThreadHandlesCannotMutate() throws InterruptedException {
        try (var registry = new RocketFuelRegistry(items(1)::get)) {
            var stale = registry.forOwner("fixture");
            var live = registry.forOwner("fixture");
            assertThrows(IllegalStateException.class, () -> add(stale, 0));
            var failure = new AtomicReference<Throwable>();
            Thread thread = new Thread(() -> { try { add(live, 0); } catch (Throwable thrown) { failure.set(thrown); } });
            thread.start(); thread.join(5000);
            assertFalse(thread.isAlive()); assertInstanceOf(IllegalStateException.class, failure.get());
            add(live, 0); registry.freeze();
            assertThrows(IllegalStateException.class, () -> add(live, 1));
            assertThrows(IllegalStateException.class, registry::freeze);
            assertThrows(IllegalStateException.class, () -> registry.forOwner("fixture"));
        }
    }

    @Test void reentrantLookupsCannotPublishOrCloseOuterRegistration() {
        for (String action : new String[]{"register", "close", "freeze", "owner"}) {
            var lookup = items(1);
            var reference = new AtomicReference<RocketFuelRegistry>();
            var registrar = new AtomicReference<RocketFuelRegistrar>();
            var fault = new java.util.concurrent.atomic.AtomicBoolean(true);
            try (var registry = new RocketFuelRegistry(id -> {
                if (id.equals(id("item0")) && fault.getAndSet(false)) {
                    switch (action) {
                        case "register" -> add(registrar.get(), 0);
                        case "close" -> reference.get().close();
                        case "freeze" -> reference.get().freeze();
                        case "owner" -> reference.get().forOwner("fixture");
                        default -> throw new AssertionError(action);
                    }
                }
                return lookup.get(id);
            })) {
                reference.set(registry); registrar.set(registry.forOwner("fixture"));
                assertThrows(IllegalStateException.class, () -> add(registrar.get(), 0));
                add(registrar.get(), 0); assertEquals(2, registry.freeze().size());
            }
        }
    }

    @Test void definitionAndItemBudgetsAreIndependentOfBuiltin() {
        try (var registry = new RocketFuelRegistry(items(257)::get)) {
            var handle = registry.forOwner("fixture");
            for (int index = 0; index < 256; index++) { add(handle, index); }
            assertThrows(IllegalArgumentException.class, () -> add(handle, 256));
            assertEquals(257, registry.freeze().size());
        }
        try (var registry = new RocketFuelRegistry(items(1025)::get)) {
            var handle = registry.forOwner("fixture");
            var tooMany = new HashSet<ResourceLocation>();
            for (int index = 0; index < 65; index++) { tooMany.add(id("item" + index)); }
            assertThrows(IllegalArgumentException.class, () -> handle.register(id("large"), tooMany, VALUE));
            for (int batch = 0; batch < 16; batch++) {
                var ids = new HashSet<ResourceLocation>();
                for (int index = 0; index < 64; index++) { ids.add(id("item" + (batch * 64 + index))); }
                handle.register(id("batch" + batch), ids, VALUE);
            }
            assertThrows(IllegalArgumentException.class, () -> add(handle, 1024));
            assertEquals(1025, registry.freeze().size());
        }
    }

    private static Map<ResourceLocation, Item> items(int count) {
        var result = new HashMap<ResourceLocation, Item>();
        result.put(RocketFuelRegistry.BUILTIN_ID, Items.BLAZE_ROD);
        result.put(ResourceLocation.tryParse("advancedrocketrycommunity:empty_canister"), Items.BOWL);
        result.put(id("remainder"), Items.STICK);
        for (int index = 0; index < count; index++) {
            result.put(id("item" + index), index == 0 ? Items.COAL : new Item(new Item.Properties()));
        }
        return result;
    }

    private static void add(RocketFuelRegistrar handle, int index) { handle.register(id("fuel" + index), Set.of(id("item" + index)), VALUE); }
    private static ResourceLocation id(String name) { return ResourceLocation.tryParse("fixture:" + name); }
}
