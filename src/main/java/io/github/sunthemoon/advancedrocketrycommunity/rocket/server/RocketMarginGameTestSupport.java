package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transfer.RocketDiscoveryColumns;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.Ticket;
import net.minecraft.world.level.ChunkPos;

/**
 * W90a GameTest support: reflective, read-only ticket views and the two ticket effects that a fixture needs (the F2
 * callback's origin tickets, and the ticket removal that a process restart implies). Never product authority.
 */
public final class RocketMarginGameTestSupport {
    private RocketMarginGameTestSupport() { }

    /** R52 native first-tick observation; pending retirement work is runtime-owned, never saved. */
    public static int ordinaryRetirementEntries(MinecraftServer server) {
        io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowTestEndpoints.requireEnabled(server);
        var installed = RocketRuntime.installedTransfers();
        return installed == null ? 0 : RocketSourceRetirementFixtures.recovery(installed).retirements().size();
    }

    /** The origin flight tickets that this process's F2 callback adds for the recorded ends. */
    public static void addOriginTickets(MinecraftServer server, RocketTransferRecord record) {
        for (var snapshot : List.of(record.sourceSnapshot(), record.destinationSnapshot())) {
            var level = RocketTransferEntities.level(server, snapshot.sourceDimension());
            if (level != null) { RocketTransferEntities.keepLoaded(level, snapshot, record.transferId()); }
        }
    }

    /** Every flight ticket of the record's transfer, removed as a process restart leaves none. */
    public static void removeFlightTickets(MinecraftServer server, RocketTransferRecord record) {
        RocketTransferEntities.releaseFlightTickets(server, record);
    }

    /** The checked readiness predicate for one recorded end. */
    public static boolean endReady(MinecraftServer server, RocketStructureSnapshot snapshot) {
        return RocketTransferEntities.endAlreadyLoaded(server, snapshot);
    }

    public static String column(ResourceLocation level, ChunkPos column) { return level + "@" + column.x + "," + column.z; }

    /** {@link #column} names of both recorded ends' margin refresh sets. */
    public static Set<String> marginColumns(RocketTransferRecord record) {
        var columns = new TreeSet<String>();
        for (var snapshot : List.of(record.sourceSnapshot(), record.destinationSnapshot())) {
            RocketDiscoveryColumns.of(snapshot).refreshSet().forEach(value -> columns.add(column(snapshot.sourceDimension(), value)));
        }
        return columns;
    }

    public static boolean flightTicket(Ticket<?> ticket, UUID transfer) {
        var text = ticket.toString();
        return text.contains("arce_rocket_flight") && text.contains(transfer.toString());
    }

    /** {@link #column} names of every flight ticket keyed by the transfer, in every Level. */
    public static Set<String> flightTicketColumns(MinecraftServer server, UUID transfer) {
        var found = new TreeSet<String>();
        for (var level : server.getAllLevels()) {
            for (var map : ticketMaps(level)) {
                for (var entry : map.long2ObjectEntrySet()) {
                    if (!(entry.getValue() instanceof Iterable<?> tickets)) { continue; }
                    for (var ticket : tickets) {
                        if (ticket instanceof Ticket<?> typed && flightTicket(typed, transfer)) {
                            found.add(column(level.dimension().location(), new ChunkPos(entry.getLongKey())));
                        }
                    }
                }
            }
        }
        return found;
    }

    /** Every ticket on one column, read by field type so that mapped and packaged names both work. */
    public static List<Ticket<?>> tickets(ServerLevel level, ChunkPos column) {
        var found = new ArrayList<Ticket<?>>();
        for (var map : ticketMaps(level)) {
            if (map.get(column.toLong()) instanceof Iterable<?> tickets) {
                for (var ticket : tickets) { if (ticket instanceof Ticket<?> typed) { found.add(typed); } }
            }
        }
        return found;
    }

    private static List<it.unimi.dsi.fastutil.longs.Long2ObjectMap<?>> ticketMaps(ServerLevel level) {
        var distance = level.getChunkSource().chunkMap.getDistanceManager();
        var maps = new ArrayList<it.unimi.dsi.fastutil.longs.Long2ObjectMap<?>>();
        try {
            for (var field : DistanceManager.class.getDeclaredFields()) {
                if (!it.unimi.dsi.fastutil.longs.Long2ObjectMap.class.isAssignableFrom(field.getType())) { continue; }
                field.setAccessible(true);
                maps.add((it.unimi.dsi.fastutil.longs.Long2ObjectMap<?>) field.get(distance));
            }
        } catch (ReflectiveOperationException exception) { throw new IllegalStateException("Cannot inspect chunk tickets", exception); }
        return maps;
    }
}
