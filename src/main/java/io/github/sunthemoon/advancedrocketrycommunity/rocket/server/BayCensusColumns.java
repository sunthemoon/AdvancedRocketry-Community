package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transfer.RocketDiscoveryColumns;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiPredicate;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

/**
 * W170-R65 under Root decision R150: one recorded end's bay census columns. Every flight keeps the shared checked set
 * ({@link RocketDiscoveryColumns}, from {@code (min - 1) >> 4}). The census box's low edge is {@code min - 1}, so a
 * 1.5-block-wide image of L can intersect it from up to 0.75 block before that edge, with its home column at
 * {@code (min - 2) >> 4}: one column outside the shared set whenever {@code min - 1} starts a column. The bay's absence
 * census, its end-loading waits and its claim tickets use the rectangle from {@code (min - 2) >> 4} to
 * {@code (max + 2) >> 4}, under the same {@value RocketFlightLimits#MAX_LANDING_CHUNKS}-column bound: an end that this
 * strip takes past it is not valid here, so the claim holds, never with a truncated census and never with a ticket beyond
 * the shared ones. Recognition already reads the strip once it is loaded, because the entity index visits sections two
 * blocks past a query box. The flight's release removes only the shared columns; a strip ticket ends with the existing
 * {@value RocketFlightLimits#FLIGHT_TICKET_TIMEOUT_TICKS}-tick ticket timeout after the claim's last renewal.
 */
final class BayCensusColumns {
    private final RocketDiscoveryColumns shared;
    private final boolean valid;
    private final int minX, maxX, minZ, maxZ;

    private BayCensusColumns(RocketDiscoveryColumns shared, boolean valid, int minX, int maxX, int minZ, int maxZ) {
        this.shared = shared; this.valid = valid; this.minX = minX; this.maxX = maxX; this.minZ = minZ; this.maxZ = maxZ;
    }

    static BayCensusColumns of(RocketStructureSnapshot snapshot) {
        var shared = RocketDiscoveryColumns.of(snapshot);
        var invalid = new BayCensusColumns(shared, false, 0, -1, 0, -1);
        if (!shared.valid()) { return invalid; }
        int minX, minZ;
        try {
            minX = Math.subtractExact(Math.addExact(snapshot.sourceOrigin().x(), snapshot.bounds().minimum().x()), 2) >> 4;
            minZ = Math.subtractExact(Math.addExact(snapshot.sourceOrigin().z(), snapshot.bounds().minimum().z()), 2) >> 4;
        } catch (ArithmeticException overflow) {
            return invalid;
        }
        long count = ((long) shared.maxColumnX() - minX + 1) * ((long) shared.maxColumnZ() - minZ + 1);
        return count > RocketFlightLimits.MAX_LANDING_CHUNKS ? invalid
                : new BayCensusColumns(shared, true, minX, shared.maxColumnX(), minZ, shared.maxColumnZ());
    }

    /** The shared set is valid and its low strip keeps the rectangle within the bound. */
    boolean valid() { return valid; }

    /** As the shared set's admissibility: valid, with the end's own origin column among the columns. */
    boolean admissible() { return valid && shared.admissible(); }

    int count() { return valid ? (maxX - minX + 1) * (maxZ - minZ + 1) : 0; }

    /** The rectangle in one fixed order, x then z; empty unless valid. */
    List<ChunkPos> columns() {
        var columns = new ArrayList<ChunkPos>(count());
        if (valid) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) { columns.add(new ChunkPos(x, z)); }
            }
        }
        return List.copyOf(columns);
    }

    /** The columns the strip adds to the shared set: at most one row in x and one in z; empty unless valid. */
    List<ChunkPos> strip() {
        var shared = this.shared.columns();
        return columns().stream().filter(column -> !shared.contains(column)).toList();
    }

    /** Every column ready; false unless valid. Reads only. */
    boolean ready(BiPredicate<Integer, Integer> ready) {
        if (!valid) { return false; }
        for (var column : columns()) {
            if (!ready.test(column.x, column.z)) { return false; }
        }
        return true;
    }

    /** The end's every census column entity-loaded and entity-ticking, as W90's readiness reads a column. Never loads. */
    static boolean loaded(MinecraftServer server, RocketStructureSnapshot end) {
        ServerLevel level = RocketTransferEntities.level(server, end.sourceDimension());
        return level != null && of(end).ready((x, z) -> RocketTransferEntities.columnReady(level, x, z, end.sourceOrigin().y()));
    }

    /**
     * R1 for a held restart group (W170): one ticket on each census column of both ends, the origins among them; nothing
     * unless both Levels exist and both ends are admissible. At most 16 per end. No load.
     */
    static int renewRestart(MinecraftServer server, RocketTransferRecord record) {
        if (!server.isSameThread()) { return 0; }
        return renewBoth(record.transferId(), record.sourceSnapshot(), record.destinationSnapshot(), RocketTransferEntities.levelTickets(server));
    }

    static int renewBoth(UUID transfer, RocketStructureSnapshot source, RocketStructureSnapshot destination,
                         RocketTransferEntities.FlightTickets tickets) {
        for (var end : List.of(source, destination)) {
            if (!tickets.present(end.sourceDimension()) || !of(end).admissible()) { return 0; }
        }
        return add(transfer, List.of(source, destination), tickets);
    }

    /**
     * W185-R2 for a held in-session claim from CONSUME on: one ticket on each census column of each admissible end, else
     * none beyond T's own flight tickets. At the destination this adds only the strip to the existing destination tickets.
     * At most 16 per end. No load.
     */
    static int renewInSession(MinecraftServer server, RocketTransferRecord record) {
        if (!server.isSameThread()) { return 0; }
        return renewEach(record.transferId(), List.of(record.sourceSnapshot(), record.destinationSnapshot()),
                RocketTransferEntities.levelTickets(server));
    }

    static int renewEach(UUID transfer, List<RocketStructureSnapshot> ends, RocketTransferEntities.FlightTickets tickets) {
        return add(transfer, ends.stream().filter(end -> tickets.present(end.sourceDimension()) && of(end).admissible()).toList(), tickets);
    }

    private static int add(UUID transfer, List<RocketStructureSnapshot> ends, RocketTransferEntities.FlightTickets tickets) {
        var added = new LinkedHashSet<Map.Entry<ResourceLocation, ChunkPos>>();
        for (var end : ends) {
            for (var column : of(end).columns()) {
                if (added.add(Map.entry(end.sourceDimension(), column))) { tickets.add(end.sourceDimension(), column, transfer); }
            }
        }
        return added.size();
    }
}
