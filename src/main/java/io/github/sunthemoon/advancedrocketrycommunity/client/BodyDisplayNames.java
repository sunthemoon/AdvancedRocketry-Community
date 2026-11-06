package io.github.sunthemoon.advancedrocketrycommunity.client;

import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.network.CelestialClientCache;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.network.CelestialSnapshot;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/** Display-only body names, retaining unknown datapack IDs as readable fallbacks. */
public final class BodyDisplayNames {
    public static Component bodyName(String value) {
        ResourceLocation id = ResourceLocation.tryParse(value);
        return id == null ? Component.literal(value) : Component.translatableWithFallback(
                "body." + id.getNamespace() + "." + id.getPath().replace('/', '.'), value);
    }

    static Component levelName(String value) {
        return levelName(value, CelestialClientCache.snapshot());
    }

    static Component levelName(String value, Optional<CelestialSnapshot> snapshot) {
        ResourceLocation id = ResourceLocation.tryParse(value);
        if (id == null) { return Component.literal(value); }
        Optional<ResourceLocation> body = snapshot.stream().flatMap(view -> view.entries().stream())
                .filter(entry -> entry.levelId().filter(id::equals).isPresent())
                .map(CelestialSnapshot.Entry::bodyId).findFirst();
        // The fixed Earth Level has a different namespace and path from its logical body.
        return bodyName(body.orElse(id.equals(Level.OVERWORLD.location()) ? CelestialIds.EARTH_ID : id).toString());
    }

    private BodyDisplayNames() { }
}
