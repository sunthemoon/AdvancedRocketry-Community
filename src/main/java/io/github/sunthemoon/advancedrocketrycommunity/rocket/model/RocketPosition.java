package io.github.sunthemoon.advancedrocketrycommunity.rocket.model;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.validation.RocketValidationCode;

/** Integer world or relative position with overflow-checked arithmetic. */
public record RocketPosition(int x, int y, int z) implements Comparable<RocketPosition> {
    public RocketPosition add(RocketPosition offset) {
        try {
            return new RocketPosition(
                    Math.addExact(x, offset.x),
                    Math.addExact(y, offset.y),
                    Math.addExact(z, offset.z)
            );
        } catch (ArithmeticException exception) {
            throw new RocketSnapshotException(
                    RocketValidationCode.POSITION_OVERFLOW,
                    offset,
                    "Rocket position overflows world coordinates",
                    exception
            );
        }
    }

    public RocketPosition subtract(RocketPosition origin) {
        try {
            return new RocketPosition(
                    Math.subtractExact(x, origin.x),
                    Math.subtractExact(y, origin.y),
                    Math.subtractExact(z, origin.z)
            );
        } catch (ArithmeticException exception) {
            throw new RocketSnapshotException(
                    RocketValidationCode.POSITION_OVERFLOW,
                    this,
                    "Rocket relative position overflows integer coordinates",
                    exception
            );
        }
    }

    @Override
    public int compareTo(RocketPosition other) {
        int byX = Integer.compare(x, other.x);
        if (byX != 0) {
            return byX;
        }
        int byY = Integer.compare(y, other.y);
        return byY != 0 ? byY : Integer.compare(z, other.z);
    }
}
