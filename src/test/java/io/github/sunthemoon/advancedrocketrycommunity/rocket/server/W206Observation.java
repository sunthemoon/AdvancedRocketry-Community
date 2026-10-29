package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBounds;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transfer.RocketDiscoveryColumns;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.Ticket;
import net.minecraft.world.level.ChunkPos;

/** Read-only diagnostic export; no Level getters which create or load a chunk are used. */
public final class W206Observation {
    private W206Observation() { }
    public record TicketRow(String dimension, int x, int z, int level, String type, String text) { }

    public static int[] extent(String shape) {
        return switch (shape) {
            case "contained" -> new int[] {0, 0};
            case "border" -> new int[] {14, 0};
            case "square" -> new int[] {46, 46};
            case "strip" -> new int[] {238, 0};
            default -> throw new IllegalArgumentException("Unknown fixture shape " + shape);
        };
    }

    public static RocketDiscoveryColumns candidate(String shape, int centerX, int centerZ) {
        var extent = extent(shape);
        return RocketDiscoveryColumns.of(centerX - extent[0] / 2, centerZ - extent[1] / 2,
                new RocketBounds(new RocketPosition(0, 0, 0), new RocketPosition(extent[0], 1, extent[1])));
    }

    public static boolean noInfluence(List<ChunkPos> columns, List<TicketRow> tickets) {
        return tickets.stream().noneMatch(ticket -> columns.stream().anyMatch(column ->
                ticket.level() + Math.max(Math.abs((long) column.x - ticket.x()),
                        Math.abs((long) column.z - ticket.z())) <= 31));
    }

    static long fullTick(long core, long end) {
        if (core < 0 || end < 0) { throw new IllegalArgumentException("Negative tick duration"); }
        return Math.addExact(core, end);
    }

    static int ownerCallbacks(List<Map<String, Object>> delivered, int endTick) {
        // START callbacks can run before MinecraftServer increments tickCount. Delivery before the last END
        // observer, not equality with the callback's getTickCount(), identifies this completed tick.
        return (int) delivered.stream().filter(row -> Boolean.TRUE.equals(row.get("owner_thread"))).count();
    }

    static boolean released(boolean landingWitness, boolean survivorStillLoaded, List<String> flight, List<String> warmup) {
        return landingWitness && flight.isEmpty() && warmup.isEmpty();
    }

    public static List<TicketRow> tickets(ServerLevel level) {
        var rows = new ArrayList<TicketRow>();
        var distance = level.getChunkSource().chunkMap.getDistanceManager();
        try {
            for (var field : DistanceManager.class.getDeclaredFields()) {
                if (!Long2ObjectMap.class.isAssignableFrom(field.getType())) { continue; }
                field.setAccessible(true);
                var map = (Long2ObjectMap<?>) field.get(distance);
                for (var entry : map.long2ObjectEntrySet()) {
                    if (!(entry.getValue() instanceof Iterable<?> values)) { continue; }
                    for (var value : values) {
                        if (value instanceof Ticket<?> ticket) {
                            var at = new ChunkPos(entry.getLongKey());
                            rows.add(new TicketRow(level.dimension().location().toString(), at.x, at.z,
                                    ticket.getTicketLevel(), ticket.getType().toString(), ticket.toString()));
                        }
                    }
                }
            }
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
        return rows;
    }

    public static List<String> owned(MinecraftServer server, String type, Object key) {
        var found = new TreeSet<String>();
        for (var level : server.getAllLevels()) {
            for (var row : tickets(level)) {
                if (row.type().equals(type) && row.text().contains(key.toString())) {
                    found.add(row.dimension() + ":" + row.x() + ":" + row.z());
                }
            }
        }
        return List.copyOf(found);
    }

    public static List<Map<String, Object>> chunks(ServerLevel level) {
        var found = new ArrayList<Map<String, Object>>();
        for (var holder : level.getChunkSource().chunkMap.getChunks()) {
            var at = holder.getPos();
            boolean full = level.getChunkSource().getChunkNow(at.x, at.z) != null;
            found.add(Map.of("x", at.x, "z", at.z, "full", full,
                    "entities_loaded", level.areEntitiesLoaded(at.toLong()),
                    "entity_ticking", level.isPositionEntityTicking(at.getWorldPosition())));
        }
        return found;
    }
}
