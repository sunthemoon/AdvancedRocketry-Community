package io.github.sunthemoon.advancedrocketrycommunity.endgame.model;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import java.util.Locale;
import java.util.Optional;

/**
 * Stable endgame status and refusal codes (ADR-054..059). They are written, sent and audited by name, never by
 * ordinal, and every one has a translation, so no status depends on colour or an icon (ADR-054 section 4).
 */
public enum EndgameCode {
    OK,
    // ADR-054 section 1 and 2: switches, ownership, device roots and structures.
    SYSTEM_DISABLED,
    UNOWNED,
    DEVICE_QUARANTINED,
    UNFORMED,
    STRUCTURE_UNLOADED,
    // Section 3 authority.
    UNAUTHORIZED,
    STATION_UNAVAILABLE,
    // Section 4 intent validation.
    NOT_A_PLAYER,
    DEVICE_CHANGED,
    TOO_FAR,
    WRONG_LEVEL,
    CHUNK_UNLOADED,
    RATE_LIMITED,
    // Section 5 protection chain.
    TARGET_UNLOADED,
    TARGET_OUT_OF_BOUNDS,
    TARGET_PROTECTED,
    // Section 7 counts.
    ACTIVE_LIMIT,
    // Sections 9 and 10: endpoints and the endgame root.
    ROOT_UNAVAILABLE,
    ROOT_FULL,
    ROOT_BUSY,
    ENDPOINT_LIMIT,
    AWAITING_WORLD_SAVE,
    ENDPOINT_RETIRED,
    ENDPOINT_POSITION_CONFLICT,
    ENDPOINT_BUSY,
    ENDPOINT_NOT_FOUND,
    /** {@code endpoint retire} is refused while the endpoint's chunk is loaded; breaking it settles from live state. */
    ENDPOINT_CHUNK_LOADED,
    // Section 6 zones (operator commands).
    ZONE_INVALID,
    ZONE_EXISTS,
    ZONE_NOT_FOUND,
    ZONE_LIMIT,
    // ADR-055 section 2: the laser drill's logical mode.
    NO_LENS,
    STOPPED,
    REDSTONE_BLOCKED,
    ORBIT_BODY_UNAVAILABLE,
    NO_TABLE,
    INSUFFICIENT_ENERGY,
    OUTPUT_FULL,
    // ADR-055 section 3: the physical mode, its laser target and the link.
    PHYSICAL_DISABLED,
    NO_TARGET,
    FOOTPRINT_AT_CHUNK_EDGE,
    TARGET_FOREIGN,
    TARGET_WRONG_BODY,
    LINK_LOST,
    LINK_UNSETTLED,
    CONFIRM_REQUIRED,
    BLOCKED_IMMUNE,
    BLOCKED_FLUID,
    TARGET_BUFFER_FULL,
    ENERGY_DEBT,
    COMPLETE,
    /** ADR-054 section 9: no body context for an endpoint's position. */
    BODY_UNAVAILABLE,
    // ADR-058: the area gravity field.
    STATION_OWNER_REQUIRED,
    FIELD_OUTSIDE_STATION,
    FIELD_DENSITY,
    /** A player's trust list holds at most 32 owners (ADR-058 section 5). */
    TRUST_LIMIT,
    // ADR-057: the black-hole generator.
    GENERATING,
    PAUSED_FULL,
    NO_FUEL,
    NO_SINGULARITY,
    // ADR-054 section 11: the transit ledger.
    /** Records, stubs and known outbox entries are at the server's or the owner's limit. */
    TRANSIT_LIMIT,
    OUTBOX_FULL,
    /** An outbox payload no longer decodes; only {@code transfer purge} removes it. */
    OUTBOX_QUARANTINED,
    /** A stack over 512 bytes stays in the input buffer. */
    PAYLOAD_TOO_LARGE,
    /** The record's destination endpoint was removed; an owner or operator redirect resolves it. */
    DESTINATION_MISSING,
    TRANSFER_NOT_FOUND,
    /** A redirect waits until its destination's index removal is durable. */
    DESTINATION_ACTIVE,
    /** The redirect target does not pass the system's route rule. */
    ROUTE_REFUSED,
    // ADR-056: the railgun.
    /** No input stack holds the minimum stack size. */
    NO_PAYLOAD,
    /** The destination lies in another star system. */
    ROUTE_OUT_OF_SYSTEM,
    // ADR-059: the space elevator.
    /** An ADR-045 endpoint rule failed; the rule is named with the code. */
    ELEVATOR_RULE,
    /** The terminal is not ACTIVE or not inside this station's committed region. */
    TERMINAL_UNAVAILABLE,
    /** A terminal within 2 blocks of the landing-pad column. */
    TERMINAL_ON_PAD,
    ANCHOR_UNAVAILABLE,
    ANCHOR_FOREIGN,
    STATION_BOUND,
    ANCHOR_BOUND,
    TERMINAL_BOUND,
    COLUMN_BOUND,
    /** A warp confirmation or countdown is pending for the station. */
    WARP_PENDING,
    /** The anchor's body now maps to another Level; the pair is kept. */
    PAIR_LEVEL_CHANGED,
    /** The endpoint has no pair. */
    NOT_BOUND,
    PAIR_LIMIT,
    /** The anchor's owner is no longer the station's owner or a member. */
    ANCHOR_OWNER_NOT_MEMBER,
    NOT_ON_PLATFORM,
    DISMOUNT_FIRST,
    RIDE_PENDING,
    RIDE_LIMIT,
    /** A ride's countdown runs; the rider stays on the platform. */
    RIDE_COUNTDOWN,
    RIDE_CANCELLED,
    ARRIVAL_UNLOADED,
    ARRIVAL_OBSTRUCTED;

    private final String translationKey = ModIdentity.MOD_ID + ".endgame.code." + name().toLowerCase(Locale.ROOT);

    public String translationKey() {
        return translationKey;
    }

    public static Optional<EndgameCode> byName(String name) {
        for (EndgameCode code : values()) {
            if (code.name().equals(name)) {
                return Optional.of(code);
            }
        }
        return Optional.empty();
    }
}
