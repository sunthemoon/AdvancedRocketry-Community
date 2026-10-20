package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowReconciliationGameTests;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Unit;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * W111: every remote fixture area of the {@value #BATCH} batch is generated, saved and flushed before the batch's first
 * test tick, and stays loaded until the batch ends.
 * <p>Each of these tests holds a START ticket of radius {@value #RADIUS} on a fixture chunk far from the others. On hosted
 * runners, generating that 5x5 FULL area and the generation pyramid around it took up to 165 paced ticks, inside the
 * tests' unchanged timeouts. Vanilla's eager save of the newly FULL chunks, and the unload save of a whole area when a
 * finished test removed its ticket, queued hundreds of chunk writes ahead of the other tests' own stores and forces on
 * the Level's single region IO worker. The batch hooks do that work while no test runs: a GameTest timeout counts the
 * ticks after its test starts, and the hooks run before the batch's first test tick and after its last one. Their own
 * ticket type keeps every area loaded when a test removes its START ticket.
 */
@GameTestHolder(AdvancedRocketryCommunity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EscrowCoreFixtureAreas {
    public static final String BATCH = "escrow_core";
    /** The radius of the tests' START tickets: the fixture chunk is entity-ticking and its 5x5 area FULL. */
    static final int RADIUS = 2;
    // Wall-clock bounds of the hooks; the GameTest timeouts count ticks and do not run while a hook blocks. They only
    // stop a hang: hosted runners generate these areas in about 15 seconds, a deliberately starved local JVM in four
    // minutes (W111). A prepare that runs out fails each of the batch's remote-fixture tests at its start.
    private static final long PREPARE_NANOS = TimeUnit.SECONDS.toNanos(600);
    private static final long RELEASE_NANOS = TimeUnit.SECONDS.toNanos(60);
    private record Batch(ServerLevel level, List<ChunkPos> centres, boolean ready) { }
    // Batch hooks and test functions run serially on the server thread.
    private static Batch active;

    private EscrowCoreFixtureAreas() { }

    /** Every fixture chunk of the batch whose test holds a START ticket of {@link #RADIUS}. */
    static List<ChunkPos> centres() {
        var centres = new ArrayList<ChunkPos>();
        centres.add(new ChunkPos(EscrowCoreGameTests.JOINT_FIXTURE));
        centres.add(new ChunkPos(EscrowCoreGameTests.OWN_FIXTURE));
        centres.add(new ChunkPos(EscrowProofRetryGameTests.fixture(false)));
        centres.add(new ChunkPos(EscrowProofRetryGameTests.fixture(true)));
        for (int missing = 0; missing < EscrowReconciliationGameTests.FIXTURES; missing++) {
            centres.add(new ChunkPos(EscrowReconciliationGameTests.fixture(missing)));
        }
        return List.copyOf(centres);
    }

    @BeforeBatch(batch = BATCH)
    public static void prepareBatch(ServerLevel level) {
        if (active != null) { throw new IllegalStateException("The previous escrow_core batch did not release its fixture areas"); }
        active = prepare(level, centres(), true);
    }

    @AfterBatch(batch = BATCH)
    public static void releaseBatch(ServerLevel level) {
        Batch batch = active; active = null;
        if (batch == null || batch.level() != level) { throw new IllegalStateException("escrow_core fixture areas have a mismatched batch lifecycle"); }
        long started = System.nanoTime();
        var chunks = level.getChunkSource();
        for (ChunkPos centre : batch.centres()) {
            chunks.removeRegionTicket(Native.HOLD, centre, RADIUS, centre);
            chunks.removeRegionTicket(TicketType.START, centre, RADIUS, Unit.INSTANCE); // A failed test keeps its own.
        }
        // Unload now, while no test runs, rather than in the next batch's first ticks: vanilla saves every chunk of
        // each area's generation pyramid when it unloads it.
        boolean unloaded = await(level, () -> drainUnloads(level)
                && batch.centres().stream().allMatch(centre -> VanillaChunkSave.holder(level, centre) == null), started + RELEASE_NANOS);
        var flush = chunks.chunkMap.worker.synchronize(true);
        boolean flushed = await(level, flush::isDone, System.nanoTime() + RELEASE_NANOS) && !flush.isCompletedExceptionally();
        var loaded = batch.centres().stream().filter(centre -> VanillaChunkSave.holder(level, centre) != null).toList();
        AdvancedRocketryCommunity.LOGGER.info("ARCE_GAMETEST_FIXTURE_AREAS batch={} stage=release areas={} unloaded={} flushed={} ms={} still_loaded={}",
                BATCH, batch.centres().size(), unloaded, flushed, (System.nanoTime() - started) / 1_000_000L,
                loaded.stream().map(centre -> centre + " level " + VanillaChunkSave.holder(level, centre).getTicketLevel()
                        + " " + chunks.getChunkDebugData(centre)).toList());
    }

    /**
     * Whether this fixture's area was generated, saved and flushed before the batch's first tick and is held for the
     * whole batch. A single {@code /test run} has no batch hook; it prepares the area now, in the test's own setup.
     */
    public static boolean prepared(ServerLevel level, ChunkPos centre) {
        Batch batch = active;
        if (batch == null) { return prepare(level, List.of(centre), false).ready(); }
        return batch.ready() && batch.level() == level && batch.centres().contains(centre) && generated(level, centre);
    }

    private static Batch prepare(ServerLevel level, List<ChunkPos> centres, boolean hold) {
        long started = System.nanoTime(), deadline = started + PREPARE_NANOS;
        var chunks = level.getChunkSource();
        if (hold) { centres.forEach(centre -> chunks.addRegionTicket(Native.HOLD, centre, RADIUS, centre)); }
        centres.forEach(centre -> level.getChunk(centre.x, centre.z)); // FULL now, as each test's own setup asks.
        boolean generated = await(level, () -> centres.stream().allMatch(centre -> generated(level, centre)), deadline);
        int saved = 0;
        if (generated) {
            // Vanilla's own eager save of each newly FULL chunk, with its 10-second cooldown, now rather than mid-batch.
            for (ChunkPos centre : centres) {
                for (ChunkPos pos : area(centre)) { if (VanillaChunkSave.saveIfNeeded(level, pos)) { saved++; } }
            }
        }
        // Also finish the unloads the previous batch left, so that no test's own store queues behind them.
        boolean drained = await(level, () -> drainUnloads(level), deadline);
        var flush = chunks.chunkMap.worker.synchronize(true);
        boolean flushed = await(level, flush::isDone, deadline) && !flush.isCompletedExceptionally();
        AdvancedRocketryCommunity.LOGGER.info("ARCE_GAMETEST_FIXTURE_AREAS batch={} stage=prepare areas={} held={} generated={} saved={} drained={} flushed={} ms={}",
                BATCH, centres.size(), hold, generated, saved, drained, flushed, (System.nanoTime() - started) / 1_000_000L);
        return new Batch(level, centres, generated && flushed);
    }

    /** The centre is entity-ticking, and every chunk of its FULL area may be saved: no generation step is pending. */
    static boolean generated(ServerLevel level, ChunkPos centre) {
        var holder = VanillaChunkSave.holder(level, centre);
        var ticking = holder == null ? null : holder.getEntityTickingChunkFuture().getNow(null);
        return ticking != null && ticking.left().isPresent() && area(centre).stream().allMatch(pos -> VanillaChunkSave.generated(level, pos));
    }

    static List<ChunkPos> area(ChunkPos centre) {
        var area = new ArrayList<ChunkPos>();
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dz = -RADIUS; dz <= RADIUS; dz++) { area.add(new ChunkPos(centre.x + dx, centre.z + dz)); }
        }
        return area;
    }

    /** Runs the chunk source's main-thread tasks, as a blocking chunk load does, until done or the deadline. */
    private static boolean await(ServerLevel level, BooleanSupplier done, long deadline) {
        while (!done.getAsBoolean()) {
            if (System.nanoTime() - deadline > 0) { return false; }
            int ran = 0;
            while (ran < 256 && level.getChunkSource().pollTask()) { ran++; }
            if (ran == 0) { LockSupport.parkNanos(500_000L); }
        }
        return true;
    }

    /**
     * One pass of vanilla's {@code ChunkMap.processUnloads} over every dropped chunk and every ready unload callback, as
     * {@code saveAllChunks(true)} runs it; true when none is left. Its eager save sweep is skipped: the time supplier
     * turns false once both queues are empty. A chunk still waiting for its chunk-to-save future stays pending.
     */
    private static boolean drainUnloads(ServerLevel level) {
        ChunkMap map = level.getChunkSource().chunkMap;
        LongSet toDrop = ObfuscationReflectionHelper.getPrivateValue(ChunkMap.class, map, "f_140139_");
        Queue<Runnable> unloadQueue = ObfuscationReflectionHelper.getPrivateValue(ChunkMap.class, map, "f_140125_");
        try {
            Native.PROCESS_UNLOADS.invoke(map, (BooleanSupplier) () -> !toDrop.isEmpty() || !unloadQueue.isEmpty());
        } catch (IllegalAccessException | InvocationTargetException failure) {
            throw new IllegalStateException("Vanilla chunk unloading unavailable", failure);
        }
        return toDrop.isEmpty() && unloadQueue.isEmpty();
    }

    // Lazy native access keeps the area list's unit test independent of Minecraft bootstrap and SRG names.
    private static final class Native {
        private static final TicketType<ChunkPos> HOLD = TicketType.create("arce_escrow_core_fixture", Comparator.comparingLong(ChunkPos::toLong));
        // Development-only access to ChunkMap.processUnloads, like VanillaChunkSave; no AT or mixin.
        private static final Method PROCESS_UNLOADS = ObfuscationReflectionHelper.findMethod(ChunkMap.class, "m_140353_", BooleanSupplier.class);
    }
}
