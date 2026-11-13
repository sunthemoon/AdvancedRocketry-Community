package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowFluidAccount.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketCargoRole;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketFluidCargoRoot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.LoaderTank;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.List;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** C-E3 item 4: only an acknowledged released group removes a cargo device's leading markers. */
class EscrowCargoSettlementPruningTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    record Released(EscrowOperation op, EscrowLedger root, List<EscrowSettlementPruning.Group> groups, RocketFluidCargoRoot device) { }
    /** A load held after its device published, or an unload held after both published, then O3 and O4. */
    static Released released(boolean load) {
        var f = EscrowCargoReconciliationFixture.create(load); var op = f.op().withReason(EscrowReason.OTHER_STATE);
        var root = EscrowCargoSettlementProofTest.held(f, op);
        var intent = EscrowCargoSettlementProofTest.intent(root, op, List.of(
                EscrowCargoSettlementObservationTest.rocket(f, load ? f.before() : f.after()),
                EscrowCargoSettlementObservationTest.device(f, f.deviceAfter())));
        var released = EscrowSettlementTombstones.release(EscrowSettlementTombstones.install(intent.freeze(root), intent), intent, 7);
        return new Released(op, released, EscrowSettlementPruning.groups(released), EscrowFluidCargoEndpoint.root(f.deviceAfter()));
    }
    static RocketFluidCargoRoot with(RocketFluidCargoRoot root, RocketEscrowSection section) {
        return new RocketFluidCargoRoot(root.owner(), root.device(), root.role(), root.binding(), root.tank(), section);
    }

    @Test void theReleasedMarkerIsRemovedWithoutChangingTankEpochOrIdentity() {
        for (boolean load : List.of(true, false)) {
            var r = released(load); var members = Set.of(r.op().id()); var device = r.op().device();
            assertEquals(1, r.device().escrow().markers().size());
            var decision = EscrowSettlementPruning.device(r.root(), r.groups(), device, r.device(), true, members, Set.of());
            assertEquals(EscrowSettlementPruning.State.DONE, decision.state());
            assertEquals(new EscrowPrefix.State(r.root().id(), 1, r.device().signature(), List.of()), decision.pruned());
            // The permit's consumer: the same ledger, epoch and tank signature, with only the marker removed.
            var section = EscrowFluidCargoEndpoint.pruned(r.device(), decision.pruned());
            assertEquals(new RocketEscrowSection(r.root().id(), 1, List.of()), section);
            var pruned = with(r.device(), section);
            assertEquals(r.device().tank(), pruned.tank()); assertEquals(r.device().signature(), pruned.signature());
            var again = EscrowSettlementPruning.device(r.root(), r.groups(), device, pruned, true, members, Set.of());
            assertNull(again.pruned()); assertEquals(EscrowSettlementPruning.State.DONE, again.state(), "A stop before saving repeats safely");
            var waiting = EscrowSettlementPruning.device(r.root(), r.groups(), device, r.device(), true, members, members);
            assertNull(waiting.pruned()); assertEquals(EscrowSettlementPruning.State.WAITING_EARLIER_GROUP, waiting.state());
        }
    }

    @Test void schema3AndSchema4ReceiptsGiveEqualPruningResultsAfterRestart() {
        for (boolean load : List.of(true, false)) {
            var f = EscrowCargoReconciliationFixture.create(load);
            var key = EscrowFluidAccountCodec.key(f.op().payload().getCompound("fluid")).value();
            var baseline = new Baseline(Scope.CARGO, List.of(new Entry(key, 5_000, 0, -1_000, 1_000)));
            var op = f.op().withReason(EscrowReason.OTHER_STATE).accounting(baseline);
            var rows = List.of(new Row(op.id(), baseline, new Frozen(Knowledge.KNOWN, op.id(), List.of(op.id()),
                    List.of(new FrozenEntry(key, OptionalLong.of(4_000), OptionalLong.of(1_000), OptionalLong.of(0), op.id())))));
            var root = f.ledger().accounting(List.of(op), List.of(new Row(op.id(), baseline, null)));
            var observed = List.of(EscrowCargoSettlementObservationTest.rocket(f, f.after()),
                    EscrowCargoSettlementObservationTest.device(f, f.deviceAfter()));
            var actor = EscrowTestFixtures.id(700);
            var preview = EscrowSettlementIntent.preview(root, op.id(), EscrowSettlementIntent.Choice.ACCEPT_CURRENT,
                    actor, "operator", 10, observed).accounting(rows);
            var intent = preview.confirm(actor, preview.digest(), 11, observed);
            var installed = EscrowSettlementTombstones.install(intent.freeze(root, rows), intent);
            assertTrue(EscrowSettlementPruning.groups(installed).isEmpty(), "Unreleased schema-4 receipts cannot prune");
            var released = EscrowSettlementTombstones.release(installed, intent, 7);
            var receipt = released.tombstones().get(0);
            assertEquals(4, receipt.getInt("schema_version"));
            // Pair identical release/survivor authority with the old UNKNOWN accounting envelope.
            receipt.putInt("schema_version", 3); receipt.remove("accounting_digest"); receipt.putString("accounting_status", "UNKNOWN");
            var legacy = new EscrowLedger(released.id(), released.issuedThrough(), released.writerGeneration(),
                    List.of(), released.floors(), List.of(receipt), released.issuedLaunchSerial(),
                    released.rocketAuthorities(), released.armedFlights());
            var oldRestart = EscrowCodec.decode(EscrowCodec.encode(legacy));
            var newRestart = EscrowCodec.decode(EscrowCodec.encode(released));
            var oldGroups = EscrowSettlementPruning.groups(oldRestart); var newGroups = EscrowSettlementPruning.groups(newRestart);
            assertEquals(1, oldGroups.size()); assertEquals(oldGroups, newGroups);
            var members = Set.of(op.id()); var device = EscrowFluidCargoEndpoint.root(f.deviceAfter());
            assertEquals(1, device.escrow().markers().size());
            var oldDecision = EscrowSettlementPruning.device(oldRestart, oldGroups, op.device(), device, true, members, Set.of());
            var newDecision = EscrowSettlementPruning.device(newRestart, newGroups, op.device(), device, true, members, Set.of());
            assertEquals(oldDecision, newDecision);
            assertEquals(EscrowSettlementPruning.State.DONE, newDecision.state());
            assertEquals(new EscrowPrefix.State(root.id(), 1, device.signature(), List.of()), newDecision.pruned());
            var pruned = with(device, EscrowFluidCargoEndpoint.pruned(device, newDecision.pruned()));
            assertEquals(device.tank(), pruned.tank()); assertEquals(device.signature(), pruned.signature());
            assertEquals(device.device(), pruned.device()); assertEquals(device.escrow().epoch(), pruned.escrow().epoch());
            var waiting = EscrowSettlementPruning.device(newRestart, newGroups, op.device(), device, true, members, members);
            assertEquals(EscrowSettlementPruning.device(oldRestart, oldGroups, op.device(), device, true, members, members), waiting);
            assertNull(waiting.pruned()); assertEquals(EscrowSettlementPruning.State.WAITING_EARLIER_GROUP, waiting.state());
            assertEquals(EscrowSettlementPruning.device(oldRestart, oldGroups, op.device(), pruned, true, members, Set.of()),
                    EscrowSettlementPruning.device(newRestart, newGroups, op.device(), pruned, true, members, Set.of()));
            assertEquals(rows, newRestart.fluidAccounts());
        }
    }

    @Test void changedIdentityRoleLedgerTankOrAuthorityRetainsTheMarker() {
        for (boolean load : List.of(true, false)) {
            var r = released(load); var members = Set.of(r.op().id()); var device = r.op().device(); var held = r.device();
            var other = load ? RocketCargoRole.UNLOAD : RocketCargoRole.LOAD;
            var refusals = List.of(
                    EscrowSettlementPruning.device(r.root(), r.groups(), device, held, false, members, Set.of()),
                    EscrowSettlementPruning.device(r.root(), r.groups(), device, null, true, members, Set.of()),
                    EscrowSettlementPruning.device(r.root(), r.groups(), device, new RocketFluidCargoRoot(held.owner(), UUID.randomUUID(),
                            held.role(), held.binding(), held.tank(), held.escrow()), true, members, Set.of()),
                    EscrowSettlementPruning.device(r.root(), r.groups(), new EscrowEndpoint(device.kind(), device.level(), device.identity(),
                            null, "advancedrocketrycommunity:" + other.id(), device.x(), device.y(), device.z(), device.unit(),
                            device.beforeEpoch(), device.beforeSignature(), device.afterSignature()), held, true, members, Set.of()),
                    EscrowSettlementPruning.device(r.root(), r.groups(), device, with(held, new RocketEscrowSection(UUID.randomUUID(),
                            held.escrow().epoch(), held.escrow().markers())), true, members, Set.of()),
                    EscrowSettlementPruning.device(r.root(), r.groups(), device, held.withTank(LoaderTank.of(
                            ResourceLocation.tryParse("minecraft:water"), null, held.tank().amount() + 1)), true, members, Set.of()),
                    EscrowSettlementPruning.device(r.root(), List.of(), device, held, true, members, Set.of()));
            for (var refusal : refusals) {
                assertNull(refusal.pruned());
                assertEquals(EscrowSettlementPruning.State.Kind.REFUSED, refusal.state().kind(), refusal.state().text());
            }
            // A device that never published (abandon or a neutral device) has nothing to prune.
            var neutral = EscrowSettlementPruning.device(r.root(), r.groups(), device, with(held, RocketEscrowSection.neutral()), true, members, Set.of());
            assertNull(neutral.pruned()); assertEquals(EscrowSettlementPruning.State.DONE, neutral.state());
        }
    }
}
