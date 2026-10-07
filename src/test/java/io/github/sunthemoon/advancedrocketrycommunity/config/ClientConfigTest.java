package io.github.sunthemoon.advancedrocketrycommunity.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import io.github.sunthemoon.advancedrocketrycommunity.client.LifeSupportHudLayout.Anchor;
import io.github.sunthemoon.advancedrocketrycommunity.client.LifeSupportHudLayout.PanelSettings;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraftforge.common.ForgeConfigSpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** CLIENT-only effects: ADR-063 section 6 and the existing-category subset of ADR-066 section 7.2. */
class ClientConfigTest {
    @AfterEach
    void releaseLoadedConfig() {
        ClientConfig.SPEC.setConfig(null);
    }

    @Test
    void exactClientCategoriesRetainTheElectricMushroomSetting() {
        assertEquals(Set.of("effects", "sky", "hud"), ClientConfig.SPEC.getValues().valueMap().keySet());
        assertEquals(Set.of("electricMushroomFlashes"), assertInstanceOf(UnmodifiableConfig.class,
                ClientConfig.SPEC.getValues().get("effects")).valueMap().keySet());
        assertEquals(Set.of("planetOverride", "stationOverride"), assertInstanceOf(UnmodifiableConfig.class,
                ClientConfig.SPEC.getValues().get("sky")).valueMap().keySet());
        assertSame(ClientConfig.ELECTRIC_MUSHROOM_FLASHES,
                ClientConfig.SPEC.getValues().get("effects.electricMushroomFlashes"));
        assertEquals(Boolean.TRUE, ClientConfig.ELECTRIC_MUSHROOM_FLASHES.getDefault());
        assertEquals(List.of("effects", "electricMushroomFlashes"), ClientConfig.ELECTRIC_MUSHROOM_FLASHES.getPath());
        assertTrue(ClientConfig.electricMushroomFlashes(), "the default applies before the config is loaded");
    }

    @Test
    void bothExactSkyKeysDefaultToEnabledWithoutLoadedConfig() {
        assertFalse(ClientConfig.SPEC.isLoaded());
        assertSame(ClientConfig.SKY_PLANET_OVERRIDE, ClientConfig.SPEC.getValues().get("sky.planetOverride"));
        assertSame(ClientConfig.SKY_STATION_OVERRIDE, ClientConfig.SPEC.getValues().get("sky.stationOverride"));
        assertEquals(List.of("sky", "planetOverride"), ClientConfig.SKY_PLANET_OVERRIDE.getPath());
        assertEquals(List.of("sky", "stationOverride"), ClientConfig.SKY_STATION_OVERRIDE.getPath());
        assertEquals(Boolean.TRUE, ClientConfig.SKY_PLANET_OVERRIDE.getDefault());
        assertEquals(Boolean.TRUE, ClientConfig.SKY_STATION_OVERRIDE.getDefault());
        assertTrue(ClientConfig.planetSkyOverride());
        assertTrue(ClientConfig.stationSkyOverride());
    }

    @Test
    void loadedSkySwitchesAreIndependentAndCanBeReenabled() {
        ClientConfig.SPEC.setConfig(CommentedConfig.inMemory());
        assertTrue(ClientConfig.SPEC.isLoaded());
        assertTrue(ClientConfig.planetSkyOverride());
        assertTrue(ClientConfig.stationSkyOverride());
        assertTrue(ClientConfig.electricMushroomFlashes());

        ClientConfig.SKY_PLANET_OVERRIDE.set(false);
        assertFalse(ClientConfig.planetSkyOverride());
        assertTrue(ClientConfig.stationSkyOverride());
        ClientConfig.SKY_STATION_OVERRIDE.set(false);
        assertFalse(ClientConfig.planetSkyOverride());
        assertFalse(ClientConfig.stationSkyOverride());
        ClientConfig.SKY_PLANET_OVERRIDE.set(true);
        assertTrue(ClientConfig.planetSkyOverride());
        assertFalse(ClientConfig.stationSkyOverride());
        ClientConfig.SKY_STATION_OVERRIDE.set(true);
        assertTrue(ClientConfig.planetSkyOverride());
        assertTrue(ClientConfig.stationSkyOverride());
        assertTrue(ClientConfig.electricMushroomFlashes());
    }

