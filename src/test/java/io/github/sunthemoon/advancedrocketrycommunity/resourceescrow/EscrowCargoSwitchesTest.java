package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EscrowCargoSwitchesTest {
    @Test void everyCargoKindNeedsItsOwnNamedDeviceAndGateOnTheOwnerThread() {
        var switches = new EscrowCargoSwitches(); var device = UUID.randomUUID();
        for (var selected : EscrowOperation.Kind.values()) {
            assertFalse(EscrowCargoSwitches.production(selected));
            if (!selected.cargo()) { continue; }
            switches.set(selected, device, true);
            for (var kind : EscrowOperation.Kind.values()) {
                for (int mask = 0; mask < 4; mask++) {
                    boolean owner = (mask & 1) != 0, gate = (mask & 2) != 0;
                    assertEquals(kind == selected && owner && gate,
                            EscrowCargoSwitches.open(kind, owner, () -> gate, () -> switches.allowed(kind, device)));
                }
            }
            assertFalse(EscrowCargoSwitches.open(selected, false, () -> { fail(); return true; }, () -> { fail(); return true; }));
            assertFalse(EscrowCargoSwitches.open(selected, true, () -> false, () -> { fail(); return true; }));
            switches.clear(); assertFalse(switches.allowed(selected, device));
        }
        assertFalse(EscrowCoordinator.PRODUCTION_ADMISSION);
    }
    @Test void testRegistrationsAreBoundedAndCanBeRevoked() {
        var switches = new EscrowCargoSwitches(); var kind = EscrowOperation.Kind.FLUID_CARGO_LOAD;
        for (int i = 0; i < EscrowLimits.OPERATIONS; i++) { switches.set(kind, new UUID(0, i), true); }
        assertThrows(IllegalArgumentException.class, () -> switches.set(kind, new UUID(1, 1), true));
        switches.set(kind, new UUID(0, 0), false); switches.set(kind, new UUID(1, 1), true);
        assertFalse(switches.allowed(EscrowOperation.Kind.FLUID_CARGO_UNLOAD, new UUID(1, 1)));
        assertThrows(IllegalArgumentException.class, () -> switches.set(EscrowOperation.Kind.FLUID_PROPULSION_FILL, UUID.randomUUID(), true));
    }
}
