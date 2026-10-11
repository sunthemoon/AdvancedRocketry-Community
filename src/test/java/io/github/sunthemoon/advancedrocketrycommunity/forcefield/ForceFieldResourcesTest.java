package io.github.sunthemoon.advancedrocketrycommunity.forcefield;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.datagen.V180ForceFieldData;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class ForceFieldResourcesTest {
    @Test void originalGridsModelsRecipeAndTechnicalExemptionStayWithinTheSlice() throws Exception {
        assertEquals(3, V180ForceFieldData.textures().size());
        for (var bytes : V180ForceFieldData.textures().values()) {
            var png = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(bytes));
            assertNotNull(png); assertEquals(16, png.getWidth()); assertEquals(16, png.getHeight());
        }
        var variants = V180ForceFieldData.clientFiles().get("assets/advancedrocketrycommunity/blockstates/force_field_projector.json").getAsJsonObject("variants");
        for (String direction : List.of("up", "down", "north", "south", "east", "west")) { assertTrue(variants.has("facing=" + direction)); }
        assertTrue(V180ForceFieldData.clientFiles().keySet().stream().noneMatch(path -> path.endsWith("models/item/force_field.json")));
        assertTrue(V180ForceFieldData.serverFiles().keySet().stream().noneMatch(path -> path.endsWith("blocks/force_field.json") || path.endsWith("recipes/force_field.json")));
        var recipe = V180ForceFieldData.serverFiles().get("data/advancedrocketrycommunity/recipes/force_field_projector.json");
        assertEquals("advancedrocketrycommunity:force_field_projector", recipe.getAsJsonObject("result").get("item").getAsString());
        assertEquals("advancedrocketrycommunity:control_board", recipe.getAsJsonObject("key").getAsJsonObject("C").get("item").getAsString());
        assertEquals("advancedrocketrycommunity:machine_casing", recipe.getAsJsonObject("key").getAsJsonObject("M").get("item").getAsString());
        String exemptions = Files.readString(Path.of("docs/recipe-graph-exemptions.json"));
        assertTrue(exemptions.contains("advancedrocketrycommunity:force_field"));
        assertFalse(exemptions.contains("force_field_projector"));
    }
}
