package io.github.sunthemoon.advancedrocketrycommunity.satellite.service;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * Runtime positions of microwave receivers seen since the server started (ADR-049 section 9). A link's receiver
 * is <em>missing</em> only when its last known chunk is loaded and holds no receiver with that ID; a receiver
 * never seen since the start, or in an unloaded chunk, is unknown, and only an operator may unlink then.
 * Owned by the satellite manager and cleared with the server.
 */
public final class ReceiverDirectory {
    public static final int MAX_ENTRIES = 16_384;

    /** Whether a receiver ID can be confirmed present, confirmed missing, or neither. */
    public enum Presence { PRESENT, MISSING, UNKNOWN }

    /** Implemented by the receiver block entity, so this service does not depend on the block package. */
    public interface Host {
        UUID receiverId();
    }

    private final Map<UUID, Location> locations = new HashMap<>();

    public synchronized void register(UUID receiverId, ResourceKey<Level> level, BlockPos position) {
        Objects.requireNonNull(receiverId, "receiverId");
        if (locations.size() >= MAX_ENTRIES && !locations.containsKey(receiverId)) {
            return;
        }
        locations.put(receiverId, new Location(level, position.immutable()));
    }

    public synchronized void remove(UUID receiverId) {
        locations.remove(receiverId);
    }

    public synchronized Presence presence(MinecraftServer server, UUID receiverId) {
        Location location = locations.get(receiverId);
        if (location == null) {
            return Presence.UNKNOWN;
        }
        ServerLevel level = server.getLevel(location.level());
        if (level == null || !level.hasChunkAt(location.position())) {
            return Presence.UNKNOWN;
        }
        return level.getBlockEntity(location.position()) instanceof Host host && receiverId.equals(host.receiverId())
                ? Presence.PRESENT : Presence.MISSING;
    }

    public synchronized void clear() {
        locations.clear();
    }

    private record Location(ResourceKey<Level> level, BlockPos position) {
    }
}
