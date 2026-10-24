package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class W151NativeReturnsTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void pendingWithoutAWaitReasonRemainsAnObservablePendingAttempt() {
        var row = W151NativeReturns.pendingObservation(1, null, 0, 72, "PROVING");
        assertEquals(true, row.get("pending"));
        assertEquals(1, row.get("attempts"));
        assertEquals("none", row.get("reason"));
        assertEquals(0, row.get("cursor"));
        assertEquals(72L, row.get("not_before"));
        assertEquals("PROVING", row.get("state"));
    }

    @Test void anActualWaitReasonIsPreserved() {
        assertEquals("BUSY", W151NativeReturns.pendingObservation(0, "BUSY", 1, 92, "WAITING").get("reason"));
    }

    @Test void anInvalidStateIsNotHiddenByTheObserver() {
        assertThrows(NullPointerException.class, () -> W151NativeReturns.pendingObservation(1, null, 0, 72, null));
    }
}
