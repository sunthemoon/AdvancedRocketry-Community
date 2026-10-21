package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketFluidCargoBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EscrowFluidCargoPermitTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    @Test void nativeDeviceHasOnlyPermitGatedPublication() {
        assertTrue(Arrays.stream(RocketFluidCargoBlockEntity.class.getDeclaredMethods())
                .anyMatch(method -> method.getName().equals("applyEscrowCargo") && method.getParameterCount() == 1
                        && method.getParameterTypes()[0].getSimpleName().equals("Permit")));
        assertTrue(Arrays.stream(EscrowFluidCargoPublication.Permit.class.getDeclaredConstructors())
                .allMatch(constructor -> java.lang.reflect.Modifier.isPrivate(constructor.getModifiers())));
    }

    static EscrowOperation bound(boolean load) {
        var op = EscrowFluidCargoEndpointTest.operation(load); var b = op.nativeBinding(); var end = op.rocket();
        var before = EscrowFluidCargoEndpointTest.envelope(EscrowFluidCargoEndpointTest.root(load, load ? 5_000 : 0));
        var after = EscrowFluidCargoEndpoint.change(EscrowFluidCargoEndpointTest.LEDGER, op, before);
        var binding = new EscrowNativeBinding(EscrowSignatureKind.ROCKET_COMPOSITE_V1, end.identity(), end.logicalRocket(), op.owner(),
                EscrowTestFixtures.id(900), EscrowTestFixtures.hash(901), EscrowTestFixtures.hash(902), b.sourceReference(), b.destinationReference(),
                EscrowTestFixtures.hash(903), EscrowTestFixtures.hash(904), end.beforeSignature(), end.afterSignature(),
                load ? EscrowNativeEnvelope.digest(before) : EscrowTestFixtures.hash(905),
                load ? EscrowNativeEnvelope.digest(after) : EscrowTestFixtures.hash(906),
                load ? EscrowTestFixtures.hash(905) : EscrowNativeEnvelope.digest(before),
                load ? EscrowTestFixtures.hash(906) : EscrowNativeEnvelope.digest(after), true);
        return new EscrowOperation(op.id(), op.sequence(), op.owner(), op.createdTick(), op.phase(), op.reason(), op.payload(), op.payloadHash(),
                op.source(), op.destination(), op.guard(), op.kind(), op.settlement(), binding);
    }

    @Test void eitherRolePublishesOnceAndNeverAcceptsAChangedCaptureOrLostAuthority() {
        for (boolean load : List.of(true, false)) {
            var op = bound(load); var ledger = EscrowLedger.empty(EscrowFluidCargoEndpointTest.LEDGER).append(List.of(op));
            var before = EscrowFluidCargoEndpointTest.envelope(EscrowFluidCargoEndpointTest.root(load, load ? 5_000 : 0));
            var authority = new AtomicBoolean(true);
            var permit = EscrowFluidCargoPublication.issue(ledger, op, before, 10, authority::get);
            var after = permit.consume(10, () -> before);
            assertEquals(load ? 4_000 : 1_000, after.tank().amount());
            assertEquals(List.of(new EscrowPrefix.Link(op, op.device(), load ? "SOURCE" : "DESTINATION").marker()), after.escrow().markers());
            assertThrows(IllegalArgumentException.class, () -> permit.consume(10, () -> before));
            var stale = EscrowFluidCargoPublication.issue(ledger, op, before, 10, authority::get);
            assertThrows(IllegalArgumentException.class, () -> stale.consume(11, () -> before));
            var changed = EscrowFluidCargoPublication.issue(ledger, op, before, 10, authority::get);
            var altered = before.copy(); altered.putInt("changed", 1);
            assertThrows(IllegalArgumentException.class, () -> changed.consume(10, () -> altered));
            assertThrows(IllegalArgumentException.class, () -> changed.consume(10, () -> before));
            var lost = EscrowFluidCargoPublication.issue(ledger, op, before, 10, authority::get);
            authority.set(false);
            assertThrows(IllegalArgumentException.class, () -> lost.consume(10, () -> before));
        }
    }

    @Test void offThreadCannotConsumeAndIncompleteOrTamperedBindingsCannotIssue() {
        for (boolean load : List.of(true, false)) {
            var op = bound(load); var ledger = EscrowLedger.empty(EscrowFluidCargoEndpointTest.LEDGER).append(List.of(op));
            var before = EscrowFluidCargoEndpointTest.envelope(EscrowFluidCargoEndpointTest.root(load, load ? 5_000 : 0));
            var permit = EscrowFluidCargoPublication.issue(ledger, op, before, 10, () -> true);
            CompletableFuture.runAsync(() -> assertThrows(IllegalArgumentException.class, () -> permit.consume(10, () -> before))).join();
            assertDoesNotThrow(() -> permit.consume(10, () -> before));
            var model = EscrowFluidCargoEndpointTest.operation(load);
            assertThrows(IllegalArgumentException.class, () -> EscrowFluidCargoPublication.issue(
                    EscrowLedger.empty(ledger.id()).append(List.of(model)), model, before, 10, () -> true));
            var altered = before.copy(); altered.putInt("changed", 1);
            assertThrows(IllegalArgumentException.class, () -> EscrowFluidCargoPublication.issue(ledger, op, altered, 10, () -> true));
        }
    }
}
