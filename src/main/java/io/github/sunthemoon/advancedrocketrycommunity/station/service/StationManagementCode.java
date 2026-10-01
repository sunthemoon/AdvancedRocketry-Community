package io.github.sunthemoon.advancedrocketrycommunity.station.service;

/** Player-visible outcome of a local station-management command (expansion, gravity, warp). */
public enum StationManagementCode {
    ISSUED(true, "confirmation issued"),
    EXPANDED(true, "station expanded"),
    GRAVITY_SET(true, "station gravity set"),
    GRAVITY_UNCHANGED(true, "the station already uses that gravity"),
    GRAVITY_COOLDOWN(false, "the station's gravity was changed moments ago; wait 5 seconds"),
    ALREADY_EXPANDED(false, "the station already uses its largest region"),
    AUTHORITY_UNAVAILABLE(false, "station authority is unavailable"),
    NOT_LOCAL_PLAYER(false, "run this command yourself as a connected player;"
            + " command blocks, functions, signs, the console and /execute run by anyone else cannot"
            + " manage stations"),
    REGISTRY_BUSY(false, "the station registry is saving another change; try again in a few seconds"),
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
    OUTCOME_UNKNOWN(false, "saving failed with an unknown result; station changes are disabled until restart"),
    WARP_ISSUED(true, "warp confirmation issued"),
    WARP_STARTED(true, "warp countdown started"),
    WARP_CANCELLED(true, "warp countdown cancelled"),
    WARP_COMMITTED(true, "station warped"),
    WARP_STATUS(true, "warp status"),
    WARP_DISABLED(false, "station warp is disabled on this server"),
    NO_WARP_CORE(false, "look at a warp core of this station within 5 blocks"),
    WARP_CATALOG_UNAVAILABLE(false, "the celestial catalog is unavailable"),
    WARP_TARGET_UNAVAILABLE(false, "the target body is missing or cannot be orbited"),
    WARP_TARGET_UNKNOWN(false, "the target body has not been discovered"),
    WARP_SAME_ORBIT(false, "the station already orbits that body"),
    WARP_INSUFFICIENT_ENERGY(false, "the station's warp energy does not cover the cost"),
    WARP_COOLDOWN(false, "the station changed moments ago; wait 5 seconds"),
    WARP_ROCKETS_IN_MOTION(false, "a rocket that can still move uses this station, or rocket state is not"
            + " yet known; wait until it has landed or left"),
    WARP_COUNTDOWN_ACTIVE(false, "a warp countdown is already running for this station"),
    WARP_NO_COUNTDOWN(false, "no warp countdown is running for this station"),
    WARP_CAPACITY_REACHED(false, "too many warps are pending; try again shortly"),
    WARP_NO_CONFIRMATION(false, "no warp confirmation is pending; run /arce station warp <body> first"),
    WARP_CONFIRMATION_EXPIRED(false, "the confirmation expired; run /arce station warp <body> again"),
    WARP_QUOTE_CHANGED(false, "the warp cost or its class changed; request the warp again"),
    WARP_ACTOR_CHANGED(false, "the confirming player is no longer the owner or an operator"),
    ELEVATOR_BOUND(false, "a space elevator is bound to this station; unbind it first"),
    ENDGAME_UNAVAILABLE(false, "endgame data is unavailable, so station warps are refused until it loads");

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
