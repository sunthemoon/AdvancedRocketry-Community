package io.github.sunthemoon.advancedrocketrycommunity.satellite.service;

import java.util.Optional;

/** Narrow bridge from block entities to the ADR-051 service; cleared with the server's other runtimes. */
public final class ResourceMissionRuntime {
    private static volatile ResourceMissionService service;

    private ResourceMissionRuntime() {
    }

    public static void install(ResourceMissionService installed) {
        service = installed;
    }

    public static Optional<ResourceMissionService> service() {
        return Optional.ofNullable(service);
    }

    /** Clears the service's per-server state; the service itself stays installed for the next world. */
    public static void clear() {
        ResourceMissionService current = service;
        if (current != null) {
            current.clear();
        }
    }
}
