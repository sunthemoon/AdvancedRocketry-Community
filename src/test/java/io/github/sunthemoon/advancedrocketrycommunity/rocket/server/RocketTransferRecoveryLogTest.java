package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferPhase;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecoveryAction;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

class RocketTransferRecoveryLogTest {
    private static final UUID TRANSFER = new UUID(1, 2);

    @Test
    void unchangedCommittedLandedAuthorityIsInformational() {
        assertEvent("info", new RocketTransferRecoveryService.Result(
                RocketTransferRecoveryService.Status.RECOVERED, TRANSFER, RocketTransferPhase.COMMITTED,
                RocketTransferRecoveryAction.KEEP_DESTINATION, 0, 1), true);
    }

    @Test
    void repairsIncompletePhasesDuplicatesAndNonLandedRecoveryRemainWarnings() {
        for (var status : RocketTransferRecoveryService.Status.values()) {
            for (var phase : RocketTransferPhase.values()) {
                for (var action : RocketTransferRecoveryAction.values()) {
                    for (int[] counts : new int[][]{{0, 0}, {0, 1}, {0, 2}, {1, 1}, {2, 1}}) {
                        var result = new RocketTransferRecoveryService.Result(
                                status, TRANSFER, phase, action, counts[0], counts[1]);
                        assertEvent("warn", result, false);
                        if (status != RocketTransferRecoveryService.Status.RECOVERED
                                || phase != RocketTransferPhase.COMMITTED
                                || action != RocketTransferRecoveryAction.KEEP_DESTINATION
                                || counts[0] != 0 || counts[1] != 1) {
                            assertEvent("warn", result, true);
                        }
                    }
                }
            }
        }
    }

    private static void assertEvent(String methodName, RocketTransferRecoveryService.Result result, boolean landed) {
        List<List<Object>> events = new ArrayList<>();
        var logger = (Logger) Proxy.newProxyInstance(Logger.class.getClassLoader(), new Class<?>[]{Logger.class},
                (proxy, method, arguments) -> {
                    events.add(List.of(method.getName(), arguments[0],
                            List.copyOf(Arrays.asList((Object[]) arguments[1]))));
                    return null;
                });
        result.log(logger, landed);
        assertEquals(List.of(List.of(methodName,
                "ARCE_TRANSFER_RECOVERY transfer={} phase={} source_count={} destination_count={} action={} status={}",
                List.of(result.transferId(), result.phase(), result.sourceCount(), result.destinationCount(),
                        result.action(), result.status()))), events);
    }
}
