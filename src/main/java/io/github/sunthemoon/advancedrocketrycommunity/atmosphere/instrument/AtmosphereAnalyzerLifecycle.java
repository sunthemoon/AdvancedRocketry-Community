package io.github.sunthemoon.advancedrocketrycommunity.atmosphere.instrument;

import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.server.AtmosphereManager;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.context.BodyContextResolver;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalogManager;
import io.github.sunthemoon.advancedrocketrycommunity.station.orbit.StationRegionBodyContextResolver;
import io.github.sunthemoon.advancedrocketrycommunity.station.persistence.StationRegistrySavedData;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;

/** Acquires committed station lookup only at startup, before exposing the read-only item handle. */
public final class AtmosphereAnalyzerLifecycle {
    private final CelestialCatalogManager catalogs;
    private final Supplier<AtmosphereManager> atmospheres;
    private AtmosphereAnalyzerService service;

    public AtmosphereAnalyzerLifecycle(CelestialCatalogManager catalogs, Supplier<AtmosphereManager> atmospheres) {
        this.catalogs = Objects.requireNonNull(catalogs, "catalogs");
        this.atmospheres = Objects.requireNonNull(atmospheres, "atmospheres");
    }

    public void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        if (!server.isSameThread()) { throw new IllegalStateException("Analyzer startup requires the host thread"); }
        if (service != null) { throw new IllegalStateException("Analyzer lifecycle is already started"); }
        AtmosphereManager manager = atmospheres.get();
        if (manager == null) { return; }
        var stations = StationRegistrySavedData.get(server);
        var contexts = new BodyContextResolver(catalogs, List.of(new StationRegionBodyContextResolver(stations::findAt)));
        AtmosphereAnalyzerService candidate = new AtmosphereAnalyzerService(server, catalogs, manager, contexts);
        try {
            AtmosphereAnalyzerRuntime.install(candidate);
            service = candidate;
        } catch (RuntimeException failed) {
            candidate.close();
            throw failed;
        }
    }

    public void onServerStopping(ServerStoppingEvent event) { close(event.getServer()); }
    public void onServerStopped(ServerStoppedEvent event) { close(event.getServer()); }

    private void close(MinecraftServer server) {
        if (service != null && service.owns(server)) {
            AtmosphereAnalyzerRuntime.remove(service);
            service.close();
            service = null;
        }
    }
}
