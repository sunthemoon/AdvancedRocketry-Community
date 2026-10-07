package io.github.sunthemoon.advancedrocketrycommunity.client.sky;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** Only registered under our two opt-in IDs; never replaces a global vanilla effect. */
public final class PlanetaryDimensionEffects extends DimensionSpecialEffects {
    private final DimensionSpecialEffects fallback;
    private final BooleanSupplier enabled;

    public PlanetaryDimensionEffects(DimensionSpecialEffects fallback) {
        this(fallback, () -> true);
    }

    public PlanetaryDimensionEffects(DimensionSpecialEffects fallback, BooleanSupplier enabled) {
        super(Float.NaN, fallback.hasGround(), fallback.skyType(),
                fallback.forceBrightLightmap(), fallback.constantAmbientLight());
        this.fallback = fallback;
        this.enabled = Objects.requireNonNull(enabled, "enabled");
    }

    public boolean overridesSky() { return enabled.getAsBoolean(); }

    @Override public float getCloudHeight() {
        return overridesSky() ? Float.NaN : fallback.getCloudHeight();
    }

    @Override public Vec3 getBrightnessDependentFogColor(Vec3 color, float brightness) {
        return fallback.getBrightnessDependentFogColor(color, brightness);
    }

    @Override public boolean isFoggyAt(int x, int z) { return fallback.isFoggyAt(x, z); }

    @Override public float[] getSunriseColor(float time, float partialTick) {
        return fallback.getSunriseColor(time, partialTick);
    }

    @Override public boolean renderSky(ClientLevel level, int ticks, float partialTick, PoseStack poses,
            Camera camera, Matrix4f projection, boolean foggy, Runnable setupFog) {
        if (!overridesSky()) {
            return fallback.renderSky(level, ticks, partialTick, poses, camera, projection, foggy, setupFog);
        }
        var selection = PlanetarySkyClient.visualSelection(level);
        if (selection == null || foggy || !PlanetarySkyClient.clearView(camera)) {
            return false;
        }
        setupFog.run();
        return PlanetarySkyClient.render(selection, poses, projection, level.getSunAngle(partialTick),
                level.getSkyFlashTime() - partialTick);
    }

    @Override public boolean renderClouds(ClientLevel level, int ticks, float partialTick, PoseStack poses,
            double x, double y, double z, Matrix4f projection) {
        return overridesSky() || fallback.renderClouds(level, ticks, partialTick, poses, x, y, z, projection);
    }

    @Override public boolean renderSnowAndRain(ClientLevel level, int ticks, float partialTick,
            LightTexture light, double x, double y, double z) {
        return overridesSky() || fallback.renderSnowAndRain(level, ticks, partialTick, light, x, y, z);
    }

    @Override public boolean tickRain(ClientLevel level, int ticks, Camera camera) {
        return overridesSky() || fallback.tickRain(level, ticks, camera);
    }
}
