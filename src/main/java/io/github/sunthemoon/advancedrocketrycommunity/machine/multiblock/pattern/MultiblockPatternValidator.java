package io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Deterministic bounded validator that delegates no chunk-loading operation. */
public final class MultiblockPatternValidator {
    private MultiblockPatternValidator() {
    }

    public static PatternValidationResult validate(
            MultiblockPatternDefinition definition,
            PatternTransform transform,
            PatternPosition controllerWorld,
            PatternWorldView world
    ) {
        if (!definition.allows(transform)) {
            return new PatternValidationResult(
                    PatternValidationStatus.INVALID_DEFINITION,
                    List.of(new PatternDiagnostic(
                            PatternDiagnosticReason.TRANSFORM_NOT_ALLOWED,
                            definition.controllerAnchor(),
                            controllerWorld,
                            "transform",
                            Optional.empty()
                    )),
                    0
            );
        }

        List<PatternDiagnostic> mismatches = new ArrayList<>();
        List<PatternDiagnostic> unloaded = new ArrayList<>();
        int inspected = 0;
        for (Map.Entry<PatternPosition, PatternMatcher> cell : definition.cells().entrySet()) {
            PatternPosition worldPosition;
            try {
                worldPosition = transform.localToWorld(
                        cell.getKey(),
                        definition.controllerAnchor(),
                        controllerWorld
                );
            } catch (ArithmeticException exception) {
                return new PatternValidationResult(
                        PatternValidationStatus.LIMIT_EXCEEDED,
                        List.of(new PatternDiagnostic(
                                PatternDiagnosticReason.POSITION_OVERFLOW,
                                cell.getKey(),
                                controllerWorld,
                                cell.getValue().summary(),
                                Optional.empty()
                        )),
                        inspected
                );
            }
            PatternObservation observation = world.observe(worldPosition);
            inspected++;
            if (!observation.loaded()) {
                addBounded(unloaded, new PatternDiagnostic(
                        PatternDiagnosticReason.CHUNK_NOT_LOADED,
                        cell.getKey(),
                        worldPosition,
                        cell.getValue().summary(),
                        Optional.empty()
                ));
                continue;
            }
            PatternBlock block = observation.block().orElseThrow();
            if (!cell.getValue().matches(block)) {
                addBounded(mismatches, new PatternDiagnostic(
                        PatternDiagnosticReason.BLOCK_MISMATCH,
                        cell.getKey(),
                        worldPosition,
                        cell.getValue().summary(),
                        Optional.of(block.blockId())
                ));
            }
        }
        if (!unloaded.isEmpty()) {
            return new PatternValidationResult(PatternValidationStatus.WAITING_UNLOADED, unloaded, inspected);
        }
        if (!mismatches.isEmpty()) {
            return new PatternValidationResult(PatternValidationStatus.MISMATCH, mismatches, inspected);
        }
        return new PatternValidationResult(PatternValidationStatus.FORMED, List.of(), inspected);
    }

    private static void addBounded(List<PatternDiagnostic> diagnostics, PatternDiagnostic diagnostic) {
        if (diagnostics.size() < PatternValidationResult.MAX_DIAGNOSTICS) {
            diagnostics.add(diagnostic);
        }
    }
}
