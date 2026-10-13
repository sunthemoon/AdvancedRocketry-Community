package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.runtime;

/** Stable bounded menu codes; append new statuses instead of reordering these values. */
public enum ResearchStatus {
    IDLE, RUNNING, COMPLETE, REGISTRY_BLOCKED, DISABLED, UNFORMED, UNLOADED, NO_SKY, DAYTIME, WEATHER,
    BODY_UNAVAILABLE, SYSTEM_CHANGED, CATALOG_UNAVAILABLE, NO_POWER, WAITING_SAVE, QUEUED,
    SOURCE_MISSING, DEVICE_COLLISION, QUARANTINED, ACCOUNT_FULL, OFFLINE_REPAIR_REQUIRED,
    UNAUTHORIZED, DEVICE_BUSY, PROCESSOR_BUSY, OWNER_LIMIT, CAPACITY_REACHED, STORAGE_BUDGET,
    ALREADY_CLAIMED, NOT_READY, JOB_NOT_FOUND, CANCELLED, IDENTITY_CONFLICT, SOURCE_MISMATCH;

    public String key() { return "status.advancedrocketrycommunity.research." + name().toLowerCase(java.util.Locale.ROOT); }
    public static ResearchStatus fromCode(int code) {
        return code >= 0 && code < values().length ? values()[code] : OFFLINE_REPAIR_REQUIRED;
    }
}
