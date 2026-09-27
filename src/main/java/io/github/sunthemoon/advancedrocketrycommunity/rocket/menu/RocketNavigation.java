package io.github.sunthemoon.advancedrocketrycommunity.rocket.menu;

import io.github.sunthemoon.advancedrocketrycommunity.celestial.model.BoundedCelestialCodecs;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationDestinationSummary;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationLimits;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationState;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;

/** One bounded server view. Generation zero is unavailable/legacy, never a coherent star map. */
public record RocketNavigation(long generation, RocketFlightQuotes quotes, List<Station> stations) {
    public static final int REFRESH_TICKS = 20;
    public static final int CATALOG_REFRESH_TICKS = 100;
    public static final int REFRESH_BUTTON = 0;

    public RocketNavigation {
        if (generation < 0) { throw new IllegalArgumentException("Negative navigation generation"); }
        Objects.requireNonNull(quotes, "quotes");
        if (stations.size() > StationLimits.MAX_ACCESSIBLE_DESTINATIONS) {
            throw new IllegalArgumentException("Navigation station count exceeds the bound");
        }
        stations = List.copyOf(stations);
        var ids = new HashSet<UUID>();
        for (var station : stations) {
            if (!ids.add(station.stationId())) {
                throw new IllegalArgumentException("Duplicate navigation station");
            }
        }
    }

    public static RocketNavigation empty() { return new RocketNavigation(0, RocketFlightQuotes.empty(), List.of()); }

    public boolean coherent(long catalogGeneration, boolean validCatalog) {
        return validCatalog && generation > 0 && generation == catalogGeneration;
    }

    public record Station(UUID stationId, String name, ResourceLocation orbitBody) {
        public Station {
            Objects.requireNonNull(stationId, "stationId");
            new io.github.sunthemoon.advancedrocketrycommunity.travel.model.TravelTarget.Station(stationId);
            name = new StationDestinationSummary(stationId, name).name();
            BoundedCelestialCodecs.requireId(orbitBody, "station orbit body");
        }

        public StationDestinationSummary summary() { return new StationDestinationSummary(stationId, name); }

        public static Station from(StationState state) {
            return new Station(state.stationId(), state.name(), state.orbitBody());
        }
    }
}
