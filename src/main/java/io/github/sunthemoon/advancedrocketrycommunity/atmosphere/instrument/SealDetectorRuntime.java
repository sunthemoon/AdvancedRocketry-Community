package io.github.sunthemoon.advancedrocketrycommunity.atmosphere.instrument;

import java.util.Objects;
import net.minecraft.world.item.context.UseOnContext;

/** One lifecycle-owned reader reference, never a world/player collection or persistence authority. */
final class SealDetectorRuntime {
    private static volatile SealDetectorService active;

    static synchronized void install(SealDetectorService service) {
        Objects.requireNonNull(service, "service");
        if (active != null && active != service) {
            throw new IllegalStateException("A detector host is already installed");
        }
        active = service;
    }

    static synchronized void remove(SealDetectorService service) {
        if (active == service) { active = null; }
    }

    static SealDetectorReading read(UseOnContext context) {
        SealDetectorService service = active;
        return service == null ? SealDetectorReading.unavailable() : service.read(context);
    }

    private SealDetectorRuntime() { }
}
