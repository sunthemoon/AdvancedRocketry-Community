package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import java.lang.reflect.Field;
import java.util.Map;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;

/** A1 diagnostic over the installed adapter, without adding a production mutation hook. */
final class EscrowTerminalRetryCut {
    private Object invalidatedCapture;
    private long previousDue;
    private int injected;
    private int observedFailures;

    static void assertInvalidated(GameTestHelper helper, MinecraftServer server, RocketTransferRecord record) {
        var captures = (Map<?, ?>) field(EscrowRuntime.of(server).terminal(), "work");
        var source = (EscrowFlightSource) field(captures.get(record.transferId()), "source");
        helper.assertTrue(!source.unchanged.get(), "Departure did not invalidate the F6/F7 lease synchronously");
    }

    static void assertWriteFailure(GameTestHelper helper, MinecraftServer server, RocketTransferRecord record) {
        EscrowTestEndpoints.requireEnabled(server);
        var histories = (EscrowTerminalRetries) field(EscrowRuntime.of(server).terminal(), "retries");
        var history = histories.history(record.logicalRocketId());
        helper.assertTrue(history.failures() == 1 && history.nextTick() == server.getTickCount() + 100,
                "Definite F7 callback lost the first retry backoff");
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
