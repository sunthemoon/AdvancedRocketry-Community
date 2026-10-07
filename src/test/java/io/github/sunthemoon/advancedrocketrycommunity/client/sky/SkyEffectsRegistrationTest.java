package io.github.sunthemoon.advancedrocketrycommunity.client.sky;

import static org.junit.jupiter.api.Assertions.*;

import com.electronwill.nightconfig.core.CommentedConfig;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.visual.SkyProfiles;
import io.github.sunthemoon.advancedrocketrycommunity.client.ClientBootstrap;
import io.github.sunthemoon.advancedrocketrycommunity.config.ClientConfig;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.DimensionSpecialEffects.SkyType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RegisterDimensionSpecialEffectsEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The actual ClientBootstrap handler fills the map Forge hands to its registration event (ADR-066 section 7.2):
 * planetary uses the planet switch over Overworld, planetary_space the station switch over End.
 */
class SkyEffectsRegistrationTest {
    private static final ResourceLocation SURFACE = new ResourceLocation("advancedrocketrycommunity", "planetary");
    private static final ResourceLocation SPACE = new ResourceLocation("advancedrocketrycommunity", "planetary_space");

    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @BeforeEach void startUnloaded() { ClientConfig.SPEC.setConfig(null); }

    @AfterEach void releaseLoadedConfig() { ClientConfig.SPEC.setConfig(null); }

    @Test void handlerRegistersExactlyTheTwoExistingCategoryIds() {
        assertEquals(SURFACE, SkyProfiles.SURFACE_EFFECTS);
        assertEquals(SPACE, SkyProfiles.SPACE_EFFECTS);
        var registered = register();
        assertEquals(Set.of(SURFACE, SPACE), registered.keySet());
        assertEquals(2, registered.size());
        var surface = assertInstanceOf(PlanetaryDimensionEffects.class, registered.get(SURFACE));
        var space = assertInstanceOf(PlanetaryDimensionEffects.class, registered.get(SPACE));
        assertNotSame(surface, space);
    }

    @Test void handlerLeavesEveryVanillaEffectsEntryInTheEventMapUntouched() {
        var effects = new HashMap<ResourceLocation, DimensionSpecialEffects>();
        var overworld = new DimensionSpecialEffects.OverworldEffects();
        var nether = new DimensionSpecialEffects.NetherEffects();
        var end = new DimensionSpecialEffects.EndEffects();
        effects.put(new ResourceLocation("minecraft", "overworld"), overworld);
        effects.put(new ResourceLocation("minecraft", "the_nether"), nether);
        effects.put(new ResourceLocation("minecraft", "the_end"), end);
        ClientBootstrap.onRegisterDimensionEffects(new RegisterDimensionSpecialEffectsEvent(effects));
        assertEquals(5, effects.size());
        assertSame(overworld, effects.get(new ResourceLocation("minecraft", "overworld")));
        assertSame(nether, effects.get(new ResourceLocation("minecraft", "the_nether")));
        assertSame(end, effects.get(new ResourceLocation("minecraft", "the_end")));
        assertInstanceOf(PlanetaryDimensionEffects.class, effects.get(SURFACE));
        assertInstanceOf(PlanetaryDimensionEffects.class, effects.get(SPACE));
    }

    @Test void unloadedDefaultsEnableBothAndEachIdKeepsItsOwnFallbackPolicy() {
        assertFalse(ClientConfig.SPEC.isLoaded());
        var registered = register();
        assertSurface(registered.get(SURFACE), true);
        assertSpace(registered.get(SPACE), true);

        ClientConfig.SPEC.setConfig(CommentedConfig.inMemory());
        ClientConfig.SKY_PLANET_OVERRIDE.set(false);
        ClientConfig.SKY_STATION_OVERRIDE.set(false);
        assertSurface(registered.get(SURFACE), false);
        assertSpace(registered.get(SPACE), false);
    }

    @Test void fourIndependentSettingCombinationsDriveTheSameRegisteredInstances() {
        var registered = register();
        var surface = registered.get(SURFACE);
        var space = registered.get(SPACE);
        ClientConfig.SPEC.setConfig(CommentedConfig.inMemory());
        // Each step changes one switch at a time where possible, then both off, then both re-enabled.
        boolean[][] steps = {{true, true}, {false, true}, {true, false}, {false, false}, {true, true}};
        for (boolean[] step : steps) {
            ClientConfig.SKY_PLANET_OVERRIDE.set(step[0]);
            ClientConfig.SKY_STATION_OVERRIDE.set(step[1]);
            assertEquals(step[0], ClientConfig.planetSkyOverride());
            assertEquals(step[1], ClientConfig.stationSkyOverride());
            assertEquals(2, registered.size());
            assertSame(surface, registered.get(SURFACE));
            assertSame(space, registered.get(SPACE));
            assertSurface(surface, step[0]);
            assertSpace(space, step[1]);
        }
    }

