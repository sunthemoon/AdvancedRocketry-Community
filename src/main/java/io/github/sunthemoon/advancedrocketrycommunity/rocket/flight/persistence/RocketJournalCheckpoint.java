package io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.CheckedJournalCheckpoint;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataPolicy;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;

/** Session-local acknowledgement. RAM phase, dirty state and a successful ordinary save are not proof. */
final class RocketJournalCheckpoint {
    private final CheckedJournalCheckpoint disk;
    RocketJournalCheckpoint() { this(CheckedSavedDataPolicy.DirectoryForce.PLATFORM); }
    RocketJournalCheckpoint(CheckedSavedDataPolicy.DirectoryForce directoryForce) {
        this.disk = new CheckedJournalCheckpoint(directoryForce, 35);
    }
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
    /** W44 section 2.4: {@code firstMoonLanding} is the full validated outbox read from the written payload. */
    record Acknowledgement(long sequence, String digest, Map<UUID, String> records, Set<UUID> logicalRockets,
                           Map<UUID, RecordVersion> versions,
                           io.github.sunthemoon.advancedrocketrycommunity.progression.classic.firstevent.FirstEventOutbox firstMoonLanding) {
        Acknowledgement {
            records = Map.copyOf(records); logicalRockets = Set.copyOf(logicalRockets); versions = Map.copyOf(versions);
            java.util.Objects.requireNonNull(firstMoonLanding, "firstMoonLanding");
        }
    }

    private Acknowledgement acknowledged;
    private Outcome outcome = Outcome.UNACKNOWLEDGED;

    Outcome outcome() { return outcome; }
    Optional<Acknowledgement> acknowledged() { return Optional.ofNullable(acknowledged); }
    boolean current(long sequence) { return acknowledged != null && acknowledged.sequence() == sequence; }
    Outcome unknown() { disk.unknown(); acknowledged = null; outcome = Outcome.UNKNOWN; return outcome; }

    void publish(RocketJournalWriter.Completion completion) {
        outcome = completion.outcome(); acknowledged = completion.acknowledged();
    }

    Outcome write(Path file, CheckedSavedDataPolicy policy, long sequence, CompoundTag payload,
                  Map<UUID, String> records) {
        // Capture all fallible encoding/digest work before replacing authority.
        Acknowledgement candidate = new Acknowledgement(sequence,
                "", records,
                payload.getList("transfers", net.minecraft.nbt.Tag.TAG_COMPOUND).stream()
                        .map(raw -> ((CompoundTag) raw).getUUID("logical_rocket_id"))
                        .collect(java.util.stream.Collectors.toSet()),
                payload.getList("transfers", net.minecraft.nbt.Tag.TAG_COMPOUND).stream()
                        .map(raw -> (CompoundTag) raw).collect(java.util.stream.Collectors.toMap(
                                raw -> raw.getUUID("logical_rocket_id"), RecordVersion::of)),
                io.github.sunthemoon.advancedrocketrycommunity.progression.classic.firstevent.FirstEventOutbox.decode(
                        payload.get(RocketTransferSavedData.FIRST_MOON_LANDING), io.github.sunthemoon
                                .advancedrocketrycommunity.progression.classic.firstevent.FirstEventKind.FIRST_MOON_LANDING));
        outcome = Outcome.valueOf(disk.write(file, policy, sequence, payload, records).name());
        if (outcome == Outcome.COMMITTED) {
            acknowledged = new Acknowledgement(sequence, disk.acknowledged().orElseThrow().digest(), records,
                    candidate.logicalRockets(), candidate.versions(), candidate.firstMoonLanding());
        }
        else if (outcome == Outcome.UNKNOWN) { acknowledged = null; }
        return outcome;
    }
}
