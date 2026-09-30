package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import com.mojang.authlib.GameProfile;
import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.content.SatelliteIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.SatelliteOperationCode;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteBlueprint;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteKind;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteKindState;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteState;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteStats;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.network.SurveyScanResultPacket;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.SatelliteMissionSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.scan.ScanSettings;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.scan.SurveyScanService;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.service.SatelliteRuntime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.Tags;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** ADR-049 section 8 against a live Level: payment, limits, loaded-only reads and cancellation. */
@GameTestHolder(AdvancedRocketryCommunity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SurveyScanGameTests {
    private static final ResourceLocation SURVEY = ModIdentity.id("survey_satellite");

    private SurveyScanGameTests() {
    }

    @GameTest(template = "empty", batch = "satellite", timeoutTicks = 30)
    public static void aPaidScanCountsTheLoadedCellExactly(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        FakePlayer owner = player(helper, "SurveyScanner");
        for (int offset = 0; offset < 3; offset++) {
            helper.setBlock(new BlockPos(1 + offset, 1, 1), Blocks.IRON_ORE);
        }
        SatelliteIdentity identity = launch(server, owner.getUUID(), CelestialIds.EARTH_ID, 10_720L);
        // Counted first, in the same server tick as the whole scan below, so nothing changes in between.
        long[] counted = count(helper.getLevel(), owner.getBlockX(), owner.getBlockZ());
        List<SurveyScanResultPacket> results = new ArrayList<>();
        SurveyScanService service = new SurveyScanService(SatelliteRuntime::celestialCatalog,
                () -> ScanSettings.DEFAULTS, (player, packet) -> results.add(packet));

        helper.assertTrue(service.request(owner, identity) == SatelliteOperationCode.SUCCESS, "A charged scan was refused");
        SatelliteKindState.Survey paid = (SatelliteKindState.Survey) SatelliteMissionSavedData.get(server)
                .satellite(identity.satelliteId()).orElseThrow().kindState();
        helper.assertTrue(paid.charge() == 9_720L, "The scan did not cost exactly its scan energy: " + paid.charge());
        helper.assertTrue(service.request(owner, identity) == SatelliteOperationCode.RATE_LIMITED,
                "A second scan started while the first ran");
        for (int tick = 0; tick < 100 && results.isEmpty(); tick++) {
            service.tick(server);
        }
        helper.assertTrue(results.size() == 1 && service.activeJobs() == 0, "The scan did not finish exactly once");
        SurveyScanResultPacket result = results.get(0);
        helper.assertTrue(result.radius() == 16 && result.cell() == 16 && result.cells().size() == 4
                        && result.centreX() == owner.getBlockX() && result.centreZ() == owner.getBlockZ(),
                "The scan geometry does not match the server-derived centre and the snapshot");
        // The owner's own column starts cell 3 (the south-east cell): x and z from the centre to centre + 15.
        SurveyScanResultPacket.Cell centre = result.cells().get(3);
        helper.assertTrue(!centre.unknown() && counted[1] > 0 && centre.ratio() == (int) (counted[0] * 65_535L / counted[1]),
                "The ore share differs from a direct count: " + centre + " vs " + counted[0] + "/" + counted[1]);
        helper.assertTrue(counted[0] >= 3, "The placed ores were not counted");
        helper.assertTrue(service.request(owner, identity) == SatelliteOperationCode.RATE_LIMITED,
                "The per-player cooldown was not applied");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "satellite", timeoutTicks = 30)
    public static void scansAreRefusedBeforePaymentAndCancelledByMovement(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        FakePlayer owner = player(helper, "SurveyRefusals");
        List<SurveyScanResultPacket> results = new ArrayList<>();
        SurveyScanService service = new SurveyScanService(SatelliteRuntime::celestialCatalog,
                () -> new ScanSettings(1, ScanSettings.MIN_COOLDOWN_TICKS, 64), (player, packet) -> results.add(packet));

        SatelliteIdentity uncharged = launch(server, owner.getUUID(), CelestialIds.EARTH_ID, 0L);
        expect(helper, service, owner, uncharged, SatelliteOperationCode.NO_POWER, "an empty battery");
        SatelliteIdentity moon = launch(server, owner.getUUID(), CelestialIds.MOON_ID, 10_720L);
        expect(helper, service, owner, moon, SatelliteOperationCode.TARGET_NOT_ALLOWED, "a scan from another body");
        SatelliteIdentity lost = launch(server, owner.getUUID(), ModIdentity.id("removed_body"), 10_720L);
        expect(helper, service, owner, lost, SatelliteOperationCode.BODY_UNAVAILABLE, "a removed orbit body");
        SatelliteIdentity unknown = new SatelliteIdentity(UUID.randomUUID(), owner.getUUID(), SURVEY, SatelliteKind.SURVEY,
                uncharged.components());
        expect(helper, service, owner, unknown, SatelliteOperationCode.SATELLITE_NOT_FOUND, "an unregistered satellite");
        SatelliteIdentity earth = launch(server, owner.getUUID(), CelestialIds.EARTH_ID, 10_720L);
        expect(helper, service, player(helper, "SurveyIntruder"), earth, SatelliteOperationCode.UNAUTHORIZED,
                "another player's chip");
        SatelliteState before = SatelliteMissionSavedData.get(server).satellite(earth.satelliteId()).orElseThrow();

        helper.assertTrue(service.request(owner, earth) == SatelliteOperationCode.SUCCESS, "A valid scan was refused");
        FakePlayer second = player(helper, "SurveySecond");
        SatelliteIdentity secondChip = launch(server, second.getUUID(), CelestialIds.EARTH_ID, 10_720L);
        expect(helper, service, second, secondChip, SatelliteOperationCode.CAPACITY_REACHED, "a full job table");
        service.tick(server);
        owner.setPos(owner.getX() + 100.0D, owner.getY(), owner.getZ());
        service.tick(server);
        helper.assertTrue(service.activeJobs() == 0 && results.isEmpty(), "Moving away did not cancel the scan");
        helper.assertTrue(!SatelliteMissionSavedData.get(server).satellite(earth.satelliteId()).orElseThrow()
                .kindState().equals(before.kindState()), "The cancelled scan was refunded");
        helper.succeed();
    }

    private static void expect(GameTestHelper helper, SurveyScanService service, FakePlayer player,
                               SatelliteIdentity identity, SatelliteOperationCode expected, String what) {
        SatelliteOperationCode actual = service.request(player, identity);
        helper.assertTrue(actual == expected, "Expected " + expected + " for " + what + " but got " + actual);
    }

    /** Ores and non-air blocks in the 16 × 16 columns starting at the centre, from the minimum to maximum height. */
    private static long[] count(ServerLevel level, int x0, int z0) {
        long ores = 0L;
        long solids = 0L;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = x0; x < x0 + 16; x++) {
            for (int z = z0; z < z0 + 16; z++) {
                for (int y = level.getMinBuildHeight(); y < level.getMaxBuildHeight(); y++) {
                    BlockState state = level.getBlockState(cursor.set(x, y, z));
                    if (!state.isAir()) {
                        solids++;
                        if (state.is(Tags.Blocks.ORES)) {
                            ores++;
                        }
                    }
                }
            }
        }
        return new long[] {ores, solids};
    }

    private static SatelliteIdentity launch(MinecraftServer server, UUID ownerId, ResourceLocation orbit, long charge) {
        List<ResourceLocation> components = List.of(ModIdentity.id("satellite_chassis"),
                ModIdentity.id("survey_scanner_module"), ModIdentity.id("satellite_solar_module"),
                ModIdentity.id("data_storage_unit"), ModIdentity.id("satellite_battery"));
        UUID satelliteId = UUID.randomUUID();
        SatelliteMissionSavedData.get(server).launchIdle(time -> SatelliteState.launchIdle(satelliteId, SURVEY, ownerId,
                time, orbit, new SatelliteBlueprint(components, false, new SatelliteStats(4, 10_720, 1_000, 0, 10)),
                new SatelliteKindState.Survey(charge, time, 1_000, 16, 16)), server.overworld().getGameTime());
        return new SatelliteIdentity(satelliteId, ownerId, SURVEY, SatelliteKind.SURVEY, components);
    }

    private static FakePlayer player(GameTestHelper helper, String name) {
        FakePlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
        BlockPos position = helper.absolutePos(new BlockPos(1, 2, 1));
        player.setPos(position.getX() + 0.5D, position.getY(), position.getZ() + 0.5D);
        return player;
    }
}
