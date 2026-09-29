package io.github.sunthemoon.advancedrocketrycommunity.station.service;

/** Player-visible outcome of a station expansion request or confirmation. */
public enum StationExpansionCode {
    ISSUED(true, "confirmation issued"),
    EXPANDED(true, "station expanded"),
    ALREADY_EXPANDED(false, "the station already uses its largest region"),
    AUTHORITY_UNAVAILABLE(false, "station authority is unavailable"),
    NOT_IN_SPACE(false, "stand inside the station in Space"),
    NOT_IN_STATION(false, "stand inside the station's current region"),
    CHUNK_UNLOADED(false, "your current chunk is not loaded"),
    UNAUTHORIZED(false, "only the owner or an operator standing in the station can expand it"),
    CAPACITY_REACHED(false, "too many expansion confirmations are pending; try again shortly"),
    NO_CONFIRMATION(false, "no expansion confirmation is pending; run /arce station expand first"),
    CONFIRMATION_EXPIRED(false, "the confirmation expired; run /arce station expand again"),
    CONFIRMATION_MISMATCH(false, "the confirmation belongs to a different station or server session"),
    STATION_CHANGED(false, "the station changed after the confirmation was issued; run /arce station expand again"),
    WRITE_FAILED(false, "the station file could not be saved; the station was not expanded"),
    OUTCOME_UNKNOWN(false, "saving failed with an unknown result; expansion is disabled until restart");

    private final boolean success;
    private final String description;

    StationExpansionCode(boolean success, String description) {
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
