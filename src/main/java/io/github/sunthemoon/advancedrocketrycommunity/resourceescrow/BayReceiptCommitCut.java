package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import com.mojang.logging.LogUtils;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import org.slf4j.Logger;

/**
 * W27d1's native cut seam on the receipt committer (W27A-DESIGN section 9's {@code bayCheckpoint=<cut>}). Inert until
 * the coordinator arms it, which only happens on a gated release-test server: then the next replacement pauses before
 * the move (staged-only) or after it (replaced-before-callback) and prints {@code ARCE_RELEASE_BAY_PAUSED cut=<id>
 * op=<uuid>}, or throws before the move (an unknown outcome whose exact candidate is retried) or, for W170's B15, after
 * it (a replacement followed by an exception, which the writer's readback resolves). It never launches or registers
 * anything. The IO thread reads only this object's volatile cut and latch.
 */
public final class BayReceiptCommitCut implements CheckedSavedDataFile.Committer {
    public enum Point { STAGED, MOVED }
    public enum Mode { PAUSE, UNKNOWN }
    private record Armed(Point point, Mode mode, String cut, UUID op) { }
    private static final Logger LOGGER = LogUtils.getLogger();
    private final CountDownLatch release = new CountDownLatch(1);
    private final java.util.concurrent.atomic.AtomicBoolean guardPaused = new java.util.concurrent.atomic.AtomicBoolean();
    private volatile Armed armed;

    /** One-shot: the next receipt replacement fires this cut. */
    public void arm(Point point, Mode mode, String cut, UUID op) {
        armed = new Armed(Objects.requireNonNull(point), Objects.requireNonNull(mode), Objects.requireNonNull(cut), Objects.requireNonNull(op));
        LOGGER.info("ARCE_RELEASE_BAY_ARMED cut={} op={} point={} mode={}", cut, op, point, mode);
    }

    /** Clean stop (B18): a paused replacement continues, so the drain can finish it. */
    public void release() { release.countDown(); }

    @Override
    public void commit(Path staged, Path target) throws IOException {
        Armed current = armed;
        if (current != null && current.point() == Point.STAGED) { fire(current); }
        CheckedSavedDataFile.atomicMove(staged, target);
        if (current != null && current.point() == Point.MOVED) { fire(current); }
    }

    /**
     * W170 B16: once per process, a guarded removal's guard pauses here on the IO thread before it reads its tokens, with
     * the same marker and release as a committer pause. Later guards of the session pass at once.
     */
    public void pauseBeforeGuard(String cut, UUID op) {
        if (guardPaused.compareAndSet(false, true)) { pause(cut, op); }
    }

    private void fire(Armed current) throws IOException {
        armed = null;
        if (current.mode() == Mode.UNKNOWN) {
            LOGGER.info("ARCE_RELEASE_BAY_INJECTED cut={} op={} outcome=UNKNOWN point={}", current.cut(), current.op(), current.point());
            throw new IOException("Injected an ambiguous receipt replacement");
        }
        pause(current.cut(), current.op());
    }

    private void pause(String cut, UUID op) {
        LOGGER.info("ARCE_RELEASE_BAY_PAUSED cut={} op={}", cut, op);
        boolean interrupted = false;
        while (true) {
            try { release.await(); break; } catch (InterruptedException again) { interrupted = true; }
        }
        if (interrupted) { Thread.currentThread().interrupt(); }
        LOGGER.info("ARCE_RELEASE_BAY_RELEASED cut={} op={}", cut, op);
    }
}
