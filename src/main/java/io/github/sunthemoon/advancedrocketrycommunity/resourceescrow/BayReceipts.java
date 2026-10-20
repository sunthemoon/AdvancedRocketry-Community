package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import com.mojang.logging.LogUtils;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
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
    void stop() { if (store != null) { store.close(); } }
    void abort() { if (store != null) { store.abort(); } }
}
