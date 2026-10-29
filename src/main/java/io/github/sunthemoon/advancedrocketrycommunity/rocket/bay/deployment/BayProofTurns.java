package io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.deployment;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;

/**
 * Root decision R154: at most one bay claim at a time runs D6's census captures and holds their proofs through D7. A
 * claim ready to capture asks for the turn; while another claim holds it, the asker waits in line, in the order the
 * claims first asked, and keeps its place while it keeps asking. The holder keeps the turn until it passes it on, and a
 * claim that asks again afterwards queues behind every claim already waiting. RAM only, rebuilt every session; pure Java,
 * owner-thread only.
 */
public final class BayProofTurns {
    @Nullable private UUID holder;
    private final LinkedHashSet<UUID> line = new LinkedHashSet<>();

    /** Whether L holds the turn, taking it when it is free and L is first in line; otherwise L waits in line. */
    public boolean take(UUID logical) {
        Objects.requireNonNull(logical, "logical");
        if (logical.equals(holder)) { return true; }
        line.add(logical);
        if (holder != null || !line.iterator().next().equals(logical)) { return false; }
        line.remove(logical);
        holder = logical;
        return true;
    }

    /** L stops asking: it leaves the line, or passes the turn on to the first claim waiting. */
    public void leave(UUID logical) {
        line.remove(logical);
        if (logical.equals(holder)) { holder = null; }
    }

    public boolean holds(UUID logical) { return logical.equals(holder); }
    public Optional<UUID> holder() { return Optional.ofNullable(holder); }
    /** The claims waiting for the turn, first in line first. */
    public List<UUID> line() { return List.copyOf(line); }
    public void clear() { holder = null; line.clear(); }
}