    @Test
    void exactEightHudPathsDefaultsTypesAndUnloadedAccessors() {
        assertFalse(ClientConfig.SPEC.isLoaded());
        assertEquals(Set.of("environment", "oxygen"), assertInstanceOf(UnmodifiableConfig.class,
                ClientConfig.SPEC.getValues().get("hud")).valueMap().keySet());
        for (String panel : List.of("environment", "oxygen")) {
            assertEquals(Set.of("x", "y", "anchorX", "anchorY"), assertInstanceOf(UnmodifiableConfig.class,
                    ClientConfig.SPEC.getValues().get("hud." + panel)).valueMap().keySet());
        }
        List<ForgeConfigSpec.ConfigValue<Number>> offsets = offsets();
        List<String> paths = List.of("hud.environment.x", "hud.environment.y", "hud.oxygen.x", "hud.oxygen.y");
        List<Integer> defaults = List.of(-6, 6, -6, 30);
        for (int i = 0; i < offsets.size(); i++) {
            var value = offsets.get(i);
            assertSame(value, ClientConfig.SPEC.getValues().get(paths.get(i)));
            assertEquals(List.of(paths.get(i).split("\\.")), value.getPath());
            assertInstanceOf(Integer.class, value.getDefault());
            assertEquals(defaults.get(i), value.getDefault());
        }
        List<ForgeConfigSpec.EnumValue<Anchor>> anchors = anchors();
        List<String> anchorPaths = List.of("hud.environment.anchorX", "hud.environment.anchorY",
                "hud.oxygen.anchorX", "hud.oxygen.anchorY");
        for (int i = 0; i < anchors.size(); i++) {
            assertSame(anchors.get(i), ClientConfig.SPEC.getValues().get(anchorPaths.get(i)));
            assertEquals(List.of(anchorPaths.get(i).split("\\.")), anchors.get(i).getPath());
            assertEquals(i % 2 == 0 ? Anchor.END : Anchor.START, anchors.get(i).getDefault());
        }
        assertEquals(new PanelSettings(-6, 6, Anchor.END, Anchor.START), ClientConfig.environmentHudSettings());
        assertEquals(new PanelSettings(-6, 30, Anchor.END, Anchor.START), ClientConfig.oxygenHudSettings());
    }

    @Test
    void offsetPredicatesAdmitOnlyBoundedPrimitiveIntegralWrappers() {
        List<Object> admitted = List.of((byte) -128, (byte) 127, (short) -4096, (short) 4096,
                -4096, 4096, -4096L, 4096L, 0L);
        List<Object> rejected = List.of(-4097, 4097, Long.MIN_VALUE, Long.MAX_VALUE, 0.0f, 1.0d,
                Float.NaN, Double.POSITIVE_INFINITY, new BigDecimal("1"), BigInteger.ONE,
                new AtomicInteger(1), true, "1");
        for (var offset : offsets()) {
            ForgeConfigSpec.ValueSpec spec = ClientConfig.SPEC.getSpec().get(offset.getPath());
            for (Object value : admitted) {
                assertTrue(spec.test(value), "admit " + value.getClass().getSimpleName() + ": " + value);
            }
            for (Object value : rejected) {
                assertFalse(spec.test(value), "reject " + value.getClass().getSimpleName() + ": " + value);
            }
            assertFalse(spec.test(null));
        }
    }

    @Test
    void nativeLoadCorrectsEveryMissingOrInvalidHudValueToItsOwnDefault() {
        CommentedConfig config = CommentedConfig.inMemory();
        ClientConfig.SPEC.setConfig(config);
        assertDefaults(config);
        List<Object> invalidOffsets = List.of(-4097, 4097L, 1.0f, 1.0d, true, "6", BigInteger.ONE);
        for (Object value : invalidOffsets) {
            for (var offset : offsets()) {
                config.set(offset.getPath(), value);
            }
            for (var anchor : anchors()) {
                config.set(anchor.getPath(), "NOT_AN_ANCHOR");
            }
            ClientConfig.SPEC.setConfig(config);
            assertDefaults(config);
        }
        for (Object invalidAnchor : List.of(1, 1.0d, true, Thread.State.NEW)) {
            for (var anchor : anchors()) {
                config.set(anchor.getPath(), invalidAnchor);
            }
            ClientConfig.SPEC.setConfig(config);
            assertDefaults(config);
        }
        for (var offset : offsets()) {
            config.remove(offset.getPath());
        }
        for (var anchor : anchors()) {
            config.remove(anchor.getPath());
        }
        ClientConfig.SPEC.setConfig(config);
        assertDefaults(config);
    }

