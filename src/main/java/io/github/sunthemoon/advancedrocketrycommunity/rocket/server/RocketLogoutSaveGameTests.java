package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.gametest.GameTestTickPacer;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.rejoin.RocketRejoinCodec;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.rejoin.RocketRejoinEvents;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.rejoin.RocketRejoinSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityMountEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** W312: native connected survival logouts, installed player files and uncorrected logins. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RocketLogoutSaveGameTests {
    private RocketLogoutSaveGameTests() { }

    @AfterBatch(batch = "rocket_logout_save")
    public static void afterSave(net.minecraft.server.level.ServerLevel level) { RocketLogoutSaveFixture.closeAbandoned(); }
    @AfterBatch(batch = "rocket_logout_budget")
    public static void afterBudget(net.minecraft.server.level.ServerLevel level) { RocketLogoutSaveFixture.closeAbandoned(); }
    @AfterBatch(batch = "rocket_logout_guarded")
    public static void afterGuarded(net.minecraft.server.level.ServerLevel level) { RocketLogoutSaveFixture.closeAbandoned(); }

    @GameTest(template = "rocket_test", batch = "rocket_logout_save", timeoutTicks = 160)
    public static void offlineOperatorReleaseRejoinsOutsideTheTallRocketUnhurt(GameTestHelper helper) { run(helper, Mode.ASSEMBLED); }
    @GameTest(template = "rocket_test", batch = "rocket_logout_save", timeoutTicks = 160)
    public static void fueledOfflineReleaseUsesTheSameExit(GameTestHelper helper) { run(helper, Mode.FUELED); }
    @GameTest(template = "rocket_test", batch = "rocket_logout_save", timeoutTicks = 160)
    public static void landedOfflineReleaseUsesTheSameExit(GameTestHelper helper) { run(helper, Mode.LANDED); }
    @GameTest(template = "rocket_test", batch = "rocket_logout_save", timeoutTicks = 160)
    public static void removedRocketLeavesTheReturningRiderAtTheSavedExit(GameTestHelper helper) { run(helper, Mode.REMOVED); }
    @GameTest(template = "rocket_test", batch = "rocket_logout_save", timeoutTicks = 160)
    public static void retainedSeatSurvivesLogoutAndReloadThenReconnects(GameTestHelper helper) { run(helper, Mode.KEPT); }
    @GameTest(template = "rocket_test", batch = "rocket_logout_save", timeoutTicks = 160)
    public static void ascentLogoutKeepsTheExistingSavedPositionAndMotion(GameTestHelper helper) { run(helper, Mode.FLIGHT); }
    @GameTest(template = "rocket_test", batch = "rocket_logout_save", timeoutTicks = 160)
    public static void activeTransferLogoutKeepsTheExistingSavedPositionAndMotion(GameTestHelper helper) { run(helper, Mode.TRANSFER); }
    @GameTest(template = "rocket_test", batch = "rocket_logout_save", timeoutTicks = 160)
    public static void returningRecordlessRiderStaysSafeThroughStaleSeatRelease(GameTestHelper helper) { run(helper, Mode.STALE); }
    @GameTest(template = "rocket_test", batch = "rocket_logout_save", timeoutTicks = 160)
    public static void unsupportedExitKeepsTheExistingHighSeatSave(GameTestHelper helper) { run(helper, Mode.UNSUPPORTED); }
    @GameTest(template = "rocket_test", batch = "rocket_logout_save", timeoutTicks = 160)
    public static void obstructedExitKeepsTheExistingHighSeatSave(GameTestHelper helper) { run(helper, Mode.OBSTRUCTED); }
    @GameTest(template = "rocket_test", batch = "rocket_logout_save", timeoutTicks = 160)
    public static void seatedAutosaveIsUnchangedBeforeTheLogoutSave(GameTestHelper helper) { run(helper, Mode.AUTOSAVE); }
    @GameTest(template = "rocket_test", batch = "rocket_logout_save", timeoutTicks = 160)
    public static void cancelledNativeDismountIsNotBypassedForSaving(GameTestHelper helper) { run(helper, Mode.CANCELLED); }
    @GameTest(template = "rocket_test", batch = "rocket_logout_budget", timeoutTicks = 160)
    public static void exhaustedSharedBudgetLeavesTheSaveUnchanged(GameTestHelper helper) { run(helper, Mode.BUDGET); }
    @GameTest(template = "rocket_test", batch = "rocket_logout_guarded", timeoutTicks = 160)
    public static void pendingRejoinGuardOwnsTheLogoutSave(GameTestHelper helper) { run(helper, Mode.GUARDED); }

    private enum Mode { ASSEMBLED, FUELED, LANDED, REMOVED, KEPT, FLIGHT, TRANSFER, STALE, UNSUPPORTED, OBSTRUCTED, AUTOSAVE, CANCELLED, BUDGET, GUARDED }
    private static void run(GameTestHelper helper, Mode mode) { new Scenario(helper, mode).start(); }

    private static final class Scenario {
        final GameTestHelper helper;
        final Mode mode;
        final GameTestTickPacer pacer;
        RocketLogoutSaveFixture f;
        int joinedAt;
        boolean finished;

        Scenario(GameTestHelper helper, Mode mode) {
            this.helper = helper; this.mode = mode; pacer = new GameTestTickPacer(helper.getLevel().getServer());
        }
        void start() { helper.onEachTick(this::tick); }
        private void tick() {
            if (finished) { return; }
            try {
                if (f == null) {
                    var at = helper.absolutePos(RocketLogoutSaveFixture.ORIGIN);
                    var level = helper.getLevel();
                    if (level.getChunkSource().getChunkNow(at.getX() >> 4, at.getZ() >> 4) == null
                            || !level.areEntitiesLoaded(new ChunkPos(at).toLong()) || !level.isPositionEntityTicking(at)) {
                        pacer.pace(); return;
                    }
                    var state = switch (mode) {
                        case FUELED -> RocketFlightState.FUELED;
                        case LANDED, KEPT -> RocketFlightState.LANDED;
                        case FLIGHT -> RocketFlightState.ASCENT;
                        case TRANSFER -> RocketFlightState.TRANSIT;
                        default -> RocketFlightState.ASSEMBLED;
                    };
                    f = new RocketLogoutSaveFixture(helper, state);
                    exercise();
                    if (f.player == null) { complete(); return; }
                    joinedAt = level.getServer().getTickCount();
                } else if (helper.getLevel().getServer().getTickCount() - joinedAt >= (mode == Mode.STALE ? 60 : 20)) {
                    helper.assertTrue(!f.player.hasDisconnected() && f.player.getHealth() == f.player.getMaxHealth(),
                            "Returning survival rider disconnected or lost health during native ticks");
                    if (mode == Mode.KEPT) {
                        helper.assertTrue(f.player.getVehicle() == f.rocket, "Reconnect lost its retained physical seat");
                    } else {
                        helper.assertTrue(!f.player.isPassenger() && f.player.position().distanceTo(f.exit) < 1e-7,
                                "Returning rider left the safe exit during native ticks");
                        helper.assertTrue(!f.player.getBoundingBox().intersects(f.rocket.getBoundingBox()), "Returning rider is inside the rocket");
                        if (mode == Mode.STALE) {
                            helper.assertTrue(f.rocket.flightData().orElseThrow().passengers().assignment(f.playerId).isEmpty(),
                                    "Native stale-seat observation did not release the returning absent rider");
                        }
                    }
                    complete();
                }
            } catch (Throwable failure) {
                finished = true;
                if (f != null) { try { f.close(); } catch (Throwable cleanup) { failure.addSuppressed(cleanup); } }
                if (failure instanceof RuntimeException runtime) { throw runtime; }
                if (failure instanceof Error error) { throw error; }
                throw new java.io.UncheckedIOException((java.io.IOException) failure);
            }
        }
        private void exercise() {
            var before = f.player.saveWithoutId(new net.minecraft.nbt.CompoundTag());
            helper.assertTrue(!before.contains("RootVehicle"), "Mounted autosave embeds a rocket authority");
            if (mode == Mode.AUTOSAVE) {
                helper.getLevel().getServer().getPlayerList().saveAll();
                helper.assertTrue(f.player.getVehicle() == f.rocket && f.disk().get("Pos").equals(before.get("Pos"))
                        && !f.disk().contains("RootVehicle"), "Autosave changed the seated position or detached the rider");
            }
            if (mode == Mode.UNSUPPORTED) { f.block(BlockPos.containing(f.exit).below(), Blocks.AIR.defaultBlockState()); }
            if (mode == Mode.OBSTRUCTED) { f.block(BlockPos.containing(f.exit), Blocks.STONE.defaultBlockState()); }
            if (mode == Mode.BUDGET) {
                var budget = RocketRuntime.passengerExits().orElseThrow().budget;
                for (int i = 0; i < io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.exit.BayExitBudget.CANDIDATES_PER_TICK; i++) {
                    var probe = budget.openProbe().orElse(null); if (probe != null) { probe.close(); }
                }
            }
            var server = helper.getLevel().getServer();
            RocketRejoinSavedData previous = null, guarded = null;
            var cancel = mode == Mode.CANCELLED ? new CancelLogoutDismount(f.player) : null;
            if (cancel != null) { MinecraftForge.EVENT_BUS.register(cancel); }
            if (mode == Mode.GUARDED) {
                previous = RocketRejoinEvents.get(server);
                guarded = RocketRejoinSavedData.load(RocketRejoinCodec.encode(RocketSeatRosterGameTests.rejoinRoot(f.playerId)));
                server.overworld().getDataStorage().set(RocketRejoinSavedData.TYPE.dataName(), guarded);
            }
            try { f.logout(mode == Mode.CANCELLED); }
            finally {
                if (cancel != null) { MinecraftForge.EVENT_BUS.unregister(cancel); }
                if (guarded != null) {
                    guarded.close(); server.overworld().getDataStorage().set(RocketRejoinSavedData.TYPE.dataName(), previous);
                }
            }
            if (switch (mode) { case FLIGHT, TRANSFER, UNSUPPORTED, OBSTRUCTED, CANCELLED, BUDGET, GUARDED -> true; default -> false; }) {
                var saved = f.disk();
                helper.assertTrue(f.mountedWhenSaved, "Excluded logout detached before saving: " + mode);
                helper.assertTrue(saved.get("Pos").equals(before.get("Pos")), "Excluded logout changed its saved position: " + mode);
                if (mode == Mode.FLIGHT || mode == Mode.TRANSFER) {
                    helper.assertTrue(saved.get("Motion").equals(before.get("Motion"))
                            && saved.get("FallDistance").equals(before.get("FallDistance")), "Flight logout changed motion or fall distance");
                }
                return;
            }
            helper.assertTrue(RocketLogoutSaveFixture.position(f.disk()).distanceTo(f.exit) < 1e-7,
                    "Logout saved the seat rather than the exit: expected=" + f.exit + " saved=" + RocketLogoutSaveFixture.position(f.disk()));
            helper.assertTrue(!f.mountedWhenSaved, "Stationary logout saved before native detachment");
            if (mode == Mode.REMOVED) { f.rocket.discard(); }
            else if (mode != Mode.KEPT && mode != Mode.STALE) {
                var source = server.createCommandSourceStack().withLevel(helper.getLevel()).withPermission(2);
                int released = server.getCommands().performPrefixedCommand(source,
                        "arce rocket release-seat " + f.rocket.getStringUUID() + " " + f.playerId);
                helper.assertTrue(released == 1 && f.rocket.flightData().orElseThrow().passengers().assignment(f.playerId).isEmpty(),
                        "Operator did not release the offline seat");
            }
            if (mode == Mode.KEPT) { f.reloadRocket(); }
            f.player = f.join();
            helper.assertTrue(f.player.position().distanceTo(f.exit) < 1e-7 && !f.player.isPassenger(),
                    "Native login did not restore the saved exit: " + f.player.position());
            if (mode == Mode.KEPT) {
                f.reconnect();
                helper.assertTrue(f.player.getVehicle() == f.rocket, "Existing reconnect did not reseat the retained rider");
                f.rocket.positionRider(f.player);
                helper.assertTrue(f.player.getY() > f.rocket.getY() + 9, "Reconnect did not restore the high seat");
            }
            helper.assertTrue(f.player.getHealth() == f.player.getMaxHealth(), "Returning rider lost health at login");
        }
        private void complete() { finished = true; f.close(); helper.succeed(); }
    }

    public static final class CancelLogoutDismount {
        private final ServerPlayer player;
        CancelLogoutDismount(ServerPlayer player) { this.player = player; }
        @SubscribeEvent public void mount(EntityMountEvent event) {
            if (event.isDismounting() && event.getEntityMounting() == player && player.hasDisconnected()) { event.setCanceled(true); }
        }
    }
}
