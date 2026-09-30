package io.github.sunthemoon.advancedrocketrycommunity.station.service;

/** Player-visible outcome of a local station-management command (expansion, gravity). */
public enum StationManagementCode {
    ISSUED(true, "confirmation issued"),
    EXPANDED(true, "station expanded"),
    GRAVITY_SET(true, "station gravity set"),
    GRAVITY_UNCHANGED(true, "the station already uses that gravity"),
    GRAVITY_COOLDOWN(false, "the station's gravity was changed moments ago; wait 5 seconds"),
    ALREADY_EXPANDED(false, "the station already uses its largest region"),
    AUTHORITY_UNAVAILABLE(false, "station authority is unavailable"),
    NOT_LOCAL_PLAYER(false, "run this command yourself as a connected player;"
            + " command blocks, functions, signs and /execute cannot manage stations"),
    NOT_IN_SPACE(false, "stand inside the station in Space"),
    NOT_IN_STATION(false, "stand inside the station's current region"),
    CHUNK_UNLOADED(false, "your current chunk is not loaded"),
    UNAUTHORIZED(false, "only the owner or an operator standing in the station can manage it"),
    CAPACITY_REACHED(false, "too many expansion confirmations are pending; try again shortly"),
    NO_CONFIRMATION(false, "no expansion confirmation is pending; run /arce station expand first"),
    CONFIRMATION_EXPIRED(false, "the confirmation expired; run /arce station expand again"),
    CONFIRMATION_MISMATCH(false, "the confirmation belongs to a different station or server session"),
    STATION_CHANGED(false, "the station changed meanwhile; run the command again"),
    WRITE_FAILED(false, "the station file could not be saved; the station was not changed"),
    OUTCOME_UNKNOWN(false, "saving failed with an unknown result; station changes are disabled until restart");

    private final boolean success;
    private final String description;

    StationManagementCode(boolean success, String description) {
        this.success = success;
        this.description = description;
    }

    public boolean success() {
        return success;
    }

    public String description() {
        return description;
    }
}
