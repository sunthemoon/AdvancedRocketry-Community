package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import com.mojang.logging.LogUtils;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferPhase;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import java.nio.file.Path;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.server.MinecraftServer;

/**
 * The runtime-owned bay side of the shared admission authority (ADR-065's W27c1 note): the one BAY delegate, the
 * startup lookup that indexes an existing receipt file before the first tick (N6), the bound store, and the insertion
 * gate. It lives as long as {@link EscrowRuntime}; it holds no static state.
 */
public final class BayReceipts {
    private static final org.slf4j.Logger LOGGER = LogUtils.getLogger();
    private final EscrowAdmissionAuthority.Delegate bay;
    private final CheckedSavedDataFile.Committer committer;
    /** Null in production: each writer starts its own IO thread. Unit tests supply an IO thread they can await. */
    @Nullable private final java.util.function.Supplier<java.util.concurrent.ExecutorService> io;
    private BayReceiptStore store;
    @Nullable private EscrowAdmissionAuthority.Store substitute; // W95's synthetic index while its fixture holds it
    private EscrowAdmissionAuthority.Delegate.State firstTick;
    @Nullable private BayDeploymentSession session;
    @Nullable private BayReceiptCommitCut commitCut; // only on a GameTestServer or a gated release-test server
    private boolean started;

    BayReceipts(EscrowAdmissionAuthority.Delegate bay, CheckedSavedDataFile.Committer committer) { this(bay, committer, null); }
    BayReceipts(EscrowAdmissionAuthority.Delegate bay, CheckedSavedDataFile.Committer committer,
                @Nullable java.util.function.Supplier<java.util.concurrent.ExecutorService> io) {
        EscrowTags.require(bay.consumer() == EscrowAdmissionAuthority.Consumer.BAY, "Bay receipts need the BAY delegate");
        this.bay = bay; this.committer = committer; this.io = io;
    }

    /**
     * ServerStartingEvent, after the Levels exist and before the first tick: the storage's load-only lookup never
     * creates a file. An existing root binds and indexes at once (LOADED); a proven-absent file gives NO_FILE; a file
     * that exists but did not load keeps NOT_LOADED, so admission and insertion of every package stay refused.
     */
    void start(MinecraftServer server) {
        Path file = BayReceiptSavedData.file(server);
        start(BayReceiptSavedData.existing(server), CheckedSavedDataFile.presence(file), file, server.getTickCount());
    }
    /** The startup sequence on a looked-up root and the file's presence; unit tests drive it with real files. */
    void start(@Nullable BayReceiptSavedData existing, CheckedSavedDataFile.Presence presence, Path file, long tick) {
        started = true;
        if (bay.state() != EscrowAdmissionAuthority.Delegate.State.NOT_LOADED) { return; }
        if (existing != null) {
            bind(existing, file, tick);
        } else if (presence == CheckedSavedDataFile.Presence.ABSENT) {
            bay.noFile();
        } else {
            LOGGER.warn("ARCE_BAY_RECEIPTS_NOT_LOADED; the receipt file exists but did not load, so bay admission and package insertion stay refused");
        }
        LOGGER.info("ARCE_BAY_RECEIPTS_INDEX state={} receipts={}", bay.state(), store == null ? 0 : store.receipts().size());
    }

    private BayReceiptStore bind(BayReceiptSavedData data, Path file, long tick) {
        BayReceiptRoot root = data.root();
        var writer = io == null ? new BayReceiptWriter(file, root.storeId(), root.writerGeneration(), committer)
                : new BayReceiptWriter(file, root.storeId(), root.writerGeneration(), committer, io.get());
        writer.tick(tick); // The first write uses the real server clock, not tick zero.
        writer.baseline(root);
        var bound = new BayReceiptStore(root, writer, bay);
        data.attach(bound);
        EscrowTags.require(bound.attach(), "The BAY delegate still holds another receipt store's work");
        store = bound;
        return bound;
    }

