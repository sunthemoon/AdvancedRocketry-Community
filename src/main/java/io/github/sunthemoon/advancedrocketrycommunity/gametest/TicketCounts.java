package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.Ticket;
import net.minecraft.util.SortedArraySet;
import net.minecraft.world.level.ChunkPos;

/**
 * GameTest support for ADR-054 section 12: chunk tickets around a system's chunks counted by type, read from the
 * distance manager. Player tickets, the short-lived tickets of chunk loads and lighting, the tests' own fixture
 * tickets and the rocket-flight tickets of earlier tests' flights still in the air come and go on their own; every
 * other type must be unchanged by an endgame system, which never adds a ticket. Chunk loads themselves are caught by
 * the loaded-chunk counts.
 */
final class TicketCounts {
    private static final Set<String> FIXTURE_TYPES = Set.of("player", "unknown", "light", "post_teleport");

    private TicketCounts() {
    }

    /**
     * Ticket counts by type name on the chunks within {@code radius} of {@code center}, without player, transient and
     * fixture tickets. Other systems' tickets elsewhere in the Level (rocket flights, forced chunks) do not count.
     */
    static Map<String, Integer> near(ServerLevel level, ChunkPos center, int radius) {
        Map<String, Integer> counts = new TreeMap<>();
        tickets(level).long2ObjectEntrySet().forEach(entry -> {
            ChunkPos chunk = new ChunkPos(entry.getLongKey());
            if (Math.abs(chunk.x - center.x) > radius || Math.abs(chunk.z - center.z) > radius) {
                return;
            }
            for (Ticket<?> ticket : entry.getValue()) {
                String type = ticket.getType().toString();
                if (!FIXTURE_TYPES.contains(type) && !type.startsWith("arce_gametest")
                        && !type.startsWith("arce_rocket")) {
                    counts.merge(type, 1, Integer::sum);
                }
            }
        });
        return counts;
    }

    @SuppressWarnings("unchecked")
    private static Long2ObjectOpenHashMap<SortedArraySet<Ticket<?>>> tickets(ServerLevel level) {
        DistanceManager manager = level.getChunkSource().chunkMap.getDistanceManager();
        try {
            Field field = DistanceManager.class.getDeclaredField("tickets");
            field.setAccessible(true);
            return (Long2ObjectOpenHashMap<SortedArraySet<Ticket<?>>>) field.get(manager);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("The distance manager's tickets are not readable", exception);
        }
    }
}
