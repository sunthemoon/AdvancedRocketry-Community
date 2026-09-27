package io.github.sunthemoon.advancedrocketrycommunity.celestial.data;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.model.BoundedCelestialCodecs;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class BoundedDefinitionJsonTest {
    @Test
    void rawWhitespaceCountsAtBothByteLimits() throws IOException {
        for (int bound : List.of(BoundedDefinitionJson.MAX_BODY_BYTES, BoundedDefinitionJson.MAX_ROUTE_BYTES)) {
            String exact = " ".repeat(bound - 2) + "{}";
            assertTrue(read(exact, bound).isJsonObject());
            assertThrows(IOException.class, () -> read(exact + " ", bound));
        }
    }

    @Test
    void utf8BytesNotUtf16CharactersAreBounded() throws IOException {
        String exact = "\"" + "\u00e9".repeat(2_047) + "\"";
        assertEquals(4_096, exact.getBytes(StandardCharsets.UTF_8).length);
        assertEquals(2_047, read(exact, 4_096).getAsString().length());
        assertThrows(IOException.class, () -> read(exact + " ", 4_096));
        assertThrows(IOException.class, () -> BoundedDefinitionJson.read(
                new ByteArrayInputStream(new byte[] {'"', (byte) 0xc3, '"'}), 4_096));
    }

    @Test
    void duplicateKeysAreRejectedIncludingEscapesNullsAndNestedObjects() {
        for (String text : List.of("{\"a\":1,\"a\":2}", "{\"a\":null,\"a\":null}",
                "{\"nested\":{\"a\":1,\"\\u0061\":2}}")) {
            assertTrue(assertThrows(IOException.class, () -> read(text, 4_096))
                    .getMessage().contains("duplicate JSON key"));
        }
    }

    @Test
    void nestingCountsContainersBeforeAllocatingTheirChildren() throws IOException {
        assertTrue(read("[".repeat(16) + "0" + "]".repeat(16), 4_096).isJsonArray());
        assertThrows(IOException.class, () -> read("[".repeat(17) + "0" + "]".repeat(17), 4_096));
        assertTrue(read("{\"a\":".repeat(16) + "0" + "}".repeat(16), 4_096).isJsonObject());
        assertThrows(IOException.class, () -> read("{\"a\":".repeat(17) + "0" + "}".repeat(17), 4_096));
    }

    @Test
    void malformedLenientAndTrailingInputsAreRejected() {
        for (String text : List.of("", " ", "{", "{\"a\":}", "{} {}", "{} trailing",
                "{unquoted:1}", "{'a':1}", "{/*comment*/\"a\":1}", "[1,]", "{\"a\":01}",
                "{\"a\":NaN}", "{\"a\":Infinity}", "{\"a\":+1}", "\"raw\nnewline\"",
                "\"raw\ttab\"", "\"raw" + (char) 0 + "null\"", "\"\\x20\"",
                "{\"a\":TRUE}", "{\"a\":False}", "{\"a\":nuLL}", "{\"raw\nkey\":1}",
                "\"\\'\"", "{\"\\'key\":1}", "\"\\\n\"")) {
            assertThrows(IOException.class, () -> read(text, 4_096), text);
        }
    }

    @Test
    void validEscapesInKeysAndValuesRemainValid() throws IOException {
        var value = read("{\"a\\u0062\":\"\\\"\\\\\\/\\b\\f\\n\\r\\t\\u0061\"}", 4_096);
        assertEquals("\"\\/\b\f\n\r\ta", value.getAsJsonObject().get("ab").getAsString());
    }

    @Test
    void numericSpellingSurvivesForExactIntegerValidation() throws IOException {
        var values = read("[1,1e0,1.0,true,\"1\",null]", 4_096).getAsJsonArray();
        assertTrue(BoundedCelestialCodecs.EXACT_LONG.parse(JsonOps.INSTANCE, values.get(0)).result().isPresent());
        assertEquals("1e0", values.get(1).getAsNumber().toString());
        assertEquals("1.0", values.get(2).getAsNumber().toString());
        for (int index = 1; index < values.size(); index++) {
            assertTrue(BoundedCelestialCodecs.EXACT_LONG.parse(JsonOps.INSTANCE, values.get(index)).error().isPresent());
        }
    }

    @Test
    void oversizedStreamsAreNotDrained() {
        class CountingInput extends InputStream {
            int reads;
            @Override public int read() { reads++; return ' '; }
        }
        var input = new CountingInput();
        assertThrows(IOException.class, () -> BoundedDefinitionJson.read(input, 4_096));
        assertEquals(4_097, input.reads);
    }

    private static JsonElement read(String text, int bound) throws IOException {
        return BoundedDefinitionJson.read(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)), bound);
    }
}
