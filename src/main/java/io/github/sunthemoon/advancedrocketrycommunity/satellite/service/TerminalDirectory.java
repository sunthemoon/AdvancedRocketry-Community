package io.github.sunthemoon.advancedrocketrycommunity.satellite.service;

import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.MissionPayload;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Loaded delivery terminals by ID, for the rebind command and the missing-terminal check (ADR-051 section 9).
 * Main thread only; cleared when the server stops. Lookups never load a chunk.
 */
public final class TerminalDirectory {
    public static final int MAX_TERMINALS = 65_536;

    public enum Presence { PRESENT, MISSING, UNKNOWN }

    public record Location(ResourceKey<Level> level, BlockPos pos) {
    }

    private final Map<UUID, Location> loaded = new HashMap<>();

    public void register(UUID id, ResourceKey<Level> level, BlockPos pos) {
        if (loaded.containsKey(id) || loaded.size() < MAX_TERMINALS) {
            loaded.put(id, new Location(level, pos.immutable()));
        }
    }

    public void unregister(UUID id, ResourceKey<Level> level, BlockPos pos) {
        loaded.remove(id, new Location(level, pos.immutable()));
    }

    public Optional<Location> loaded(UUID id) {
        return Optional.ofNullable(loaded.get(id));
    }

    /** The loaded terminal with this ID, if its chunk is loaded and it is really there. */
    public Optional<DeliveryEndpoint> endpoint(MinecraftServer server, UUID id) {
        return loaded(id).flatMap(location -> endpointAt(server, location.level(), location.pos()))
                .filter(endpoint -> endpoint.terminalId().equals(id));
    }

    /**
     * A terminal is missing when the chunk at its last recorded position is loaded and holds no terminal with
     * that ID; an unloaded chunk or an unknown level says nothing.
     */
    public static Presence presence(MinecraftServer server, UUID id,
                                    Optional<MissionPayload.TerminalLocation> display) {
        if (display.isEmpty()) {
            return Presence.UNKNOWN;
        }
        ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, display.orElseThrow().level());
        ServerLevel level = server.getLevel(key);
        BlockPos pos = display.orElseThrow().pos();
        if (level == null || level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) == null) {
            return Presence.UNKNOWN;
        }
        return endpointAt(server, key, pos).filter(endpoint -> endpoint.terminalId().equals(id)).isPresent()
                ? Presence.PRESENT : Presence.MISSING;
    }

    public void clear() {
        loaded.clear();
    }

    private static Optional<DeliveryEndpoint> endpointAt(MinecraftServer server, ResourceKey<Level> key,
                                                         BlockPos pos) {
        ServerLevel level = server.getLevel(key);
        LevelChunk chunk = level == null ? null
                : level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        return chunk != null && chunk.getBlockEntity(pos, LevelChunk.EntityCreationType.CHECK)
                instanceof DeliveryEndpoint endpoint ? Optional.of(endpoint) : Optional.empty();
    }
}
