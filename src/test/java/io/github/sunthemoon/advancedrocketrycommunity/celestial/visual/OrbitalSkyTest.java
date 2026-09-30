package io.github.sunthemoon.advancedrocketrycommunity.celestial.visual;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialDefaults;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.content.PlanetaryContent;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.content.StarSystemContent;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.model.CelestialBodyDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** ADR-047: where the orbited body is drawn and how each packaged body looks from a station. */
final class OrbitalSkyTest {
    private static final Path SPACE_TYPE = Path.of(
            "src/generated/v1.4/resources/data/advancedrocketrycommunity/dimension_type/space.json");

    @BeforeAll
    static void bootstrap() {
        MinecraftBootstrap.initialize();
    }

    @Test
    void theDiscNeverCoversTheSunOrItsHaloInTheSpaceLevel() throws Exception {
        long fixedTime = JsonParser.parseString(Files.readString(SPACE_TYPE, StandardCharsets.UTF_8))
                .getAsJsonObject().get("fixed_time").getAsLong();
        assertEquals(18000L, fixedTime, "The disc placement assumes the Space Level's fixed time");
        // Vanilla DimensionType#timeOfDay for a fixed time, as the client's sun angle uses it.
        double d = (fixedTime / 24000.0D - 0.25D) - Math.floor(fixedTime / 24000.0D - 0.25D);
        double e = 0.5D - Math.cos(d * Math.PI) / 2.0D;
        double sunAngle = (d * 2.0D + e) / 3.0D * Math.PI * 2;
        assertEquals(Math.PI, sunAngle, 1e-9, "Space's sun is at the nadir");
        double separation = SkyMath.separationDegrees(SkyMath.orbitedBodyDirection(), SkyMath.sunDirection(sunAngle));
        assertTrue(separation >= SkyMath.ORBITED_BODY_RADIUS_DEGREES + SkyMath.MAX_SUN_HALO_DEGREES,
                "The disc overlaps the sun: " + separation);
        assertTrue(SkyMath.orbitedBodyDirection()[1] < 0, "The orbited body is below the horizon");
    }

    @Test
    void everyPackagedOrbitableBodyHasAVisibleColour() {
        int spaceSky = SkyProfiles.builtins().get(ModIdentity.id("space")).dayColor();
        List<CelestialBodyDefinition> bodies = new ArrayList<>(CelestialDefaults.definitions());
        bodies.addAll(PlanetaryContent.definitions());
        bodies.addAll(StarSystemContent.definitions());
        int checked = 0;
        for (CelestialBodyDefinition body : bodies) {
            if (!body.capabilities().orbitable()) {
                continue;
            }
            int color = OrbitalAppearance.color(body.visualProfile(), SkyProfiles.builtins().get(body.visualProfile()));
            assertTrue(OrbitalAppearance.luminance(color) - OrbitalAppearance.luminance(spaceSky) >= 0.2,
                    body.id() + " is not visible against space: " + Integer.toHexString(color));
            checked++;
        }
        assertTrue(checked >= 5, "Too few packaged orbitable bodies: " + checked);
    }

    @Test
    void otherBodiesUseABrightProfileColourOrTheNeutralOne() {
        var dark = new SkyProfile(0x020202, 0, 0, 0xFFFFFF, 1, 1, 1, 0.5, 64, Optional.empty(), 0);
        var bright = new SkyProfile(0xD0A060, 0, 0, 0xFFFFFF, 1, 1, 1, 0.5, 64, Optional.empty(), 0);
        assertEquals(OrbitalAppearance.NEUTRAL_COLOR, OrbitalAppearance.color(ModIdentity.id("custom_dark"), dark));
        assertEquals(0xD0A060, OrbitalAppearance.color(ModIdentity.id("custom_bright"), bright));
        assertEquals(OrbitalAppearance.NEUTRAL_COLOR, OrbitalAppearance.color(ModIdentity.id("no_profile"), null));
    }
}
