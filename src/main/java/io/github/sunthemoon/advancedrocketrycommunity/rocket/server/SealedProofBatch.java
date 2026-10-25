package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowBarrier;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowReason;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/** One fixed full-slot proof plan, sharing ordinary attempts/captures and retaining no authority after timeout. */
final class SealedProofBatch implements AutoCloseable {
    enum Readiness { READY, UNLOADED, SAVE_OFF, CONFLICT }
    interface Port {
        long now();
        Readiness readiness();
        boolean attemptRoom();
        boolean available(int slot);
        boolean capture(int slot, Consumer<EscrowBarrier.Result> callback);
        void held(String reason);
    }
    private final Thread owner = Thread.currentThread();
    private final int slots;
    private final Port port;
    private final Runnable attemptEnded;
    private final Map<Integer, EscrowBarrier.Proof> proofs = new HashMap<>();
    private long started, notBefore = Long.MIN_VALUE, lastTick = Long.MIN_VALUE;
    private int cursor, attempt;
    private boolean active, closed;

    SealedProofBatch(int slots, Port port) {
        this(slots, port, () -> { });
    }
    SealedProofBatch(int slots, Port port, Runnable attemptEnded) {
        if (slots < 1 || slots > RocketOrdinaryRetirement.MAX_SLOTS) { throw new IllegalArgumentException("Sealed proof census exceeds ordinary bound"); }
        this.slots = slots; this.port = Objects.requireNonNull(port);
        this.attemptEnded = Objects.requireNonNull(attemptEnded);
    }
    boolean active() { owner(); return active; }
    long retryAt() { owner(); return notBefore; }
    void deferUntil(long tick) {
        owner();
        if (closed || active) { throw new IllegalStateException("Only an unstarted proof plan may inherit retry backoff"); }
        notBefore = Math.max(notBefore, tick);
    }
    String diagnostic() {
        owner(); return "active=" + active + ",cursor=" + cursor + ",proofs=" + proofs.size() + "/" + slots
                + ",age=" + (active ? port.now() - started : 0) + ",wait=" + (notBefore > port.now() ? notBefore - port.now() : 0);
    }
    boolean current() {
        owner();
        return !closed && active && port.now() - started <= RocketOrdinaryRetirement.ATTEMPT_TICKS
                && port.readiness() == Readiness.READY && !closed && proofs.size() == slots
                && proofs.values().stream().allMatch(EscrowBarrier.Proof::valid);
    }
    /** Non-selected recovery may release an expired lease, but must not inspect or capture another slot. */
    void tickDeadline() {
        owner();
        if (!closed && active && port.now() - started > RocketOrdinaryRetirement.ATTEMPT_TICKS) {
            fail("DEADLINE", RocketOrdinaryRetirement.RETRY_TICKS);
        }
    }
    void tick() {
        owner();
        long now = port.now();
        if (closed || now == lastTick || now < notBefore) { return; }
        lastTick = now;
        try {
            if (active && now - started > RocketOrdinaryRetirement.ATTEMPT_TICKS) {
                fail("DEADLINE", RocketOrdinaryRetirement.RETRY_TICKS); return;
            }
            Readiness readiness = port.readiness();
            if (closed) { return; }
            if (readiness != Readiness.READY) {
                fail(readiness.name(), readiness == Readiness.CONFLICT
                        ? RocketOrdinaryRetirement.RETRY_TICKS : RocketOrdinaryRetirement.POLL_TICKS); return;
            }
            if (proofs.values().stream().anyMatch(proof -> !proof.valid())) { fail("SUPERSEDING_SAVE", 1); return; }
            if (!active) {
                // No attempt is occupied while the first native capture cannot start.
                if (!port.attemptRoom() || !port.available(0)) { fail("BUSY", 1); return; }
                active = true; started = now; cursor = 0; attempt++;
            }
            if (cursor == slots) { return; }
            if (!port.available(cursor)) {
                if (cursor == 0) { fail("BUSY", 1); }
                return;
            }
            int slot = cursor, generation = attempt;
            if (port.capture(slot, result -> completed(generation, slot, result))) {
                if (active && attempt == generation) { cursor++; }
            } else if (cursor == 0) { fail("BUSY", 1); }
        } catch (RuntimeException unavailable) {
            fail("UNAVAILABLE", RocketOrdinaryRetirement.RETRY_TICKS);
        } catch (Error fatal) {
            release(); throw fatal;
        }
    }
    private void completed(int generation, int slot, EscrowBarrier.Result result) {
        if (Thread.currentThread() != owner) {
            if (result.proof() != null) { result.proof().release(); }
            throw new IllegalStateException("Sealed proof completion requires its owner thread");
        }
        if (closed || !active || attempt != generation || proofs.containsKey(slot)) {
            if (result.proof() != null && result.proof() != proofs.get(slot)) { result.proof().release(); }
            return;
        }
        if (result.confirmed()) { proofs.put(slot, result.proof()); return; }
        if (result.proof() != null) { result.proof().release(); }
        boolean superseded = result.reason() == EscrowReason.SUPERSEDING_SAVE || result.reason() == EscrowReason.NONE;
        fail(superseded ? "SUPERSEDING_SAVE" : result.reason().name(), superseded ? 1 : RocketOrdinaryRetirement.RETRY_TICKS);
    }
    private void fail(String reason, int delay) {
        release(); notBefore = port.now() + delay; port.held(reason);
    }
    private void release() {
        boolean ended = active;
        proofs.values().forEach(EscrowBarrier.Proof::release); proofs.clear();
        active = false; cursor = 0; attempt++;
        if (ended) { attemptEnded.run(); }
    }
    @Override public void close() { owner(); closed = true; release(); }
    private void owner() {
        if (Thread.currentThread() != owner) { throw new IllegalStateException("Sealed proof batch requires its owner thread"); }
    }
}
