package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.data.PlanetaryCatalog;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.data.PlanetaryCatalogManager;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.network.CelestialSnapshotPacket;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightPlanResult;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.menu.RocketFlightQuotes;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.menu.RocketNavigation;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.menu.RocketNavigationStatus;
import io.github.sunthemoon.advancedrocketrycommunity.station.persistence.StationRegistrySavedData;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationAccessAction;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationAccessService;
import io.github.sunthemoon.advancedrocketrycommunity.travel.migration.LegacyTravelTargetAdapter;
import io.github.sunthemoon.advancedrocketrycommunity.travel.model.TravelTarget;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;

/** Bounded read-only navigation over one catalog generation, using the launch planner itself. */
final class RocketNavigationService {
    private static final UUID PREVIEW_ID = UUID.fromString("123e4567-e89b-42d3-a456-426614174721");
    private final PlanetaryCatalogManager catalogs;
    private final boolean configuredCatalogs;
    private final Planner planner;
    private final StationAccessService access = new StationAccessService();
    private final RocketCatalogRefreshLimiter refreshes = new RocketCatalogRefreshLimiter();
    private long encodedGeneration = -1;
    private CelestialSnapshotPacket encodedCatalog;

    RocketNavigationService(PlanetaryCatalogManager catalogs, boolean configuredCatalogs, Planner planner) {
        this.catalogs = catalogs;
        this.configuredCatalogs = configuredCatalogs;
        this.planner = java.util.Objects.requireNonNull(planner, "planner");
    }

    RocketNavigation snapshot(ServerPlayer player, RocketEntity rocket, boolean controlsRocket) {
        var flight = rocket.flightData().orElse(null);
        if (flight == null || rocket.snapshot().isEmpty()) { return RocketNavigation.empty(); }
        var captured = catalogs == null ? null : catalogs.capture().orElse(null);
        if (captured == null) {
            if (configuredCatalogs) { return RocketNavigation.empty(); }
            var source = flight.currentTarget().flatMap(LegacyTravelTargetAdapter::toLegacy)
                    .map(LegacyTravelTargetAdapter.LegacyDestination::destination).orElse(null);
            var quotes = RocketFlightQuotes.compute(rocket.snapshot().orElseThrow().stats(), flight.fuel(), source, flight.state());
            if (!controlsRocket) {
                quotes = new RocketFlightQuotes(quotes.entries().stream().map(entry -> new RocketFlightQuotes.TargetQuote(
                        entry.target(), new RocketFlightQuotes.Quote(entry.quote().requiredFuel(), RocketNavigationStatus.UNAUTHORIZED))).toList());
            }
            return new RocketNavigation(0, quotes, List.of());
        }
        var pair = captured.catalog();
        var stations = StationRegistrySavedData.get(player.getServer());
        if (!stations.operational()) { return RocketNavigation.empty(); }
        var visibleStations = access.accessibleDestinations(stations.stations(), player.getUUID(), player.hasPermissions(2))
                .stream().filter(station -> pair.celestial().get(station.orbitBody())
                        .filter(body -> body.capabilities().orbitable()).isPresent()).toList();
        var targets = new ArrayList<TravelTarget>();
        pair.celestial().definitions().stream().filter(body -> body.supportsSurfaceArrival())
                .filter(body -> !body.id().equals(CelestialIds.SPACE_ID))
                .map(body -> new TravelTarget.BodySurface(body.id())).forEach(targets::add);
        visibleStations.stream().map(station -> new TravelTarget.Station(station.stationId())).forEach(targets::add);
        if (targets.size() > RocketFlightQuotes.MAX_QUOTES) { return RocketNavigation.empty(); }
        TravelTarget source = flight.currentTarget().orElse(null);
        if (source == null) { return RocketNavigation.empty(); }
        boolean authorized = controlsRocket && (!(source instanceof TravelTarget.Station station)
                || stations.find(station.instanceId()).filter(value -> access.allowed(value, player.getUUID(),
                player.hasPermissions(2), StationAccessAction.VISIT)).isPresent());
        boolean launchableState = flight.state() == RocketFlightState.FUELED
                || flight.state() == RocketFlightState.LANDED && flight.fuel().amount() > 0;
        var entries = new ArrayList<RocketFlightQuotes.TargetQuote>(targets.size());
        for (var target : targets) {
            var result = planner.plan(rocket, flight, source, target, stations, PREVIEW_ID, pair);
            entries.add(new RocketFlightQuotes.TargetQuote(target, new RocketFlightQuotes.Quote(
                    Math.toIntExact(result.requiredFuel()), RocketNavigationStatus.resolve(result.code(), launchableState, authorized))));
        }
        return new RocketNavigation(captured.generation(), new RocketFlightQuotes(entries),
                visibleStations.stream().map(RocketNavigation.Station::from).toList());
    }

    Optional<CelestialSnapshotPacket> catalog(ServerPlayer player) {
        var captured = catalogs == null ? null : catalogs.capture().orElse(null);
        long now = Math.max(0, player.getServer().overworld().getGameTime());
        if (captured == null || !refreshes.acquire(player.getUUID(), now)) { return Optional.empty(); }
        if (captured.generation() != encodedGeneration) {
            encodedGeneration = captured.generation();
            var result = CelestialSnapshotPacket.fromCatalog(captured.catalog().celestial(), captured.generation());
            encodedCatalog = result.result().orElse(null);
            result.error().ifPresent(error -> AdvancedRocketryCommunity.LOGGER.error("Navigation catalog encoding: {}", error.message()));
        }
        return Optional.ofNullable(encodedCatalog);
    }

    void remove(UUID player) { refreshes.remove(player); }

    void clear() {
        refreshes.clear();
        encodedGeneration = -1;
        encodedCatalog = null;
    }

    @FunctionalInterface
    interface Planner {
        RocketFlightPlanResult plan(RocketEntity rocket, RocketFlightData flight, TravelTarget source,
                                    TravelTarget target, StationRegistrySavedData stations, UUID requestId, PlanetaryCatalog pair);
    }
}
