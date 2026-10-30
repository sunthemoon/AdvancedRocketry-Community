package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.server.MinecraftServer;

/** Exact, release-only selection of a native stop/drain observation. */
final class EscrowLoaderS6ShutdownProbe {
    static final String PROPERTY = "advancedrocketrycommunity.escrowS6Shutdown";
    static final String RETIRED_OPERATION = "advancedrocketrycommunity.escrowS6RetiredOperation";
    static final String L1_INFLIGHT = "LF_S6_G3_L1_INFLIGHT";
    static final String L2_INFLIGHT = "LF_S6_G3_L2_INFLIGHT";
    static final String L1_QUEUED = "LF_S6_G3_L1_QUEUED";
    static final String L1_UNKNOWN = "LF_S6_G3_L1_UNKNOWN";
    static final String L2_UNKNOWN = "LF_S6_G3_L2_UNKNOWN";
    static final String FAILED_CLOSE = "LF_S6_G3_FAILED_CLOSE";
    static final String NATIVE_CASE = "LF_M_K7";
    private final MinecraftServer server;
    private final boolean recovery;
    private final String cut;
    private volatile UUID operation;
    private final EscrowLoaderCutProbe fixture;
    private final EscrowLoaderS6Recovery observer;
    private final AtomicBoolean selected = new AtomicBoolean();
    private final AtomicBoolean held = new AtomicBoolean();
    private volatile boolean ioHeld;
    private volatile String queuedState = "";
    private boolean injected;

    static boolean selected(boolean hooks, String nativeCase, String selection) {
        if (!hooks) { return false; }
        if (L1_UNKNOWN.equals(selection)) { return "LF_M_E4L1_UNKNOWN".equals(nativeCase); }
        if (L2_UNKNOWN.equals(selection)) { return "LF_M_E4L2_UNKNOWN".equals(nativeCase); }
        return NATIVE_CASE.equals(nativeCase) && (L1_INFLIGHT.equals(selection) || L2_INFLIGHT.equals(selection)
                || L1_QUEUED.equals(selection) || FAILED_CLOSE.equals(selection));
    }

    static boolean queuedReady(String state) {
        return "active:true,successor:true,completions:0,running:true,unknown:false,generation:2,durable:1".equals(state);
    }
    static boolean unknownReady(String cut, String state) {
        var match = java.util.regex.Pattern.compile("active:true,successor:(true|false),completions:0,running:false,unknown:true,generation:([0-9]{1,18}),durable:([0-9]{1,18})").matcher(state);
        if (!match.matches()) { return false; }
        long generation = Long.parseLong(match.group(2)), durable = Long.parseLong(match.group(3));
        return generation == durable + 1 && (L1_UNKNOWN.equals(cut) && durable == 1 || L2_UNKNOWN.equals(cut) && durable >= 2);
    }

    static boolean stopBoundary(String label, boolean recovery) { return stopBoundary(L1_INFLIGHT, label, recovery); }

    static boolean stopBoundary(String cut, String label, boolean recovery) {
        return !recovery && (L1_INFLIGHT.equals(cut) && "K1".equals(label) || L2_INFLIGHT.equals(cut) && "K8".equals(label));
    }

    EscrowLoaderS6ShutdownProbe(MinecraftServer server, boolean recovery) {
        EscrowTestEndpoints.requireEnabled(server);
        EscrowTags.require(selected(Boolean.getBoolean(EscrowTestEndpoints.HOOKS),
                System.getProperty("advancedrocketrycommunity.escrowCase"), System.getProperty(PROPERTY)),
                "S6 shutdown requires its exact native/observation selector pair");
        this.server = server; this.recovery = recovery;
        cut = System.getProperty(PROPERTY);
        fixture = !recovery ? new EscrowLoaderCutProbe(server, NATIVE_CASE, false)
                : L1_UNKNOWN.equals(cut) ? new EscrowLoaderCutProbe(server, "LF_M_E4L1_UNKNOWN", true) : null;
        observer = recovery && !L1_UNKNOWN.equals(cut) ? new EscrowLoaderS6Recovery(server,
                L2_INFLIGHT.equals(cut) ? UUID.fromString(System.getProperty(RETIRED_OPERATION)) : null) : null;
    }

    void startTick() { if (fixture != null) { fixture.startTick(); } }
    void tick() {
        if (observer != null) { observer.tick(); } else { fixture.tick(); }
        if (recovery || selected.get()) { return; }
        if (L1_QUEUED.equals(cut) && ioHeld) {
            String state = writerState();
            if (queuedReady(state) && selected.compareAndSet(false, true)) {
                queuedState = state;
                AdvancedRocketryCommunity.LOGGER.info("ARCE_LOADER_S6_DRAIN_READY cut={} boundary=K1 running=true state={}", cut, state);
            }
        }
        if (FAILED_CLOSE.equals(cut) && injected) {
            var runtime = EscrowRuntime.of(server); var data = ResourceEscrowSavedData.existing(server);
            if (runtime.barrier().ioFailures() == 0 || data == null) { return; }
            var coordinator = data.bind(server);
            EscrowTags.require(data.ledger().records().size() == 1, "S6 failed close lost its durable L1");
            var op = data.ledger().records().get(0);
            EscrowTags.require(runtime.barrier().ioFailures() == 1 && data.ledger().issuedThrough() == 1
                    && coordinator.admissionAuthority().pendingCount() == 1 && coordinator.locked(op.source().key())
                    && coordinator.locked(op.destination().key()) && !coordinator.reconciliationCheckpointProved(op.id())
                    && EscrowReason.BARRIER_IO.name().equals(runtime.reconciliationScheduler().view(op.id()).reason()),
                    "S6 failed close changed or released authority");
            AdvancedRocketryCommunity.LOGGER.info("ARCE_LOADER_S6_CLOSE_FAILURE records=1 pending=1 issued=1 io_failures=1 reason=BARRIER_IO proof=false");
            normalHalt("BARRIER_IO", "");
        }
    }

