package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.gametest.GameTestTickPacer;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketCargoRole;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketFluidCargoBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketCargoRecoveryGameTestSupport;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** W330 C-E-A12/A13/A14: recovery and cancellation consume a real retired cargo transaction. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CargoRecoveryAcceptanceGameTests {
    private static Scenario running;
    private CargoRecoveryAcceptanceGameTests() { }
    @GameTest(template = "empty", batch = "w330_cargo_stale", timeoutTicks = 1_600)
    public static void stalePreparedWithBothEntitiesKeepsProtectedCargoAndRequiresSnapshotIdentity(GameTestHelper helper) { start(helper, 0); }
    @AfterBatch(batch = "w330_cargo_stale")
    public static void cleanupStale(ServerLevel level) { cleanup(); }
    @GameTest(template = "empty", batch = "w330_cargo_reconnect", timeoutTicks = 1_600)
    public static void disembarkedPassengerReconnectCannotReplayPreCargoAuthority(GameTestHelper helper) { start(helper, 1); }
    @AfterBatch(batch = "w330_cargo_reconnect")
    public static void cleanupReconnect(ServerLevel level) { cleanup(); }
    @GameTest(template = "empty", batch = "w330_cargo_cancel", timeoutTicks = 2_000)
    public static void exactSourceCancellationPreservesTheLatestCargo(GameTestHelper helper) { start(helper, 2); }
    @AfterBatch(batch = "w330_cargo_cancel")
    public static void cleanupCancel(ServerLevel level) { cleanup(); }
    private static void start(GameTestHelper helper, int mode) { running = new Scenario(helper, mode); helper.onEachTick(running::tick); }
    private static void cleanup() { if (running != null) { running.close(); running = null; } }

    static final class Scenario implements AutoCloseable {
        final GameTestHelper helper;
        final int mode;
        final BlockPos origin;
        final EscrowCargoGameTestWorld world;
        final GameTestTickPacer pacer;
        RocketEntity rocket;
        RocketFluidCargoBlockEntity device;
        RocketStructureSnapshot before;
        EscrowOperation admitted;
        EscrowCargoLaunch launch;
        int step;
        boolean done;
        Scenario(GameTestHelper helper, int mode) {
            this.helper = helper; this.mode = mode; origin = new BlockPos(670_008 + mode * 512, 100, 670_008);
            world = new EscrowCargoGameTestWorld(helper, List.of(origin, origin.offset(32, 0, 0)));
            pacer = new GameTestTickPacer(world.server);
        }
        void tick() {
            if (done) { return; }
            pacer.pace();
            if (step == 0) {
                if (!world.ready()) { return; }
                world.isolate();
                rocket = world.rocket(origin, mode == 2 ? FluidStack.EMPTY : new FluidStack(Fluids.WATER, 5_000));
                before = rocket.snapshot().orElseThrow();
                device = world.device(origin.offset(2, 0, 0), rocket, mode == 2 ? RocketCargoRole.LOAD : RocketCargoRole.UNLOAD, mode == 2 ? 5_000 : 0);
                world.enable(device, true); step = 1; return;
            }
            if (step == 1) {
                if (admitted == null) {
                    admitted = world.member(device); if (admitted == null) { return; }
                    world.enable(device, false);
                    helper.assertTrue(admitted.nativeBinding().complete() && admitted.phase() == EscrowOperation.Phase.PREPARED,
                            "Recovery test did not admit real cargo");
                }
                var data = world.data();
                if (!data.ledger().records().isEmpty() || !rocket.escrowSection().markers().isEmpty()
                        || !EscrowCargoGameTestWorld.section(device).markers().isEmpty() || !data.bind(world.server).writerIdle()) { return; }
                helper.assertTrue(EscrowCargoGameTestWorld.cargo(rocket) == (mode == 2 ? 1_000 : 4_000)
                        && EscrowCargoGameTestWorld.tank(device) == (mode == 2 ? 4_000 : 1_000) && rocket.escrowSection().epoch() == 1,
                        "Recovery fixture lost its real completed cargo");
                if (mode != 2) { RocketCargoRecoveryGameTestSupport.verify(helper, rocket, before, mode == 1); finish(); return; }
                launch = new EscrowCargoLaunch(helper, world, rocket, origin.offset(32, 0, 0), true); step = 2;
            }
            if (step == 2 && launch.tick()) {
                helper.assertTrue(launch.survivor() == rocket && rocket.isAlive() && EscrowCargoGameTestWorld.cargo(rocket) == 1_000
                        && EscrowCargoGameTestWorld.tank(device) == 4_000, "Exact-source cancellation changed cargo");
                finish();
            }
        }
        private void finish() { close(); done = true; helper.succeed(); }
        @Override public void close() { if (launch != null) { launch.close(); } world.close(); }
    }
}
