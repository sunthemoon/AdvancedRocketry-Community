package io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class PatternTransformTest {
    @Test
    void everyRotationAndMirrorHasAnExactInverse() {
        PatternPosition anchor = new PatternPosition(1, 1, 1);
        PatternPosition worldAnchor = new PatternPosition(100, 70, -40);
        for (PatternRotation rotation : PatternRotation.values()) {
            for (boolean mirrored : new boolean[] {false, true}) {
                PatternTransform transform = new PatternTransform(rotation, mirrored);
                for (int x = 0; x < 3; x++) {
                    for (int y = 0; y < 3; y++) {
                        for (int z = 0; z < 3; z++) {
                            PatternPosition local = new PatternPosition(x, y, z);
                            PatternPosition world = transform.localToWorld(local, anchor, worldAnchor);
                            PatternPosition restored = transform.inverseOffset(world.subtract(worldAnchor)).add(anchor);
                            assertEquals(local, restored, rotation + " mirrored=" + mirrored);
                        }
                    }
                }
                assertEquals(worldAnchor, transform.localToWorld(anchor, anchor, worldAnchor));
            }
        }
    }

    @Test
    void clockwiseQuarterTurnsUseLocalRightUpBackAxes() {
        PatternPosition right = new PatternPosition(1, 0, 0);
        PatternPosition back = new PatternPosition(0, 0, 1);

        assertEquals(back, new PatternTransform(PatternRotation.CLOCKWISE_90, false).applyOffset(right));
        assertEquals(new PatternPosition(-1, 0, 0),
                new PatternTransform(PatternRotation.CLOCKWISE_90, false).applyOffset(back));
    }

    @Test
    void disallowedTransformReturnsBeforeWorldObservation() {
        MultiblockPatternDefinition definition = oneCellDefinition(Set.of(PatternRotation.ZERO), false);
        AtomicInteger observations = new AtomicInteger();

        PatternValidationResult result = MultiblockPatternValidator.validate(
                definition,
                new PatternTransform(PatternRotation.CLOCKWISE_90, false),
                new PatternPosition(0, 64, 0),
                position -> {
                    observations.incrementAndGet();
                    return PatternObservation.unloaded();
                }
        );

        assertEquals(PatternValidationStatus.INVALID_DEFINITION, result.status());
        assertEquals(PatternDiagnosticReason.TRANSFORM_NOT_ALLOWED, result.diagnostics().get(0).reason());
        assertEquals(0, observations.get());
    }

    @Test
    void worldPositionOverflowIsBoundedWithoutObservation() {
        MultiblockPatternDefinition definition = new MultiblockPatternDefinition(
                "test:overflow",
                1,
                100,
                new PatternSize(2, 1, 1),
                new PatternPosition(0, 0, 0),
                Set.of(PatternRotation.ZERO),
                false,
                Map.of(
                        new PatternPosition(0, 0, 0), new PatternMatcher.Controller(),
                        new PatternPosition(1, 0, 0), new PatternMatcher.Air()
                )
        );
        AtomicInteger observations = new AtomicInteger();

        PatternValidationResult result = MultiblockPatternValidator.validate(
                definition,
                new PatternTransform(PatternRotation.ZERO, false),
                new PatternPosition(Integer.MAX_VALUE, 0, 0),
                position -> {
                    observations.incrementAndGet();
                    return PatternObservation.unloaded();
                }
        );

        assertEquals(PatternValidationStatus.LIMIT_EXCEEDED, result.status());
        assertEquals(1, observations.get());
        assertEquals(1, result.inspectedCells());
    }

    private static MultiblockPatternDefinition oneCellDefinition(
            Set<PatternRotation> rotations,
            boolean mirror
    ) {
        PatternPosition origin = new PatternPosition(0, 0, 0);
        return new MultiblockPatternDefinition(
                "test:single",
                1,
                100,
                new PatternSize(1, 1, 1),
                origin,
                rotations,
                mirror,
                Map.of(origin, new PatternMatcher.Controller())
        );
    }
}