    /** The bound store; while NO_FILE, the first admission creates the root (and its first write, the file). */
    BayReceiptStore storeForAdmission(MinecraftServer server) {
        EscrowTags.require(server.isSameThread(), "Bay receipts live on the server thread");
        if (store != null) { return store; }
        EscrowTags.require(BayReceiptSavedData.existing(server) == null, "Bay receipt admission found an unbound root");
        var created = createForAdmission(BayReceiptSavedData.file(server), server.getTickCount());
        server.overworld().getDataStorage().set(ManagedSavedDataType.BAY_RECEIPTS.dataName(), created);
        return store;
    }
    /** Creation from NO_FILE only; the new root's first generation creates the file. */
    BayReceiptSavedData createForAdmission(Path file, long tick) {
        EscrowTags.require(store == null && bay.state() == EscrowAdmissionAuthority.Delegate.State.NO_FILE,
                "Bay receipt admission needs a known-empty index");
        var created = BayReceiptSavedData.create(UUID.randomUUID());
        bind(created, file, tick);
        return created;
    }

    /**
     * The bay's insertion check (W27A-DESIGN section 6), on the server thread: true while a receipt blocks the satellite's
     * package, and also while the index is not loaded or the runtime is missing, so it fails closed.
     */
    public static boolean blocksInsertion(MinecraftServer server, UUID satellite) {
        if (!server.isSameThread() || server.overworld() == null) { return true; }
        return server.overworld().getCapability(EscrowRuntime.CAPABILITY).resolve()
                .map(runtime -> runtime.stopping() || runtime.bayReceipts(server).blocksInsertion(satellite)).orElse(true);
    }
    /** Fails closed: NOT_LOADED blocks every package; NO_FILE blocks none; LOADED asks the store's index. */
    public boolean blocksInsertion(UUID satellite) {
        return switch (bay.state()) {
            case NOT_LOADED -> true;
            case NO_FILE -> false;
            case LOADED -> store == null || store.blocks(satellite);
        };
    }
    /** Registry removal fence; unlike launch/insertion, an exactly acknowledged REGISTERED does not block. */
    public boolean blocksDecommission(UUID satellite) {
        return switch (bay.state()) {
            case NOT_LOADED -> true;
            case NO_FILE -> false;
            case LOADED -> store == null || store.blocksDecommission(satellite);
        };
    }
    /** Shared registry fence, including startup and a missing/stopping server-lifetime runtime. */
    public static boolean blocksRegistry(MinecraftServer server, UUID satellite, boolean decommission) {
        if (!server.isSameThread() || server.overworld() == null) { return true; }
        return server.overworld().getCapability(EscrowRuntime.CAPABILITY).resolve().map(runtime -> {
            if (runtime.stopping()) { return true; }
            BayReceipts receipts = runtime.bayReceipts(server);
            return decommission ? receipts.blocksDecommission(satellite) : receipts.blocksInsertion(satellite);
        }).orElse(true);
    }
    /**
     * W27A-DESIGN section 8's bay part of the gate lock (R49 Q3 and Q8), on the server thread: a coordinator claim on L
     * (including a pending handoff), or a blocking or released receipt naming L in any state. While the index is
     * NOT_LOADED after startup, every PREPARED transfer record of L holds it, and every L while the transfer journal is
     * not operational. Before ServerStartingEvent it is false: no login, command or tick runs then, and the startup
     * classification supersedes it.
     */
    public boolean holds(MinecraftServer server, UUID logical) {
        return holds(logical, () -> RocketTransferSavedData.get(server));
    }
    /** The same part over a given transfer journal; unit tests drive it with real receipt files and a real journal. */
    boolean holds(UUID logical, java.util.function.Supplier<RocketTransferSavedData> journal) {
        if (!started) { return false; }
        if (session != null && session.claimed(logical)) { return true; }
        return switch (bay.state()) {
            case NOT_LOADED -> preparedTransfer(journal.get(), logical);
            case NO_FILE -> false;
            case LOADED -> store == null || store.holdsRocket(logical);
        };
    }
    private static boolean preparedTransfer(RocketTransferSavedData journal, UUID logical) {
        if (!journal.operational()) { return true; }
        for (var record : journal.entries()) {
            if (record.logicalRocketId().equals(logical)
                    && record.phase() == RocketTransferPhase.PREPARED) {
                return true;
            }
        }
        return false;
    }
    /** The bay part of the gate lock for any caller; off the server thread it fails closed. */
    public static boolean holdsRocket(MinecraftServer server, UUID logical) {
        if (!server.isSameThread()) { return true; }
        if (server.overworld() == null) { return false; }
        return server.overworld().getCapability(EscrowRuntime.CAPABILITY).resolve()
                .map(runtime -> runtime.bayReceipts(server).holds(server, logical)).orElse(false);
    }

