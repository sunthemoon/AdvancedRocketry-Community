package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static io.github.sunthemoon.advancedrocketrycommunity.rocket.server.BayConsumptionFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

import com.google.common.collect.ImmutableList;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowBarrier;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowChunkStore;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.RocketEscrowSection;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.deployment.BayClaim;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.deployment.BayGroup;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.deployment.BayMember;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.deployment.BayStepQueue;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.RocketCargoWorkBudget;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketJournalTestAccess;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.TestDataStorage;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.UnstartedServer;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityLookup;
import net.minecraft.world.level.entity.EntitySectionStorage;
import net.minecraft.world.level.entity.LevelEntityGetter;
import net.minecraft.world.level.entity.LevelEntityGetterAdapter;
import net.minecraft.world.level.entity.Visibility;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** W185 / W170-R62: D6's waits must not serialize the source on every tick. */
final class BaySourceProofWaitTest {
    @BeforeAll static void bootstrap() throws Exception {
        MinecraftBootstrap.initialize();
        // Reuse the existing plain-JUnit capability bootstrap before resolving EscrowRuntime.
        var bootstrap = Class.forName("io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.RocketEscrowAuditDirectionTest")
                .getDeclaredMethod("bootstrap");
        bootstrap.setAccessible(true); bootstrap.invoke(null);
    }
    @TempDir Path directory;

    @Test void loadingEndDoesNotSaveTheSource() { check(false, true, true, false, "END_LOADING"); }
    @Test void unsupportedBarrierDoesNotSaveTheSource() { check(true, false, true, false, "BARRIER_UNSUPPORTED"); }
    @Test void unavailableCaptureDoesNotSaveTheSource() { check(true, true, false, false, "BAY_SOURCE_PROOF"); }
    @Test void aRecognizedDuplicateWaitDoesNotSaveTheSource() { check(true, true, true, true, "DUPLICATE_PENDING"); }

    /** W185-R9: D7 checks for a quiet receipt store before it serializes the source, so a busy store's wait saves nothing. */
    @Test void aBusyStoreAtD7DoesNotSaveTheSource() throws ReflectiveOperationException {
        try (var f = new Fixture(true, true, true, false)) {
            f.retiring(); f.busy = true;
            for (int tick = 0; tick < 3; tick++) {
                // The coordinator's dispatch: D7 at RETIRE, D6 at PROVE once a busy store parks the claim (W185-R5).
                assertDoesNotThrow(() -> f.claim.stage() == BayClaim.Stage.RETIRE ? f.proof.retire(f.claim) : f.proof.prove(f.claim),
                        "a busy store's tick must not call Entity.save");
            }
        }
    }

    /**
     * W185-R5: D7 parked on a busy store releases its slot proofs and watch and returns to D6, which captures nothing and
     * saves nothing until the store is quiet, then proves again. A parked claim holds no save-monitor slot.
     */
    @Test void aParkedRetirementHoldsNoSlotProofAndProvesAgainOnceTheStoreIsQuiet() throws ReflectiveOperationException {
        try (var f = new Fixture(true, true, true, false)) {
            f.retiring(); f.busy = true;
            assertEquals(BayStepQueue.Result.AGAIN, assertDoesNotThrow(() -> f.proof.retire(f.claim)));
            assertEquals(BayClaim.Stage.PROVE, f.claim.stage(), "the parked claim returns to D6");
            assertEquals(1, f.released.get(), "the parked claim still holds its slot proof");
            assertTrue(f.<Map<?, ?>>field("proofs").isEmpty() && f.<Map<?, ?>>field("watches").isEmpty(), "proof or watch kept");
            for (int tick = 0; tick < 3; tick++) {
                assertEquals(BayStepQueue.Result.NEXT_TICK, assertDoesNotThrow(() -> f.proof.prove(f.claim), "a parked tick saved the source"));
                assertEquals("BAY_RECEIPT_WRITE", f.claim.reason());
            }
            assertTrue(f.captures.isEmpty(), "a parked claim captured a slot");
            f.busy = false;
            assertSaveTrap(assertThrows(NullPointerException.class, () -> f.proof.prove(f.claim),
                    "once the store is quiet, D6 must serialize the source to prove again"));
        }
    }

    /**
     * W185-R11: D6 reads D5's one census and rule set, so an image of L in the end's box but off the exact origin column is
     * an unexpected copy held for repair, never a recognized duplicate that D6 would wait on.
     */
    @Test void d6ReadsTheCopyCensusRuleSetOfD5() {
        try (var f = new Fixture(true, true, true, false)) {
            var offColumn = rocket(f.sourceLevel, f.record, new UUID(185, 63));
            UnstartedServer.set(Entity.class, offColumn, "position", new Vec3(EARTH.x() + 1.5, EARTH.y() + 48, EARTH.z() + .5));
            assertDoesNotThrow(() -> f.proof.prove(f.claim), "a census refusal must not call Entity.save");
            assertEquals(BayClaim.Hold.REPAIR, f.claim.hold());
            assertEquals("UNEXPECTED_COPY", f.claim.reason());
        }
    }

