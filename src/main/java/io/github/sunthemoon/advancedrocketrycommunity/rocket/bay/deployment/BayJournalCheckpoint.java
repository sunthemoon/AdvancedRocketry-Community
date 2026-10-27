package io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.deployment;

import java.util.Objects;

/**
 * D0's B14 step under ADR-074 item 11 (W128's resumption): one decision per group step from the transaction journal's
 * absence answer and whether this group has already sent its own checkpoint request. Pure Java.
 */
public final class BayJournalCheckpoint {
    /** The journal's {@code absenceStatus} answers, by name. */
    public enum Answer { PROVED, WRITE_REQUIRED, ASSEMBLY_JOURNAL_PENDING, ASSEMBLY_JOURNAL_UNPROVED, WRITE_PENDING }
    /** What the group does now; REQUEST sends {@code requestCheckpoint}, whose REFUSED answer rejects every bay. */
    public enum Action { CONTINUE, REJECT_PENDING, REJECT_UNPROVED, REQUEST, WAIT }

    private BayJournalCheckpoint() { }

    /**
     * PROVED continues; a record of L rejects every bay; WRITE_PENDING waits with no time limit; the first UNPROVED or
     * WRITE_REQUIRED requests; UNPROVED after the request rejects every bay; WRITE_REQUIRED after it requests again.
     */
    public static Action decide(Answer answer, boolean requested) {
        return switch (Objects.requireNonNull(answer, "answer")) {
            case PROVED -> Action.CONTINUE;
            case ASSEMBLY_JOURNAL_PENDING -> Action.REJECT_PENDING;
            case WRITE_PENDING -> Action.WAIT;
            case ASSEMBLY_JOURNAL_UNPROVED -> requested ? Action.REJECT_UNPROVED : Action.REQUEST;
            case WRITE_REQUIRED -> Action.REQUEST;
        };
    }
}
