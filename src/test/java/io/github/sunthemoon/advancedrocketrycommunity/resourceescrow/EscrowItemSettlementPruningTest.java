package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketCargoRole;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketItemCargoRoot;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** D-E2 item 4: only an acknowledged released group removes a cargo device's leading markers. */
class EscrowItemSettlementPruningTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    record Released(EscrowOperation op, EscrowLedger root, List<EscrowSettlementPruning.Group> groups, RocketItemCargoRoot device) { }
    /** A load held after its device published, or an unload held after both published, then O3 and O4. */
    static Released released(boolean load) {
        var f = EscrowItemReconciliationFixture.create(load); var op = f.op().withReason(EscrowReason.OTHER_STATE);
        var root = EscrowItemSettlementProofTest.held(f, op);
        var intent = EscrowItemSettlementProofTest.intent(root, op, List.of(
                EscrowItemSettlementObservationTest.rocket(f, load ? f.before() : f.after()),
                EscrowItemSettlementObservationTest.device(f, f.deviceAfter())));
        var released = EscrowSettlementTombstones.release(EscrowSettlementTombstones.install(intent.freeze(root), intent), intent, 7);
        return new Released(op, released, EscrowSettlementPruning.groups(released), EscrowItemCargoEndpoint.root(f.deviceAfter()));
    }
    static RocketItemCargoRoot with(RocketItemCargoRoot root, RocketEscrowSection section) {
        return new RocketItemCargoRoot(root.owner(), root.device(), root.role(), root.binding(), root.buffer(), section);
    }

    @Test void theReleasedMarkerIsRemovedWithoutChangingBufferEpochOrIdentity() {
        for (boolean load : List.of(true, false)) {
            var r = released(load); var members = Set.of(r.op().id()); var device = r.op().device();
            assertEquals(1, r.device().escrow().markers().size());
            var decision = EscrowSettlementPruning.itemDevice(r.root(), r.groups(), device, r.device(), true, members, Set.of());
            assertEquals(EscrowSettlementPruning.State.DONE, decision.state());
            assertEquals(new EscrowPrefix.State(r.root().id(), 1, r.device().signature(), List.of()), decision.pruned());
            // The permit's consumer: the same ledger, epoch and buffer signature, with only the marker removed.
            var section = EscrowItemCargoEndpoint.pruned(r.device(), decision.pruned());
            assertEquals(new RocketEscrowSection(r.root().id(), 1, List.of()), section);
            var pruned = with(r.device(), section);
            assertEquals(r.device().buffer(), pruned.buffer()); assertEquals(r.device().signature(), pruned.signature());
            var again = EscrowSettlementPruning.itemDevice(r.root(), r.groups(), device, pruned, true, members, Set.of());
            assertNull(again.pruned()); assertEquals(EscrowSettlementPruning.State.DONE, again.state(), "A stop before saving repeats safely");
            var waiting = EscrowSettlementPruning.itemDevice(r.root(), r.groups(), device, r.device(), true, members, members);
            assertNull(waiting.pruned()); assertEquals(EscrowSettlementPruning.State.WAITING_EARLIER_GROUP, waiting.state());
        }
    }

    @Test void changedIdentityRoleLedgerBufferOrAuthorityRetainsTheMarker() {
        for (boolean load : List.of(true, false)) {
            var r = released(load); var members = Set.of(r.op().id()); var device = r.op().device(); var held = r.device();
            var refusals = List.of(
                    EscrowSettlementPruning.itemDevice(r.root(), r.groups(), device, held, false, members, Set.of()),
                    EscrowSettlementPruning.itemDevice(r.root(), r.groups(), device, null, true, members, Set.of()),
                    EscrowSettlementPruning.itemDevice(r.root(), r.groups(), device, new RocketItemCargoRoot(held.owner(), UUID.randomUUID(),
                            held.role(), held.binding(), held.buffer(), held.escrow()), true, members, Set.of()),
                    EscrowSettlementPruning.itemDevice(r.root(), r.groups(), new EscrowEndpoint(device.kind(), device.level(), device.identity(),
                            null, "advancedrocketrycommunity:rocket_item_" + (load ? "unloader" : "loader"), device.x(), device.y(), device.z(), device.unit(),
                            device.beforeEpoch(), device.beforeSignature(), device.afterSignature()), held, true, members, Set.of()),
                    EscrowSettlementPruning.itemDevice(r.root(), r.groups(), device, with(held, new RocketEscrowSection(UUID.randomUUID(),
                            held.escrow().epoch(), held.escrow().markers())), true, members, Set.of()),
                    EscrowSettlementPruning.itemDevice(r.root(), r.groups(), device, held.withSlot(3,
                            ItemCargoReconciliationFixtures.stack(1)), true, members, Set.of()),
                    EscrowSettlementPruning.itemDevice(r.root(), List.of(), device, held, true, members, Set.of()));
            for (var refusal : refusals) {
                assertNull(refusal.pruned());
                assertEquals(EscrowSettlementPruning.State.Kind.REFUSED, refusal.state().kind(), refusal.state().text());
            }
            // A device that never published (abandon or a neutral device) has nothing to prune.
            var neutral = EscrowSettlementPruning.itemDevice(r.root(), r.groups(), device, with(held, RocketEscrowSection.neutral()), true, members, Set.of());
            assertNull(neutral.pruned()); assertEquals(EscrowSettlementPruning.State.DONE, neutral.state());
        }
    }
}
