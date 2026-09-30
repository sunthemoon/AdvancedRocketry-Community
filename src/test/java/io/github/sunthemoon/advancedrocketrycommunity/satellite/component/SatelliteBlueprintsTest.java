package io.github.sunthemoon.advancedrocketrycommunity.satellite.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.SatelliteOperationCode;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteKind;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteStats;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/** Pins ADR-049 §4 to the accepted reference vectors in docs/work/v1.6.0-preparation/examples.json. */
final class SatelliteBlueprintsTest {
    private static final Path EXAMPLES = Path.of("docs/work/v1.6.0-preparation/examples.json");

    @Test
    void everyReferenceBlueprintVectorMatches() throws Exception {
        JsonObject examples = JsonParser.parseString(Files.readString(EXAMPLES)).getAsJsonObject();
        List<SatelliteComponentDefinition> definitions = new ArrayList<>();
        for (var entry : examples.getAsJsonObject("components").entrySet()) {
            JsonObject raw = entry.getValue().getAsJsonObject().deepCopy();
            raw.addProperty("schema_version", 1);
            raw.addProperty("item", "example:" + entry.getKey());
            definitions.add(SatelliteComponentDefinition.decode(raw));
        }
        SatelliteComponentCatalog catalog = SatelliteComponentCatalog.create(definitions, id -> true).result().orElseThrow();
        int checked = 0;
        for (JsonElement element : examples.getAsJsonArray("blueprints")) {
            JsonObject vector = element.getAsJsonObject();
            JsonObject slots = vector.getAsJsonObject("slots");
            List<ResourceLocation> modules = new ArrayList<>();
            slots.getAsJsonArray("modules").forEach(module -> modules.add(item(module.getAsString())));
            SatelliteBlueprints.Evaluation evaluation = SatelliteBlueprints.evaluate(
                    optional(slots.get("chassis")),
                    optional(slots.get("primary")),
                    modules,
                    SatelliteKind.parse(vector.get("kind").getAsString()).orElseThrow(),
                    vector.has("scan_energy") ? vector.get("scan_energy").getAsInt() : 0,
                    catalog
            );
            String name = vector.get("name").getAsString();
            JsonElement expected = vector.get("expected");
            if (expected.isJsonObject()) {
                JsonObject stats = expected.getAsJsonObject();
                assertEquals(Optional.of(new SatelliteStats(
                        stats.get("power").getAsInt(), stats.get("battery").getAsInt(), stats.get("data").getAsInt(),
                        stats.get("cargo").getAsInt(), stats.get("rating").getAsInt())), evaluation.stats(), name);
                assertTrue(evaluation.accepted(), name);
            } else {
                assertEquals(SatelliteOperationCode.valueOf(expected.getAsString()), evaluation.code(), name);
                assertFalse(evaluation.accepted(), name);
            }
            checked++;
        }
        assertEquals(17, checked);
    }

    @Test
    void identityComponentsAreReDerivedAgainstTheCurrentCatalog() {
        SatelliteComponentCatalog catalog = SatelliteComponentCatalog.create(List.of(
                component("chassis", "chassis", null, 0),
                component("drill", "primary", "asteroid_miner", 10),
                component("panel", "power", null, 40),
                component("hold", "cargo", null, 9)
        ), id -> true).result().orElseThrow();
        List<ResourceLocation> identity = SatelliteBlueprints.components(item("chassis"), item("drill"),
                List.of(item("panel"), item("hold")));
        SatelliteBlueprints.Evaluation accepted = SatelliteBlueprints.evaluateIdentity(
                identity, SatelliteKind.ASTEROID_MINER, 0, catalog);
        assertEquals(Optional.of(new SatelliteStats(40, 720, 0, 9, 10)), accepted.stats());

        SatelliteComponentCatalog withoutHold = SatelliteComponentCatalog.create(List.of(
                component("chassis", "chassis", null, 0),
                component("drill", "primary", "asteroid_miner", 10),
                component("panel", "power", null, 40)
        ), id -> true).result().orElseThrow();
        assertEquals(SatelliteOperationCode.COMPONENT_UNAVAILABLE,
                SatelliteBlueprints.evaluateIdentity(identity, SatelliteKind.ASTEROID_MINER, 0, withoutHold).code());
        assertEquals(SatelliteOperationCode.INVALID_COMPONENTS,
                SatelliteBlueprints.evaluateIdentity(List.of(item("chassis")), SatelliteKind.ASTEROID_MINER, 0, catalog).code());
    }

    static SatelliteComponentDefinition component(String name, String role, String kind, int value) {
        JsonObject raw = new JsonObject();
        raw.addProperty("schema_version", 1);
        raw.addProperty("item", "example:" + name);
        raw.addProperty("role", role);
        if (kind != null) {
            raw.addProperty("kind", kind);
        }
        SatelliteComponentRole.parse(role).orElseThrow().statField().ifPresent(field -> raw.addProperty(field, value));
        return SatelliteComponentDefinition.decode(raw);
    }

    private static Optional<ResourceLocation> optional(JsonElement element) {
        return element == null || element.isJsonNull() ? Optional.empty() : Optional.of(item(element.getAsString()));
    }

    private static ResourceLocation item(String name) {
        return ResourceLocation.tryParse("example:" + name);
    }
}
