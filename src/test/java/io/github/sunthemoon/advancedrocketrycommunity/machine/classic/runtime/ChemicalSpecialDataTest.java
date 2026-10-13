package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.runtime;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import io.github.sunthemoon.advancedrocketrycommunity.datagen.V180ChemicalSpecialData;
import io.github.sunthemoon.advancedrocketrycommunity.machine.classic.adapter.ClassicItemInput;
import io.github.sunthemoon.advancedrocketrycommunity.machine.classic.adapter.ClassicItemOutput;
import io.github.sunthemoon.advancedrocketrycommunity.machine.classic.adapter.ClassicRecipeCodec;
import io.github.sunthemoon.advancedrocketrycommunity.machine.classic.adapter.ClassicValidatedRecipe;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** W32 recipes keep the pinned legacy inputs, times and FE (TileChemicalReactor, carboncartridgerefresh.json). */
class ChemicalSpecialDataTest {
    private static final String PREFIX = "data/advancedrocketrycommunity/recipes/";

    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    private static ClassicValidatedRecipe rows(String id) {
        return ClassicRecipeCodec.decode(ResourceLocation.tryParse("advancedrocketrycommunity:" + id),
                V180ChemicalSpecialData.serverFiles().get(PREFIX + id + ".json").toString());
    }

    @Test void exactlyFiveChemicalReactorRecipesAreGenerated() {
        assertEquals(Set.of("chemical_reacting_space_breathing_helmet", "chemical_reacting_space_breathing_chestplate",
                        "chemical_reacting_space_breathing_leggings", "chemical_reacting_space_breathing_boots",
                        "chemical_reacting_carbon_cartridge_refresh").stream().map(id -> PREFIX + id + ".json")
                        .collect(java.util.stream.Collectors.toSet()),
                V180ChemicalSpecialData.serverFiles().keySet());
        for (String id : V180ChemicalSpecialData.serverFiles().keySet()) {
            assertEquals("advancedrocketrycommunity:chemical_reacting",
                    V180ChemicalSpecialData.serverFiles().get(id).get("type").getAsString());
        }
    }

    @Test void spaceBreathingKeepsTheLegacyInputsAndOneHundredTicksAtTenFe() {
        String[][] pieces = {{"helmet", "space_breathing_head", "forge:armors/helmets"},
                {"chestplate", "space_breathing_chest", "forge:armors/chestplates"},
                {"leggings", "space_breathing_legs", "forge:armors/leggings"}, {"boots", "space_breathing_feet", "forge:armors/boots"}};
        for (String[] piece : pieces) {
            var recipe = rows("chemical_reacting_space_breathing_" + piece[0]);
            assertEquals(piece[1], recipe.special());
            assertEquals(100, recipe.processingTicks()); assertEquals(10, recipe.energyPerTick());
            var expected = new java.util.ArrayList<>(List.of(new ClassicItemInput("{\"tag\":\"" + piece[2] + "\"}", 1),
                    new ClassicItemInput("{\"item\":\"advancedrocketrycommunity:pipe_seal\"}", 1),
                    new ClassicItemInput("{\"tag\":\"advancedrocketrycommunity:sheets/titanium_aluminide\"}", 4)));
            if (piece[0].equals("chestplate")) {
                // Legacy ItemPressureTank meta 3, "Super High Pressure Tank" in the pinned en_US.lang.
                expected.add(new ClassicItemInput("{\"item\":\"advancedrocketrycommunity:super_high_pressure_tank\"}", 1));
            }
            assertEquals(expected, recipe.itemInputs());
            assertTrue(recipe.itemOutputs().isEmpty(), "The enchanted piece is the subject itself");
            assertTrue(recipe.fluidInputs().isEmpty()); assertTrue(recipe.fluidOutputs().isEmpty());
        }
    }

    @Test void refreshTakesOneSpentCartridgeForFortyTicksAtTwentyFeAndAddsOneCharcoal() {
        var recipe = rows("chemical_reacting_carbon_cartridge_refresh");
        assertEquals("carbon_cartridge_refresh", recipe.special());
        assertEquals(40, recipe.processingTicks()); assertEquals(20, recipe.energyPerTick());
        assertEquals(List.of(new ClassicItemInput("{\"item\":\"advancedrocketrycommunity:carbon_scrubber_cartridge\"}", 1)),
                recipe.itemInputs());
        // Legacy minecraft:coal data 1 is charcoal; the refreshed cartridge is the retained subject, not this row.
        assertEquals(List.of(new ClassicItemOutput(ResourceLocation.tryParse("minecraft:charcoal"), 1)), recipe.itemOutputs());
    }

    @Test void bothLanguagesNameTheSubjectTooltipAndGeneratedFilesMatch() throws Exception {
        assertEquals(V180ChemicalSpecialData.language(false).keySet(), V180ChemicalSpecialData.language(true).keySet());
        for (var entry : V180ChemicalSpecialData.serverFiles().entrySet()) {
            Path generated = Path.of("src/generated/v1.8/resources").resolve(entry.getKey());
            assertTrue(Files.isRegularFile(generated), "runData output missing: " + generated);
            assertEquals(entry.getValue(), JsonParser.parseString(Files.readString(generated)), entry.getKey());
        }
    }
}
