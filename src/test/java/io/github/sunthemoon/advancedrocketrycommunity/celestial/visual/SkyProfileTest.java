package io.github.sunthemoon.advancedrocketrycommunity.celestial.visual;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SkyProfileTest {
    @Test void allOriginalProfilesRoundTripWithoutDefaults() {
        assertEquals(4, SkyProfiles.builtins().size());
        SkyProfiles.builtins().forEach((id, profile) -> assertEquals(profile, SkyProfile.decode(profile.encode())));
        assertEquals(0, SkyProfiles.builtins().get(ModIdentity.id("moon")).soundVolume());
        assertTrue(SkyProfiles.builtins().get(ModIdentity.id("space")).ambientSound().isEmpty());
        assertNotEquals(SkyProfiles.builtins().get(ModIdentity.id("mars")), SkyProfiles.builtins().get(ModIdentity.id("venus")));
    }

    @Test void everyRequiredFieldRejectsMissingAndWrongTypes() {
        var original = SkyProfiles.builtins().get(ModIdentity.id("mars")).encode();
        for (var key : original.keySet()) {
            if (!key.equals("ambient_sound")) {
                var missing = original.deepCopy();
                missing.remove(key);
                assertThrows(IllegalArgumentException.class, () -> SkyProfile.decode(missing), key);
            }
            for (var malformed : List.of("null", "[]", "{}", "true", "\"1\"")) {
                if (key.equals("ambient_sound") && malformed.equals("\"1\"")) { continue; }
                var input = original.deepCopy();
                input.add(key, JsonParser.parseString(malformed));
                assertThrows(IllegalArgumentException.class, () -> SkyProfile.decode(input), key + ": " + malformed);
            }
        }
    }

    @Test void fieldRangesAndExactIntegersAreStrict() {
        for (var entry : Map.of("schema_version", List.of("0", "2", "1.0", "1e0"),
                "day_color", List.of("-1", "16777216", "1.5", "1e2"),
                "sun_radius", List.of("0.49", "12.01", "1e999"),
                "sun_opacity", List.of("-0.1", "1.01"),
                "star_brightness", List.of("-0.1", "1.01"),
                "fog_start", List.of("-0.01", "0.951"),
                "fog_end", List.of("7.99", "512.01"),
                "sound_volume", List.of("-0.1", "0.51")).entrySet()) {
            for (String invalid : entry.getValue()) {
                var json = SkyProfiles.builtins().get(ModIdentity.id("mars")).encode();
                json.add(entry.getKey(), JsonParser.parseString(invalid));
                assertThrows(IllegalArgumentException.class, () -> SkyProfile.decode(json), entry.getKey() + ": " + invalid);
            }
        }
    }

    @Test void malformedOptionalSoundIsRejectedInsteadOfSilentlyDropped() {
        for (var invalid : List.of("INVALID ID", "x:" + "a".repeat(128), "", "minecraft:")) {
            var input = SkyProfiles.builtins().get(ModIdentity.id("mars")).encode();
            input.addProperty("ambient_sound", invalid);
            assertThrows(IllegalArgumentException.class, () -> SkyProfile.decode(input), invalid);
        }
    }
}
