package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.adapter;

import java.util.Optional;
import java.util.Objects;

/** Exclusive raw-storage lease only; no Level, native decoder or world admission. */
final class ClassicRawPermit implements AutoCloseable {
    private final ClassicOwnerState state;
    private final ClassicRawPurpose purpose;
    private final Object guard;
    private final boolean ownsGuard;
    private final Object captureLifetime;
    private Object storageEpoch;
    private boolean closed;

    private ClassicRawPermit(ClassicOwnerState state, ClassicRawPurpose purpose, Object guard, boolean ownsGuard) {
        this.state = state; this.purpose = purpose; this.guard = guard; this.storageEpoch = state.storageEpoch();
        this.ownsGuard = ownsGuard; this.captureLifetime = state.lifetime();
    }

    static Optional<ClassicRawPermit> acquire(ClassicOwnerState state, ClassicRawPurpose purpose) {
        Objects.requireNonNull(state, "state"); Objects.requireNonNull(purpose, "purpose");
        Object token = state.acquire();
        return token == null ? Optional.empty() : Optional.of(new ClassicRawPermit(state, purpose, token, true));
    }

    static ClassicRawPermit underLoad(ClassicOwnerState state, GuardTicket ticket) {
        if (ticket.purpose() != ClassicTicketPurpose.LOAD || !ticket.owns(state)) {
            throw new IllegalStateException("Not the installed owner's LOAD guard");
        }
        return new ClassicRawPermit(state, ClassicRawPurpose.CAPTURE, ticket.guardFor(state), false);
    }

    static ClassicRawPermit emission(ClassicOwnerState state) {
        synchronized (state) {
            Object token = state.acquire();
            if (token != null) { return new ClassicRawPermit(state, ClassicRawPurpose.EMIT, token, true); }
            // A busy native operation may emit its last immutable checkpoint on the server thread.
            if (!(state.owner().getLevel() instanceof net.minecraft.server.level.ServerLevel level)
                    || !level.getServer().isSameThread()) {
                throw new IllegalStateException("Busy raw emission is not a worker-thread world serializer");
            }
            return new ClassicRawPermit(state, ClassicRawPurpose.EMIT, state.currentGuard(), false);
        }
    }

    boolean storageStillCurrent() {
        return !closed && state.heldBy(guard) && state.storageEpoch() == storageEpoch
                && (purpose == ClassicRawPurpose.EMIT || state.lifetime() == captureLifetime);
    }

    void requireCurrent() {
        if (!storageStillCurrent()) { throw new IllegalStateException("Raw storage lease unavailable"); }
    }

    ClassicOwnerState state() { return state; }
    ClassicRawPurpose purpose() { return purpose; }
    void advanceStorage(Object epoch) {
        if (closed || !state.heldBy(guard) || state.storageEpoch() != epoch) {
            throw new IllegalStateException("Raw storage publication lost its lease");
        }
        storageEpoch = epoch;
    }

    @Override public void close() {
        if (!closed) { closed = true; if (ownsGuard) { state.release(guard); } }
    }
}
