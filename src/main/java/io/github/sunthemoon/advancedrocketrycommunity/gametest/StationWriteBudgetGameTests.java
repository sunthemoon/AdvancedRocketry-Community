package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialDefaults;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalog;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalogManager;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.station.forge.StationPlatformGenerator;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationLimits;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationState;
import io.github.sunthemoon.advancedrocketrycommunity.station.persistence.StationRegistrySavedData;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationCreationService;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationManagementCode;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationManager;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationWriteBudget;
import io.github.sunthemoon.advancedrocketrycommunity.station.warp.StationWarpService;
import io.github.sunthemoon.advancedrocketrycommunity.station.warp.WarpSettings;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Final v1.5 review B9 on the live server: one server-wide spacing between checked station writes.
 * A gravity write on one station makes another station's write in the same window "busy" without any
 * change, and a due warp countdown waits for the spacing and then commits once.
 */
@GameTestHolder(AdvancedRocketryCommunity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StationWriteBudgetGameTests {
    private StationWriteBudgetGameTests() {
    }

    @GameTest(template = "empty", batch = "station_write_budget", timeoutTicks = 400)
    public static void checkedWritesShareOneServerWideSpacing(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel space = server.getLevel(CelestialIds.SPACE_LEVEL);
        StationRegistrySavedData data = StationRegistrySavedData.get(server);
        helper.assertTrue(space != null && data.updatesAvailable(), "Station authority or Space is unavailable");
        // A spacing far longer than the test, whatever the registry size, until the test lifts it.
        AtomicInteger perHundred = new AtomicInteger(100_000);
        StationWriteBudget budget = new StationWriteBudget(perHundred::get);
        CelestialCatalogManager catalogs = new CelestialCatalogManager();
        helper.assertTrue(catalogs.applyCandidate(CelestialCatalog.create(CelestialDefaults.definitions())),
                "The private catalog was rejected");
        StationManager manager = new StationManager(catalogs, budget);
        StationWarpService warp = new StationWarpService(catalogs, system -> false, () -> WarpSettings.DEFAULTS, budget);
        warp.installRocketAuthority((ignoredServer, ignoredStation) -> false);
        StationPlatformGenerator platforms = new StationPlatformGenerator();
        StationCreationService creation = new StationCreationService(platforms, body -> true);
        UUID firstOwner = UUID.randomUUID();
        UUID secondOwner = UUID.randomUUID();
        StationState first = creation.create(server, firstOwner, "Budget one", CelestialIds.EARTH_ID, false)
                .station().orElseThrow();
        StationState second = creation.create(server, secondOwner, "Budget two", CelestialIds.EARTH_ID, false)
                .station().orElseThrow();
        List<ServerPlayer> online = new ArrayList<>();
        BlockPos core = pad(second).east(2);
        boolean scheduled = false;
        try {
            ServerPlayer one = join(server, space, online, firstOwner, "budgetOne", pad(first));
            ServerPlayer two = join(server, space, online, secondOwner, "budgetTwo", pad(second));
            helper.assertTrue(manager.setGravity(one, true, 40).code() == StationManagementCode.GRAVITY_SET,
                    "The first gravity write was refused");
            StationState secondBefore = data.find(second.stationId()).orElseThrow();
            helper.assertTrue(manager.setGravity(two, true, 40).code() == StationManagementCode.REGISTRY_BUSY,
                    "Another station's write inside the spacing was not refused as busy");
            helper.assertTrue(data.find(second.stationId()).orElseThrow().equals(secondBefore),
                    "A busy refusal changed the station");

            // A warp confirmed inside the spacing stays due, then commits once the spacing is lifted.
            data.foldWarpCredits(Map.of(second.stationId(), 2_000_000));
            space.setBlockAndUpdate(core, ModBlocks.WARP_CORE.get().defaultBlockState());
            two.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(core));
            helper.assertTrue(warp.request(two, true, CelestialIds.MOON_ID).code() == StationManagementCode.WARP_ISSUED,
                    "The warp request was refused");
            helper.assertTrue(warp.confirm(two, true, second.stationId()).code() == StationManagementCode.WARP_STARTED,
                    "The countdown did not start");
            helper.onEachTick(() -> warp.onServerTick(
                    new TickEvent.ServerTickEvent(TickEvent.Phase.END, () -> true, server)));
            scheduled = true;
            helper.runAfterDelay((int) StationLimits.WARP_COUNTDOWN_TICKS + 20, () -> {
                try {
                    helper.assertTrue(warp.countdown(second.stationId()).isPresent()
                                    && data.find(second.stationId()).orElseThrow().orbitBody().equals(CelestialIds.EARTH_ID),
                            "A due warp committed inside the spacing");
                    perHundred.set(0);
                    helper.runAfterDelay(3, () -> {
                        try {
                            helper.assertTrue(warp.countdown(second.stationId()).isEmpty()
                                            && data.find(second.stationId()).orElseThrow().orbitBody()
                                            .equals(CelestialIds.MOON_ID)
                                            && data.warpEnergy(second.stationId()) == 0,
                                    "The waiting warp did not commit once when the spacing ended");
                        } finally {
                            cleanup(server, space, data, platforms, online, core, first, second);
                        }
                        helper.succeed();
                    });
                } catch (RuntimeException | Error failure) {
                    cleanup(server, space, data, platforms, online, core, first, second);
                    throw failure;
                }
            });
        } finally {
            if (!scheduled) {
                cleanup(server, space, data, platforms, online, core, first, second);
            }
        }
    }

    private static ServerPlayer join(MinecraftServer server, ServerLevel space, List<ServerPlayer> online, UUID id,
                                     String name, BlockPos position) {
        space.getChunkAt(position); // Test setup only.
        ServerPlayer player = ConnectedTestPlayers.join(server, id, name, space, position, new ArrayList<>());
        online.add(player);
        return player;
    }

    private static BlockPos pad(StationState station) {
        return new BlockPos(station.landingPad().x(), StationLimits.LANDING_Y, station.landingPad().z());
    }

    private static void cleanup(MinecraftServer server, ServerLevel space, StationRegistrySavedData data,
                                StationPlatformGenerator platforms, List<ServerPlayer> online, BlockPos core,
                                StationState... stations) {
        online.forEach(server.getPlayerList()::remove);
        online.clear();
        space.setBlockAndUpdate(core, Blocks.AIR.defaultBlockState());
        for (StationState station : stations) {
            data.delete(station.stationId());
            platforms.removeTemplate(space, station.cell());
        }
        data.flush(server);
    }
}