    private void check(boolean ready, boolean supported, boolean available, boolean duplicate, String reason) {
        try (var f = new Fixture(ready, supported, available, duplicate)) {
            for (int tick = 0; tick < 3; tick++) {
                assertEquals(available ? BayStepQueue.Result.NEXT_TICK : BayStepQueue.Result.IDLE,
                        assertDoesNotThrow(() -> f.proof.prove(f.claim), "a waiting tick must not call Entity.save"));
                assertEquals(reason, f.claim.reason());
            }
        }
    }

    /** A restart claim in PROVE over T', its trapped source, the real D6/D7 class and a stand-in barrier port. */
    private final class Fixture implements AutoCloseable {
        final RocketTransferRecord record;
        final BayClaim claim;
        final RocketTransferSavedData journal;
        final ProbeLevel sourceLevel;
        final RocketEntity source;
        final BaySourceProof proof;
        final List<RocketRetirementProof.Slot> captures = new ArrayList<>();
        final AtomicInteger released = new AtomicInteger();
        boolean busy;

        Fixture(boolean ready, boolean supported, boolean available, boolean duplicate) {
            var before = record(1);
            var job = BayConsumedSnapshots.prepare(before.sourceSnapshot(), before.destinationSnapshot(), Map.of(bay(0), intent(before, 0)))
                    .orElseThrow();
            var budget = new RocketCargoWorkBudget();
            for (long tick = 1; !job.step(budget, tick); tick++) { }
            var result = job.result().orElseThrow();
            record = before.withConsumedSnapshots(result.source(), result.destination());
            var member = BayMember.restartRegistered(registered(intent(before, 0), result.source().contentHash(), result.destination().contentHash()));
            var group = BayGroup.resumed(LOGICAL, TRANSFER, List.of(member)).orElseThrow();
            claim = BayClaim.restart(LOGICAL, TRANSFER, group.members());
            claim.freeze(group); claim.establishSource(SOURCE); claim.advance(BayClaim.Stage.CONSUME); claim.advance(BayClaim.Stage.PROVE);

            var server = UnstartedServer.owned();
            var access = UnstartedServer.allocate(LevelStorageSource.LevelStorageAccess.class);
            UnstartedServer.set(LevelStorageSource.LevelStorageAccess.class, access, "resources", new HashMap<>());
            UnstartedServer.set(LevelStorageSource.LevelStorageAccess.class, access, "levelDirectory", new LevelStorageSource.LevelDirectory(directory));
            UnstartedServer.set(MinecraftServer.class, server, "storageSource", access);
            sourceLevel = level(server, Level.OVERWORLD, true);
            var destination = level(server, ResourceKey.create(Registries.DIMENSION, record.destinationSnapshot().sourceDimension()), ready);
            UnstartedServer.set(MinecraftServer.class, server, "levels", Map.of(sourceLevel.key, sourceLevel, destination.key, destination));
            journal = new RocketTransferSavedData(); journal.put(record);
            journal.save(directory.resolve(RocketTransferSavedData.DATA_NAME + ".dat").toFile());
            RocketJournalTestAccess.awaitWriter(journal);
            assertTrue(journal.actionable(LOGICAL));
            sourceLevel.storage.set(RocketTransferSavedData.DATA_NAME, journal);
            source = rocket(sourceLevel, record, SOURCE);
            // Deliberately omit Entity.type: all projection checks pass, but Entity.save itself trips this trap.
            // This avoids a Forge entity constructor in plain JUnit and detects any premature serialization.
            assertSaveTrap(assertThrows(NullPointerException.class, () -> BaySourceProof.projection(source, record)));
            if (duplicate) { rocket(sourceLevel, record, new UUID(185, 62)); }

            proof = new BaySourceProof(server, null, ignored -> { }, (held, op) -> { }, held -> false, (held, guard) -> guard);
            UnstartedServer.set(BaySourceProof.class, proof, "port", new RocketRetirementWorld(server, null) {
                @Override public boolean captureSupported(RocketRetirementProof.Slot slot) { return supported; }
                @Override public boolean captureAvailable(RocketRetirementProof.Slot slot, long now) { return available; }
                @Override public void retryNextTick(Runnable retry) { }
                @Override public boolean capture(RocketRetirementProof.Slot slot, Predicate<CompoundTag> validator, long now,
                                                 Consumer<String> sink, Consumer<EscrowBarrier.Result> callback) {
                    captures.add(slot); return true;
                }
            });
            UnstartedServer.set(BaySourceProof.class, proof, "storeWaits", (BooleanSupplier) () -> busy);
        }

        /** D6's outcome as {@code captured} leaves it: RETIRE with one valid held slot proof and the source watched. */
        void retiring() throws ReflectiveOperationException {
            claim.advance(BayClaim.Stage.RETIRE);
            var generation = new AtomicLong(1);
            var capture = new EscrowChunkStore.Capture(CompletableFuture.completedFuture(null), null, Path.of("."),
                    new ChunkPos(EARTH.x() >> 4, EARTH.z() >> 4), generation, generation.get(), 1, () -> { }, released::incrementAndGet);
            var slots = new BaySlotProofs.Proof(List.of(new EscrowBarrier.Proof(null, capture, new AtomicBoolean(true), new AtomicBoolean(true))));
            var constructor = Class.forName(BaySourceProof.class.getName() + "$Proved").getDeclaredConstructors()[0];
            constructor.setAccessible(true);
            BooleanSupplier current = () -> true;
            Map<UUID, Object> proofs = field("proofs");
            proofs.put(LOGICAL, constructor.newInstance(slots, current, current, new CompoundTag()));
            proof.watch(claim, source);
        }

