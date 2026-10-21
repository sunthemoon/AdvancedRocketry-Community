package io.github.sunthemoon.advancedrocketrycommunity.persistence;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataPolicy;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;

/** Shared checked journal replacement and exception-time readback, with session-local exact proof. */
public final class CheckedJournalCheckpoint {
    public enum Outcome { UNACKNOWLEDGED, COMMITTED, NOT_COMMITTED, UNKNOWN }

    public record Acknowledgement(long sequence, String digest, Map<UUID, String> records) {
        public Acknowledgement { records = Map.copyOf(records); }
    }

    private final CheckedSavedDataPolicy.DirectoryForce directoryForce;
    private final int maxDepth;
    private Acknowledgement acknowledged;
    private Outcome outcome = Outcome.UNACKNOWLEDGED;

    public CheckedJournalCheckpoint(CheckedSavedDataPolicy.DirectoryForce directoryForce, int maxDepth) {
        this.directoryForce = Objects.requireNonNull(directoryForce);
        this.maxDepth = maxDepth;
    }

    public Outcome outcome() { return outcome; }
    public Optional<Acknowledgement> acknowledged() { return Optional.ofNullable(acknowledged); }
    public boolean current(long sequence) { return acknowledged != null && acknowledged.sequence() == sequence; }
    public Outcome unknown() { acknowledged = null; outcome = Outcome.UNKNOWN; return outcome; }

    /** Encoding failures before replacement cannot resolve a previously ambiguous disk generation. */
    public Outcome refused() {
        if (outcome != Outcome.UNKNOWN) { outcome = Outcome.NOT_COMMITTED; }
        return outcome;
    }

    public Outcome write(Path file, CheckedSavedDataPolicy policy, long sequence, CompoundTag payload,
                         Map<UUID, String> records) {
        int maxBytes = Math.toIntExact(policy.maxUncompressedBytes());
        Acknowledgement candidate = new Acknowledgement(sequence,
                CanonicalNbtDigest.of(payload, maxBytes, maxDepth), records);
        boolean[] attempted = {false};
        try {
            var checked = policy.withCommitter((staged, target) -> {
                attempted[0] = true;
                policy.committer().commit(staged, target);
            });
            checked.replace(file, () -> payload);
            directoryForce.force(file.toAbsolutePath().normalize().getParent());
            acknowledged = candidate;
            outcome = Outcome.COMMITTED;
        } catch (RuntimeException | IOException failure) {
            if (!attempted[0]) {
                refused();
            } else {
                try {
                    Optional<CompoundTag> disk = policy.read(file);
                    if (disk.filter(payload::equals).isPresent()) {
                        directoryForce.force(file.toAbsolutePath().normalize().getParent());
                        acknowledged = candidate;
                        outcome = Outcome.COMMITTED;
                    } else if (disk.isEmpty() && acknowledged == null && outcome != Outcome.UNKNOWN
                            || acknowledged != null && disk.isPresent()
                            && CanonicalNbtDigest.of(disk.orElseThrow(), maxBytes, maxDepth)
                                    .equals(acknowledged.digest())) {
                        outcome = Outcome.NOT_COMMITTED;
                    } else {
                        unknown();
                    }
                } catch (RuntimeException | IOException unreadable) {
                    unknown();
                }
            }
        }
        return outcome;
    }
}
