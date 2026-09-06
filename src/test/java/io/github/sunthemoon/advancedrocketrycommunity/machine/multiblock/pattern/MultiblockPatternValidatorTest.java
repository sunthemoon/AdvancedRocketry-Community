package io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class MultiblockPatternValidatorTest {
    private static final PatternPosition CONTROLLER = new PatternPosition(0, 0, 0);
    private static final PatternPosition WORLD = new PatternPosition(20, 70, 20);

    @Test
    void closedMatcherSetFormsWithExactTagAirControllerPortAndOptional() {
        MultiblockPatternDefinition definition = representativeDefinition();
        Map<PatternPosition, PatternBlock> blocks = Map.of(
                WORLD, block("test:controller", Set.of(), false, true, Optional.empty()),
                WORLD.add(new PatternPosition(1, 0, 0)), block("minecraft:iron_block", Set.of(), false, false, Optional.empty()),
                WORLD.add(new PatternPosition(2, 0, 0)), block("test:casing", Set.of("test:casings"), false, false, Optional.empty()),
                WORLD.add(new PatternPosition(0, 1, 0)), block("minecraft:air", Set.of(), true, false, Optional.empty()),
                WORLD.add(new PatternPosition(1, 1, 0)), block("test:hatch", Set.of(), false, false, Optional.of("item_input")),
                WORLD.add(new PatternPosition(2, 1, 0)), block("minecraft:glass", Set.of(), false, false, Optional.empty())
        );

        PatternValidationResult result = validate(definition, position -> PatternObservation.loaded(blocks.get(position)));

        assertTrue(result.formed());
        assertEquals(6, result.inspectedCells());
        assertTrue(result.diagnostics().isEmpty());
    }

    @Test
    void optionalMatcherAlsoAcceptsAir() {
        MultiblockPatternDefinition definition = representativeDefinition();
        Map<PatternPosition, PatternBlock> blocks = validBlocksWithOptional(
                block("minecraft:air", Set.of(), true, false, Optional.empty())
        );

        assertTrue(validate(definition, position -> PatternObservation.loaded(blocks.get(position))).formed());
    }

    @Test
    void mismatchContainsCoordinatesExpectedAndLoadedActual() {
        MultiblockPatternDefinition definition = representativeDefinition();
        Map<PatternPosition, PatternBlock> blocks = validBlocksWithOptional(
                block("minecraft:glass", Set.of(), false, false, Optional.empty())
        );
        PatternPosition wrongWorld = WORLD.add(new PatternPosition(1, 0, 0));
        blocks.put(wrongWorld, block("minecraft:gold_block", Set.of(), false, false, Optional.empty()));

        PatternValidationResult result = validate(definition, position -> PatternObservation.loaded(blocks.get(position)));

        assertEquals(PatternValidationStatus.MISMATCH, result.status());
        PatternDiagnostic diagnostic = result.diagnostics().get(0);
        assertEquals(new PatternPosition(1, 0, 0), diagnostic.localPosition());
        assertEquals(wrongWorld, diagnostic.worldPosition());
        assertEquals("block=minecraft:iron_block", diagnostic.expected());
        assertEquals(Optional.of("minecraft:gold_block"), diagnostic.actualBlockId());
    }

    @Test
    void anyUnloadedCellReturnsWaitingWithoutInventingActualBlock() {
        MultiblockPatternDefinition definition = representativeDefinition();
        Map<PatternPosition, PatternBlock> blocks = validBlocksWithOptional(
                block("minecraft:glass", Set.of(), false, false, Optional.empty())
        );
        PatternPosition unloaded = WORLD.add(new PatternPosition(2, 0, 0));

        PatternValidationResult result = validate(
                definition,
                position -> position.equals(unloaded)
                        ? PatternObservation.unloaded()
                        : PatternObservation.loaded(blocks.get(position))
        );

        assertEquals(PatternValidationStatus.WAITING_UNLOADED, result.status());
        assertEquals(PatternDiagnosticReason.CHUNK_NOT_LOADED, result.diagnostics().get(0).reason());
        assertTrue(result.diagnostics().get(0).actualBlockId().isEmpty());
    }

    @Test
    void diagnosticsAreCappedWhileEveryBoundedCellIsInspected() {
        PatternSize size = new PatternSize(4, 4, 4);
        Map<PatternPosition, PatternMatcher> cells = completeCells(size, new PatternMatcher.ExactBlock("minecraft:iron_block"));
        cells.put(CONTROLLER, new PatternMatcher.Controller());
        MultiblockPatternDefinition definition = definition("test:diagnostics", size, cells);
        AtomicInteger reads = new AtomicInteger();

        PatternValidationResult result = validate(definition, position -> {
            reads.incrementAndGet();
            return PatternObservation.loaded(block("minecraft:air", Set.of(), true, false, Optional.empty()));
        });

        assertEquals(PatternValidationStatus.MISMATCH, result.status());
        assertEquals(32, result.diagnostics().size());
        assertEquals(64, result.inspectedCells());
        assertEquals(64, reads.get());
    }

    @Test
    void dimensionsCoverageControllerAndPortLimitsRejectBeforeObservation() {
        assertThrows(IllegalArgumentException.class, () -> new PatternSize(17, 1, 1));
        assertThrows(
                IllegalArgumentException.class,
                () -> definition(
                        "test:missing",
                        new PatternSize(2, 1, 1),
                        Map.of(CONTROLLER, new PatternMatcher.Controller())
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> definition(
                        "test:controller",
                        new PatternSize(2, 1, 1),
                        Map.of(
                                CONTROLLER, new PatternMatcher.Controller(),
                                new PatternPosition(1, 0, 0), new PatternMatcher.Controller()
                        )
                )
        );

        PatternSize manyPortsSize = new PatternSize(16, 1, 5);
        Map<PatternPosition, PatternMatcher> manyPorts = completeCells(manyPortsSize, new PatternMatcher.Air());
        manyPorts.put(CONTROLLER, new PatternMatcher.Controller());
        int assigned = 0;
        for (PatternPosition position : manyPorts.keySet().stream().sorted().toList()) {
            if (!position.equals(CONTROLLER) && assigned++ < 65) {
                manyPorts.put(position, new PatternMatcher.Port("port" + assigned));
            }
        }
        assertThrows(
                IllegalArgumentException.class,
                () -> definition("test:ports", manyPortsSize, manyPorts)
        );
    }

    @Test
    void matcherAndObservationBoundsRejectInvalidData() {
        PatternMatcher exact = new PatternMatcher.ExactBlock("minecraft:iron_block");
        assertThrows(IllegalArgumentException.class, () -> new PatternMatcher.OptionalCell(new PatternMatcher.OptionalCell(exact)));
        assertThrows(IllegalArgumentException.class, () -> new PatternMatcher.Port("Bad Channel"));
        assertThrows(
                IllegalArgumentException.class,
                () -> new PatternObservation(true, Optional.empty())
        );
        assertFalse(new PatternMatcher.OptionalCell(exact).matches(
                block("minecraft:gold_block", Set.of(), false, false, Optional.empty())
        ));
    }

    private static PatternValidationResult validate(
            MultiblockPatternDefinition definition,
            PatternWorldView world
    ) {
        return MultiblockPatternValidator.validate(
                definition,
                new PatternTransform(PatternRotation.ZERO, false),
                WORLD,
                world
        );
    }

    private static MultiblockPatternDefinition representativeDefinition() {
        PatternSize size = new PatternSize(3, 2, 1);
        return definition("test:representative", size, Map.of(
                new PatternPosition(0, 0, 0), new PatternMatcher.Controller(),
                new PatternPosition(1, 0, 0), new PatternMatcher.ExactBlock("minecraft:iron_block"),
                new PatternPosition(2, 0, 0), new PatternMatcher.BlockTag("test:casings"),
                new PatternPosition(0, 1, 0), new PatternMatcher.Air(),
                new PatternPosition(1, 1, 0), new PatternMatcher.Port("item_input"),
                new PatternPosition(2, 1, 0), new PatternMatcher.OptionalCell(
                        new PatternMatcher.ExactBlock("minecraft:glass")
                )
        ));
    }

    private static Map<PatternPosition, PatternBlock> validBlocksWithOptional(PatternBlock optional) {
        Map<PatternPosition, PatternBlock> blocks = new HashMap<>();
        blocks.put(WORLD, block("test:controller", Set.of(), false, true, Optional.empty()));
        blocks.put(WORLD.add(new PatternPosition(1, 0, 0)), block("minecraft:iron_block", Set.of(), false, false, Optional.empty()));
        blocks.put(WORLD.add(new PatternPosition(2, 0, 0)), block("test:casing", Set.of("test:casings"), false, false, Optional.empty()));
        blocks.put(WORLD.add(new PatternPosition(0, 1, 0)), block("minecraft:air", Set.of(), true, false, Optional.empty()));
        blocks.put(WORLD.add(new PatternPosition(1, 1, 0)), block("test:hatch", Set.of(), false, false, Optional.of("item_input")));
        blocks.put(WORLD.add(new PatternPosition(2, 1, 0)), optional);
        return blocks;
    }

    private static Map<PatternPosition, PatternMatcher> completeCells(PatternSize size, PatternMatcher matcher) {
        Map<PatternPosition, PatternMatcher> cells = new HashMap<>();
        for (int y = 0; y < size.y(); y++) {
            for (int z = 0; z < size.z(); z++) {
                for (int x = 0; x < size.x(); x++) {
                    cells.put(new PatternPosition(x, y, z), matcher);
                }
            }
        }
        return cells;
    }

    private static MultiblockPatternDefinition definition(
            String id,
            PatternSize size,
            Map<PatternPosition, PatternMatcher> cells
    ) {
        return new MultiblockPatternDefinition(
                id,
                1,
                1_000,
                size,
                CONTROLLER,
                Set.of(PatternRotation.ZERO, PatternRotation.CLOCKWISE_90),
                true,
                cells
        );
    }

    private static PatternBlock block(
            String id,
            Set<String> tags,
            boolean air,
            boolean controller,
            Optional<String> port
    ) {
        return new PatternBlock(id, tags, air, controller, port);
    }
}
