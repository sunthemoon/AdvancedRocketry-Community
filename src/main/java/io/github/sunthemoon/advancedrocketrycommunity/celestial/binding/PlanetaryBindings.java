package io.github.sunthemoon.advancedrocketrycommunity.celestial.binding;

import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.model.BoundedCelestialCodecs;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalog;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/** Immutable world identity reservations; absence from a catalog never releases a binding. */
public final class PlanetaryBindings {
    public static final int MAX_BINDINGS = 128;
    private final Map<ResourceLocation, Binding> bindings;

    private PlanetaryBindings(Collection<Binding> entries) {
        if (entries.size() > MAX_BINDINGS) {
            throw new IllegalArgumentException("Planetary lifetime bindings exceed " + MAX_BINDINGS);
        }
        var byBody = new LinkedHashMap<ResourceLocation, Binding>();
        var owners = new LinkedHashMap<ResourceLocation, ResourceLocation>();
        for (Binding binding : entries.stream().sorted((a, b) -> a.bodyId().compareTo(b.bodyId())).toList()) {
            if (byBody.putIfAbsent(binding.bodyId(), binding) != null) {
                throw new IllegalArgumentException("Duplicate planetary binding: " + binding.bodyId());
            }
            binding.level().ifPresent(level -> {
                ResourceLocation owner = owners.putIfAbsent(level, binding.bodyId());
                if (owner != null) {
                    throw new IllegalArgumentException("Level " + level + " has conflicting body bindings: "
                            + owner + " and " + binding.bodyId());
                }
            });
        }
        requireFixed(byBody, CelestialIds.EARTH_ID, Level.OVERWORLD.location());
        requireFixed(byBody, CelestialIds.MOON_ID, CelestialIds.MOON_LEVEL.location());
        requireFixed(byBody, CelestialIds.SPACE_ID, CelestialIds.SPACE_LEVEL.location());
        bindings = java.util.Collections.unmodifiableMap(byBody);
    }

    public static PlanetaryBindings restore(Collection<Binding> entries) {
        return new PlanetaryBindings(entries);
    }

    /** Prospective first adoption, not reconstruction of pre-ledger history. */
    public static PlanetaryBindings adopt(CelestialCatalog catalog) {
        requireBaseline(catalog);
        return restore(catalog.definitions().stream()
                .map(body -> new Binding(body.id(), body.levelKey().map(key -> key.location()))).toList());
    }

    public PlanetaryBindings reconcile(CelestialCatalog catalog) {
        requireBaseline(catalog);
        var next = new LinkedHashMap<>(bindings);
        for (var body : catalog.definitions()) {
            Binding candidate = new Binding(body.id(), body.levelKey().map(key -> key.location()));
            Binding existing = next.putIfAbsent(body.id(), candidate);
            if (existing != null && !existing.equals(candidate)) {
                throw new IllegalArgumentException("Body " + body.id() + " cannot change its recorded Level binding");
            }
        }
        return next.size() == bindings.size() ? this : restore(next.values());
    }

    public List<Binding> entries() {
        return List.copyOf(bindings.values());
    }

    private static void requireBaseline(CelestialCatalog catalog) {
        catalog.requireFixedBaseline().error().ifPresent(error -> {
            throw new IllegalArgumentException(error.message());
        });
    }

    private static void requireFixed(Map<ResourceLocation, Binding> entries, ResourceLocation body, ResourceLocation level) {
        if (!new Binding(body, Optional.of(level)).equals(entries.get(body))) {
            throw new IllegalArgumentException("Missing or changed fixed planetary binding: " + body);
        }
    }

    public record Binding(ResourceLocation bodyId, Optional<ResourceLocation> level) {
        public Binding {
            BoundedCelestialCodecs.requireId(bodyId, "body_id");
            Objects.requireNonNull(level, "level");
            level.ifPresent(id -> BoundedCelestialCodecs.requireId(id, "level"));
        }
    }
}
