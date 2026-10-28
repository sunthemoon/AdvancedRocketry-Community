package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static io.github.sunthemoon.advancedrocketrycommunity.rocket.server.BayConsumptionFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.deployment.BayClaim;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.deployment.BayGroup;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.deployment.BayMember;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.RocketCargoWorkBudget;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.UnstartedServer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityLookup;
import net.minecraft.world.level.entity.EntitySection;
import net.minecraft.world.level.entity.EntitySectionStorage;
import net.minecraft.world.level.entity.LevelEntityGetter;
import net.minecraft.world.level.entity.LevelEntityGetterAdapter;
import net.minecraft.world.level.entity.Visibility;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * W170-R41 A0: the census counts only loaded images, so R2 and D6 must not run it while a recorded end is still loading.
 * R2 waits for both ends, which R1 tickets. D6 waits for the ends that its claim's renewed tickets cover in full: both
 * ends of a restart group, an in-session claim's admissible destination, and an in-session source end only when the
 * origin column is all of it. The loading case starts from the re-review's HIDDEN-section probe, with the real vanilla
 * entity index and no started server.
 */
final class BaySourceReadinessTest {
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

    /** An image of L with another destination hash, operational, at block {@code (x, y, z)}. */
    private static RocketEntity unexpected(RocketTransferRecord record, double x, double y, double z) {
        var rocket = UnstartedServer.allocate(RocketEntity.class);
        UnstartedServer.set(RocketEntity.class, rocket, "assemblyTransactionId", LOGICAL);
        UnstartedServer.set(RocketEntity.class, rocket, "ownerId", OWNER);
        UnstartedServer.set(RocketEntity.class, rocket, "snapshot", record(2).destinationSnapshot());
        UnstartedServer.set(RocketEntity.class, rocket, "flightData", record.sourceFlightData());
        UnstartedServer.set(Entity.class, rocket, "uuid", new UUID(996, 1));
        UnstartedServer.set(Entity.class, rocket, "bb", new AABB(x, y, z, x + 1, y + 1, z + 1));
        return rocket;
    }

    @Test void aRestartGroupAwaitsBothEndsAndAnInSessionClaimItsAdmissibleDestination() {
        var record = record(1);
        var source = record.sourceSnapshot();
        var destination = record.destinationSnapshot();
        assertTrue(BaySourceProof.covered(BayClaim.Origin.RESTART, source, destination, false));
        assertTrue(BaySourceProof.covered(BayClaim.Origin.RESTART, source, destination, true));
        assertTrue(BaySourceProof.covered(BayClaim.Origin.CHECKPOINT, source, destination, true), "the destination ticket set");
        assertTrue(BaySourceProof.covered(BayClaim.Origin.CHECKPOINT, source, destination, false),
                "control: this source end is its origin column alone, which the origin ticket covers");
    }

    /** The one path that reaches D6 with an end no renewed ticket covers: an in-session source wider than its origin. */
    @Test void anInSessionSourceEndWiderThanItsOriginColumnIsNotAwaited() {
        var record = record(1);
        // Origin x = 14: the margin runs from column 0 to column 1, and the in-session claim tickets only column 0.
        RocketStructureSnapshot wide = record.sourceSnapshot().relocated(new UUID(996, 2), record.sourceSnapshot().sourceDimension(),
                new RocketPosition(14, 80, 8), 0L);
        assertFalse(BaySourceProof.covered(BayClaim.Origin.CHECKPOINT, wide, record.destinationSnapshot(), false));
        assertTrue(BaySourceProof.covered(BayClaim.Origin.RESTART, wide, record.destinationSnapshot(), false),
                "a restart group's R1 tickets cover the whole checked set");
    }

    /**
     * W185-R2: from CONSUME on, an in-session claim tickets its whole source end, as R1 does for a restart group, and the
     * flight's ordinary release removes those tickets. Earlier stages, REPAIR and restart groups add none here.
     */
    @Test void anInSessionClaimTicketsItsWholeSourceEndFromConsumeOn() {
        var record = record(1);
        var group = group(record);
        var claim = BayClaim.checkpoint(LOGICAL, TRANSFER, SOURCE, group.members());
        assertFalse(BaySourceProof.ticketsSourceEnd(claim), "GROUP");
        claim.groupPassed(1); claim.nextCandidate(); claim.freeze(group);
        assertFalse(BaySourceProof.ticketsSourceEnd(claim), "before CONSUME");
        for (var stage : List.of(BayClaim.Stage.CONSUME, BayClaim.Stage.PROVE, BayClaim.Stage.RETIRE)) {
            claim.advance(stage);
            assertTrue(BaySourceProof.ticketsSourceEnd(claim), stage.name());
        }
        claim.repair("W185_TEST");
        assertFalse(BaySourceProof.ticketsSourceEnd(claim), "REPAIR adds no ticket");
        var restart = BayClaim.restart(LOGICAL, TRANSFER, group.members());
        restart.freeze(group); restart.establishSource(SOURCE); restart.advance(BayClaim.Stage.CONSUME);
        assertFalse(BaySourceProof.ticketsSourceEnd(restart), "a restart group's R1 renewal covers both ends instead");

        // Origin x = 14: the checked set has two columns, and the origin ticket alone covers only one of them.
        RocketStructureSnapshot wide = record.sourceSnapshot().relocated(new UUID(996, 2), record.sourceSnapshot().sourceDimension(),
                new RocketPosition(14, 80, 8), 0L);
        var columns = io.github.sunthemoon.advancedrocketrycommunity.rocket.transfer.RocketDiscoveryColumns.of(wide);
        assertTrue(columns.admissible() && columns.count() == 2, "control: " + columns);
        var tickets = new RocketMarginTicketsTest.Tickets(wide.sourceDimension());
        assertEquals(2, RocketTransferEntities.addRefreshSet(TRANSFER, wide, tickets));
        assertEquals(java.util.Set.copyOf(columns.columns()), tickets.columns(wide.sourceDimension(), TRANSFER));
        RocketTransferEntities.releaseMargins(TRANSFER, wide, record.destinationSnapshot(), tickets);
        assertEquals(0, tickets.count(TRANSFER), "the flight's ordinary release removes the source end's tickets");
    }

