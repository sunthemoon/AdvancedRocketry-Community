package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import com.google.common.collect.ImmutableList;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlock;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlockState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketStats;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.TestDataStorage;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.UnstartedServer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** Calls the real audit entry points; no server, terrain, native proof or cargo mutation is simulated as passed. */
class RocketEscrowAuditDirectionTest {
    @TempDir Path directory;

    @BeforeAll static void bootstrap() { EscrowTestBootstrap.initialize(); }

    static Stream<EscrowOperation.Kind> kinds() {
        return Stream.of(EscrowOperation.Kind.FLUID_PROPULSION_FILL,
                EscrowOperation.Kind.FLUID_CARGO_LOAD, EscrowOperation.Kind.FLUID_CARGO_UNLOAD);
    }

    @ParameterizedTest @MethodSource("kinds")
    void observeCorrectPlacementDoesNotTransition(EscrowOperation.Kind kind) throws Exception {
        try (var f = fixture(operation(kind))) {
            RocketEscrowAudit.observe(f.rocket);
            f.unchanged();
            assertEquals(1, f.frozenSlot.get());
            assertEquals(1, f.deviceSlot.get());
        }
    }

    @ParameterizedTest @MethodSource("kinds")
    void observeSelectsMatchingLogicalIdentityEvenWithWrongPhysicalIdentity(EscrowOperation.Kind kind) throws Exception {
        try (var f = fixture(operation(kind))) {
            UnstartedServer.set(Entity.class, f.rocket, "uuid", EscrowTestFixtures.id(901));
            RocketEscrowAudit.observe(f.rocket);
            f.repair(EscrowReason.INVALID_ENDPOINT);
            assertEquals(3, f.frozenSlot.get());
            assertEquals(1, f.deviceSlot.get());
        }
    }

    @ParameterizedTest @MethodSource("kinds")
    void observeSelectsMatchingPhysicalIdentityEvenWithWrongLogicalIdentity(EscrowOperation.Kind kind) throws Exception {
        try (var f = fixture(operation(kind))) {
            UnstartedServer.set(RocketEntity.class, f.rocket, "assemblyTransactionId", EscrowTestFixtures.id(902));
            RocketEscrowAudit.observe(f.rocket);
            f.repair(EscrowReason.INVALID_ENDPOINT);
        }
    }

    @ParameterizedTest @MethodSource("kinds")
    void observeDoesNotSelectAnUnrelatedRocketWithTheDeviceUuid(EscrowOperation.Kind kind) throws Exception {
        try (var f = fixture(operation(kind))) {
            UnstartedServer.set(Entity.class, f.rocket, "uuid", f.op.device().identity());
            UnstartedServer.set(RocketEntity.class, f.rocket, "assemblyTransactionId", EscrowTestFixtures.id(902));
            RocketEscrowAudit.observe(f.rocket);
            f.unchanged();
            assertEquals(1, f.frozenSlot.get());
            assertEquals(1, f.deviceSlot.get());
        }
    }

    @ParameterizedTest @MethodSource("kinds")
    void observeRelocationInvalidatesFrozenAndLiveRocketSlots(EscrowOperation.Kind kind) throws Exception {
        try (var f = fixture(operation(kind))) {
            var live = f.relocate();
            RocketEscrowAudit.observe(f.rocket);
            f.repair(EscrowReason.RELOCATED_ENDPOINT);
            assertEquals(2, f.frozenSlot.get());
            assertEquals(2, live.get());
            assertEquals(1, f.deviceSlot.get());
        }
    }

    @ParameterizedTest @MethodSource("kinds")
    void observeManualAndBothSettlementsStaySticky(EscrowOperation.Kind kind) throws Exception {
        for (var op : sticky(operation(kind))) {
            try (var f = fixture(op)) {
                RocketEscrowAudit.observe(f.rocket);
                f.unchanged();
                var live = f.relocate();
                RocketEscrowAudit.observe(f.rocket);
                f.unchanged();
                assertEquals(2, f.frozenSlot.get());
                assertEquals(2, live.get());
                assertEquals(1, f.deviceSlot.get());
            }
        }
    }

