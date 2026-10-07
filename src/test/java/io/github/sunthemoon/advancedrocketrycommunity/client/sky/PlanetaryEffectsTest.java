package io.github.sunthemoon.advancedrocketrycommunity.client.sky;

import static org.junit.jupiter.api.Assertions.*;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.valueproviders.ConstantFloat;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PlanetaryEffectsTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void surfaceAndSpaceRetainDistinctFallbackAndLightmapPolicies() {
        for (var fallback : List.of(new DimensionSpecialEffects.OverworldEffects(), new DimensionSpecialEffects.EndEffects())) {
            var custom = new PlanetaryDimensionEffects(fallback);
            assertEquals(fallback.hasGround(), custom.hasGround());
            assertEquals(fallback.skyType(), custom.skyType());
            assertEquals(fallback.forceBrightLightmap(), custom.forceBrightLightmap());
            assertEquals(fallback.constantAmbientLight(), custom.constantAmbientLight());
            assertEquals(fallback.isFoggyAt(0, 0), custom.isFoggyAt(0, 0));
            assertTrue(Float.isNaN(custom.getCloudHeight()));
            for (float brightness : new float[] {0, 0.5F, 1}) {
                assertEquals(fallback.getBrightnessDependentFogColor(new Vec3(0.2, 0.4, 0.8), brightness),
                        custom.getBrightnessDependentFogColor(new Vec3(0.2, 0.4, 0.8), brightness));
                assertArrayEquals(fallback.getSunriseColor(brightness, 0), custom.getSunriseColor(brightness, 0));
            }
        }
    }

    @Test void additionalSoundIsQuietRelativeNonLoopingAmbientAtNormalPitch() throws Exception {
        var sound = PlanetaryAmbience.instance(SoundEvents.AMBIENT_BASALT_DELTAS_ADDITIONS.value(), 0.18F);
        // Supply the resource-resolution result without creating an audio device in plain JUnit.
        var resolved = AbstractSoundInstance.class.getDeclaredField("sound");
        resolved.setAccessible(true);
        resolved.set(sound, new Sound("test:unit_gain", ConstantFloat.of(1), ConstantFloat.of(1),
                1, Sound.Type.FILE, false, false, 16));
        assertEquals(SoundSource.AMBIENT, sound.getSource());
        assertEquals(0.18F, sound.getVolume());
        assertEquals(1, sound.getPitch());
        assertFalse(sound.isLooping());
        assertTrue(sound.isRelative());
    }

    @Test void disabledHooksDelegateEveryArgumentAndBothFallbackResultsWithoutPreparingFog() {
        for (boolean result : new boolean[] {false, true}) {
            var fallback = new RecordingEffects(192, result);
            var custom = new PlanetaryDimensionEffects(fallback, () -> false);
            var poses = new PoseStack(); var projection = new Matrix4f();
            Runnable setupFog = () -> fail("Disabled adapter ran setupFog before its fallback");
            // Null native objects are inert arguments to the recording fallback, not fake client worlds.
            assertEquals(result, custom.renderSky(null, 13, 0.25F, poses, null, projection, true, setupFog));
            assertArrayEquals(new Object[] {null, 13, 0.25F, poses, null, projection, true, setupFog}, fallback.skyArgs);
            assertEquals(result, custom.renderClouds(null, 17, 0.5F, poses, -2.0, 3.0, 4.0, projection));
            assertArrayEquals(new Object[] {null, 17, 0.5F, poses, -2.0, 3.0, 4.0, projection}, fallback.cloudArgs);
            assertEquals(result, custom.renderSnowAndRain(null, 19, 0.75F, null, 5.0, -6.0, 7.0));
            assertArrayEquals(new Object[] {null, 19, 0.75F, null, 5.0, -6.0, 7.0}, fallback.weatherArgs);
            assertEquals(result, custom.tickRain(null, 23, null));
            assertArrayEquals(new Object[] {null, 23, null}, fallback.rainArgs);
            assertEquals(4, fallback.hookCalls);
            assertFalse(custom.overridesSky());
        }
    }

    @Test void disabledSkyLeavesFogPreparationToTheFallbackExactlyOnce() {
        var fallback = new RecordingEffects(192, true); fallback.prepareFog = true;
        var custom = new PlanetaryDimensionEffects(fallback, () -> false);
        var fogCalls = new AtomicInteger(); Runnable setupFog = fogCalls::incrementAndGet;
        assertTrue(custom.renderSky(null, 29, 0.5F, new PoseStack(), null, new Matrix4f(), false, setupFog));
        assertEquals(1, fallback.hookCalls);
        assertEquals(1, fogCalls.get());
    }

    @Test void sameInstanceToggleRestoresLiveCloudHeightAndEnabledWeatherSuppression() {
        var enabled = new AtomicBoolean(false);
        var fallback = new RecordingEffects(192, false);
        var custom = new PlanetaryDimensionEffects(fallback, enabled::get);
        assertEquals(192, custom.getCloudHeight());
        fallback.cloudHeight = 224;
        assertEquals(224, custom.getCloudHeight());
        assertFalse(custom.renderClouds(null, 1, 0, null, 0, 0, 0, null));
        assertFalse(custom.renderSnowAndRain(null, 1, 0, null, 0, 0, 0));
        assertFalse(custom.tickRain(null, 1, null));
        assertEquals(3, fallback.hookCalls);

        enabled.set(true);
        assertTrue(custom.overridesSky());
        assertTrue(Float.isNaN(custom.getCloudHeight()));
        assertTrue(custom.renderClouds(null, 2, 0, null, 0, 0, 0, null));
        assertTrue(custom.renderSnowAndRain(null, 2, 0, null, 0, 0, 0));
        assertTrue(custom.tickRain(null, 2, null));
        assertEquals(3, fallback.hookCalls);

        enabled.set(false); fallback.cloudHeight = 256;
        assertFalse(custom.overridesSky());
        assertEquals(256, custom.getCloudHeight());
        assertFalse(custom.renderClouds(null, 3, 0, null, 0, 0, 0, null));
        assertFalse(custom.renderSnowAndRain(null, 3, 0, null, 0, 0, 0));
        assertFalse(custom.tickRain(null, 3, null));
        assertEquals(6, fallback.hookCalls);
    }

    @Test void independentCategorySuppliersDoNotChangeTheOtherInstalledAdapter() {
        var planetEnabled = new AtomicBoolean(true); var stationEnabled = new AtomicBoolean(true);
        var planetFallback = new RecordingEffects(192, false); var stationFallback = new RecordingEffects(288, false);
        var planet = new PlanetaryDimensionEffects(planetFallback, planetEnabled::get);
        var station = new PlanetaryDimensionEffects(stationFallback, stationEnabled::get);
        planetEnabled.set(false);
        assertFalse(planet.overridesSky()); assertTrue(station.overridesSky());
        assertEquals(192, planet.getCloudHeight()); assertTrue(Float.isNaN(station.getCloudHeight()));
        assertFalse(planet.tickRain(null, 1, null)); assertTrue(station.tickRain(null, 1, null));
        assertEquals(1, planetFallback.hookCalls); assertEquals(0, stationFallback.hookCalls);
        stationEnabled.set(false); planetEnabled.set(true);
        assertTrue(planet.overridesSky()); assertFalse(station.overridesSky());
        assertTrue(Float.isNaN(planet.getCloudHeight())); assertEquals(288, station.getCloudHeight());
        assertTrue(planet.tickRain(null, 2, null)); assertFalse(station.tickRain(null, 2, null));
        assertEquals(1, planetFallback.hookCalls); assertEquals(1, stationFallback.hookCalls);
    }

    @Test void bothModesRetainFallbackFlagsFogAndSunriseAndRejectMissingSupplier() {
        for (var fallback : List.of(new DimensionSpecialEffects.OverworldEffects(), new DimensionSpecialEffects.EndEffects())) {
            var enabled = new AtomicBoolean();
            var custom = new PlanetaryDimensionEffects(fallback, enabled::get);
            for (boolean value : new boolean[] {false, true}) {
                enabled.set(value);
                assertEquals(value, custom.overridesSky());
                assertEquals(fallback.hasGround(), custom.hasGround());
                assertEquals(fallback.skyType(), custom.skyType());
                assertEquals(fallback.forceBrightLightmap(), custom.forceBrightLightmap());
                assertEquals(fallback.constantAmbientLight(), custom.constantAmbientLight());
                assertEquals(fallback.isFoggyAt(0, 0), custom.isFoggyAt(0, 0));
                for (float brightness : new float[] {0, 0.5F, 1}) {
                    var color = new Vec3(0.2, 0.4, 0.8);
                    assertEquals(fallback.getBrightnessDependentFogColor(color, brightness),
                            custom.getBrightnessDependentFogColor(color, brightness));
                    assertArrayEquals(fallback.getSunriseColor(brightness, 0), custom.getSunriseColor(brightness, 0));
                }
                if (value) { assertTrue(Float.isNaN(custom.getCloudHeight())); }
                else { assertEquals(fallback.getCloudHeight(), custom.getCloudHeight()); }
            }
            assertThrows(NullPointerException.class, () -> new PlanetaryDimensionEffects(fallback, null));
        }
    }

    /** No client singleton, viewport event, native level or GPU object is manufactured. */
    private static final class RecordingEffects extends DimensionSpecialEffects {
        private float cloudHeight;
        private final boolean hookResult;
        private boolean prepareFog;
        private int hookCalls;
        private Object[] skyArgs, cloudArgs, weatherArgs, rainArgs;

        private RecordingEffects(float cloudHeight, boolean hookResult) {
            super(cloudHeight, true, SkyType.NORMAL, false, true);
            this.cloudHeight = cloudHeight; this.hookResult = hookResult;
        }
        @Override public float getCloudHeight() { return cloudHeight; }
        @Override public Vec3 getBrightnessDependentFogColor(Vec3 color, float brightness) { return color; }
        @Override public boolean isFoggyAt(int x, int z) { return false; }
        @Override public boolean renderSky(ClientLevel level, int ticks, float partialTick, PoseStack poses,
                Camera camera, Matrix4f projection, boolean foggy, Runnable setupFog) {
            hookCalls++; skyArgs = new Object[] {level, ticks, partialTick, poses, camera, projection, foggy, setupFog};
            if (prepareFog) { setupFog.run(); }
            return hookResult;
        }
        @Override public boolean renderClouds(ClientLevel level, int ticks, float partialTick, PoseStack poses,
                double x, double y, double z, Matrix4f projection) {
            hookCalls++; cloudArgs = new Object[] {level, ticks, partialTick, poses, x, y, z, projection};
            return hookResult;
        }
        @Override public boolean renderSnowAndRain(ClientLevel level, int ticks, float partialTick,
                LightTexture light, double x, double y, double z) {
            hookCalls++; weatherArgs = new Object[] {level, ticks, partialTick, light, x, y, z};
            return hookResult;
        }
        @Override public boolean tickRain(ClientLevel level, int ticks, Camera camera) {
            hookCalls++; rainArgs = new Object[] {level, ticks, camera};
            return hookResult;
        }
    }
}
