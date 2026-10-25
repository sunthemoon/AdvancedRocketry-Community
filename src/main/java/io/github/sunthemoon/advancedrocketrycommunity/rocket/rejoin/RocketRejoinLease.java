package io.github.sunthemoon.advancedrocketrycommunity.rocket.rejoin;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

/** Owner-thread revocation, IO-thread read only. A revoked attempt can never be revived. */
final class RocketRejoinLease implements BooleanSupplier {
    private final Thread owner = Thread.currentThread();
    private final AtomicBoolean valid = new AtomicBoolean(true);
    void refresh(boolean safe) { if (!safe) { revoke(); } }
    void revoke() {
        if (Thread.currentThread() != owner) { throw new IllegalStateException("Rejoin lease requires its owner thread"); }
        valid.set(false);
    }
    @Override public boolean getAsBoolean() { return valid.get(); }
}