    @ParameterizedTest @MethodSource("kinds")
    void attemptLooksUpRocketAndLeavesCorrectPlacementUnchanged(EscrowOperation.Kind kind) throws Exception {
        try (var f = fixture(operation(kind))) {
            assertEquals(new EscrowReconciliationScheduler.Result(EscrowReason.RECONCILIATION_UNIMPLEMENTED, false), f.attempt());
            assertEquals(List.of(f.op.rocket().identity()), f.level.lookups);
            f.unchanged();
            assertEquals(1, f.frozenSlot.get());
            assertEquals(1, f.deviceSlot.get());
        }
    }

    @ParameterizedTest @MethodSource("kinds")
    void attemptRejectsWrongIdentityAtTheRocketLookup(EscrowOperation.Kind kind) throws Exception {
        try (var f = fixture(operation(kind))) {
            UnstartedServer.set(RocketEntity.class, f.rocket, "assemblyTransactionId", EscrowTestFixtures.id(902));
            assertEquals(new EscrowReconciliationScheduler.Result(EscrowReason.INVALID_ENDPOINT, false), f.attempt());
            assertEquals(List.of(f.op.rocket().identity()), f.level.lookups);
            f.unchanged(); // The scheduler, not attempt(), owns persistence of this diagnosis.
            assertEquals(3, f.frozenSlot.get());
            assertEquals(1, f.deviceSlot.get());
        }
    }

    @ParameterizedTest @MethodSource("kinds")
    void attemptRelocationInvalidatesFrozenAndLiveRocketSlots(EscrowOperation.Kind kind) throws Exception {
        try (var f = fixture(operation(kind))) {
            var live = f.relocate();
            assertEquals(new EscrowReconciliationScheduler.Result(EscrowReason.RELOCATED_ENDPOINT, false), f.attempt());
            assertEquals(List.of(f.op.rocket().identity()), f.level.lookups);
            f.unchanged();
            assertEquals(2, f.frozenSlot.get());
            assertEquals(2, live.get());
            assertEquals(1, f.deviceSlot.get());
        }
    }

    @ParameterizedTest @MethodSource("kinds")
    void attemptAndSchedulerDoNotClearManualOrSettlingRecords(EscrowOperation.Kind kind) throws Exception {
        for (var op : sticky(operation(kind))) {
            try (var f = fixture(op)) {
                assertEquals(EscrowReconciliationScheduler.Result.deferred(), f.attempt());
                assertEquals(List.of(op.rocket().identity()), f.level.lookups);
                f.unchanged();
                f.relocate();
                assertEquals(new EscrowReconciliationScheduler.Result(EscrowReason.RELOCATED_ENDPOINT, false), f.attempt());
                f.unchanged();
                var scheduler = new EscrowReconciliationScheduler(f.data::ledger, f.coordinator::transition,
                        (record, records, done) -> fail("Sticky records must not enter automatic reconciliation"));
                scheduler.tick(0);
                f.unchanged();
            }
        }
    }

    @ParameterizedTest @MethodSource("kinds")
    void pruningChecksRocketIdentityAndKeepsManualConflicts(EscrowOperation.Kind kind) {
        var op = operation(kind);
        assertFalse(EscrowReconciliationPruning.rocketConflict(op, op.rocket().identity(), EscrowReason.NONE));
        assertTrue(EscrowReconciliationPruning.rocketConflict(op, op.device().identity(), EscrowReason.NONE));
        assertTrue(EscrowReconciliationPruning.rocketConflict(op, EscrowTestFixtures.id(999), EscrowReason.NONE));
        assertTrue(EscrowReconciliationPruning.rocketConflict(op, op.rocket().identity(), EscrowReason.OTHER_STATE));
    }

    private static EscrowOperation operation(EscrowOperation.Kind kind) {
        var op = kind.cargo() ? EscrowCargoFixtures.operation(kind, 1, 1) : EscrowTestFixtures.operation(1);
        var device = op.device();
        // Distinct coordinates expose accidental block-coordinate-as-entity-chunk invalidation.
        device = new EscrowEndpoint(device.kind(), device.level(), device.identity(), null, device.type(), 160, 64, 176,
                device.unit(), device.beforeEpoch(), device.beforeSignature(), device.afterSignature());
        boolean unload = kind == EscrowOperation.Kind.FLUID_CARGO_UNLOAD;
        var changed = new EscrowOperation(op.id(), op.sequence(), op.owner(), op.createdTick(), op.phase(), op.reason(), op.payload(),
                op.payloadHash(), unload ? op.rocket() : device, unload ? device : op.rocket(), op.guard(), kind, op.settlement());
        return kind.cargo() ? EscrowTestFixtures.nativeModel(changed, RocketEscrowSection.neutral()) : changed;
    }

