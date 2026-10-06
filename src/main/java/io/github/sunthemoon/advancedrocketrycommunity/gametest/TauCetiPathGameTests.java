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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
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
    private static final int OBSERVATION_BYTES = 2048;
    private static final String OBSERVATION_PREFIX = "ARCE_TAU_CETI_MISSING";

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
        final RocketTransferSavedData observerJournal;
        try {
            observerJournal = observerJournal(fixture.server);
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
            landed(helper, fixture.space, logical[0], "the station", observerJournal);
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
                RocketEntity docked = landed(helper, fixture.space, logical[0], "the warped station", observerJournal);
                RocketFlightRequestResult down = RocketRuntime.requestAdminFlight(docked,
                        new TravelTarget.BodySurface(ExoplanetContent.TAU_CETI_F), UUID.randomUUID());
                helper.assertTrue(down.success(), "Station-to-Tau Ceti f launch failed: " + down.code());
                helper.runAfterDelay(FLIGHT, () -> guarded(cleanup, () -> {
                    RocketEntity onF = landed(helper, surface, logical[0], "Tau Ceti f", observerJournal);
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
                        RocketEntity back = landed(helper, fixture.space, logical[0], "the station on return",
                                observerJournal);
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

    private static RocketEntity landed(GameTestHelper helper, ServerLevel level, UUID logical, String where,
            RocketTransferSavedData observerJournal) {
        String before = missingSample(helper, level, logical, where, observerJournal, "PRE");
        RocketEntity rocket = findLogicalRocket(level, logical);
        if (rocket == null) {
            String after = missingSample(helper, level, logical, where, observerJournal, "POST");
            emitMissing(before);
            emitMissing(after);
            // BEGIN transfer service diagnostics
            observeServiceFailure(helper, observerJournal, logical);
            // END transfer service diagnostics
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

    // BEGIN transfer service diagnostics
    /** One extra failure line; no callback, world activation or assertion replacement. */
    private static void observeServiceFailure(GameTestHelper helper, RocketTransferSavedData journal, UUID logical) {
        String line;
        try {
            var record = journal == null ? null : journal.findByLogicalRocket(logical).orElse(null);
            UUID transferId = record == null ? null : record.transferId();
            String state = transferId == null ? "installed=UNOBSERVED"
                    : RocketRuntime.transferFailureDiagnostics(transferId);
            line = "ARCE_TRANSFER_SERVICE_FAILURE fixture=TAU transfer="
                    + (transferId == null ? "UNOBSERVED" : transferId.toString())
                    + " current_server_tick=" + helper.getLevel().getServer().getTickCount() + " " + state;
            if (line.length() > 512) {
                line = "ARCE_TRANSFER_SERVICE_FAILURE fixture=TAU diagnostic=UNAVAILABLE";
            } else {
                for (int index = 0; index < line.length(); index++) {
                    if (line.charAt(index) < 32 || line.charAt(index) > 126) {
                        line = "ARCE_TRANSFER_SERVICE_FAILURE fixture=TAU diagnostic=UNAVAILABLE";
                        break;
                    }
                }
            }
        } catch (RuntimeException | Error observationFailure) {
            line = "ARCE_TRANSFER_SERVICE_FAILURE fixture=TAU diagnostic=UNAVAILABLE";
        }
        try {
            AdvancedRocketryCommunity.LOGGER.warn("{}", line);
        } catch (RuntimeException | Error loggingFailure) {
            // One logging attempt only: preserve the original assertion and cleanup even after a partial write.
        }
    }
    // END transfer service diagnostics

    /** The original clearTransferJournal call has already initialized this fixture's journal. */
    private static RocketTransferSavedData observerJournal(MinecraftServer server) {
        try {
            return RocketTransferSavedData.get(server);
        } catch (RuntimeException | Error exception) {
            return null;
        }
    }

    /** Immutable scalar text only; no world/entity/record survives a capture in the sample. */
    private static String missingSample(GameTestHelper helper, ServerLevel expected, UUID logical, String where,
            RocketTransferSavedData journal, String sample) {
        try {
            long nanos = System.nanoTime();
            if (journal == null) {
                return unavailableSample(sample, logical, "JOURNAL_UNAVAILABLE");
            }
            boolean operational = journal.operational();
            var record = journal.findByLogicalRocket(logical).orElse(null);
            var source = record == null ? null : expected.getServer().getLevel(ResourceKey.create(Registries.DIMENSION,
                    record.sourceSnapshot().sourceDimension()));
            var destination = record == null ? null : expected.getServer().getLevel(ResourceKey.create(
                    Registries.DIMENSION, record.destinationSnapshot().sourceDimension()));
            UUID destinationId = record == null ? null : record.destinationEntityId().orElse(null);
            EntitySample sourceEntity = record == null ? unknownEntity("NA") : entitySample(source, record.sourceEntityId());
            EntitySample destinationEntity = destinationId == null ? unknownEntity("NA") : entitySample(destination, destinationId);
            var origin = record == null ? null : record.destinationSnapshot().sourceOrigin();
            String loaded = "NA";
            String entitiesLoaded = "NA";
            String entityTicking = "NA";
            if (destination != null && origin != null) {
                loaded = yesNo(destination.getChunkSource().getChunkNow(origin.x() >> 4, origin.z() >> 4) != null);
                entitiesLoaded = yesNo(destination.areEntitiesLoaded(ChunkPos.asLong(origin.x() >> 4, origin.z() >> 4)));
                entityTicking = yesNo(destination.isPositionEntityTicking(new BlockPos(origin.x(), origin.y(), origin.z())));
            }
            StringBuilder text = new StringBuilder(OBSERVATION_BYTES).append(OBSERVATION_PREFIX);
            token(text, "sample", sample, 4);
            token(text, "logical", logical.toString(), 36);
            token(text, "where", whereToken(where), 32);
            token(text, "test_tick", Long.toString(helper.getTick()), 20);
            token(text, "journal", yesNo(operational), 3);
            token(text, "record", record == null ? "NONE" : "FOUND", 11);
            token(text, "transfer", record == null ? "NA" : record.transferId().toString(), 36);
            token(text, "phase", record == null ? "NA" : record.phase().name(), 32);
            token(text, "expected_level", dimensionId(expected.dimension().location()), 128);
            token(text, "expected_time", Long.toString(expected.getGameTime()), 20);
            token(text, "source_level", record == null ? "NA" : dimensionId(record.sourceSnapshot().sourceDimension()), 128);
            token(text, "source_entity", record == null ? "NA" : record.sourceEntityId().toString(), 36);
            token(text, "source_observed", sourceEntity.observed(), 36);
            token(text, "source_kind", sourceEntity.kind(), 32);
            token(text, "source_state", sourceEntity.state(), 32);
            token(text, "source_removed", sourceEntity.removed(), 3);
            token(text, "source_x", sourceEntity.x(), 11);
            token(text, "source_y", sourceEntity.y(), 11);
            token(text, "source_z", sourceEntity.z(), 11);
            token(text, "source_time", source == null ? "NA" : Long.toString(source.getGameTime()), 20);
            token(text, "destination_level", record == null ? "NA" : dimensionId(record.destinationSnapshot().sourceDimension()), 128);
            token(text, "destination_entity", record == null ? "NA" : destinationId == null ? "UNASSIGNED" : destinationId.toString(), 36);
            token(text, "destination_observed", destinationEntity.observed(), 36);
            token(text, "destination_kind", destinationEntity.kind(), 32);
            token(text, "destination_state", destinationEntity.state(), 32);
            token(text, "destination_removed", destinationEntity.removed(), 3);
            token(text, "destination_x", destinationEntity.x(), 11);
            token(text, "destination_y", destinationEntity.y(), 11);
            token(text, "destination_z", destinationEntity.z(), 11);
            token(text, "destination_time", destination == null ? "NA" : Long.toString(destination.getGameTime()), 20);
            token(text, "origin_x", origin == null ? "NA" : Integer.toString(origin.x()), 11);
            token(text, "origin_y", origin == null ? "NA" : Integer.toString(origin.y()), 11);
            token(text, "origin_z", origin == null ? "NA" : Integer.toString(origin.z()), 11);
            token(text, "loaded", loaded, 3);
            token(text, "entities_loaded", entitiesLoaded, 3);
            token(text, "entity_ticking", entityTicking, 3);
            token(text, "created_time", record == null ? "NA" : Long.toString(record.createdAtGameTime()), 20);
            token(text, "sample_nanos", Long.toString(nanos), 20);
            token(text, "diagnostic", record == null ? "NO_RECORD" : "OK", 32);
            return text.toString();
        } catch (ObservationFormatFailure exception) {
            return unavailableSample(sample, logical, "FORMAT_FAILED");
        } catch (RuntimeException | Error exception) {
            return unavailableSample(sample, logical, "CAPTURE_FAILED");
        }
    }

    private static EntitySample entitySample(ServerLevel level, UUID id) {
        if (level == null) {
            return unknownEntity("LEVEL_MISSING");
        }
        var entity = level.getEntity(id);
        if (entity instanceof RocketEntity rocket) {
            BlockPos position = rocket.blockPosition();
            return new EntitySample(rocket.getUUID().toString(), "ROCKET",
                    rocket.flightData().map(flight -> flight.state().name()).orElse("NO_FLIGHT"), yesNo(rocket.isRemoved()),
                    Integer.toString(position.getX()), Integer.toString(position.getY()), Integer.toString(position.getZ()));
        }
        return unknownEntity(entity == null ? "ABSENT" : "NOT_ROCKET");
    }

    private static EntitySample unknownEntity(String kind) {
        return new EntitySample("NA", kind, "NA", "NA", "NA", "NA", "NA");
    }

    private static String whereToken(String where) {
        return switch (where) {
            case "the station" -> "the_station";
            case "the warped station" -> "the_warped_station";
            case "Tau Ceti f" -> "Tau_Ceti_f";
            case "the station on return" -> "the_station_on_return";
            default -> throw new ObservationFormatFailure();
        };
    }

    private static String dimensionId(ResourceLocation id) {
        String namespace = id.getNamespace();
        String path = id.getPath();
        if (namespace.length() > 127 || path.length() > 127 || namespace.length() > 127 - path.length()) {
            return "OVERSIZE_ID";
        }
        return namespace + ":" + path;
    }

    private static void token(StringBuilder text, String key, String value, int maximum) {
        if (value.length() > maximum) {
            throw new ObservationFormatFailure();
        }
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) <= 32 || value.charAt(i) > 126) {
                throw new ObservationFormatFailure();
            }
        }
        int remaining = OBSERVATION_BYTES - text.length();
        if (remaining < key.length() + 2 || remaining - key.length() - 2 < value.length()) {
            throw new ObservationFormatFailure();
        }
        text.append(' ').append(key).append('=').append(value);
    }

    private static String yesNo(boolean value) {
        return value ? "YES" : "NO";
    }

    private static String unavailableSample(String sample, UUID logical, String diagnostic) {
        return OBSERVATION_PREFIX + " sample=" + sample + " logical=" + (logical == null ? "NA" : logical.toString())
                + " diagnostic=" + diagnostic;
    }

    private static void emitMissing(String text) {
        try {
            AdvancedRocketryCommunity.LOGGER.info(text);
        } catch (RuntimeException | Error exception) {
            // Diagnostics must not replace the original missing-rocket assertion or add another log attempt.
        }
    }

    private record EntitySample(String observed, String kind, String state, String removed, String x, String y, String z) {
    }

    private static final class ObservationFormatFailure extends RuntimeException {
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
