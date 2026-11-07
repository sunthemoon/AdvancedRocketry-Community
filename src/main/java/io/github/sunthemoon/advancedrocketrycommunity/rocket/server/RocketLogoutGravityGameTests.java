package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import com.mojang.authlib.GameProfile;
import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalogManager;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialEnvironmentService;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialGravityController;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.gravity.GravityFieldBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.gametest.GameTestTickPacer;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.exit.BayExitSafety.Verdict;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.exit.LogoutExitSafety;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightState;
import java.util.List;
import java.util.OptionalDouble;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * W312F2 (R378, review W312FR F1): the logout save's fall allowance under the mod's gravity scaling. Each rocket case uses
 * a real gravity field controller, a clear column below the exit, native logout, an offline seat release, native login and
 * a wait past spawn immunity, then lands through the server's own fall routine with the registered gravity and fall
 * listeners, as W312FR's probe did.
 */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RocketLogoutGravityGameTests {
    static final String BATCH = "rocket_logout_gravity";
    /** ServerPlayer's spawn invulnerability lasts 60 ticks; the landing waits past it, so that it cannot hide damage. */
    private static final int AFTER_SPAWN_IMMUNITY = 70;

    private RocketLogoutGravityGameTests() { }

    @AfterBatch(batch = BATCH)
    public static void afterGravity(ServerLevel level) { RocketLogoutSaveFixture.closeAbandoned(); }

    /** The probe's case: 2 g over three blocks. The save is the seat or a place whose landing loses no health. */
    @GameTest(template = "rocket_test", batch = BATCH, timeoutTicks = 240)
    public static void twoGravityFieldSavesNoHarmfulFall(GameTestHelper helper) {
        new Scenario(helper, 200, 3, Expect.UNHURT).start();
    }

    @GameTest(template = "rocket_test", batch = BATCH, timeoutTicks = 240)
    public static void oneGravityFieldStillSavesTheExitAboveAThreeBlockFall(GameTestHelper helper) {
        new Scenario(helper, 100, 3, Expect.EXIT).start();
    }

    /** At 0.5 g a four-block landing would be harmless, but the geometric three-block ceiling still refuses it. */
    @GameTest(template = "rocket_test", batch = BATCH, timeoutTicks = 240)
    public static void lowGravityFieldKeepsTheThreeBlockCeiling(GameTestHelper helper) {
        new Scenario(helper, 50, 4, Expect.SEAT).start();
    }

    /** The controller's query for another block follows its tick's layers: area field, station override, Level. */
    @GameTest(template = "empty", batch = BATCH)
    public static void controllerAnswersThroughTheTickLayersAtAnyBlock(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos at = helper.absolutePos(new BlockPos(1, 2, 1));
        FakePlayer player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "W312F2Gravity"));
        player.setPos(at.getX() + 0.5D, at.getY(), at.getZ() + 0.5D);
        // No catalog, so the Level layer is 1 g; the station layer answers one block down and the field two blocks down.
        var environments = new CelestialEnvironmentService(new CelestialCatalogManager());
        CelestialGravityController.PositionGravity station = (world, pos) ->
                pos.getY() == at.getY() - 1 ? OptionalDouble.of(0.35D) : OptionalDouble.empty();
        CelestialGravityController.PlayerGravityAt fields = (who, pos) ->
                who == player && pos.getY() <= at.getY() - 2 ? OptionalDouble.of(2.0D) : OptionalDouble.empty();
        var controller = new CelestialGravityController(environments, station, fields, () -> true);
        try {
            expect(helper, controller.playerMultiplier(player, at), 1.0D, "Level layer");
            expect(helper, controller.playerMultiplier(player, at.below()), 0.35D, "Station layer");
            expect(helper, controller.playerMultiplier(player, at.below(2)), 2.0D, "Field layer");
            for (BlockPos pos : List.of(at, at.below(), at.below(2))) {
                player.setPos(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
                controller.onLivingTick(new LivingEvent.LivingTickEvent(player));
                expect(helper, controller.playerMultiplier(player, pos), CelestialGravityController.appliedMultiplier(player),
                        "The tick applied another value at " + pos);
            }
            player.setPos(at.getX() + 0.5D, at.getY(), at.getZ() + 0.5D);
            CelestialGravityController.PlayerGravity ownBlockOnly = who -> OptionalDouble.of(2.0D);
            var own = new CelestialGravityController(environments, station, ownBlockOnly, () -> true);
            expect(helper, own.playerMultiplier(player, at), 2.0D, "Own-block field layer");
            helper.assertTrue(own.playerMultiplier(player, at.below()).isEmpty(),
                    "A field layer that answers only at the player's block was taken for another block");
            var outside = new CelestialGravityController(environments, (world, pos) -> OptionalDouble.of(Double.NaN),
                    (CelestialGravityController.PlayerGravityAt) (who, pos) -> OptionalDouble.empty(), () -> true);
            helper.assertTrue(outside.playerMultiplier(player, at).isEmpty(), "A value outside the model bounds was answered");
        } finally {
            player.discard();
        }
        helper.succeed();
    }

    private static void expect(GameTestHelper helper, OptionalDouble actual, double expected, String message) {
        helper.assertTrue(actual.isPresent() && Math.abs(actual.getAsDouble() - expected) < 1.0E-9D,
                message + ": " + actual + " != " + expected);
    }

    /** EXIT and SEAT name the save the fix must make; UNHURT accepts either, but the landing must lose no health. */
    private enum Expect { EXIT, SEAT, UNHURT }

    private static final class Scenario {
        final GameTestHelper helper;
        final int field;
        final int depth;
        final Expect expect;
        final GameTestTickPacer pacer;
        RocketLogoutSaveFixture f;
        GravityFieldBlockEntity device;
        BlockPos devicePos;
        LogoutExitSafety.Proof proof;
        Vec3 saved;
        boolean seatSaved, finished;
        int joinedAt;

        /** {@code field} is the controller's multiplier in hundredths; {@code depth} the rows of air below the exit. */
        Scenario(GameTestHelper helper, int field, int depth, Expect expect) {
            this.helper = helper; this.field = field; this.depth = depth; this.expect = expect;
            pacer = new GameTestTickPacer(helper.getLevel().getServer());
        }

        void start() { helper.onEachTick(this::tick); }

        private void tick() {
            if (finished) { return; }
            try {
                if (f == null) {
                    if (!ready()) { pacer.pace(); return; }
                    logoutReleaseAndLogin();
                    return;
                }
                if (helper.getLevel().getServer().getTickCount() - joinedAt < AFTER_SPAWN_IMMUNITY) { pacer.pace(); return; }
                land();
                finished = true;
                f.close();
                helper.succeed();
            } catch (Throwable failure) {
                finished = true;
                if (f != null) { try { f.close(); } catch (Throwable cleanup) { failure.addSuppressed(cleanup); } }
                if (failure instanceof RuntimeException runtime) { throw runtime; }
                if (failure instanceof Error error) { throw error; }
                throw new RuntimeException(failure);
            }
        }

        /** Every column of the chamber and of the exit proof's window is loaded and entity-ticking (as W312F's scenario). */
        private boolean ready() {
            var level = helper.getLevel();
            var at = helper.absolutePos(RocketLogoutSaveFixture.ORIGIN);
            for (int x = (at.getX() - 1) >> 4; x <= (at.getX() + 4) >> 4; x++) {
                for (int z = (at.getZ() - 2) >> 4; z <= (at.getZ() + 2) >> 4; z++) {
                    if (level.getChunkSource().getChunkNow(x, z) == null || !level.areEntitiesLoaded(ChunkPos.asLong(x, z))
                            || !level.isPositionEntityTicking(new BlockPos(x * 16, at.getY(), z * 16))) { return false; }
                }
            }
            return true;
        }

        private void logoutReleaseAndLogin() throws java.io.IOException {
            var level = helper.getLevel();
            var server = level.getServer();
            f = new RocketLogoutSaveFixture(helper, RocketFlightState.ASSEMBLED);
            f.settle(depth);
            // A real controller owned by the rider; its radius-8 box holds the seat, the exit and the whole fall column.
            devicePos = helper.absolutePos(RocketLogoutSaveFixture.ORIGIN).west(3).above(2);
            f.block(devicePos, ModBlocks.GRAVITY_FIELD_CONTROLLER.get().defaultBlockState());
            device = level.getBlockEntity(devicePos) instanceof GravityFieldBlockEntity placed ? placed : null;
            helper.assertTrue(device != null && device.assignOwner(f.playerId), "The gravity field controller was not placed and owned");
            device.energy().set(GravityFieldBlockEntity.ENERGY_CAPACITY);
            CompoundTag root = device.saveWithoutMetadata();
            root.getCompound("endgame").putInt("multiplier", field);
            root.getCompound("endgame").putBoolean("running", true);
            device.load(root);
            GravityFieldBlockEntity.serverTick(level, devicePos, device.getBlockState(), device);
            helper.assertTrue(device.active() && device.multiplier() == field, "The gravity field is not active: " + device.describe());
            registeredTick(f.player);
            helper.assertTrue(Math.abs(CelestialGravityController.appliedMultiplier(f.player) - field / 100.0D) < 1.0E-9D,
                    "The field does not apply to the seated rider: " + CelestialGravityController.appliedMultiplier(f.player));

            proof = f.logoutProof();
            // UNHURT asserts nothing here, so that a proof that accepts the fall fails at the landing below.
            if (expect == Expect.EXIT) {
                helper.assertTrue(proof.equals(new LogoutExitSafety.Proof(Verdict.SAFE, depth)), "Logout proof at 1 g: " + proof);
            } else if (expect == Expect.SEAT) {
                helper.assertTrue(proof.verdict() == Verdict.UNSAFE, "Logout proof beyond the ceiling: " + proof);
            }
            var before = f.player.saveWithoutId(new CompoundTag());
            f.logout();
            var disk = f.disk();
            saved = RocketLogoutSaveFixture.position(disk);
            seatSaved = f.mountedWhenSaved;
            if (seatSaved) {
                helper.assertTrue(expect != Expect.EXIT, "The 1 g control kept the seat: " + proof);
                helper.assertTrue(proof.verdict() == Verdict.UNSAFE && disk.get("Pos").equals(before.get("Pos")),
                        "The seat save was not the existing one kept by a refused fall: " + proof + " saved=" + saved);
            } else {
                helper.assertTrue(expect != Expect.SEAT, "Logout saved the exit above a fall beyond three blocks: " + proof);
                helper.assertTrue(saved.distanceTo(f.exit) < 1e-7, "Logout saved neither the seat nor the exit: " + saved);
            }
            int released = server.getCommands().performPrefixedCommand(
                    server.createCommandSourceStack().withLevel(level).withPermission(2),
                    "arce rocket release-seat " + f.rocket.getStringUUID() + " " + f.playerId);
            helper.assertTrue(released == 1 && f.rocket.flightData().orElseThrow().passengers().assignment(f.playerId).isEmpty(),
                    "Operator did not release the offline seat");
            f.player = f.join();
            helper.assertTrue(f.player.position().distanceTo(saved) < 1e-7 && !f.player.isPassenger(),
                    "Native login did not restore the save: " + f.player.position());
            joinedAt = server.getTickCount();
        }

        private void land() {
            var level = helper.getLevel();
            var player = f.player;
            helper.assertTrue(!player.isPassenger() && !player.hasDisconnected() && player.position().distanceTo(saved) < 1e-7,
                    "The returning rider moved, remounted or disconnected during native ticks: " + player.position());
            helper.assertTrue(player.getHealth() == player.getMaxHealth(), "The returning rider lost health at login or in native ticks");
            GravityFieldBlockEntity.serverTick(level, devicePos, device.getBlockState(), device);
            helper.assertTrue(device.active(), "The gravity field lapsed: " + device.describe());
            float lost = fallFromTheExit(player);
            AdvancedRocketryCommunity.LOGGER.info("W312F2_LOGOUT_GRAVITY field={} depth={} proof={} seatSaved={} landingLost={}",
                    field / 100.0D, depth, proof, seatSaved, lost);
            if (!seatSaved) {
                helper.assertTrue(lost == 0.0F, "The returning rider lost " + lost + " health landing " + depth
                        + " blocks below the saved exit in a " + field / 100.0D + " g field (logout proof " + proof + ")");
            } else if (expect == Expect.UNHURT) {
                // The refused place: the same landing does hurt in this field, so the kept seat is what spared the rider.
                helper.assertTrue(lost > 0.0F, "The control landing from the refused exit did not hurt; the field did not scale it");
            } else {
                // The ceiling is geometric: this landing would be harmless at low gravity, and it is still refused.
                helper.assertTrue(lost == 0.0F, "The low-gravity control landing hurt (" + lost + "); the field did not apply");
            }
        }

        /**
         * The server's own fall routine, as handleMovePlayer calls it, from the exit onto the first support {@code depth}
         * blocks down: one-block airborne moves, each after the registered tick listeners, then the landing. Returns the
         * health lost.
         */
        private float fallFromTheExit(ServerPlayer player) {
            float before = player.getHealth();
            player.invulnerableTime = 0;
            player.setDeltaMovement(Vec3.ZERO);
            player.setPos(f.exit);
            registeredTick(player);
            player.resetFallDistance();
            for (int step = 1; step <= depth; step++) {
                player.setPos(f.exit.x, f.exit.y - step, f.exit.z);
                registeredTick(player);
                player.doCheckFallDamage(0.0D, -1.0D, 0.0D, false);
            }
            player.doCheckFallDamage(0.0D, 0.0D, 0.0D, true);
            return before - player.getHealth();
        }
    }

    /** The registered tick listeners, as the rider's own ticks would post them: they apply the production gravity. */
    private static void registeredTick(ServerPlayer player) {
        MinecraftForge.EVENT_BUS.post(new LivingEvent.LivingTickEvent(player));
    }
}
