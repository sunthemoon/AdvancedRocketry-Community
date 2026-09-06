package io.github.sunthemoon.advancedrocketrycommunity.rocket.testing;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.RocketLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketTransactionPhase;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketTransactionRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketTransactionType;
import java.util.Objects;

/** Bounded selection of an actually emitted transaction record, for opt-in diagnostics. */
public record RocketTransactionCheckpoint(
        RocketTransactionType type, RocketTransactionPhase phase, int progress
) {
    public RocketTransactionCheckpoint {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(phase, "phase");
        if (progress < 0 || progress > RocketLimits.MAX_BLOCKS
                || (type == RocketTransactionType.ASSEMBLY
                    && (phase == RocketTransactionPhase.RESTORING || phase == RocketTransactionPhase.RESTORED))
                || (type == RocketTransactionType.DISASSEMBLY
                    && (phase == RocketTransactionPhase.EXTRACTING || phase == RocketTransactionPhase.EXTRACTED
                        || phase == RocketTransactionPhase.SPAWNED))) {
            throw new IllegalArgumentException("Invalid transaction checkpoint");
        }
        int fixedProgress = switch (phase) {
            case SNAPSHOT_VALIDATED, LOCKED -> 0;
            case ROLLING_BACK, ROLLED_BACK -> 2;
            case FAILED -> 1;
            default -> -1;
        };
        if (fixedProgress >= 0 ? progress != fixedProgress : progress == 0) {
            throw new IllegalArgumentException("Checkpoint progress does not match its phase");
        }
    }

    public static RocketTransactionCheckpoint parse(String text) {
        if (text == null || text.length() > 96) {
            throw new IllegalArgumentException("Checkpoint must be a bounded TYPE:PHASE:PROGRESS value");
        }
        String[] fields = text.split(":", -1);
        if (fields.length != 3 || !fields[2].matches("0|[1-9][0-9]{0,3}")) {
            throw new IllegalArgumentException("Invalid checkpoint fields");
        }
        return new RocketTransactionCheckpoint(RocketTransactionType.valueOf(fields[0]),
                RocketTransactionPhase.valueOf(fields[1]), Integer.parseInt(fields[2]));
    }

    public void validateBlockCount(int count) {
        boolean entireStructure = switch (phase) {
            case EXTRACTED, SPAWNED, RESTORED, COMMITTED -> true;
            default -> false;
        };
        if (progress > count || count < 2 || (entireStructure && progress != count)) {
            throw new IllegalArgumentException("Checkpoint progress cannot occur for this structure");
        }
    }

    public boolean reached(RocketTransactionRecord record) {
        return record.type() == type && record.phase() == phase && record.progress() == progress;
    }

    public boolean requiresRollback() {
        return phase == RocketTransactionPhase.ROLLING_BACK || phase == RocketTransactionPhase.ROLLED_BACK
                || phase == RocketTransactionPhase.FAILED;
    }
}
