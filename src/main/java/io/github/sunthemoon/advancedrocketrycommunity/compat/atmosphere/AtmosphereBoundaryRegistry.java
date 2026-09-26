package io.github.sunthemoon.advancedrocketrycommunity.compat.atmosphere;

import io.github.sunthemoon.advancedrocketrycommunity.api.atmosphere.AtmosphereBoundary;
import io.github.sunthemoon.advancedrocketrycommunity.api.atmosphere.AtmosphereBoundaryProvider;
import io.github.sunthemoon.advancedrocketrycommunity.api.atmosphere.AtmosphereBoundaryRegistrar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.LongSupplier;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Loading-thread compiler. Failed registrations never reserve IDs or publish states. */
public final class AtmosphereBoundaryRegistry implements AutoCloseable {
    public static final int MAX_PROVIDERS = 256;
    public static final int MAX_BLOCKS_PER_PROVIDER = 64;
    public static final int MAX_BLOCKS = 1024;
    public static final int MAX_STATES_PER_PROVIDER = 4096;
    public static final int MAX_STATES = 16384;
    private static final long MAX_CALLBACK_NANOS = 5_000_000L;
    private static final long MAX_REGISTRATION_NANOS = 1_000_000_000L;

    private final Thread loadingThread = Thread.currentThread();
    private final Function<ResourceLocation, Block> lookup;
    private final LongSupplier nanoTime;
    private final Set<ResourceLocation> providers = new HashSet<>();
    private final Set<ResourceLocation> claimedBlocks = new HashSet<>();
    private final Map<BlockState, AtmosphereBoundary> states = new HashMap<>();
    private Object activeHandle;
    private boolean closed;
    private boolean registering;

    /** The lookup returns null for unknown block IDs, not the registry default. */
    public AtmosphereBoundaryRegistry(Function<ResourceLocation, Block> lookup) {
        this(lookup, System::nanoTime);
    }

    AtmosphereBoundaryRegistry(Function<ResourceLocation, Block> lookup, LongSupplier nanoTime) {
        this.lookup = Objects.requireNonNull(lookup, "lookup");
        this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime");
    }

    public AtmosphereBoundaryRegistrar forOwner(String owner) {
        requireOpen();
        ResourceLocation probe = ResourceLocation.tryParse(Objects.requireNonNull(owner, "owner") + ":registration");
        if (probe == null || !probe.getNamespace().equals(owner) || owner.length() > 255) {
            throw new IllegalArgumentException("Invalid atmosphere boundary owner namespace");
        }
        Object handle = new Object();
        activeHandle = handle;
        return (id, blocks, provider) -> {
            requireOpen();
            if (activeHandle != handle) {
                throw new IllegalStateException("Registration handle is no longer active");
            }
            registering = true;
            try {
                register(owner, id, blocks, provider);
            } finally {
                registering = false;
            }
        };
    }

    private void register(String owner, ResourceLocation id, Set<ResourceLocation> blocks,
                          AtmosphereBoundaryProvider provider) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(blocks, "blocks");
        Objects.requireNonNull(provider, "provider");
        if (!owner.equals(id.getNamespace()) || id.toString().length() > 255
                || providers.contains(id) || providers.size() >= MAX_PROVIDERS
                || blocks.isEmpty() || blocks.size() > MAX_BLOCKS_PER_PROVIDER
                || claimedBlocks.size() + blocks.size() > MAX_BLOCKS) {
            throw new IllegalArgumentException("Invalid, duplicate or over-capacity atmosphere boundary registration");
        }
        Map<ResourceLocation, Block> resolved = new LinkedHashMap<>();
        int stateCount = 0;
        for (ResourceLocation blockId : Set.copyOf(blocks).stream().sorted().toList()) {
            Block block = lookup.apply(blockId);
            if (blockId.toString().length() > 255 || block == null || claimedBlocks.contains(blockId)) {
                throw new IllegalArgumentException("Invalid or claimed atmosphere block: " + blockId);
            }
            stateCount += block.getStateDefinition().getPossibleStates().size();
            if (stateCount > MAX_STATES_PER_PROVIDER || states.size() + stateCount > MAX_STATES) {
                throw new IllegalArgumentException("Atmosphere boundary state capacity exceeded: " + id);
            }
            resolved.put(blockId, block);
        }
        Map<BlockState, AtmosphereBoundary> compiled = new HashMap<>();
        long totalNanos = 0L;
        for (Map.Entry<ResourceLocation, Block> entry : resolved.entrySet()) {
            for (BlockState state : entry.getValue().getStateDefinition().getPossibleStates()) {
                AtmosphereBoundary result;
                long start = nanoTime.getAsLong();
                try {
                    result = provider.classify(state);
                } catch (RuntimeException exception) {
                    throw failure(id, entry.getKey());
                }
                long elapsed = nanoTime.getAsLong() - start;
                totalNanos += elapsed;
                if (result == null || elapsed > MAX_CALLBACK_NANOS || totalNanos > MAX_REGISTRATION_NANOS
                        || states.containsKey(state) || compiled.putIfAbsent(state, result) != null) {
                    throw failure(id, entry.getKey());
                }
            }
        }
        providers.add(id);
        claimedBlocks.addAll(resolved.keySet());
        states.putAll(compiled);
    }

    public AtmosphereBoundaryCatalog freeze() {
        requireOpen();
        AtmosphereBoundaryCatalog catalog = new AtmosphereBoundaryCatalog(states);
        close();
        return catalog;
    }

    @Override
    public void close() {
        requireThread();
        if (registering) {
            throw new IllegalStateException("Cannot close during boundary compilation");
        }
        closed = true;
        activeHandle = null;
        providers.clear();
        claimedBlocks.clear();
        states.clear();
    }

    private void requireOpen() {
        requireThread();
        if (closed || registering) {
            throw new IllegalStateException("Atmosphere boundary registry is closed or compiling");
        }
    }

    private void requireThread() {
        if (Thread.currentThread() != loadingThread) {
            throw new IllegalStateException("Atmosphere boundary registration requires its loading thread");
        }
    }

    private static IllegalArgumentException failure(ResourceLocation id, ResourceLocation block) {
        return new IllegalArgumentException("Atmosphere boundary compilation rejected provider " + id + " for " + block);
    }
}
