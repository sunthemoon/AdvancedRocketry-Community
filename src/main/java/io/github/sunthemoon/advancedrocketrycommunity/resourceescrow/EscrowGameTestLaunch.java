package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;

/** GameTest launch scaffolding only: an identity-matched logged F1 supersession may be requested again. */
final class EscrowGameTestLaunch extends AbstractAppender implements AutoCloseable {
    @FunctionalInterface interface Request {
        boolean begin(Consumer<RocketTransferRecord> accepted, Runnable discarded);
    }
    private final GameTestHelper helper;
    private final UUID ledger, logical, transfer;
    private final Request request;
    private final Consumer<RocketTransferRecord> accepted;
    private final Runnable discarded;
    private final Logger logger = (Logger) LogManager.getLogger(EscrowSuccessorRetry.class);
    private String hold;
    private int attempts, superseded;
    private boolean retry;
    private long discardedAt = -1;

    EscrowGameTestLaunch(GameTestHelper helper, UUID ledger, UUID logical, UUID transfer, Request request,
                        Consumer<RocketTransferRecord> accepted, Runnable discarded) {
        super("escrow-launch-" + logical, null, null, false, Property.EMPTY_ARRAY);
        this.helper = helper; this.ledger = ledger; this.logical = logical; this.transfer = transfer;
        this.request = request; this.accepted = accepted; this.discarded = discarded;
        // Close the observer even on a GameTest timeout; it never outlives its fixture.
        try {
            var field = java.util.Arrays.stream(GameTestHelper.class.getDeclaredFields())
                    .filter(candidate -> candidate.getType() == GameTestInfo.class).findFirst().orElseThrow();
            field.setAccessible(true);
            ((GameTestInfo) field.get(helper)).addListener(new GameTestListener() {
                @Override public void testStructureLoaded(GameTestInfo info) { }
                @Override public void testPassed(GameTestInfo info) { close(); }
                @Override public void testFailed(GameTestInfo info) { close(); }
            });
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException("Cannot own launch observer", failure); }
    }

    void begin() {
        helper.assertTrue(attempts < 3, "Governed fixture exceeded three F1 attempts");
        attempts++; hold = null; retry = false; start(); logger.addAppender(this);
        boolean started;
        try {
            started = request.begin(record -> { close(); accepted.accept(record); }, () -> {
                close();
                if (!retryable(hold, ledger, logical, transfer, attempts)) { discarded.run(); return; }
                superseded++; discardedAt = helper.getLevel().getServer().getTickCount(); retry = true;
            });
        } catch (RuntimeException | Error failure) { close(); throw failure; }
        if (!started) { close(); }
        helper.assertTrue(started, "Governed fixture F1 preparation was refused");
    }

    void tick() {
        if (!retry) { return; }
        var server = helper.getLevel().getServer();
        if (!EscrowRuntime.of(server).flightCheckpoint().retryReady(logical, server.getTickCount())) { return; }
        helper.assertTrue(server.getTickCount() - discardedAt >= EscrowReason.backoff(superseded),
                "F1 retry preceded the unchanged product backoff");
        io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity.LOGGER.info(
                "ARCE_GAMETEST_F1_RETRY logical={} transfer={} attempt={} waited_ticks={} reason=SUPERSEDING_SAVE",
                logical, transfer, attempts + 1, server.getTickCount() - discardedAt);
        begin();
    }

    int attempts() { return attempts; }
    int superseded() { return superseded; }
    boolean retryPending() { return retry; }

    @Override public void append(LogEvent event) {
        String message = event.getMessage().getFormattedMessage();
        if (identity(message, ledger, logical, transfer)) { hold = message; }
    }

    static boolean retryable(String message, UUID ledger, UUID logical, UUID transfer, int attempts) {
        return attempts >= 1 && attempts < 3 && identity(message, ledger, logical, transfer)
                && message.endsWith(" serial=0 phase=F1_PROOF reason=SUPERSEDING_SAVE backoff=" + EscrowReason.backoff(attempts));
    }

    private static boolean identity(String message, UUID ledger, UUID logical, UUID transfer) {
        return message != null && message.startsWith("ARCE_ESCROW_SUCCESSOR_HOLD ledger=" + ledger
                + " logical=" + logical + " transfer=" + transfer + " ");
    }

    @Override public void close() { logger.removeAppender(this); stop(); }
}
