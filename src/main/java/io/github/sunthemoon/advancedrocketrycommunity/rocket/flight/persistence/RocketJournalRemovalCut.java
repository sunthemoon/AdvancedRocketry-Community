package io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataPolicy;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowTestEndpoints;
import java.util.HashSet;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;

/** Passive writer-thread cut after an ordinary PREPARED removal, before completion publication. */
final class RocketJournalRemovalCut {
    static final String MODE = "N2_REMOVAL_MOVED";
    private static final ManagedSavedDataType TYPE = ManagedSavedDataType.ROCKET_TRANSFERS;
    private RocketJournalRemovalCut() { }

    static Optional<CheckedSavedDataPolicy> checked(MinecraftServer server) {
        if (server == null || !Boolean.getBoolean(EscrowTestEndpoints.HOOKS)
                || !MODE.equals(System.getProperty(RocketJournalFailurePolicy.PROPERTY))) { return Optional.empty(); }
        EscrowTestEndpoints.requireEnabled(server);
        return Optional.of(policy(() -> {
            AdvancedRocketryCommunity.LOGGER.info("ARCE_N2_REMOVAL_PAUSED boundary=after_atomic_move");
            awaitBounded(TimeUnit.SECONDS.toNanos(20));
            // Expiry does not change the write, its outcome, or its normal acknowledgement order.
            AdvancedRocketryCommunity.LOGGER.info("ARCE_N2_REMOVAL_PAUSE_EXPIRED");
        }));
    }

    static CheckedSavedDataPolicy policy(Runnable pause) {
        return new CheckedSavedDataPolicy(TYPE, (staged, target) -> {
            boolean selected = ordinaryRemoval(CheckedSavedDataFile.readPayload(staged, TYPE).orElseThrow(),
                    CheckedSavedDataFile.readPayload(target, TYPE).orElse(null));
            CheckedSavedDataFile.atomicMove(staged, target);
            if (selected) { pause.run(); }
        });
    }

    static boolean ordinaryRemoval(CompoundTag candidate, CompoundTag previous) {
        if (previous == null) { return false; }
        var kept = new HashSet<UUID>();
        for (var raw : candidate.getList("transfers", 10)) {
            var record = (CompoundTag) raw;
            if (record.hasUUID("transfer_id")) { kept.add(record.getUUID("transfer_id")); }
        }
        return previous.getList("transfers", 10).stream().map(CompoundTag.class::cast)
                .anyMatch(record -> record.getString("phase").equals("PREPARED") && !record.contains("native_transfer")
                        && record.hasUUID("transfer_id") && !kept.contains(record.getUUID("transfer_id")));
    }

    static void awaitBounded(long nanos) {
        if (nanos <= 0 || nanos > TimeUnit.SECONDS.toNanos(20)) { throw new IllegalArgumentException("Invalid pause bound"); }
        long deadline = System.nanoTime() + nanos;
        long remaining;
        while ((remaining = deadline - System.nanoTime()) > 0) {
            try { TimeUnit.NANOSECONDS.sleep(remaining); }
            catch (InterruptedException interrupted) {
                // Like RocketJournalHold, this pause is not cancellation of the subsequent force.
            }
        }
    }
}