    private static List<EscrowOperation> sticky(EscrowOperation op) {
        var result = new ArrayList<EscrowOperation>();
        result.add(op.withReason(EscrowReason.OTHER_STATE));
        op = op.withReason(EscrowReason.BARRIER_IO);
        var root = EscrowTestFixtures.ledger(op);
        for (int missing : List.of(0, 2)) {
            var observations = op.kind().cargo() ? EscrowCargoFixtures.observations(root, op, 0, missing, false)
                    : EscrowSettlementIntentTest.observations(root, missing != 0);
            var preview = EscrowSettlementIntent.preview(root, op.id(), missing == 0
                    ? EscrowSettlementIntent.Choice.ACCEPT_CURRENT : EscrowSettlementIntent.Choice.ABANDON,
                    EscrowTestFixtures.id(700), "operator", 10, observations);
            result.add(op.settle(preview.confirm(preview.actor(), preview.digest(), missing == 0 ? 11 : 210, observations)));
        }
        return result;
    }

    private Fixture fixture(EscrowOperation op) throws Exception {
        return new Fixture(op, Files.createTempDirectory(directory, "audit-"));
    }

    private static final class Fixture implements AutoCloseable {
        final EscrowOperation op;
        final MinecraftServer server = UnstartedServer.owned();
        final AuditLevel level = UnstartedServer.allocate(AuditLevel.class);
        final RocketEntity rocket = UnstartedServer.allocate(RocketEntity.class);
        final EscrowRuntime runtime = new EscrowRuntime();
        final ResourceEscrowSavedData data;
        final EscrowCoordinator coordinator;
        final AwaitedLedgerIo io = new AwaitedLedgerIo();
        final EscrowLedgerWriter writer;
        final RocketTransferSavedData journal = new RocketTransferSavedData();
        final Path file;
        final AtomicLong frozenSlot, deviceSlot;

        Fixture(EscrowOperation op, Path directory) throws Exception {
            this.op = op;
            // This fixture tests audit lookup/invalidation only; it intentionally has no terrain or native C2 executor.
            if (op.kind().cargo()) { runtime.cargoSwitches.pause(op.device().identity(), true); }
            level.server = server; level.storage = TestDataStorage.over(directory); level.key = Level.OVERWORLD;
            level.runtime = LazyOptional.of(() -> runtime); level.lookups = new ArrayList<>(); level.entities = new HashMap<>();
            UnstartedServer.set(MinecraftServer.class, server, "levels", Map.of(Level.OVERWORLD, level));
            var storage = UnstartedServer.allocate(LevelStorageSource.LevelStorageAccess.class);
            UnstartedServer.set(LevelStorageSource.LevelStorageAccess.class, storage, "resources", new HashMap<>());
            UnstartedServer.set(LevelStorageSource.LevelStorageAccess.class, storage, "levelDirectory", new LevelStorageSource.LevelDirectory(directory));
            UnstartedServer.set(MinecraftServer.class, server, "storageSource", storage);
            data = ResourceEscrowSavedData.load(EscrowCodec.encode(EscrowTestFixtures.ledger(op)));
            level.storage.set(ManagedSavedDataType.RESOURCE_ESCROW.dataName(), data);
            EscrowArmedRestartBoundaryTest.acknowledge(journal, directory.resolve(ManagedSavedDataType.ROCKET_TRANSFERS.fileName()));
            level.storage.set(ManagedSavedDataType.ROCKET_TRANSFERS.dataName(), journal);
            file = directory.resolve(ManagedSavedDataType.RESOURCE_ESCROW.fileName());
            writer = io.writer(file, 0, CheckedSavedDataFile::atomicMove);
            coordinator = new EscrowCoordinator(data, writer);
            data.attach(coordinator, () -> true);
            var origin = new RocketPosition(0, 64, 0);
            var snapshot = RocketStructureSnapshot.create(EscrowTestFixtures.id(900), Level.OVERWORLD.location(), origin,
                    List.of(new RocketBlock(new RocketPosition(0, 0, 0),
                            new RocketBlockState(ResourceLocation.tryParse("minecraft:iron_block"), Map.of()))),
                    List.of(), new RocketStats(1, 1, 0, 1_000, 0, 0, 0, 0), 0);
            UnstartedServer.set(RocketEntity.class, rocket, "snapshot", snapshot);
            UnstartedServer.set(RocketEntity.class, rocket, "assemblyTransactionId", op.rocket().logicalRocket());
            UnstartedServer.set(RocketEntity.class, rocket, "ownerId", op.owner());
            UnstartedServer.set(RocketEntity.class, rocket, "flightData", RocketFlightData.initial(op.rocket().logicalRocket(), 1_000, 0,
                    ResourceLocation.tryParse("advancedrocketrycommunity:earth"), Level.OVERWORLD.location(), origin, 0));
            UnstartedServer.set(RocketEntity.class, rocket, "escrowSection", RocketEscrowSection.neutral());
            UnstartedServer.set(Entity.class, rocket, "type", BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.tryParse(op.rocket().type())));
            UnstartedServer.set(Entity.class, rocket, "uuid", op.rocket().identity());
            UnstartedServer.set(Entity.class, rocket, "level", level);
            UnstartedServer.set(Entity.class, rocket, "passengers", ImmutableList.of());
            UnstartedServer.set(Entity.class, rocket, "position", new Vec3(.5, 64, .5));
            UnstartedServer.set(Entity.class, rocket, "chunkPosition", new ChunkPos(0, 0));
            level.entities.put(rocket.getUUID(), rocket);
            frozenSlot = runtime.monitor().watch(op.rocket().level(), true, new ChunkPos(0, 0));
            deviceSlot = runtime.monitor().watch(op.device().level(), true, new ChunkPos(op.device().x(), op.device().z()));
        }

