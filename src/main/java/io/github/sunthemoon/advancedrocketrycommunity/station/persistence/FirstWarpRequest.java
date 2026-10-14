package io.github.sunthemoon.advancedrocketrycommunity.station.persistence;

import io.github.sunthemoon.advancedrocketrycommunity.progression.classic.firstevent.FirstEventDisposition;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * W44 section 1.2: what the warp commit asks of the checked relocation about the first-warp outbox. It carries the
 * participants captured in the commit tick, the confirmed actor and the disposition refreshed before the call; the
 * checked entry rechecks an ALLOW on the same server thread before it builds a candidate. {@link #NONE} never elects:
 * the four-argument relocation used by GameTest fixtures passes it.
 */
public record FirstWarpRequest(UUID actor, List<UUID> participants, FirstEventDisposition disposition,
                               Supplier<FirstEventDisposition> recheck) {
    public static final FirstWarpRequest NONE = new FirstWarpRequest(null, List.of(), FirstEventDisposition.NOT_FIRST,
            () -> FirstEventDisposition.NOT_FIRST);

    public FirstWarpRequest {
        participants = List.copyOf(participants);
        Objects.requireNonNull(disposition, "disposition");
        Objects.requireNonNull(recheck, "recheck");
        if (disposition == FirstEventDisposition.ALLOW) {
            Objects.requireNonNull(actor, "actor");
        }
    }

    /** The disposition at candidate construction: an ALLOW is refreshed, anything else stands. */
    FirstEventDisposition current() {
        if (this == NONE) {
            return FirstEventDisposition.NOT_FIRST;
        }
        return disposition == FirstEventDisposition.ALLOW ? Objects.requireNonNull(recheck.get()) : disposition;
    }
}
