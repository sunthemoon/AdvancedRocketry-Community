package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static io.github.sunthemoon.advancedrocketrycommunity.rocket.server.BayConsumptionFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

import com.google.common.collect.ImmutableList;
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
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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

    private void check(boolean ready, boolean supported, boolean available, boolean duplicate, String reason) {
        var before = record(1);
        var job = BayConsumedSnapshots.prepare(before.sourceSnapshot(), before.destinationSnapshot(), Map.of(bay(0), intent(before, 0)))
                .orElseThrow();
        var budget = new RocketCargoWorkBudget();
        for (long tick = 1; !job.step(budget, tick); tick++) { }
        var result = job.result().orElseThrow();
        var record = before.withConsumedSnapshots(result.source(), result.destination());
        var member = BayMember.restartRegistered(registered(intent(before, 0), result.source().contentHash(), result.destination().contentHash()));
        var group = BayGroup.resumed(LOGICAL, TRANSFER, List.of(member)).orElseThrow();
        var claim = BayClaim.restart(LOGICAL, TRANSFER, group.members());
        claim.freeze(group); claim.establishSource(SOURCE); claim.advance(BayClaim.Stage.CONSUME); claim.advance(BayClaim.Stage.PROVE);

        var server = UnstartedServer.owned();
        var access = UnstartedServer.allocate(LevelStorageSource.LevelStorageAccess.class);
        UnstartedServer.set(LevelStorageSource.LevelStorageAccess.class, access, "resources", new HashMap<>());
        UnstartedServer.set(LevelStorageSource.LevelStorageAccess.class, access, "levelDirectory", new LevelStorageSource.LevelDirectory(directory));
        UnstartedServer.set(MinecraftServer.class, server, "storageSource", access);
        var sourceLevel = level(server, Level.OVERWORLD, true);
        var destination = level(server, ResourceKey.create(Registries.DIMENSION, record.destinationSnapshot().sourceDimension()), ready);
        UnstartedServer.set(MinecraftServer.class, server, "levels", Map.of(sourceLevel.key, sourceLevel, destination.key, destination));
        var journal = new RocketTransferSavedData(); journal.put(record);
        journal.save(directory.resolve(RocketTransferSavedData.DATA_NAME + ".dat").toFile());
        RocketJournalTestAccess.awaitWriter(journal);
        assertTrue(journal.actionable(LOGICAL));
        sourceLevel.storage.set(RocketTransferSavedData.DATA_NAME, journal);
        var source = rocket(sourceLevel, record, SOURCE);
        // Deliberately omit Entity.type: all projection checks pass, but Entity.save itself trips this trap.
        // This avoids a Forge entity constructor in plain JUnit and detects any premature serialization.
        var trap = assertThrows(NullPointerException.class, () -> BaySourceProof.projection(source, record));
        assertTrue(java.util.Arrays.stream(trap.getStackTrace()).anyMatch(frame ->
                frame.getClassName().equals(Entity.class.getName()) && frame.getMethodName().equals("save")),
                "control: the serialization trap must come from the real Entity.save, not a projection check");
        if (duplicate) { rocket(sourceLevel, record, new UUID(185, 62)); }

        var proof = new BaySourceProof(server, null, ignored -> { }, (held, op) -> { }, held -> false, (held, guard) -> guard);
        UnstartedServer.set(BaySourceProof.class, proof, "port", new RocketRetirementWorld(server, null) {
            @Override public boolean captureSupported(RocketRetirementProof.Slot slot) { return supported; }
            @Override public boolean captureAvailable(RocketRetirementProof.Slot slot, long now) { return available; }
            @Override public void retryNextTick(Runnable retry) { }
        });
        try {
            for (int tick = 0; tick < 3; tick++) {
                assertEquals(available ? BayStepQueue.Result.NEXT_TICK : BayStepQueue.Result.IDLE,
                        assertDoesNotThrow(() -> proof.prove(claim), "a waiting tick must not call Entity.save"));
                assertEquals(reason, claim.reason());
            }
        } finally { journal.abort(); }
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
