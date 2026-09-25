package io.github.sunthemoon.advancedrocketrycommunity.machine.precision;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.MultiblockPatternCatalogDecoder;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.MultiblockPatternDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.MultiblockPatternValidator;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternBlock;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternMatcher;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternObservation;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternPosition;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternRotation;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternSize;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternTransform;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternValidationStatus;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PrecisionAssemblerPatternTest {
    private static final String ID = "advancedrocketrycommunity:precision_assembler";
    private static final PatternPosition CONTROLLER = new PatternPosition(200, 80, 300);

    @Test
    void packagedPatternHasFrozenShapeAndGenericPortPositions() throws IOException {
        MultiblockPatternDefinition definition = packagedPattern();

        assertEquals(new PatternSize(3, 3, 4), definition.size());
        assertEquals(PrecisionAssemblerPortLayout.CONTROLLER_ANCHOR, definition.controllerAnchor());
        assertEquals(36, definition.cells().size());
        assertEquals(4, definition.allowedRotations().size());
        assertTrue(definition.allowMirrorLocalX());
        assertEquals(8L, definition.cells().values().stream()
                .filter(PatternMatcher.Port.class::isInstance).count());
        PrecisionAssemblerPortLayout.INPUT_CELLS.forEach(position -> assertEquals(
                new PatternMatcher.Port("item_input"), definition.cells().get(position)
        ));
        PrecisionAssemblerPortLayout.OUTPUT_CELLS.forEach(position -> assertEquals(
                new PatternMatcher.Port("item_output"), definition.cells().get(position)
        ));
        assertEquals(new PatternMatcher.Port("energy_input"),
                definition.cells().get(PrecisionAssemblerPortLayout.ENERGY_CELL));
        assertTrue(PrecisionAssemblerPortLayout.matchesDefinition(definition));

        Map<PatternPosition, PatternMatcher> remapped = new HashMap<>(definition.cells());
        remapped.put(PrecisionAssemblerPortLayout.ENERGY_CELL, new PatternMatcher.Port("item_input"));
        MultiblockPatternDefinition incompatible = new MultiblockPatternDefinition(
                definition.id(), definition.schemaVersion(), definition.encodedSizeBytes(),
                definition.size(), definition.controllerAnchor(), definition.allowedRotations(),
                definition.allowMirrorLocalX(), remapped
        );
        assertFalse(PrecisionAssemblerPortLayout.matchesDefinition(incompatible));
    }

    @Test
    void allRotationsAndMirrorsPreserveLocalChannelAssignment() throws IOException {
        MultiblockPatternDefinition definition = packagedPattern();
        for (PatternRotation rotation : PatternRotation.values()) {
            for (boolean mirrored : List.of(false, true)) {
                PatternTransform transform = new PatternTransform(rotation, mirrored);
                Map<PatternPosition, PatternBlock> world = formedWorld(definition, transform);
                assertEquals(PatternValidationStatus.FORMED,
                        MultiblockPatternValidator.validate(
                                definition, transform, CONTROLLER,
                                position -> PatternObservation.loaded(world.get(position))
                        ).status());

                List<PatternPosition> shuffled = new ArrayList<>(PrecisionAssemblerPortLayout.INPUT_CELLS);
                Collections.reverse(shuffled);
                for (PatternPosition local : shuffled) {
                    PatternPosition worldPosition = transform.localToWorld(
                            local, definition.controllerAnchor(), CONTROLLER
                    );
                    assertEquals(
                            PrecisionAssemblerPortLayout.atLocal(local),
                            PrecisionAssemblerPortLayout.atWorld(transform, CONTROLLER, worldPosition)
                    );
                }
                for (PatternPosition local : PrecisionAssemblerPortLayout.OUTPUT_CELLS) {
                    PatternPosition worldPosition = transform.localToWorld(
                            local, definition.controllerAnchor(), CONTROLLER
                    );
                    assertEquals(
                            PrecisionAssemblerPortLayout.atLocal(local),
                            PrecisionAssemblerPortLayout.atWorld(transform, CONTROLLER, worldPosition)
                    );
                }
            }
        }
        assertEquals("item_input_0", PrecisionAssemblerPortLayout.atLocal(
                PrecisionAssemblerPortLayout.INPUT_CELLS.get(0)
        ).orElseThrow().channel());
        assertEquals("item_input_4", PrecisionAssemblerPortLayout.atLocal(
                PrecisionAssemblerPortLayout.INPUT_CELLS.get(4)
        ).orElseThrow().channel());
        assertEquals("item_output_1", PrecisionAssemblerPortLayout.atLocal(
                PrecisionAssemblerPortLayout.OUTPUT_CELLS.get(1)
        ).orElseThrow().channel());
    }

    @Test
    void missingWrongAndUnloadedCellsHaveDistinctBoundedResults() throws IOException {
        MultiblockPatternDefinition definition = packagedPattern();
        PatternTransform transform = new PatternTransform(PatternRotation.ZERO, false);
        Map<PatternPosition, PatternBlock> world = formedWorld(definition, transform);
        PatternPosition inputWorld = transform.localToWorld(
                PrecisionAssemblerPortLayout.INPUT_CELLS.get(0), definition.controllerAnchor(), CONTROLLER
        );

        world.put(inputWorld, block("minecraft:air", true, false, Optional.empty()));
        assertEquals(PatternValidationStatus.MISMATCH,
                MultiblockPatternValidator.validate(
                        definition, transform, CONTROLLER,
                        position -> PatternObservation.loaded(world.get(position))
                ).status());

        world.put(inputWorld, block("advancedrocketrycommunity:precision_assembler_item_input_port",
                false, false, Optional.of("item_input")));
        Set<PatternPosition> unloaded = new HashSet<>(Set.of(inputWorld));
        var waiting = MultiblockPatternValidator.validate(
                definition, transform, CONTROLLER,
                position -> unloaded.contains(position)
                        ? PatternObservation.unloaded()
                        : PatternObservation.loaded(world.get(position))
        );
        assertEquals(PatternValidationStatus.WAITING_UNLOADED, waiting.status());
        assertFalse(waiting.diagnostics().isEmpty());
        assertTrue(waiting.diagnostics().size() <= 32);
        assertEquals(36, waiting.inspectedCells());
        assertTrue(PrecisionAssemblerPortLayout.atWorld(
                transform, CONTROLLER, new PatternPosition(Integer.MAX_VALUE, 0, 0)
        ).isEmpty());
    }

    private static MultiblockPatternDefinition packagedPattern() throws IOException {
        String path = "/data/advancedrocketrycommunity/machine_patterns/precision_assembler.json";
        try (InputStream input = PrecisionAssemblerPatternTest.class.getResourceAsStream(path)) {
            if (input == null) {
                throw new AssertionError("missing packaged Precision Assembler pattern");
            }
            return MultiblockPatternCatalogDecoder.decode(Map.of(ID, input.readAllBytes()))
                    .get(ID).orElseThrow();
        }
    }

    private static Map<PatternPosition, PatternBlock> formedWorld(
            MultiblockPatternDefinition definition,
            PatternTransform transform
    ) {
        Map<PatternPosition, PatternBlock> world = new HashMap<>();
        definition.cells().forEach((local, matcher) -> {
            PatternBlock block;
            if (matcher instanceof PatternMatcher.Controller) {
                block = block(ID, false, true, Optional.empty());
            } else if (matcher instanceof PatternMatcher.Port port) {
                block = block("advancedrocketrycommunity:precision_assembler_" + port.channel() + "_port",
                        false, false, Optional.of(port.channel()));
            } else {
                block = block("advancedrocketrycommunity:machine_casing", false, false, Optional.empty());
            }
            world.put(transform.localToWorld(local, definition.controllerAnchor(), CONTROLLER), block);
        });
        return world;
    }

    private static PatternBlock block(
            String id,
            boolean air,
            boolean controller,
            Optional<String> portChannel
    ) {
        return new PatternBlock(id, Set.of(), air, controller, portChannel);
    }
}
