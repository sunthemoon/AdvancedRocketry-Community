package io.github.sunthemoon.advancedrocketrycommunity.client.starmap;

import java.util.function.BooleanSupplier;

/** Container screens consume even blank clicks, so offer the bounded viewport first. */
public final class StarMapInput {
    private StarMapInput() { }

    public static boolean click(boolean mapMode, BooleanSupplier viewport, BooleanSupplier container) {
        return mapMode && viewport.getAsBoolean() || container.getAsBoolean();
    }
}
