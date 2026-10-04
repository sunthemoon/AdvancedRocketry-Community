package io.github.sunthemoon.advancedrocketrycommunity.atmosphere.instrument;

import java.util.Objects;
import net.minecraft.server.level.ServerPlayer;

/** One lifecycle-managed handle, not a world/player collection or atmosphere truth store. */
public final class AtmosphereAnalyzerRuntime {
    private static volatile AtmosphereAnalyzerService active;

    static synchronized void install(AtmosphereAnalyzerService service) {
        Objects.requireNonNull(service, "service");
        if (active != null && active != service) {
            throw new IllegalStateException("An analyzer host is already installed");
        }
        active = service;
    }

    static synchronized void remove(AtmosphereAnalyzerService service) {
        if (active == service) { active = null; }
    }

    public static AnalyzerReading read(ServerPlayer player) {
        AtmosphereAnalyzerService service = active;
        return service == null ? AnalyzerReading.unavailable() : service.read(player);
    }

    private AtmosphereAnalyzerRuntime() { }
}
