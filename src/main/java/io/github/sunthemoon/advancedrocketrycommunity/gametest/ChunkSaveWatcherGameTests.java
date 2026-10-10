package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import java.lang.reflect.Field;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.ChunkDataEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Native success notification plus explicitly labelled callback probes; not storage or removal evidence. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ChunkSaveWatcherGameTests {
    private static final String SUCCESS = "chunk_save_watcher_success";
    private static final String IDENTITY = "chunk_save_watcher_identity";
    // One driver owns this slot; its required checker keeps SUCCESS active until terminal checks finish.
    private static TerminalObservation active;

    private ChunkSaveWatcherGameTests() { }

    @GameTest(template = "empty", batch = SUCCESS, timeoutTicks = 40)
    public static void installedWatcherClosesAfterItsRealSuccessfulTerminalNotification(GameTestHelper helper) {
        helper.assertTrue(active == null, "Another watcher success fixture owns the terminal observation");
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
        ChunkSaveWatcher watcher = ChunkSaveWatcher.start(helper, pos);
        TerminalObservation fixture = null;
        try {
            fixture = new TerminalObservation(helper, pos, watcher, state(watcher));
            active = fixture;
            owner(helper).addListener(fixture);
            MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, fixture.stoppingListener);
            helper.assertFalse(watcher.any(pos), "The newly installed watcher already recorded a save");
            postSave(helper, pos);
            helper.assertTrue(watcher.between(pos, fixture.installedTick, fixture.installedTick),
                    "The installed subscriber did not record a matching Forge save event");
            // Termination is the normal required test's success, never a direct watcher callback.
            helper.runAfterDelay(1, helper::succeed);
        } catch (RuntimeException | Error failure) {
            if (fixture == null) { stop(watcher, failure); }
            else { fixture.close(failure); }
            throw failure;
        }
    }

    @GameTest(template = "empty", batch = SUCCESS, timeoutTicks = 40)
    public static void aRunningRequiredCheckerObservesTheDriversSuccessfulTerminalState(GameTestHelper helper) {
        helper.startSequence()
                .thenWaitUntil(() -> {
                    TerminalObservation fixture = active;
                    helper.assertTrue(fixture != null && fixture.level == helper.getLevel(),
                            "The terminal driver has not installed its owned observation");
                    helper.assertTrue(fixture.passed && fixture.failure == null,
                            "The driver's native successful terminal notification is still pending or failed");
                })
                .thenExecute(() -> validateSuccessfulTerminalState(helper))
                .thenSucceed();
    }

    private static void validateSuccessfulTerminalState(GameTestHelper helper) {
        TerminalObservation fixture = active;
        helper.assertTrue(fixture != null && fixture.level == helper.getLevel(),
                "The required checker lost the driver's owned observation");
        try {
            helper.assertTrue(fixture.passed && fixture.failure == null,
                    "The native success witness did not receive a successful terminal notification");
            helper.assertTrue(fixture.state.isClosed(),
                    "The watcher remained active after native successful termination");
            helper.assertTrue(helper.getLevel().getGameTime() > fixture.installedTick,
                    "The post-terminal observation needs a distinct native tick");
            String history = fixture.watcher.toString();
            ChunkDataEvent.Save later = postSave(helper, fixture.pos);
            helper.assertTrue(history.equals(fixture.watcher.toString()),
                    "A post-terminal bus event extended the watcher history");
            fixture.watcher.onSave(later); // An explicit stale callback probe, not notification/removal proof.
            helper.assertTrue(history.equals(fixture.watcher.toString()),
                    "A stale callback extended the closed watcher history");
        } catch (RuntimeException | Error failure) {
            fixture.close(failure);
            throw failure;
        }
    }

    @AfterBatch(batch = SUCCESS)
    public static void releaseTerminalObservation(ServerLevel level) {
        TerminalObservation fixture = active;
        if (fixture != null && fixture.level == level) { fixture.close(fixture.failure); }
    }

    @GameTest(template = "empty", batch = IDENTITY, timeoutTicks = 40)
    public static void directForeignLevelAndServerIdentityProbesPreserveTheOwnedObserver(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
        ChunkSaveWatcher watcher = ChunkSaveWatcher.start(helper, pos);
        Throwable primary = null;
        try {
            ChunkSaveWatchState state = state(watcher);
            LevelChunk chunk = loadedChunk(helper, pos);
            ServerLevel foreign = level.getServer().getLevel(CelestialIds.MOON_LEVEL);
            helper.assertTrue(foreign != null && foreign != level, "The native foreign Level is unavailable");
            // Do not post a mismatched Level/chunk pair to unrelated production save subscribers.
            watcher.onSave(new ChunkDataEvent.Save(chunk, foreign, new CompoundTag()));
            helper.assertFalse(watcher.any(pos), "The direct foreign-Level callback recorded a save");
            // Null is a malformed foreign identity, not a second native MinecraftServer.
            watcher.onStopping(new ServerStoppingEvent(null));
            helper.assertFalse(state.isClosed(), "A direct null-server callback closed the owned observer");
            postSave(helper, pos);
            helper.assertTrue(watcher.any(pos), "The identity probes disabled matching save-bus dispatch");
            String history = watcher.toString();
            // Never post synthetic stopping to the global bus: it would quiesce unrelated production services.
            watcher.onStopping(new ServerStoppingEvent(level.getServer()));
            helper.assertTrue(state.isClosed(), "The direct matching-server callback did not close the observer");
            watcher.onSave(new ChunkDataEvent.Save(chunk, level, new CompoundTag()));
            helper.assertTrue(history.equals(watcher.toString()), "A stale direct callback changed closed history");
            watcher.stop();
            watcher.stop();
            helper.succeed();
        } catch (RuntimeException | Error failure) {
            primary = failure;
            throw failure;
        } finally {
            stop(watcher, primary);
        }
    }

    private static LevelChunk loadedChunk(GameTestHelper helper, BlockPos pos) {
        ServerLevel level = helper.getLevel();
        helper.assertTrue(level.getServer().isSameThread(), "Save probe must run on the native server thread");
        LevelChunk chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        helper.assertTrue(chunk != null && chunk.getLevel() == level, "Save probe needs its already loaded native chunk");
        return chunk;
    }

    /** Normal native serialization plus Forge dispatch, not a durable world save. At most two posts per test. */
    private static ChunkDataEvent.Save postSave(GameTestHelper helper, BlockPos pos) {
        LevelChunk chunk = loadedChunk(helper, pos);
        ChunkDataEvent.Save event = new ChunkDataEvent.Save(chunk, helper.getLevel(),
                ChunkSerializer.write(helper.getLevel(), chunk));
        MinecraftForge.EVENT_BUS.post(event);
        return event;
    }

    /** Same bounded development-only helper-owner access as the adjacent fixtures; no lifecycle state mutation. */
    private static GameTestInfo owner(GameTestHelper helper) {
        try {
            Field field = GameTestHelper.class.getDeclaredField("testInfo");
            field.setAccessible(true);
            return (GameTestInfo) field.get(helper);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Watcher success witness cannot access its owning test", failure);
        }
    }

    /** Read one private field on this fixture's own watcher, not event-bus internals or engine collections. */
    private static ChunkSaveWatchState state(ChunkSaveWatcher watcher) {
        try {
            Field field = ChunkSaveWatcher.class.getDeclaredField("observations");
            field.setAccessible(true);
            return (ChunkSaveWatchState) field.get(watcher);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Watcher fixture cannot observe its owned lifecycle state", failure);
        }
    }

    private static void stop(ChunkSaveWatcher watcher, Throwable primary) {
        try { watcher.stop(); }
        catch (RuntimeException | Error failure) {
            if (primary == null) { throw failure; }
            if (primary != failure) { primary.addSuppressed(failure); }
        }
    }

    private static final class TerminalObservation implements GameTestListener {
        private final ServerLevel level;
        private final BlockPos pos;
        private final ChunkSaveWatcher watcher;
        private final ChunkSaveWatchState state;
        private final long installedTick;
        private final Consumer<ServerStoppingEvent> stoppingListener = this::onStopping;
        private boolean passed;
        private Throwable failure;
        private boolean closed;

        private TerminalObservation(GameTestHelper helper, BlockPos pos, ChunkSaveWatcher watcher,
                ChunkSaveWatchState state) {
            level = helper.getLevel();
            this.pos = pos.immutable();
            this.watcher = watcher;
            this.state = state;
            installedTick = level.getGameTime();
        }

        @Override public void testStructureLoaded(GameTestInfo info) { }
        @Override public void testPassed(GameTestInfo info) { passed = true; }
        @Override public void testFailed(GameTestInfo info) { failure = info.getError(); }

        private void onStopping(ServerStoppingEvent event) {
            if (event.getServer() == level.getServer()) { close(null); }
        }

        private void close(Throwable primary) {
            if (closed) { return; }
            closed = true;
            Throwable first = primary;
            try {
                watcher.stop();
            } catch (RuntimeException | Error cleanupFailure) {
                first = retain(first, cleanupFailure);
            }
            try {
                MinecraftForge.EVENT_BUS.unregister(stoppingListener);
            } catch (RuntimeException | Error cleanupFailure) {
                first = retain(first, cleanupFailure);
            } finally {
                if (active == this) { active = null; }
            }
            if (primary == null && first instanceof RuntimeException cleanupFailure) { throw cleanupFailure; }
            if (primary == null && first instanceof Error cleanupFailure) { throw cleanupFailure; }
        }

        private static Throwable retain(Throwable first, Throwable next) {
            if (first == null) { return next; }
            if (first != next) { first.addSuppressed(next); }
            return first;
        }
    }
}
