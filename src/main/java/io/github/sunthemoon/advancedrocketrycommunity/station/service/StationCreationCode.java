package io.github.sunthemoon.advancedrocketrycommunity.station.service;

import java.util.Locale;

public enum StationCreationCode {
    SUCCESS,
    SERVICE_UNAVAILABLE,
    REGISTRY_BLOCKED,
    OWNER_LIMIT_REACHED,
    SPACE_UNAVAILABLE,
    INVALID_SOURCE,
    REGION_UNAVAILABLE,
    PLATFORM_BLOCKED,
    PERSISTENCE_FAILED;

    public String translationKey() {
        return "station.advancedrocketrycommunity.creation."
                + name().toLowerCase(Locale.ROOT);
    }
}

