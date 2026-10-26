package io.github.sunthemoon.advancedrocketrycommunity.rocket.monitor;

/** R92 kind-4 status IDs, in the exact published layout order. */
public enum RocketMonitorStatus {
    UNBOUND, READY, WAITING_UNLOADED, STALE_BINDING, NOT_OWNER, ROCKET_BUSY, NO_TARGET, UNSUPPORTED_DATA, REQUESTED;
    public String translationKey() { return "monitor.advancedrocketrycommunity.status." + name().toLowerCase(java.util.Locale.ROOT); }
}
