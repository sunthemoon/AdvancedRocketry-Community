package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * W130F (W130-R3): the ordinary retirement's holds that are reported once, kept apart from its state machine in
 * {@link RocketOrdinaryRetirement}: a refused admission (R4), a census Level with saving off (R5(d)) and another mod's
 * entity storage (R5(c), and D2-4 for the destination site). Moved unchanged from the coordinator; RAM only and cleared
 * with it.
 */
final class RocketRetirementHolds {
    private final Map<UUID, String> refusals = new LinkedHashMap<>();
    private final Set<String> warnedLevels = new HashSet<>();

    /** R4: true without a refusal reason; otherwise false, with one WARN per rocket and reason for at most 64 rockets. */
    boolean admitted(RocketTransferRecord record, String reason) {
        if (reason == null) { refusals.remove(record.logicalRocketId()); return true; }
        if (!reason.equals(refusals.get(record.logicalRocketId()))) {
            if (refusals.size() >= RocketOrdinaryRetirement.MAX_PENDING) { refusals.remove(refusals.keySet().iterator().next()); }
            refusals.put(record.logicalRocketId(), reason);
            AdvancedRocketryCommunity.LOGGER.warn("ARCE_TRANSFER_RETIREMENT_HELD transfer={} logical={} reason={} repair=move-copy-or-offline-whole-world",
                    record.transferId(), record.logicalRocketId(), reason);
        }
        return false;
    }

    /** An attempt's start: SLOT_UNLOADED or SAVE_OFF for the first census slot that holds it, or null when none does. */
    String slots(RocketOrdinaryRetirement.Port port, List<RocketRetirementProof.Slot> census) {
        for (var slot : census) {
            var state = port.slot(slot);
            if (state == RocketOrdinaryRetirement.SlotState.UNLOADED) { return "SLOT_UNLOADED"; }
            if (state == RocketOrdinaryRetirement.SlotState.SAVE_OFF) { saveOff(slot); return "SAVE_OFF"; }
            warnedLevels.remove("SAVE_OFF " + slot.dimension());
        }
        return null;
    }

    /** R5(d): a save-off Level holds every return there, with one WARN per Level until saving resumes. */
    void saveOff(RocketRetirementProof.Slot slot) {
        if (warnedLevels.add("SAVE_OFF " + slot.dimension())) {
            AdvancedRocketryCommunity.LOGGER.warn("ARCE_TRANSFER_RETIREMENT_SAVE_OFF level={}; ordinary returns there keep their records until saving resumes",
                    slot.dimension());
        }
    }

    /** R5(c) and D2-4: whether every census slot gives an own-store proof, with one WARN per Level for each site kind. */
    boolean supported(RocketOrdinaryRetirement.Port port, List<RocketRetirementProof.Slot> census, boolean fill) {
        for (var slot : census) {
            if (!port.captureSupported(slot)) {
                if (fill) {
                    if (warnedLevels.add("FILL_STORAGE " + slot.dimension())) {
                        AdvancedRocketryCommunity.LOGGER.warn("ARCE_TRANSFER_FILL_RETIREMENT_UNSUPPORTED_STORAGE level={}; escrow fills retain the transfer until entity storage supports a checked proof", slot.dimension());
                    }
                    return false;
                }
                if (warnedLevels.add("STORAGE " + slot.dimension())) {
                    AdvancedRocketryCommunity.LOGGER.warn("ARCE_TRANSFER_RETIREMENT_UNSUPPORTED_STORAGE level={}; another entity storage replaced "
                            + "this Level's, so ordinary returns remove their records after the in-memory return (R-035 residual)", slot.dimension());
                }
                return false;
            }
        }
        return true;
    }

    void clear() { refusals.clear(); warnedLevels.clear(); }
}
