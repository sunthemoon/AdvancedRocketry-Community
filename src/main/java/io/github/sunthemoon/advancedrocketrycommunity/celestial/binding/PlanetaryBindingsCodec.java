package io.github.sunthemoon.advancedrocketrycommunity.celestial.binding;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.data.BoundedDefinitionJson;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.model.BoundedCelestialCodecs;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/** Independent schema; no Beta SavedData epoch or definition defaults apply. */
public final class PlanetaryBindingsCodec {
    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_BYTES = 32_768;

    private PlanetaryBindingsCodec() { }

    public static PlanetaryBindings decode(byte[] bytes) throws IOException {
        if (bytes.length > MAX_BYTES) {
            throw new IOException("Planetary bindings exceed the byte limit");
        }
        try {
            JsonElement raw = BoundedDefinitionJson.read(new ByteArrayInputStream(bytes), MAX_BYTES);
            if (!raw.isJsonObject()) {
                throw new IllegalArgumentException("Planetary bindings must be an object");
            }
            JsonObject root = raw.getAsJsonObject();
            if (!root.keySet().equals(Set.of("schema_version", "bindings"))
                    || !root.get("schema_version").isJsonPrimitive()
                    || !root.getAsJsonPrimitive("schema_version").isNumber()
                    || !"1".equals(root.get("schema_version").getAsString())) {
                throw new IllegalArgumentException("Unsupported planetary binding schema or root fields");
            }
            if (!root.get("bindings").isJsonArray()
                    || root.getAsJsonArray("bindings").size() > PlanetaryBindings.MAX_BINDINGS) {
                throw new IllegalArgumentException("Invalid or excessive planetary bindings");
            }
            var entries = new ArrayList<PlanetaryBindings.Binding>();
            for (JsonElement element : root.getAsJsonArray("bindings")) {
                if (!element.isJsonObject()) {
                    throw new IllegalArgumentException("Planetary binding must be an object");
                }
                JsonObject entry = element.getAsJsonObject();
                if (!entry.keySet().equals(entry.has("level") ? Set.of("body_id", "level") : Set.of("body_id"))) {
                    throw new IllegalArgumentException("Invalid planetary binding fields");
                }
                entries.add(new PlanetaryBindings.Binding(id(entry.get("body_id")),
                        entry.has("level") ? Optional.of(id(entry.get("level"))) : Optional.empty()));
            }
            return PlanetaryBindings.restore(entries);
        } catch (IllegalArgumentException exception) {
            throw new IOException("Invalid planetary bindings: " + exception.getMessage(), exception);
        }
    }

    public static byte[] encode(PlanetaryBindings bindings) throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("schema_version", SCHEMA_VERSION);
        JsonArray entries = new JsonArray();
        for (var binding : bindings.entries()) {
            JsonObject entry = new JsonObject();
            entry.addProperty("body_id", binding.bodyId().toString());
            binding.level().ifPresent(level -> entry.addProperty("level", level.toString()));
            entries.add(entry);
        }
        root.add("bindings", entries);
        byte[] bytes = (root + "\n").getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_BYTES) {
            throw new IOException("Planetary bindings exceed the byte limit");
        }
        return bytes;
    }

    private static ResourceLocation id(JsonElement value) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("Planetary binding ID must be a string");
        }
        String raw = value.getAsString();
        if (raw.length() > BoundedCelestialCodecs.MAX_RESOURCE_LOCATION_CHARS) {
            throw new IllegalArgumentException("Planetary binding ID exceeds its bound");
        }
        ResourceLocation parsed = ResourceLocation.tryParse(raw);
        if (parsed == null || !parsed.toString().equals(raw)) {
            throw new IllegalArgumentException("Planetary binding ID must be canonical");
        }
        return parsed;
    }
}
