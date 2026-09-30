package io.github.sunthemoon.advancedrocketrycommunity.celestial.visual;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

/**
 * ADR-047: how an orbited body looks from a station, keyed by its visual profile ID. A surface sky's
 * day colour is the colour of the sky seen from the ground (black for the Moon), and Earth and the gas
 * giant have no surface sky profile, so packaged bodies have explicit colours. Other bodies use their
 * profile's day colour when it is bright enough to see against space, otherwise a neutral colour.
 * Presentation only; tuning belongs to real-GPU (V1) review.
 */
public final class OrbitalAppearance {
    public static final int NEUTRAL_COLOR = 0x6E8FB4;
    /** Minimum relative luminance of a profile's day colour to be used as a disc colour. */
    public static final double MINIMUM_LUMINANCE = 0.2;
    private static final Map<ResourceLocation, Integer> PACKAGED = Map.of(
            ModIdentity.id("earth"), 0x3E6FB0,
            ModIdentity.id("moon"), 0x8C8C8C,
            ModIdentity.id("mars"), 0xB0583A,
            ModIdentity.id("venus"), 0xD9C28A,
            ModIdentity.id("gas_giant"), 0xC8A06A);

    private OrbitalAppearance() {
    }

    public static int color(ResourceLocation visualProfile, SkyProfile profile) {
        Integer packaged = PACKAGED.get(visualProfile);
        if (packaged != null) {
            return packaged;
        }
        return profile != null && luminance(profile.dayColor()) >= MINIMUM_LUMINANCE ? profile.dayColor() : NEUTRAL_COLOR;
    }

    public static Map<ResourceLocation, Integer> packaged() {
        return PACKAGED;
    }

    /** Relative luminance of an sRGB colour (Rec. 709 weights on the gamma-encoded channels), 0..1. */
    public static double luminance(int rgb) {
        return (0.2126 * ((rgb >> 16) & 255) + 0.7152 * ((rgb >> 8) & 255) + 0.0722 * (rgb & 255)) / 255D;
    }
}