    /** The coordinator's one session handle for this server lifetime; a second request throws. */
    public BayDeploymentSession session(MinecraftServer server) {
        EscrowTags.require(server.isSameThread(), "Bay receipts live on the server thread");
        if (session != null) { throw new IllegalStateException("The bay deployment session was already issued"); }
        session = new BayDeploymentSession(this, server);
        return session;
    }
    /** A bay's menu status from its package's satellite: the receipt, else this session's runtime rejection. */
    public static java.util.Optional<BayDeploymentSession.Status> deploymentStatus(MinecraftServer server, UUID satellite) {
        if (!server.isSameThread() || server.overworld() == null) { return java.util.Optional.empty(); }
        return server.overworld().getCapability(EscrowRuntime.CAPABILITY).resolve().flatMap(runtime -> {
            BayReceipts receipts = runtime.bayReceipts(server);
            if (receipts.session != null) { return receipts.session.status(satellite); }
            return java.util.Optional.ofNullable(receipts.store).flatMap(bound -> bound.find(satellite))
                    .map(receipt -> new BayDeploymentSession.Status(receipt.phase().name(), receipt.reason()));
        });
    }

    void commitCut(@Nullable BayReceiptCommitCut cut) { commitCut = cut; }
    /** The receipt committer's cut seam; refused outside a GameTestServer or a gated release-test server. */
    public java.util.Optional<BayReceiptCommitCut> commitCutForTests(MinecraftServer server) {
        EscrowTestEndpoints.requireEnabled(server);
        return java.util.Optional.ofNullable(commitCut);
    }

    public EscrowAdmissionAuthority.Delegate.State state() { return bay.state(); }
    @Nullable public BayReceiptStore store() { return store; }
    EscrowAdmissionAuthority.Delegate delegate() { return bay; }
    /** The BAY delegate's state at the START of the server's first tick, for the N6 startup check. */
    @Nullable public EscrowAdmissionAuthority.Delegate.State firstTickState() { return firstTick; }
    void observeTickStart() { if (firstTick == null) { firstTick = bay.state(); } }

    /**
     * W95's gated N3 fixture seam: a synthetic BAY index replaces the store's attachment, and null restores the exact
     * prior state, the bound store or NO_FILE. False, with nothing changed, while the index is NOT_LOADED (a restoration
     * cannot return there and would open admission over an unloaded receipt file, W27C1-R7), when there is nothing to
     * restore, or while the attached store holds work.
     */
    boolean substituteForTests(@Nullable EscrowAdmissionAuthority.Store synthetic) {
        if (synthetic == null ? substitute == null : bay.state() == EscrowAdmissionAuthority.Delegate.State.NOT_LOADED) { return false; }
        if (!bay.retarget(synthetic != null ? synthetic : store)) { return false; }
        substitute = synthetic;
        return true;
    }

    void tick(long now) { if (store != null) { store.tick(now); } }
    /** A paused native cut is released first, so the drain (or the crash path's join) can finish its write. */
    void stop() {
        if (commitCut != null) { commitCut.release(); }
        if (session != null) { session.close(); }
        if (store != null) { store.close(); }
    }
    void abort() {
        if (commitCut != null) { commitCut.release(); }
        if (session != null) { session.close(); }
        if (store != null) { store.abort(); }
    }
}
