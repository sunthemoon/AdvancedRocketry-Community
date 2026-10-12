package io.github.sunthemoon.advancedrocketrycommunity.station.warp;

import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationLimits;
import java.util.Objects;

/**
 * ADR-065 section 5.1: the controller's core reach, a loaded warp core of the station whose centre is within
 * {@link StationLimits#WARP_CORE_REACH} of the controller's centre. The scan is bounded (at most 11^3 positions)
 * and asks whether a position is loaded before it reads the block there, so it never loads a chunk.
 */
public final class WarpCoreReach {
    /** The scan radius in whole blocks. */
    public static final int RADIUS = (int) StationLimits.WARP_CORE_REACH;
    /** The most positions one scan can probe. */
    public static final int MAX_PROBES = (2 * RADIUS + 1) * (2 * RADIUS + 1) * (2 * RADIUS + 1);

    private WarpCoreReach() {
    }

    /** True when a position within reach of the controller at {@code (x, y, z)} holds a loaded core of the station. */
    public static boolean reachable(int x, int y, int z, Probe probe) {
        Objects.requireNonNull(probe, "probe");
        double reachSquared = StationLimits.WARP_CORE_REACH * StationLimits.WARP_CORE_REACH;
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dy = -RADIUS; dy <= RADIUS; dy++) {
                for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                    if (dx * dx + dy * dy + dz * dz > reachSquared) {
                        continue;
                    }
                    int px = x + dx;
                    int py = y + dy;
                    int pz = z + dz;
                    if (probe.loaded(px, py, pz) && probe.core(px, py, pz) && probe.inStation(px, pz)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** The world as the scan sees it; {@link #core} is asked only for a loaded position. */
    public interface Probe {
        /** Inside the world bounds and in a chunk that is already loaded. */
        boolean loaded(int x, int y, int z);

        /** A warp core stands at this loaded position. */
        boolean core(int x, int y, int z);

        /** The position lies inside this station's committed region. */
        boolean inStation(int x, int z);
    }
}