    /** The re-review's HIDDEN-section probe: an image of L at a destination that is still loading. */
    @Test void aDestinationEndThatIsStillLoadingIsAwaitedAndItsImageCountedOnceLoaded() {
        var record = record(1);
        var copy = unexpected(record, MOON.x(), MOON.y(), MOON.z());
        var world = new World();
        var source = world.put(record.sourceSnapshot().sourceDimension(), Visibility.TICKING, true);
        var destination = world.put(record.destinationSnapshot().sourceDimension(), Visibility.HIDDEN, false, copy);
        assertTrue(source.ready && RocketTransferEntities.endAlreadyLoaded(world.server, record.sourceSnapshot()),
                "control: the source end is ready");
        assertNull(BaySourceResume.copies(world.server, record, group(record), SOURCE),
                "characterization: the census cannot see an image in a section that is not yet accessible");
        assertFalse(RocketTransferEntities.recoveryEndsAlreadyLoaded(world.server, record), "R2 must wait for the destination end");
        assertFalse(BaySourceProof.endsLoaded(world.server, BayClaim.Origin.RESTART, record), "D6 of a restart group must wait");
        assertFalse(BaySourceProof.endsLoaded(world.server, BayClaim.Origin.CHECKPOINT, record), "D6 of an in-session claim must wait");

        destination.load(); // the end's entity sections load and tick, as R1's tickets make them
        assertTrue(RocketTransferEntities.recoveryEndsAlreadyLoaded(world.server, record));
        assertTrue(BaySourceProof.endsLoaded(world.server, BayClaim.Origin.RESTART, record));
        assertTrue(BaySourceProof.endsLoaded(world.server, BayClaim.Origin.CHECKPOINT, record));
        assertEquals("UNEXPECTED_COPY", BaySourceResume.copies(world.server, record, group(record), SOURCE),
                "once the end is loaded, the census counts the image");
    }

    /** Levels by dimension over the real vanilla entity index, with settable entity-section readiness. */
    private static final class World {
        final MinecraftServer server = UnstartedServer.owned();
        final Map<ResourceKey<Level>, ServerLevel> levels = new LinkedHashMap<>();

        ProbeLevel put(ResourceLocation dimension, Visibility visibility, boolean ready, RocketEntity... entities) {
            var level = UnstartedServer.allocate(ProbeLevel.class);
            level.key = ResourceKey.create(Registries.DIMENSION, dimension);
            level.ready = ready;
            level.sections = new EntitySectionStorage<Entity>(Entity.class, key -> visibility);
            var lookup = new EntityLookup<Entity>();
            for (RocketEntity rocket : entities) {
                var box = rocket.getBoundingBox();
                level.sections.getOrCreateSection(SectionPos.asLong(SectionPos.blockToSectionCoord(box.minX),
                        SectionPos.blockToSectionCoord(box.minY), SectionPos.blockToSectionCoord(box.minZ))).add(rocket);
                lookup.add(rocket);
            }
            level.getter = new LevelEntityGetterAdapter<>(lookup, level.sections);
            levels.put(level.key, level);
            UnstartedServer.set(MinecraftServer.class, server, "levels", levels);
            return level;
        }
    }

    static final class ProbeLevel extends ServerLevel {
        LevelEntityGetter<Entity> getter;
        EntitySectionStorage<Entity> sections;
        ResourceKey<Level> key;
        boolean ready;
        ProbeLevel() { super(null, null, null, null, null, null, null, false, 0, List.of(), false, null); }
        /** Every section becomes accessible and ticking, and every column entity-loaded and entity-ticking. */
        void load() {
            for (long chunk : sections.getAllChunksWithExistingSections()) {
                sections.getExistingSectionsInChunk(chunk).forEach((EntitySection<Entity> section) ->
                        section.updateChunkStatus(Visibility.TICKING));
            }
            ready = true;
        }
        @Override public ResourceKey<Level> dimension() { return key; }
        @Override public int getMinBuildHeight() { return -64; }
        @Override public int getHeight() { return 384; }
        @Override public LevelEntityGetter<Entity> getEntities() { return getter; }
        @Override public boolean areEntitiesLoaded(long chunk) { return ready; }
        @Override public boolean isPositionEntityTicking(BlockPos pos) { return ready; }
    }
}
