package io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence;

import com.mojang.logging.LogUtils;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataPolicy;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlock;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlockState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketStats;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketRegion;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketTransactionPhase;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketTransactionRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketTransactionType;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;

/**
 * W129a native cuts (design section 6.4): release-test seams of the transaction writer, inert in production. They are
 * armed only by {@code arce rocket release-test transaction-writer} on an empty dedicated server that runs with the
 * release-test property. The IO thread reads only this object's volatile cut and latch, never the journal.
 */
public final class RocketTransactionWriterCuts {
    static final String PROPERTY = "advancedrocketrycommunity.releaseTestHooks";
    private static final Logger LOGGER = LogUtils.getLogger();
    // A dimension that never loads, so restart recovery never touches these records and no chunk is loaded.
    private static final ResourceLocation ABSENT = ModIdentity.id("w129a_absent");
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000129a01");

    enum Point { CAPTURE, STAGED, MOVED }
    enum Mode { PAUSE, STALL, FAIL, UNKNOWN }
    private record Cut(Point point, Mode mode, CountDownLatch release) { }

    private volatile Cut cut;
    private final Map<CheckedSavedDataPolicy, CheckedSavedDataPolicy> wrapped = new ConcurrentHashMap<>();
    // Owner thread only.
    private boolean mutateAtStopping, crashArmed, releaseOnCrash;

    static boolean enabled(MinecraftServer server) {
        return server != null && Boolean.getBoolean(PROPERTY) && server.isDedicatedServer() && server.isSameThread()
                && server.getPlayerList() != null && server.getPlayerList().getPlayerCount() == 0;
    }

    /**
     * The v1.0 transaction probe pauses after {@code saveEverything}, which now only requests the journal's write; it
     * awaits that write here before the external kill. Refused outside an empty release-test dedicated server.
     */
    public static void awaitAcknowledged(MinecraftServer server) {
        if (!enabled(server)) {
            throw new IllegalStateException("The release-test journal await needs an empty release-test dedicated server");
        }
        var journal = RocketTransactionSavedData.get(server);
        journal.awaitWriter();
        if (!journal.acknowledgedCurrent()) {
            throw new IllegalStateException("The transaction journal was not acknowledged: " + journal.writeOutcome());
        }
    }

    void arm(Point point, Mode mode) {
        if (mode == Mode.FAIL && point != Point.STAGED || mode == Mode.UNKNOWN && point != Point.MOVED) {
            throw new IllegalArgumentException("Unsupported cut " + point + " " + mode);
        }
        cut = new Cut(point, mode, new CountDownLatch(1));
        LOGGER.info("ARCE_RELEASE_TXW_ARMED point={} mode={}", point, mode);
    }

    void release() {
        var current = cut;
        if (current != null) { current.release().countDown(); }
    }

    void mutateAtStopping() { mutateAtStopping = true; }

    void crashAtNextTick(boolean release) { crashArmed = true; releaseOnCrash = release; }

    /** Carried by the capture to the IO thread: the CAPTURE cut runs before anything is encoded or staged. */
    Runnable beforeEncode(long sequence) {
        var current = cut;
        return current != null && current.point() == Point.CAPTURE ? () -> hold(current, sequence) : null;
    }

    /** STAGED runs after the staging file is forced and read back; MOVED runs after the atomic move. */
    CheckedSavedDataPolicy policy(CheckedSavedDataPolicy base) {
        return wrapped.computeIfAbsent(base, policy -> new CheckedSavedDataPolicy(policy.type(), (staged, target) -> {
            at(Point.STAGED);
            policy.committer().commit(staged, target);
            at(Point.MOVED);
        }, (file, type) -> {
            var current = cut;
            if (current != null && current.mode() == Mode.UNKNOWN) {
                throw new IllegalStateException("W129a injected unreadable readback");
            }
            return policy.read(file);
        }));
    }

    /** {@code ServerStoppingEvent}, normal path only, after the latch: a logout-time mutation, then the release. */
    void onStopping(RocketTransactionSavedData journal) {
        LOGGER.info("ARCE_RELEASE_TXW_STOPPING_EVENT sequence={}", journal.mutationSequence());
        if (mutateAtStopping) { mutateAtStopping = false; add(journal); release(); }
    }

    /** Tick START: an armed crash mutates, optionally releases a paused attempt, then throws out of the tick. */
    void onTickStart(RocketTransactionSavedData journal) {
        if (!crashArmed) { return; }
        crashArmed = false;
        add(journal);
        if (releaseOnCrash) { release(); }
        LOGGER.info("ARCE_RELEASE_TXW_CRASH sequence={}", journal.mutationSequence());
        throw new IllegalStateException("W129a injected tick exception");
    }

    /** One inert one-block assembly record in the absent dimension. */
    void add(RocketTransactionSavedData journal) {
        UUID id = UUID.randomUUID();
        var origin = new RocketPosition(1_000_000 + (int) (id.getMostSignificantBits() & 0xFFFF) * 64, 64, 1_000_000);
        var snapshot = RocketStructureSnapshot.create(id, ABSENT, origin, List.of(new RocketBlock(new RocketPosition(0, 0, 0),
                new RocketBlockState(new ResourceLocation("minecraft:iron_block"), Map.of()))), List.of(),
                new RocketStats(1, 10, 0, 0, 0, 0, 0, 0), 0);
        journal.journalFor(snapshot, OWNER).write(new RocketTransactionRecord(UUID.randomUUID(), RocketTransactionType.ASSEMBLY,
                RocketTransactionPhase.SNAPSHOT_VALIDATED, snapshot.snapshotId(), snapshot.contentHash(),
                RocketRegion.fromSnapshot(snapshot), 0, null));
        LOGGER.info("ARCE_RELEASE_TXW_MUTATED sequence={} records={}", journal.mutationSequence(), journal.entries().size());
    }

    private void at(Point point) throws IOException {
        var current = cut;
        if (current == null || current.point() != point) { return; }
        switch (current.mode()) {
            case PAUSE, STALL -> hold(current, -1);
            case FAIL, UNKNOWN -> {
                LOGGER.info("ARCE_RELEASE_TXW_INJECTED point={} mode={}", point, current.mode());
                throw new IOException("W129a injected " + (current.mode() == Mode.FAIL ? "definite failure" : "ambiguous replacement"));
            }
        }
    }

    private void hold(Cut current, long sequence) {
        LOGGER.info("ARCE_RELEASE_TXW_PAUSED point={} mode={} sequence={}", current.point(), current.mode(),
                sequence < 0 ? "staged" : String.valueOf(sequence));
        // STALL is never released: only the watchdog or a kill ends the process (R7, interrupted teardown).
        CountDownLatch latch = current.mode() == Mode.PAUSE ? current.release() : new CountDownLatch(1);
        boolean interrupted = false;
        while (true) {
            try { latch.await(); break; } catch (InterruptedException again) { interrupted = true; }
        }
        if (interrupted) { Thread.currentThread().interrupt(); }
        if (cut == current) { cut = null; } // one-shot: the next attempt passes
        LOGGER.info("ARCE_RELEASE_TXW_RELEASED point={}", current.point());
    }
}
