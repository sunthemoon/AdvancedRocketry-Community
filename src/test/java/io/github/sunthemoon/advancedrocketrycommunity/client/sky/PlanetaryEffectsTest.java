package io.github.sunthemoon.advancedrocketrycommunity.client.sky;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.List;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.valueproviders.ConstantFloat;
import net.minecraft.world.phys.Vec3;
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
}
