package io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device;

/** Stable persisted device role; the registered block, never the item or client, chooses it. */
public enum RocketCargoRole {
    LOAD("rocket_fluid_loader", "SOURCE"), UNLOAD("rocket_fluid_unloader", "DESTINATION");

    private final String id;
    private final String markerRole;

    RocketCargoRole(String id, String markerRole) { this.id = id; this.markerRole = markerRole; }
    public String id() { return id; }
    public String markerRole() { return markerRole; }
}
