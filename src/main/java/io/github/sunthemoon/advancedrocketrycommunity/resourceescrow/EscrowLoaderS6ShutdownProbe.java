package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.server.MinecraftServer;

/** Exact, release-only selection of a native stop/drain observation. */
final class EscrowLoaderS6ShutdownProbe {
    static final String PROPERTY = "advancedrocketrycommunity.escrowS6Shutdown";
    static final String L1_INFLIGHT = "LF_S6_G3_L1_INFLIGHT";
    static final String NATIVE_CASE = "LF_M_K7";
    private final MinecraftServer server;
    private final boolean recovery;
    private final EscrowLoaderCutProbe fixture;
    private final EscrowLoaderS6Recovery observer;
    private final AtomicBoolean selected = new AtomicBoolean();

    static boolean selected(boolean hooks, String nativeCase, String selection) {
        return hooks && NATIVE_CASE.equals(nativeCase) && L1_INFLIGHT.equals(selection);
    }

    static boolean stopBoundary(String label, boolean recovery) { return !recovery && "K1".equals(label); }

    EscrowLoaderS6ShutdownProbe(MinecraftServer server, boolean recovery) {
        EscrowTestEndpoints.requireEnabled(server);
        EscrowTags.require(selected(Boolean.getBoolean(EscrowTestEndpoints.HOOKS),
                System.getProperty("advancedrocketrycommunity.escrowCase"), System.getProperty(PROPERTY)),
                "S6 shutdown requires its exact native/observation selector pair");
        this.server = server; this.recovery = recovery;
        fixture = recovery ? null : new EscrowLoaderCutProbe(server, NATIVE_CASE, false);
        observer = recovery ? new EscrowLoaderS6Recovery(server) : null;
    }

    void startTick() { if (fixture != null) { fixture.startTick(); } }
    void tick() { if (observer != null) { observer.tick(); } else { fixture.tick(); } }

    void checkpoint(String label) {
        if (recovery) { return; }
        // K7 is setup only: never enter its forced-stop pause at the successful proof.
        if (!"C6_JOINT_READBACK".equals(label)) { fixture.checkpoint(label); }
        if (!stopBoundary(label, false) || !selected.compareAndSet(false, true)) { return; }
        EscrowTags.require(!server.isSameThread() && server.isRunning(), "S6 shutdown missed the live L1 writer");
        AdvancedRocketryCommunity.LOGGER.info("ARCE_LOADER_S6_DRAIN_READY cut={} boundary=K1 running=true", L1_INFLIGHT);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (server.isRunning() && System.nanoTime() < deadline) {
            try { TimeUnit.MILLISECONDS.sleep(10); }
            catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new IllegalStateException(failure); }
        }
        EscrowTags.require(!server.isRunning(), "S6 normal stop did not arrive within the existing 30-second checkpoint bound");
        AdvancedRocketryCommunity.LOGGER.info("ARCE_LOADER_S6_DRAIN_RELEASE cut={} boundary=K1 running=false", L1_INFLIGHT);
    }
}
