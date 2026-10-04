package io.github.sunthemoon.advancedrocketrycommunity.fluid;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.sunthemoon.advancedrocketrycommunity.datagen.V180FluidArt;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class V180FluidResourcesTest {
    private static final Path ROOT = Path.of("src/generated/v1.8/resources");
    private static final String NS = "advancedrocketrycommunity";

    private static JsonObject json(String relative) throws Exception {
        return JsonParser.parseString(Files.readString(ROOT.resolve(relative))).getAsJsonObject();
    }

    @Test void gasGiantRetainsHydrogenAndAddsNitrogenAtTheAcceptedRate() throws Exception {
        var table = json("data/" + NS + "/gas_harvest/gas_giant.json");
        assertEquals(1, table.get("schema_version").getAsInt());
        assertEquals(NS + ":gas_giant", table.get("body").getAsString());
        var products = table.getAsJsonArray("products");
        assertEquals(2, products.size());
        for (int index = 0; index < 2; index++) {
            var product = products.get(index).getAsJsonObject();
            assertEquals(NS + ":" + List.of("hydrogen", "nitrogen").get(index) + "_canister",
                    product.get("item").getAsString());
            assertEquals(8, product.get("amount_per_1000_ticks").getAsInt());
        }
        var historical = JsonParser.parseString(Files.readString(Path.of(
                "src/generated/v1.6/resources/data/" + NS + "/gas_harvest/gas_giant.json"))).getAsJsonObject();
        assertEquals(historical.getAsJsonArray("products").get(0), products.get(0));
        assertEquals(1, historical.getAsJsonArray("products").size());
        assertFalse(Files.exists(ROOT.resolve("data/" + NS + "/gas_harvest/tau_ceti_f.json")));
    }

    @Test void originalTextureBytesAndDimensionsAreDeterministic() throws Exception {
        assertEquals(13, V180FluidArt.textures().size());
        for (String name : V180FluidArt.textures()) {
            byte[] bytes = V180FluidArt.png(name);
            assertArrayEquals(bytes, V180FluidArt.png(name));
            assertArrayEquals(bytes, Files.readAllBytes(ROOT.resolve("assets/" + NS + "/textures/" + name + ".png")));
            var image = ImageIO.read(new ByteArrayInputStream(bytes));
            assertEquals(name.endsWith("_flow") ? 32 : 16, image.getWidth());
            assertEquals(image.getWidth(), image.getHeight());
            assertTrue(image.getRGB(image.getWidth() / 2, image.getHeight() / 2) != 0);
        }
        assertThrows(IllegalArgumentException.class, () -> V180FluidArt.png("item/unknown"));
    }

    @Test void onlyLiquidsHaveWorldModelsAndBucketsAndEachFluidHasTwoLabels() throws Exception {
        for (String locale : List.of("en_us", "zh_cn")) {
            var lang = json("assets/" + NS + "_v180/lang/" + locale + ".json");
            for (var definition : ClassicFluidDefinition.values()) {
                assertTrue(lang.has("fluid_type." + NS + "." + definition.id()));
                if (definition.gas()) {
                    assertFalse(Files.exists(ROOT.resolve("assets/" + NS + "/blockstates/" + definition.id() + ".json")));
                } else {
                    var state = json("assets/" + NS + "/blockstates/" + definition.id() + ".json");
                    assertEquals(NS + ":block/" + definition.id(), state.getAsJsonObject("variants")
                            .getAsJsonObject("").get("model").getAsString());
                    assertTrue(lang.has("item." + NS + "." + definition.id() + "_bucket"));
                }
            }
            assertTrue(lang.has("item." + NS + ".nitrogen_canister"));
        }
    }
}
