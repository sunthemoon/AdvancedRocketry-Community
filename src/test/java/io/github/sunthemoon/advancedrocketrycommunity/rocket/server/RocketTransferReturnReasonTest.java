package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

class RocketTransferReturnReasonTest {
    private static final UUID TRANSFER = new UUID(1, 2);
    private static final UUID ROCKET = new UUID(3, 4);
    private static final String MESSAGE =
            "ARCE_TRANSFER_RETURNED_TO_SOURCE transfer={} logical={} reason={} fuel={}";

    @Test
    void requestedCancellationIsInformationalAndKeepsTheDiagnosticPayload() {
        List<Event> events = new ArrayList<>();
        RocketTransferReturnReason.COUNTDOWN_CANCELLED.log(logger(events), TRANSFER, ROCKET, 670L);
        assertEquals(List.of(new Event("info", MESSAGE,
                List.of(TRANSFER, ROCKET, "countdown_cancelled", 670L))), events);
    }

    @Test
    void everyTransferFaultStillEmitsExactlyOneWarningWithItsStableReason() {
        var reasons = Map.of(
                RocketTransferReturnReason.DESTINATION_PAD_BLOCKED, "destination_pad_blocked",
                RocketTransferReturnReason.DESTINATION_ENTITY_CREATE_FAILED, "destination_entity_create_failed",
                RocketTransferReturnReason.DESTINATION_ENTITY_SPAWN_FAILED, "destination_entity_spawn_failed");
        assertEquals(RocketTransferReturnReason.values().length - 1, reasons.size());
        for (var reason : reasons.entrySet()) {
            List<Event> events = new ArrayList<>();
            reason.getKey().log(logger(events), TRANSFER, ROCKET, 1_000L);
            assertEquals(List.of(new Event("warn", MESSAGE,
                    List.of(TRANSFER, ROCKET, reason.getValue(), 1_000L))), events);
        }
    }

    private static Logger logger(List<Event> events) {
        return (Logger) Proxy.newProxyInstance(Logger.class.getClassLoader(), new Class<?>[]{Logger.class},
                (proxy, method, arguments) -> {
                    events.add(new Event(method.getName(), (String) arguments[0],
                            List.copyOf(Arrays.asList((Object[]) arguments[1]))));
                    return null;
                });
    }

    private record Event(String method, String message, List<Object> arguments) {
    }
}
