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
    ZONE_LIMIT;

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
