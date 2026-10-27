package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;

/** A1 diagnostic over the installed adapter, without adding a production mutation hook. */
final class EscrowTerminalRetryCut {
    private Object invalidatedCapture;
    private long previousDue;
    private int injected;
    private int observedFailures;

    static void assertNotStarted(GameTestHelper helper, MinecraftServer server, RocketTransferRecord record) {
        var captures = (Map<?, ?>) field(EscrowRuntime.of(server).terminal(), "work");
        helper.assertTrue(!captures.containsKey(record.transferId()), "Relogin fixture dismount raced an already-started F6 capture");
    }

    static void assertInvalidated(GameTestHelper helper, MinecraftServer server, RocketTransferRecord record) {
        var captures = (Map<?, ?>) field(EscrowRuntime.of(server).terminal(), "work");
        var source = (EscrowFlightSource) field(captures.get(record.transferId()), "source");
        helper.assertTrue(!source.unchanged.get(), "Departure did not invalidate the F6/F7 lease synchronously");
    }

    static WriteFailure observeWriteFailure(MinecraftServer server, RocketTransferRecord record) {
        EscrowTestEndpoints.requireEnabled(server);
        var histories = (EscrowTerminalRetries) field(EscrowRuntime.of(server).terminal(), "retries");
        return new WriteFailure(histories.history(record.logicalRocketId()), server::getTickCount);
    }

    static void assertWriteFailure(GameTestHelper helper, MinecraftServer server, RocketTransferRecord record,
                                   WriteFailure failure) {
        EscrowTestEndpoints.requireEnabled(server);
        var histories = (EscrowTerminalRetries) field(EscrowRuntime.of(server).terminal(), "retries");
        var history = histories.history(record.logicalRocketId());
        helper.assertTrue(failure.matches(history),
                "Definite F7 callback lost the first retry backoff: failures=" + history.failures()
                        + ", next=" + history.nextTick() + ", recorded=" + failure.tick
                        + ", observed=" + server.getTickCount() + ", callbacks=" + failure.callbacks);
    }

    /** Records the owner tick inside failed(), without replacing its history or changing its logging. */
    static final class WriteFailure implements AutoCloseable {
        private final EscrowSuccessorRetry history;
        private final Field logger;
        private final Consumer<EscrowSuccessorRetry.Hold> original, observer;
        private long tick = -1;
        private int callbacks;

        @SuppressWarnings("unchecked")
        WriteFailure(EscrowSuccessorRetry history, LongSupplier clock) {
            this.history = history;
            try {
                logger = EscrowSuccessorRetry.class.getDeclaredField("logger"); logger.setAccessible(true);
                original = (Consumer<EscrowSuccessorRetry.Hold>) logger.get(history);
                observer = hold -> {
                    if (hold.phase().equals("F7_WRITE") && hold.reason().equals("LEDGER_WRITE_FAILED")) {
                        tick = clock.getAsLong(); callbacks++;
                    }
                    original.accept(hold);
                };
                logger.set(history, observer);
            } catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
        }

        boolean matches(EscrowSuccessorRetry observed) {
            return observed == history && callbacks == 1 && tick >= 0 && history.failures() == 1
                    && history.nextTick() == tick + EscrowReason.backoff(1);
        }

        @Override public void close() {
            try { if (logger.get(history) == observer) { logger.set(history, original); } }
            catch (IllegalAccessException failure) { throw new IllegalStateException(failure); }
        }
    }

    boolean tick(GameTestHelper helper, MinecraftServer server, RocketTransferRecord record, RocketEntity survivor) {
        EscrowTestEndpoints.requireEnabled(server);
        var terminal = EscrowRuntime.of(server).terminal();
        var histories = (EscrowTerminalRetries) field(terminal, "retries");
        var history = histories.history(record.logicalRocketId());
        var captures = (Map<?, ?>) field(terminal, "work");
        var capture = captures.get(record.transferId());
        if (injected > observedFailures && history.failures() == injected) {
            int delay = EscrowReason.backoff(injected);
            helper.assertTrue(history.nextTick() > server.getTickCount()
                    && history.nextTick() <= server.getTickCount() + delay,
                    "F6 failure lost its retry deadline");
            helper.assertTrue(!histories.ready(record.logicalRocketId(), history.nextTick() - 1)
                    && histories.ready(record.logicalRocketId(), history.nextTick()), "F6 retry boundary changed");
            previousDue = history.nextTick();
            observedFailures = injected;
            if (injected == 3) {
                helper.assertTrue(capture == invalidatedCapture, "Failure history belongs to a different capture");
                return true;
            }
        }
        if (capture == null || capture == invalidatedCapture || !(boolean) field(capture, "pending")) { return false; }
        helper.assertTrue(history.failures() == injected, "A terminal Work replacement reset failure history");
        helper.assertTrue(injected == 0 || server.getTickCount() >= previousDue,
                "Replacement capture ran before its inherited retry deadline");
        invalidatedCapture = capture; injected++;
        terminal.invalidated(survivor);
        return false;
    }

    private static Object field(Object target, String name) {
        try {
            Field field = target.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(target);
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
    }
}
