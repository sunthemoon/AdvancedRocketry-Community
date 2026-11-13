package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowTestFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowFluidAccount.*;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EscrowAccountingSettlementTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    @org.junit.jupiter.api.io.TempDir java.nio.file.Path directory;

    static List<EscrowSettlementIntent.Observation> observations(EscrowLedger root, EscrowEndpoint.Key missing) {
        var endpoints = new java.util.LinkedHashMap<EscrowEndpoint.Key, EscrowEndpoint>();
        root.records().forEach(op -> { endpoints.put(op.source().key(), op.source()); endpoints.put(op.destination().key(), op.destination()); });
        return endpoints.values().stream().map(endpoint -> new EscrowSettlementIntent.Observation(endpoint,
                endpoint.key().equals(missing) ? null : new EscrowPrefix.State(root.id(), endpoint.beforeEpoch(), endpoint.beforeSignature(), List.of()),
                !endpoint.key().equals(missing), 0)).toList();
    }
    static Map<EscrowEndpoint.Key, Map<UUID, Vector>> vectors(EscrowLedger root, long source, long destination) {
        var values = new HashMap<EscrowEndpoint.Key, Map<UUID, Vector>>();
        for (var op : root.records()) {
            if (op.accountingBaseline() == null) { continue; }
            var key = EscrowAccountingReconciliation.baseline(root, op).moved();
            values.computeIfAbsent(op.source().key(), k -> new HashMap<>()).put(op.id(), Vector.of(key, source).value());
            values.computeIfAbsent(op.destination().key(), k -> new HashMap<>()).put(op.id(), Vector.of(key, destination).value());
        }
        return values;
    }
    static EscrowSettlementIntent preview(EscrowLedger root, List<EscrowSettlementIntent.Observation> observations, List<Row> rows) {
        var intent = EscrowSettlementIntent.preview(root, root.records().get(0).id(), observations.stream().anyMatch(EscrowSettlementIntent.Observation::missing)
                ? EscrowSettlementIntent.Choice.ABANDON : EscrowSettlementIntent.Choice.ACCEPT_CURRENT, id(700), "Operator", 10, observations);
        return rows.isEmpty() ? intent : intent.accounting(rows);
    }
    static EscrowLedger released(EscrowLedger root, List<EscrowSettlementIntent.Observation> observations, List<Row> rows) {
        var preview = preview(root, observations, rows);
        var intent = preview.confirm(preview.actor(), preview.digest(), observations.stream().anyMatch(EscrowSettlementIntent.Observation::missing) ? 210 : 11, observations);
        var o1 = intent.freeze(root, EscrowSettlementAccounting.merge(root, intent, rows));
        var o3 = EscrowSettlementTombstones.install(o1, intent);
        var o4 = EscrowSettlementTombstones.release(o3, intent);
        for (var stage : List.of(o1, o3, o4)) { assertEquals(stage, EscrowCodec.decode(EscrowCodec.encode(stage))); }
        assertEquals(rows, o4.fluidAccounts()); return o4;
    }

    @Test void signedLossGainAndBothHalfApplicationsAreExplicitDigestConfirmedDecisions() {
        var root = EscrowAccountingFormatTest.planned(operation(1).withReason(EscrowReason.ACCOUNTING_MISMATCH));
        var observed = observations(root, null); var op = root.records().get(0);
        for (long[] amounts : List.of(new long[]{4000,1000,0}, new long[]{4000,900,100}, new long[]{5000,1000,-1000}, new long[]{4000,0,1000})) {
            var vectors = vectors(root, amounts[0], amounts[1]);
            var rows = EscrowSettlementAccounting.project(root, root.records(), observed, vectors);
            assertEquals(amounts[2], rows.get(0).frozen().entries().get(0).adjustment().orElseThrow());
            var p = preview(root, observed, rows);
            assertEquals(3, p.encode().getInt("schema_version"));
            var changed = EscrowSettlementAccounting.project(root, root.records(), observed, vectors(root, amounts[0], amounts[1]+1));
            assertNotEquals(p.digest(), preview(root, observed, changed).digest());
            assertThrows(IllegalArgumentException.class, () -> p.confirm(id(701), p.digest(), 11, observed));
            assertThrows(IllegalArgumentException.class, () -> p.confirm(p.actor(), hash(88), 11, observed));
            EscrowSettlementAccounting.requireFrozen(root, p, observed, vectors);
            assertThrows(IllegalArgumentException.class, () -> EscrowSettlementAccounting.requireFrozen(root, p, observed, vectors(root, amounts[0], amounts[1]+1)));
            var lines = EscrowAccountingDiagnostics.page(rows, 0, "PREVIEW").toString();
            assertTrue(lines.contains("dS=") && lines.contains("residual=") && lines.contains("adjustment_owner=" + op.id()));
            if (amounts[2] == 1000 || amounts[2] == -1000) { assertTrue(lines.contains("HALF_APPLIED_")); }
            var result = released(root, observed, rows); var receipt = result.tombstones().get(0);
            assertEquals(4, receipt.getInt("schema_version")); assertEquals("KNOWN", receipt.getString("accounting_status"));
            assertEquals(EscrowFluidAccountCodec.digest(rows.get(0), EscrowFluidAccountCodec.SAVED).value(), receipt.getString("accounting_digest"));
        }
    }

    @Test void abandonRetainsKnownContributionsAndUnknownAfterReleaseWithNoRefund() {
        for (boolean sourceMissing : List.of(true, false)) {
            var root = EscrowAccountingFormatTest.planned(operation(1).withReason(EscrowReason.MISSING_ENDPOINT));
            var op = root.records().get(0); var observed = observations(root, (sourceMissing ? op.source() : op.destination()).key());
            var rows = EscrowSettlementAccounting.project(root, root.records(), observed, vectors(root, 4000, 900));
            var entry = rows.get(0).frozen().entries().get(0);
            assertEquals(sourceMissing, entry.source().isEmpty()); assertEquals(!sourceMissing, entry.destination().isEmpty());
            assertTrue(entry.adjustment().isEmpty()); assertEquals(Knowledge.UNKNOWN_ENDPOINT, rows.get(0).knowledge());
            var p = preview(root, observed, rows);
            assertThrows(IllegalArgumentException.class, () -> p.confirm(p.actor(), p.digest(), 209, observed));
            assertTrue(EscrowAccountingDiagnostics.page(rows, 0, "PREVIEW").toString().contains("residual=UNKNOWN"));
            var result = released(root, observed, rows);
            assertEquals("UNKNOWN", result.tombstones().get(0).getString("accounting_status"));
            assertEquals(1, result.tombstones().get(0).getList("missing", 10).size());
            assertEquals(root.fluidAccounts().get(0).baseline(), result.fluidAccounts().get(0).baseline());
        }
    }

    @Test void sameTotalFluidReplacementCannotReuseTheFrozenCommitment() {
        var root = EscrowAccountingFormatTest.planned(operation(1).withReason(EscrowReason.OTHER_STATE));
        var observed = observations(root, null); var values = vectors(root, 4000, 1000);
        var rows = EscrowSettlementAccounting.project(root, root.records(), observed, values); var p = preview(root, observed, rows);
        var op = root.records().get(0); var water = rows.get(0).baseline().moved(); var lava = Key.derive("minecraft:lava", null).value();
        values.put(op.destination().key(), Map.of(op.id(), Vector.aggregate(List.of(new Unit(water, 900),new Unit(lava, 100))).value()));
        var replaced = EscrowSettlementAccounting.project(root, root.records(), observed, values);
        assertEquals(2, replaced.get(0).frozen().entries().size());
        assertEquals(List.of(-100L,100L), replaced.get(0).frozen().entries().stream().map(e -> e.adjustment().orElseThrow()).sorted().toList());
        assertThrows(IllegalArgumentException.class, () -> EscrowSettlementAccounting.requireFrozen(root, p, observed, values));
    }

    @Test void chainAccountsCountEachTypedEndpointOnceAndKeepSharedAdjustmentOwner() {
        var a = EscrowAccountingCaptureTest.fuel(false).admission(); var b = EscrowAccountingCaptureTest.fuel(true).admission();
        var root = EscrowAccountingReconciliationTest.root(a,b);
        root = root.replace(root.records().stream().map(op -> op.withReason(EscrowReason.OTHER_STATE)).toList(), root.floors(),root.tombstones());
        var observed = observations(root,null); var values = EscrowAccountingReconciliationTest.vectors(root,true,true);
        var rows = EscrowSettlementAccounting.project(root,root.records(),observed,values);
        assertEquals(0, rows.stream().flatMap(row -> row.frozen().entries().stream()).mapToLong(e -> e.adjustment().orElse(0)).sum());
        assertTrue(rows.get(1).frozen().entries().get(0).shared(b.operation().id()));
        assertEquals(a.operation().id(),rows.get(1).frozen().entries().get(0).owner());
        released(root,observed,rows);
    }

    @Test void mixedItemAndLegacyMembersSelectTheExactIntentAndReceiptSchemas() {
        for (boolean legacy : List.of(false,true)) { for (boolean abandon : List.of(false,true)) {
            var original = operation(1).withReason(EscrowReason.OTHER_STATE);
            var other = legacy ? EscrowCargoFixtures.operation(EscrowOperation.Kind.FLUID_CARGO_UNLOAD,2,99)
                    : EscrowItemFixtures.operation(EscrowOperation.Kind.ITEM_CARGO_LOAD, 2, 99, 0, -1, "minecraft:overworld");
            // W114F's cross-kind UUID alias connects distinct typed endpoints, without sharing a cargo rocket.
            var s = original.source();
            var alias = new EscrowEndpoint(s.kind(),s.level(),other.rocket().identity(),s.logicalRocket(),s.type(),s.x(),s.y(),s.z(),s.unit(),s.beforeEpoch(),s.beforeSignature(),s.afterSignature());
            var fluid = new EscrowOperation(original.id(),1,original.owner(),1,original.phase(),original.reason(),original.payload(),original.payloadHash(),alias,original.destination(),original.guard());
            var accounted = EscrowAccountingFormatTest.planned(fluid);
            var root = new EscrowLedger(accounted.id(),2,0,List.of(accounted.records().get(0),other),List.of(),List.of(),0,List.of(),List.of(),3,accounted.fluidAccounts());
            assertEquals(2, EscrowSettlementIntent.component(root.records(),fluid.id()).size());
            var observed = observations(root,abandon ? fluid.source().key() : null); var rows = EscrowSettlementAccounting.project(root,root.records(),observed,vectors(root,4000,1000));
            assertEquals(legacy ? Knowledge.UNKNOWN_BASELINE : abandon ? Knowledge.UNKNOWN_ENDPOINT : Knowledge.KNOWN, rows.get(0).knowledge());
            var result = released(root,observed,rows);
            assertEquals(List.of(4,3), result.tombstones().stream().map(tag -> tag.getInt("schema_version")).toList());
            assertEquals("UNKNOWN",result.tombstones().get(1).getString("accounting_status"));
        } }
    }

    @Test void rowlessLegacyPreparedMemberStillOffersAnUnknownDecision() {
        for (boolean abandon : List.of(false,true)) {
        var root = ledger(operation(1)).accounting(List.of(operation(1)),List.of());
        var observed = observations(root,abandon ? root.records().get(0).source().key() : null); var rows = EscrowSettlementAccounting.project(root,root.records(),observed,Map.of());
        assertTrue(rows.isEmpty()); assertEquals(2, preview(root,observed,rows).encode().getInt("schema_version"));
        var result = released(root,observed,rows); assertEquals(3,result.tombstones().get(0).getInt("schema_version"));
        assertEquals("UNKNOWN",result.tombstones().get(0).getString("accounting_status"));
        }
    }

    @Test void numericReadbackUsesTheSameDecodedCargoAndDeviceRootsAsTheirSignatures() {
        for (boolean load : List.of(true,false)) {
            var f = EscrowCargoReconciliationFixture.create(load);
            var capture = EscrowAccountingCapture.bind(f.op(),RocketEscrowSection.neutral(), f.before(),f.after(),f.deviceBefore(),f.deviceAfter(),
                    EscrowCargoSettlementProofTest.snapshot(f.before()),EscrowCargoSettlementProofTest.snapshot(f.after()));
            var root = f.ledger().accounting(List.of(capture.operation().withReason(EscrowReason.OTHER_STATE)),List.of(capture.account()));
            var op = root.records().get(0);
            var rocketSlot = EscrowReconciliationCheckpointTest.rocketSlot(op.rocket(),f.after());
            var deviceSlot = EscrowFluidCargoEndpointTest.slot(f.deviceAfter());
            var rocket = EscrowSettlementDisk.ordinary(rocketSlot,op.rocket(),root.id(),null,List.of(op));
            var device = EscrowSettlementDisk.ordinary(deviceSlot,op.device(),root.id(),null,List.of(op));
            var observed = List.of(rocket.settlement(),device.settlement()); var values = Map.of(op.rocket().key(),rocket.fluids(),op.device().key(),device.fluids());
            var rows = EscrowSettlementAccounting.project(root,root.records(),observed,values); var p = preview(root,observed,rows);
            var intent = p.confirm(p.actor(),p.digest(),11,observed); var o1 = intent.freeze(root,rows);
            for (var endpoint : List.of(op.rocket(),op.device())) {
                var proof = EscrowSettlementProofs.slot(intent,List.of(op),endpoint,e -> EscrowCargoSettlementProofTest.snapshot(f.after()),o1.fluidAccounts());
                assertEquals(1,proof.readbacks().size()); var target = proof.targets().get(0); var slot = endpoint.equals(op.rocket()) ? rocketSlot : deviceSlot;
                assertTrue(target.validator().test(slot)); assertFalse(proof.confirmed());
                assertTrue(CompletableFuture.supplyAsync(() -> target.validator().test(slot)).join()); assertTrue(proof.confirmed());
                var beforeSlot = endpoint.equals(op.rocket()) ? EscrowReconciliationCheckpointTest.rocketSlot(op.rocket(),f.before()) : EscrowFluidCargoEndpointTest.slot(f.deviceBefore());
                assertTrue(CompletableFuture.supplyAsync(() -> target.validator().test(beforeSlot)).join()); assertFalse(proof.confirmed());
                assertEquals(EscrowReason.ACCOUNTING_MISMATCH,proof.reason(new EscrowBarrier.JointResult(Map.of(),EscrowReason.BARRIER_UNSTABLE)));
                if (endpoint.equals(op.device())) {
                    var changed = f.deviceAfter().copy();
                    var tank = changed.getCompound(io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketFluidCargoRoot.KEY).getCompound("tank");
                    tank.putString("FluidName","minecraft:lava");
                    var swapped = EscrowFluidCargoEndpointTest.slot(changed);
                    assertEquals(device.settlement().quantity(),EscrowSettlementDisk.ordinary(swapped,op.device(),root.id(),null,List.of(op)).settlement().quantity());
                    assertTrue(CompletableFuture.supplyAsync(() -> target.validator().test(swapped)).join()); assertFalse(proof.confirmed());
                    assertEquals(EscrowReason.ACCOUNTING_MISMATCH,proof.reason(new EscrowBarrier.JointResult(Map.of(),EscrowReason.BARRIER_UNSTABLE)));
                }
            }
            assertEquals(rows,o1.fluidAccounts());
        }
    }

    @Test void diagnosticsAreDemandDrivenPaginatedAndNeverContainNativeTagBodies() {
        var rows = new ArrayList<Row>();
        for (int i=1;i<=20;i++) { rows.add(EscrowAccountingFormatTest.frozen(operation(i),Knowledge.KNOWN)); }
        assertEquals(17,EscrowAccountingDiagnostics.page(rows,0,"O1").size());
        assertEquals(5,EscrowAccountingDiagnostics.page(rows,1,"O1").size());
        assertThrows(IllegalArgumentException.class,() -> EscrowAccountingDiagnostics.page(rows,2,"O1"));
        assertThrows(IllegalArgumentException.class,() -> EscrowAccountingDiagnostics.page(rows,-1,"O1"));
        assertFalse(EscrowAccountingDiagnostics.page(rows,0,"O1").toString().contains("FluidName"));
        var root = EscrowAccountingFormatTest.planned(operation(1).withReason(EscrowReason.OTHER_STATE));
        var observed = observations(root,null); var frozen = EscrowSettlementAccounting.project(root,root.records(),observed,vectors(root,4000,1000));
        var p = preview(root,observed,frozen); var cache = new EscrowSettlementOperator.PreviewCache(); cache.put(p,frozen,10);
        assertEquals(frozen,cache.rows(p.actor())); assertEquals(p,cache.get(p.actor(),20));
        assertTrue(EscrowAccountingDiagnostics.audit(p,frozen).contains("signed_loss=0"));
        assertNull(cache.get(p.actor(),1211)); assertTrue(cache.rows(p.actor()).isEmpty());
    }

    @Test void bareFloorSettlementCreatesNoNumericRowOrHistoricalZeroLossClaim() {
        var floor = floor(99); floor.putUUID("transfer_id",id(800));
        var root = new EscrowLedger(id(500),1,0,List.of(),List.of(floor),List.of(),0,List.of(),List.of(),3,List.of());
        var endpoint = new EscrowEndpoint(EscrowEndpoint.Kind.ENTITY,"minecraft:overworld",floor.getUUID("entity_id"),floor.getUUID("rocket_id"),
                "minecraft:armor_stand",0,0,0,EscrowEndpoint.Unit.PRIMARY,1,floor.getString("signature"),floor.getString("signature"));
        var observed = List.of(new EscrowSettlementIntent.Observation(endpoint,null,false,0));
        var p = EscrowSettlementIntent.floorPreview(root,id(701),floor,id(700),"Operator",10,observed);
        var intent = p.confirm(p.actor(),p.digest(),210,observed);
        var o1 = EscrowSettlementCapacity.floorRetirement(root,intent,2,210,FLOOR_OWNER);
        var result = EscrowSettlementTombstones.release(EscrowSettlementTombstones.install(o1,intent),intent);
        assertTrue(result.fluidAccounts().isEmpty()); assertTrue(result.floors().isEmpty()); assertTrue(result.records().isEmpty());
        assertEquals(3,result.tombstones().get(0).getInt("schema_version"));
        assertEquals(0,result.tombstones().get(0).getInt("payload_amount")); assertEquals("UNKNOWN",result.tombstones().get(0).getString("accounting_status"));
        assertEquals(result,EscrowCodec.decode(EscrowCodec.encode(result)));
    }

    @Test void independentItemPreparationAcceptsPendingFluidRowsWithoutChangingRoot2() {
        for (boolean accounting : List.of(false,true)) {
            var empty = ledger(); if (accounting) { empty = empty.accounting(List.of(),List.of()); }
            var first = nativeModel(EscrowSettlementCapacityTest.operation(1,991,0,true),RocketEscrowSection.neutral());
            var base = EscrowAccountingFormatTest.baseline(first);
            if (accounting) { first = first.accounting(base); }
            var data = ResourceEscrowSavedData.load(EscrowCodec.encode(empty));
            try (var writer = new EscrowLedgerWriter(directory.resolve("pending-"+accounting),0); var coordinator = new EscrowCoordinator(data,writer)) {
                var admission = new EscrowNativeBinding.Admission(first,RocketEscrowSection.neutral(), accounting ? new Row(first.id(),base,null) : null);
                assertEquals(EscrowCoordinator.Fill.QUEUED,coordinator.prepareFill(admission,ignored -> {}));
                var c = EscrowItemCargoEligibilityTest.sample(true,5,EscrowItemCargoEligibilityTest.stone(-1));
                var old = c.op();
                var op = new EscrowOperation(id(2),2,old.owner(),old.createdTick(),old.phase(),old.reason(),old.payload(),old.payloadHash(),
                        old.source(),old.destination(),old.guard(),old.kind(),old.settlement());
                var after = c.after().copy(); after.put("wrapper", new RocketEscrowSection(empty.id(),1,List.of(new EscrowPrefix.Link(op,op.rocket(),"DESTINATION").marker())).encode());
                var deviceAfter = c.deviceAfter().copy(); deviceAfter.getCompound(EscrowItemCargoEligibility.DEVICE_ROOT).put("resource_escrow",
                        new RocketEscrowSection(empty.id(),1,List.of(new EscrowPrefix.Link(op,op.device(),"SOURCE").marker())).encode());
                var snapshot = EscrowCargoSettlementProofTest.snapshot(c.before());
                try (var session = new io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.CargoTicketTestFixture(op.guard().rocket(),snapshot,EscrowItemCargoPayload.decode(op.payload()),true)) {
                    long tick=1; while (!session.advance(tick++)) { assertTrue(tick<100); }
                    var capture = new EscrowCargoPreparedCapture(session.ticket,op,c.before(),after,c.deviceBefore(),deviceAfter,0);
                    while (true) {
                        session.at(tick++); assertTrue(tick<100);
                        if (capture.advance(accounting ? coordinator.pendingLedger() : coordinator.ledger(),accounting ? List.of() : coordinator.pendingAdmissions())) { break; }
                    }
                    assertEquals(io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.RocketCargoSelection.Status.ELIGIBLE,capture.result().status());
                    session.at(tick);
                    assertEquals(EscrowCoordinator.Transfer.QUEUED,coordinator.prepareCargo(capture,() -> true,ignored -> {}));
                    assertEquals(2,coordinator.pendingAdmissions().size()); assertEquals(accounting ? 1 : 0,coordinator.pendingLedger().fluidAccounts().size());
                    assertEquals(3,coordinator.nextSequence()); assertTrue(writer.idle());
                }
            }
        }
    }

    @Test void fuelO2ConfirmsTypedBanksAndSourceAndRefusesSameAmountTagSubstitution() {
        var f = EscrowAccountingCaptureTest.fuel(false); var capture = f.admission();
        var root = EscrowAccountingReconciliationTest.root(capture);
        root = root.replace(List.of(capture.operation().withReason(EscrowReason.OTHER_STATE)),root.floors(),root.tombstones());
        var op = root.records().get(0);
        var rocketSlot = EscrowReconciliationCheckpointTest.rocketSlot(op.rocket(),f.after());
        var deviceSlot = EscrowAccountingReconciliationTest.slot(op.device(),f.deviceAfter());
        var rocket = EscrowSettlementDisk.ordinary(rocketSlot,op.rocket(),root.id(),null,List.of(op));
        var device = EscrowSettlementDisk.ordinary(deviceSlot,op.device(),root.id(),null,List.of(op));
        var observed = List.of(rocket.settlement(),device.settlement());
        var rows = EscrowSettlementAccounting.project(root,root.records(),observed,Map.of(op.rocket().key(),rocket.fluids(),op.device().key(),device.fluids()));
        var p = preview(root,observed,rows); var intent = p.confirm(p.actor(),p.digest(),11,observed); var o1 = intent.freeze(root,rows);
        for (var endpoint : List.of(op.rocket(),op.device())) {
            var proof = EscrowSettlementProofs.slot(intent,List.of(op),endpoint,e -> EscrowCargoSettlementProofTest.snapshot(f.after()),o1.fluidAccounts());
            assertTrue(CompletableFuture.supplyAsync(() -> proof.targets().get(0).validator().test(endpoint.equals(op.rocket()) ? rocketSlot : deviceSlot)).join());
            assertTrue(proof.confirmed());
            if (endpoint.equals(op.device())) {
                var changed = f.deviceAfter().copy(); changed.getCompound(io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.LoaderRoot.KEY)
                        .getCompound("tank").put("Tag",new CompoundTag());
                var sameAmount = EscrowAccountingReconciliationTest.slot(op.device(),changed);
                assertTrue(CompletableFuture.supplyAsync(() -> proof.targets().get(0).validator().test(sameAmount)).join());
                assertFalse(proof.confirmed()); assertEquals(EscrowReason.ACCOUNTING_MISMATCH,
                        proof.reason(new EscrowBarrier.JointResult(Map.of(),EscrowReason.BARRIER_UNSTABLE)));
            }
        }
        assertEquals(rows,o1.fluidAccounts());
    }

    @Test void abandonO2ProvesAbsenceWithoutInventingAMissingVector() {
        var f = EscrowCargoReconciliationFixture.create(true);
        var capture = EscrowAccountingCapture.bind(f.op(),RocketEscrowSection.neutral(),f.before(),f.after(),f.deviceBefore(),f.deviceAfter(),
                EscrowCargoSettlementProofTest.snapshot(f.before()),EscrowCargoSettlementProofTest.snapshot(f.after()));
        var root = f.ledger().accounting(List.of(capture.operation().withReason(EscrowReason.MISSING_ENDPOINT)),List.of(capture.account()));
        var op = root.records().get(0); var rocketSlot = EscrowReconciliationCheckpointTest.rocketSlot(op.rocket(),f.after());
        var rocket = EscrowSettlementDisk.ordinary(rocketSlot,op.rocket(),root.id(),null,List.of(op));
        var absent = new EscrowSettlementIntent.Observation(op.device(),null,false,0);
        var observed = List.of(rocket.settlement(),absent);
        var rows = EscrowSettlementAccounting.project(root,root.records(),observed,Map.of(op.rocket().key(),rocket.fluids()));
        var p = preview(root,observed,rows); var intent = p.confirm(p.actor(),p.digest(),210,observed);
        var o1 = intent.freeze(root,rows);
        var missingProof = EscrowSettlementProofs.slot(intent,List.of(op),op.device(),e -> fail("Absence needs no snapshot"),o1.fluidAccounts());
        var missingSlot = EscrowFluidCargoEndpointTest.slot(f.deviceAfter()); missingSlot.put("block_entities",new net.minecraft.nbt.ListTag());
        assertTrue(missingProof.readbacks().isEmpty()); assertTrue(missingProof.targets().get(0).validator().test(missingSlot));
        assertFalse(missingProof.targets().get(0).validator().test(EscrowFluidCargoEndpointTest.slot(f.deviceAfter())));
        var survivor = EscrowSettlementProofs.slot(intent,List.of(op),op.rocket(),e -> EscrowCargoSettlementProofTest.snapshot(f.after()),o1.fluidAccounts());
        assertTrue(CompletableFuture.supplyAsync(() -> survivor.targets().get(0).validator().test(rocketSlot)).join()); assertTrue(survivor.confirmed());
        var o4 = released(root,observed,rows); assertEquals(Knowledge.UNKNOWN_ENDPOINT,o4.fluidAccounts().get(0).knowledge());
    }
}
