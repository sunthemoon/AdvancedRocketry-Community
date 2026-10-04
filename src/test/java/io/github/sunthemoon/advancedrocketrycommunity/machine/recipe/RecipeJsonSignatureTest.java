package io.github.sunthemoon.advancedrocketrycommunity.machine.recipe;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

class RecipeJsonSignatureTest {
    @Test void keysWhitespaceAndIntegerRepresentationsAreCanonical() {
        assertEquals(RecipeJsonSignature.signature(JsonParser.parseString("{\"b\":2e0,\"a\":1.0}")),
                RecipeJsonSignature.signature(JsonParser.parseString(" { \"a\": 1, \"b\": 2 } ")));
    }

    @Test void arraysAndEverySemanticValueRemainSignificant() {
        assertNotEquals(RecipeJsonSignature.signature(JsonParser.parseString("[1,2]")),
                RecipeJsonSignature.signature(JsonParser.parseString("[2,1]")));
        JsonObject base = JsonParser.parseString("{\"time\":20,\"energy\":40,\"chance\":1,\"flag\":true}").getAsJsonObject();
        for (String field : base.keySet()) {
            JsonObject changed = base.deepCopy();
            if (field.equals("flag")) { changed.addProperty(field, false); }
            else { changed.addProperty(field, 2); }
            assertNotEquals(RecipeJsonSignature.signature(base), RecipeJsonSignature.signature(changed));
        }
    }

    @Test void oversizedDeepNullFractionalAndNonIntegerNumbersFailBoundedly() {
        for (String json : new String[]{"null", "1.5", "2147483648", "1e999999"}) {
            assertThrows(IllegalArgumentException.class, () -> RecipeJsonSignature.canonical(JsonParser.parseString(json)));
        }
        JsonArray root = new JsonArray(); JsonArray cursor = root;
        for (int i = 0; i < 17; i++) { JsonArray next = new JsonArray(); cursor.add(next); cursor = next; }
        assertThrows(IllegalArgumentException.class, () -> RecipeJsonSignature.canonical(root));
        JsonArray many = new JsonArray();
        for (int i = 0; i < RecipeJsonSignature.MAX_NODES + 1; i++) { many.add(i); }
        assertThrows(IllegalArgumentException.class, () -> RecipeJsonSignature.canonical(many));
        JsonObject large = new JsonObject(); large.addProperty("text", "x".repeat(65_537));
        assertThrows(IllegalArgumentException.class, () -> RecipeJsonSignature.canonical(large));
    }
}
