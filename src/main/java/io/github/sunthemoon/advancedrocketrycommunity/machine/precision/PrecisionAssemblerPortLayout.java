package io.github.sunthemoon.advancedrocketrycommunity.machine.precision;

import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternPosition;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternTransform;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Assigns wildcard physical ports to channels by local cell, across every transform. */
public final class PrecisionAssemblerPortLayout {
    public static final PatternPosition CONTROLLER_ANCHOR = new PatternPosition(1, 0, 0);
    public static final List<PatternPosition> INPUT_CELLS = List.of(
            new PatternPosition(0, 0, 0),
            new PatternPosition(2, 0, 0),
            new PatternPosition(0, 0, 1),
            new PatternPosition(2, 0, 1),
            new PatternPosition(0, 0, 2)
    );
    public static final List<PatternPosition> OUTPUT_CELLS = List.of(
            new PatternPosition(2, 0, 2),
            new PatternPosition(0, 0, 3)
    );
    public static final PatternPosition ENERGY_CELL = new PatternPosition(2, 0, 3);

    private static final Map<PatternPosition, Assignment> ASSIGNMENTS = createAssignments();

    private PrecisionAssemblerPortLayout() {
    }

    public static Optional<Assignment> atLocal(PatternPosition local) {
        return Optional.ofNullable(ASSIGNMENTS.get(Objects.requireNonNull(local, "local")));
    }

    public static Optional<Assignment> atWorld(
            PatternTransform transform,
            PatternPosition controllerWorld,
            PatternPosition portWorld
    ) {
        Objects.requireNonNull(transform, "transform");
        Objects.requireNonNull(controllerWorld, "controllerWorld");
        Objects.requireNonNull(portWorld, "portWorld");
        try {
            PatternPosition local = transform.inverseOffset(portWorld.subtract(controllerWorld))
                    .add(CONTROLLER_ANCHOR);
            return atLocal(local);
        } catch (ArithmeticException exception) {
            return Optional.empty();
        }
    }

    private static Map<PatternPosition, Assignment> createAssignments() {
        if (INPUT_CELLS.size() != PrecisionAssemblerChannels.INPUT_COUNT
                || OUTPUT_CELLS.size() != PrecisionAssemblerChannels.OUTPUT_COUNT) {
            throw new IllegalStateException("precision pattern does not match the frozen port count");
        }
        Map<PatternPosition, Assignment> assignments = new HashMap<>();
        for (int index = 0; index < INPUT_CELLS.size(); index++) {
            putUnique(assignments, INPUT_CELLS.get(index),
                    new Assignment(Role.ITEM_INPUT, index, PrecisionAssemblerChannels.input(index)));
        }
        for (int index = 0; index < OUTPUT_CELLS.size(); index++) {
            putUnique(assignments, OUTPUT_CELLS.get(index),
                    new Assignment(Role.ITEM_OUTPUT, index, PrecisionAssemblerChannels.output(index)));
        }
        putUnique(assignments, ENERGY_CELL,
                new Assignment(Role.ENERGY_INPUT, 0, PrecisionAssemblerChannels.ENERGY_INPUT));
        return Map.copyOf(assignments);
    }

    private static void putUnique(
            Map<PatternPosition, Assignment> assignments,
            PatternPosition position,
            Assignment assignment
    ) {
        if (assignments.putIfAbsent(position, assignment) != null) {
            throw new IllegalStateException("precision pattern contains duplicate port coordinates");
        }
    }

    public enum Role {
        ITEM_INPUT,
        ITEM_OUTPUT,
        ENERGY_INPUT
    }

    public record Assignment(Role role, int index, String channel) {
        public Assignment {
            Objects.requireNonNull(role, "role");
            Objects.requireNonNull(channel, "channel");
        }
    }
}
