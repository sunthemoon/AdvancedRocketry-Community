package io.github.sunthemoon.advancedrocketrycommunity.station.controller;

/** ADR-065 section 2, C17b: the four station controller blocks, by their stable block and item ID. */
public enum StationControllerKind {
    WARP("warp_controller"),
    GRAVITY("station_gravity_controller"),
    ALTITUDE("station_altitude_controller"),
    ORIENTATION("station_orientation_controller");

    private final String id;

    StationControllerKind(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }
}
