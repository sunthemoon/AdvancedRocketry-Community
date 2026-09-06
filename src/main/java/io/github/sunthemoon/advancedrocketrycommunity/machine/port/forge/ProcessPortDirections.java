package io.github.sunthemoon.advancedrocketrycommunity.machine.port.forge;

import io.github.sunthemoon.advancedrocketrycommunity.machine.port.ProcessPortSide;
import javax.annotation.Nullable;
import net.minecraft.core.Direction;

public final class ProcessPortDirections {
    private ProcessPortDirections() {
    }

    @Nullable
    public static Direction toWorld(ProcessPortSide localSide, Direction controllerFacing) {
        if (!controllerFacing.getAxis().isHorizontal()) {
            throw new IllegalArgumentException("controller facing must be horizontal");
        }
        return switch (localSide) {
            case FRONT -> controllerFacing;
            case BACK -> controllerFacing.getOpposite();
            case LEFT -> controllerFacing.getCounterClockWise();
            case RIGHT -> controllerFacing.getClockWise();
            case TOP -> Direction.UP;
            case BOTTOM -> Direction.DOWN;
            case UNSIDED -> null;
        };
    }
}
