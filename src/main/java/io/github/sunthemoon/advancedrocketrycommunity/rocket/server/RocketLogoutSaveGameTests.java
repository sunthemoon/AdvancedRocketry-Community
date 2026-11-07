package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.gametest.GameTestTickPacer;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.BayReceipts;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.deployment.BayClaim;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.deployment.BayClaimBook;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.deployment.BayMember;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.exit.BayExitSafety;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.rejoin.RocketRejoinCodec;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.rejoin.RocketRejoinEvents;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.rejoin.RocketRejoinRoot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.rejoin.RocketRejoinSavedData;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityMountEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** W312: native connected survival logouts, installed player files and uncorrected logins. W312F adds R375's cases. */
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
    @AfterBatch(batch = "rocket_logout_fenced")
    public static void afterFenced(net.minecraft.server.level.ServerLevel level) { RocketLogoutSaveFixture.closeAbandoned(); }
    @AfterBatch(batch = "rocket_logout_stop")
    public static void afterStop(net.minecraft.server.level.ServerLevel level) { RocketLogoutSaveFixture.closeAbandoned(); }
    @AfterBatch(batch = "rocket_logout_failure")
    public static void afterFailure(net.minecraft.server.level.ServerLevel level) { RocketLogoutSaveFixture.closeAbandoned(); }

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

    // W312F (R375 item 1): finding 7-1's field shape, then each logout-only rule on its own.
    @GameTest(template = "rocket_test", batch = "rocket_logout_save", timeoutTicks = 200)
    public static void rocketOnItsAssemblerAboveALowerPlatformSavesTheExit(GameTestHelper helper) { run(helper, Mode.ASSEMBLER); }
    @GameTest(template = "rocket_test", batch = "rocket_logout_save", timeoutTicks = 160)
    public static void ownRocketBoxOverTheExitDoesNotBlockTheSave(GameTestHelper helper) { run(helper, Mode.WIDE); }
    @GameTest(template = "rocket_test", batch = "rocket_logout_save", timeoutTicks = 160)
    public static void threeBlockFallToStableSupportSavesTheExit(GameTestHelper helper) { run(helper, Mode.DROP3); }
    @GameTest(template = "rocket_test", batch = "rocket_logout_save", timeoutTicks = 160)
    public static void fourBlockFallKeepsTheExistingHighSeatSave(GameTestHelper helper) { run(helper, Mode.DROP4); }
    @GameTest(template = "rocket_test", batch = "rocket_logout_save", timeoutTicks = 160)
    public static void hazardousLandingKeepsTheExistingHighSeatSave(GameTestHelper helper) { run(helper, Mode.DROP_HAZARD); }
    @GameTest(template = "rocket_test", batch = "rocket_logout_save")
    public static void logoutStructuralBlocksAreFullCubesWithoutHazard(GameTestHelper helper) {
        for (var supplier : RocketLogoutExitWorld.STRUCTURAL) {
            Block block = supplier.get();
            for (var state : block.getStateDefinition().getPossibleStates()) {
                var shape = state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
                helper.assertTrue(Block.isShapeFullBlock(shape) && state.getFluidState().isEmpty()
                        && BayPassengerExitWorld.support(state, shape) == BayExitSafety.Support.FULL_STABLE
                        && RocketLogoutExitWorld.hazard(state) == BayExitSafety.Hazard.NONE
                        && BayPassengerExitWorld.hazard(state) == BayExitSafety.Hazard.UNKNOWN,
                        "Not a full-cube, non-hazard structural block for the logout only: " + state);
            }
        }
        helper.succeed();
    }
    // R375 item 3: other players are not collision surfaces; mobs still are.
    @GameTest(template = "rocket_test", batch = "rocket_logout_save", timeoutTicks = 160)
    public static void crewmateAtTheExitDoesNotBlockTheSave(GameTestHelper helper) { run(helper, Mode.CREWMATE); }
    @GameTest(template = "rocket_test", batch = "rocket_logout_save", timeoutTicks = 160)
    public static void mobAtTheExitKeepsTheExistingHighSeatSave(GameTestHelper helper) { run(helper, Mode.MOB); }
    // R375 item 4: the remaining exclusions.
    @GameTest(template = "rocket_test", batch = "rocket_logout_save", timeoutTicks = 160)
    public static void fireAtTheExitKeepsTheExistingHighSeatSave(GameTestHelper helper) { run(helper, Mode.HAZARD); }
    @GameTest(template = "rocket_test", batch = "rocket_logout_save", timeoutTicks = 160)
    public static void internalPassengerMoveOwnsTheLogoutSave(GameTestHelper helper) { run(helper, Mode.INTERNAL); }
    @GameTest(template = "rocket_test", batch = "rocket_logout_save", timeoutTicks = 160)
    public static void heldBayClaimOwnsTheLogoutSave(GameTestHelper helper) { run(helper, Mode.HELD); }
    @GameTest(template = "rocket_test", batch = "rocket_logout_fenced", timeoutTicks = 160)
    public static void releaseFenceOwnsTheLogoutSave(GameTestHelper helper) { run(helper, Mode.FENCED); }
    // R375 item 2: the server stop saves online players before any logout.
    @GameTest(template = "rocket_test", batch = "rocket_logout_stop", timeoutTicks = 160)
    public static void serverStopDetachesTheStationaryRiderBeforeItsSave(GameTestHelper helper) { run(helper, Mode.STOP); }
    @GameTest(template = "rocket_test", batch = "rocket_logout_stop", timeoutTicks = 160)
    public static void serverStopKeepsAnAscendingRiderSeated(GameTestHelper helper) { run(helper, Mode.STOP_FLIGHT); }
    // R375 item 5: an unexpected failure never blocks the save.
    @GameTest(template = "rocket_test", batch = "rocket_logout_failure", timeoutTicks = 160)
    public static void unexpectedFailureLogsOnceAndKeepsTheExistingSave(GameTestHelper helper) { run(helper, Mode.FAILURE); }

    private enum Mode {
        ASSEMBLED, FUELED, LANDED, REMOVED, KEPT, FLIGHT, TRANSFER, STALE, UNSUPPORTED, OBSTRUCTED, AUTOSAVE, CANCELLED, BUDGET, GUARDED,
        ASSEMBLER, WIDE, DROP3, DROP4, DROP_HAZARD, CREWMATE, MOB, HAZARD, INTERNAL, HELD, FENCED, STOP, STOP_FLIGHT, FAILURE;

        /** The logout leaves vanilla's seated save unchanged. */
        boolean excluded() {
            return switch (this) {
                case FLIGHT, TRANSFER, UNSUPPORTED, OBSTRUCTED, CANCELLED, BUDGET, GUARDED, DROP4, DROP_HAZARD, MOB, HAZARD, INTERNAL,
                        HELD, FENCED, STOP_FLIGHT, FAILURE -> true;
                default -> false;
            };
        }
        /** The rocket's W311 box covers the exit; a rider standing in it after LEAVE SEAT is harmless (R375). */
        boolean boxOverExit() { return this == ASSEMBLER || this == WIDE; }
    }
    private static void run(GameTestHelper helper, Mode mode) { new Scenario(helper, mode).start(); }

    private static final class Scenario {
        final GameTestHelper helper;
        final Mode mode;
        final GameTestTickPacer pacer;
        RocketLogoutSaveFixture f;
        int joinedAt;
        boolean finished, boarded;

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
                    // W312F: every column of the chamber and of the exit proof's window, not only the origin's, must be
                    // FULL, entity-loaded and entity-ticking first; the window can cross into the next column.
                    boolean ready = true;
                    for (int x = (at.getX() - 1) >> 4; x <= (at.getX() + 4) >> 4; x++) {
                        for (int z = (at.getZ() - 2) >> 4; z <= (at.getZ() + 2) >> 4; z++) {
                            ready &= level.getChunkSource().getChunkNow(x, z) != null && level.areEntitiesLoaded(ChunkPos.asLong(x, z))
                                    && level.isPositionEntityTicking(new BlockPos(x * 16, at.getY(), z * 16));
                        }
                    }
                    if (!ready) { pacer.pace(); return; }
                    var state = switch (mode) {
                        case FUELED -> RocketFlightState.FUELED;
                        case LANDED, KEPT -> RocketFlightState.LANDED;
                        case FLIGHT, STOP_FLIGHT -> RocketFlightState.ASCENT;
                        case TRANSFER -> RocketFlightState.TRANSIT;
                        default -> RocketFlightState.ASSEMBLED;
                    };
                    var shape = switch (mode) {
                        case ASSEMBLER -> RocketLogoutSaveFixture.Shape.ON_ASSEMBLER;
                        case WIDE -> RocketLogoutSaveFixture.Shape.WIDE;
                        default -> RocketLogoutSaveFixture.Shape.TALL;
                    };
                    f = new RocketLogoutSaveFixture(helper, state, shape);
                    boarded = shape != RocketLogoutSaveFixture.Shape.ON_ASSEMBLER;
                    if (!boarded) { pacer.pace(); return; }
                    exerciseAndJoin();
                } else if (!boarded) {
                    // The installed manager assembles on its own server tick; board as soon as the rocket exists.
                    if (!f.boardAssembled()) { pacer.pace(); return; }
                    boarded = true;
                    exerciseAndJoin();
                } else if (helper.getLevel().getServer().getTickCount() - joinedAt >= (mode == Mode.STALE ? 60 : 20)) {
                    helper.assertTrue(!f.player.hasDisconnected() && f.player.getHealth() == f.player.getMaxHealth(),
                            "Returning survival rider disconnected or lost health during native ticks");
                    if (mode == Mode.KEPT) {
                        helper.assertTrue(f.player.getVehicle() == f.rocket, "Reconnect lost its retained physical seat");
                    } else {
                        helper.assertTrue(!f.player.isPassenger() && f.player.position().distanceTo(f.exit) < 1e-7,
                                "Returning rider left the safe exit during native ticks");
                        helper.assertTrue(mode.boxOverExit() || !f.player.getBoundingBox().intersects(f.rocket.getBoundingBox()),
                                "Returning rider is inside the rocket");
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
        private void exerciseAndJoin() {
            exercise();
            if (f.player == null) { complete(); return; }
            joinedAt = helper.getLevel().getServer().getTickCount();
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
            arrange();
            var server = helper.getLevel().getServer();
            if (mode == Mode.BUDGET) {
                var budget = RocketRuntime.passengerExits().orElseThrow().budget;
                for (int i = 0; i < io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.exit.BayExitBudget.CANDIDATES_PER_TICK; i++) {
                    var probe = budget.openProbe().orElse(null); if (probe != null) { probe.close(); }
                }
            }
            if (mode == Mode.STOP || mode == Mode.STOP_FLIGHT) { stop(before); }
            else { logout(); }
            if (mode.excluded()) {
                var saved = f.disk();
                helper.assertTrue(f.mountedWhenSaved, "Excluded logout detached before saving: " + mode);
                helper.assertTrue(saved.get("Pos").equals(before.get("Pos")), "Excluded logout changed its saved position: " + mode);
                if (mode == Mode.FLIGHT || mode == Mode.TRANSFER || mode == Mode.STOP_FLIGHT) {
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

        /** W312F: the terrain, entities and players around the exit, each checked against both proofs before logout. */
        private void arrange() {
            var level = helper.getLevel();
            switch (mode) {
                case DROP3 -> { f.settle(3); f.assertProofs(BayExitSafety.Verdict.UNSAFE, BayExitSafety.Verdict.SAFE, 3); }
                case DROP4 -> { f.settle(4); f.assertProofs(BayExitSafety.Verdict.UNSAFE, BayExitSafety.Verdict.UNSAFE, -1); }
                case DROP_HAZARD -> {
                    f.settle(2); f.row(-3, Blocks.MAGMA_BLOCK.defaultBlockState());
                    f.assertProofs(BayExitSafety.Verdict.UNSAFE, BayExitSafety.Verdict.UNSAFE, -1);
                }
                case HAZARD -> {
                    f.block(BlockPos.containing(f.exit), Blocks.FIRE.defaultBlockState());
                    helper.assertTrue(level.getBlockState(BlockPos.containing(f.exit)).is(Blocks.FIRE), "Fixture fire did not stay");
                    f.assertProofs(BayExitSafety.Verdict.UNSAFE, BayExitSafety.Verdict.UNSAFE, -1);
                }
                case CREWMATE -> {
                    var crew = f.joinOther("W312FCrew");
                    crew.teleportTo(level, f.exit.x, f.exit.y, f.exit.z, 0, 0);
                    helper.assertTrue(crew.getBoundingBox().intersects(BayPassengerExitWorld.aabb(f.exitBox())), "Crewmate is not at the exit");
                    f.assertProofs(BayExitSafety.Verdict.UNSAFE, BayExitSafety.Verdict.SAFE, 0);
                }
                case MOB -> {
                    var stand = EntityType.ARMOR_STAND.create(level);
                    helper.assertTrue(stand != null, "Armor stand type unavailable");
                    stand.moveTo(f.exit.x, f.exit.y, f.exit.z, 0, 0);
                    helper.assertTrue(level.addFreshEntity(stand), "Fixture mob was not admitted");
                    f.extras.add(stand);
                    f.assertProofs(BayExitSafety.Verdict.UNSAFE, BayExitSafety.Verdict.UNSAFE, -1);
                }
                default -> { }
            }
        }

        private void logout() {
            var server = helper.getLevel().getServer();
            RocketRejoinSavedData previous = null, replaced = null;
            RocketReleaseAccess.Owner previousOwner = null;
            RocketTransferService transfers = null;
            RocketRuntime.PassengerMoveScope internal = null;
            LocationFailure failure = null;
            UUID logical = f.rocket.assemblyTransactionId().orElseThrow();
            var rider = f.player;
            var cancel = mode == Mode.CANCELLED ? new CancelLogoutDismount(f.player) : null;
            if (cancel != null) { MinecraftForge.EVENT_BUS.register(cancel); }
            try {
                if (mode == Mode.GUARDED) {
                    previous = RocketRejoinEvents.get(server);
                    replaced = RocketRejoinSavedData.load(RocketRejoinCodec.encode(RocketSeatRosterGameTests.rejoinRoot(f.playerId)));
                    server.overworld().getDataStorage().set(RocketRejoinSavedData.TYPE.dataName(), replaced);
                }
                if (mode == Mode.FENCED) {
                    // A pending release of another rider fences this rocket; the logging-out rider is not guarded.
                    transfers = RocketRuntime.installedTransfers();
                    previousOwner = transfers.swapReleaseOwnerForGameTest(server, null);
                    previous = RocketRejoinEvents.get(server);
                    replaced = RocketRejoinSavedData.load(RocketRejoinCodec.encode(fenceRoot(logical)));
                    server.overworld().getDataStorage().set(RocketRejoinSavedData.TYPE.dataName(), replaced);
                    helper.assertTrue(RocketRuntime.releaseFenced(f.rocket) && !BayPassengerExitPermit.guarded(f.player),
                            "Fixture release fence does not hold this rocket alone");
                }
                if (mode == Mode.HELD) {
                    // The same RAM claim the real session consults, without advancing its deployment queue (as W300F).
                    var claims = (BayClaimBook) field(BayDeploymentGameTestSupport.bays(helper), "claims");
                    helper.assertTrue(claims.begin(BayClaim.checkpoint(logical, f.record.transferId(), f.rocket.getUUID(),
                            List.of(BayMember.candidate(new RocketPosition(0, 0, 0), UUID.randomUUID())))), "Could not hold the bay claim");
                    helper.assertTrue(BayReceipts.holdsRocket(server, logical), "The claim does not hold the rocket");
                }
                if (mode == Mode.INTERNAL) { internal = RocketRuntime.internalPassengerMove(f.player); }
                if (mode == Mode.FAILURE) { failure = new LocationFailure(f.playerId); failure.install(); }
                // A held rider's vanilla detach after the save reaches the held bay exit service, as before W312.
                f.logout(mode == Mode.CANCELLED, mode == Mode.HELD);
                if (failure != null) { helper.assertTrue(failure.thrown == 1, "The injected failure did not reach the logout hook once"); }
            } finally {
                if (cancel != null) { MinecraftForge.EVENT_BUS.unregister(cancel); }
                if (internal != null) { internal.close(); }
                if (failure != null) { failure.remove(); }
                if (mode == Mode.HELD) {
                    RocketRuntime.passengerExits().ifPresent(exits -> exits.revoke(rider));
                    var bays = BayDeploymentGameTestSupport.bays(helper);
                    bays.unqueueForTests(logical); bays.forgetForTests(logical);
                }
                if (transfers != null) {
                    var created = transfers.swapReleaseOwnerForGameTest(server, previousOwner);
                    if (created != null) { created.close(); }
                }
                if (replaced != null) {
                    replaced.close(); server.overworld().getDataStorage().set(RocketRejoinSavedData.TYPE.dataName(), previous);
                }
            }
            if (mode == Mode.HELD) { helper.assertTrue(!BayReceipts.holdsRocket(server, logical), "The fixture claim was not forgotten"); }
        }

        /** R375 item 2: the real stopping handler, then stopServer's own saveAll; the seat stays assigned. */
        private void stop(net.minecraft.nbt.CompoundTag before) {
            var server = helper.getLevel().getServer();
            var manifest = f.rocket.flightData().orElseThrow().passengers();
            RocketPassengerLogout.stopping(new ServerStoppingEvent(server));
            helper.assertTrue(!f.player.hasDisconnected() && f.rocket.flightData().orElseThrow().passengers().equals(manifest)
                    && manifest.assignment(f.playerId).isPresent(), "The stop pass disconnected the rider or changed the seat");
            if (mode == Mode.STOP_FLIGHT) {
                helper.assertTrue(f.player.getVehicle() == f.rocket, "The stop pass detached an ascending rider");
                server.getPlayerList().saveAll();
                f.mountedWhenSaved = true;
                helper.assertTrue(f.disk().get("Pos").equals(before.get("Pos")) && !f.disk().contains("RootVehicle"),
                        "The stop save moved an ascending rider");
                f.logout();
                return;
            }
            helper.assertTrue(f.player.getVehicle() == null && f.player.position().distanceTo(f.exit) < 1e-7,
                    "The stop pass did not detach the stationary rider at its exit: " + f.player.position());
            server.getPlayerList().saveAll();
            f.mountedWhenSaved = false;
            helper.assertTrue(!f.disk().contains("RootVehicle"), "The stop save embedded the rocket");
            // The stop save is the one that counts; the fixture's own disconnect afterwards writes the same position.
            helper.assertTrue(RocketLogoutSaveFixture.position(f.disk()).distanceTo(f.exit) < 1e-7,
                    "The stop save kept the seat: " + RocketLogoutSaveFixture.position(f.disk()));
            f.player.connection.onDisconnect(Component.literal("W312F fixture stop"));
            f.recovery.onPlayerLoggedOut(f.playerId);
            f.player = null;
        }

        private void complete() { finished = true; f.close(); helper.succeed(); }
    }

    /** A pending release batch for another rider on this logical rocket, so the rocket is fenced. */
    static RocketRejoinRoot fenceRoot(UUID logical) {
        var release = UUID.randomUUID(); var other = UUID.randomUUID();
        var version = new RocketRejoinRoot.Version("a".repeat(64), "PREPARED", null);
        var batch = new RocketRejoinRoot.Batch(release, 0, UUID.randomUUID(), logical, UUID.randomUUID(),
                RocketRejoinRoot.Hold.H1, "minecraft:overworld", new RocketRejoinRoot.Position(0, 64, 0),
                new RocketRejoinRoot.Position(-1, 64, -1), new RocketRejoinRoot.Position(1, 66, 1),
                new RocketRejoinRoot.Resources("b".repeat(64), "c".repeat(64)), version, version, List.of(other),
                List.of(new RocketRejoinRoot.Slot("minecraft:overworld", 0, 0)));
        return RocketRejoinRoot.empty().admit(batch, List.of(new RocketRejoinRoot.Entry(release, other, 0,
                RocketRejoinRoot.Placement.PENDING, RocketRejoinRoot.Seat.PENDING, null)));
    }

    private static Object field(Object target, String name) {
        try { var field = target.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(target); }
        catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
    }

    /** R375 item 5: the location hook throws once, for this disconnecting rider, through the gated GameTest observer. */
    private static final class LocationFailure {
        final UUID rider;
        int thrown;
        LocationFailure(UUID rider) { this.rider = rider; }
        void install() {
            RocketRuntime.passengerExits().orElseThrow().probeLocationForTests((player, rocket) -> {
                if (thrown == 0 && player.getUUID().equals(rider) && player.hasDisconnected()) {
                    thrown++;
                    throw new IllegalStateException("W312F injected logout exit failure");
                }
            });
        }
        void remove() { RocketRuntime.passengerExits().ifPresent(exits -> exits.probeLocationForTests(null)); }
    }

    public static final class CancelLogoutDismount {
        private final ServerPlayer player;
        CancelLogoutDismount(ServerPlayer player) { this.player = player; }
        @SubscribeEvent public void mount(EntityMountEvent event) {
            if (event.isDismounting() && event.getEntityMounting() == player && player.hasDisconnected()) { event.setCanceled(true); }
        }
    }
}
