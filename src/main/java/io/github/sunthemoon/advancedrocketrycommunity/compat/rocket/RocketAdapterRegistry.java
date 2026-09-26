package io.github.sunthemoon.advancedrocketrycommunity.compat.rocket;

import io.github.sunthemoon.advancedrocketrycommunity.api.rocket.RocketAdapterRegistrar;
import io.github.sunthemoon.advancedrocketrycommunity.api.rocket.RocketBlockEntityAdapter;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.RocketBlockEntityAdapters;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.VanillaContainerRocketAdapter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.resources.ResourceLocation;

/** Loading-thread builder. Only its frozen result reaches world services. */
public final class RocketAdapterRegistry implements AutoCloseable {
    public static final int MAX_ADAPTERS = 256;
    public static final int MAX_TYPES_PER_ADAPTER = 64;
    public static final int MAX_TYPE_MAPPINGS = 1024;
    private final Thread loadingThread = Thread.currentThread();
    private final Predicate<ResourceLocation> knownType;
    private final Map<ResourceLocation, ExternalRocketBlockEntityAdapter> adapters = new HashMap<>();
    private final Set<ResourceLocation> claimedTypes = new HashSet<>();
    private Object activeHandle;
    private boolean closed;

    public RocketAdapterRegistry(Predicate<ResourceLocation> knownType) {
        this.knownType = Objects.requireNonNull(knownType, "knownType");
    }

    public RocketAdapterRegistrar forOwner(String owner) {
        requireOpen();
        ResourceLocation probe = ResourceLocation.tryParse(Objects.requireNonNull(owner, "owner") + ":registration");
        if (probe == null || !probe.getNamespace().equals(owner) || owner.length() > 255) {
            throw new IllegalArgumentException("Invalid adapter owner namespace");
        }
        Object handle = new Object();
        activeHandle = handle;
        return (id, types, version, provider) -> {
            requireOpen();
            if (activeHandle != handle) {
                throw new IllegalStateException("Registration handle is no longer active");
            }
            register(owner, id, types, version, provider);
        };
    }

    private void register(String owner, ResourceLocation id, Set<ResourceLocation> types,
                          int version, RocketBlockEntityAdapter provider) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(types, "types");
        Objects.requireNonNull(provider, "provider");
        if (!owner.equals(id.getNamespace()) || id.toString().length() > 255 || version <= 0
                || id.equals(VanillaContainerRocketAdapter.ID) || adapters.containsKey(id)
                || types.isEmpty() || types.size() > MAX_TYPES_PER_ADAPTER
                || adapters.size() >= MAX_ADAPTERS
                || claimedTypes.size() + types.size() > MAX_TYPE_MAPPINGS) {
            throw new IllegalArgumentException("Invalid, duplicate or over-capacity rocket adapter registration");
        }
        Set<ResourceLocation> copy = Set.copyOf(types);
        for (ResourceLocation type : copy) {
            if (type.toString().length() > 255 || !knownType.test(type) || claimedTypes.contains(type)
                    || type.equals(ResourceLocation.tryParse("minecraft:chest"))
                    || type.equals(ResourceLocation.tryParse("minecraft:barrel"))) {
                throw new IllegalArgumentException("Invalid or claimed rocket BlockEntity type: " + type);
            }
        }
        adapters.put(id, new ExternalRocketBlockEntityAdapter(id, copy, version, provider));
        claimedTypes.addAll(copy);
    }

    public RocketBlockEntityAdapters freeze() {
        requireOpen();
        close();
        ArrayList<io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.RocketBlockEntityAdapter> frozen =
                new ArrayList<>();
        frozen.add(new VanillaContainerRocketAdapter());
        adapters.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> frozen.add(entry.getValue()));
        return new RocketBlockEntityAdapters(frozen);
    }

    @Override
    public void close() {
        requireThread();
        closed = true;
        activeHandle = null;
    }

    private void requireOpen() {
        requireThread();
        if (closed) {
            throw new IllegalStateException("Rocket adapter registry is closed");
        }
    }

    private void requireThread() {
        if (Thread.currentThread() != loadingThread) {
            throw new IllegalStateException("Rocket adapter registration requires its loading thread");
        }
    }
}
