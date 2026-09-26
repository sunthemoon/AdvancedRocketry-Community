package io.github.sunthemoon.advancedrocketrycommunity.compat.rocket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.api.rocket.RocketAdapterRegistrar;
import io.github.sunthemoon.advancedrocketrycommunity.api.rocket.RocketBlockEntityAdapter;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.RocketBlockEntityAdapters;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.VanillaContainerRocketAdapter;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlockEntityPayload;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.junit.jupiter.api.Test;

final class RocketAdapterRegistryTest {
    private static final String OWNER = "fixture";
    private static final ResourceLocation ADAPTER = id("fixture:container");
    private static final ResourceLocation TYPE = id("blocks:container");
    private static final RocketBlockEntityAdapter UNUSED_CALLBACKS = new RocketBlockEntityAdapter() {
        @Override
        public boolean canMove(BlockEntity blockEntity) {
            throw new AssertionError("Registration and availability must not invoke providers");
        }

        @Override
        public CompoundTag capture(BlockEntity blockEntity) {
            throw new AssertionError("Registration and availability must not invoke providers");
        }

        @Override
        public boolean restore(BlockEntity blockEntity, CompoundTag body) {
            throw new AssertionError("Registration and availability must not invoke providers");
        }
    };

    @Test
    void emptyCatalogRetainsLegacyPayloadWithoutAnExternalEnvelope() {
        RocketBlockEntityAdapters adapters = registry().freeze();
        assertTrue(adapters.supportsPayload(new RocketBlockEntityPayload(
                VanillaContainerRocketAdapter.ID, new CompoundTag())));
        assertFalse(adapters.supportsPayload(payload(ADAPTER, 1)));
    }

    @Test
    void registrationAcceptsOwnedIdAndKnownForeignTypeWithoutCallingProvider() {
        RocketAdapterRegistry registry = new RocketAdapterRegistry(TYPE::equals);
        registry.forOwner(OWNER).register(ADAPTER, Set.of(TYPE), 7, UNUSED_CALLBACKS);
        RocketBlockEntityAdapters adapters = registry.freeze();
        assertTrue(adapters.supportsPayload(payload(ADAPTER, 7)));
        assertFalse(adapters.supportsPayload(payload(ADAPTER, 6)));
        assertFalse(adapters.supportsPayload(payload(ADAPTER, 8)));
        assertFalse(adapters.supportsPayload(new RocketBlockEntityPayload(ADAPTER, new CompoundTag())));
    }

    @Test
    void ownerCannotClaimAnotherNamespaceOrTheReservedLegacyAdapter() {
        RocketAdapterRegistry registry = registry();
        RocketAdapterRegistrar registrar = registry.forOwner(OWNER);
        assertThrows(IllegalArgumentException.class,
                () -> registrar.register(id("other:container"), Set.of(TYPE), 1, UNUSED_CALLBACKS));
        registrar.register(ADAPTER, Set.of(TYPE), 1, UNUSED_CALLBACKS);
        RocketAdapterRegistrar host = registry.forOwner(VanillaContainerRocketAdapter.ID.getNamespace());
        assertThrows(IllegalArgumentException.class,
                () -> host.register(VanillaContainerRocketAdapter.ID,
                        Set.of(id("blocks:other")), 1, UNUSED_CALLBACKS));
        assertTrue(registry.freeze().supportsPayload(payload(ADAPTER, 1)));
    }

    @Test
    void vanillaChestAndBarrelTypesCannotBeReassigned() {
        for (String type : new String[] {"minecraft:chest", "minecraft:barrel"}) {
            RocketAdapterRegistry registry = registry();
            RocketAdapterRegistrar registrar = registry.forOwner(OWNER);
            assertThrows(IllegalArgumentException.class,
                    () -> registrar.register(ADAPTER, Set.of(id(type)), 1, UNUSED_CALLBACKS));
            registrar.register(ADAPTER, Set.of(TYPE), 1, UNUSED_CALLBACKS);
            assertTrue(registry.freeze().supportsPayload(payload(ADAPTER, 1)));
        }
    }

