package io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataPolicy;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightLimits;
import java.nio.file.Path;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;

/** Session-local acknowledgement. RAM phase, dirty state and a successful ordinary save are not proof. */
final class RocketJournalCheckpoint {
    @FunctionalInterface interface DirectoryForce { void force(Path directory) throws IOException; }
    private final DirectoryForce directoryForce;
    RocketJournalCheckpoint() { this(CheckedSavedDataPolicy::forceDirectory); }
    RocketJournalCheckpoint(DirectoryForce directoryForce) { this.directoryForce = java.util.Objects.requireNonNull(directoryForce); }
    enum Outcome { UNACKNOWLEDGED, COMMITTED, NOT_COMMITTED, UNKNOWN }
    /** Checksum covers the immutable authority; phase and destination identity are its mutable envelope. */
    record RecordVersion(String checksum, String phase, UUID destination) {
        static RecordVersion of(io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord record) {
            return new RecordVersion(record.checksum(), record.phase().name(), record.destinationEntityId().orElse(null));
        }
        static RecordVersion of(CompoundTag record) {
            return new RecordVersion(record.getString("checksum"), record.getString("phase"),
                    record.hasUUID("destination_entity_id") ? record.getUUID("destination_entity_id") : null);
        }
    }
    record Acknowledgement(long sequence, String digest, Map<UUID, String> records, Set<UUID> logicalRockets,
                           Map<UUID, RecordVersion> versions) {
        Acknowledgement {
            records = Map.copyOf(records); logicalRockets = Set.copyOf(logicalRockets); versions = Map.copyOf(versions);
        }
    }

    private Acknowledgement acknowledged;
    private Outcome outcome = Outcome.UNACKNOWLEDGED;

    Outcome outcome() { return outcome; }
    Optional<Acknowledgement> acknowledged() { return Optional.ofNullable(acknowledged); }
    boolean current(long sequence) { return acknowledged != null && acknowledged.sequence() == sequence; }

    void publish(RocketJournalWriter.Completion completion) {
        outcome = completion.outcome(); acknowledged = completion.acknowledged();
    }

    Outcome write(Path file, CheckedSavedDataPolicy policy, long sequence, CompoundTag payload,
                  Map<UUID, String> records) {
        // Capture all fallible encoding/digest work before replacing authority.
        Acknowledgement candidate = new Acknowledgement(sequence,
                RocketJournalDigest.of(payload, RocketFlightLimits.MAX_TRANSFER_JOURNAL_NBT_BYTES), records,
                payload.getList("transfers", net.minecraft.nbt.Tag.TAG_COMPOUND).stream()
                        .map(raw -> ((CompoundTag) raw).getUUID("logical_rocket_id"))
                        .collect(java.util.stream.Collectors.toSet()),
                payload.getList("transfers", net.minecraft.nbt.Tag.TAG_COMPOUND).stream()
                        .map(raw -> (CompoundTag) raw).collect(java.util.stream.Collectors.toMap(
                                raw -> raw.getUUID("logical_rocket_id"), RecordVersion::of)));
        boolean[] attempted = {false};
        try {
            var checked = new CheckedSavedDataPolicy(policy.type(), (staged, target) -> {
                attempted[0] = true;
                policy.committer().commit(staged, target);
            });
            checked.replace(file, () -> payload);
            directoryForce.force(file.toAbsolutePath().normalize().getParent());
            acknowledged = candidate;
            outcome = Outcome.COMMITTED;
        } catch (RuntimeException | IOException failure) {
            if (!attempted[0]) {
                outcome = Outcome.NOT_COMMITTED;
            } else {
                try {
                    Optional<CompoundTag> disk = policy.read(file);
                    if (disk.filter(payload::equals).isPresent()) {
                        directoryForce.force(file.toAbsolutePath().normalize().getParent());
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
                } catch (RuntimeException | IOException unreadable) {
                    acknowledged = null;
                    outcome = Outcome.UNKNOWN;
                }
            }
        }
        return outcome;
    }
}
