package io.github.sunthemoon.advancedrocketrycommunity.client;

/** The gauge stays in the unused top-right area, above all nine receive slots at y=54. */
final class CargoScreenLayout {
    static final int ENERGY_X = 152;
    static final int ENERGY_Y = 18;
    static final int ENERGY_WIDTH = 16;
    static final int ENERGY_HEIGHT = 22;
    static final int ENERGY_BOTTOM = ENERGY_Y + ENERGY_HEIGHT;

    static int fill(int stored, int capacity) {
        return stored <= 0 || capacity <= 0 ? 0
                : (int) Math.max(1, Math.min(ENERGY_HEIGHT, (long) stored * ENERGY_HEIGHT / capacity));
    }

    private CargoScreenLayout() { }
}
