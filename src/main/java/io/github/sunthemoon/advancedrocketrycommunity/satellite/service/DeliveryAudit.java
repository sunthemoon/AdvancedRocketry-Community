package io.github.sunthemoon.advancedrocketrycommunity.satellite.service;

import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.RewardEntry;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.resource.ResourceAlgorithms;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * ADR-051 section 10: one bounded {@code ARCE_MISSION_DELIVERY} line per claim, rematerialization,
 * acknowledgement, receipt drop, conflict, cancel, rebind and purge. Each names the mission, the terminal, the
 * save epoch, the owner and a SHA-256 prefix of the reward snapshot.
 */
public final class DeliveryAudit {
    public static final String PREFIX = "ARCE_MISSION_DELIVERY";
    public static final int MAX_BYTES = 512;
    private static final Pattern EVENT = Pattern.compile("[A-Z][A-Z_]{0,31}");
    private static final Pattern DETAIL = Pattern.compile("[a-z0-9_=:./ -]{0,160}");

    private DeliveryAudit() {
    }

    public static String line(String event, UUID mission, UUID terminal, long epoch, UUID owner,
                              List<RewardEntry> reward, String detail) {
        if (!EVENT.matcher(event).matches() || !DETAIL.matcher(detail).matches()) {
            throw new IllegalArgumentException("Audit event or detail is not a bounded code");
        }
        String line = PREFIX + " event=" + event + " mission=" + mission + " terminal="
                + (terminal == null ? "none" : terminal) + " epoch=" + epoch + " owner=" + owner
                + " reward=" + (reward.isEmpty() ? "none" : digest(reward))
                + (detail.isEmpty() ? "" : " " + detail);
        if (line.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw new IllegalStateException("Audit line exceeds " + MAX_BYTES + " bytes");
        }
        return line;
    }

    /** The first 16 hex characters of the SHA-256 of {@code <item>\t<count>\n} per entry, in order. */
    public static String digest(List<RewardEntry> reward) {
        StringBuilder text = new StringBuilder();
        reward.forEach(entry -> text.append(entry.item()).append('\t').append(entry.count()).append('\n'));
        return ResourceAlgorithms.sha256Hex16(text.toString().getBytes(StandardCharsets.UTF_8));
    }
}
