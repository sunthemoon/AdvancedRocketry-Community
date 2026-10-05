package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import static io.github.sunthemoon.advancedrocketrycommunity.gametest.RocketFlightGameTestFixtures.assembleFueledRocket;
import static io.github.sunthemoon.advancedrocketrycommunity.gametest.RocketFlightGameTestFixtures.clearTransferJournal;
import static io.github.sunthemoon.advancedrocketrycommunity.gametest.RocketFlightGameTestFixtures.findLogicalRocket;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.exoplanet.ExoplanetContent;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightRequestResult;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.SatelliteIds;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.content.SatelliteIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.service.SatelliteRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationManagementCode;
import io.github.sunthemoon.advancedrocketrycommunity.travel.model.TravelTarget;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * ADR-063 section 9 with revision 6 (A1), the Tau Ceti path: a data satellite discovers Tau Ceti f, a station warps
 * to its orbit with a docked rocket, and the rocket lands on the first fixed pad and returns to the station. The batch
 * runs on an empty discovery ledger ({@link DiscoveryProgressFixture}) under the production rocket-motion rule. On
 * both worlds the landing ground under the eight fixed pads is dry and clear.
 */
@GameTestHolder(AdvancedRocketryCommunity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TauCetiPathGameTests {
    static final String BATCH = "tau_ceti_path";
    private static final int FLIGHT = 270;
    /** The fixed pads' centres (ADR-006, {@code RocketLandingPadSelector}). */
    private static final int[][] PADS = {{0, 0}, {64, 0}, {-64, 0}, {0, 64}, {0, -64}, {64, 64}, {-64, 64}, {64, -64}};
    /** Half the widest footprint a landing allows (16 chunks: 64 blocks around a pad on a chunk corner). */
    private static final int FOOTPRINT = 32;

    private TauCetiPathGameTests() {
    }

    @GameTest(template = "rocket_test", batch = BATCH, timeoutTicks = 1_400)
    public static void aDiscoveredTauCetiFIsReachedByWarpAndADockedRocketLandsAndReturns(GameTestHelper helper) {
        ServerLevel earth = helper.getLevel();
        ServerLevel surface = earth.getServer().getLevel(ExoplanetContent.TAU_CETI_F_LEVEL);
        helper.assertTrue(surface != null, "Tau Ceti f has no Level");
        clearTransferJournal(earth);
        StationWarpGameTests.Fixture fixture = new StationWarpGameTests.Fixture(helper, "Tau Ceti path",
                CelestialIds.EARTH_ID, 9_000_000);
        SatelliteIdentity satellite = new SatelliteIdentity(UUID.randomUUID(), fixture.ownerId,
                SatelliteIds.DATA_SATELLITE);
        UUID[] logical = new UUID[1];
        Runnable cleanup = () -> {
            if (logical[0] != null) {
                PlanetaryWorldGameTests.cleanRocket(fixture.server, logical[0]);
            }
            fixture.close();
            clearTransferJournal(earth);
        };
        List<String> owner;
        try {
            fixture.keepLoaded();
            fixture.core(fixture.pad.east(2));
            owner = fixture.join(fixture.ownerId, "tauCetiOwner");
            fixture.look(fixture.ownerId, fixture.pad.east(2));
            helper.assertTrue(!SatelliteRuntime.discovered(fixture.server, ExoplanetContent.TAU_CETI_F),
                    "Tau Ceti f was known before its discovery");
            fixture.run(fixture.ownerId, "arce station warp " + ExoplanetContent.TAU_CETI_F);
            StationWarpGameTests.expect(helper, owner, StationManagementCode.WARP_TARGET_UNKNOWN,
                    "a warp to the undiscovered Tau Ceti f");
            helper.assertTrue(SatelliteRuntime.launch(fixture.server.getPlayerList().getPlayer(fixture.ownerId),
                    satellite, ExoplanetContent.TAU_CETI_F).success(), "The data satellite did not start on Tau Ceti f");
            RocketEntity rocket = assembleFueledRocket(helper, new BlockPos(3, 2, 3), fixture.ownerId);
            logical[0] = rocket.assemblyTransactionId().orElseThrow();
            RocketFlightRequestResult docking = RocketRuntime.requestAdminStationFlight(rocket, fixture.id(),
                    UUID.randomUUID());
            helper.assertTrue(docking.success(), "Earth-to-station launch failed: " + docking.code());
        } catch (RuntimeException | Error exception) {
            cleanup.run();
            throw exception;
        }
        helper.runAfterDelay(FLIGHT, () -> guarded(cleanup, () -> {
            landed(helper, fixture.space, logical[0], "the station");
            helper.assertTrue(SatelliteRuntime.claim(fixture.server.getPlayerList().getPlayer(fixture.ownerId),
                    satellite).success(), "The Tau Ceti f research claim failed");
            helper.assertTrue(SatelliteRuntime.discovered(fixture.server, ExoplanetContent.TAU_CETI_F),
                    "The data satellite did not discover Tau Ceti f");
            fixture.look(fixture.ownerId, fixture.pad.east(2));
            fixture.run(fixture.ownerId, "arce station warp " + ExoplanetContent.TAU_CETI_F);
            String quote = StationWarpGameTests.last(owner);
            helper.assertTrue(quote.contains("interstellar warp, cost 8000000 FE, warp energy 9000000 FE")
                    && quote.contains("Docked rockets move with the station") && !quote.contains("no rocket routes"),
                    "Tau Ceti f quote differs: " + quote);
            fixture.run(fixture.ownerId, "arce station warp confirm " + fixture.id());
            helper.runAfterDelay(StationWarpGameTests.COMMIT_DELAY, () -> guarded(cleanup, () -> {
                helper.assertTrue(fixture.station().orbitBody().equals(ExoplanetContent.TAU_CETI_F)
                        && fixture.data.warpEnergy(fixture.id()) == 1_000_000,
                        "The station did not warp to Tau Ceti f's orbit once");
                RocketEntity docked = landed(helper, fixture.space, logical[0], "the warped station");
                RocketFlightRequestResult down = RocketRuntime.requestAdminFlight(docked,
                        new TravelTarget.BodySurface(ExoplanetContent.TAU_CETI_F), UUID.randomUUID());
                helper.assertTrue(down.success(), "Station-to-Tau Ceti f launch failed: " + down.code());
                helper.runAfterDelay(FLIGHT, () -> guarded(cleanup, () -> {
                    RocketEntity onF = landed(helper, surface, logical[0], "Tau Ceti f");
                    helper.assertTrue(onF.flightData().orElseThrow().currentBody().equals(ExoplanetContent.TAU_CETI_F),
                            "The rocket did not land on Tau Ceti f");
                    // The first pad, at the origin: the landing ground keeps it free.
                    helper.assertTrue(Math.abs(onF.getX()) < 4 && Math.abs(onF.getZ()) < 4,
                            "The rocket landed away from the first pad at " + onF.blockPosition());
                    helper.assertTrue(findLogicalRocket(fixture.space, logical[0]) == null,
                            "The station kept a copy of the rocket");
                    RocketFlightRequestResult up = RocketRuntime.requestAdminStationFlight(onF, fixture.id(),
                            UUID.randomUUID());
                    helper.assertTrue(up.success(), "Tau Ceti f-to-station launch failed: " + up.code());
                    helper.runAfterDelay(FLIGHT, () -> guarded(cleanup, () -> {
                        RocketEntity back = landed(helper, fixture.space, logical[0], "the station on return");
                        helper.assertTrue(fixture.station().region().contains(back.blockPosition().getX(),
                                back.blockPosition().getZ()), "The returning rocket landed outside the station");
                        helper.assertTrue(findLogicalRocket(surface, logical[0]) == null,
                                "Tau Ceti f kept a copy of the rocket");
                        cleanup.run();
                        helper.succeed();
                    }));
                }));
            }));
        }));
    }

    @GameTest(template = "empty", batch = "tau_ceti_landing_f", timeoutTicks = 2_400)
    public static void tauCetiFHasDryClearGroundUnderEachPad(GameTestHelper helper) {
        landingGround(helper, ExoplanetContent.TAU_CETI_F_LEVEL);
    }

    @GameTest(template = "empty", batch = "tau_ceti_landing_g", timeoutTicks = 2_400)
    public static void tauCetiGHasDryClearGroundUnderEachPad(GameTestHelper helper) {
        landingGround(helper, ExoplanetContent.TAU_CETI_G_LEVEL);
    }

    /**
     * Within {@link #FOOTPRINT} blocks of each fixed pad (ADR-006: the origin and seven points 64 blocks out), the
     * widest rocket's footprint, every column's ground is solid, not fluid, and nothing stands on it.
     */
    private static void landingGround(GameTestHelper helper, ResourceKey<Level> key) {
        ServerLevel level = helper.getLevel().getServer().getLevel(key);
        helper.assertTrue(level != null, "Missing Level " + key.location());
        int reach = 64 + FOOTPRINT;
        ChunkPlacementChecks.whenLoaded(helper, level, new BoundingBox(-reach, 0, -reach, reach, 255, reach), () -> {
            for (int[] pad : PADS) {
                for (int x = pad[0] - FOOTPRINT; x <= pad[0] + FOOTPRINT; x++) {
                    for (int z = pad[1] - FOOTPRINT; z <= pad[1] + FOOTPRINT; z++) {
                        int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
                        BlockPos top = new BlockPos(x, ground, z);
                        BlockState state = level.getBlockState(top);
                        helper.assertTrue(state.getFluidState().isEmpty() && state.isFaceSturdy(level, top, Direction.UP),
                                key.location() + " ground " + state + " at " + top);
                        helper.assertTrue(level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) == ground + 1,
                                key.location() + " " + level.getBlockState(top.above()) + " stands on the ground at "
                                        + top);
                    }
                }
            }
        });
    }

    private static RocketEntity landed(GameTestHelper helper, ServerLevel level, UUID logical, String where) {
        RocketEntity rocket = findLogicalRocket(level, logical);
        if (rocket == null) {
            describeMissingRocket(helper, level, logical, where);
        }
        helper.assertTrue(rocket != null, "No rocket at " + where);
        var flight = rocket.flightData().orElseThrow();
        if (flight.state() != RocketFlightState.LANDED) {
            RocketTransferSavedData journal = RocketTransferSavedData.get(level.getServer());
            helper.fail("The rocket did not land at " + where + ": state " + flight.state() + ", transfer "
                    + flight.activeTransferId() + ", journal " + (journal.operational() ? "operational" : "blocked")
                    + ", record " + journal.findByLogicalRocket(logical).map(record -> record.phase().name())
                    .orElse("none") + ", entity ticking " + level.isPositionEntityTicking(rocket.blockPosition())
                    + " at " + rocket.blockPosition());
        }
        return rocket;
    }

    /** One failure-only observation, after the existing fixture lookup has primed its pad chunks. */
    private static void describeMissingRocket(GameTestHelper helper, ServerLevel expected, UUID logical, String where) {
        RocketTransferSavedData journal = RocketTransferSavedData.get(expected.getServer());
        var record = journal.findByLogicalRocket(logical).orElse(null);
        if (record == null) {
            AdvancedRocketryCommunity.LOGGER.info(
                    "ARCE_TAU_CETI_MISSING logical={} where={} test_tick={} journal={} record=none",
                    logical, where, helper.getTick(), journal.operational());
            return;
        }
        var source = expected.getServer().getLevel(ResourceKey.create(Registries.DIMENSION,
                record.sourceSnapshot().sourceDimension()));
        var destination = expected.getServer().getLevel(ResourceKey.create(Registries.DIMENSION,
                record.destinationSnapshot().sourceDimension()));
        var origin = record.destinationSnapshot().sourceOrigin();
        BlockPos position = new BlockPos(origin.x(), origin.y(), origin.z());
        AdvancedRocketryCommunity.LOGGER.info(
                "ARCE_TAU_CETI_MISSING logical={} where={} test_tick={} journal={} phase={} source_level={}"
                        + " source_entity={} destination_level={} destination_entity={} origin={}"
                        + " loaded={} entities_loaded={} entity_ticking={} expected_time={} created_time={}",
                logical, where, helper.getTick(), journal.operational(), record.phase(),
                record.sourceSnapshot().sourceDimension(), entityStatus(source, record.sourceEntityId()),
                record.destinationSnapshot().sourceDimension(),
                record.destinationEntityId().map(id -> entityStatus(destination, id)).orElse("unassigned"), position,
                destination != null && destination.getChunkSource().getChunkNow(origin.x() >> 4, origin.z() >> 4) != null,
                destination != null && destination.areEntitiesLoaded(
                        net.minecraft.world.level.ChunkPos.asLong(origin.x() >> 4, origin.z() >> 4)),
                destination != null && destination.isPositionEntityTicking(position),
                expected.getGameTime(), record.createdAtGameTime());
    }

    private static String entityStatus(ServerLevel level, UUID id) {
        var entity = level == null ? null : level.getEntity(id);
        if (entity instanceof RocketEntity rocket) {
            return rocket.flightData().map(flight -> flight.state().name()).orElse("no_flight")
                    + ":removed=" + rocket.isRemoved() + ":pos=" + rocket.blockPosition();
        }
        return entity == null ? "absent" : "not_rocket";
    }

    private static void guarded(Runnable cleanup, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException | Error exception) {
            cleanup.run();
            throw exception;
        }
    }
}
