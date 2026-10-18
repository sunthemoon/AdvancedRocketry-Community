package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightRequestCode;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightRequestResult;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transfer.NamedLandingPadStaging;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transfer.RocketLandingPadSelector;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationEnvironmentProfile;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationGridCell;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationLimits;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationState;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.UnstartedServer;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.entity.EntityLookup;
import net.minecraft.world.level.entity.EntitySectionStorage;
import net.minecraft.world.level.entity.LevelEntityGetter;
import net.minecraft.world.level.entity.LevelEntityGetterAdapter;
import net.minecraft.world.level.entity.Visibility;
import net.minecraft.world.level.levelgen.Heightmap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** W90 (R21 item 3): governed-only admission bounds, counted against a recording Level with no world loading. */
class RocketGovernedAdmissionTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void theGovernedSourceNeedsAnAdmissibleSetBeforeItsReadinessIsRead() {
        var earth = Level.OVERWORLD.location();
        assertNull(RocketGovernedAdmission.sourceRefusal(RocketMarginTicketsTest.snapshot(earth, 15, 15, 0), () -> true));
        assertEquals(RocketFlightRequestCode.LAUNCH_AREA_INACTIVE,
                RocketGovernedAdmission.sourceRefusal(RocketMarginTicketsTest.snapshot(earth, 15, 15, 0), () -> false));
        for (var refused : List.of(RocketMarginTicketsTest.snapshot(earth, 2, 8, 0, 252),
                RocketMarginTicketsTest.snapshot(earth, 15, 8, 5, 6),
                RocketMarginTicketsTest.snapshot(earth, Integer.MAX_VALUE, 8, 0))) {
            assertEquals(RocketFlightRequestCode.LAUNCH_CHUNK_BUDGET, RocketGovernedAdmission.sourceRefusal(refused,
                    () -> { fail("An inadmissible source read its readiness"); return true; }));
        }
        // The source refusal is a code alone; RocketFlightRequestResult accepts only pad detail keys.
        var result = RocketFlightRequestResult.failure(RocketFlightRequestCode.LAUNCH_CHUNK_BUDGET, 100);
        assertNull(result.detailKey());
        assertEquals("flight.advancedrocketrycommunity.request.launch_chunk_budget", result.code().translationKey());
        assertEquals("flight.advancedrocketrycommunity.request.launch_area_inactive",
                RocketFlightRequestCode.LAUNCH_AREA_INACTIVE.translationKey());
    }

    @Test void theGovernedSurfaceSelectorStopsAtTheFirstBudgetFailureWithoutTerrainAccess() {
        // Blocks 0..240 centred on the spawn pad at x = 8: the region spans 16 columns, its margin 17.
        var source = RocketMarginTicketsTest.snapshot(Level.OVERWORLD.location(), 5_000, 5_000, 0, 240);
        var governedLevel = UnstartedServer.allocate(RecordingLevel.class);
        var refused = new RocketLandingPadSelector().select(governedLevel, source, UUID.randomUUID(), List.of(), 0, true);
        assertFalse(refused.success());
        assertEquals(RocketLandingPadSelector.CHUNK_BUDGET, refused.refusalKey());
        assertEquals(1, refused.candidatesChecked(), "Selection continued past the first budget failure");
        assertEquals(0, refused.chunksLoaded());
        assertEquals(0, governedLevel.terrainReads(), "The governed refusal read terrain");
        var legacyLevel = UnstartedServer.allocate(RecordingLevel.class);
        var legacy = new RocketLandingPadSelector().select(legacyLevel, source, UUID.randomUUID(), List.of(), 0);
        assertTrue(legacy.success(), "A legacy launch that a governed one refuses no longer selects: " + legacy.detail());
        assertTrue(legacyLevel.terrainReads() > 0);
        var contained = RocketMarginTicketsTest.snapshot(Level.OVERWORLD.location(), 5_000, 5_000, 0);
        assertTrue(new RocketLandingPadSelector().select(UnstartedServer.allocate(RecordingLevel.class), contained,
                UUID.randomUUID(), List.of(), 0, true).success(), "An admissible governed source did not select");
    }

    @Test void theGovernedStationPadRefusesBeforeLoadingAndLegacyStillReachesTerrain() {
        // Bounds 40..41 centred on the pad at x = 0 put the origin at -40, outside the margin columns -1..0.
        var source = RocketMarginTicketsTest.snapshot(Level.OVERWORLD.location(), 5_000, 5_000, 40, 41);
        var station = station();
        var governedLevel = UnstartedServer.allocate(SpaceLevel.class);
        var refused = new RocketLandingPadSelector().selectStation(governedLevel, source, UUID.randomUUID(), station, List.of(), 0, true);
        assertEquals(RocketLandingPadSelector.CHUNK_BUDGET, refused.refusalKey());
        assertEquals(0, refused.chunksLoaded()); assertEquals(0, governedLevel.terrainReads());
        var legacyLevel = UnstartedServer.allocate(SpaceLevel.class);
        new RocketLandingPadSelector().selectStation(legacyLevel, source, UUID.randomUUID(), station, List.of(), 0);
        assertTrue(legacyLevel.terrainReads() > 0, "The legacy station path changed");
    }

    @Test void theGovernedNamedPadAnswersChunkBudgetBeforeBoundsChunksOrBlocks() {
        var station = station();
        // Region 4000..4240 spans 16 columns; its margin 3999..4242 spans 17.
        var far = RocketMarginTicketsTest.snapshot(CelestialIds.SPACE_LEVEL.location(), 4_000, 4_000, 0, 240);
        var level = UnstartedServer.allocate(SpaceLevel.class);
        assertEquals(RocketLandingPadSelector.CHUNK_BUDGET, new NamedLandingPadStaging().unavailable(level, far, station, true));
        assertEquals(0, level.terrainReads());
        // Legacy passes the region count and refuses on its unchanged bounds check instead.
        assertEquals("message.advancedrocketrycommunity.pad.reason.bounds", new NamedLandingPadStaging().unavailable(level, far, station));
        assertEquals(0, level.terrainReads());
    }

    private static StationState station() {
        var cell = new StationGridCell(0, 0);
        return new StationState(StationLimits.STATE_SCHEMA_VERSION, new UUID(90, 1), new UUID(90, 2), "Margin", cell,
                cell.region(StationLimits.REGION_SIZE), cell.landingPad(), ModIdentity.id("earth"), 0,
                StationEnvironmentProfile.BASIC_SPACE, List.of(), List.of());
    }

    /** Only terrain and entity reads are stubbed and counted; candidates, regions and budgets are production code. */
    static class RecordingLevel extends ServerLevel {
        int chunks, heights, blocks;
        RecordingLevel() { super(null, null, null, null, null, null, null, false, 0, List.of(), false, null); }
        int terrainReads() { return chunks + heights + blocks; }
        @Override public net.minecraft.util.profiling.ProfilerFiller getProfiler() { return net.minecraft.util.profiling.InactiveProfiler.INSTANCE; }
        @Override public ResourceKey<Level> dimension() { return Level.OVERWORLD; }
        @Override public BlockPos getSharedSpawnPos() { return new BlockPos(8, 80, 8); }
        @Override public int getMinBuildHeight() { return -64; }
        @Override public int getHeight() { return 384; }
        @Override public int getHeight(Heightmap.Types type, int x, int z) { heights++; return 80; }
        @Override public LevelChunk getChunk(int x, int z) { chunks++; return null; }
        @Override public WorldBorder getWorldBorder() { return new WorldBorder(); }
        @Override public BlockState getBlockState(BlockPos pos) { blocks++; return Blocks.AIR.defaultBlockState(); }
        @Override public BlockEntity getBlockEntity(BlockPos pos) { return null; }
        @Override public java.util.Collection<net.minecraftforge.entity.PartEntity<?>> getPartEntities() { return List.of(); }
        @Override public LevelEntityGetter<Entity> getEntities() {
            return new LevelEntityGetterAdapter<>(new EntityLookup<>(), new EntitySectionStorage<>(Entity.class, key -> Visibility.HIDDEN));
        }
    }

    static final class SpaceLevel extends RecordingLevel {
        @Override public ResourceKey<Level> dimension() { return CelestialIds.SPACE_LEVEL; }
    }
}
