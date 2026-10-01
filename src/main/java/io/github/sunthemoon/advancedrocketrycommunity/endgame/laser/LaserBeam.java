package io.github.sunthemoon.advancedrocketrycommunity.endgame.laser;

/**
 * ADR-055 section 5 visual bounds shared by server and client: the beam starts at most 384 blocks above the marker,
 * stays visible for a short time after each layer, and the controller shows a short emitter glow.
 */
public final class LaserBeam {
    public static final int MAX_HEIGHT = 384;
    /** How long a layer keeps the beam visible beyond the operation interval. */
    public static final int AFTERGLOW_TICKS = 20;
    public static final float EMITTER_LENGTH = 3.0F;

    private LaserBeam() {
    }
}
