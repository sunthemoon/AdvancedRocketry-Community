package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.station.controller.StationControllerKind;
import io.github.sunthemoon.advancedrocketrycommunity.station.controller.StationControllerMenu;
import io.github.sunthemoon.advancedrocketrycommunity.station.controller.WarpControllerMenu;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationLimits;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationState;
import io.github.sunthemoon.advancedrocketrycommunity.station.persistence.StationRegistrySavedData;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationManagementCode;
import io.github.sunthemoon.advancedrocketrycommunity.station.warp.StationRocketAuthority;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestBatch;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** F8: current core + station saves reinstalled as the live controller/service authority, not detached copies. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
@Mod.EventBusSubscriber(modid = ModIdentity.MOD_ID)
public final class StationWarpRestartGameTests {
    private static final TicketType<UUID> FIXTURE_TICKET = TicketType.create("arce_w416_warp_restart",
            java.util.Comparator.comparing(UUID::toString));

    private StationWarpRestartGameTests() { }

    /** Opt-in ordering for the restart/isolation regression, after the ordinary batch filter. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void restartBatchesFirst(ServerAboutToStartEvent event) {
        if (!Boolean.getBoolean("advancedrocketrycommunity.w418RestartFirst")
                || !(event.getServer() instanceof GameTestServer server)) { return; }
        List<GameTestBatch> batches = ObfuscationReflectionHelper.getPrivateValue(
                GameTestServer.class, server, "f_177587_");
        batches.sort(Comparator.comparingInt((GameTestBatch batch) -> restartOrder(batch.getName()))
                .thenComparing(GameTestBatch::getName));
        io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity.LOGGER.info(
                "ARCE_W418_RESTART_BATCH_ORDER batches={}", batches.stream().map(GameTestBatch::getName).toList());
    }

    private static int restartOrder(String name) {
        if (name.startsWith("w416_f8_")) { return 0; }
        return name.startsWith("w415_") || name.startsWith("w416_") || name.startsWith("w417_") ? 1 : 2;
    }

    // F8: the final stop fold persists charging once; the stateless restored core cannot replay its old credit.
    @GameTest(template = "empty", batch = "w416_f8_charging_reload", timeoutTicks = 900)
    public static void f8ChargingCoreAndStationReloadThenControllerWarpDebitsOnce(GameTestHelper helper) {
        run(helper, Cut.CHARGING);
    }

    // F8: ADR-044 discards the in-memory countdown; a new controller request is required, then commits once.
    @GameTest(template = "empty", batch = "w416_f8_pending_reload", timeoutTicks = 900)
    public static void f8PendingWarpCoreAndStationReloadRequireFreshConsentAndDebitOnce(GameTestHelper helper) {
        run(helper, Cut.PENDING);
    }

    // F8: a committed relocation is restored with its debit, and cannot be replayed by the controller.
    @GameTest(template = "empty", batch = "w416_f8_committed_reload", timeoutTicks = 900)
    public static void f8CommittedWarpCoreAndStationReloadKeepOrbitAndSingleDebit(GameTestHelper helper) {
        run(helper, Cut.COMMITTED);
    }

    private enum Cut { CHARGING, PENDING, COMMITTED }

    private static void run(GameTestHelper helper, Cut cut) {
        StationWarpGameTests.Fixture f = new StationWarpGameTests.Fixture(helper, "W416 " + cut,
                CelestialIds.EARTH_ID, cut == Cut.CHARGING ? 2_400_000 : 2_500_000);
        StationRocketAuthority previous = f.warp.installRocketAuthority(StationWarpGameTests.NOTHING_IN_MOTION);
        List<BlockPos> placed = new ArrayList<>();
        ChunkPos chunk = new ChunkPos(f.pad);
        boolean[] cleaned = {false};
        Runnable cleanup = () -> {
            if (cleaned[0]) { return; }
            cleaned[0] = true;
            var live = StationRegistrySavedData.get(f.server);
            try {
                f.warp.fold(f.server);
                f.warp.clear();
                f.warp.installRocketAuthority(previous);
                f.online.forEach(f.server.getPlayerList()::remove);
                f.online.clear();
                f.cores.forEach(pos -> f.space.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState()));
                StationControlsGameTests.clear(f, placed);
                // Delete only this station from the latest authority: never roll back unrelated first-warp metadata.
                int balance = live.warpEnergy(f.id());
                if (balance > 0) {
                    var station = live.find(f.id()).orElseThrow();
                    var target = station.orbitBody().equals(CelestialIds.EARTH_ID)
                            ? CelestialIds.MOON_ID : CelestialIds.EARTH_ID;
                    // Teardown only: spend the remaining balance through the ordinary checked debit before deletion.
                    helper.assertTrue(live.checkedRelocation(f.server, station, target, balance)
                                    == StationRegistrySavedData.CheckedUpdate.COMMITTED,
                            "Fixture cleanup did not commit its remaining warp-energy debit");
                }
                helper.assertTrue(live.warpEnergy(f.id()) == 0, "Fixture deletion would drop warp energy");
                live.delete(f.id());
                io.github.sunthemoon.advancedrocketrycommunity.station.notice.StationNoticeSavedData.get(f.server)
                        .remove(f.id());
                f.platforms.removeTemplate(f.space, f.created.cell());
                live.flush(f.server);
            } finally {
                try { restoreOriginalRegistry(helper, f, live); }
                finally { f.space.getChunkSource().removeRegionTicket(FIXTURE_TICKET, chunk, 2, f.id()); }
            }
        };
        RestartTestSupport.cleanup(helper, cleanup);
        f.space.getChunkSource().addRegionTicket(FIXTURE_TICKET, chunk, 2, f.id());
        f.join(f.ownerId, "warpRestart");
        ServerPlayer player = f.server.getPlayerList().getPlayer(f.ownerId);
        BlockPos core = f.pad.east(2);
        f.core(core);
        BlockPos controller = StationControlsGameTests.place(f, placed, StationControllerKind.WARP,
                f.pad.offset(3, 0, 2));
        StationControllerMenu[] menu = {menu(f, player, controller)};
        var sequence = helper.startSequence().thenExecute(() -> {
            if (cut == Cut.CHARGING) {
                var energy = f.space.getBlockEntity(core).getCapability(ForgeCapabilities.ENERGY)
                        .orElseThrow(IllegalStateException::new);
                helper.assertTrue(energy.receiveEnergy(100_000, false) == 100_000
                        && f.warp.pendingCredit(f.id()) == 100_000, "Charging cut did not hold exactly 100000 FE");
            } else {
                request(helper, menu[0], player);
            }
        });
        if (cut != Cut.CHARGING) {
            sequence.thenExecuteAfter(11, () -> confirm(helper, f, controller, menu[0], player));
        }
        sequence.thenExecuteAfter(cut == Cut.COMMITTED ? StationWarpGameTests.COMMIT_DELAY : 1, () -> {
            if (cut == Cut.PENDING) {
                helper.assertTrue(f.warp.countdown(f.id()).orElseThrow().ticksLeft(f.server.getTickCount())
                        == StationLimits.WARP_COUNTDOWN_TICKS - 1, "Pending cut lost its exact countdown");
            }
            if (cut == Cut.COMMITTED) { state(helper, f, CelestialIds.MOON_ID, 500_000); }
            // Normal stop folds credits and discards consent/countdowns. No invented pending-warp NBT is used.
            f.warp.onServerStopping(new ServerStoppingEvent(f.server));
            f.warp.clear();
            StationRegistrySavedData live = StationRegistrySavedData.get(f.server);
            live.flush(f.server);
            CompoundTag coreSave = f.space.getBlockEntity(core).saveWithFullMetadata();
            StationRegistrySavedData restored = StationRegistrySavedData.load(f.stationFile());
            helper.assertTrue(restored.operational() && restored != live, "Station registry failed to reopen");
            f.server.overworld().getDataStorage().set(StationRegistrySavedData.DATA_NAME, restored);
            var old = f.space.getBlockEntity(core);
            var copy = RestartTestSupport.replace(f.space, core, coreSave);
            helper.assertTrue(old.isRemoved() && copy != old && coreSave.equals(copy.saveWithFullMetadata()),
                    "Core did not reopen with its exact current save");
            helper.assertTrue(copy.getCapability(ForgeCapabilities.ENERGY).orElseThrow(IllegalStateException::new)
                            .getEnergyStored() == 0,
                    "Stateless restored core owns energy");
            menu[0] = menu(f, player, controller);
            view(helper, f, menu[0], cut == Cut.COMMITTED ? CelestialIds.MOON_ID : CelestialIds.EARTH_ID,
                    cut == Cut.COMMITTED ? 500_000 : 2_500_000);
            helper.assertTrue(f.warp.confirm(player, StationControlsGameTests.context(f, StationControllerKind.WARP,
                    controller), f.id()).code() == StationManagementCode.WARP_NO_CONFIRMATION,
                    "A pre-reload confirmation survived the stop");
        }).thenExecuteAfter(StationWarpGameTests.COMMIT_DELAY, () -> {
            view(helper, f, menu[0], cut == Cut.COMMITTED ? CelestialIds.MOON_ID : CelestialIds.EARTH_ID,
                    cut == Cut.COMMITTED ? 500_000 : 2_500_000);
            if (cut == Cut.COMMITTED) {
                helper.assertTrue(f.warp.request(player, StationControlsGameTests.context(f, StationControllerKind.WARP,
                        controller), CelestialIds.MOON_ID).code() == StationManagementCode.WARP_SAME_ORBIT,
                        "Restored committed target was accepted a second time");
            } else { request(helper, menu[0], player); }
        });
        if (cut != Cut.COMMITTED) {
            sequence.thenExecuteAfter(11, () -> confirm(helper, f, controller, menu[0], player))
                    .thenExecuteAfter(StationWarpGameTests.COMMIT_DELAY,
                            () -> view(helper, f, menu[0], CelestialIds.MOON_ID, 500_000));
        }
        sequence.thenExecuteAfter(StationWarpGameTests.COMMIT_DELAY, () -> {
            view(helper, f, menu[0], CelestialIds.MOON_ID, 500_000);
            StationRegistrySavedData disk = StationRegistrySavedData.load(f.stationFile());
            helper.assertTrue(disk.warpEnergy(f.id()) == 500_000
                    && disk.find(f.id()).orElseThrow().equals(f.created.withOrbitBody(CelestialIds.MOON_ID)),
                    "Durable warp was not exactly one orbit change and debit");
        }).thenExecute(cleanup).thenSucceed();
    }

    private static void restoreOriginalRegistry(GameTestHelper helper, StationWarpGameTests.Fixture f,
                                                StationRegistrySavedData live) {
        if (live != f.data) {
            // Startup-bound analyzer/environment/solar services retain f.data. Reinstalling its stale state
            // would resurrect the fixture or roll back the clock/outbox. Copy the entire cleaned authority,
            // including the registry model's station, reservation, region, energy and access indices.
            try {
                for (var field : StationRegistrySavedData.class.getDeclaredFields()) {
                    if (!Modifier.isStatic(field.getModifiers())) {
                        field.setAccessible(true);
                        field.set(f.data, field.get(live));
                    }
                }
                f.data.setDirty(live.isDirty());
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("Cannot restore the startup-bound station authority", failure);
            }
        }
        f.server.overworld().getDataStorage().set(StationRegistrySavedData.DATA_NAME, f.data);
        helper.assertTrue(StationRegistrySavedData.get(f.server) == f.data
                        && f.data.save(new CompoundTag()).equals(live.save(new CompoundTag())),
                "Startup-bound readers and the live station authority differ after cleanup");
        helper.assertTrue(f.data.find(f.id()).isEmpty() && f.data.findAt(f.pad.getX(), f.pad.getZ()).isEmpty()
                        && f.data.warpEnergy(f.id()) == 0
                        && f.data.reservations().stream().noneMatch(r -> r.stationId().equals(f.id()))
                        && f.data.nextAccessCandidate(f.ownerId, false, null).isEmpty(),
                "Restart fixture retained a station, region claim, balance, reservation or owner access link");
    }

    private static StationControllerMenu menu(StationWarpGameTests.Fixture f, ServerPlayer player, BlockPos pos) {
        StationState station = StationRegistrySavedData.get(f.server).find(f.id()).orElseThrow();
        var open = StationControllerMenu.openData(StationControllerKind.WARP, player, pos, station);
        var menu = StationControllerMenu.server(StationControllerKind.WARP, 9, player.getInventory(), f.space,
                pos, ModBlocks.WARP_CONTROLLER.get(), open, f.id());
        menu.broadcastChanges();
        return menu;
    }

    private static void request(GameTestHelper helper, StationControllerMenu menu, ServerPlayer player) {
        int target = menu.names().subList(1, menu.names().size()).indexOf(CelestialIds.MOON_ID);
        helper.assertTrue(target >= 0, "Controller does not offer Moon");
        for (int index = 0; index < target; index++) {
            StationControlsGameTests.send(menu, player, WarpControllerMenu.BUTTON_NEXT, 0, 0L);
        }
        StationControlsGameTests.click(helper, menu, player, WarpControllerMenu.BUTTON_REQUEST, target, 0L,
                StationManagementCode.WARP_ISSUED);
        helper.assertTrue(menu.lastView().orElseThrow().value(WarpControllerMenu.COST) == 2_000_000,
                "Controller quoted another energy debit");
    }

    private static void confirm(GameTestHelper helper, StationWarpGameTests.Fixture f, BlockPos controller,
                                StationControllerMenu menu, ServerPlayer player) {
        menu.broadcastChanges();
        StationControlsGameTests.click(helper, menu, player, WarpControllerMenu.BUTTON_CONFIRM, 0,
                menu.lastView().orElseThrow().token(), StationManagementCode.WARP_STARTED);
        helper.assertTrue(f.warp.countdown(f.id()).orElseThrow().ticksLeft(f.server.getTickCount())
                        == StationLimits.WARP_COUNTDOWN_TICKS
                && menu.lastView().orElseThrow().value(WarpControllerMenu.COUNTDOWN) == 10,
                "Confirmed warp does not begin at exactly 200 ticks / 10 seconds");
        state(helper, f, CelestialIds.EARTH_ID, 2_500_000);
        helper.assertTrue(f.warp.confirm(player, StationControlsGameTests.context(f, StationControllerKind.WARP,
                controller), f.id()).code() == StationManagementCode.WARP_NO_CONFIRMATION,
                "Confirmation was not consumed exactly once");
    }

    private static void state(GameTestHelper helper, StationWarpGameTests.Fixture f, ResourceLocation orbit, int balance) {
        var data = StationRegistrySavedData.get(f.server);
        helper.assertTrue(data.find(f.id()).orElseThrow().equals(f.created.withOrbitBody(orbit))
                && data.warpEnergy(f.id()) == balance, "Restored warp state differs: " + orbit + "/" + balance);
    }

    private static void view(GameTestHelper helper, StationWarpGameTests.Fixture f, StationControllerMenu menu,
                             ResourceLocation orbit, int balance) {
        state(helper, f, orbit, balance);
        menu.broadcastChanges();
        var view = menu.lastView().orElseThrow();
        helper.assertTrue(view.value(WarpControllerMenu.BALANCE) == balance
                && view.value(WarpControllerMenu.PENDING) == 0 && view.value(WarpControllerMenu.COUNTDOWN) == -1
                && menu.names().get(view.value(WarpControllerMenu.ORBIT)).equals(orbit)
                && f.warp.countdown(f.id()).isEmpty() && f.warp.pendingCredit(f.id()) == 0,
                "Restored controller/service view differs from registry or retained a countdown/credit");
    }

}
