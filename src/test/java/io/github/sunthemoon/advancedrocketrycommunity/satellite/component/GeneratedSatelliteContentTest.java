package io.github.sunthemoon.advancedrocketrycommunity.satellite.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteKind;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteKindDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteStats;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.service.SatelliteCatalog;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Stream;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/**
 * The committed v1.6 DataGen output is a complete, valid catalog: the ADR-049 section 2 legacy numbers, one
 * schema-2 definition per primary module, and launch targets that name generated celestial bodies.
 */
final class GeneratedSatelliteContentTest {
    private static final Path V160 = Path.of("src", "generated", "v1.6", "resources", "data", "advancedrocketrycommunity");

    @Test
    void generatedComponentsCarryTheLegacyNumbers() throws IOException {
        SatelliteComponentCatalog components = components();
        assertEquals(11, components.size());
        assertEquals(4, value(components, "satellite_solar_module", SatelliteComponentRole.POWER));
        assertEquals(40, value(components, "advanced_solar_panel", SatelliteComponentRole.POWER));
        assertEquals(10_000, value(components, "satellite_battery", SatelliteComponentRole.BATTERY));
        assertEquals(40_000, value(components, "large_satellite_battery", SatelliteComponentRole.BATTERY));
        assertEquals(1_000, value(components, "data_storage_unit", SatelliteComponentRole.DATA_STORAGE));
        assertEquals(9, value(components, "satellite_cargo_hold", SatelliteComponentRole.CARGO));
        assertEquals(0, value(components, "satellite_chassis", SatelliteComponentRole.CHASSIS));
        for (String primary : List.of("survey_scanner_module", "solar_transmitter_module", "asteroid_drill_module",
                "gas_intake_module")) {
            assertEquals(10, value(components, primary, SatelliteComponentRole.PRIMARY));
        }
    }

    @Test
    void generatedDefinitionsFormOneCatalogWithOneDefinitionPerPrimary() throws IOException {
        SatelliteComponentCatalog components = components();
        List<SatelliteDefinition> data = new ArrayList<>();
        List<SatelliteKindDefinition> kinds = new ArrayList<>();
        data.add(SatelliteDefinition.CODEC.parse(JsonOps.INSTANCE, json(Path.of("src", "generated", "v1.5", "resources",
                "data", "advancedrocketrycommunity", "satellite_definitions", "data_satellite.json")))
                .getOrThrow(false, message -> { }));
        for (Map.Entry<String, JsonElement> entry : directory(V160.resolve("satellite_definitions")).entrySet()) {
            kinds.add(SatelliteKindDefinition.decode(entry.getValue()));
        }
        SatelliteCatalog catalog = SatelliteCatalog.create(data, kinds, celestialBodies(), components)
                .getOrThrow(false, message -> { });

        assertEquals(4, catalog.kindDefinitions().size());
        for (SatelliteKind kind : List.of(SatelliteKind.SURVEY, SatelliteKind.SOLAR, SatelliteKind.ASTEROID_MINER,
                SatelliteKind.GAS_HARVESTER)) {
            assertEquals(1, catalog.kindDefinitions().stream().filter(definition -> definition.kind() == kind).count());
        }
        SatelliteKindDefinition survey = catalog.kindDefinition(ModIdentity.id("survey_satellite")).orElseThrow();
        assertEquals(new SatelliteKindDefinition.Survey(6_000, 2, 1_000, 32, 8), survey.parameters());
        assertEquals(0, survey.requiredLifetimeResearch());
        assertEquals(List.of(ModIdentity.id("gas_giant")),
                catalog.kindDefinition(ModIdentity.id("gas_harvester")).orElseThrow().launchTargets());

        // The representative survey blueprint meets its requirements with the generated numbers.
        SatelliteBlueprints.Evaluation evaluation = SatelliteBlueprints.evaluate(
                Optional.of(ModIdentity.id("satellite_chassis")), Optional.of(ModIdentity.id("survey_scanner_module")),
                List.of(ModIdentity.id("satellite_solar_module"), ModIdentity.id("data_storage_unit"),
                        ModIdentity.id("satellite_battery")),
                SatelliteKind.SURVEY, survey.scanEnergy(), components);
        assertTrue(evaluation.accepted());
        assertEquals(new SatelliteStats(4, 10_720, 1_000, 0, 10), evaluation.stats().orElseThrow());
    }

    private static int value(SatelliteComponentCatalog components, String item, SatelliteComponentRole role) {
        SatelliteComponentDefinition definition = components.get(ModIdentity.id(item)).orElseThrow();
        assertEquals(role, definition.role());
        return definition.value();
    }

    private static SatelliteComponentCatalog components() throws IOException {
        Map<ResourceLocation, JsonElement> resources = new TreeMap<>();
        directory(V160.resolve("satellite_components")).forEach((name, json) -> resources.put(ModIdentity.id(name), json));
        return SatelliteComponentCatalog.decode(resources, id -> true).getOrThrow(false, message -> { });
    }

    private static List<ResourceLocation> celestialBodies() throws IOException {
        List<ResourceLocation> bodies = new ArrayList<>();
        try (Stream<Path> files = Files.walk(Path.of("src", "generated"))) {
            for (Path file : files.filter(path -> path.getParent().endsWith("celestial_bodies")
                    && path.toString().endsWith(".json")).toList()) {
                String name = file.getFileName().toString();
                bodies.add(ModIdentity.id(name.substring(0, name.length() - ".json".length())));
            }
        }
        return bodies;
    }

    private static Map<String, JsonElement> directory(Path directory) throws IOException {
        Map<String, JsonElement> files = new TreeMap<>();
        try (Stream<Path> paths = Files.list(directory)) {
            for (Path path : paths.filter(file -> file.toString().endsWith(".json")).toList()) {
                String name = path.getFileName().toString();
                files.put(name.substring(0, name.length() - ".json".length()), json(path));
            }
        }
        return files;
    }

    private static JsonElement json(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8));
    }
}
