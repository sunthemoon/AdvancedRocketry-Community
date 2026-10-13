package io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataPolicy;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightLimits;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;

/** Session-local acknowledgement. RAM phase, dirty state and a successful ordinary save are not proof. */
final class RocketJournalCheckpoint {
    enum Outcome { UNACKNOWLEDGED, COMMITTED, NOT_COMMITTED, UNKNOWN }
    record Acknowledgement(long sequence, String digest, Map<UUID, String> records) {
        Acknowledgement { records = Map.copyOf(records); }
    }

    private Acknowledgement acknowledged;
    private Outcome outcome = Outcome.UNACKNOWLEDGED;

    Outcome outcome() { return outcome; }
    Optional<Acknowledgement> acknowledged() { return Optional.ofNullable(acknowledged); }
    boolean current(long sequence) { return acknowledged != null && acknowledged.sequence() == sequence; }

    Outcome write(Path file, CheckedSavedDataPolicy policy, long sequence, CompoundTag payload,
                  Map<UUID, String> records) {
        // Capture all fallible encoding/digest work before replacing authority.
        Acknowledgement candidate = new Acknowledgement(sequence,
                RocketJournalDigest.of(payload, RocketFlightLimits.MAX_TRANSFER_JOURNAL_NBT_BYTES), records);
        boolean[] attempted = {false};
        try {
            var checked = new CheckedSavedDataPolicy(policy.type(), (staged, target) -> {
                attempted[0] = true;
                policy.committer().commit(staged, target);
            });
            checked.replace(file, () -> payload);
            acknowledged = candidate;
            outcome = Outcome.COMMITTED;
        } catch (RuntimeException failure) {
            if (!attempted[0]) {
                outcome = Outcome.NOT_COMMITTED;
            } else {
                try {
                    Optional<CompoundTag> disk = policy.read(file);
                    if (disk.filter(payload::equals).isPresent()) {
                        acknowledged = candidate;
                        outcome = Outcome.COMMITTED;
                    } else if (disk.isEmpty() && acknowledged == null || acknowledged != null && disk.isPresent()
                            && RocketJournalDigest.of(disk.orElseThrow(), RocketFlightLimits.MAX_TRANSFER_JOURNAL_NBT_BYTES)
                            .equals(acknowledged.digest())) {
                        outcome = Outcome.NOT_COMMITTED;
                    } else {
                        acknowledged = null;
                        outcome = Outcome.UNKNOWN;
                    }
                } catch (RuntimeException unreadable) {
                    acknowledged = null;
                    outcome = Outcome.UNKNOWN;
                }
            }
        }
        return outcome;
    }
}