        @SuppressWarnings("unchecked")
        <T> T field(String name) throws ReflectiveOperationException {
            var field = BaySourceProof.class.getDeclaredField(name); field.setAccessible(true);
            return (T) field.get(proof);
        }

        @Override public void close() { journal.abort(); }
    }

    private static void assertSaveTrap(NullPointerException trap) {
        assertTrue(java.util.Arrays.stream(trap.getStackTrace()).anyMatch(frame ->
                frame.getClassName().equals(Entity.class.getName()) && frame.getMethodName().equals("save")),
                "control: the serialization trap must come from the real Entity.save, not a projection check");
    }

    private ProbeLevel level(MinecraftServer server, ResourceKey<Level> key, boolean ready) {
        var level = UnstartedServer.allocate(ProbeLevel.class);
        level.key = key; level.ready = ready; level.storage = TestDataStorage.over(directory.resolve(key.location().getPath()));
        try {
            var type = Class.forName("io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowRuntime");
            Object runtime = type.getConstructor().newInstance();
            level.capability = (Capability<?>) type.getField("CAPABILITY").get(null);
            level.runtime = LazyOptional.of(() -> runtime);
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
        level.lookup = new EntityLookup<>(); level.sections = new EntitySectionStorage<>(Entity.class, chunk -> Visibility.TICKING);
        level.getter = new LevelEntityGetterAdapter<>(level.lookup, level.sections);
        UnstartedServer.set(ServerLevel.class, level, "server", server);
        return level;
    }

    private static RocketEntity rocket(ProbeLevel level, io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord record,
                                       UUID id) {
        var rocket = UnstartedServer.allocate(RocketEntity.class);
        UnstartedServer.set(Entity.class, rocket, "uuid", id);
        UnstartedServer.set(RocketEntity.class, rocket, "assemblyTransactionId", LOGICAL);
        UnstartedServer.set(RocketEntity.class, rocket, "ownerId", OWNER);
        UnstartedServer.set(RocketEntity.class, rocket, "snapshot", record.sourceSnapshot());
        UnstartedServer.set(RocketEntity.class, rocket, "flightData", record.sourceFlightData());
        UnstartedServer.set(RocketEntity.class, rocket, "escrowSection", RocketEscrowSection.neutral());
        UnstartedServer.set(Entity.class, rocket, "level", level);
        UnstartedServer.set(Entity.class, rocket, "passengers", ImmutableList.of());
        UnstartedServer.set(Entity.class, rocket, "position", new Vec3(EARTH.x() + .5, EARTH.y() + 48, EARTH.z() + .5));
        UnstartedServer.set(Entity.class, rocket, "blockPosition", new BlockPos(EARTH.x(), EARTH.y() + 48, EARTH.z()));
        UnstartedServer.set(Entity.class, rocket, "chunkPosition", new ChunkPos(EARTH.x() >> 4, EARTH.z() >> 4));
        UnstartedServer.set(Entity.class, rocket, "bb", new AABB(EARTH.x(), EARTH.y() + 48, EARTH.z(),
                EARTH.x() + 1, EARTH.y() + 49, EARTH.z() + 1));
        UnstartedServer.set(Entity.class, rocket, "isAddedToWorld", true);
        level.lookup.add(rocket); level.sections.getOrCreateSection(SectionPos.asLong(rocket.blockPosition())).add(rocket);
        return rocket;
    }

    static final class ProbeLevel extends ServerLevel {
        ResourceKey<Level> key;
        boolean ready;
        DimensionDataStorage storage;
        EntityLookup<Entity> lookup;
        EntitySectionStorage<Entity> sections;
        LevelEntityGetter<Entity> getter;
        Capability<?> capability;
        LazyOptional<Object> runtime;
        ProbeLevel() { super(null, null, null, null, null, null, null, false, 0, List.of(), false, null); }
        @Override public ResourceKey<Level> dimension() { return key; }
        @Override public DimensionDataStorage getDataStorage() { return storage; }
        @Override public boolean areEntitiesLoaded(long chunk) { return ready; }
        @Override public boolean isPositionEntityTicking(BlockPos pos) { return ready; }
        @Override public int getMinBuildHeight() { return -64; }
        @Override public int getHeight() { return 384; }
        @Override public LevelEntityGetter<Entity> getEntities() { return getter; }
        @Override public Entity getEntity(UUID id) { return lookup.getEntity(id); }
        @Override public <T> LazyOptional<T> getCapability(Capability<T> requested, Direction side) {
            return requested == capability ? runtime.cast() : LazyOptional.empty();
        }
    }
}