        AtomicLong relocate() {
            var chunk = new ChunkPos(2, 3);
            UnstartedServer.set(Entity.class, rocket, "position", new Vec3(32.5, 64, 48.5));
            UnstartedServer.set(Entity.class, rocket, "chunkPosition", chunk);
            return runtime.monitor().watch(level.dimension().location().toString(), true, chunk);
        }

        EscrowReconciliationScheduler.Result attempt() {
            var result = new AtomicReference<EscrowReconciliationScheduler.Result>();
            RocketEscrowAudit.attempt(coordinator, server, op, data.ledger().records(), value -> {
                assertNull(result.getAndSet(value), "Audit completed twice");
            });
            assertNotNull(result.get());
            return result.get();
        }

        void unchanged() {
            assertEquals(op.reason(), coordinator.currentReason(op));
            assertEquals(EscrowCodec.encode(EscrowTestFixtures.ledger(op)), EscrowCodec.encode(data.ledger()));
            assertTrue(writer.idle()); assertFalse(data.isDirty()); assertFalse(Files.exists(file));
        }

        void repair(EscrowReason reason) {
            assertEquals(reason, coordinator.currentReason(op));
            io.tickUntil(coordinator::tick, 1, writer::idle);
            assertTrue(writer.idle()); assertFalse(data.isDirty());
            var restored = EscrowCodec.decode(CheckedSavedDataFile.readPayload(file, ManagedSavedDataType.RESOURCE_ESCROW).orElseThrow());
            assertEquals(List.of(op.withReason(reason)), restored.records());
            assertEquals(EscrowOperation.Phase.REPAIR, restored.records().get(0).phase());
        }

        @Override public void close() {
            coordinator.close(); writer.close(); journal.stop(); runtime.monitor().clear(); level.runtime.invalidate();
        }
    }

    /** Only world lookup/capability adapters are stubbed. Neither constructor nor a world-loading method runs. */
    private static final class AuditLevel extends ServerLevel {
        MinecraftServer server;
        DimensionDataStorage storage;
        ResourceKey<Level> key;
        LazyOptional<EscrowRuntime> runtime;
        List<UUID> lookups;
        Map<UUID, Entity> entities;
        AuditLevel() { super(null, null, null, null, null, null, null, false, 0, List.of(), false, null); }
        @Override public MinecraftServer getServer() { return server; }
        @Override public ResourceKey<Level> dimension() { return key; }
        @Override public DimensionDataStorage getDataStorage() { return storage; }
        @Override public Entity getEntity(UUID identity) { lookups.add(identity); return entities.get(identity); }
        @Override public <T> LazyOptional<T> getCapability(Capability<T> capability, Direction side) {
            return capability == EscrowRuntime.CAPABILITY ? runtime.cast() : LazyOptional.empty();
        }
    }
}
