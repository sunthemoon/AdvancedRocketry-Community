package io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataPolicy;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedRootSupplier;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightLimits;
import java.io.IOException;
import java.nio.file.Path;
import net.minecraft.nbt.CompoundTag;

/** First-write ownership survives failed moves; only a known commit retires the absence proof. */
final class RocketJournalFirstWrite {
    private boolean committed, appeared;
    /** This attempt's beforeMove proved the target absent; attempts are sequential (one active request). */
    private boolean provedAbsent;
    private String attemptedDigest;

    synchronized void beforeMove(Path staged, Path target, CheckedSavedDataPolicy policy) throws IOException {
        if (committed) { return; }
        provedAbsent = ManagedRootSupplier.stillAbsent(target);
        if (!provedAbsent) {
            // UNKNOWN may have moved our exact candidate. No other or unreadable payload establishes ownership.
            boolean ownCandidate = false;
            if (attemptedDigest != null) {
                try {
                    ownCandidate = policy.read(target).map(RocketJournalFirstWrite::digest)
                            .filter(attemptedDigest::equals).isPresent();
                } catch (RuntimeException unreadable) {
                    appeared = true;
                    throw new IOException("Cannot establish ownership of the appeared transfer journal", unreadable);
                }
            }
            if (!ownCandidate) {
                appeared = true;
                throw new IOException("A transfer journal file appeared before this session's first known commit");
            }
        }
        attemptedDigest = digest(CheckedSavedDataFile.readPayload(staged,
                ManagedSavedDataType.ROCKET_TRANSFERS).orElseThrow());
    }

    synchronized void completed(RocketJournalCheckpoint.Outcome outcome) {
        if (outcome == RocketJournalCheckpoint.Outcome.COMMITTED) { committed = true; attemptedDigest = null; }
        // A failure before the committer proves nothing about the disk: an earlier UNKNOWN candidate may be there.
        else if (outcome == RocketJournalCheckpoint.Outcome.NOT_COMMITTED && provedAbsent) { attemptedDigest = null; }
        provedAbsent = false;
    }

    synchronized boolean appeared() { return appeared; }

    private static String digest(CompoundTag payload) {
        return RocketJournalDigest.of(payload, RocketFlightLimits.MAX_TRANSFER_JOURNAL_NBT_BYTES);
    }
}
