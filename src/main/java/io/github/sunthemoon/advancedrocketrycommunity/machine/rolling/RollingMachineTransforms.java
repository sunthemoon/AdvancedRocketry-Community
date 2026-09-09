package io.github.sunthemoon.advancedrocketrycommunity.machine.rolling;

import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternRotation;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternTransform;
import net.minecraft.core.Direction;

/** Maps a horizontal controller facing to the frozen local-axis convention. */
public final class RollingMachineTransforms {
    private RollingMachineTransforms() {
    }

    public static PatternTransform forFacing(Direction facing) {
        PatternRotation rotation = switch (facing) {
            case NORTH -> PatternRotation.ZERO;
            case EAST -> PatternRotation.CLOCKWISE_90;
            case SOUTH -> PatternRotation.CLOCKWISE_180;
            case WEST -> PatternRotation.CLOCKWISE_270;
            default -> throw new IllegalArgumentException("rolling machine facing must be horizontal");
        };
        return new PatternTransform(rotation, false);
    }
}