    @Test
    void duplicateAdapterAndTypeRejectionPreservesTheAcceptedCatalog() {
        RocketAdapterRegistry registry = registry();
        RocketAdapterRegistrar registrar = registry.forOwner(OWNER);
        ResourceLocation other = id("fixture:other");
        ResourceLocation otherType = id("blocks:other");
        registrar.register(ADAPTER, Set.of(TYPE), 1, UNUSED_CALLBACKS);
        assertThrows(IllegalArgumentException.class,
                () -> registrar.register(ADAPTER, Set.of(otherType), 2, UNUSED_CALLBACKS));
        assertThrows(IllegalArgumentException.class,
                () -> registrar.register(other, Set.of(TYPE), 1, UNUSED_CALLBACKS));
        registrar.register(other, Set.of(otherType), 3, UNUSED_CALLBACKS);
        RocketBlockEntityAdapters adapters = registry.freeze();
        assertTrue(adapters.supportsPayload(payload(ADAPTER, 1)));
        assertFalse(adapters.supportsPayload(payload(ADAPTER, 2)));
        assertTrue(adapters.supportsPayload(payload(other, 3)));
    }

    @Test
    void unknownTypeInRegistrationDoesNotReserveIdOrOtherTypes() {
        ResourceLocation unknown = id("blocks:unknown");
        RocketAdapterRegistry registry = new RocketAdapterRegistry(type -> !unknown.equals(type));
        RocketAdapterRegistrar registrar = registry.forOwner(OWNER);
        LinkedHashSet<ResourceLocation> types = new LinkedHashSet<>();
        types.add(TYPE);
        types.add(unknown);
        assertThrows(IllegalArgumentException.class,
                () -> registrar.register(ADAPTER, types, 1, UNUSED_CALLBACKS));
        registrar.register(ADAPTER, Set.of(TYPE), 1, UNUSED_CALLBACKS);
        assertTrue(registry.freeze().supportsPayload(payload(ADAPTER, 1)));
    }

    @Test
    void conflictingTypeDoesNotConsumeOtherMappingOrAdapterId() {
        RocketAdapterRegistry registry = registry();
        RocketAdapterRegistrar registrar = registry.forOwner(OWNER);
        registrar.register(ADAPTER, Set.of(TYPE), 1, UNUSED_CALLBACKS);
        ResourceLocation candidate = id("fixture:candidate");
        ResourceLocation freeType = id("blocks:free");
        LinkedHashSet<ResourceLocation> types = new LinkedHashSet<>();
        types.add(freeType);
        types.add(TYPE);
        assertThrows(IllegalArgumentException.class,
                () -> registrar.register(candidate, types, 1, UNUSED_CALLBACKS));
        registrar.register(candidate, Set.of(freeType), 1, UNUSED_CALLBACKS);
        assertTrue(registry.freeze().supportsPayload(payload(candidate, 1)));
    }

    @Test
    void knownTypeLookupExceptionDoesNotPartiallyReserveRegistration() {
        AtomicBoolean failLookup = new AtomicBoolean(true);
        RocketAdapterRegistry registry = new RocketAdapterRegistry(type -> {
            if (failLookup.get()) {
                throw new IllegalStateException("Injected registry lookup failure");
            }
            return true;
        });
        RocketAdapterRegistrar registrar = registry.forOwner(OWNER);
        assertThrows(IllegalStateException.class,
                () -> registrar.register(ADAPTER, Set.of(TYPE), 1, UNUSED_CALLBACKS));
        failLookup.set(false);
        registrar.register(ADAPTER, Set.of(TYPE), 1, UNUSED_CALLBACKS);
        assertTrue(registry.freeze().supportsPayload(payload(ADAPTER, 1)));
    }

