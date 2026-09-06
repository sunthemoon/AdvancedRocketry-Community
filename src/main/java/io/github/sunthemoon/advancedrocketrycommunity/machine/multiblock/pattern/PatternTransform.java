package io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern;

import java.util.Objects;

/** Local-X mirror followed by a clockwise rotation around local Y. */
public record PatternTransform(PatternRotation rotation, boolean mirroredLocalX) {
    public PatternTransform {
        Objects.requireNonNull(rotation, "rotation");
    }

    public PatternPosition applyOffset(PatternPosition offset) {
        int x = mirroredLocalX ? Math.negateExact(offset.x()) : offset.x();
        int z = offset.z();
        return switch (rotation) {
            case ZERO -> new PatternPosition(x, offset.y(), z);
            case CLOCKWISE_90 -> new PatternPosition(Math.negateExact(z), offset.y(), x);
            case CLOCKWISE_180 -> new PatternPosition(Math.negateExact(x), offset.y(), Math.negateExact(z));
            case CLOCKWISE_270 -> new PatternPosition(z, offset.y(), Math.negateExact(x));
        };
    }

    public PatternPosition inverseOffset(PatternPosition offset) {
        PatternPosition unrotated = switch (rotation) {
            case ZERO -> offset;
            case CLOCKWISE_90 -> new PatternPosition(offset.z(), offset.y(), Math.negateExact(offset.x()));
            case CLOCKWISE_180 -> new PatternPosition(
                    Math.negateExact(offset.x()),
                    offset.y(),
                    Math.negateExact(offset.z())
            );
            case CLOCKWISE_270 -> new PatternPosition(Math.negateExact(offset.z()), offset.y(), offset.x());
        };
        return mirroredLocalX
                ? new PatternPosition(Math.negateExact(unrotated.x()), unrotated.y(), unrotated.z())
                : unrotated;
    }

    public PatternPosition localToWorld(
            PatternPosition local,
            PatternPosition controllerAnchor,
            PatternPosition controllerWorld
    ) {
        return controllerWorld.add(applyOffset(local.subtract(controllerAnchor)));
    }
}
