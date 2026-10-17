package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNbtSize;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** W11b3 B3-M: the production fill entry point, its typed refusals, Root-R6's headroom and Root-R8's ceiling charge. */
class EscrowPrepareFillTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    @TempDir Path directory;

    /** A native fill into a rocket's fuel vector, captured with the neutral marker section. */
    private static EscrowOperation fill(long sequence, long rocket) { return EscrowSettlementCapacityTest.operation(sequence, rocket, 0, true); }
    private static EscrowNativeBinding.Admission capture(EscrowOperation op) { return new EscrowNativeBinding.Admission(op, RocketEscrowSection.neutral()); }
    private static ResourceEscrowSavedData loaded(EscrowLedger root) { return ResourceEscrowSavedData.load(EscrowCodec.encode(root)); }
    private Path file(String name) throws Exception {
        return Files.createDirectories(directory.resolve(name)).resolve(ManagedSavedDataType.RESOURCE_ESCROW.fileName());
    }
    /** Nothing changed: no sequence, no queue or writer capture, no dirty root and no reservation. */
    private static void assertUnchanged(EscrowCoordinator coordinator, EscrowLedgerWriter writer, ResourceEscrowSavedData data,
                                        EscrowLedger root, long next, int pending) {
        assertEquals(next, coordinator.nextSequence()); assertEquals(root, coordinator.ledger());
        assertTrue(writer.idle()); assertTrue(writer.capturedOperations().isEmpty()); assertFalse(data.isDirty());
        assertEquals(pending, coordinator.admissionAuthority().pendingCount());
    }

    @Test void aQueuedFillReservesOneSlotAndReportsTheExactL1Acknowledgement() throws Exception {
        var data = loaded(EscrowTestFixtures.ledger()); var ledgerIo = new AwaitedLedgerIo();
        try (var writer = ledgerIo.writer(file("queued"), 0, CheckedSavedDataFile::atomicMove); var coordinator = new EscrowCoordinator(data, writer)) {
            var op = fill(1, 1); var replies = new ArrayList<Boolean>();
            assertFalse(coordinator.productionAdmissionEnabled(), "No device admits yet");
            assertEquals(EscrowCoordinator.Fill.QUEUED, coordinator.prepareFill(capture(op), replies::add));
            assertEquals(2, coordinator.nextSequence()); assertEquals(1, coordinator.admissionAuthority().pendingCount());
            assertTrue(coordinator.admitting(op.source().key()) && coordinator.admitting(op.destination().key()), "The queue entry locks both endpoints");
            assertEquals(EscrowCoordinator.Fill.STALE, coordinator.prepareFill(capture(op), ignored -> fail()), "A repeat is stale");
            assertTrue(coordinator.commitPreparations()); ledgerIo.tickUntil(coordinator::tick, 1, writer::idle);
            assertEquals(List.of(true), replies); assertEquals(List.of(op), coordinator.ledger().records());
            assertEquals(1, coordinator.admissionAuthority().pendingCount());
        }
    }

    @Test void anExistingLedgerBoundAtStartupAdmitsAFillAndABayInTheFirstTick() throws Exception {
        var authority = new EscrowAdmissionAuthority(); var delegate = authority.register(EscrowAdmissionAuthority.Consumer.PROPULSION);
        var bays = new ArrayList<EscrowAdmissionAuthority.Pending>();
        authority.registerIndex(EscrowAdmissionAuthority.Consumer.BAY, () -> new EscrowAdmissionAuthority.Index(true, bays));
        var durable = fill(1, 1); var data = loaded(EscrowTestFixtures.ledger(durable));
        // ServerStartingEvent's early bind attaches the ledger; the first tick has not run a coordinator or writer tick.
        try (var writer = new EscrowLedgerWriter(file("first-tick"), 0); var coordinator = new EscrowCoordinator(data, writer, delegate)) {
            assertEquals(EscrowAdmissionAuthority.Delegate.State.LOADED, delegate.state());
            assertEquals(EscrowCoordinator.Fill.QUEUED, coordinator.prepareFill(capture(fill(2, 2)), ignored -> { }));
            var bay = new EscrowAdmissionAuthority.Pending(EscrowTestFixtures.id(9_000), EscrowTestFixtures.id(900), EscrowTestFixtures.id(3),
                    EscrowAdmissionAuthority.Consumer.BAY);
            assertEquals(EscrowAdmissionAuthority.Result.RESERVED, authority.tryReserve(bay.op(), bay.owner(), bay.rocket(), bay.consumer()));
            bays.add(bay); assertEquals(3, authority.pendingCount()); assertEquals(3, coordinator.nextSequence());
        }
    }

    @Test void everyRefusalIsTypedAndChangesNothing() throws Exception {
        var root = EscrowTestFixtures.ledger(); var data = loaded(root);
        var bays = new ArrayList<EscrowAdmissionAuthority.Pending>();
        try (var writer = new EscrowLedgerWriter(file("refusals"), 0); var coordinator = new EscrowCoordinator(data, writer)) {
            var authority = coordinator.admissionAuthority();
            authority.registerIndex(EscrowAdmissionAuthority.Consumer.BAY, () -> new EscrowAdmissionAuthority.Index(true, bays));
            // INVALID: a fixture-kind binding, an incomplete native binding and a repair phase are not fresh native fills.
            var fixtureKind = EscrowTestFixtures.nativeModel(EscrowTestFixtures.operation(1), RocketEscrowSection.neutral());
            assertEquals(EscrowSignatureKind.FIXTURE_RESOURCE_V1, fixtureKind.nativeBinding().kind());
            var nativeOp = fill(1, 1); var b = nativeOp.nativeBinding();
            assertEquals(EscrowSignatureKind.FUEL_VECTOR_V1, b.kind()); assertTrue(b.complete());
            var incomplete = new EscrowOperation(nativeOp.id(), nativeOp.sequence(), nativeOp.owner(), nativeOp.createdTick(), nativeOp.phase(),
                    nativeOp.reason(), nativeOp.payload(), nativeOp.payloadHash(), nativeOp.source(), nativeOp.destination(), nativeOp.guard(),
                    nativeOp.kind(), nativeOp.settlement(), new EscrowNativeBinding(b.kind(), b.physical(), b.logical(), b.owner(), b.snapshot(),
                    b.snapshotHash(), b.invariant(), b.sourceReference(), b.destinationReference(), b.beforeWrapper(), b.afterWrapper(),
                    b.beforeFrontier(), b.afterFrontier(), b.sourceBefore(), b.sourceAfter(), b.destinationBefore(), b.destinationAfter(), false));
            for (var op : List.of(fixtureKind, incomplete, nativeOp.withReason(EscrowReason.OTHER_STATE))) {
                assertEquals(EscrowCoordinator.Fill.INVALID, coordinator.prepareFill(capture(op), ignored -> fail()));
            }
            assertUnchanged(coordinator, writer, data, root, 1, 0);
            // STALE: a sequence other than the next one, and a capture whose section names another ledger.
            assertEquals(EscrowCoordinator.Fill.STALE, coordinator.prepareFill(capture(fill(2, 1)), ignored -> fail()));
            var foreign = new RocketEscrowSection(EscrowTestFixtures.id(501), 0, List.of());
            var foreignOp = EscrowTestFixtures.nativeModel(EscrowSettlementCapacityTest.operation(1, 1, 0, true), foreign);
            assertEquals(EscrowCoordinator.Fill.STALE, coordinator.prepareFill(new EscrowNativeBinding.Admission(foreignOp, foreign), ignored -> fail()));
            // IDENTITY_CONFLICT, SERVER_FULL and ROCKET_FULL come from the shared authority, after the pure check.
            var op = fill(1, 1);
            bays.add(new EscrowAdmissionAuthority.Pending(op.id(), op.owner(), op.guard().rocket(), EscrowAdmissionAuthority.Consumer.BAY));
            assertEquals(EscrowCoordinator.Fill.IDENTITY_CONFLICT, coordinator.prepareFill(capture(op), ignored -> fail()));
            bays.clear();
            for (int i = 0; i < 8; i++) {
                bays.add(new EscrowAdmissionAuthority.Pending(EscrowTestFixtures.id(5_000 + i), EscrowTestFixtures.id(900), op.guard().rocket(),
                        EscrowAdmissionAuthority.Consumer.BAY));
            }
            assertEquals(EscrowCoordinator.Fill.ROCKET_FULL, coordinator.prepareFill(capture(op), ignored -> fail()));
            bays.clear();
            for (int i = 0; i < 64; i++) {
                bays.add(new EscrowAdmissionAuthority.Pending(EscrowTestFixtures.id(6_000 + i), EscrowTestFixtures.id(900),
                        EscrowTestFixtures.id(20_000 + i / 8), EscrowAdmissionAuthority.Consumer.BAY));
            }
            assertEquals(EscrowCoordinator.Fill.SERVER_FULL, coordinator.prepareFill(capture(op), ignored -> fail()));
            assertUnchanged(coordinator, writer, data, root, 1, 64);
            bays.clear();
            // UNAVAILABLE: a closed coordinator admits nothing.
            coordinator.close();
            assertEquals(EscrowCoordinator.Fill.UNAVAILABLE, coordinator.prepareFill(capture(op), ignored -> fail()));
            assertUnchanged(coordinator, writer, data, root, 1, 0);
        }
    }

    @Test void anArmedFlightHoldsTheRocketAgainstAFill() throws Exception {
        var root = EscrowFlightAuthority.arm(EscrowTestFixtures.ledger(), EscrowFlightAuthorityTest.launch(EscrowTestFixtures.ledger(), 1), 1);
        var data = loaded(root);
        try (var writer = new EscrowLedgerWriter(file("armed"), root.writerGeneration()); var coordinator = new EscrowCoordinator(data, writer)) {
            long next = coordinator.nextSequence();
            assertEquals(EscrowCoordinator.Fill.ENDPOINT_BUSY, coordinator.prepareFill(capture(fill(next, 201)), ignored -> fail()));
            assertUnchanged(coordinator, writer, data, root, next, 0);
            assertEquals(EscrowCoordinator.Fill.QUEUED, coordinator.prepareFill(capture(fill(next, 202)), ignored -> { }));
        }
    }

    @Test void retiredAndLiveMarkersCountOnceUpToFiveAndTheSixthIsRefused() throws Exception {
        for (int retired = 0; retired <= 5; retired++) {
            var markers = new ArrayList<EscrowPrefix.Marker>();
            for (int i = 1; i <= retired; i++) { markers.add(new EscrowPrefix.Marker(EscrowTestFixtures.id(5_000 + i), i, "DESTINATION", i)); }
            var root = new EscrowLedger(EscrowTestFixtures.id(500), 99, 0, List.of(), List.of(), List.of());
            var section = new RocketEscrowSection(root.id(), retired, markers);
            var op = EscrowTestFixtures.nativeModel(EscrowSettlementCapacityTest.operation(100, 99, retired, true), section);
            var data = loaded(root);
            // The same capture: a different section cannot even form the admission.
            assertThrows(IllegalArgumentException.class, () -> new EscrowNativeBinding.Admission(op, RocketEscrowSection.neutral()));
            try (var writer = new EscrowLedgerWriter(file("retired-" + retired), 0); var coordinator = new EscrowCoordinator(data, writer)) {
                var result = coordinator.prepareFill(new EscrowNativeBinding.Admission(op, section), ignored -> { });
                if (retired < 5) {
                    assertEquals(EscrowCoordinator.Fill.QUEUED, result); assertEquals(101, coordinator.nextSequence());
                } else {
                    assertEquals(EscrowCoordinator.Fill.ROCKET_LIMIT, result);
                    assertUnchanged(coordinator, writer, data, root, 100, 0);
                }
            }
        }
    }

    @Test void theFirstFillCheckRunsAgainstAVirtualEmptyRootWithoutCreatingAnything() {
        var empty = EscrowLedger.empty(EscrowTestFixtures.id(777));
        assertTrue(EscrowCoordinator.fillRefusal(empty, List.of(), capture(fill(1, 1))).isEmpty());
        var markers = new ArrayList<EscrowPrefix.Marker>();
        for (int i = 1; i <= 5; i++) { markers.add(new EscrowPrefix.Marker(EscrowTestFixtures.id(5_000 + i), i, "DESTINATION", i)); }
        var section = new RocketEscrowSection(empty.id(), 5, markers);
        var full = EscrowTestFixtures.nativeModel(EscrowSettlementCapacityTest.operation(1, 1, 5, true), section);
        assertEquals(java.util.Optional.of(EscrowCoordinator.Fill.ROCKET_LIMIT),
                EscrowCoordinator.fillRefusal(empty, List.of(), new EscrowNativeBinding.Admission(full, section)));
        assertEquals(EscrowLedger.empty(EscrowTestFixtures.id(777)), empty);
    }

    /**
     * Root-R6: with 29 retained floors the root admits its pinned 60 fills. The 61st would fit without the headroom,
     * so its refusal is the headroom alone; after L1, a floor retirement still passes.
     */
    @Test void r6HeadroomRefusesTheFillThatWouldTakeTheFloorRetirementAndTheRetirementThenPasses() throws Exception {
        var base = EscrowCombinedReservationTest.retainedFloors(29); var data = loaded(base); var ledgerIo = new AwaitedLedgerIo();
        try (var writer = ledgerIo.writer(file("r6"), 0, CheckedSavedDataFile::atomicMove); var coordinator = new EscrowCoordinator(data, writer)) {
            var admitted = new ArrayList<EscrowOperation>(); var replies = new ArrayList<Boolean>();
            EscrowOperation refused = null;
            for (int i = 0; i < 64 && refused == null; i++) {
                var op = fill(coordinator.nextSequence(), 40_000 + i);
                var result = coordinator.prepareFill(capture(op), replies::add);
                if (result == EscrowCoordinator.Fill.QUEUED) { admitted.add(op); } else { assertEquals(EscrowCoordinator.Fill.ESCROW_FULL, result); refused = op; }
            }
            assertEquals(60, admitted.size(), "The pinned capacity with 29 retained floors, unguarded");
            assertNotNull(refused);
            long next = coordinator.nextSequence(); assertEquals(refused.sequence(), next);
            assertEquals(60, coordinator.admissionAuthority().pendingCount()); assertTrue(writer.idle());
            assertTrue(writer.capturedOperations().isEmpty()); assertEquals(base, coordinator.ledger());
            var candidate = new ArrayList<>(admitted); candidate.add(refused);
            var prospective = base.append(candidate);
            assertDoesNotThrow(() -> EscrowSettlementCapacity.preflight(prospective), "Without the headroom the 61st fill would fit");
            assertThrows(IllegalArgumentException.class, () -> EscrowSettlementCapacity.preflight(prospective, admitted.get(0).id()));
            assertTrue(coordinator.commitPreparations());
            long tick = ledgerIo.tickUntil(coordinator::tick, 1, writer::idle);
            assertEquals(60, replies.size()); assertTrue(replies.stream().allMatch(Boolean::booleanValue));
            var root = coordinator.ledger(); assertEquals(admitted, root.records());
            tick += 210; coordinator.tick(tick);
            var results = new ArrayList<Boolean>();
            assertTrue(coordinator.beginSettlement(floorIntent(root, 0, tick), results::add), "The reserved headroom admits one floor retirement");
            ledgerIo.tickUntil(coordinator::tick, tick + 1, writer::idle);
            assertEquals(List.of(true), results); assertEquals(61, coordinator.ledger().records().size());
            assertEquals(61, coordinator.admissionAuthority().pendingCount());
        }
    }

    /**
     * Root-R8: with no floors the root admits its pinned 62 fills. Charged by its own preview only, the 63rd would fit
     * even with the headroom, so its refusal is the ceiling charge.
     */
    @Test void r8ChargingRefusesACandidateThatWouldFitByItsPreviewOnly() throws Exception {
        var base = EscrowTestFixtures.ledger(); var data = loaded(base);
        try (var writer = new EscrowLedgerWriter(file("r8"), 0); var coordinator = new EscrowCoordinator(data, writer)) {
            var admitted = new ArrayList<EscrowOperation>();
            EscrowOperation refused = null;
            for (int i = 0; i < 64 && refused == null; i++) {
                var op = fill(coordinator.nextSequence(), 40_000 + i);
                var result = coordinator.prepareFill(capture(op), ignored -> { });
                if (result == EscrowCoordinator.Fill.QUEUED) { admitted.add(op); } else { assertEquals(EscrowCoordinator.Fill.ESCROW_FULL, result); refused = op; }
            }
            assertEquals(62, admitted.size(), "The pinned capacity with no floors, unguarded");
            assertNotNull(refused);
            assertEquals(refused.sequence(), coordinator.nextSequence()); assertEquals(62, coordinator.admissionAuthority().pendingCount());
            assertEquals(base, coordinator.ledger()); assertTrue(writer.idle()); assertTrue(writer.capturedOperations().isEmpty());
            var candidate = new ArrayList<>(admitted); candidate.add(refused);
            var prospective = base.append(candidate);
            long ceiling = EscrowSettlementCapacity.reservedBytes(prospective);
            long previewOnly = ceiling - (EscrowLimits.OP_BYTES + 2_048L) + previewBytes(prospective, refused);
            assertTrue(ceiling + EscrowSettlementCapacity.FLOOR_RETIREMENT_BYTES > EscrowLimits.ROOT_BYTES);
            assertTrue(previewOnly + EscrowSettlementCapacity.FLOOR_RETIREMENT_BYTES <= EscrowLimits.ROOT_BYTES,
                    "Charged by its preview only, with the headroom, the candidate would fit: " + previewOnly);
            System.out.println("prepareFill R8 admitted=62 ceiling_bytes=" + ceiling + " preview_only_bytes=" + previewOnly
                    + " headroom=" + EscrowSettlementCapacity.FLOOR_RETIREMENT_BYTES + " limit=" + EscrowLimits.ROOT_BYTES);
        }
    }

    /** What the candidate's own preview measures, as EscrowSettlementCapacity.preview builds it: its settled operation and its receipt. */
    private static long previewBytes(EscrowLedger prospective, EscrowOperation candidate) {
        var component = EscrowSettlementIntent.component(prospective.records(), candidate.id());
        assertEquals(List.of(candidate), component, "The fixture's candidate is a component of its own");
        var chains = EscrowPrefix.chains(component); var observations = new ArrayList<EscrowSettlementIntent.Observation>();
        for (var endpoint : List.of(candidate.source(), candidate.destination())) {
            var chain = chains.get(endpoint.key()); var last = chain.get(chain.size() - 1);
            var markers = new ArrayList<>(chain.stream().map(EscrowPrefix.Link::marker).toList());
            long epoch = last.endpoint().afterEpoch();
            if (EscrowSignatureKind.endpoint(endpoint) == EscrowSignatureKind.FUEL_VECTOR_V1) {
                for (int i = markers.size(); i < EscrowSettlementCapacity.ADMISSION_PER_ROCKET; i++) {
                    markers.add(new EscrowPrefix.Marker(new UUID(-1, i), i + 1, "DESTINATION", i + 1));
                }
                epoch = Math.max(epoch, markers.size());
                for (int i = 0; i < markers.size(); i++) {
                    markers.set(i, new EscrowPrefix.Marker(markers.get(i).operation(), i + 1, "DESTINATION", epoch - markers.size() + i + 1));
                }
            }
            String invariant = endpoint.equals(candidate.destination()) ? candidate.nativeBinding().invariant() : EscrowInvariant.reference(endpoint);
            observations.add(new EscrowSettlementIntent.Observation(endpoint,
                    new EscrowPrefix.State(prospective.id(), epoch, last.endpoint().afterSignature(), markers), true,
                    Long.MAX_VALUE, EscrowSignatureKind.endpoint(endpoint), invariant));
        }
        var hypothetical = prospective.replace(prospective.records().stream().map(op -> op.id().equals(candidate.id())
                ? op.withReason(EscrowReason.OTHER_STATE) : op).toList(), prospective.floors(), prospective.tombstones());
        var preview = EscrowSettlementIntent.preview(hypothetical, candidate.id(), EscrowSettlementIntent.Choice.ACCEPT_CURRENT,
                new UUID(0, 1), "\0".repeat(128), 0, observations);
        var intent = preview.confirm(preview.actor(), preview.digest(), 0, observations);
        var member = hypothetical.records().stream().filter(op -> op.id().equals(candidate.id())).findFirst().orElseThrow();
        return listBytes(EscrowCodec.operation(member.settle(intent))) + listBytes(EscrowReceiptCodec.encode(EscrowSettlementTombstones.create(member, intent)));
    }
    private static int listBytes(net.minecraft.nbt.CompoundTag tag) { return RocketNbtSize.uncompressedBytes(tag) - 3; }
    private static EscrowSettlementIntent floorIntent(EscrowLedger root, int index, long tick) {
        var floor = root.floors().get(index);
        var original = EscrowTestFixtures.operation(index + 1, 10_001 + index, 0).destination();
        var endpoint = new EscrowEndpoint(original.kind(), original.level(), original.identity(), original.logicalRocket(),
                original.type(), original.x(), original.y(), original.z(), original.unit(), floor.getLong("resource_epoch"),
                floor.getString("signature"), floor.getString("signature"));
        var observations = List.of(new EscrowSettlementIntent.Observation(endpoint, null, false, 0));
        var preview = EscrowSettlementIntent.floorPreview(root, EscrowTestFixtures.id(30_000 + index), floor,
                EscrowTestFixtures.id(700), "operator", tick - 200, observations);
        return preview.confirm(preview.actor(), preview.digest(), tick, observations);
    }
}
