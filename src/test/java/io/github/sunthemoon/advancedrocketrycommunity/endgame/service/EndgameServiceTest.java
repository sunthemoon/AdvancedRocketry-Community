package io.github.sunthemoon.advancedrocketrycommunity.endgame.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.endgame.device.EndgameDeviceTags;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameCode;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameSettings;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.root.EndgameSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.root.EndpointRecord;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.root.Tombstone;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

/** ADR-054 sections 2, 9 and 11: chunk-tag observations, MISSING, tombstone settlement and the index filter. */
class EndgameServiceTest {
    private static final String TYPE = "advancedrocketrycommunity:laser_target";
    private static final ResourceLocation KIND = ResourceLocation.tryBuild("advancedrocketrycommunity", "laser_target");
    private static final ResourceLocation LEVEL = ResourceLocation.tryBuild("minecraft", "overworld");
    private static final UUID OWNER = new UUID(1L, 1L);
    private static final BlockPos POS = new BlockPos(20, 64, 20);
    private static final long CHUNK = new ChunkPos(POS).toLong();

    @Test
    void scansReadOnlyEndgameTypesAndTreatUnreadableRootsAsPresent() {
        UUID id = new UUID(0L, 1L);
        CompoundTag chunk = chunk(blockEntity(TYPE, POS, Optional.of(id)),
                blockEntity("minecraft:chest", POS.east(), Optional.of(id)),
                blockEntity(TYPE, POS.south(), Optional.empty()));
        Map<Long, Optional<UUID>> scan = EndpointObservations.scan(chunk, Set.of(TYPE));
        assertEquals(2, scan.size());
        assertTrue(EndpointObservations.present(scan, id, POS.asLong()));
        assertFalse(EndpointObservations.present(scan, new UUID(0L, 2L), POS.asLong()), "another readable ID");
        assertFalse(EndpointObservations.present(scan, id, POS.east().asLong()), "not an endgame type");
        assertTrue(EndpointObservations.present(scan, id, POS.south().asLong()), "unreadable root counts (R3-L1)");
        assertTrue(EndpointObservations.scan(new CompoundTag(), Set.of(TYPE)).isEmpty());
    }

    @Test
    void anAgedAbsenceMakesAnActiveEndpointMissingAndAPresenceCancelsIt() {
        EndgameService service = service();
        UUID id = new UUID(0L, 1L);
        UUID other = new UUID(0L, 2L);
        service.coalesced(root -> root.register(id, KIND, OWNER, LEVEL, POS.asLong(), false, 2048, 64));
        service.coalesced(root -> root.register(other, KIND, OWNER, LEVEL, POS.above().asLong(), false, 2048, 64));
        service.observe(LEVEL, CHUNK, chunk(blockEntity(TYPE, POS.above(), Optional.of(other))), false);
        service.tick(1_000L);
        service.tick(1_039L);
        assertEquals(EndpointRecord.State.ACTIVE, state(service, id), "not aged yet");
        service.observe(LEVEL, CHUNK, chunk(blockEntity(TYPE, POS, Optional.of(id)),
                blockEntity(TYPE, POS.above(), Optional.of(other))), true);
        service.tick(1_045L);
        service.tick(1_200L);
        assertEquals(EndpointRecord.State.ACTIVE, state(service, id), "a later presence cancelled the absence");
        service.observe(LEVEL, CHUNK, chunk(blockEntity(TYPE, POS.above(), Optional.of(other))), false);
        service.tick(2_000L);
        service.tick(2_040L);
        assertEquals(EndpointRecord.State.MISSING, state(service, id));
        assertEquals(EndpointRecord.State.ACTIVE, state(service, other));
        assertTrue(service.audit().page(null, 0).stream().anyMatch(line -> line.contains("action=endpoint_missing")));
    }

    @Test
    void aYoungTombstoneSettlesFortyTicksAfterItsAbsenceWasSeen() {
        EndgameService service = service();
        UUID id = new UUID(0L, 3L);
        service.coalesced(root -> root.register(id, KIND, OWNER, LEVEL, POS.asLong(), false, 2048, 64));
        service.coalesced(root -> root.remove(id));
        assertTrue(service.root().orElseThrow().tombstone(id).orElseThrow() instanceof Tombstone.Young);
        service.observe(LEVEL, CHUNK, chunk(), false);
        service.tick(500L);
        service.tick(539L);
        assertTrue(service.root().orElseThrow().tombstone(id).orElseThrow() instanceof Tombstone.Young);
        service.tick(540L);
        assertTrue(service.root().orElseThrow().tombstone(id).orElseThrow() instanceof Tombstone.Settled);
        assertEquals(0, service.root().orElseThrow().places(OWNER), "a settled tombstone frees its place");
    }

