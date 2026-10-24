package io.github.sunthemoon.advancedrocketrycommunity.classiccontrol.network;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * ADR-065 section 11.2: the menus that the {@code classic_controls} channel serves, with their stable wire byte,
 * their action table and the exact scalar layout of their view. A kind's action is an index below
 * {@link #actions()}; only {@link #valueAction()} carries a value (at most {@link #valueMaximum()}), and only
 * {@link #tokenAction()} carries a quote token. A new kind or a changed layout is a protocol change.
 */
public enum ClassicControlKind {
    /**
     * {@code warp_controller}: previous, next, request (value: the target index), confirm (the displayed quote's
     * token) and cancel. View: selected target (-1 when none), orbit (an index into the open names, -1 when
     * unknown), balance, pending credit, the displayed cost and class (0 none, 1 in-system, 2 interstellar) and the
     * countdown in seconds (-1 when none).
     */
    WARP_CONTROLLER(0, 5, 2, 31, 3,
            new int[] {-1, -1, 0, 0, 0, 0, -1},
            new int[] {31, 32, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, 2, Short.MAX_VALUE}),
    /** {@code station_gravity_controller}: four gravity steps. View: gravity in percent. */
    STATION_GRAVITY_CONTROLLER(1, 4, -1, 0, -1, new int[] {0}, new int[] {100}),
    /** {@code station_altitude_controller}: four distance steps and GEO. View: the logical distance. */
    STATION_ALTITUDE_CONTROLLER(2, 5, -1, 0, -1, new int[] {4}, new int[] {190}),
    /**
     * {@code station_orientation_controller}: per axis four rate steps (0..11), STOP (12) and per axis a phase down
     * and up (13..18). View: the clock-exhausted flag, three rates and three whole-degree phases.
     */
    STATION_ORIENTATION_CONTROLLER(3, 19, -1, 0, -1,
            new int[] {0, -60, -60, -60, 0, 0, 0},
            new int[] {1, 60, 60, 60, 359, 359, 359}),
    /** R92: monitor actions and the 21-scalar layout documented in W153; earlier kinds are unchanged. */
    ROCKET_MONITOR(4, 8, 2, 159, 6,
            new int[] {0, -1, -4096000, -64000, -1, 0, 0, 0, -1, 0,
                    -1, 0, 0, 0, -1, -1, 0, 0, 0, -1, 0},
            new int[] {8, 8, 4096000, 64000, 159, 160, 1, 1, 27, 2,
                    4, 2048000, 2048000, 2048000, Integer.MAX_VALUE,
                    4, 2048000, 2048000, 2048000, Integer.MAX_VALUE, 2048000});

    private final int wireId;
    private final int actions;
    private final int valueAction;
    private final int valueMaximum;
    private final int tokenAction;
    private final int[] minimums;
    private final int[] maximums;

    ClassicControlKind(int wireId, int actions, int valueAction, int valueMaximum, int tokenAction, int[] minimums,
                       int[] maximums) {
        if (minimums.length != maximums.length) {
            throw new IllegalArgumentException("A view layout needs one range per value");
        }
        this.wireId = wireId;
        this.actions = actions;
        this.valueAction = valueAction;
        this.valueMaximum = valueMaximum;
        this.tokenAction = tokenAction;
        this.minimums = minimums.clone();
        this.maximums = maximums.clone();
    }

    public int wireId() {
        return wireId;
    }

    /** Actions are {@code 0..actions()-1}. */
    public int actions() {
        return actions;
    }

    /** The one action that carries a value, or -1. */
    public int valueAction() {
        return valueAction;
    }

    public int valueMaximum() {
        return valueMaximum;
    }

    /** The one action that carries a quote token, or -1. */
    public int tokenAction() {
        return tokenAction;
    }

    /** The exact number of scalar values in this kind's view. */
    public int valueCount() {
        return minimums.length;
    }

    public int minimum(int index) {
        return minimums[index];
    }

    public int maximum(int index) {
        return maximums[index];
    }

    /** Whether {@code values} has this kind's exact count and each value lies in its range. */
    public boolean validValues(List<Integer> values) {
        if (values.size() != minimums.length) {
            return false;
        }
        for (int index = 0; index < minimums.length; index++) {
            int value = values.get(index);
            if (value < minimums[index] || value > maximums[index]) {
                return false;
            }
        }
        return true;
    }

    public static Optional<ClassicControlKind> byWireId(int wireId) {
        return Arrays.stream(values()).filter(kind -> kind.wireId == wireId).findFirst();
    }
}
