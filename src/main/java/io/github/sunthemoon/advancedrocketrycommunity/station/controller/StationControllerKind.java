package io.github.sunthemoon.advancedrocketrycommunity.station.controller;

import io.github.sunthemoon.advancedrocketrycommunity.classiccontrol.network.ClassicControlKind;

/**
 * ADR-065 section 2, C17b: the four station controller blocks, by their stable block and item ID, and the
 * {@code classic_controls} kind their menus use (section 11.1).
 */
public enum StationControllerKind {
    WARP("warp_controller", ClassicControlKind.WARP_CONTROLLER),
    GRAVITY("station_gravity_controller", ClassicControlKind.STATION_GRAVITY_CONTROLLER),
    ALTITUDE("station_altitude_controller", ClassicControlKind.STATION_ALTITUDE_CONTROLLER),
    ORIENTATION("station_orientation_controller", ClassicControlKind.STATION_ORIENTATION_CONTROLLER);

    private final String id;
    private final ClassicControlKind wire;

    StationControllerKind(String id, ClassicControlKind wire) {
        this.id = id;
        this.wire = wire;
    }

    public String id() {
        return id;
    }

    /** The {@code classic_controls} kind of this controller's menu. */
    public ClassicControlKind wire() {
        return wire;
    }
}
