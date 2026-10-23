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
        String replacement = System.getProperty("advancedrocketrycommunity.ordinaryReplacementCut", "");
        if (server != null && Boolean.getBoolean(EscrowTestEndpoints.HOOKS)
                && (replacement.equals("MOVED") || replacement.equals("UNKNOWN"))) {
            EscrowTestEndpoints.requireEnabled(server);
            return Optional.of(policy(replacement.equals("UNKNOWN"), () -> {
                AdvancedRocketryCommunity.LOGGER.info("ARCE_I7_PAUSED boundary=MOVED");
                RocketJournalRemovalCut.awaitBounded(TimeUnit.SECONDS.toNanos(20));
            }, RocketLandedRemovalCut::replacement));
        }
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
        return policy(unknown, movedPause, RocketJournalFailurePolicy::landedRemoval);
    }

    static CheckedSavedDataPolicy policy(boolean unknown, Runnable movedPause,
            java.util.function.BiPredicate<net.minecraft.nbt.CompoundTag, net.minecraft.nbt.CompoundTag> selected) {
        var moved = new AtomicBoolean();
        var type = ManagedSavedDataType.ROCKET_TRANSFERS;
        return new CheckedSavedDataPolicy(type, (staged, target) -> {
            boolean removal = selected.test(CheckedSavedDataFile.readPayload(staged, type).orElseThrow(),
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

    /** The existing test writer seam selects only one ordinary COMMITTED-to-PREPARED handoff of the same L and D UUID. */
    static boolean replacement(net.minecraft.nbt.CompoundTag candidate, net.minecraft.nbt.CompoundTag previous) {
        if (previous == null) { return false; }
        for (var oldTag : previous.getList("transfers", 10)) {
            var old = (net.minecraft.nbt.CompoundTag) oldTag;
            if (!old.getString("phase").equals("COMMITTED") || old.contains("native_transfer")
                    || !old.hasUUID("logical_rocket_id") || !old.hasUUID("destination_entity_id") || !old.hasUUID("transfer_id")) { continue; }
            for (var nextTag : candidate.getList("transfers", 10)) {
                var next = (net.minecraft.nbt.CompoundTag) nextTag;
                if (next.getString("phase").equals("PREPARED") && !next.contains("native_transfer")
                        && next.hasUUID("transfer_id") && !old.getUUID("transfer_id").equals(next.getUUID("transfer_id"))
                        && next.hasUUID("logical_rocket_id") && old.getUUID("logical_rocket_id").equals(next.getUUID("logical_rocket_id"))
                        && next.hasUUID("source_entity_id") && old.getUUID("destination_entity_id").equals(next.getUUID("source_entity_id"))) { return true; }
            }
        }
        return false;
    }
}
