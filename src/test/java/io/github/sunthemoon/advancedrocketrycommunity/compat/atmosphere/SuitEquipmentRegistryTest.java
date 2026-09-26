package io.github.sunthemoon.advancedrocketrycommunity.compat.atmosphere;

import io.github.sunthemoon.advancedrocketrycommunity.api.atmosphere.RegisterSuitEquipmentEvent;
import io.github.sunthemoon.advancedrocketrycommunity.api.atmosphere.SuitEquipmentRegistrar;
import io.github.sunthemoon.advancedrocketrycommunity.api.atmosphere.SuitOxygenProvider;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.content.SpaceSuitArmorItem;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.HashMap;
import java.util.Map;
import java.util.OptionalInt;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SuitEquipmentRegistryTest {
    private static final SuitOxygenProvider UNUSED = new SuitOxygenProvider() {
        public OptionalInt readOxygen(CompoundTag data) { throw new AssertionError("Loading invoked a provider"); }
        public CompoundTag writeOxygen(CompoundTag data, int units) { throw new AssertionError("Loading invoked a provider"); }
    };

    @BeforeAll
    static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test
    void freezesForeignItemsAndCopiesMutableInputWithoutCallingProviders() {
        Item item = Items.LEATHER_CHESTPLATE;
        ResourceLocation itemId = ResourceLocation.tryParse("minecraft:leather_chestplate");
        var registry = new SuitEquipmentRegistry(Map.of(itemId, item)::get);
        var items = new HashMap<>(Map.of(itemId, EquipmentSlot.CHEST));
        new RegisterSuitEquipmentEvent(registry.forOwner("fixture")).register(id("suit"), items, 2, UNUSED);
        items.clear();
        SuitEquipmentCatalog catalog = registry.freeze();
        assertEquals(1, catalog.size());
        assertEquals(EquipmentSlot.CHEST, catalog.find(item).slot());
        assertEquals(id("suit"), catalog.find(item).provider().id());
        assertEquals(2, catalog.find(item).provider().version());
        assertSame(UNUSED, catalog.find(item).provider().callbacks());
        assertNull(catalog.find(Items.STICK));
        registry.close();
        assertNotNull(catalog.find(item));
    }

    @Test
    void rejectsInvalidOwnedIdsVersionsNullsAndUnknownItems() {
        try (var registry = new SuitEquipmentRegistry(Map.of(id("chest"), Items.LEATHER_CHESTPLATE)::get)) {
            assertThrows(IllegalArgumentException.class, () -> registry.forOwner("invalid:owner"));
            var handle = registry.forOwner("fixture");
            Map<ResourceLocation, EquipmentSlot> items = Map.of(id("chest"), EquipmentSlot.CHEST);
            for (ResourceLocation bad : new ResourceLocation[]{id("x".repeat(256)), ResourceLocation.tryParse("other:suit")}) {
                assertThrows(IllegalArgumentException.class, () -> handle.register(bad, items, 1, UNUSED));
            }
            for (int version : new int[]{0, -1}) {
                assertThrows(IllegalArgumentException.class, () -> handle.register(id("suit"), items, version, UNUSED));
            }
            assertThrows(IllegalArgumentException.class, () -> handle.register(id("suit"), Map.of(), 1, UNUSED));
            assertThrows(IllegalArgumentException.class, () -> handle.register(id("suit"),
                    Map.of(id("absent"), EquipmentSlot.CHEST), 1, UNUSED));
            assertThrows(NullPointerException.class, () -> handle.register(null, items, 1, UNUSED));
            assertThrows(NullPointerException.class, () -> handle.register(id("suit"), null, 1, UNUSED));
            assertThrows(NullPointerException.class, () -> handle.register(id("suit"), items, 1, null));
            handle.register(id("suit"), items, 1, UNUSED);
            assertEquals(1, registry.freeze().size());
        }
    }

    @Test
    void rejectsStackableAirNativeSlotMismatchHandsAndBuiltInSuitClaims() {
        var builtin = new SpaceSuitArmorItem(ArmorMaterials.LEATHER, ArmorItem.Type.CHESTPLATE, new Item.Properties());
        for (Item item : new Item[]{Items.STICK, Items.AIR, Items.LEATHER_HELMET, builtin}) {
            try (var registry = new SuitEquipmentRegistry(ignored -> item)) {
                assertThrows(IllegalArgumentException.class, () -> registry.forOwner("fixture").register(id("suit"),
                        Map.of(id("item"), EquipmentSlot.CHEST), 1, UNUSED));
                assertEquals(0, registry.freeze().size());
            }
        }
        try (var registry = new SuitEquipmentRegistry(ignored -> new Item(new Item.Properties().stacksTo(1)))) {
            assertThrows(IllegalArgumentException.class, () -> registry.forOwner("fixture").register(id("suit"),
                    Map.of(id("item"), EquipmentSlot.MAINHAND), 1, UNUSED));
        }
    }

    @Test
    void duplicateClaimsAreAtomicAndDoNotReserveOtherItems() {
        Map<ResourceLocation, Item> lookup = Map.of(id("a"), Items.LEATHER_HELMET, id("b"), Items.LEATHER_CHESTPLATE);
        var registry = new SuitEquipmentRegistry(lookup::get);
        var handle = registry.forOwner("fixture");
        handle.register(id("one"), Map.of(id("a"), EquipmentSlot.HEAD), 1, UNUSED);
        assertThrows(IllegalArgumentException.class, () -> handle.register(id("one"),
                Map.of(id("b"), EquipmentSlot.CHEST), 1, UNUSED));
        assertThrows(IllegalArgumentException.class, () -> handle.register(id("two"),
                Map.of(id("a"), EquipmentSlot.HEAD, id("b"), EquipmentSlot.CHEST), 1, UNUSED));
        handle.register(id("two"), Map.of(id("b"), EquipmentSlot.CHEST), 1, UNUSED);
        assertEquals(2, registry.freeze().size());
    }

    @Test
    void rejectsRetainedOffThreadAndClosedHandles() throws InterruptedException {
        var registry = new SuitEquipmentRegistry(ignored -> Items.LEATHER_CHESTPLATE);
        var old = registry.forOwner("fixture");
        var active = registry.forOwner("fixture");
        assertThrows(IllegalStateException.class, () -> add(old, "old", "item"));
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread thread = new Thread(() -> {
            try { add(active, "other_thread", "item"); } catch (Throwable exception) { failure.set(exception); }
        });
        thread.start();
        thread.join(5000);
        assertFalse(thread.isAlive());
        assertInstanceOf(IllegalStateException.class, failure.get());
        add(active, "active", "item");
        registry.freeze();
        assertThrows(IllegalStateException.class, () -> add(active, "late", "other"));
        assertThrows(IllegalStateException.class, () -> registry.forOwner("fixture"));
        assertThrows(IllegalStateException.class, registry::freeze);
    }

    @Test
    void rejectsReentrantLookupWithoutPublishingPartialRegistration() {
        AtomicReference<SuitEquipmentRegistry> target = new AtomicReference<>();
        AtomicInteger calls = new AtomicInteger();
        var registry = new SuitEquipmentRegistry(ignored -> {
            if (calls.getAndIncrement() == 0) { target.get().forOwner("fixture"); }
            return Items.LEATHER_CHESTPLATE;
        });
        target.set(registry);
        var handle = registry.forOwner("fixture");
        assertThrows(IllegalStateException.class, () -> add(handle, "suit", "item"));
        add(handle, "suit", "item");
        assertEquals(1, registry.freeze().size());
    }

    @Test
    void enforcesPerRegistrationAndTotalItemLimits() {
        Map<ResourceLocation, Item> lookup = new HashMap<>();
        for (int index = 0; index <= 1024; index++) {
            lookup.put(id("item" + index), new Item(new Item.Properties().stacksTo(1)));
        }
        var registry = new SuitEquipmentRegistry(lookup::get);
        var handle = registry.forOwner("fixture");
        Map<ResourceLocation, EquipmentSlot> tooMany = new HashMap<>();
        for (int index = 0; index < 65; index++) { tooMany.put(id("item" + index), EquipmentSlot.CHEST); }
        assertThrows(IllegalArgumentException.class, () -> handle.register(id("too_many"), tooMany, 1, UNUSED));
        for (int batch = 0; batch < 16; batch++) {
            Map<ResourceLocation, EquipmentSlot> items = new HashMap<>();
            for (int index = 0; index < 64; index++) { items.put(id("item" + (64 * batch + index)), EquipmentSlot.CHEST); }
            handle.register(id("suit" + batch), items, 1, UNUSED);
        }
        assertThrows(IllegalArgumentException.class, () -> add(handle, "overflow", "item1024"));
        assertEquals(1024, registry.freeze().size());
    }

    @Test
    void enforcesProviderLimitIndependentlyOfItemLimit() {
        Map<ResourceLocation, Item> lookup = new HashMap<>();
        for (int index = 0; index <= 256; index++) {
            lookup.put(id("item" + index), new Item(new Item.Properties().stacksTo(1)));
        }
        var registry = new SuitEquipmentRegistry(lookup::get);
        var handle = registry.forOwner("fixture");
        for (int index = 0; index < 256; index++) { add(handle, "suit" + index, "item" + index); }
        assertThrows(IllegalArgumentException.class, () -> add(handle, "overflow", "item256"));
        assertEquals(256, registry.freeze().size());
    }

    private static void add(SuitEquipmentRegistrar registrar, String provider, String item) {
        registrar.register(id(provider), Map.of(id(item), EquipmentSlot.CHEST), 1, UNUSED);
    }

    private static ResourceLocation id(String path) { return ResourceLocation.tryParse("fixture:" + path); }
}
