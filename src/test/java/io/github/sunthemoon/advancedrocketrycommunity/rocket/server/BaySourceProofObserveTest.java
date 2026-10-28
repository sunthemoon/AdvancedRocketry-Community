package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static io.github.sunthemoon.advancedrocketrycommunity.rocket.server.BayConsumptionFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

import com.google.common.collect.ImmutableList;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.deployment.BayClaim;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.deployment.BayGroup;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.deployment.BayMember;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.RocketCargoWorkBudget;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.UnstartedServer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityInLevelCallback;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * W170-R46 A0: a vanilla chunk unload removes an entity through {@code setRemoved(UNLOADED_TO_CHUNK)}, which bypasses
 * {@code RocketEntity.remove} and its bay hook. The coordinator's per-tick {@code BaySourceProof.observe} must still see
 * the watched source leave and invalidate its group's D6/D7 token, once.
 */
final class BaySourceProofObserveTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    private static BayGroup group(RocketTransferRecord record) {
        var job = BayConsumedSnapshots.prepare(record.sourceSnapshot(), record.destinationSnapshot(),
                Map.of(bay(0), intent(record, 0))).orElseThrow();
        var budget = new RocketCargoWorkBudget();
        for (long tick = 1; !job.step(budget, tick); tick++) { }
        var result = job.result().orElseThrow();
        var receipt = registered(intent(record, 0), result.source().contentHash(), result.destination().contentHash());
        return BayGroup.resumed(LOGICAL, TRANSFER, List.of(BayMember.restartRegistered(receipt))).orElseThrow();
    }

    @Test void aSourceUnloadedThroughSetRemovedIsSeenByThePerTickObservation() {
        var record = record(1);
        var group = group(record);
        var claim = BayClaim.restart(LOGICAL, TRANSFER, group.members());
        claim.freeze(group);

        var server = UnstartedServer.owned();
        var level = UnstartedServer.allocate(TestLevel.class);
        level.key = ResourceKey.create(Registries.DIMENSION, record.sourceSnapshot().sourceDimension());
        UnstartedServer.set(MinecraftServer.class, server, "levels", Map.of(level.key, level));
        var origin = record.sourceSnapshot().sourceOrigin();
        var source = UnstartedServer.allocate(RocketEntity.class);
        UnstartedServer.set(RocketEntity.class, source, "assemblyTransactionId", LOGICAL);
        UnstartedServer.set(RocketEntity.class, source, "ownerId", OWNER);
        UnstartedServer.set(RocketEntity.class, source, "snapshot", record.sourceSnapshot());
        UnstartedServer.set(RocketEntity.class, source, "flightData", record.sourceFlightData());
        UnstartedServer.set(Entity.class, source, "uuid", SOURCE);
        UnstartedServer.set(Entity.class, source, "level", level);
        UnstartedServer.set(Entity.class, source, "passengers", ImmutableList.of());
        UnstartedServer.set(Entity.class, source, "position", new Vec3(origin.x() + .5, origin.y() + 48, origin.z() + .5));
        UnstartedServer.set(Entity.class, source, "blockPosition", new BlockPos(origin.x(), origin.y() + 48, origin.z()));
        UnstartedServer.set(Entity.class, source, "chunkPosition", new ChunkPos(origin.x() >> 4, origin.z() >> 4));
        UnstartedServer.set(Entity.class, source, "bb",
                new AABB(origin.x(), origin.y() + 48, origin.z(), origin.x() + 1, origin.y() + 49, origin.z() + 1));
        UnstartedServer.set(Entity.class, source, "levelCallback", EntityInLevelCallback.NULL);
        UnstartedServer.set(Entity.class, source, "isAddedToWorld", true);
        level.source = source;
        assertTrue(RocketTransferEntities.loadedEntity(source), "control: the source is a loaded world entity");

        var offered = new ArrayList<UUID>();
        var proof = new BaySourceProof(server, null, offered::add, (held, op) -> { }, held -> false, (held, guard) -> guard);
        var token = group.token();
        proof.watch(claim, source); // as D6's capture starts it
        proof.observe();
        assertTrue(token.getAsBoolean() && offered.isEmpty(), "control: an unchanged watched source keeps its token");

        source.setRemoved(Entity.RemovalReason.UNLOADED_TO_CHUNK); // the chunk unload's path, not RocketEntity.remove
        assertTrue(source.isRemoved() && !RocketTransferEntities.loadedEntity(source), "control: the source unloaded");
        assertTrue(token.getAsBoolean() && offered.isEmpty(), "control: setRemoved alone reports nothing to the coordinator");

        proof.observe(); // the next coordinator tick
        assertFalse(token.getAsBoolean(), "the per-tick observation must invalidate the group's D6/D7 token");
        assertEquals(List.of(LOGICAL), offered, "the claim must be offered once to repeat D6");
        proof.observe();
        assertEquals(1, offered.size(), "the watch ends with its invalidation");
    }

    static final class TestLevel extends ServerLevel {
        ResourceKey<Level> key;
        RocketEntity source;
        TestLevel() { super(null, null, null, null, null, null, null, false, 0, List.of(), false, null); }
        @Override public ResourceKey<Level> dimension() { return key; }
        @Override public boolean areEntitiesLoaded(long chunk) { return true; }
        /** The loaded index: an unloaded entity has left it. */
        @Override public Entity getEntity(UUID id) {
            return source != null && source.getUUID().equals(id) && !source.isRemoved() ? source : null;
        }
    }
}
