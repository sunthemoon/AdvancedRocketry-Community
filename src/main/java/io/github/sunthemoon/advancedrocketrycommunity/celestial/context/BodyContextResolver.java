package io.github.sunthemoon.advancedrocketrycommunity.celestial.context;

import io.github.sunthemoon.advancedrocketrycommunity.celestial.model.CelestialBodyDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalog;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalogManager;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Ordered server resolver: authoritative instance/region, unambiguous Level, unresolved. */
public final class BodyContextResolver {
    public static final int MAX_INSTANCE_RESOLVERS = 16;

    private final CelestialCatalogManager catalogs;
    private final List<InstanceBodyContextResolver> instanceResolvers;

    public BodyContextResolver(
            CelestialCatalogManager catalogs,
            List<InstanceBodyContextResolver> instanceResolvers
    ) {
        this.catalogs = Objects.requireNonNull(catalogs, "catalogs");
        this.instanceResolvers = List.copyOf(instanceResolvers);
        if (this.instanceResolvers.size() > MAX_INSTANCE_RESOLVERS) {
            throw new IllegalArgumentException("Body context resolver count exceeds its fixed bound");
        }
    }

    public Optional<BodyContext> resolve(WorldLocation location) {
        Objects.requireNonNull(location, "location");
        CelestialCatalog catalog = catalogs.current().orElse(null);
        if (catalog == null) {
            return Optional.empty();
        }

        for (InstanceBodyContextResolver resolver : instanceResolvers) {
            BodyContextResolution result = Objects.requireNonNull(
                    resolver.resolve(location),
                    "instance resolver result"
            );
            if (result.status() == BodyContextResolution.Status.UNRESOLVED) {
                return Optional.empty();
            }
            if (result.status() == BodyContextResolution.Status.RESOLVED) {
                BodyContext context = result.context().orElseThrow();
                return catalog.get(context.bodyId()).isPresent()
                        ? Optional.of(context)
                        : Optional.empty();
            }
        }

        List<CelestialBodyDefinition> candidates = catalog.candidatesForLevel(location.levelKey());
        return candidates.size() == 1
                ? Optional.of(BodyContext.surface(candidates.get(0).id()))
                : Optional.empty();
    }
}
