package io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataPolicy;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowTestEndpoints;
import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.server.MinecraftServer;

/** R52 native cuts at the committed record's removal; inert outside the exact release-test cases. */
final class RocketLandedRemovalCut {
    private RocketLandedRemovalCut() { }

    static Optional<CheckedSavedDataPolicy> checked(MinecraftServer server) {
        String cut = System.getProperty("advancedrocketrycommunity.escrowCase", "");
        if (server == null || !Boolean.getBoolean(EscrowTestEndpoints.HOOKS)
                || !"start".equals(System.getProperty("advancedrocketrycommunity.escrowScenario"))
                || !(cut.equals("LF_M_R52_MOVED") || cut.equals("LF_M_R52_UNKNOWN"))) { return Optional.empty(); }
        EscrowTestEndpoints.requireEnabled(server);
        return Optional.of(policy(cut.endsWith("UNKNOWN"), () -> {
            AdvancedRocketryCommunity.LOGGER.info("ARCE_R52_PAUSED cut={} boundary=MOVED", cut);
            RocketJournalRemovalCut.awaitBounded(TimeUnit.SECONDS.toNanos(20));
        }));
    }

    static CheckedSavedDataPolicy policy(boolean unknown, Runnable movedPause) {
        var moved = new AtomicBoolean();
        var type = ManagedSavedDataType.ROCKET_TRANSFERS;
        return new CheckedSavedDataPolicy(type, (staged, target) -> {
            boolean removal = RocketJournalFailurePolicy.landedRemoval(CheckedSavedDataFile.readPayload(staged, type).orElseThrow(),
                    CheckedSavedDataFile.readPayload(target, type).orElse(null));
            CheckedSavedDataFile.atomicMove(staged, target);
            if (removal) { moved.set(true); }
            // CheckedJournalCheckpoint reads back only after a replacement failure. Keep this process's retries
            // unknown too, so a later empty-root write cannot acknowledge the removal before the cut is observed.
            if (unknown && moved.get()) { throw new IOException("R52 injected removal failure after replacement"); }
            if (removal) { movedPause.run(); }
        }, (file, kind) -> {
            if (moved.get() && unknown) { throw new IllegalStateException("R52 injected unreadable removal readback"); }
            return CheckedSavedDataFile.readPayload(file, kind);
        });
    }
}
