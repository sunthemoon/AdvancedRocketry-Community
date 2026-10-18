package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.gametest.GameTestTickPacer;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlock;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlockState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketStats;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * W90a, Root decision R22 amendment 5: loaded AND entity-ticking is the conservative readiness check, and neither
 * predicate alone suffices. Real vanilla states on far columns of the shared overworld; they share the fence batch,
 * whose fixtures use isolated journals, and read no other fixture's columns.
 */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RocketMarginReadinessGameTests {
    private static final TicketType<UUID> FIXTURE = TicketType.create("arce_w90a_readiness_fixture", UUID::compareTo);
    private RocketMarginReadinessGameTests() { }

    @GameTest(template = "empty", batch = "escrow_transfer_fence", timeoutTicks = 1_200)
    public static void aDemotedColumnWhoseEntitiesAreStillLoadedIsNotReady(GameTestHelper helper) {
        new Demotion(helper).start();
    }

    @GameTest(template = "empty", batch = "escrow_transfer_fence", timeoutTicks = 1_200)
    public static void aPendingEntityLoadWithTickingVisibilityIsNotReady(GameTestHelper helper) {
        new Pending(helper).start();
    }

    /** A two-block rocket end whose margin stays inside the origin's column. */
    static RocketStructureSnapshot contained(ServerLevel level, BlockPos origin) {
        var iron = new RocketBlockState(ResourceLocation.tryParse("minecraft:iron_block"), Map.of());
        return RocketStructureSnapshot.create(UUID.randomUUID(), level.dimension().location(),
                new RocketPosition(origin.getX(), origin.getY(), origin.getZ()),
                List.of(new RocketBlock(new RocketPosition(0, 0, 0), iron), new RocketBlock(new RocketPosition(0, 1, 0), iron)),
                List.of(), new RocketStats(2, 100, 1_000, 2_000, 1, 0, 1, 0), level.getGameTime());
    }

    private abstract static class Column {
        final GameTestHelper helper;
        final ServerLevel level;
        final GameTestTickPacer pacer;
        final BlockPos origin;
        final ChunkPos chunk;
        final UUID key = UUID.randomUUID();
        final RocketStructureSnapshot snapshot;
        boolean done;

        Column(GameTestHelper helper, BlockPos origin) {
            this.helper = helper; level = helper.getLevel(); pacer = new GameTestTickPacer(level.getServer());
            this.origin = origin; chunk = new ChunkPos(origin); snapshot = contained(level, origin);
            helper.assertTrue(!RocketMarginGameTestSupport.endReady(level.getServer(), snapshot)
                    && !level.areEntitiesLoaded(chunk.toLong()), "The readiness fixture's column was already loaded");
        }
        void start() { ticket(true); helper.onEachTick(this::tick); }
        void ticket(boolean add) {
            if (add) { level.getChunkSource().addRegionTicket(FIXTURE, chunk, 2, key); }
            else { level.getChunkSource().removeRegionTicket(FIXTURE, chunk, 2, key); }
        }
        boolean loaded() { return level.areEntitiesLoaded(chunk.toLong()); }
        boolean ticking() { return level.isPositionEntityTicking(origin); }
        boolean ready() { return RocketMarginGameTestSupport.endReady(level.getServer(), snapshot); }
        /** Applies pending ticket changes now; demotion is synchronous in the update, as in a chunk getter. */
        void update() { var map = level.getChunkSource().chunkMap; map.getDistanceManager().runAllUpdates(map); }
        private void tick() {
            if (done) { return; }
            pacer.pace();
            helper.assertTrue(loaded() && ticking() || !ready(), "Readiness held for a column that was not loaded and entity-ticking");
            step();
        }
        abstract void step();
        void finish() { ticket(false); done = true; helper.succeed(); }
    }

    private static final class Demotion extends Column {
        Demotion(GameTestHelper helper) { super(helper, new BlockPos(650_008, 100, 650_008)); }
        @Override void step() {
            if (!loaded() || !ticking()) { return; }
            helper.assertTrue(ready(), "A loaded, entity-ticking contained margin was not ready");
            ticket(false); update();
            // The entity sections stay LOADED until the next entity-manager tick unloads them.
            helper.assertTrue(loaded() && !ticking(), "The fixture did not reach the demoted, still-loaded state");
            helper.assertTrue(!ready(), "A demoted column whose entities are still loaded was ready");
            done = true; helper.succeed();
        }
    }

    private static final class Pending extends Column {
        private int phase, attempts;
        Pending(GameTestHelper helper) { super(helper, new BlockPos(652_008, 100, 652_008)); }
        @Override void step() {
            switch (phase) {
                case 0 -> {
                    if (!loaded() || !ticking()) { return; }
                    ticket(false); update(); phase = 1;
                }
                case 1 -> { if (!loaded()) { phase = 2; } }
                case 2 -> {
                    helper.assertTrue(++attempts <= 5, "The pending entity load was never observed with ticking visibility");
                    ticket(true);
                    // Promote inside this callback: no entity-manager tick can complete the queued load before the check.
                    if (pump()) { observe(); } else { phase = 3; }
                }
                case 3 -> {
                    if (!ticking()) { return; }
                    if (!loaded()) { observe(); return; }
                    // The load completed between ticks; demote again and retry.
                    ticket(false); update(); phase = 1;
                }
                case 4 -> {
                    if (!loaded() || !ticking()) { return; }
                    helper.assertTrue(ready(), "The completed load did not make the column ready");
                    finish();
                }
                default -> { }
            }
        }
        private boolean pump() {
            var source = level.getChunkSource();
            long deadline = System.nanoTime() + 2_000_000_000L;
            while (!ticking() && System.nanoTime() < deadline) {
                update();
                if (!source.pollTask()) { java.util.concurrent.locks.LockSupport.parkNanos(200_000L); }
            }
            return ticking();
        }
        private void observe() {
            helper.assertTrue(ticking() && !loaded(), "The fixture did not reach the pending, ticking state");
            helper.assertTrue(!ready(), "A pending entity load with ticking visibility was ready");
            phase = 4;
        }
    }
}
