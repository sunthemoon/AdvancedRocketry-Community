package io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence;

import com.mojang.logging.LogUtils;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataPolicy;
import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;

/**
 * W129bc native cuts (design section 6.4): release-test seams of the satellite registry writer, inert in production.
 * They are armed only through {@code arce satellite release-test registry-writer} on an empty dedicated server that runs
 * with the release-test property; every public action refuses elsewhere. The IO thread reads only this object's
 * volatile cut and latch, never the registry. The effect and discovery holds are read by the terminal's tick pass and
 * the discovery replay, so a cut can fall between a published commit and its dependent effect.
 */
public final class SatelliteRegistryWriterCuts {
    private static final Logger LOGGER = LogUtils.getLogger();

    public enum Point { CAPTURE, STAGED, MOVED }
    public enum Mode { PAUSE, STALL, FAIL, UNKNOWN }
    private record Cut(Point point, Mode mode, CountDownLatch release) { }

    private volatile Cut cut, held;
    private final Map<CheckedSavedDataPolicy, CheckedSavedDataPolicy> wrapped = new ConcurrentHashMap<>();
    // Owner thread only.
    private Runnable atStopping, atCrash;
    private boolean releaseOnCrash, effectsHeld, discoveryHeld;

    SatelliteRegistryWriterCuts() { }

    public static boolean enabled(MinecraftServer server) {
        return server != null && Boolean.getBoolean(SatelliteRegistryTestAwait.PROPERTY) && server.isDedicatedServer()
                && server.isSameThread() && server.getPlayerList() != null && server.getPlayerList().getPlayerCount() == 0;
    }

    private static void require(MinecraftServer server) {
        if (!enabled(server)) {
            throw new IllegalStateException("Registry writer cuts need an empty release-test dedicated server");
        }
    }

    /** The next attempt stops at {@code point}: PAUSE until released, STALL for good, FAIL or UNKNOWN every attempt. */
    public void arm(MinecraftServer server, Point point, Mode mode) {
        require(server);
        if (mode == Mode.FAIL && point != Point.STAGED || mode == Mode.UNKNOWN && point != Point.MOVED) {
            throw new IllegalArgumentException("Unsupported cut " + point + " " + mode);
        }
        cut = new Cut(point, mode, new CountDownLatch(1));
        LOGGER.info("ARCE_RELEASE_SRW_ARMED point={} mode={}", point, mode);
    }

    /** Releases the paused attempt; a cut armed while it waits stays armed for the next attempt. */
    public void release(MinecraftServer server) {
        require(server);
        var current = held != null ? held : cut;
        if (current != null) { current.release().countDown(); }
    }

    /** At {@code ServerStoppingEvent} (normal path only), after the latch: a logout-time mutation, then the release. */
    public void atStopping(MinecraftServer server, Runnable mutation) {
        require(server);
        atStopping = Objects.requireNonNull(mutation, "mutation");
    }

    /** At the next tick START: a mutation, optionally the release of a paused attempt, then a thrown tick exception. */
    public void crashAtNextTick(MinecraftServer server, Runnable mutation, boolean release) {
        require(server);
        atCrash = Objects.requireNonNull(mutation, "mutation");
        releaseOnCrash = release;
    }

    /** Covered terminal entries wait (R4: a commit published, its terminal effect not yet run). */
    public void holdEffects(MinecraftServer server, boolean hold) { require(server); effectsHeld = hold; }

    /** The discovery replay waits (a paid receipt committed, its celestial write not yet made). */
    public void holdDiscovery(MinecraftServer server, boolean hold) { require(server); discoveryHeld = hold; }

    /** The writer's state and the registry's record counts, for the release-test status line. */
    public String status(MinecraftServer server, SatelliteMissionSavedData data) {
        require(server);
        return data.writerForTests().releaseTestStatus() + " satellites=" + data.satellites().size()
                + " missions=" + data.missions().size();
    }

    /** False unless a release-test command set it. */
    public boolean effectsHeld() { return effectsHeld; }
    public boolean discoveryHeld() { return discoveryHeld; }

    /** Carried by the capture to the IO thread: the CAPTURE cut runs before anything is encoded or staged. */
    Runnable beforeEncode(long sequence) {
        var current = cut;
        return current != null && current.point() == Point.CAPTURE ? () -> hold(current, sequence) : null;
    }

    /** STAGED runs after the staging file is forced and read back; MOVED runs after the atomic move. */
    CheckedSavedDataPolicy policy(CheckedSavedDataPolicy base) {
        if (cut == null) { return base; }
        return wrapped.computeIfAbsent(base, policy -> new CheckedSavedDataPolicy(policy.type(), (staged, target) -> {
            at(Point.STAGED);
            policy.committer().commit(staged, target);
            at(Point.MOVED);
        }, (file, type) -> {
            var current = cut;
            if (current != null && current.mode() == Mode.UNKNOWN) {
                throw new IllegalStateException("W129bc injected unreadable readback");
            }
            return policy.read(file);
        }));
    }

    void onStopping(long sequence) {
        LOGGER.info("ARCE_RELEASE_SRW_STOPPING_EVENT sequence={}", sequence);
        if (atStopping == null) { return; }
        var mutation = atStopping;
        atStopping = null;
        mutation.run();
        releaseHeld();
    }

    void onTickStart(SatelliteMissionSavedData data) {
        if (atCrash == null) { return; }
        var mutation = atCrash;
        atCrash = null;
        mutation.run();
        if (releaseOnCrash) { releaseHeld(); }
        // An attempt still in flight here is the stop path's first budgeted attempt (ADR-074 item 10).
        LOGGER.info("ARCE_RELEASE_SRW_CRASH sequence={} in_flight={}", data.mutationSequence(),
                data.writerForTests().inFlight());
        throw new IllegalStateException("W129bc injected tick exception");
    }

    private void releaseHeld() {
        var current = held != null ? held : cut;
        if (current != null) { current.release().countDown(); }
    }

    private void at(Point point) throws IOException {
        var current = cut;
        if (current == null || current.point() != point) { return; }
        switch (current.mode()) {
            case PAUSE, STALL -> hold(current, -1);
            case FAIL, UNKNOWN -> {
                LOGGER.info("ARCE_RELEASE_SRW_INJECTED point={} mode={}", point, current.mode());
                throw new IOException("W129bc injected " + (current.mode() == Mode.FAIL ? "definite failure" : "ambiguous replacement"));
            }
        }
    }

    private void hold(Cut current, long sequence) {
        held = current;
        LOGGER.info("ARCE_RELEASE_SRW_PAUSED point={} mode={} sequence={}", current.point(), current.mode(),
                sequence < 0 ? "staged" : String.valueOf(sequence));
        // STALL is never released: only the watchdog or a kill ends the process (R7, interrupted teardown).
        CountDownLatch latch = current.mode() == Mode.PAUSE ? current.release() : new CountDownLatch(1);
        boolean interrupted = false;
        while (true) {
            try { latch.await(); break; } catch (InterruptedException again) { interrupted = true; }
        }
        if (interrupted) { Thread.currentThread().interrupt(); }
        held = null;
        if (cut == current) { cut = null; } // one-shot: the next attempt passes unless another cut was armed
        LOGGER.info("ARCE_RELEASE_SRW_RELEASED point={}", current.point());
    }
}
