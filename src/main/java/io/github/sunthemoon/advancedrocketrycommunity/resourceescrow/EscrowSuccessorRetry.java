package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import java.util.UUID;
import java.util.function.Consumer;

/** Owner-thread scheduling only. A due retry never supplies action or storage authority. */
final class EscrowSuccessorRetry {
    record Hold(UUID ledger, UUID transfer, long serial, String phase, String reason, int backoff) { }
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private final Consumer<Hold> logger;
    private int failures;
    private long next;
    private Hold logged;

    EscrowSuccessorRetry() {
        this(hold -> LOGGER.info("ARCE_ESCROW_SUCCESSOR_HOLD ledger={} transfer={} serial={} phase={} reason={} backoff={}",
                hold.ledger(), hold.transfer(), hold.serial(), hold.phase(), hold.reason(), hold.backoff()));
    }
    EscrowSuccessorRetry(Consumer<Hold> logger) { this.logger = logger; }
    boolean ready(long tick) { return tick >= next; }
    long nextTick() { return next; }
    int failures() { return failures; }

    void failed(UUID ledger, UUID transfer, long serial, String phase, String reason, long tick) {
        failures = Math.min(3, failures + 1);
        int delay = EscrowReason.backoff(failures);
        next = Math.addExact(tick, delay);
        var transition = new Hold(ledger, transfer, serial, phase, reason, delay);
        if (!transition.equals(logged)) { logger.accept(transition); logged = transition; }
    }
}
