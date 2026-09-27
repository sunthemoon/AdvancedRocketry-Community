package io.github.sunthemoon.advancedrocketrycommunity.compat.environment;

import io.github.sunthemoon.advancedrocketrycommunity.api.environment.ServerEnvironmentReadyEvent;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalogManager;
import io.github.sunthemoon.advancedrocketrycommunity.station.persistence.StationRegistrySavedData;
import java.util.Objects;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;

/** Mod-owned adapter; public handles are never rebound to a subsequent logical server. */
public final class EnvironmentQueryLifecycle {
    private final CelestialCatalogManager catalogs;
    private ServerEnvironmentQueries queries;

    public EnvironmentQueryLifecycle(CelestialCatalogManager catalogs) {
        this.catalogs = Objects.requireNonNull(catalogs, "catalogs");
    }

    public void onServerStarted(ServerStartedEvent event) {
        close();
        var server = event.getServer();
        // SavedData is acquired once at startup, never loaded as a side effect of a query.
        queries = new ServerEnvironmentQueries(server::isSameThread, key -> server.getLevel(key) != null,
                catalogs, StationRegistrySavedData.get(server));
        MinecraftForge.EVENT_BUS.post(new ServerEnvironmentReadyEvent(queries));
    }

    public void onServerStopping(ServerStoppingEvent event) {
        close();
    }

    public void onServerStopped(ServerStoppedEvent event) {
        close();
    }

    private void close() {
        if (queries != null) {
            queries.close();
            queries = null;
        }
    }
}
