package io.github.sunthemoon.advancedrocketrycommunity.rocket.rejoin;

/** Pure bounded probe order; an unavailable lookup is never permission to load or to assume safety. */
final class RocketRejoinSafety {
    interface Probe {
        boolean bounds(int x, int y, int z);
        boolean loaded(int x, int z);
        boolean harmless(int x, int y, int z);
        boolean supported(int x, int y, int z);
        boolean clear();
        boolean survives(int x, int y, int z);
    }
    private RocketRejoinSafety() { }
    static boolean check(Probe world, int x, int y, int z) {
        if (!world.bounds(x, y, z)) { return false; }
        for (int dx = -1; dx <= 1; dx++) { for (int dz = -1; dz <= 1; dz++) {
            if (!world.loaded(x + dx, z + dz)) { return false; }
        } }
        for (int dx = -1; dx <= 1; dx++) { for (int dz = -1; dz <= 1; dz++) { for (int dy = -1; dy <= 2; dy++) {
            if (!world.harmless(x + dx, y + dy, z + dz)) { return false; }
        } } }
        return world.supported(x, y - 1, z) && world.clear() && world.survives(x, y + 1, z);
    }
}