    @Test
    void onlyIndexedChunksAreQueued() {
        EndgameService service = service();
        service.observe(LEVEL, CHUNK, chunk(), false);
        assertEquals(0, service.pendingObservations(), "nothing is indexed");
        service.coalesced(root -> root.register(new UUID(0L, 4L), KIND, OWNER, LEVEL, POS.asLong(), false, 2048, 64));
        service.observe(LEVEL, new ChunkPos(POS.offset(64, 0, 0)).toLong(), chunk(), false);
        service.observe(ResourceLocation.tryBuild("minecraft", "the_nether"), CHUNK, chunk(), false);
        assertEquals(0, service.pendingObservations(), "other chunks and Levels are ignored");
        service.observe(LEVEL, CHUNK, chunk(), false);
        assertEquals(1, service.pendingObservations());
    }

    /** Review C11R-M5: each mutation moves only the IDs it touched, and the result equals a full rebuild. */
    @Test
    void theIndexFollowsEveryMutationAsAFullRebuildWould() {
        EndgameService service = service();
        ResourceLocation nether = ResourceLocation.tryBuild("minecraft", "the_nether");
        UUID a = new UUID(0L, 10L);
        UUID b = new UUID(0L, 11L);
        UUID c = new UUID(0L, 12L);
        UUID d = new UUID(0L, 13L);
        service.coalesced(root -> root.register(a, KIND, OWNER, LEVEL, POS.asLong(), false, 2048, 64));
        service.coalesced(root -> root.register(b, KIND, OWNER, LEVEL, POS.offset(64, 0, 0).asLong(), false, 2048,
                64));
        service.coalesced(root -> root.register(c, KIND, OWNER, nether, POS.asLong(), false, 2048, 64));
        service.coalesced(root -> root.register(d, KIND, OWNER, LEVEL, POS.above().asLong(), false, 2048, 64));
        assertIndexMatchesARebuild(service);
        service.coalesced(root -> root.remove(a));
        service.coalesced(root -> root.markMissing(b));
        assertIndexMatchesARebuild(service);
        assertFalse(service.indexForTest().containsKey(EndpointChunkIndex.ChunkKey.exact(LEVEL,
                new ChunkPos(POS.offset(64, 0, 0)).toLong())), "a MISSING record is not indexed");
        service.coalesced(root -> root.settle(a, root::pinned));
        service.barrier(root -> root.forget(b, OWNER, false, root::pinned));
        service.coalesced(root -> root.reassign(c, new UUID(2L, 2L), 64));
        service.coalesced(root -> root.register(a, KIND, OWNER, LEVEL, POS.asLong(), false, 2048, 64));
        assertIndexMatchesARebuild(service);
        service.coalesced(root -> root.evictOwner(OWNER, root::pinned));
        assertIndexMatchesARebuild(service);
        assertEquals(Set.of(d), service.indexForTest().get(EndpointChunkIndex.ChunkKey.exact(LEVEL, CHUNK)));
        assertEquals(Set.of(c), service.indexForTest().get(EndpointChunkIndex.ChunkKey.exact(nether, CHUNK)));
        assertEquals(2, service.indexForTest().size());
    }

    /**
     * Review C11R-L4: two Level keys with equal string hashes. A save of a chunk in one Level is not an absence of an
     * ACTIVE endpoint at the same chunk coordinates in the other, so that endpoint is never made MISSING.
     */
    @Test
    void levelKeysWithEqualHashesDoNotShareChunks() {
        ResourceLocation first = ResourceLocation.tryBuild("test", "dim_aan");
        ResourceLocation second = ResourceLocation.tryBuild("test", "dim_ac0");
        assertEquals(Tombstone.hash(first), Tombstone.hash(second), "the fixture keys collide");
        EndgameService service = service();
        UUID id = new UUID(0L, 20L);
        service.coalesced(root -> root.register(id, KIND, OWNER, second, POS.asLong(), false, 2048, 64));
        service.observe(first, CHUNK, chunk(), false);
        assertEquals(0, service.pendingObservations(), "a chunk of the other Level was queued");
        service.tick(3_000L);
        service.tick(3_100L);
        assertEquals(EndpointRecord.State.ACTIVE, state(service, id));
        service.observe(second, CHUNK, chunk(), false);
        assertEquals(1, service.pendingObservations());
    }

