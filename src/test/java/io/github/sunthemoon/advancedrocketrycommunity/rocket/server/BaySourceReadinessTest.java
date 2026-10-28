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
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transfer.RocketDiscoveryColumns;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityLookup;
import net.minecraft.world.level.entity.EntitySection;
import net.minecraft.world.level.entity.EntitySectionStorage;
import net.minecraft.world.level.entity.LevelEntityGetter;
import net.minecraft.world.level.entity.LevelEntityGetterAdapter;
import net.minecraft.world.level.entity.Visibility;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * W170-R41 A0: the census counts only loaded images, so R2 and D6 must not run it while a recorded end is still loading.
 * R2 waits for both ends, which R1 tickets. D6 waits for every checked column of both ends (W27d3), which the claim's own
 * renewed tickets cover: both ends of a restart group, and from CONSUME on both ends of an in-session claim (W185-R2).
 * The loading case starts from the re-review's HIDDEN-section probe, with the real vanilla entity index and no started
 * server. The census is {@link BayCopyCensus}, the one rule set R2, D5 and D6 share (W185-R11).
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
        UnstartedServer.set(Entity.class, rocket, "position", new Vec3(x + .5, y, z + .5));
        UnstartedServer.set(Entity.class, rocket, "bb", new AABB(x, y, z, x + 1, y + 1, z + 1));
        return rocket;
    }

    /** D6's copy wait as it reads the one census: its refusal, DUPLICATE_PENDING while a copy remains, else null. */
    private static String census(World world, RocketTransferRecord record) {
        var scan = BayCopyCensus.inspect(world.server, record, group(record));
        return scan.refusal() != null ? scan.refusal() : scan.copies().isEmpty() ? null : "DUPLICATE_PENDING";
    }

    /**
     * W185-R11, replacing the removed covered() oracle: every column D6 awaits is one the claim's own tickets cover. A
     * restart group's R1 tickets both checked sets; an in-session claim from CONSUME on tickets its source end's checked
     * set and its destination's ticket set, which is that end's checked set when admissible.
     */
    @Test void aRestartGroupAndAnInSessionClaimFromConsumeOnTicketBothCheckedEnds() {
        var record = record(1);
        var source = record.sourceSnapshot();
        var destination = record.destinationSnapshot();
        var restart = new RocketMarginTicketsTest.Tickets(source.sourceDimension(), destination.sourceDimension());
        RocketTransferEntities.refreshMargins(TRANSFER, source, destination, restart);
        var inSession = new RocketMarginTicketsTest.Tickets(source.sourceDimension(), destination.sourceDimension());
        RocketTransferEntities.addRefreshSet(TRANSFER, source, inSession);
        RocketTransferEntities.addDestinationTickets(TRANSFER, destination, inSession);
        for (var tickets : List.of(restart, inSession)) {
            assertEquals(Set.copyOf(RocketDiscoveryColumns.of(source).columns()), tickets.columns(source.sourceDimension(), TRANSFER));
            assertEquals(Set.copyOf(RocketDiscoveryColumns.of(destination).columns()),
                    tickets.columns(destination.sourceDimension(), TRANSFER));
        }
    }

    /**
     * W185-R11, renamed from "...IsNotAwaited", which W27d3 made stale: D6 awaits an in-session source end wider than its
     * origin column until every column is entity-loaded and ticking; W185-R2's tickets are what load it.
     */
    @Test void anInSessionSourceEndWiderThanItsOriginColumnIsAwaitedUntilEveryColumnLoads() {
        // Origin x = 14: the checked set runs from column 0 to column 1, and the origin ticket alone covers only column 0.
        var record = BayAbsenceCensusTest.relocate(14, 8, MOON.x(), MOON.z());
        var columns = RocketDiscoveryColumns.of(record.sourceSnapshot());
        assertTrue(columns.admissible() && columns.count() == 2, "control: " + columns);
        var margin = columns.columns().stream().filter(column -> !column.equals(columns.origin())).findFirst().orElseThrow();
        var world = new World();
        var source = world.put(record.sourceSnapshot().sourceDimension(), Visibility.TICKING, true);
        world.put(record.destinationSnapshot().sourceDimension(), Visibility.TICKING, true);
        source.cold.add(margin.toLong());
        assertFalse(BaySourceProof.endsLoaded(world.server, record), "D6 must wait for the source end's margin column");
        source.cold.clear();
        assertTrue(BaySourceProof.endsLoaded(world.server, record), "with every column loaded and ticking, D6 may census the end");
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
        assertNull(census(world, record),
                "characterization: the census cannot see an image in a section that is not yet accessible");
        assertFalse(RocketTransferEntities.recoveryEndsAlreadyLoaded(world.server, record), "R2 must wait for the destination end");
        // W185-R11: one predicate for restart groups and in-session claims alike; its unused origin parameter is gone.
        assertFalse(BaySourceProof.endsLoaded(world.server, record), "D6 must wait, for a restart group and an in-session claim");

        destination.load(); // the end's entity sections load and tick, as R1's tickets make them
        assertTrue(RocketTransferEntities.recoveryEndsAlreadyLoaded(world.server, record));
        assertTrue(BaySourceProof.endsLoaded(world.server, record));
        assertEquals("UNEXPECTED_COPY", census(world, record), "once the end is loaded, the census counts the image");
    }

    /** Levels by dimension over the real vanilla entity index, with settable entity-section readiness. */
    private static final class World {
        final MinecraftServer server = UnstartedServer.owned();
        final Map<ResourceKey<Level>, ServerLevel> levels = new LinkedHashMap<>();

        ProbeLevel put(ResourceLocation dimension, Visibility visibility, boolean ready, RocketEntity... entities) {
            var level = UnstartedServer.allocate(ProbeLevel.class);
            level.key = ResourceKey.create(Registries.DIMENSION, dimension);
            level.ready = ready; level.cold = new HashSet<>();
            level.sections = new EntitySectionStorage<Entity>(Entity.class, key -> visibility);
            var lookup = new EntityLookup<Entity>();
            for (RocketEntity rocket : entities) {
                UnstartedServer.set(Entity.class, rocket, "level", level);
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
        /** Columns that stay unloaded while the rest of a ready Level is entity-loaded and ticking. */
        Set<Long> cold;
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
        @Override public boolean areEntitiesLoaded(long chunk) { return ready && !cold.contains(chunk); }
        @Override public boolean isPositionEntityTicking(BlockPos pos) { return ready && !cold.contains(ChunkPos.asLong(pos)); }
    }
}
