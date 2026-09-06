package io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class MultiblockPatternJsonCodecTest {
    @Test
    void decodesCompleteSchemaOneDefinition() {
        byte[] encoded = validPattern().getBytes(StandardCharsets.UTF_8);

        MultiblockPatternDefinition definition = MultiblockPatternJsonCodec.decode(encoded);

        assertEquals("test:codec", definition.id());
        assertEquals(encoded.length, definition.encodedSizeBytes());
        assertEquals(new PatternSize(2, 1, 1), definition.size());
        assertEquals(2, definition.allowedRotations().size());
        assertTrue(definition.allowMirrorLocalX());
        assertInstanceOf(
                PatternMatcher.OptionalCell.class,
                definition.cells().get(new PatternPosition(1, 0, 0))
        );
    }

    @Test
    void rejectsUnknownFieldsAndNonIntegralNumbers() {
        String unknown = validPattern().replace(
                "\"allow_mirror_local_x\": true,",
                "\"allow_mirror_local_x\": true, \"script\": \"unsafe\","
        );
        String decimal = validPattern().replace("\"x\": 2, \"y\": 1", "\"x\": 2.5, \"y\": 1");

        PatternDefinitionException unknownFailure = assertThrows(
                PatternDefinitionException.class,
                () -> decode(unknown)
        );
        assertTrue(unknownFailure.getMessage().contains("unknown field script"));
        PatternDefinitionException decimalFailure = assertThrows(
                PatternDefinitionException.class,
                () -> decode(decimal)
        );
        assertTrue(decimalFailure.getMessage().contains("32-bit integer"));
    }

    @Test
    void rejectsDuplicatePositionsBeforeDefinitionConstruction() {
        String duplicate = validPattern().replace(
                "{\"x\": 1, \"y\": 0, \"z\": 0, \"matcher\": {\"optional\": {\"block\": \"minecraft:glass\"}}}",
                "{\"x\": 0, \"y\": 0, \"z\": 0, \"matcher\": {\"air\": true}}"
        );

        PatternDefinitionException failure = assertThrows(
                PatternDefinitionException.class,
                () -> decode(duplicate)
        );

        assertTrue(failure.getMessage().contains("duplicates a cell position"));
    }

    @Test
    void rejectsRecursiveOptionalAndMultipleMatcherKinds() {
        String recursive = validPattern().replace(
                "{\"optional\": {\"block\": \"minecraft:glass\"}}",
                "{\"optional\": {\"optional\": {\"block\": \"minecraft:glass\"}}}"
        );
        String multiple = validPattern().replace(
                "{\"controller\": true}",
                "{\"controller\": true, \"air\": true}"
        );

        assertThrows(PatternDefinitionException.class, () -> decode(recursive));
        PatternDefinitionException multipleFailure = assertThrows(
                PatternDefinitionException.class,
                () -> decode(multiple)
        );
        assertTrue(multipleFailure.getMessage().contains("exactly one matcher kind"));
    }

    @Test
    void rejectsOversizedAndMalformedUtf8BeforeJsonParsing() {
        byte[] oversized = new byte[MultiblockPatternDefinition.MAX_DEFINITION_BYTES + 1];

        PatternDefinitionException sizeFailure = assertThrows(
                PatternDefinitionException.class,
                () -> MultiblockPatternJsonCodec.decode(oversized)
        );
        assertTrue(sizeFailure.getMessage().contains("exceeds 65536 bytes"));
        PatternDefinitionException utf8Failure = assertThrows(
                PatternDefinitionException.class,
                () -> MultiblockPatternJsonCodec.decode(new byte[] {(byte) 0xc3, (byte) 0x28})
        );
        assertTrue(utf8Failure.getMessage().contains("not valid UTF-8"));
    }

    private static MultiblockPatternDefinition decode(String json) {
        return MultiblockPatternJsonCodec.decode(json.getBytes(StandardCharsets.UTF_8));
    }

    private static String validPattern() {
        return """
                {
                  "schema_version": 1,
                  "id": "test:codec",
                  "size": {"x": 2, "y": 1, "z": 1},
                  "controller_anchor": {"x": 0, "y": 0, "z": 0},
                  "allowed_rotations": [0, 90],
                  "allow_mirror_local_x": true,
                  "cells": [
                    {"x": 0, "y": 0, "z": 0, "matcher": {"controller": true}},
                    {"x": 1, "y": 0, "z": 0, "matcher": {"optional": {"block": "minecraft:glass"}}}
                  ]
                }
                """;
    }
}
