package io.github.sunthemoon.advancedrocketrycommunity.client.equipment;

import org.joml.Vector3f;

/** Earthbright visor: a night-vision-like lift of one lightmap colour, with no potion effect (ADR-066 section 5.2). */
public final class EarthbrightLight {
    public static final float STRENGTH = 0.7F;

    /** Scales a dark colour toward full brightness by {@link #STRENGTH}; black, bright or invalid colours stay. */
    public static void brighten(Vector3f colors) {
        if (!Float.isFinite(colors.x()) || !Float.isFinite(colors.y()) || !Float.isFinite(colors.z())) { return; }
        float brightest = Math.max(colors.x(), Math.max(colors.y(), colors.z()));
        if (!(brightest > 1.0E-4F) || brightest >= 1.0F) { return; }
        colors.lerp(new Vector3f(colors).mul(1.0F / brightest), STRENGTH);
    }

    private EarthbrightLight() { }
}
