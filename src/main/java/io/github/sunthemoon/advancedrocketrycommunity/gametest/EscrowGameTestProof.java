package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowReason;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Predicate;

/**
 * Test-only request driver for a fixed frontier. Only an explicit SUPERSEDING_SAVE may request another proof,
 * after the product backoff, with at most three accepted attempts. All other results reach the original assertions.
 * The owning GameTest still supplies its unchanged timeout and ticks its barrier; this registers no callbacks with
 * the GameTest scheduler and does not change, release or substitute a returned proof.
 */
public final class EscrowGameTestProof<R> {
    @FunctionalInterface public interface Request<R> {
        boolean begin(long tick, Consumer<R> completed);
    }

    private final LongSupplier clock;
    private final Request<R> request;
    private final Predicate<R> confirmed;
    private final Function<R, EscrowReason> reason;
    private final Consumer<R> completed;
    private int attempts;
    private boolean pending;
    private boolean done;
    private long retryAt = Long.MIN_VALUE;

    public EscrowGameTestProof(LongSupplier clock, Request<R> request, Predicate<R> confirmed,
                              Function<R, EscrowReason> reason, Consumer<R> completed) {
        this.clock = clock; this.request = request; this.confirmed = confirmed;
        this.reason = reason; this.completed = completed;
    }

    public void tick() {
        long now = clock.getAsLong();
        if (done || pending || now < retryAt) { return; }
        // begin may invoke its callback synchronously on a capture refusal. Set pending before, never after it.
        pending = true; attempts++;
        if (!request.begin(now, this::complete)) {
            pending = false; attempts--; // No capture was admitted by the unchanged barrier budget.
        }
    }

    private void complete(R result) {
        pending = false;
        if (!confirmed.test(result) && reason.apply(result) == EscrowReason.SUPERSEDING_SAVE && attempts < 3) {
            retryAt = clock.getAsLong() + EscrowReason.backoff(attempts);
            return;
        }
        done = true;
        completed.accept(result);
    }

    public int attempts() { return attempts; }
}
