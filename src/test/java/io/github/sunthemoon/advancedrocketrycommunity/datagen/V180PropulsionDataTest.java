package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.RocketFluidFuelCatalog;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPropulsion;
import java.util.*;
import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import org.junit.jupiter.api.Test;

class V180PropulsionDataTest {
    @Test void eachHardwareIdHasOriginalDeterministicArtModelsLootRecipeAndBothNames() throws Exception {
        var files = V180PropulsionData.files(true, true);
        assertEquals(8, files.get("data/advancedrocketrycommunity/tags/blocks/rocket_propulsion.json")
                .getAsJsonArray("values").size());
        assertFalse(files.containsKey("data/advancedrocketrycommunity/tags/blocks/rocket_movable.json"));
        for (String id : V180PropulsionData.IDS) {
            for (String path : List.of("models/block/", "models/item/", "blockstates/")) {
                assertTrue(files.containsKey("assets/advancedrocketrycommunity/" + path + id + ".json"));
            }
            for (String path : List.of("recipes/", "loot_tables/blocks/", "advancements/recipes/misc/")) {
                assertTrue(files.containsKey("data/advancedrocketrycommunity/" + path + id + ".json"));
            }
            for (boolean chinese : new boolean[] {false, true}) {
                assertNotNull(V180PropulsionLanguage.entries(chinese).get("block.advancedrocketrycommunity." + id));
            }
            for (String face : List.of("side", "top", "bottom")) {
                byte[] png = V180PropulsionArt.png(id, face); assertArrayEquals(png, V180PropulsionArt.png(id, face));
                var image = ImageIO.read(new ByteArrayInputStream(png)); assertEquals(16, image.getWidth()); assertEquals(16, image.getHeight());
            }
        }
    }
    @Test void generatedFuelDefinitionsReproduceTheRuntimeDefaultCatalog() {
        var files = V180PropulsionData.files(false, true);
        var docs = files.entrySet().stream().filter(entry -> entry.getKey().contains("/rocket_fluid_fuels/"))
                .map(entry -> entry.getValue().toString()).toList();
        var allowed = new EnumMap<RocketPropulsion.Role, Collection<String>>(RocketPropulsion.Role.class);
        for (var role : RocketPropulsion.Role.values()) {
            allowed.put(role, List.of("advancedrocketrycommunity:" + switch (role) {
                case MONO -> "rocket_fuel"; case OXIDIZER -> "oxygen"; default -> "hydrogen";
            }));
        }
        assertEquals(RocketFluidFuelCatalog.defaults().signature(), RocketFluidFuelCatalog.parse(docs, id -> true, allowed).signature());
    }
}
