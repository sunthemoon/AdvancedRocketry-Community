package io.github.sunthemoon.advancedrocketrycommunity.celestial.visual;

import io.github.sunthemoon.advancedrocketrycommunity.celestial.network.CelestialSnapshot;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/** One-entry cache: immutable snapshot/resource identities invalidate it without per-frame scans. */
public final class SkySelection {
    private CelestialSnapshot previousSnapshot;
    private Map<ResourceLocation, SkyProfile> previousProfiles;
    private ResourceLocation previousLevel;
    private Optional<ResourceLocation> previousOrbit = Optional.empty();
    private Selection selected;

    public Selection resolve(ResourceLocation level, CelestialSnapshot snapshot, Map<ResourceLocation, SkyProfile> profiles) {
        return resolve(level, snapshot, profiles, Optional.empty());
    }

    /**
     * ADR-047: with a station sky context, the Level's sky also shows the orbited body, and the sun
     * follows that body's solar intensity (as the server's environment does, with or without a sky
     * profile). A body missing from the snapshot adds nothing. The colour comes from
     * {@link OrbitalAppearance}. The orbit is part of the cache key, by value.
     */
    public Selection resolve(ResourceLocation level, CelestialSnapshot snapshot, Map<ResourceLocation, SkyProfile> profiles,
                             Optional<ResourceLocation> orbit) {
        if (level != null && level.equals(previousLevel) && snapshot == previousSnapshot && profiles == previousProfiles
                && orbit.equals(previousOrbit)) {
            return selected;
        }
        previousLevel = level;
        previousSnapshot = snapshot;
        previousProfiles = profiles;
        previousOrbit = orbit;
        selected = null;
        if (level == null || snapshot == null || snapshot.entries().size() > SkyProfile.MAX_PROFILES) {
            return null;
        }
        CelestialSnapshot.Entry match = null;
        for (var body : snapshot.entries()) {
            if (body.levelId().filter(level::equals).isPresent()) {
                if (match != null) {
                    return null;
                }
                match = body;
            }
        }
        if (match != null) {
            SkyProfile profile = profiles.get(match.visualProfile());
            if (profile != null) {
                Optional<Orbit> orbited = orbit.flatMap(body -> orbited(body, snapshot, profiles));
                selected = new Selection(match.bodyId(), match.visualProfile(), profile, match.pressure(),
                        orbited.map(Orbit::solarIntensity).orElse(match.solarIntensity()), orbited);
            }
        }
        return selected;
    }

    private static Optional<Orbit> orbited(ResourceLocation body, CelestialSnapshot snapshot,
                                           Map<ResourceLocation, SkyProfile> profiles) {
        for (var entry : snapshot.entries()) {
            if (entry.bodyId().equals(body)) {
                int color = OrbitalAppearance.color(entry.visualProfile(), profiles.get(entry.visualProfile()));
                return Optional.of(new Orbit(entry.bodyId(), color, entry.solarIntensity()));
            }
        }
        return Optional.empty();
    }

    public void clear() {
        previousLevel = null;
        previousSnapshot = null;
        previousProfiles = null;
        previousOrbit = Optional.empty();
        selected = null;
    }

    /** {@code solarIntensity} is the effective sun: the orbited body's when there is one. */
    public record Selection(ResourceLocation bodyId, ResourceLocation profileId, SkyProfile profile,
            double pressure, double solarIntensity, Optional<Orbit> orbit) {
        public Selection(ResourceLocation bodyId, ResourceLocation profileId, SkyProfile profile,
                         double pressure, double solarIntensity) {
            this(bodyId, profileId, profile, pressure, solarIntensity, Optional.empty());
        }
    }

    /** The body a station orbits, as its sky shows it. */
    public record Orbit(ResourceLocation bodyId, int color, double solarIntensity) { }
}
