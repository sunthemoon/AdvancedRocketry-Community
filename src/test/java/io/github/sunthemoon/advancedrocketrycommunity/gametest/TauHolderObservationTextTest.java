package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TauHolderObservationTextTest {
    private static final UUID LOGICAL = new UUID(1L, 2L);
    private static final UUID TRANSFER = new UUID(3L, 4L);

    @Test
    void nullContextProducesExplicitUnavailableScalars() {
        assertEquals("ARCE_TAU_HOLDER sample=POST_LOOKUP logical=NA transfer=NA level=NA"
                        + " chunk_x=NA chunk_z=NA diagnostic=MISSING_CONTEXT holder=NA",
                TauHolderObservationText.format(null, null, null, null, null, "MISSING_CONTEXT", null));
    }

    @Test
    void fixedPostLookupLabelDoesNotInventAPrePrimingSample() {
        assertTrue(line("Level: 31 ENTITY_TICKING").startsWith("ARCE_TAU_HOLDER sample=POST_LOOKUP"));
        assertEquals("Level:_31_ENTITY_TICKING", field(line("Level: 31 ENTITY_TICKING"), "holder"));
    }

    @Test
    void preservesRealTargetIdentifiersAndSignedChunkCoordinates() {
        String text = TauHolderObservationText.format(LOGICAL, TRANSFER, "advancedrocketrycommunity:tau_ceti_f",
                Integer.MIN_VALUE, Integer.MAX_VALUE, "QUERY_OK", "known");
        assertEquals(LOGICAL.toString(), field(text, "logical"));
        assertEquals(TRANSFER.toString(), field(text, "transfer"));
        assertEquals("-2147483648", field(text, "chunk_x"));
        assertEquals("2147483647", field(text, "chunk_z"));
        assertBounded(text);
    }

    @Test
    void sanitizesWhitespaceControlsNonAsciiAndUnpairedSurrogates() {
        String input = "a \t\r\n\u0000\u001f\u007f\u00e9\ud800\udc00z";
        assertEquals("a__________z", field(line(input), "holder"));
        assertBounded(line(input));
    }

    @Test
    void holderAtExact160CharactersIsNotTruncated() {
        assertEquals("h".repeat(160), field(line("h".repeat(160)), "holder"));
    }

    @Test
    void holderBeyond160UsesABoundedPrefixAndVisibleTruncation() {
        assertEquals("h".repeat(159) + "~", field(line("h".repeat(161)), "holder"));
        assertEquals("h".repeat(159) + "~", field(line("h".repeat(131072)), "holder"));
    }

    @Test
    void everyVariableFieldAtItsMaximumStillFits512AsciiBytes() {
        String text = TauHolderObservationText.format(LOGICAL, TRANSFER, "l".repeat(129), Integer.MIN_VALUE,
                Integer.MIN_VALUE, "d".repeat(25), "h".repeat(161));
        assertEquals("l".repeat(127) + "~", field(text, "level"));
        assertEquals("d".repeat(23) + "~", field(text, "diagnostic"));
        assertEquals(160, field(text, "holder").length());
        assertBounded(text);
    }

    @Test
    void nullAndEmptyNativeTextHaveDistinctBoundedRepresentations() {
        assertEquals("NA", field(line(null), "holder"));
        assertEquals("EMPTY", field(line(""), "holder"));
    }

    @Test
    void malformedLongContextCannotInjectAnotherFieldOrLine() {
        String input = "\n forged=YES \ud800".repeat(16384);
        String text = TauHolderObservationText.format(LOGICAL, TRANSFER, input, 0, 0, input, input);
        assertEquals(9, text.split(" ").length);
        assertBounded(text);
    }

    private static String line(String holder) {
        return TauHolderObservationText.format(LOGICAL, TRANSFER, "advancedrocketrycommunity:tau_ceti_f",
                0, 0, "QUERY_OK", holder);
    }

    private static String field(String text, String name) {
        for (String token : text.split(" ")) {
            if (token.startsWith(name + "=")) {
                return token.substring(name.length() + 1);
            }
        }
        throw new AssertionError("Missing field " + name);
    }

    private static void assertBounded(String text) {
        assertTrue(text.length() <= TauHolderObservationText.MAX_BYTES);
        assertEquals(text.length(), text.getBytes(StandardCharsets.US_ASCII).length);
        assertTrue(text.chars().allMatch(character -> character >= 32 && character <= 126));
    }
}