    @Test
    void typeOwnershipIsExclusiveAcrossDifferentModOwners() {
        RocketAdapterRegistry registry = registry();
        registry.forOwner(OWNER).register(ADAPTER, Set.of(TYPE), 1, UNUSED_CALLBACKS);
        RocketAdapterRegistrar other = registry.forOwner("other");
        ResourceLocation otherId = id("other:container");
        assertThrows(IllegalArgumentException.class,
                () -> other.register(otherId, Set.of(TYPE), 1, UNUSED_CALLBACKS));
        other.register(otherId, Set.of(id("blocks:other")), 1, UNUSED_CALLBACKS);
        RocketBlockEntityAdapters adapters = registry.freeze();
        assertTrue(adapters.supportsPayload(payload(ADAPTER, 1)));
        assertTrue(adapters.supportsPayload(payload(otherId, 1)));
    }

    @Test
    void mutatingCallerTypeSetCannotReleaseOrAddReservations() {
        RocketAdapterRegistry registry = registry();
        RocketAdapterRegistrar registrar = registry.forOwner(OWNER);
        Set<ResourceLocation> types = new HashSet<>(Set.of(TYPE));
        registrar.register(ADAPTER, types, 1, UNUSED_CALLBACKS);
        ResourceLocation laterType = id("blocks:later");
        types.clear();
        types.add(laterType);
        assertThrows(IllegalArgumentException.class, () -> registrar.register(
                id("fixture:duplicate"), Set.of(TYPE), 1, UNUSED_CALLBACKS));
        registrar.register(id("fixture:later"), Set.of(laterType), 1, UNUSED_CALLBACKS);
        RocketBlockEntityAdapters adapters = registry.freeze();
        assertTrue(adapters.supportsPayload(payload(ADAPTER, 1)));
        assertFalse(adapters.supportsPayload(payload(id("fixture:duplicate"), 1)));
    }

    @Test
    void invalidArgumentsDoNotConsumeTheRegistration() {
        RocketAdapterRegistry registry = registry();
        RocketAdapterRegistrar registrar = registry.forOwner(OWNER);
        assertThrows(RuntimeException.class, () -> registrar.register(null, Set.of(TYPE), 1, UNUSED_CALLBACKS));
        assertThrows(RuntimeException.class, () -> registrar.register(ADAPTER, null, 1, UNUSED_CALLBACKS));
        assertThrows(IllegalArgumentException.class, () -> registrar.register(ADAPTER, Set.of(), 1, UNUSED_CALLBACKS));
        assertThrows(RuntimeException.class, () -> registrar.register(ADAPTER, Set.of(TYPE), 1, null));
        Set<ResourceLocation> withNull = new HashSet<>(Set.of(TYPE));
        withNull.add(null);
        assertThrows(RuntimeException.class,
                () -> registrar.register(ADAPTER, withNull, 1, UNUSED_CALLBACKS));
        for (int version : new int[] {0, -1, Integer.MIN_VALUE}) {
            assertThrows(IllegalArgumentException.class,
                    () -> registrar.register(ADAPTER, Set.of(TYPE), version, UNUSED_CALLBACKS));
        }
        registrar.register(ADAPTER, Set.of(TYPE), Integer.MAX_VALUE, UNUSED_CALLBACKS);
        assertTrue(registry.freeze().supportsPayload(payload(ADAPTER, Integer.MAX_VALUE)));
    }

    @Test
    void identifierLimitAccepts255AndRejects256Characters() {
        RocketAdapterRegistry registry = registry();
        RocketAdapterRegistrar registrar = registry.forOwner(OWNER);
        ResourceLocation longest = id("fixture:" + "a".repeat(247));
        ResourceLocation tooLong = id("fixture:" + "a".repeat(248));
        ResourceLocation longestType = id("blocks:" + "b".repeat(248));
        ResourceLocation tooLongType = id("blocks:" + "b".repeat(249));
        assertEquals(255, longest.toString().length());
        assertEquals(256, tooLong.toString().length());
        assertEquals(255, longestType.toString().length());
        assertEquals(256, tooLongType.toString().length());
        assertThrows(IllegalArgumentException.class,
                () -> registrar.register(tooLong, Set.of(TYPE), 1, UNUSED_CALLBACKS));
        assertThrows(IllegalArgumentException.class,
                () -> registrar.register(longest, Set.of(tooLongType), 1, UNUSED_CALLBACKS));
        registrar.register(longest, Set.of(longestType), 1, UNUSED_CALLBACKS);
        assertTrue(registry.freeze().supportsPayload(payload(longest, 1)));
    }

