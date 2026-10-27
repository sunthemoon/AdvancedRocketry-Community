package io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.CanonicalNbtDigest;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.CheckedJournalCheckpoint;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.CheckedRootWriter;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataPolicy;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.SavedDataSchemaMigrator;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.RocketLimits;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/** ADR-074 items 2, 3 and 8: the transaction journal's capture, its IO-thread encoder and one checked replacement. */
final class RocketTransactionWriter {
    static final String LABEL = "rocket_transactions", THREAD = "ARCE-rocket-transactions";
    // Snapshot depth 32 plus transaction/list/root framing, as in the transfer journal.
    static final int DIGEST_DEPTH = 35;
    private static final ManagedSavedDataType TYPE = ManagedSavedDataType.ROCKET_TRANSACTIONS;

    private RocketTransactionWriter() { }

    /**
     * The owner-thread capture: references to at most 256 immutable entries, unsorted, and the acknowledgement and
     * outcome that the IO thread classifies a readback against. It builds no NBT (design section 2.2). {@code cut} is
     * the release-test CAPTURE seam, null in production.
     */
    record Capture(long sequence, List<RocketPersistedTransaction> entries,
                   CheckedJournalCheckpoint.Acknowledgement previous, CheckedJournalCheckpoint.Outcome previousOutcome,
                   Runnable cut) {
        Capture(long sequence, List<RocketPersistedTransaction> entries, CheckedJournalCheckpoint.Acknowledgement previous,
                CheckedJournalCheckpoint.Outcome previousOutcome) {
            this(sequence, entries, previous, previousOutcome, null);
        }

        Capture {
            if (sequence < 1 || entries.size() > RocketLimits.MAX_ACTIVE_TRANSACTIONS) {
                throw new IllegalArgumentException("Transaction capture exceeds its fixed bound");
            }
            // Records, regions and snapshots are immutable; snapshot payload tags are copied on construction and access.
            entries = List.copyOf(entries);
            Objects.requireNonNull(previousOutcome, "previousOutcome");
        }
    }

    /** The attempt's W123 outcome and the acknowledgement it leaves: the candidate, the previous one, or none. */
    record Result(CheckedJournalCheckpoint.Outcome outcome, CheckedJournalCheckpoint.Acknowledgement acknowledgement) { }

    /** Today's {@code save(CompoundTag)} layout over the captured entries, sorted here, on the IO thread. */
    static CompoundTag encode(List<RocketPersistedTransaction> entries, CompoundTag target) {
        SavedDataSchemaMigrator.stampCurrent(TYPE, target);
        ListTag transactions = new ListTag();
        entries.stream().sorted(Comparator.comparing(entry -> entry.record().transactionId()))
                .forEach(entry -> transactions.add(RocketTransactionCodec.encodeEntry(entry)));
        target.put("transactions", transactions);
        return target;
    }

    /** IO thread: encode, digest, stage, read back, replace and force; touches nothing but the capture. */
    static CheckedRootWriter.Attempt<Result> write(Capture capture, CheckedSavedDataPolicy policy, Path file,
                                                   CheckedSavedDataPolicy.DirectoryForce force) {
        if (capture.cut() != null) { capture.cut().run(); }
        CompoundTag payload = encode(capture.entries(), new CompoundTag());
        Map<UUID, String> digests = new LinkedHashMap<>();
        for (Tag raw : payload.getList("transactions", Tag.TAG_COMPOUND)) {
            CompoundTag record = (CompoundTag) raw;
            digests.put(record.getUUID("transaction_id"), CanonicalNbtDigest.of(record,
                    Math.toIntExact(TYPE.maxUncompressedBytes()), DIGEST_DEPTH));
        }
        var attempt = new CheckedJournalCheckpoint(force, DIGEST_DEPTH, capture.previous());
        var outcome = attempt.write(file, policy, capture.sequence(), payload, digests);
        var status = switch (outcome) {
            case COMMITTED -> CheckedRootWriter.Status.COMMITTED;
            case UNKNOWN -> CheckedRootWriter.Status.UNKNOWN;
            default -> CheckedRootWriter.Status.FAILED;
        };
        return new CheckedRootWriter.Attempt<>(status, new Result(outcome, attempt.acknowledged().orElse(null)));
    }
}