    @Test
    void loadedPanelsAxesAndAllAnchorsRemainIndependent() {
        CommentedConfig config = CommentedConfig.inMemory();
        config.set("hud.environment.x", -4096L);
        config.set("hud.environment.y", (short) 4096);
        config.set("hud.oxygen.x", (byte) -7);
        config.set("hud.oxygen.y", 91);
        ClientConfig.SPEC.setConfig(config);
        assertEquals(new PanelSettings(-4096, 4096, Anchor.END, Anchor.START), ClientConfig.environmentHudSettings());
        assertEquals(new PanelSettings(-7, 91, Anchor.END, Anchor.START), ClientConfig.oxygenHudSettings());
        for (Anchor x : Anchor.values()) {
            for (Anchor y : Anchor.values()) {
                config.set("hud.environment.anchorX", x.name().toLowerCase(java.util.Locale.ROOT));
                config.set("hud.environment.anchorY", y);
                config.set("hud.oxygen.anchorX", y);
                config.set("hud.oxygen.anchorY", x);
                ClientConfig.SPEC.setConfig(config);
                assertEquals(new PanelSettings(-4096, 4096, x, y), ClientConfig.environmentHudSettings());
                assertEquals(new PanelSettings(-7, 91, y, x), ClientConfig.oxygenHudSettings());
                assertTrue(ClientConfig.electricMushroomFlashes());
                assertTrue(ClientConfig.planetSkyOverride());
                assertTrue(ClientConfig.stationSkyOverride());
            }
        }
    }

    @Test
    void sameInstanceNativeCorrectionAndAfterReloadInvalidateCachedAccessors() {
        CommentedConfig config = CommentedConfig.inMemory();
        ClientConfig.SPEC.setConfig(config);
        assertEquals(-6, ClientConfig.environmentHudSettings().x());
        assertEquals(30, ClientConfig.oxygenHudSettings().y());
        config.set("hud.environment.x", 101L);
        config.set("hud.environment.anchorY", "CENTER");
        config.set("hud.oxygen.y", -4096);
        config.set("hud.oxygen.anchorX", Anchor.START);
        assertTrue(ClientConfig.SPEC.isCorrect(config));
        ClientConfig.SPEC.afterReload();
        assertEquals(new PanelSettings(101, 6, Anchor.END, Anchor.CENTER), ClientConfig.environmentHudSettings());
        assertEquals(new PanelSettings(-6, -4096, Anchor.START, Anchor.START), ClientConfig.oxygenHudSettings());
        config.set("hud.environment.x", 101.0d);
        config.set("hud.oxygen.anchorX", "BROKEN");
        assertFalse(ClientConfig.SPEC.isCorrect(config));
        assertTrue(ClientConfig.SPEC.correct(config) > 0);
        ClientConfig.SPEC.afterReload();
        assertEquals(new PanelSettings(-6, 6, Anchor.END, Anchor.CENTER), ClientConfig.environmentHudSettings());
        assertEquals(new PanelSettings(-6, -4096, Anchor.END, Anchor.START), ClientConfig.oxygenHudSettings());
        ClientConfig.SPEC.setConfig(null);
        assertEquals(new PanelSettings(-6, 6, Anchor.END, Anchor.START), ClientConfig.environmentHudSettings());
        assertEquals(new PanelSettings(-6, 30, Anchor.END, Anchor.START), ClientConfig.oxygenHudSettings());
    }

    private static List<ForgeConfigSpec.ConfigValue<Number>> offsets() {
        return List.of(ClientConfig.HUD_ENVIRONMENT_X, ClientConfig.HUD_ENVIRONMENT_Y,
                ClientConfig.HUD_OXYGEN_X, ClientConfig.HUD_OXYGEN_Y);
    }

    private static List<ForgeConfigSpec.EnumValue<Anchor>> anchors() {
        return List.of(ClientConfig.HUD_ENVIRONMENT_ANCHOR_X, ClientConfig.HUD_ENVIRONMENT_ANCHOR_Y,
                ClientConfig.HUD_OXYGEN_ANCHOR_X, ClientConfig.HUD_OXYGEN_ANCHOR_Y);
    }

    private static void assertDefaults(CommentedConfig config) {
        for (var offset : offsets()) {
            assertEquals(offset.getDefault(), config.get(offset.getPath()));
        }
        for (var anchor : anchors()) {
            assertEquals(anchor.getDefault(), config.get(anchor.getPath()));
        }
        assertEquals(new PanelSettings(-6, 6, Anchor.END, Anchor.START), ClientConfig.environmentHudSettings());
        assertEquals(new PanelSettings(-6, 30, Anchor.END, Anchor.START), ClientConfig.oxygenHudSettings());
    }
}
