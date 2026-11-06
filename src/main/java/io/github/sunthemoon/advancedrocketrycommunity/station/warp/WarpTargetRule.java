package io.github.sunthemoon.advancedrocketrycommunity.station.warp;

import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.model.CelestialBodyDefinition;
import java.util.Objects;

/**
 * Which bodies station creation and warp may target (ADR-044 section 4, ADR-065 section 5.1): an orbitable body, except the
 * shared Space body. That body names the Space Level that every station region sits in (ADR-031: shared Space is a
 * world host; ADR-044 section 1: every system is a label over the same Space Level), and it is orbitable only by
 * ADR-031's legacy default. No rocket route, satellite bay source or laser drill table accepts it as an orbit (W308).
 * The rule closes new arrivals only: a station that already orbits the Space body keeps its record and can warp away.
 */
public final class WarpTargetRule {
    private WarpTargetRule() {
    }

    /** True when a station may be created around {@code body}, or the body may be listed and quoted for warp. */
    public static boolean offered(CelestialBodyDefinition body) {
        Objects.requireNonNull(body, "body");
        return body.capabilities().orbitable() && !CelestialIds.SPACE_ID.equals(body.id());
    }
}
