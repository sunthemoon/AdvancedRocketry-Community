package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.nbt.CompoundTag;

/** C4 uses an immutable capture witness; C6 hashes the bounded native decode off-thread and confirms on the owner. */
final class EscrowCargoReadback implements Predicate<CompoundTag> {
    private final Thread owner = Thread.currentThread();
    private final UUID ledger;
    private final EscrowReconciliationObservation expected;
    private final RocketStructureSnapshot captureWitness;
    private volatile EscrowReconciliationObservation readback;

    EscrowCargoReadback(UUID ledger, EscrowReconciliationObservation expected, RocketStructureSnapshot captureWitness) {
        this.ledger = ledger; this.expected = expected; this.captureWitness = captureWitness;
    }

    @Override public boolean test(CompoundTag slot) {
        boolean capture = Thread.currentThread() == owner;
        readback = null;
        try {
            // The barrier supplies an AnvilRecordReader-bounded detached root. No live entity/Level crosses threads.
            var observed = EscrowSettlementDisk.ordinary(slot, expected.settlement().endpoint(), ledger,
                    capture ? captureWitness : null);
            if (capture) { return expected.equals(observed); }
            readback = observed;
            return true; // Comparison is owner-thread authority, not the IO worker's boolean.
        } catch (IllegalArgumentException | IllegalStateException | java.util.NoSuchElementException invalid) { return false; }
    }

    boolean confirmed() {
        EscrowTags.require(Thread.currentThread() == owner, "Cargo readback confirmation requires owner thread");
        return expected.equals(readback);
    }
}