    @Test
    void nextOwnerClosesPreviousHandleEvenWhenTheNamespaceIsTheSame() {
        RocketAdapterRegistry registry = registry();
        RocketAdapterRegistrar first = registry.forOwner(OWNER);
        RocketAdapterRegistrar second = registry.forOwner(OWNER);
        assertThrows(IllegalStateException.class,
                () -> first.register(ADAPTER, Set.of(TYPE), 1, UNUSED_CALLBACKS));
        second.register(ADAPTER, Set.of(TYPE), 1, UNUSED_CALLBACKS);
        RocketAdapterRegistrar other = registry.forOwner("other");
        assertThrows(IllegalStateException.class,
                () -> second.register(id("fixture:late"), Set.of(id("blocks:late")), 1, UNUSED_CALLBACKS));
        other.register(id("other:container"), Set.of(id("blocks:other")), 1, UNUSED_CALLBACKS);
        assertTrue(registry.freeze().supportsPayload(payload(ADAPTER, 1)));
    }

    @Test
    void invalidOwnerDoesNotReplaceAnExistingValidHandle() {
        RocketAdapterRegistry registry = registry();
        RocketAdapterRegistrar registrar = registry.forOwner(OWNER);
        for (String owner : new String[] {"", "Uppercase", "has:colon", "has space", "a".repeat(256)}) {
            assertThrows(IllegalArgumentException.class, () -> registry.forOwner(owner));
        }
        assertThrows(NullPointerException.class, () -> registry.forOwner(null));
        registrar.register(ADAPTER, Set.of(TYPE), 1, UNUSED_CALLBACKS);
        assertTrue(registry.freeze().supportsPayload(payload(ADAPTER, 1)));
    }

    @Test
    void freezeClosesHandlesAndCannotBeRepeatedOrReopened() {
        RocketAdapterRegistry registry = registry();
        RocketAdapterRegistrar registrar = registry.forOwner(OWNER);
        registrar.register(ADAPTER, Set.of(TYPE), 1, UNUSED_CALLBACKS);
        RocketBlockEntityAdapters adapters = registry.freeze();
        assertThrows(IllegalStateException.class,
                () -> registrar.register(id("fixture:late"), Set.of(id("blocks:late")), 1, UNUSED_CALLBACKS));
        assertThrows(IllegalStateException.class, registry::freeze);
        assertThrows(IllegalStateException.class, () -> registry.forOwner(OWNER));
        assertTrue(adapters.supportsPayload(payload(ADAPTER, 1)));
        assertFalse(adapters.supportsPayload(payload(id("fixture:late"), 1)));
    }

    @Test
    void closeWithoutFreezeRevokesTheActiveHandleAndCatalog() {
        RocketAdapterRegistry registry = registry();
        RocketAdapterRegistrar registrar = registry.forOwner(OWNER);
        registry.close();
        assertThrows(IllegalStateException.class,
                () -> registrar.register(ADAPTER, Set.of(TYPE), 1, UNUSED_CALLBACKS));
        assertThrows(IllegalStateException.class, registry::freeze);
        assertThrows(IllegalStateException.class, () -> registry.forOwner(OWNER));
    }

    @Test
    void anotherThreadCannotUseHandleOrChangeRegistryLifecycle() throws Exception {
        RocketAdapterRegistry registry = registry();
        RocketAdapterRegistrar registrar = registry.forOwner(OWNER);
        assertOtherThreadRejected(() -> registrar.register(ADAPTER, Set.of(TYPE), 1, UNUSED_CALLBACKS));
        assertOtherThreadRejected(() -> registry.forOwner("other"));
        assertOtherThreadRejected(registry::freeze);
        assertOtherThreadRejected(registry::close);
        registrar.register(ADAPTER, Set.of(TYPE), 1, UNUSED_CALLBACKS);
        assertTrue(registry.freeze().supportsPayload(payload(ADAPTER, 1)));
    }

