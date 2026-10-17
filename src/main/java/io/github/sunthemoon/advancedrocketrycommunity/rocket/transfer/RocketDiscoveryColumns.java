package io.github.sunthemoon.advancedrocketrycommunity.rocket.transfer;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBounds;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.world.level.ChunkPos;

/**
 * W90 (Root decisions R21 item 2 and R22): the one checked discovery-margin column set of a rocket end. Its rectangle
 * is the chunk columns from {@code (min - 1) >> 4} to {@code (max + 2) >> 4} in x and z, exactly the columns that the
 * transfer census reads, with overflow-checked arithmetic and at most {@link RocketFlightLimits#MAX_LANDING_CHUNKS}
 * columns. Readiness, every governed pad-admission path, F1's source check, the margin ticket refresh and the release
 * all use it. Server-free: it never reads a Level, a chunk or a ticket.
 */
public final class RocketDiscoveryColumns {
    public enum Status { VALID, ARITHMETIC, OVER_BUDGET }

    private final Status status;
    private final int minX, maxX, minZ, maxZ;
    private final int originX, originZ;

    private RocketDiscoveryColumns(Status status, int minX, int maxX, int minZ, int maxZ, int originX, int originZ) {
        this.status = status; this.minX = minX; this.maxX = maxX; this.minZ = minZ; this.maxZ = maxZ;
        this.originX = originX; this.originZ = originZ;
    }

    public static RocketDiscoveryColumns of(RocketStructureSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        return of(snapshot.sourceOrigin().x(), snapshot.sourceOrigin().z(), snapshot.bounds());
    }

    /** A pad candidate is checked from its origin x and z before its surface height is known. */
    public static RocketDiscoveryColumns of(int originX, int originZ, RocketBounds bounds) {
        Objects.requireNonNull(bounds, "bounds");
        int columnX = originX >> 4, columnZ = originZ >> 4;
        int minX, maxX, minZ, maxZ;
        try {
            minX = Math.subtractExact(Math.addExact(originX, bounds.minimum().x()), 1) >> 4;
            maxX = Math.addExact(Math.addExact(originX, bounds.maximum().x()), 2) >> 4;
            minZ = Math.subtractExact(Math.addExact(originZ, bounds.minimum().z()), 1) >> 4;
            maxZ = Math.addExact(Math.addExact(originZ, bounds.maximum().z()), 2) >> 4;
        } catch (ArithmeticException overflow) {
            return new RocketDiscoveryColumns(Status.ARITHMETIC, 0, -1, 0, -1, columnX, columnZ);
        }
        long width = (long) maxX - minX + 1, depth = (long) maxZ - minZ + 1;
        if (width < 1 || depth < 1 || width * depth > RocketFlightLimits.MAX_LANDING_CHUNKS) {
            return new RocketDiscoveryColumns(Status.OVER_BUDGET, 0, -1, 0, -1, columnX, columnZ);
        }
        return new RocketDiscoveryColumns(Status.VALID, minX, maxX, minZ, maxZ, columnX, columnZ);
    }

    public Status status() { return status; }
    public boolean valid() { return status == Status.VALID; }

    /** True when the end's own origin column lies in its valid rectangle. */
    public boolean containsOrigin() {
        return valid() && originX >= minX && originX <= maxX && originZ >= minZ && originZ <= maxZ;
    }

    /** New governed admission and a recorded governed end's margin tickets need both. */
    public boolean admissible() { return valid() && containsOrigin(); }

    /** Rectangle column count; zero unless valid. */
    public int count() { return valid() ? (maxX - minX + 1) * (maxZ - minZ + 1) : 0; }

    public int minColumnX() { return minX; }
    public int maxColumnX() { return maxX; }
    public int minColumnZ() { return minZ; }
    public int maxColumnZ() { return maxZ; }
    public ChunkPos origin() { return new ChunkPos(originX, originZ); }

    /** The rectangle in one fixed order, x then z; empty unless valid. */
    public List<ChunkPos> columns() {
        var columns = new ArrayList<ChunkPos>(count());
        if (valid()) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) { columns.add(new ChunkPos(x, z)); }
            }
        }
        return List.copyOf(columns);
    }

    /** The margin tickets of an admissible end; none otherwise. */
    public List<ChunkPos> refreshSet() { return admissible() ? columns() : List.of(); }

    /** Always the origin column, plus the refreshed rectangle when the end is admissible. */
    public List<ChunkPos> releaseSet() { return admissible() ? columns() : List.of(origin()); }

    @Override public String toString() {
        return valid() ? "columns " + minX + ".." + maxX + " x " + minZ + ".." + maxZ + " origin=" + containsOrigin()
                : status.name();
    }
}
