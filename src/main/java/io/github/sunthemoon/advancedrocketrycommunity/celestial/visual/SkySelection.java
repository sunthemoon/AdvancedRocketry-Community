package io.github.sunthemoon.advancedrocketrycommunity.celestial.visual;

import io.github.sunthemoon.advancedrocketrycommunity.celestial.network.CelestialSnapshot;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

/** One-entry cache: immutable snapshot/resource identities invalidate it without per-frame scans. */
public final class SkySelection {
    private CelestialSnapshot previousSnapshot;
    private Map<ResourceLocation, SkyProfile> previousProfiles;
    private ResourceLocation previousLevel;
    private Selection selected;

    public Selection resolve(ResourceLocation level, CelestialSnapshot snapshot, Map<ResourceLocation, SkyProfile> profiles) {
        if (level != null && level.equals(previousLevel) && snapshot == previousSnapshot && profiles == previousProfiles) {
            return selected;
        }
        previousLevel = level;
        previousSnapshot = snapshot;
        previousProfiles = profiles;
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
                selected = new Selection(match.bodyId(), match.visualProfile(), profile, match.pressure(), match.solarIntensity());
            }
        }
        return selected;
    }

    public void clear() {
        previousLevel = null;
        previousSnapshot = null;
        previousProfiles = null;
        selected = null;
    }

    public record Selection(ResourceLocation bodyId, ResourceLocation profileId, SkyProfile profile,
            double pressure, double solarIntensity) { }
}