    @Test void registrationCapturesNoValueSoReloadAndReenableNeedNoNewRegistration() {
        ClientConfig.SPEC.setConfig(CommentedConfig.inMemory());
        ClientConfig.SKY_PLANET_OVERRIDE.set(false);
        ClientConfig.SKY_STATION_OVERRIDE.set(false);
        var registered = register();
        var surface = registered.get(SURFACE);
        var space = registered.get(SPACE);
        assertSurface(surface, false);
        assertSpace(space, false);

        ClientConfig.SPEC.setConfig(null);
        assertSurface(surface, true);
        assertSpace(space, true);

        CommentedConfig planetOff = CommentedConfig.inMemory();
        planetOff.set("sky.planetOverride", false);
        ClientConfig.SPEC.setConfig(planetOff);
        assertSurface(surface, false);
        assertSpace(space, true);

        CommentedConfig stationOff = CommentedConfig.inMemory();
        stationOff.set("sky.stationOverride", false);
        ClientConfig.SPEC.setConfig(stationOff);
        assertSurface(surface, true);
        assertSpace(space, false);

        ClientConfig.SKY_STATION_OVERRIDE.set(true);
        assertSurface(surface, true);
        assertSpace(space, true);
        assertSame(surface, registered.get(SURFACE));
        assertSame(space, registered.get(SPACE));
        assertEquals(2, registered.size());
    }

    /** Calls the real handler with Forge's own event over the map Forge would pass in. */
    private static Map<ResourceLocation, DimensionSpecialEffects> register() {
        var effects = new HashMap<ResourceLocation, DimensionSpecialEffects>();
        ClientBootstrap.onRegisterDimensionEffects(new RegisterDimensionSpecialEffectsEvent(effects));
        return effects;
    }

    private static void assertSurface(DimensionSpecialEffects actual, boolean enabled) {
        assertCategory(new DimensionSpecialEffects.OverworldEffects(), SkyType.NORMAL, true, actual, enabled);
    }

    private static void assertSpace(DimensionSpecialEffects actual, boolean enabled) {
        assertCategory(new DimensionSpecialEffects.EndEffects(), SkyType.END, false, actual, enabled);
    }

    /**
     * Fallback flags, fog and sunrise always follow the category's own vanilla fallback; the switch decides
     * the cloud height and the weather hooks. Null hook arguments only reach Forge's inert default fallbacks.
     */
    private static void assertCategory(DimensionSpecialEffects fallback, SkyType skyType, boolean hasGround,
            DimensionSpecialEffects actual, boolean enabled) {
        var effects = assertInstanceOf(PlanetaryDimensionEffects.class, actual);
        assertEquals(enabled, effects.overridesSky());
        assertEquals(skyType, effects.skyType());
        assertEquals(hasGround, effects.hasGround());
        assertEquals(fallback.skyType(), effects.skyType());
        assertEquals(fallback.hasGround(), effects.hasGround());
        assertEquals(fallback.forceBrightLightmap(), effects.forceBrightLightmap());
        assertEquals(fallback.constantAmbientLight(), effects.constantAmbientLight());
        assertEquals(fallback.isFoggyAt(0, 0), effects.isFoggyAt(0, 0));
        for (float brightness : new float[] {0, 0.5F, 1}) {
            var color = new Vec3(0.2, 0.4, 0.8);
            assertEquals(fallback.getBrightnessDependentFogColor(color, brightness),
                    effects.getBrightnessDependentFogColor(color, brightness));
        }
        for (float time : new float[] {0, 0.25F, 0.5F, 0.75F}) {
            assertArrayEquals(fallback.getSunriseColor(time, 0), effects.getSunriseColor(time, 0));
        }
        assertEquals(enabled ? Float.NaN : fallback.getCloudHeight(), effects.getCloudHeight());
        assertEquals(enabled, effects.renderClouds(null, 0, 0, null, 0, 0, 0, null));
        assertEquals(enabled, effects.renderSnowAndRain(null, 0, 0, null, 0, 0, 0));
        assertEquals(enabled, effects.tickRain(null, 0, null));
    }
}
