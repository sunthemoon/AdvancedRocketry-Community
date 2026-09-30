package io.github.sunthemoon.advancedrocketrycommunity.station.elevator;

/** Outcome of an ADR-045 endpoint check: the first failing rule, in order, or {@link #VALID}. */
public enum ElevatorEndpointCode {
    REGISTRY_UNAVAILABLE(1, "the station registry is unavailable or quarantined"),
    STATION_MISSING(1, "the station does not exist"),
    UNAUTHORIZED(2, "only the station's owner or an operator may check its endpoint"),
    NOT_CURRENT_ORBIT(3, "the body is not the station's current orbit body"),
    CATALOG_UNAVAILABLE(4, "the celestial catalog is unavailable"),
    NO_SURFACE(4, "the body is missing, not landable or has no surface Level"),
    LEVEL_ABSENT(4, "the body's Level is not present on this server"),
    LEVEL_SHARED(4, "the body's Level does not resolve to exactly this body"),
    OUT_OF_RANGE(5, "the column is outside +/-30,000,000"),
    OUTSIDE_WORLD_BORDER(5, "the column is outside that Level's world border"),
    VALID(0, "valid");

    private final int rule;
    private final String description;

    ElevatorEndpointCode(int rule, String description) {
        this.rule = rule;
        this.description = description;
    }

    /** ADR-045 rule number (1-5); 0 for {@link #VALID}. */
    public int rule() {
        return rule;
    }

    public String description() {
        return description;
    }
}
