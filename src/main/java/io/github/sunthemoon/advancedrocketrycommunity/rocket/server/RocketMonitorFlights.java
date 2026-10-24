package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import com.mojang.serialization.JsonOps;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.data.PlanetaryCatalog;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.model.CelestialBodyDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNativeResources;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.monitor.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketOperationLedger;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationState;
import io.github.sunthemoon.advancedrocketrycommunity.station.persistence.StationRegistrySavedData;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationAccessAction;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationAccessService;
import io.github.sunthemoon.advancedrocketrycommunity.travel.model.TravelTarget;
import io.github.sunthemoon.advancedrocketrycommunity.travel.route.model.RouteDefinition;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** Local/offline monitor entry, sharing the ordinary request ledger, rate limiter, planner and launch executor. */
public final class RocketMonitorFlights {
    private static final UUID PREVIEW = UUID.fromString("9cbce38c-fbcb-47ad-8d5d-71a7f37a8110");
    private final RocketFlightService flights;
    private final RocketOperationLedger requests;
    private final RocketIntentRateLimiter rate;
    private final StationAccessService access = new StationAccessService();
    private PlanetaryCatalog hashedCatalog;
    private String catalogHash = "";

    RocketMonitorFlights(RocketFlightService flights, RocketOperationLedger requests, RocketIntentRateLimiter rate) {
        this.flights = flights; this.requests = requests; this.rate = rate;
    }
    public record Target(TravelTarget target, String label) { }

    public List<Target> targets(RocketMonitorBlockEntity monitor) {
        var resolved = RocketMonitorBinding.resolve(monitor, false);
        if (resolved.rocket() == null || !(monitor.getLevel() instanceof ServerLevel level)) { return List.of(); }
        var catalog = flights.captureCatalog();
        var stations = StationRegistrySavedData.get(level.getServer());
        if (!stations.operational()) { return List.of(); }
        var entries = new ArrayList<Target>();
        if (catalog == null) {
            if (flights.configuredCatalogs()) { return List.of(); }
            entries.add(new Target(new TravelTarget.BodySurface(RocketDestination.EARTH.bodyId()), "Earth"));
            entries.add(new Target(new TravelTarget.BodySurface(RocketDestination.MOON.bodyId()), "Moon"));
        } else {
            for (var body : catalog.celestial().definitions()) {
                var target = new TravelTarget.BodySurface(body.id());
                if (body.supportsSurfaceArrival() && PlanetaryFlightAdmission.discovered(level, target, catalog.celestial(), stations)) {
                    entries.add(new Target(target, body.id().toString()));
                }
            }
            for (var station : access.accessibleDestinations(stations.stations(), monitor.root().owner(), false)) {
                var target = new TravelTarget.Station(station.stationId());
                if (PlanetaryFlightAdmission.discovered(level, target, catalog.celestial(), stations)) {
                    entries.add(new Target(target, station.name()));
                }
            }
        }
        return entries.size() <= 160 ? List.copyOf(entries) : List.of();
    }

    public String authority(RocketMonitorBlockEntity monitor, TravelTarget target) {
        var resolved = RocketMonitorBinding.resolve(monitor, false);
        if (resolved.rocket() == null || target == null || !(monitor.getLevel() instanceof ServerLevel level)) { return ""; }
        var stations = StationRegistrySavedData.get(level.getServer());
        if (!stations.operational()) { return ""; }
        var catalog = flights.captureCatalog();
        if (catalog == null && flights.configuredCatalogs()) { return ""; }
        if (catalog != null && !PlanetaryFlightAdmission.discovered(level, target, catalog.celestial(), stations)) { return ""; }
        if (catalog != hashedCatalog || catalogHash.isEmpty()) {
            var text = new StringBuilder("monitor-catalog-1:");
            if (catalog != null) {
                for (var body : catalog.celestial().definitions()) {
                    text.append(canonical(CelestialBodyDefinition.CODEC.encodeStart(JsonOps.INSTANCE, body).result().orElseThrow()));
                }
                for (var route : catalog.routes().definitions()) {
                    text.append(canonical(RouteDefinition.CODEC.encodeStart(JsonOps.INSTANCE, route).result().orElseThrow()));
                }
            }
            catalogHash = RocketNativeResources.hash(text.toString()); hashedCatalog = catalog;
        }
        var root = monitor.root();
        var text = new StringBuilder(effectiveCatalogAuthority(catalogHash)).append(root.owner()).append(root.device()).append(root.binding()).append(target);
        var source = resolved.rocket().flightData().orElseThrow().currentTarget().orElse(null);
        if (source == null) { return ""; }
        for (var location : List.of(source, target)) {
            if (!(location instanceof TravelTarget.Station named)) { continue; }
            StationState station = stations.find(named.instanceId()).orElse(null);
            if (station == null || !access.allowed(station, root.owner(), false, StationAccessAction.VISIT)) { return ""; }
            text.append(station.stationId()).append(station.ownerId()).append(station.orbitBody()).append(station.region())
                    .append(station.orbitalControls());
            station.members().stream().sorted().forEach(text::append);
        }
        return RocketNativeResources.hash(text.toString());
    }

    static String effectiveCatalogAuthority(String planetarySignature) {
        return RocketNativeResources.hash(planetarySignature + ":"
                + io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.RocketFluidFuelRuntime.capture().signature());
    }

