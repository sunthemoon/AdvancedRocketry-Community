package io.github.sunthemoon.advancedrocketrycommunity.celestial.visual;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/** Original authoring inputs shared by DataGen and the initial-reload fallback. */
public final class SkyProfiles {
    public static final ResourceLocation SURFACE_EFFECTS = ModIdentity.id("planetary");
    public static final ResourceLocation SPACE_EFFECTS = ModIdentity.id("planetary_space");
    private static final Map<ResourceLocation, SkyProfile> BUILTINS = Map.of(
            ModIdentity.id("moon"), new SkyProfile(0x010208, 0x000105, 0x030407, 0xFFF1CB,
                    2, 1, 0.95, 0.95, 512, Optional.empty(), 0),
            ModIdentity.id("space"), new SkyProfile(0x01030B, 0x01030B, 0x02040B, 0xFFF4DD,
                    3, 1, 1, 0.95, 512, Optional.empty(), 0),
            ModIdentity.id("mars"), new SkyProfile(0xAC7C70, 0x090B1C, 0xBE8C74, 0xEBE9FF,
                    3, 0.95, 0.85, 0.4, 192,
                    Optional.of(new ResourceLocation("minecraft", "ambient.basalt_deltas.additions")), 0.18),
            ModIdentity.id("venus"), new SkyProfile(0xC6A34F, 0x302719, 0xBDAD67, 0xFFE9AA,
                    4, 0.18, 0.025, 0.1, 48,
                    Optional.of(new ResourceLocation("minecraft", "ambient.nether_wastes.mood")), 0.12),
            ModIdentity.id("tau_ceti_f"), new SkyProfile(0x5FA8C8, 0x07131C, 0x7FB8C0, 0xFFE2B0,
                    3, 1.0, 0.3, 0.6, 256, Optional.empty(), 0),
            ModIdentity.id("tau_ceti_g"), new SkyProfile(0x202020, 0x050505, 0x2A2A2E, 0xFFD8A0,
                    4, 0.5, 0.1, 0.3, 160, Optional.empty(), 0));

    private SkyProfiles() { }

    public static Map<ResourceLocation, SkyProfile> builtins() { return BUILTINS; }
}
