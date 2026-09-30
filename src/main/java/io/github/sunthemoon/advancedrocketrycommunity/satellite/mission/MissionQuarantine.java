package io.github.sunthemoon.advancedrocketrycommunity.satellite.mission;

import java.util.Objects;
import java.util.regex.Pattern;

/** Why a mission is held for an operator, and what it was before (ADR-050 §3, §9). */
public record MissionQuarantine(String reason, MissionStatus previousStatus, boolean receiptSeen) {
    private static final Pattern REASON = Pattern.compile("[A-Z][A-Z0-9_]{0,47}");

    public MissionQuarantine {
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(previousStatus, "previousStatus");
        if (!REASON.matcher(reason).matches()) {
            throw new IllegalArgumentException("Quarantine reason is not a bounded code");
        }
        if (!previousStatus.unfinished() || previousStatus == MissionStatus.QUARANTINED) {
            throw new IllegalArgumentException("Only an unfinished, non-quarantined mission can be quarantined");
        }
    }
}
