package io.github.sunthemoon.advancedrocketrycommunity.celestial.data;

import com.mojang.serialization.DataResult;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalog;
import io.github.sunthemoon.advancedrocketrycommunity.travel.route.model.RouteAnchor;
import io.github.sunthemoon.advancedrocketrycommunity.travel.route.service.RouteCatalog;
import java.util.List;
import java.util.Objects;

/** One validated immutable definition/route pair, including its own derived route cache. */
public final class PlanetaryCatalog {
    private final CelestialCatalog celestial;
    private final RouteCatalog routes;

    private PlanetaryCatalog(CelestialCatalog celestial, RouteCatalog routes) {
        this.celestial = celestial;
        this.routes = routes;
    }

    public static DataResult<PlanetaryCatalog> create(CelestialCatalog celestial, RouteCatalog routes) {
        Objects.requireNonNull(celestial, "celestial");
        Objects.requireNonNull(routes, "routes");
        for (var route : routes.definitions()) {
            var fromSystem = celestial.systemOf(route.from().bodyId());
            var toSystem = celestial.systemOf(route.to().bodyId());
            if (fromSystem.isPresent() && toSystem.isPresent() && !fromSystem.equals(toSystem)) {
                // ADR-043: rockets never cross star systems; only station warp does.
                return DataResult.error(() -> "Route " + route.id() + " connects systems "
                        + fromSystem.get() + " and " + toSystem.get());
            }
            for (var anchor : List.of(route.from(), route.to())) {
                var body = celestial.get(anchor.bodyId()).orElse(null);
                if (body == null) {
                    return DataResult.error(() -> "Route " + route.id() + " references missing body " + anchor.bodyId());
                }
                if (anchor.kind() == RouteAnchor.Kind.BODY_SURFACE
                        && (body.levelKey().isEmpty() || body.capabilities().gasGiant())) {
                    return DataResult.error(() -> "Route " + route.id()
                            + " requires a mapped non-gas surface for " + anchor.bodyId());
                }
                // Arrival flags do not erase valid departure sources or reverse edges.
            }
        }
        return DataResult.success(new PlanetaryCatalog(celestial, routes));
    }

    public CelestialCatalog celestial() {
        return celestial;
    }

    public RouteCatalog routes() {
        return routes;
    }
}
