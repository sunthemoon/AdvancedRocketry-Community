package io.github.sunthemoon.advancedrocketrycommunity.rocket.rejoin;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class RocketRejoinLeaseTest {
    @Test void unsafeRefreshRevokesPermanentlyInsteadOfRevivingOldProof() {
        var lease = new RocketRejoinLease(); lease.refresh(true); assertTrue(lease.getAsBoolean());
        lease.refresh(false); lease.refresh(true); assertFalse(lease.getAsBoolean());
    }
    @Test void onlyOwnerRevokesAndIoOnlyReads() throws Exception {
        var lease = new RocketRejoinLease();
        var io = new Thread(() -> {
            assertTrue(lease.getAsBoolean());
            assertThrows(IllegalStateException.class, lease::revoke);
        });
        var failure = new java.util.concurrent.atomic.AtomicReference<Throwable>();
        io.setUncaughtExceptionHandler((thread, error) -> failure.set(error));
        io.start(); io.join(1000); assertFalse(io.isAlive()); assertNull(failure.get());
        lease.revoke(); lease.revoke(); assertFalse(lease.getAsBoolean());
    }
}
