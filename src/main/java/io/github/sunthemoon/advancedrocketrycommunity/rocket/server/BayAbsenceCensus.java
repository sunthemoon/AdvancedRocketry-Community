package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Complete bounded end coverage, never just the slots of copies already visible in RAM: each end's
 * {@link BayCensusColumns}, the shared checked set with W170-R65's low strip (R150).
 */
final class BayAbsenceCensus {
    private BayAbsenceCensus() { }

    /** Null is a hold, never a truncated census. The first slot is always the survivor's physical origin column. */
    @Nullable static List<RocketRetirementProof.Slot> slots(RocketTransferRecord record) {
        return slots(record.sourceSnapshot(), record.destinationSnapshot());
    }

    @Nullable static List<RocketRetirementProof.Slot> slots(
            io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot source,
            io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot destination) {
        var slots = new ArrayList<RocketRetirementProof.Slot>();
        slots.add(RocketRetirementProof.Slot.origin(source));
        RocketRetirementProof.addSlot(slots, RocketRetirementProof.Slot.origin(destination), RocketOrdinaryRetirement.MAX_SLOTS);
        for (var snapshot : List.of(source, destination)) {
            var columns = BayCensusColumns.of(snapshot);
            if (!columns.valid()) { return null; }
            for (var column : columns.columns()) {
                if (!RocketRetirementProof.addSlot(slots, new RocketRetirementProof.Slot(snapshot.sourceDimension(), column),
                        RocketOrdinaryRetirement.MAX_SLOTS)) { return null; }
            }
        }
        return List.copyOf(slots);
    }

    /** Includes valid non-admissible ends and in-session margins which receive no renewal ticket. */
    @Nullable static String waiting(RocketRetirementWorld port, List<RocketRetirementProof.Slot> slots) {
        for (var slot : slots) {
            var state = port.slot(slot);
            if (state == RocketOrdinaryRetirement.SlotState.UNLOADED) { return "END_LOADING"; }
            if (state == RocketOrdinaryRetirement.SlotState.SAVE_OFF) { return "SAVE_OFF"; }
            if (!port.captureSupported(slot)) { return "BARRIER_UNSUPPORTED"; }
        }
        return null;
    }
}
