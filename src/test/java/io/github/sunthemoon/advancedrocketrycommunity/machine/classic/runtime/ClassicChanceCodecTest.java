package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.runtime;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.sunthemoon.advancedrocketrycommunity.machine.classic.adapter.ClassicRecipeCodec;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ClassicChanceCodecTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    private static final ResourceLocation ID = ResourceLocation.tryParse("test:chance");
    private JsonObject recipe() { return C16cMachineDataTest.json("centrifuging_enriched_lava").deepCopy(); }
    @Test void sixteenCandidatesAreSeparateFromFourDeterministicOutputsAndSeventeenRefused() {
        var json = recipe(); var chances = new JsonArray();
        for (int i = 0; i < 16; i++) { chances.add(json.getAsJsonArray("chance_outputs").get(0).deepCopy()); }
        json.add("chance_outputs", chances); assertEquals(16, ClassicRecipeCodec.decode(ID, json.toString()).chanceOutputs().size());
        var outputs = new JsonArray();
        for (int i = 0; i < 4; i++) {
            var output = new JsonObject(); output.addProperty("item", "minecraft:iron_nugget"); output.addProperty("count", 1); outputs.add(output);
        }
        json.add("item_outputs", outputs); assertEquals(4, ClassicRecipeCodec.decode(ID, json.toString()).itemOutputs().size());
        chances.add(chances.get(0).deepCopy()); assertThrows(IllegalArgumentException.class, () -> ClassicRecipeCodec.decode(ID, json.toString()));
    }
    @Test void chanceWeightsCountsUnknownFieldsAndWrongMachineTypesFailClosed() {
        for (String field : java.util.List.of("weight", "count")) {
            for (int value : new int[]{0, -1, field.equals("weight") ? 101 : 65}) {
                var json = recipe(); json.getAsJsonArray("chance_outputs").get(0).getAsJsonObject().addProperty(field, value);
                assertThrows(IllegalArgumentException.class, () -> ClassicRecipeCodec.decode(ID, json.toString()));
            }
        }
        var extra = recipe(); extra.getAsJsonArray("chance_outputs").get(0).getAsJsonObject().addProperty("other", 1);
        assertThrows(IllegalArgumentException.class, () -> ClassicRecipeCodec.decode(ID, extra.toString()));
        var wrong = recipe(); wrong.addProperty("type", "advancedrocketrycommunity:lathe");
        assertThrows(IllegalArgumentException.class, () -> ClassicRecipeCodec.decode(ID, wrong.toString()));
        var fractional = recipe().toString().replace("\"weight\":100", "\"weight\":99.5");
        assertThrows(IllegalArgumentException.class, () -> ClassicRecipeCodec.decode(ID, fractional));
    }
    @Test void candidateOrderAndWeightsEnterTheJsonSignatureButWhitespaceDoesNot() {
        var original = recipe(); var before = ClassicRecipeCodec.decode(ID, original.toString());
        assertEquals(before.jsonSignature(), ClassicRecipeCodec.decode(ID, " \n" + original + " ").jsonSignature());
        var reordered = original.deepCopy(); var candidates = reordered.getAsJsonArray("chance_outputs");
        var first = candidates.get(0); candidates.set(0, candidates.get(4)); candidates.set(4, first);
        assertNotEquals(before.jsonSignature(), ClassicRecipeCodec.decode(ID, reordered.toString()).jsonSignature());
        original.getAsJsonArray("chance_outputs").get(0).getAsJsonObject().addProperty("weight", 99);
        assertNotEquals(before.jsonSignature(), ClassicRecipeCodec.decode(ID, original.toString()).jsonSignature());
    }
}
