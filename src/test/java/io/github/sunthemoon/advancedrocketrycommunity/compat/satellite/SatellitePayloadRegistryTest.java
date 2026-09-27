package io.github.sunthemoon.advancedrocketrycommunity.compat.satellite;

import io.github.sunthemoon.advancedrocketrycommunity.api.satellite.RegisterSatellitePayloadsEvent;
import io.github.sunthemoon.advancedrocketrycommunity.api.satellite.SatelliteMissionDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.api.satellite.SatellitePayloadRegistrar;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SatellitePayloadRegistryTest {
    private static final SatelliteMissionDefinition VALUE = new SatelliteMissionDefinition(400, 137, 11, List.of(id("earth")));
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void recordBoundsCopiesAndNullsAreExplicit() {
        var targets = new ArrayList<>(VALUE.allowedTargets());
        var value = new SatelliteMissionDefinition(20, 1, 1, targets);
        targets.clear(); assertEquals(List.of(id("earth")), value.allowedTargets());
        assertThrows(UnsupportedOperationException.class, () -> value.allowedTargets().clear());
        assertEquals(72000, new SatelliteMissionDefinition(72000, 10000, 10000, VALUE.allowedTargets()).durationTicks());
        for (int duration : new int[]{-1, 0, 19, 72001, Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> new SatelliteMissionDefinition(duration, 1, 1, VALUE.allowedTargets()));
        }
        for (int[] pair : List.of(new int[]{0, 1}, new int[]{10001, 1}, new int[]{5, 0}, new int[]{5, 6})) {
            assertThrows(IllegalArgumentException.class, () -> new SatelliteMissionDefinition(20, pair[0], pair[1], VALUE.allowedTargets()));
        }
        assertThrows(NullPointerException.class, () -> new SatelliteMissionDefinition(20, 1, 1, null));
        assertThrows(NullPointerException.class, () -> new SatelliteMissionDefinition(20, 1, 1, java.util.Arrays.asList((ResourceLocation) null)));
        for (var invalid : List.of(List.<ResourceLocation>of(), List.of(id("one"), id("one")), List.of(id("x".repeat(128))),
                java.util.stream.IntStream.range(0, 17).mapToObj(i -> id("body" + i)).toList())) {
            assertThrows(IllegalArgumentException.class, () -> new SatelliteMissionDefinition(20, 1, 1, invalid));
        }
        assertThrows(NullPointerException.class, () -> new RegisterSatellitePayloadsEvent(null));
    }

    @Test void frozenCatalogCopiesDefaultsAndReservesBuiltin() {
        var items = items(1);
        try (var registry = new SatellitePayloadRegistry(items::get)) {
            new RegisterSatellitePayloadsEvent(registry.forOwner("fixture")).register(id("scan"), id("item0"), VALUE);
            var catalog = registry.freeze();
            assertEquals(2, catalog.size());
            assertEquals(id("scan"), catalog.definitionFor(items.get(id("item0"))));
            var definition = catalog.defaults().get(0);
            assertEquals(400, definition.missionDurationTicks());
            assertEquals(137, definition.researchYield());
            assertEquals(11, definition.discoveryCost());
            assertEquals(VALUE.allowedTargets(), definition.allowedTargets());
            assertThrows(UnsupportedOperationException.class, () -> catalog.defaults().clear());
            assertNull(catalog.definitionFor(Items.DIAMOND));
        }
    }

    @Test void invalidRegistrationIsAtomicIncludingAliasesAndOwnership() {
        var items = items(2);
        items.put(id("alias"), items.get(id("item0")));
        items.put(id("builtin_alias"), items.get(host("data_storage_unit")));
        items.put(id("air"), Items.AIR); items.put(id("redstone"), Items.REDSTONE);
        try (var registry = new SatellitePayloadRegistry(items::get)) {
            for (String bad : List.of("", "Upper", "bad:owner", "x".repeat(129))) {
                assertThrows(IllegalArgumentException.class, () -> registry.forOwner(bad));
            }
            assertThrows(NullPointerException.class, () -> registry.forOwner(null));
            var handle = registry.forOwner("fixture");
            assertThrows(NullPointerException.class, () -> handle.register(null, id("item0"), VALUE));
            assertThrows(NullPointerException.class, () -> handle.register(id("one"), null, VALUE));
            assertThrows(NullPointerException.class, () -> handle.register(id("one"), id("item0"), null));
            assertThrows(IllegalArgumentException.class, () -> handle.register(host("one"), id("item0"), VALUE));
            for (ResourceLocation input : List.of(id("air"), id("redstone"), id("absent"), id("builtin_alias"),
                    id("x".repeat(128)), host("satellite_chassis"), host("satellite_solar_module"),
                    host("satellite_control_chip"), host("data_satellite_package"))) {
                assertThrows(IllegalArgumentException.class, () -> handle.register(id("one"), input, VALUE));
            }
            handle.register(id("one"), id("item0"), VALUE);
            assertThrows(IllegalArgumentException.class, () -> handle.register(id("two"), id("alias"), VALUE));
            assertThrows(IllegalArgumentException.class, () -> handle.register(id("one"), id("item1"), VALUE));
            handle.register(id("two"), id("item1"), VALUE);
            assertEquals(3, registry.freeze().size());
        }
    }

    @Test void maximumIsFifteenExternalItems() {
        var items = items(16);
        try (var registry = new SatellitePayloadRegistry(items::get)) {
            var handle = registry.forOwner("fixture");
            for (int i = 0; i < 15; i++) { handle.register(id("def" + i), id("item" + i), VALUE); }
            assertThrows(IllegalArgumentException.class, () -> handle.register(id("last"), id("item15"), VALUE));
            assertEquals(16, registry.freeze().size());
        }
    }

    @Test void expiredOffThreadAndClosedCallsAreRejected() throws InterruptedException {
        try (var registry = new SatellitePayloadRegistry(items(1)::get)) {
            var stale = registry.forOwner("fixture"); var live = registry.forOwner("fixture");
            assertThrows(IllegalStateException.class, () -> stale.register(id("one"), id("item0"), VALUE));
            var failure = new AtomicReference<Throwable>();
            var thread = new Thread(() -> { try { live.register(id("one"), id("item0"), VALUE); }
                catch (Throwable thrown) { failure.set(thrown); } });
            thread.start(); thread.join(5000); assertFalse(thread.isAlive());
            assertInstanceOf(IllegalStateException.class, failure.get());
            live.register(id("one"), id("item0"), VALUE); registry.freeze();
            assertThrows(IllegalStateException.class, () -> live.register(id("two"), id("item0"), VALUE));
            assertThrows(IllegalStateException.class, registry::freeze);
            assertThrows(IllegalStateException.class, () -> registry.forOwner("fixture"));
        }
    }

    @Test void reentrantLookupsCannotClosePublishOrClaimPartialState() {
        for (String action : List.of("register", "owner", "close", "freeze")) {
            var items = items(1); var reference = new AtomicReference<SatellitePayloadRegistry>();
            var handle = new AtomicReference<SatellitePayloadRegistrar>(); var fault = new AtomicBoolean(true);
            try (var registry = new SatellitePayloadRegistry(id -> {
                if (id.equals(id("item0")) && fault.getAndSet(false)) {
                    switch (action) {
                        case "register" -> handle.get().register(id("other"), id, VALUE);
                        case "owner" -> reference.get().forOwner("fixture");
                        case "close" -> reference.get().close();
                        case "freeze" -> reference.get().freeze();
                    }
                }
                return items.get(id);
            })) {
                reference.set(registry); handle.set(registry.forOwner("fixture"));
                assertThrows(IllegalStateException.class, () -> handle.get().register(id("one"), id("item0"), VALUE));
                handle.get().register(id("one"), id("item0"), VALUE); assertEquals(2, registry.freeze().size());
            }
        }
    }

    private static Map<ResourceLocation, Item> items(int count) {
        var result = new HashMap<ResourceLocation, Item>();
        for (String name : List.of("satellite_chassis", "satellite_solar_module", "data_storage_unit", "satellite_control_chip", "data_satellite_package")) {
            result.put(host(name), new Item(new Item.Properties()));
        }
        for (int i = 0; i < count; i++) { result.put(id("item" + i), new Item(new Item.Properties())); }
        return result;
    }
    private static ResourceLocation id(String name) { return ResourceLocation.tryParse("fixture:" + name); }
    private static ResourceLocation host(String name) { return ResourceLocation.tryParse("advancedrocketrycommunity:" + name); }
}
