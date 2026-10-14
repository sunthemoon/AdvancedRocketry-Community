package io.github.sunthemoon.advancedrocketrycommunity.progression.classic.firstevent;

import java.util.Objects;
import java.util.Optional;

/**
 * W44 section 3.3: the pure election and hold rules. Progression never chooses a winner; it can only refuse an
 * election, or defer it while unavailable, so a rolled-back or unavailable store cannot produce a second winner.
 */
public final class FirstEventElection {
    private FirstEventElection() {
    }

    /** The permanent data holds; each needs offline repair (W44 section 7). */
    public enum Hold { SOURCE_ROLLBACK, RECEIPT_MISMATCH, RECEIPT_MISSING }

    /**
     * Step 4 of the startup and election order. {@code ram} is the source's RAM slot, never proof: an occupied slot
     * means another election already exists in this session, so no later action can win.
     */
    public static FirstEventDisposition disposition(FirstEventKind kind, FirstEventSourceView source,
                                                    FirstEventOutbox ram, ClassicProgressionSavedData progression) {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(ram, "ram");
        if (progression == null || !progression.operational()) {
            return FirstEventDisposition.DEFER;
        }
        if (!ram.isEmpty()) {
            return FirstEventDisposition.NOT_FIRST;
        }
        if (!source.known()) {
            return FirstEventDisposition.DEFER;
        }
        if (!source.outbox().isEmpty() || hold(kind, source, progression).isPresent()) {
            return FirstEventDisposition.NOT_FIRST;
        }
        return progression.validatedAbsent(kind) ? FirstEventDisposition.ALLOW : FirstEventDisposition.NOT_FIRST;
    }

    /**
     * The permanent hold of a KNOWN source against a validated receipt; empty for every other combination. A missing
     * writer confirmation or an unavailable store is never a hold.
     */
    public static Optional<Hold> hold(FirstEventKind kind, FirstEventSourceView source,
                                      ClassicProgressionSavedData progression) {
        if (!source.known() || progression == null || !progression.operational()) {
            return Optional.empty();
        }
        FirstEventOutbox outbox = source.outbox();
        Optional<FirstEvent> receipt = progression.receipt(kind);
        if (outbox.isEmpty()) {
            // Only a proven comparison establishes rollback: a known EMPTY source against an occurred receipt.
            return receipt.isPresent() ? Optional.of(Hold.SOURCE_ROLLBACK) : Optional.empty();
        }
        if (receipt.isPresent() && !receipt.orElseThrow().equals(outbox.event())) {
            return Optional.of(Hold.RECEIPT_MISMATCH);
        }
        if (receipt.isEmpty() && outbox.state() == FirstEventOutbox.State.DELIVERED) {
            return Optional.of(Hold.RECEIPT_MISSING);
        }
        return Optional.empty();
    }
}