    void checkpoint(String label) {
        if (recovery) { return; }
        if (FAILED_CLOSE.equals(cut) && !injected && "C4_SOURCE_SELECTED".equals(label)) {
            injected = true;
            int size = EscrowCargoContentionProbe.injectCacheClose(true, server.overworld().getChunkSource().chunkMap.worker);
            AdvancedRocketryCommunity.LOGGER.info("ARCE_LOADER_S6_CACHE regions={} own_region_cached=false", size);
        }
        if (L2_INFLIGHT.equals(cut) && "C6_JOINT_READBACK".equals(label)) {
            EscrowTags.require(server.isSameThread(), "S6 L2 operation capture requires the server thread");
            var data = ResourceEscrowSavedData.existing(server);
            EscrowTags.require(data != null && data.ledger().records().size() == 1, "S6 L2 lost its proved operation");
            operation = data.ledger().records().get(0).id();
        }
        // K7 is setup only: never enter its forced-stop pause at the successful proof.
        if (!"C6_JOINT_READBACK".equals(label)) { fixture.checkpoint(label); }
        if ((L1_UNKNOWN.equals(cut) || L2_UNKNOWN.equals(cut)) && "WRITE_UNKNOWN".equals(label) && !selected.get()) {
            String state = writerState();
            EscrowTags.require(unknownReady(cut, state), "S6 stop missed the selected UNKNOWN candidate");
            normalHalt(label, " state=" + state); return;
        }
        if (L1_QUEUED.equals(cut) && "K1".equals(label) && held.compareAndSet(false, true)) {
            EscrowTags.require(!server.isSameThread() && server.isRunning(), "S6 queued stop missed the live writer");
            ioHeld = true; awaitStop();
            EscrowTags.require(selected.get() && queuedReady(queuedState), "S6 stop preceded the actual successor request");
            AdvancedRocketryCommunity.LOGGER.info("ARCE_LOADER_S6_DRAIN_RELEASE cut={} boundary=K1 running=false state={}", cut, queuedState);
            return;
        }
        if (!stopBoundary(cut, label, false) || !selected.compareAndSet(false, true)) { return; }
        EscrowTags.require(!server.isSameThread() && server.isRunning(), "S6 shutdown missed the live writer");
        EscrowTags.require(!L2_INFLIGHT.equals(cut) || operation != null, "S6 L2 lacks its server-thread operation observation");
        String observed = operation == null ? "" : " operation=" + operation;
        AdvancedRocketryCommunity.LOGGER.info("ARCE_LOADER_S6_DRAIN_READY cut={} boundary={} running=true{}", cut, label, observed);
        awaitStop();
        AdvancedRocketryCommunity.LOGGER.info("ARCE_LOADER_S6_DRAIN_RELEASE cut={} boundary={} running=false{}", cut, label, observed);
    }

    private void awaitStop() {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (server.isRunning() && System.nanoTime() < deadline) {
            try { TimeUnit.MILLISECONDS.sleep(10); }
            catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new IllegalStateException(failure); }
        }
        EscrowTags.require(!server.isRunning(), "S6 normal stop did not arrive within the existing 30-second checkpoint bound");
    }

    private void normalHalt(String boundary, String observation) {
        EscrowTags.require(server.isSameThread() && server.isRunning(), "S6 normal halt requires the live server thread");
        if (!selected.compareAndSet(false, true)) { return; }
        AdvancedRocketryCommunity.LOGGER.info("ARCE_LOADER_S6_DRAIN_READY cut={} boundary={} running=true{}", cut, boundary, observation);
        server.halt(false);
        EscrowTags.require(!server.isRunning(), "S6 normal halt did not set the stop request");
        AdvancedRocketryCommunity.LOGGER.info("ARCE_LOADER_S6_DRAIN_RELEASE cut={} boundary={} running=false{}", cut, boundary, observation);
    }

    private String writerState() {
        EscrowTags.require(server.isSameThread(), "S6 writer observation requires the owner thread");
        try {
            var field = EscrowCoordinator.class.getDeclaredField("writer"); field.setAccessible(true);
            var coordinator = ResourceEscrowSavedData.existing(server).bind(server);
            return ((EscrowLedgerWriter) field.get(coordinator)).observation();
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException("S6 writer observation unavailable", failure); }
    }
}
