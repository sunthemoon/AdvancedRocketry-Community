package io.github.sunthemoon.advancedrocketrycommunity.satellite.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.SatelliteIds;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.component.SatelliteComponentCatalog;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.component.SatelliteComponentDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.service.SatelliteCatalogDecoder;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

final class SatelliteKindDefinitionTest {
    private static final String SURVEY = "{\"schema_version\":2,\"id\":\"a:survey\",\"kind\":\"survey\","
            + "\"primary_component\":\"a:survey_module\",\"required_lifetime_research\":200,"
            + "\"launch_targets\":[\"advancedrocketrycommunity:earth\"],\"mission_duration_ticks\":1200,"
            + "\"instances_per_survey\":2,\"scan_energy\":5000,\"scan_radius_blocks\":32,\"scan_cell_blocks\":8}";
    private static final String SOLAR = "{\"schema_version\":2,\"id\":\"a:solar\",\"kind\":\"solar\","
            + "\"primary_component\":\"a:solar_module\",\"launch_targets\":[\"advancedrocketrycommunity:moon\"],"
            + "\"output_multiplier_percent\":100}";

    @Test
    void kindDefinitionsDecodeStrictlyAndSnapshotTheirParameters() {
        SatelliteKindDefinition survey = SatelliteKindDefinition.decode(json(SURVEY));
        assertEquals(SatelliteKind.SURVEY, survey.kind());
        assertEquals(200, survey.requiredLifetimeResearch());
        assertEquals(5000, survey.scanEnergy());
        assertEquals(new SatelliteKindState.Survey(0L, 77L, 5000, 32, 8), survey.initialState(77L));
        SatelliteKindDefinition solar = SatelliteKindDefinition.decode(json(SOLAR));
        assertEquals(0, solar.requiredLifetimeResearch());
        assertEquals(new SatelliteKindState.Solar(100, java.util.Optional.empty()), solar.initialState(0L));
        SatelliteKindDefinition miner = SatelliteKindDefinition.decode(json("{\"schema_version\":2,\"id\":\"a:m\","
                + "\"kind\":\"asteroid_miner\",\"primary_component\":\"a:drill\",\"launch_targets\":[\"a:b\"]}"));
        assertEquals(new SatelliteKindState.Plain(SatelliteKind.ASTEROID_MINER), miner.initialState(5L));

        for (String invalid : List.of(
                SOLAR.replace("\"output_multiplier_percent\":100", "\"output_multiplier_percent\":100,\"scan_energy\":5"),
                SOLAR.replace("100}", "401}"),
                SURVEY.replace("\"scan_radius_blocks\":32", "\"scan_radius_blocks\":30"),
                SURVEY.replace("\"instances_per_survey\":2", "\"instances_per_survey\":5"),
                SURVEY.replace("\"kind\":\"survey\"", "\"kind\":\"data\""),
                SURVEY.replace("\"launch_targets\":[\"advancedrocketrycommunity:earth\"]", "\"launch_targets\":[]"),
                SURVEY.replace("200,", "1000001,"),
                SURVEY.replace("}", ",\"extra\":true}"))) {
            assertThrows(IllegalArgumentException.class, () -> SatelliteKindDefinition.decode(json(invalid)), invalid);
        }
    }

    @Test
    void catalogDispatchesSchemasAndCrossChecksPrimaryComponents() {
        List<ResourceLocation> targets = List.of(ModIdentity.id("earth"), ModIdentity.id("moon"));
        SatelliteComponentCatalog components = SatelliteComponentCatalog.create(List.of(
                SatelliteComponentDefinition.decode(json("{\"schema_version\":1,\"item\":\"a:survey_module\","
                        + "\"role\":\"primary\",\"kind\":\"survey\",\"primary_rating\":10}")),
                SatelliteComponentDefinition.decode(json("{\"schema_version\":1,\"item\":\"a:solar_module\","
                        + "\"role\":\"primary\",\"kind\":\"solar\",\"primary_rating\":10}"))
        ), id -> true).result().orElseThrow();
        Map<ResourceLocation, JsonElement> resources = new LinkedHashMap<>();
        resources.put(SatelliteIds.DATA_SATELLITE, json("{\"schema_version\":1,\"id\":\"advancedrocketrycommunity:data_satellite\","
                + "\"mission_duration_ticks\":200,\"research_yield\":120,\"discovery_cost\":100,"
                + "\"allowed_targets\":[\"advancedrocketrycommunity:earth\"]}"));
        resources.put(ResourceLocation.tryParse("a:survey"), json(SURVEY));
        resources.put(ResourceLocation.tryParse("a:solar"), json(SOLAR));

        var catalog = SatelliteCatalogDecoder.decode(resources, targets, List.of(), components).result().orElseThrow();
        assertEquals(1, catalog.definitions().size());
        assertEquals(2, catalog.kindDefinitions().size());
        assertEquals(SatelliteKind.SOLAR, catalog.kindDefinitionForPrimary(ResourceLocation.tryParse("a:solar_module"))
                .orElseThrow().kind());

        assertTrue(SatelliteCatalogDecoder.decode(resources, targets, List.of(), SatelliteComponentCatalog.EMPTY)
                .error().orElseThrow().message().contains("names no primary component"));
        resources.put(ResourceLocation.tryParse("a:survey"), json(SURVEY.replace("a:survey_module", "a:solar_module")
                .replace("\"kind\":\"survey\"", "\"kind\":\"solar\"")
                .replace(",\"mission_duration_ticks\":1200,\"instances_per_survey\":2,\"scan_energy\":5000,"
                        + "\"scan_radius_blocks\":32,\"scan_cell_blocks\":8", ",\"output_multiplier_percent\":50")));
        assertTrue(SatelliteCatalogDecoder.decode(resources, targets, List.of(), components)
                .error().orElseThrow().message().contains("more than one definition"));
        resources.put(ResourceLocation.tryParse("a:survey"), json(SURVEY.replace("advancedrocketrycommunity:earth", "a:nowhere")));
        assertTrue(SatelliteCatalogDecoder.decode(resources, targets, List.of(), components)
                .error().orElseThrow().message().contains("Unknown celestial target"));
        resources.put(ResourceLocation.tryParse("a:survey"), json(SURVEY.replace("\"id\":\"a:survey\"", "\"id\":\"a:other\"")));
        assertTrue(SatelliteCatalogDecoder.decode(resources, targets, List.of(), components)
                .error().orElseThrow().message().contains("mismatched id"));
    }

    private static JsonElement json(String raw) {
        return JsonParser.parseString(raw);
    }
}
