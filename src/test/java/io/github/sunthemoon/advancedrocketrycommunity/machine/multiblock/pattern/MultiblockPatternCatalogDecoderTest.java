package io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MultiblockPatternCatalogDecoderTest {
    @Test
    void decodesOriginalBytesAndSortsDefinitions() {
        byte[] alpha = definition("test:alpha");
        byte[] beta = definition("test:beta");

        MultiblockPatternCatalog catalog = MultiblockPatternCatalogDecoder.decode(
                Map.of("test:beta", beta, "test:alpha", alpha)
        );

        assertEquals(2, catalog.size());
        assertEquals(
                java.util.List.of("test:alpha", "test:beta"),
                catalog.definitions().stream().map(MultiblockPatternDefinition::id).toList()
        );
        assertEquals(alpha.length, catalog.get("test:alpha").orElseThrow().encodedSizeBytes());
    }

    @Test
    void rejectsEmptyCatalogAndMismatchedEmbeddedId() {
        assertThrows(
                PatternDefinitionException.class,
                () -> MultiblockPatternCatalogDecoder.decode(Map.of())
        );

        PatternDefinitionException mismatch = assertThrows(
                PatternDefinitionException.class,
                () -> MultiblockPatternCatalogDecoder.decode(
                        Map.of("test:file_name", definition("test:embedded"))
                )
        );
        assertTrue(mismatch.getMessage().contains("embedded id must match"));
    }

    @Test
    void rejectsCatalogBeforeDecodingMoreThanTheHardLimit() {
        Map<String, byte[]> definitions = new LinkedHashMap<>();
        for (int index = 0; index <= MultiblockPatternCatalog.MAX_DEFINITIONS; index++) {
            definitions.put("test:pattern_" + index, new byte[] {'{', '}'});
        }

        PatternDefinitionException failure = assertThrows(
                PatternDefinitionException.class,
                () -> MultiblockPatternCatalogDecoder.decode(definitions)
        );

        assertTrue(failure.getMessage().contains("exceeds 256 definitions"));
    }

    @Test
    void packagedRollingPatternIsCompleteAndUsesFourTypedPorts() throws IOException {
        String path = "/data/advancedrocketrycommunity/machine_patterns/rolling_machine.json";
        byte[] encoded;
        try (InputStream input = getClass().getResourceAsStream(path)) {
            if (input == null) {
                throw new AssertionError("missing packaged rolling pattern");
            }
            encoded = input.readAllBytes();
        }

        MultiblockPatternDefinition definition = MultiblockPatternCatalogDecoder.decode(
                Map.of("advancedrocketrycommunity:rolling_machine", encoded)
        ).get("advancedrocketrycommunity:rolling_machine").orElseThrow();

        assertEquals(new PatternSize(5, 3, 2), definition.size());
        assertEquals(new PatternPosition(2, 0, 0), definition.controllerAnchor());
        assertEquals(4, definition.allowedRotations().size());
        assertEquals(4L, definition.cells().values().stream()
                .filter(PatternMatcher.Port.class::isInstance)
                .count());
    }

    private static byte[] definition(String id) {
        return ("""
                {
                  "schema_version": 1,
                  "id": "%s",
                  "size": {"x": 1, "y": 1, "z": 1},
                  "controller_anchor": {"x": 0, "y": 0, "z": 0},
                  "allowed_rotations": [0],
                  "allow_mirror_local_x": false,
                  "cells": [
                    {"x": 0, "y": 0, "z": 0, "matcher": {"controller": true}}
                  ]
                }
                """).formatted(id).getBytes(StandardCharsets.UTF_8);
    }
}