    @Test
    void externalAdapterCapacityDoesNotCountTheLegacyAdapter() {
        assertEquals(256, RocketAdapterRegistry.MAX_ADAPTERS);
        RocketAdapterRegistry registry = registry();
        RocketAdapterRegistrar registrar = registry.forOwner(OWNER);
        for (int index = 0; index < 256; index++) {
            registrar.register(id("fixture:adapter_" + index), Set.of(id("blocks:type_" + index)),
                    1, UNUSED_CALLBACKS);
        }
        ResourceLocation overflow = id("fixture:overflow");
        assertThrows(IllegalArgumentException.class,
                () -> registrar.register(overflow, Set.of(id("blocks:overflow")), 1, UNUSED_CALLBACKS));
        RocketBlockEntityAdapters adapters = registry.freeze();
        for (int index = 0; index < 256; index++) {
            assertTrue(adapters.supportsPayload(payload(id("fixture:adapter_" + index), 1)));
        }
        assertFalse(adapters.supportsPayload(payload(overflow, 1)));
    }

    @Test
    void typeSetCapacityRejects65WithoutConsumingTheAdapterId() {
        assertEquals(64, RocketAdapterRegistry.MAX_TYPES_PER_ADAPTER);
        RocketAdapterRegistry registry = registry();
        RocketAdapterRegistrar registrar = registry.forOwner(OWNER);
        assertThrows(IllegalArgumentException.class,
                () -> registrar.register(ADAPTER, types(0, 65), 1, UNUSED_CALLBACKS));
        registrar.register(ADAPTER, types(0, 64), 1, UNUSED_CALLBACKS);
        registrar.register(id("fixture:next"), types(64, 1), 1, UNUSED_CALLBACKS);
        assertTrue(registry.freeze().supportsPayload(payload(ADAPTER, 1)));
    }

    @Test
    void totalTypeCapacityIs1024AndOverflowDoesNotReserveItsPrefix() {
        assertEquals(1024, RocketAdapterRegistry.MAX_TYPE_MAPPINGS);
        RocketAdapterRegistry registry = registry();
        RocketAdapterRegistrar registrar = registry.forOwner(OWNER);
        for (int index = 0; index < 15; index++) {
            registrar.register(id("fixture:adapter_" + index), types(index * 64, 64), 1, UNUSED_CALLBACKS);
        }
        registrar.register(id("fixture:almost_full"), types(960, 63), 1, UNUSED_CALLBACKS);
        assertThrows(IllegalArgumentException.class,
                () -> registrar.register(ADAPTER, types(1023, 2), 1, UNUSED_CALLBACKS));
        registrar.register(ADAPTER, types(1023, 1), 1, UNUSED_CALLBACKS);
        ResourceLocation overflow = id("fixture:overflow");
        assertThrows(IllegalArgumentException.class,
                () -> registrar.register(overflow, types(1024, 1), 1, UNUSED_CALLBACKS));
        RocketBlockEntityAdapters adapters = registry.freeze();
        assertTrue(adapters.supportsPayload(payload(ADAPTER, 1)));
        assertFalse(adapters.supportsPayload(payload(overflow, 1)));
    }

    private static RocketAdapterRegistry registry() {
        return new RocketAdapterRegistry(type -> true);
    }

    private static ResourceLocation id(String value) {
        return ResourceLocation.tryParse(value);
    }

    private static RocketBlockEntityPayload payload(ResourceLocation adapter, int version) {
        return RocketAdapterPayloads.encode(adapter, version, new CompoundTag());
    }

    private static Set<ResourceLocation> types(int start, int count) {
        LinkedHashSet<ResourceLocation> result = new LinkedHashSet<>();
        for (int index = start; index < start + count; index++) {
            result.add(id("blocks:type_" + index));
        }
        return result;
    }

    private static void assertOtherThreadRejected(Runnable action) throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> result = executor.submit(action);
            ExecutionException failure = assertThrows(ExecutionException.class,
                    () -> result.get(5, TimeUnit.SECONDS));
            assertInstanceOf(IllegalStateException.class, failure.getCause());
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS), "Test worker did not terminate");
        }
    }
}
