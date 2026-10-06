package io.github.sunthemoon.advancedrocketrycommunity.atmosphere.instrument;

import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.server.AtmosphereManager;
import io.github.sunthemoon.advancedrocketrycommunity.compat.atmosphere.AtmosphereBoundaryCatalog;
import java.util.Objects;
import java.util.function.Supplier;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;

/** Root wires startup after manager setup and exact-owner closure before atmosphere clearing. */
public final class SealDetectorLifecycle {
    private final Supplier<AtmosphereManager> atmospheres;
    private final Supplier<AtmosphereBoundaryCatalog> boundaries;
    private SealDetectorService service;

    public SealDetectorLifecycle(Supplier<AtmosphereManager> atmospheres, Supplier<AtmosphereBoundaryCatalog> boundaries) {
        this.atmospheres = Objects.requireNonNull(atmospheres, "atmospheres");
        this.boundaries = Objects.requireNonNull(boundaries, "boundaries");
    }

    public void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        if (!server.isSameThread()) { throw new IllegalStateException("Detector startup requires the host thread"); }
        if (service != null) { throw new IllegalStateException("Detector lifecycle is already started"); }
        AtmosphereManager manager = atmospheres.get();
        AtmosphereBoundaryCatalog catalog = boundaries.get();
        if (manager == null || catalog == null) { return; }
        SealDetectorService candidate = new SealDetectorService(server, manager, catalog);
        try {
            SealDetectorRuntime.install(candidate);
            service = candidate;
        } catch (RuntimeException failure) {
            candidate.close();
            throw failure;
        }
    }

    public void onServerStopping(ServerStoppingEvent event) { close(event.getServer()); }
    public void onServerStopped(ServerStoppedEvent event) { close(event.getServer()); }

    private void close(MinecraftServer server) {
        if (service != null && service.owns(server)) {
            SealDetectorRuntime.remove(service);
            service.close();
            service = null;
        }
    }
}
