package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class GuardedChunkDiagnosticTextTest {
    @Test void asciiEmptyAndLiteralNullAreDistinctAndUnchanged() {
        assertEquals(new GuardedChunkDiagnosticText.Encoded("", false), GuardedChunkDiagnosticText.encode(""));
        assertEquals(new GuardedChunkDiagnosticText.Encoded("null", false), GuardedChunkDiagnosticText.encode("null"));
        assertEquals(new GuardedChunkDiagnosticText.Encoded("ticket=33 status=minecraft:full", false),
                GuardedChunkDiagnosticText.encode("ticket=33 status=minecraft:full"));
    }

    @Test void nullInputCannotInventAnAbsentHolder() {
        assertThrows(NullPointerException.class, () -> GuardedChunkDiagnosticText.encode(null));
    }

    @Test void quoteBackslashAndControlsHaveExactSingleLineEncoding() {
        assertEquals("\\\"\\\\\\u0000\\u0009\\u000a\\u000d\\u007f",
                GuardedChunkDiagnosticText.encode("\"\\\0\t\n\r\u007f").text());
    }

    @Test void unicodeAndSurrogatesAreEscapedWithoutEncodingAmbiguity() {
        var encoded = GuardedChunkDiagnosticText.encode("\u00e9\u4e2d\ud83d\ude80\ud800");
        assertEquals("\\u00e9\\u4e2d\\ud83d\\ude80\\ud800", encoded.text());
        assertFalse(encoded.truncated());
    }

    @Test void exactAsciiCapIsNotTruncatedButNextCharacterIs() {
        String full = "x".repeat(GuardedChunkDiagnosticText.MAX_BYTES);
        assertEquals(new GuardedChunkDiagnosticText.Encoded(full, false), GuardedChunkDiagnosticText.encode(full));
        assertEquals(new GuardedChunkDiagnosticText.Encoded(full, true), GuardedChunkDiagnosticText.encode(full + "x"));
    }

    @Test void anEscapeIsNeverPartiallyWrittenAtTheCap() {
        var encoded = GuardedChunkDiagnosticText.encode("x".repeat(251) + "\n");
        assertEquals("x".repeat(251), encoded.text());
        assertTrue(encoded.truncated());
        assertEquals(new GuardedChunkDiagnosticText.Encoded("x".repeat(250) + "\\u000a", false),
                GuardedChunkDiagnosticText.encode("x".repeat(250) + "\n"));
    }

    @Test void doubleCharacterEscapesRespectBothBoundarySides() {
        assertEquals(new GuardedChunkDiagnosticText.Encoded("x".repeat(254) + "\\\"", false),
                GuardedChunkDiagnosticText.encode("x".repeat(254) + "\""));
        assertEquals(new GuardedChunkDiagnosticText.Encoded("x".repeat(255), true),
                GuardedChunkDiagnosticText.encode("x".repeat(255) + "\\"));
    }

    @Test void largeInputProducesAtMostTheFixedAsciiBudget() {
        for (String input : new String[]{"x".repeat(100_000), "\n".repeat(100_000), "\\".repeat(100_000)}) {
            var encoded = GuardedChunkDiagnosticText.encode(input);
            assertTrue(encoded.truncated());
            assertTrue(encoded.text().length() <= GuardedChunkDiagnosticText.MAX_BYTES);
            assertEquals(encoded.text().length(), encoded.text().getBytes(StandardCharsets.UTF_8).length);
            assertTrue(encoded.text().chars().allMatch(value -> value >= 32 && value <= 126));
        }
    }
}
