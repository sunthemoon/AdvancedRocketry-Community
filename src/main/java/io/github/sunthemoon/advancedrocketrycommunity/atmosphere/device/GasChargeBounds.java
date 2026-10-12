package io.github.sunthemoon.advancedrocketrycommunity.atmosphere.device;

/** Pure pad bounds, not a player/chunk transaction or a durability implementation. */
public final class GasChargeBounds {
    public static final int BANK_MAXIMUM = 16_000;
    public static final int OPERATION_MAXIMUM = 100;
    public static boolean accepts(int source, int stored, int maximum, int requested) {
        return source >= 0 && source <= BANK_MAXIMUM && maximum > 0 && maximum <= 32_000
                && stored >= 0 && stored <= maximum && requested > 0 && requested <= OPERATION_MAXIMUM
                && requested <= source && requested <= maximum - stored;
    }
    private GasChargeBounds() { }
}
