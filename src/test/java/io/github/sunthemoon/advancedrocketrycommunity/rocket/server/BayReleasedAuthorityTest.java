package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static io.github.sunthemoon.advancedrocketrycommunity.rocket.server.BayConsumptionFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.BayReceipt;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.RocketCargoWorkBudget;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferPhase;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class BayReleasedAuthorityTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void intentNeedsBlockedRegistryOrAnAlreadyReleasedGroup() {
        var t = record(1); var r = intent(t, 0);
        assertTrue(BayReleasedAuthority.capture(t, List.of(r), false).isEmpty());
        assertTrue(BayReleasedAuthority.capture(t, List.of(r), true).isPresent());
        assertTrue(BayReleasedAuthority.capture(t, List.of(r.withReleased()), false).isPresent());
    }

    @Test void coherentRepairKeepsItsPackageAndRequiresOneWholeReleasedGeneration() {
        var t = record(2); var a = intent(t, 0).withPhase(BayReceipt.Phase.REPAIR, "REGISTRY_SIGNATURE_CONFLICT", 3);
        var b = intent(t, 1);
        assertTrue(BayReleasedAuthority.capture(t, List.of(a, b), false).isPresent());
        assertTrue(BayReleasedAuthority.capture(t, List.of(a.withReleased(), b), false).isEmpty());
        assertTrue(BayReleasedAuthority.capture(t, List.of(a.withReleased(), b.withReleased()), false).isPresent());
        assertTrue(BayReleasedAuthority.payloads(t.sourceSnapshot(), List.of(a, b)));
        assertTrue(BayReleasedAuthority.payloads(t.destinationSnapshot(), List.of(a, b)));
    }

    @Test void wrongSignatureOwnerPositionTransferAndHashesRefuse() {
        var t = record(1); var r = intent(t, 0).withPhase(BayReceipt.Phase.REPAIR, "REGISTRY_SIGNATURE_CONFLICT", 3);
        for (int field = 0; field < 6; field++) {
            var wrong = new BayReceipt(r.op(), r.seq(), r.rocket(), field == 0 ? bay(7) : r.bay(), r.satellite(),
                    field == 1 ? UUID.randomUUID() : r.owner(), r.definition(), r.kind(), r.orbit(),
                    field == 2 ? "a".repeat(64) : r.packageSig(), field == 3 ? UUID.randomUUID() : r.transfer(),
                    field == 4 ? "b".repeat(64) : r.srcBefore(), null, field == 5 ? "c".repeat(64) : r.dstBefore(), null,
                    r.phase(), r.reason(), false, false, 0, 1, 3, 0);
            assertTrue(BayReleasedAuthority.capture(t, List.of(wrong), false).isEmpty(), "field " + field);
        }
    }

    @Test void duplicateAndEmptyGroupsRefuseAndEightMembersRemainEligible() {
        var t = record(8);
        var receipts = java.util.stream.IntStream.range(0, 8).mapToObj(i -> intent(t, i)).toList();
        assertTrue(BayReleasedAuthority.capture(t, receipts, true).isPresent());
        assertTrue(BayReleasedAuthority.capture(t, List.of(), true).isEmpty());
        assertTrue(BayReleasedAuthority.capture(t, List.of(receipts.get(0), receipts.get(0)), true).isEmpty());
    }

    @Test void registeredMemberMustBeConsumedAtBothEndsBeforeMixedGroupCanRelease() {
        var t = record(2); var registeredIntent = intent(t, 0);
        var job = BayConsumedSnapshots.prepare(t.sourceSnapshot(), t.destinationSnapshot(), Map.of(bay(0), registeredIntent)).orElseThrow();
        var budget = new RocketCargoWorkBudget();
        for (long tick = 0; tick < 10 && !job.step(budget, tick); tick++) { }
        var result = job.result().orElseThrow();
        var r = registered(registeredIntent, result.source().contentHash(), result.destination().contentHash());
        var repair = intent(t, 1).withPhase(BayReceipt.Phase.REPAIR, "REGISTRY_SIGNATURE_CONFLICT", 3);
        assertTrue(BayReleasedAuthority.capture(t, List.of(r, repair), false).isEmpty());
        var after = t.withConsumedSnapshots(result.source(), result.destination());
        assertTrue(BayReleasedAuthority.capture(after, List.of(r, repair), false).isPresent());
        var wrongMarker = new BayReceipt(UUID.randomUUID(), r.seq(), r.rocket(), r.bay(), r.satellite(), r.owner(),
                r.definition(), r.kind(), r.orbit(), r.packageSig(), r.transfer(), r.srcBefore(), r.srcAfter(),
                r.dstBefore(), r.dstAfter(), r.phase(), r.reason(), r.preexisting(), r.released(), r.registryEpoch(),
                r.createdTick(), r.updatedTick(), r.attempts());
        assertTrue(BayReleasedAuthority.capture(after, List.of(wrongMarker, repair), false).isEmpty());
    }

    @Test void onlyTheExactActionCanAdvanceOnePhaseAndBindOneDestination() {
        var t = record(1); var r = intent(t, 0).withReleased();
        var authority = BayReleasedAuthority.capture(t, List.of(r), false).orElseThrow();
        var spawned = t.destinationSpawned(UUID.randomUUID());
        for (var action : RocketTransferBayRecoveryGate.Action.values()) {
            assertEquals(action == RocketTransferBayRecoveryGate.Action.SPAWN_DESTINATION,
                    authority.successor(t, spawned, action), action.name());
        }
        var passengers = spawned.advance(RocketTransferPhase.PASSENGERS_TRANSFERRED);
        var restarted = BayReleasedAuthority.capture(spawned, List.of(r), false).orElseThrow();
        assertFalse(restarted.matches(spawned.rebindDestination(UUID.randomUUID())));
        assertFalse(restarted.matches(t));
        assertTrue(authority.successor(spawned, passengers, RocketTransferBayRecoveryGate.Action.TICK_SOURCE));
        assertFalse(authority.successor(t, passengers, RocketTransferBayRecoveryGate.Action.TICK_SOURCE));
        assertFalse(authority.successor(passengers, spawned, RocketTransferBayRecoveryGate.Action.TICK_SOURCE));
        assertFalse(authority.successor(spawned, passengers.rebindDestination(UUID.randomUUID()),
                RocketTransferBayRecoveryGate.Action.TICK_SOURCE));
        assertFalse(authority.successor(t, t.destinationSpawned(t.sourceEntityId()), RocketTransferBayRecoveryGate.Action.SPAWN_DESTINATION));
    }

    @Test void releasedRestartUsesEachJournalPhaseButAbsentJournalNeverGrantsAuthority() {
        var t = record(1); var r = intent(t, 0).withReleased();
        assertTrue(BayReleasedAuthority.capture(null, List.of(r), false).isEmpty());
        assertTrue(BayReleasedAuthority.capture(t, List.of(r), false).isPresent());
        t = t.destinationSpawned(UUID.randomUUID());
        while (true) {
            assertTrue(BayReleasedAuthority.capture(t, List.of(r), false).isPresent());
            if (t.phase() == RocketTransferPhase.COMMITTED) { break; }
            t = t.advance(RocketTransferPhase.values()[t.phase().ordinal() + 1]);
        }
    }
}
