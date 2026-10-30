package io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataPolicy;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntSupplier;

/** One-shot IO-side fault/delay, released by observation ticks or explicit diagnostic cleanup, never a product timer. */
public final class W208WriterControl {
    private final String scenario;
    private final AtomicBoolean claimed = new AtomicBoolean();
    private final CountDownLatch released = new CountDownLatch(1);
    private volatile int enteredTick = -1;
    private volatile boolean held;

    public W208WriterControl(String scenario) {
        if (!Set.of("small-quiet", "near-quiet", "near-traffic", "near-c1-unknown", "near-c3-unknown",
                "near-c1-expiry", "near-c3-expiry", "near-interrupted").contains(scenario)) {
            throw new IllegalArgumentException("Unknown W208 case");
        }
        this.scenario = scenario;
    }

    boolean claim(String phase) {
        boolean target = scenario.contains("-" + phase.toLowerCase(java.util.Locale.ROOT) + "-")
                || scenario.equals("near-interrupted") && phase.equals("C1");
        return target && claimed.compareAndSet(false, true);
    }

    public CheckedSavedDataPolicy policy(String phase, IntSupplier tick) {
        var type = ManagedSavedDataType.ROCKET_TRANSACTIONS; var atomic = CheckedSavedDataPolicy.atomic(type);
        var unknown = new AtomicBoolean();
        return new CheckedSavedDataPolicy(type, (staged, target) -> {
            boolean inject = claim(phase);
            if (inject && (scenario.endsWith("expiry") || scenario.endsWith("interrupted"))) {
                enteredTick = tick.getAsInt(); held = true;
                boolean interrupted = false;
                try {
                    while (true) { try { released.await(); break; } catch (InterruptedException error) { interrupted = true; } }
                } finally { held = false; if (interrupted) { Thread.currentThread().interrupt(); } }
            }
            atomic.committer().commit(staged, target);
            if (inject && scenario.endsWith("unknown")) { unknown.set(true); throw new IOException("W208 one-shot post-move fault"); }
        }, (file, kind) -> {
            if (unknown.getAndSet(false)) { throw new IllegalStateException("W208 one-shot readback fault"); }
            return atomic.reader().read(file, kind);
        });
    }

    public boolean held() { return held; }
    public void tick(int now) { if (scenario.endsWith("expiry") && enteredTick >= 0 && now - enteredTick >= 120) { release(); } }
    public void release() { released.countDown(); }
}
