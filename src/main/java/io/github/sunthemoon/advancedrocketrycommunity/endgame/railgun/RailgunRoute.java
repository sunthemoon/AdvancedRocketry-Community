package io.github.sunthemoon.advancedrocketrycommunity.endgame.railgun;

import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameCode;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

/**
 * ADR-056 section 3 as pure decisions: the route rule (an {@code ACTIVE} railgun endpoint other than the source, of
 * the source's owner unless an operator selects it, with both bodies available and in one star system) and the route
 * class with its cost and travel time, fixed at escrow. Distances are integer arithmetic in 64 bits.
 */
public final class RailgunRoute {
    public static final int LOCAL_BASE_FE = 25_000;
    public static final int LOCAL_FE_PER_BLOCK = 10;
    public static final int MAX_LOCAL_FE = 250_000;
    public static final int ORBITAL_FE = 250_000;
    public static final int MIN_TRAVEL_TICKS = 20;
    public static final int MAX_LOCAL_TRAVEL_TICKS = 200;
    public static final int ORBITAL_TRAVEL_TICKS = 600;
    public static final int BLOCKS_PER_TRAVEL_TICK = 64;

    private RailgunRoute() {
    }

    public enum RouteClass {
        /** Same Level and same body. */
        LOCAL,
        /** Otherwise, inside one star system. */
        ORBITAL
    }

    /** What a launch along a route costs and how long it travels, with the energy percent applied to the cost. */
    public record Quote(RouteClass routeClass, int cost, int travel) {
        public Quote {
            Objects.requireNonNull(routeClass, "routeClass");
        }
    }

    /**
     * Where an endpoint is, as its live body context gives it: its Level, its position, and its body and that body's
     * star system (both empty when no body context resolves).
     */
    public record Place(ResourceLocation level, long pos, Optional<ResourceLocation> body,
                        Optional<ResourceLocation> system) {
        public Place {
            Objects.requireNonNull(level, "level");
            Objects.requireNonNull(body, "body");
            Objects.requireNonNull(system, "system");
        }

        boolean available() {
            return body.isPresent() && system.isPresent();
        }
    }

    /** The integer horizontal distance {@code floor(sqrt(dx² + dz²))}. */
    public static long distance(long dx, long dz) {
        long square = Math.addExact(Math.multiplyExact(dx, dx), Math.multiplyExact(dz, dz));
        long root = (long) Math.sqrt((double) square);
        // Corrected by division, so no product overflows near the top of the range.
        while (root > 0 && root > square / root) {
            root--;
        }
        while (root + 1 <= square / (root + 1)) {
            root++;
        }
        return root;
    }

    /** The class, cost (times {@code percent / 100}) and travel of a route; {@code local} means same Level and body. */
    public static Quote quote(boolean local, long dx, long dz, int percent) {
        if (percent < RailgunSettings.MIN_PERCENT || percent > RailgunSettings.MAX_PERCENT) {
            throw new IllegalArgumentException("The railgun energy percent is outside 10..400");
        }
        if (!local) {
            return new Quote(RouteClass.ORBITAL, ORBITAL_FE * percent / 100, ORBITAL_TRAVEL_TICKS);
        }
        long d = distance(dx, dz);
        long cost = Math.min(LOCAL_BASE_FE + LOCAL_FE_PER_BLOCK * d, MAX_LOCAL_FE);
        long travel = Math.min(Math.max(MIN_TRAVEL_TICKS + d / BLOCKS_PER_TRAVEL_TICK, MIN_TRAVEL_TICKS),
                MAX_LOCAL_TRAVEL_TICKS);
        return new Quote(RouteClass.LOCAL, (int) (cost * percent / 100), (int) travel);
    }

    /** The quote between two places that passed {@link #check}. */
    public static Quote quote(Place from, Place to, int percent) {
        boolean local = from.level().equals(to.level()) && from.body().equals(to.body());
        BlockPos a = BlockPos.of(from.pos());
        BlockPos b = BlockPos.of(to.pos());
        return quote(local, (long) a.getX() - b.getX(), (long) a.getZ() - b.getZ(), percent);
    }

    /**
     * A destination candidate as the endgame index shows it.
     *
     * @param railgun whether its kind is {@code railgun}
     * @param active  whether its index state is {@code ACTIVE}
     */
    public record Candidate(UUID id, boolean railgun, boolean active, UUID owner) {
        public Candidate {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(owner, "owner");
        }
    }

    /**
     * The route rule, first failure wins: {@code NO_TARGET}, {@code TARGET_FOREIGN}, {@code BODY_UNAVAILABLE},
     * {@code ROUTE_OUT_OF_SYSTEM}. {@code allowSource} is the redirect case, where cargo may go back to its source.
     */
    public static EndgameCode check(UUID source, UUID sourceOwner, Place from, Candidate to, Place toPlace,
                                    boolean operator, boolean allowSource) {
        if (!to.railgun() || !to.active() || !allowSource && to.id().equals(source)) {
            return EndgameCode.NO_TARGET;
        }
        if (!operator && !to.owner().equals(sourceOwner)) {
            return EndgameCode.TARGET_FOREIGN;
        }
        if (!from.available() || !toPlace.available()) {
            return EndgameCode.BODY_UNAVAILABLE;
        }
        return from.system().equals(toPlace.system()) ? EndgameCode.OK : EndgameCode.ROUTE_OUT_OF_SYSTEM;
    }
}
