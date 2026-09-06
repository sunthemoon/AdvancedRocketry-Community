package io.github.sunthemoon.advancedrocketrycommunity.rocket.flight;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RocketTransferAuthorityOrderTest {
    private static final UUID OLDER = new UUID(Long.MIN_VALUE, 0);
    private static final UUID BOUND = new UUID(Long.MAX_VALUE, 0);

    @Test
    void journalBindingWinsRegardlessOfSignedUuidOrderOrInputOrder() {
        for (var candidates : List.of(List.of(OLDER, BOUND), List.of(BOUND, OLDER))) {
            assertEquals(List.of(BOUND, OLDER),
                    candidates.stream().sorted(RocketTransferAuthorityOrder.preferred(BOUND)).toList());
        }
    }

    @Test
    void missingOrUnboundExpectedIdentityKeepsDeterministicFallback() {
        var candidates = List.of(BOUND, OLDER);
        assertEquals(List.of(OLDER, BOUND),
                candidates.stream().sorted(RocketTransferAuthorityOrder.preferred(null)).toList());
        assertEquals(List.of(OLDER, BOUND), candidates.stream()
                .sorted(RocketTransferAuthorityOrder.preferred(new UUID(0, 0))).toList());
        assertEquals(List.of(BOUND, OLDER), candidates);
    }
}
