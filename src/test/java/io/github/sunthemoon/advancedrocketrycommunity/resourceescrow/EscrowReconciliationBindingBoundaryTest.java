package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightPlanner;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFuelState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketFlightNbtCodec;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNativeResources;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPropulsion;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketSnapshotNbtCodec;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.GovernedTransferFixture;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** C34 diagnostic: real checked L2 with explicit A0 slot tokens, not native storage evidence. */
class EscrowReconciliationBindingBoundaryTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    @TempDir Path directory;

    @Test void partialL2PruningPreservesNativeResourcesButChangesSurvivingEnvelopeCommitments() throws Exception {
        var sample = GovernedTransferFixture.create(); var original = sample.record().sourceSnapshot();
        var descriptor = new RocketPropulsion(RocketPropulsion.Mode.MONO, 1_000, 0, 1, 1,
                RocketPropulsion.Settings.DEFAULTS.withAllowedFluids(Map.of(RocketPropulsion.Role.MONO, List.of("minecraft:water"),
                        RocketPropulsion.Role.BIPROPELLANT_FUEL, List.of(), RocketPropulsion.Role.OXIDIZER, List.of(),
                        RocketPropulsion.Role.WORKING_FLUID, List.of())));
        var snapshot = RocketStructureSnapshot.create(original.snapshotId(), original.sourceDimension(), original.sourceOrigin(),
                original.blocks(), original.passengerAnchors(), original.stats(), 0, descriptor);
        var ledgerId = EscrowTestFixtures.id(500);
        var before = sample.source().copy(); before.put("snapshot", RocketSnapshotNbtCodec.encode(snapshot));
        before.putString("content_hash", snapshot.contentHash()); before.put("passengers", new ListTag());
        var fuel0 = RocketFuelState.empty(descriptor);
        var flight = RocketFlightData.initial(before.getUUID("logical"), 1_000, 1, RocketFlightPlanner.EARTH.bodyId(),
                snapshot.sourceDimension(), snapshot.sourceOrigin(), 0).withFuel(fuel0, 0);
        before.put("flight", RocketFlightNbtCodec.encode(flight));
        before.put("wrapper", new RocketEscrowSection(ledgerId, 0, List.of()).encode());
        var fuel1 = fuel0.withFluidBank(false, ResourceLocation.tryParse("minecraft:water"), null, 100);
        var fuel2 = fuel1.withFluidBank(false, ResourceLocation.tryParse("minecraft:water"), null, 200);
        var markerA = new EscrowPrefix.Marker(EscrowTestFixtures.id(1), 1, "DESTINATION", 1);
        var markerB = new EscrowPrefix.Marker(EscrowTestFixtures.id(2), 2, "DESTINATION", 2);
        var first = before.copy(); first.put("flight", RocketFlightNbtCodec.encode(EscrowNativeEnvelope.fuelOnly(flight, fuel1)));
        first.put("wrapper", new RocketEscrowSection(ledgerId, 1, List.of(markerA)).encode());
        var after = before.copy(); after.put("flight", RocketFlightNbtCodec.encode(EscrowNativeEnvelope.fuelOnly(flight, fuel2)));
        after.put("wrapper", new RocketEscrowSection(ledgerId, 2, List.of(markerA, markerB)).encode());
        var a = operation(1, before, first, snapshot); var b = operation(2, first, after, snapshot);
        var ledger = new EscrowLedger(ledgerId, 2, 1, List.of(a, b), List.of(), List.of());
        var data = ResourceEscrowSavedData.load(EscrowCodec.encode(ledger));
        var io = new AwaitedLedgerIo();
        var file = directory.resolve(ManagedSavedDataType.RESOURCE_ESCROW.fileName());
        try (var writer = io.writer(file, 1, CheckedSavedDataFile::atomicMove);
             var coordinator = new EscrowCoordinator(data, writer)) {
            var proofs = new HashMap<EscrowEndpoint.Key, EscrowBarrier.Proof>();
            for (var chain : EscrowPrefix.chains(ledger.records()).values()) {
                var last = chain.get(chain.size() - 1);
                var state = new EscrowPrefix.State(ledgerId, last.endpoint().afterEpoch(), last.endpoint().afterSignature(),
                        chain.stream().map(EscrowPrefix.Link::marker).toList());
                coordinator.freeze(last.endpoint().key(), state);
                var capture = new EscrowChunkStore.Capture(CompletableFuture.completedFuture(null), null, directory,
                        new ChunkPos(0, 0), new AtomicLong(1), 1, 0, () -> { }, () -> { });
                proofs.put(last.endpoint().key(), new EscrowBarrier.Proof(state, capture, new AtomicBoolean(true), new AtomicBoolean(true)));
            }
            var replies = new ArrayList<Boolean>();
            assertTrue(coordinator.retire(Set.of(a.id()), proofs, replies::add));
            EscrowLedgerWriterTest.poll(io, writer, writer::idle);
            assertEquals(List.of(true), replies);
            var reloaded = EscrowCodec.decode(CheckedSavedDataFile.readPayload(file, ManagedSavedDataType.RESOURCE_ESCROW).orElseThrow());
            assertEquals(List.of(b), reloaded.records()); assertTrue(reloaded.tombstones().isEmpty());
            assertTrue(reloaded.floors().isEmpty()); assertTrue(reloaded.rocketAuthorities().isEmpty());
            assertFalse(proofs.get(b.destination().key()).valid(), "Partial L2 revokes the old shared frontier token");
            assertTrue(coordinator.locked(b.destination().key()));

            var prunedMarkers = EscrowSettlementPruning.prune(List.of(markerA, markerB), Set.of(a.id()), Set.of(b.id()));
            var prunedWrapper = new RocketEscrowSection(ledgerId, 2, prunedMarkers);
            var frontier = new EscrowPrefix.State(ledgerId, 2, fuel2.resourceSignature(), prunedMarkers);
            assertEquals(List.of(markerB), prunedMarkers);
            assertEquals(EscrowPrefix.Classification.AFTER,
                    EscrowPrefix.classify(ledgerId, EscrowPrefix.chains(reloaded.records()).get(b.destination().key()), frontier));
            var prunedEnvelope = after.copy(); prunedEnvelope.put("wrapper", prunedWrapper.encode());
            assertEquals(after.getCompound("flight"), prunedEnvelope.getCompound("flight"));
            assertEquals(after.getCompound("snapshot"), prunedEnvelope.getCompound("snapshot"));
            assertEquals(b.nativeBinding().afterWrapper(), EscrowNativeEnvelope.digest(after.getCompound("wrapper")));
            assertEquals(b.nativeBinding().destinationAfter(), EscrowNativeEnvelope.digest(after));
            assertNotEquals(b.nativeBinding().afterWrapper(), EscrowNativeEnvelope.digest(prunedWrapper.encode()));
            assertNotEquals(b.nativeBinding().destinationAfter(), EscrowNativeEnvelope.digest(prunedEnvelope));
            assertEquals(b.nativeBinding(), reloaded.records().get(0).nativeBinding(), "L2 does not rebind the remaining operation");
            assertEquals(b.nativeBinding().beforeWrapper(), EscrowNativeEnvelope.digest(first.getCompound("wrapper")));
            assertNotEquals(b.nativeBinding().beforeWrapper(), EscrowNativeEnvelope.digest(new RocketEscrowSection(ledgerId, 1, List.of()).encode()));
        }
    }

    private static EscrowOperation operation(long sequence, CompoundTag before, CompoundTag after, RocketStructureSnapshot snapshot) {
        var ledger = RocketEscrowSection.decode(before.getCompound("wrapper")).ledger();
        var sourceBefore = source(sequence, ledger, false); var sourceAfter = source(sequence, ledger, true);
        var fromState = EscrowTestEndpoints.state(sourceBefore.getCompound("ForgeData"), ledger, false);
        var toState = EscrowTestEndpoints.state(sourceAfter.getCompound("ForgeData"), ledger, false);
        var source = new EscrowEndpoint(EscrowEndpoint.Kind.BLOCK_ENTITY, before.getString("level"), EscrowTestFixtures.id(1_000 + sequence), null,
                "minecraft:chest", (int) sequence, 64, 0, EscrowEndpoint.Unit.TANK, 0, fromState.signature(), toState.signature());
        var beforeFuel = RocketFlightNbtCodec.decode(before.getCompound("flight")).data().orElseThrow().fuel();
        var afterFuel = RocketFlightNbtCodec.decode(after.getCompound("flight")).data().orElseThrow().fuel();
        var chunk = before.getIntArray("chunk");
        var destination = new EscrowEndpoint(EscrowEndpoint.Kind.ENTITY, before.getString("level"), before.getUUID("physical"), before.getUUID("logical"),
                EscrowAdapterIds.ROCKET, chunk[0], 0, chunk[1], EscrowEndpoint.Unit.PRIMARY, sequence - 1,
                beforeFuel.resourceSignature(), afterFuel.resourceSignature());
        var owner = before.getUUID("owner");
        var binding = new EscrowNativeBinding(EscrowSignatureKind.FUEL_VECTOR_V1, destination.identity(), destination.logicalRocket(), owner,
                snapshot.snapshotId(), snapshot.contentHash(), EscrowInvariant.rocket(destination, owner, snapshot),
                EscrowNativeBinding.reference(source), EscrowNativeBinding.reference(destination),
                EscrowNativeEnvelope.digest(before.getCompound("wrapper")), EscrowNativeEnvelope.digest(after.getCompound("wrapper")),
                beforeFuel.resourceSignature(), afterFuel.resourceSignature(), EscrowNativeEnvelope.digest(sourceBefore), EscrowNativeEnvelope.digest(sourceAfter),
                EscrowNativeEnvelope.digest(before), EscrowNativeEnvelope.digest(after), true);
        var payload = new CompoundTag(); payload.putString("FluidName", "minecraft:water"); payload.putInt("Amount", 100);
        return new EscrowOperation(EscrowTestFixtures.id(sequence), sequence, owner, 0, EscrowOperation.Phase.PREPARED, EscrowReason.NONE,
                payload, RocketNativeResources.signature(payload), source, destination,
                new EscrowGuard(destination.logicalRocket(), null, 1, EscrowTestFixtures.hash(400)),
                EscrowOperation.Kind.FLUID_PROPULSION_FILL, new CompoundTag(), binding);
    }

    private static CompoundTag source(long sequence, java.util.UUID ledger, boolean applied) {
        var identity = EscrowTestFixtures.id(1_000 + sequence); int amount = applied ? 4_900 : 5_000;
        var fixture = new CompoundTag(); fixture.putUUID("identity", identity); fixture.putInt("amount", amount); fixture.putInt("capacity", 16_000);
        var persistent = new CompoundTag(); persistent.put("arce_escrow_fixture", fixture);
        EscrowTestEndpoints.putState(persistent, new EscrowPrefix.State(ledger, applied ? 1 : 0,
                RocketNativeResources.hash("arce-escrow-fixture-1:" + identity + ":" + amount),
                applied ? List.of(new EscrowPrefix.Marker(EscrowTestFixtures.id(sequence), sequence, "SOURCE", 1)) : List.of()), false);
        var tag = new CompoundTag(); tag.putString("id", "minecraft:chest"); tag.putInt("x", (int) sequence); tag.putInt("y", 64); tag.putInt("z", 0);
        tag.put("ForgeData", persistent); return tag;
    }
}
