package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketFluidCargoBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketFluidCargoRoot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.FluidFuelLoaderBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.UnstartedServer;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EscrowReconciliationSourceTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void cargoInBothDirectionsNeverConsultsFuelRelocationAndKeepsItsObservation() {
        for (boolean load : List.of(true, false)) {
            var op = EscrowFluidCargoEndpointTest.operation(load);
            var cargo = UnstartedServer.allocate(RocketFluidCargoBlockEntity.class);
            var root = EscrowFluidCargoEndpointTest.root(load, load ? 5_000 : 0);
            UnstartedServer.set(RocketFluidCargoBlockEntity.class, cargo, "root", root);
            UnstartedServer.set(RocketFluidCargoBlockEntity.class, cargo, "status", RocketFluidCargoRoot.Status.VALID);
            var calls = new AtomicInteger();
            Runnable collidingFuel = () -> {
                calls.incrementAndGet();
                throw new EscrowReconciliationView.Refusal(EscrowReason.RELOCATED_ENDPOINT);
            };
            assertDoesNotThrow(() -> EscrowReconciliationView.requireSource(op.device(), cargo, collidingFuel));
            assertEquals(0, calls.get(), "Cargo must never consult the fuel-loader index");
            var observed = EscrowFluidCargoEndpoint.observe(cargo, op.device(), EscrowFluidCargoEndpointTest.LEDGER);
            assertEquals(root.signature(), observed.state().signature());
            assertEquals(root.tank().amount(), observed.quantity()); assertFalse(observed.missing());
            assertEquals(EscrowReason.MISSING_ENDPOINT, assertThrows(EscrowReconciliationView.Refusal.class,
                    () -> EscrowReconciliationView.requireSource(op.device(), null, collidingFuel)).reason);
            assertEquals(0, calls.get(), "Even absent cargo cannot use the fuel index");
        }
    }

    @Test void missingFuelStillDiagnosesRelocationAndNeverAdoptsTheHolder() {
        var endpoint = EscrowFillSelfGuardTest.fill(1, 0).source(); var calls = new AtomicInteger();
        for (boolean relocated : List.of(false, true)) {
            var failure = assertThrows(EscrowReconciliationView.Refusal.class,
                    () -> EscrowReconciliationView.requireSource(endpoint, null, () -> {
                        calls.incrementAndGet();
                        var reason = EscrowLoaderRelocation.missingReason(List.of(1), endpoint, ignored -> relocated
                                ? new EscrowLoaderRelocation.Slot(endpoint.level(), new BlockPos(96, 64, 96)) : null);
                        if (reason != EscrowReason.MISSING_ENDPOINT) { throw new EscrowReconciliationView.Refusal(reason); }
                    }));
            assertEquals(relocated ? EscrowReason.RELOCATED_ENDPOINT : EscrowReason.MISSING_ENDPOINT, failure.reason);
        }
        assertEquals(2, calls.get());
        var present = UnstartedServer.allocate(FluidFuelLoaderBlockEntity.class);
        assertDoesNotThrow(() -> EscrowReconciliationView.requireSource(endpoint, present, () -> fail("Present fuel endpoint")));
    }
}
