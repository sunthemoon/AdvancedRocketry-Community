package io.github.sunthemoon.advancedrocketrycommunity.celestial.visual;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.model.BoundedCelestialCodecs;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/** Local presentation data only; never an environment or travel authority. */
public record SkyProfile(int dayColor, int nightColor, int fogColor, int sunColor,
        double sunRadius, double sunOpacity, double starBrightness,
        double fogStart, double fogEnd, Optional<ResourceLocation> ambientSound, double soundVolume) {
    public static final int SCHEMA = 1;
    public static final int MAX_PROFILES = 128;
    public static final int MAX_BYTES = 16_384;
    public static final String DIRECTORY = "celestial_visuals";

    public SkyProfile {
        for (int color : new int[] {dayColor, nightColor, fogColor, sunColor}) {
            range(color, 0, 0xFFFFFF, "color");
        }
        range(sunRadius, 0.5, 12, "sun radius");
        range(sunOpacity, 0, 1, "sun opacity");
        range(starBrightness, 0, 1, "star brightness");
        range(fogStart, 0, 0.95, "fog start");
        range(fogEnd, 8, 512, "fog end");
        Objects.requireNonNull(ambientSound, "ambient sound");
        ambientSound.ifPresent(id -> {
            BoundedCelestialCodecs.requireId(id, "ambient sound");
            if (id.getPath().isEmpty()) { throw new IllegalArgumentException("Empty ambient sound path"); }
        });
        range(soundVolume, 0, 0.5, "sound volume");
    }

    public static SkyProfile decode(JsonElement input) {
        if (!input.isJsonObject()) {
            throw new IllegalArgumentException("Sky profile must be an object");
        }
        var json = input.getAsJsonObject();
        if (integer(json, "schema_version") != SCHEMA) {
            throw new IllegalArgumentException("Unsupported sky profile schema");
        }
        Optional<ResourceLocation> sound = Optional.empty();
        if (json.has("ambient_sound")) {
            var value = json.get("ambient_sound");
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
                throw new IllegalArgumentException("ambient_sound must be a resource ID");
            }
            var id = ResourceLocation.tryParse(value.getAsString());
            if (id == null) { throw new IllegalArgumentException("Invalid ambient sound ID"); }
            sound = Optional.of(id);
        }
        return new SkyProfile(integer(json, "day_color"), integer(json, "night_color"),
                integer(json, "fog_color"), integer(json, "sun_color"),
                number(json, "sun_radius"), number(json, "sun_opacity"), number(json, "star_brightness"),
                number(json, "fog_start"), number(json, "fog_end"), sound, number(json, "sound_volume"));
    }

    public JsonObject encode() {
        var json = new JsonObject();
        json.addProperty("schema_version", SCHEMA);
        json.addProperty("day_color", dayColor);
        json.addProperty("night_color", nightColor);
        json.addProperty("fog_color", fogColor);
        json.addProperty("sun_color", sunColor);
        json.addProperty("sun_radius", sunRadius);
        json.addProperty("sun_opacity", sunOpacity);
        json.addProperty("star_brightness", starBrightness);
        json.addProperty("fog_start", fogStart);
        json.addProperty("fog_end", fogEnd);
        ambientSound.ifPresent(id -> json.addProperty("ambient_sound", id.toString()));
        json.addProperty("sound_volume", soundVolume);
        return json;
    }

    private static int integer(JsonObject json, String key) {
        var value = json.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()
                || !value.getAsString().matches("0|[1-9][0-9]{0,7}")) {
            throw new IllegalArgumentException(key + " must be a bounded integer");
        }
        return Integer.parseInt(value.getAsString());
    }

    private static double number(JsonObject json, String key) {
        var value = json.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException(key + " must be a number");
        }
        return value.getAsDouble();
    }

    private static void range(double value, double minimum, double maximum, String name) {
        BoundedCelestialCodecs.requireRange(value, minimum, maximum, name);
    }
}