    public RocketMonitorQuote quote(RocketMonitorBlockEntity monitor, TravelTarget target) {
        var resolved = RocketMonitorBinding.resolve(monitor, true);
        String stamp = authority(monitor, target);
        if (resolved.rocket() == null || stamp.isEmpty()) { return refused(target, RocketFlightRequestCode.UNAUTHORIZED, stamp); }
        var rocket = resolved.rocket(); var flight = rocket.flightData().orElseThrow();
        if (targets(monitor).stream().noneMatch(entry -> entry.target().equals(target))) {
            return refused(target, RocketFlightRequestCode.INVALID_DESTINATION, stamp);
        }
        if (!flight.launchReady()) { return refused(target, RocketFlightRequestCode.INVALID_STATE, stamp); }
        var planned = flights.plan(rocket, flight, flight.currentTarget().orElseThrow(), target,
                StationRegistrySavedData.get(rocket.getServer()), PREVIEW, flights.captureCatalog());
        return RocketMonitorQuote.planned(target, planned, flight, stamp);
    }
    static long primaryCost(RocketFlightPlanResult planned) {
        return planned.requiredCosts().primary();
    }

    /** One admitted button or edge: a fresh request, never an admin request or a synthetic player. */
    public RocketFlightRequestResult launch(RocketMonitorBlockEntity monitor, RocketMonitorQuote shown, ServerPlayer initiator) {
        if (!(monitor.getLevel() instanceof ServerLevel level) || !level.getServer().isSameThread() || !monitor.live() || monitor.root() == null
                || initiator != null && (!monitor.admitted(initiator) || shown == null)
                || initiator == null && !monitor.root().armed()) {
            return RocketFlightRequestResult.failure(RocketFlightRequestCode.UNAUTHORIZED);
        }
        var rocket = RocketMonitorBinding.resolve(monitor, true).rocket();
        if (rocket == null) { return RocketFlightRequestResult.failure(RocketFlightRequestCode.ENTITY_UNAVAILABLE); }
        var target = initiator == null ? monitor.root().destination() : shown.target();
        String stamp = initiator == null ? monitor.root().authority() : shown.authority();
        if (stamp.isEmpty() || !stamp.equals(authority(monitor, target))) {
            return RocketFlightRequestResult.failure(RocketFlightRequestCode.UNAUTHORIZED);
        }
        UUID owner = monitor.root().owner();
        if (rate.check(owner, level.getGameTime()) != RocketIntentRateLimiter.Decision.ALLOWED) {
            return RocketFlightRequestResult.failure(RocketFlightRequestCode.RATE_LIMITED);
        }
        // Rate-limit unattended edges before route planning. A local button still confirms its displayed quote.
        var checked = quote(monitor, target);
        if (initiator != null && !shown.same(checked)) {
            return RocketFlightRequestResult.failure(RocketFlightRequestCode.UNAUTHORIZED);
        }
        UUID request = UUID.randomUUID();
        if (requests.begin(request) != RocketOperationLedger.BeginResult.STARTED) {
            return RocketFlightRequestResult.failure(RocketFlightRequestCode.REQUEST_LEDGER_FULL);
        }
        monitor.requestStarted(request);
        var result = flights.launch(rocket, target, owner, false, request, null, null, () ->
                monitor.live() && stamp.equals(authority(monitor, target))
                && (initiator == null || monitor.admitted(initiator)), owner);
        if (result.code() != RocketFlightRequestCode.JOURNAL_PENDING) { requests.finish(request, result.success()); }
        return result.success() || result.code() == RocketFlightRequestCode.JOURNAL_PENDING
                ? new RocketFlightRequestResult(result.code(), checked.cost()) : result;
    }
    /** Transient display only; the shared ledger and transfer journal remain authoritative. */
    public RocketFlightRequestCode completion(UUID request) {
        var outcome = requests.outcome(request);
        if (outcome == null) { return RocketFlightRequestCode.ENTITY_UNAVAILABLE; }
        return switch (outcome) {
            case ACTIVE -> RocketFlightRequestCode.JOURNAL_PENDING;
            case SUCCEEDED -> RocketFlightRequestCode.SUCCESS;
            case FAILED -> RocketFlightRequestCode.TRANSFER_JOURNAL_BLOCKED;
        };
    }
    private static RocketMonitorQuote refused(TravelTarget target, RocketFlightRequestCode code, String stamp) {
        return new RocketMonitorQuote(target, code, -1, null, null, stamp, RocketRequiredCosts.UNKNOWN);
    }

    /** Catalog maps may iterate differently after restart; equivalent validated content must retain arming. */
    static com.google.gson.JsonElement canonical(com.google.gson.JsonElement value) {
        if (value.isJsonObject()) {
            var sorted = new com.google.gson.JsonObject();
            value.getAsJsonObject().entrySet().stream().sorted(java.util.Map.Entry.comparingByKey())
                    .forEach(entry -> sorted.add(entry.getKey(), canonical(entry.getValue())));
            return sorted;
        }
        if (value.isJsonArray()) {
            var array = new com.google.gson.JsonArray(); value.getAsJsonArray().forEach(child -> array.add(canonical(child))); return array;
        }
        return value;
    }
}
