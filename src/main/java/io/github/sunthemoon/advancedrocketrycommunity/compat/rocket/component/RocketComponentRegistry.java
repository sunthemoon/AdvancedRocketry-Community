package io.github.sunthemoon.advancedrocketrycommunity.compat.rocket.component;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.api.rocket.RocketComponentDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.api.rocket.RocketComponentRegistrar;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketBlockMetrics;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;

/** Atomic loading-thread registration of declarative numeric contributions. */
public final class RocketComponentRegistry implements AutoCloseable {
    public static final int MAX_DEFINITIONS = 256;
    public static final int MAX_BLOCKS_PER_DEFINITION = 64;
    public static final int MAX_BLOCKS = 1024;
    private static final Set<ResourceLocation> RESERVED = Set.of(
            id("rocket_motor"), id("rocket_fuel_tank"), id("rocket_seat"), id("guidance_computer"));

    private final Thread loadingThread = Thread.currentThread();
    private final Function<ResourceLocation, Block> lookup;
    private final Set<ResourceLocation> definitions = new HashSet<>();
    private final Map<Block, RocketBlockMetrics> metrics = new HashMap<>();
    private Object activeHandle;
    private boolean registering;
    private boolean closed;

    /** Unknown IDs must resolve to null, not the registry default. */
    public RocketComponentRegistry(Function<ResourceLocation, Block> lookup) {
        this.lookup = Objects.requireNonNull(lookup, "lookup");
    }

    public RocketComponentRegistrar forOwner(String owner) {
        requireOpen();
        ResourceLocation probe = ResourceLocation.tryParse(Objects.requireNonNull(owner, "owner") + ":registration");
        if (probe == null || !probe.getNamespace().equals(owner) || owner.length() > 255) {
            throw new IllegalArgumentException("Invalid rocket component owner namespace");
        }
        Object handle = new Object();
        activeHandle = handle;
        return (id, blocks, definition) -> {
            requireOpen();
            if (activeHandle != handle) {
                throw new IllegalStateException("Rocket component registration handle is no longer active");
            }
            registering = true;
            try {
                register(owner, id, blocks, definition);
            } finally {
                registering = false;
            }
        };
    }

    private void register(String owner, ResourceLocation id, Set<ResourceLocation> blocks,
                          RocketComponentDefinition definition) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(blocks, "blocks");
        Objects.requireNonNull(definition, "definition");
        if (!owner.equals(id.getNamespace()) || id.toString().length() > 255
                || definitions.contains(id) || definitions.size() >= MAX_DEFINITIONS
                || blocks.isEmpty() || blocks.size() > MAX_BLOCKS_PER_DEFINITION
                || metrics.size() + blocks.size() > MAX_BLOCKS) {
            throw new IllegalArgumentException("Invalid, duplicate or over-capacity rocket component registration");
        }
        RocketBlockMetrics value = new RocketBlockMetrics(definition.mass(), definition.thrust(),
                definition.fuelCapacity(), definition.engine(), definition.seat(), definition.guidance());
        Map<Block, RocketBlockMetrics> resolved = new HashMap<>();
        for (ResourceLocation blockId : Set.copyOf(blocks)) {
            if (blockId.toString().length() > 255 || RESERVED.contains(blockId)) {
                throw new IllegalArgumentException("Invalid or reserved rocket component block: " + blockId);
            }
            Block block = lookup.apply(blockId);
            if (block == null || block.defaultBlockState().isAir() || metrics.containsKey(block)
                    || resolved.putIfAbsent(block, value) != null) {
                throw new IllegalArgumentException("Unknown, air or claimed rocket component block: " + blockId);
            }
        }
        metrics.putAll(resolved);
        definitions.add(id);
    }

    public RocketComponentCatalog freeze() {
        requireOpen();
        RocketComponentCatalog catalog = new RocketComponentCatalog(metrics);
        close();
        return catalog;
    }

    @Override
    public void close() {
        requireThread();
        if (registering) {
            throw new IllegalStateException("Cannot close during component registration");
        }
        closed = true;
        activeHandle = null;
        definitions.clear();
        metrics.clear();
    }

    private void requireOpen() {
        requireThread();
        if (closed || registering) {
            throw new IllegalStateException("Rocket component registry is closed or registering");
        }
    }

    private void requireThread() {
        if (Thread.currentThread() != loadingThread) {
            throw new IllegalStateException("Rocket component registration requires its loading thread");
        }
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.tryParse(ModIdentity.MOD_ID + ":" + path);
    }
}
