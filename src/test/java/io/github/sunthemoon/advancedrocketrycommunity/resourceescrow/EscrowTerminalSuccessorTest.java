package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketFlightNbtCodec;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.GovernedTransferFixture;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class EscrowTerminalSuccessorTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @ParameterizedTest @EnumSource(value = EscrowFlightTables.Outcome.class, names = {"HANDED_OFF", "CANCELLED"})
    void onlyExactTerminalSuccessorReleasesResourcesAndRetainsItsFloor(EscrowFlightTables.Outcome outcome) {
        var fixture = GovernedTransferFixture.create();
        var record = outcome == EscrowFlightTables.Outcome.HANDED_OFF ? EscrowGovernedArrivalStateTest.record() : fixture.record();
        var observed = outcome == EscrowFlightTables.Outcome.HANDED_OFF ? EscrowGovernedTerminalDiskTest.landed(record)
                : EscrowGovernedTerminalDiskTest.returned(fixture);
        var root = EscrowGovernedDispatchTest.root(record); var arm = root.armedFlights().get(0);
        assertFalse(EscrowTerminalSuccessor.matches(root, record, observed));
        var wrapper = RocketEscrowSection.decode(observed.getCompound("wrapper"));
        var flight = EscrowGovernedArrivalState.flight(observed);
        var terminal = EscrowFlightAuthority.finish(root, arm, outcome, observed.getUUID("physical"), wrapper.epoch(),
                flight.fuel().resourceSignature(), EscrowInvariant.rocket(observed.getUUID("physical"), arm.logical(), arm.owner(),
                EscrowAdapterIds.ROCKET, outcome == EscrowFlightTables.Outcome.HANDED_OFF ? record.destinationSnapshot() : record.sourceSnapshot()));
        assertTrue(EscrowTerminalSuccessor.matches(terminal, record, observed));
        var changed = observed.copy(); changed.putUUID("physical", UUID.randomUUID());
        assertFalse(EscrowTerminalSuccessor.matches(terminal, record, changed));
        changed = observed.copy(); changed.putUUID("owner", UUID.randomUUID());
        assertFalse(EscrowTerminalSuccessor.matches(terminal, record, changed));
        changed = observed.copy(); changed.put("wrapper", new RocketEscrowSection(UUID.randomUUID(), wrapper.epoch(), List.of()).encode());
        assertFalse(EscrowTerminalSuccessor.matches(terminal, record, changed));
        var vector = flight.fuel().propulsion().orElseThrow();
        var fuel = io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFuelState.restorePropulsion(
                vector.updated(vector.primary().withAmount(vector.primary().amount() - 1), vector.oxidizer()), flight.fuel().committedDebits());
        changed = observed.copy(); changed.put("flight", RocketFlightNbtCodec.encode(flight.withFuel(fuel, 700)));
        assertFalse(EscrowTerminalSuccessor.matches(terminal, record, changed), "Same-epoch resource drift must remain refused");
        changed.put("wrapper", wrapper.afterNativeResourceMutation().encode());
        assertTrue(EscrowTerminalSuccessor.matches(terminal, record, changed));
        assertEquals(1, terminal.armedFlights().size(), "Resource release must not prune the charged terminal entry");
        // R13 accepts unobserved physics after capture: resource release uses the proved identity/frontier,
        // not a new off-thread entity read or a requirement that the successor never moves again.
        changed.putBoolean("on_ground", !changed.getBoolean("on_ground"));
        assertTrue(EscrowTerminalSuccessor.matches(terminal, record, changed));
    }

    @Test void terminalWorkRetainsTheExactCapacityBound() {
        var work = new HashMap<UUID, Object>();
        for (int i = 0; i < 64; i++) {
            var id = UUID.randomUUID(); EscrowGovernedTerminal.requireCapacity(work, id); work.put(id, new Object());
        }
        EscrowGovernedTerminal.requireCapacity(work, work.keySet().iterator().next());
        assertThrows(IllegalArgumentException.class, () -> EscrowGovernedTerminal.requireCapacity(work, UUID.randomUUID()));
    }
}