    /** Review C11R-L8: a registration result is kept only while it explains a refusal, and dropped on forget. */
    @Test
    void registrationResultsAreKeptOnlyForRefusals() {
        EndgameService service = service();
        UUID registered = new UUID(0L, 30L);
        service.awaitRegistration(registered, KIND, OWNER, LEVEL, POS.asLong());
        service.observe(LEVEL, CHUNK, chunk(blockEntity(TYPE, POS, Optional.of(registered))), false);
        service.tick(4_000L);
        assertEquals(EndgameCode.OK, service.endpointStatus(registered, LEVEL, POS.asLong()));
        assertEquals(0, service.registrationResultsForTest(), "a success is not kept");
        ResourceLocation longLevel = ResourceLocation.tryBuild("datapack", "d".repeat(130));
        UUID refused = new UUID(0L, 31L);
        service.awaitRegistration(refused, KIND, OWNER, longLevel, POS.asLong());
        service.observe(longLevel, CHUNK, chunk(blockEntity(TYPE, POS, Optional.of(refused))), false);
        service.tick(4_001L);
        assertEquals(EndgameCode.TARGET_OUT_OF_BOUNDS, service.endpointStatus(refused, longLevel, POS.asLong()));
        assertEquals(1, service.registrationResultsForTest());
        service.forgetCandidate(refused);
        assertEquals(0, service.registrationResultsForTest(), "a forgotten candidate's result is dropped");
    }

    private static void assertIndexMatchesARebuild(EndgameService service) {
        var incremental = service.indexForTest();
        assertEquals(service.rebuiltIndexForTest(), incremental);
    }

    @Test
    void theStatusReportsTheRootAndTheSwitches() {
        EndgameService service = service();
        assertEquals(EndgameCode.OK, service.coalesced(root -> root.register(new UUID(0L, 5L), KIND, OWNER, LEVEL,
                POS.asLong(), false, 2048, 64)));
        String status = service.status();
        assertTrue(status.contains("physical_mining=false"), status);
        assertTrue(status.contains("endpoints=1 missing=0"), status);
        assertTrue(status.contains("write_pending=true"), status);
    }

    /** Review C13-F4: a held coalesced flush waits past its interval; a barrier still writes; release lets it run. */
    @Test
    void aHeldCoalescedFlushWaitsAndABarrierStillWrites() {
        EndgameService service = new EndgameService(() -> EndgameSettings.DEFAULTS, () -> Set.of(TYPE));
        service.startForTest(EndgameSavedData.create(), true);
        service.holdCoalescedForTest(true);
        service.coalesced(root -> root.register(new UUID(0L, 40L), KIND, OWNER, LEVEL, POS.asLong(), false, 2048,
                64));
        service.tick(5_000L);
        service.tick(5_200L);
        assertTrue(service.writePending(), "a held coalesced flush wrote");
        service.barrier(root -> null);
        assertFalse(service.writePending(), "a barrier waited for the hold");
        service.coalesced(root -> root.register(new UUID(0L, 41L), KIND, OWNER, LEVEL, POS.above().asLong(), false,
                2048, 64));
        service.holdCoalescedForTest(false);
        service.tick(5_400L);
        assertFalse(service.writePending(), "the released flush did not run");
    }

    private static EndgameService service() {
        EndgameService service = new EndgameService(() -> EndgameSettings.DEFAULTS, () -> Set.of(TYPE));
        service.startForTest(EndgameSavedData.create());
        return service;
    }

    private static EndpointRecord.State state(EndgameService service, UUID id) {
        return service.root().orElseThrow().endpoint(id).orElseThrow().state();
    }

    private static CompoundTag chunk(CompoundTag... blockEntities) {
        CompoundTag chunk = new CompoundTag();
        ListTag list = new ListTag();
        for (CompoundTag blockEntity : blockEntities) {
            list.add(blockEntity);
        }
        chunk.put("block_entities", list);
        return chunk;
    }

    private static CompoundTag blockEntity(String type, BlockPos pos, Optional<UUID> id) {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", type);
        tag.putInt("x", pos.getX());
        tag.putInt("y", pos.getY());
        tag.putInt("z", pos.getZ());
        CompoundTag root = new CompoundTag();
        id.ifPresent(value -> root.putUUID(EndgameDeviceTags.DEVICE_ID, value));
        tag.put(EndgameDeviceTags.ROOT, root);
        return tag;
    }
}
