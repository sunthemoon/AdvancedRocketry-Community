package io.github.sunthemoon.advancedrocketrycommunity.station.warp;

import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalog;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/**
 * ADR-044 §2 cost class, derived from the catalog captured at the time of the quote or commit:
 * a warp within one star system, or between systems (also when leaving an unavailable orbit body).
 */
public enum WarpCostClass {
    IN_SYSTEM("in-system"),
    INTERSTELLAR("interstellar");

    private final String label;

    WarpCostClass(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static WarpCostClass of(CelestialCatalog catalog, ResourceLocation currentBody, ResourceLocation target) {
        Objects.requireNonNull(catalog, "catalog");
        Optional<ResourceLocation> from = catalog.systemOf(Objects.requireNonNull(currentBody, "currentBody"));
        Optional<ResourceLocation> to = catalog.systemOf(Objects.requireNonNull(target, "target"));
        return from.isPresent() && from.equals(to) ? IN_SYSTEM : INTERSTELLAR;
    }
}
