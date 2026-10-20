package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class EscrowCargoPermitUseTest {
    @Test void exactBeforeCanPublishOnlyOnceInItsIssuingTick() {
        var use = new EscrowCargoPublication.Use(12);
        assertDoesNotThrow(() -> use.consume(12, () -> true));
        assertThrows(IllegalArgumentException.class, () -> use.consume(12, () -> true));
    }
    @Test void changedBeforeAndStaleTickConsumeWithoutPublishing() {
        var changed = new EscrowCargoPublication.Use(12);
        assertThrows(IllegalArgumentException.class, () -> changed.consume(12, () -> false));
        assertThrows(IllegalArgumentException.class, () -> changed.consume(12, () -> true));
        var stale = new EscrowCargoPublication.Use(12);
        assertThrows(IllegalArgumentException.class, () -> stale.consume(13, () -> true));
        assertThrows(IllegalArgumentException.class, () -> stale.consume(12, () -> true));
    }
    @Test void anotherThreadCannotConsumeThePermit() {
        var use = new EscrowCargoPublication.Use(12);
        CompletableFuture.runAsync(() -> assertThrows(IllegalArgumentException.class, () -> use.consume(12, () -> true))).join();
        assertDoesNotThrow(() -> use.consume(12, () -> true));
    }
}
