package io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataPolicy;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowTestEndpoints;
import java.io.IOException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;

/** Empty dedicated-server fault injection, disabled unless both release properties are explicit. */
final class RocketJournalFailurePolicy {
    static final String PROPERTY = "advancedrocketrycommunity.rocketJournalFailure";
    /** W85 S2: the landed-disassembly probe family; only its writer-side boundary K1 is handled here. */
    static final String LANDED_PROBE = "advancedrocketrycommunity.landedDisassemblyProbe";
    private static final ManagedSavedDataType TYPE = ManagedSavedDataType.ROCKET_TRANSFERS;
    /** LANDED_REMOVAL refuses the first landed removal once per process, so that its retry succeeds (W85 K9). */
    private static final java.util.concurrent.atomic.AtomicBoolean LANDED_REMOVAL_REFUSED =
            new java.util.concurrent.atomic.AtomicBoolean();
    private RocketJournalFailurePolicy() { }

    static CheckedSavedDataPolicy checked(MinecraftServer server) {
        if (server != null && !io.github.sunthemoon.advancedrocketrycommunity.rocket.rejoin.RocketReleaseNativeCuts.selection().isEmpty()) {
            return io.github.sunthemoon.advancedrocketrycommunity.rocket.rejoin.RocketReleaseNativeCuts.policy(server, TYPE);
        }
        // W44 section 6.1: the world-first cut modes extend this gated policy; production never sets them.
        var cut = io.github.sunthemoon.advancedrocketrycommunity.progression.classic.firstevent.FirstEventCutSeams
                .journalPolicy(server);
        if (cut.isPresent()) {
            return cut.orElseThrow();
        }
        var removalCut = RocketJournalRemovalCut.checked(server);
        if (removalCut.isPresent()) { return removalCut.orElseThrow(); }
        var landedCut = RocketLandedRemovalCut.checked(server);
        if (landedCut.isPresent()) { return landedCut.orElseThrow(); }
        String mode = System.getProperty(PROPERTY, "");
        if (server != null && Boolean.getBoolean(EscrowTestEndpoints.HOOKS)
                && System.getProperty("advancedrocketrycommunity.escrowCase", "").startsWith("SR_")) {
            EscrowTestEndpoints.requireEnabled(server);
            var runtime = io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowRuntime.of(server);
            return new CheckedSavedDataPolicy(TYPE, (staged, target) -> {
                var previous = CheckedSavedDataFile.readPayload(target, TYPE).orElse(null);
                var candidate = CheckedSavedDataFile.readPayload(staged, TYPE).orElseThrow();
                if (previous != null && !previous.getList("transfers", 10).isEmpty() && candidate.getList("transfers", 10).isEmpty()) {
                    runtime.checkpoint("O3J_BEFORE");
                }
                CheckedSavedDataFile.atomicMove(staged, target);
            });
        }
        if (server != null && Boolean.getBoolean(EscrowTestEndpoints.HOOKS)
                && "LANDED_DISASSEMBLY:K1".equals(System.getProperty(LANDED_PROBE))) {
            EscrowTestEndpoints.requireEnabled(server);
            return new CheckedSavedDataPolicy(TYPE, (staged, target) -> {
                if (landedRemoval(CheckedSavedDataFile.readPayload(staged, TYPE).orElseThrow(),
                        CheckedSavedDataFile.readPayload(target, TYPE).orElse(null))) {
                    pauseBeforeLandedRemoval();
                }
                CheckedSavedDataFile.atomicMove(staged, target);
            });
        }
        if (server == null || mode.isEmpty() || !Boolean.getBoolean(EscrowTestEndpoints.HOOKS)) {
            return CheckedSavedDataPolicy.atomic(TYPE);
        }
        EscrowTestEndpoints.requireEnabled(server);
        if (mode.equals("OBSERVE")) { return CheckedSavedDataPolicy.atomic(TYPE); }
        if (mode.equals("HOLD")) {
            var hold = RocketTransferSavedData.get(server).nativeHold(server);
            return new CheckedSavedDataPolicy(TYPE, (staged, target) -> {
                hold.beforeReplace(CheckedSavedDataFile.readPayload(staged, TYPE).orElseThrow(),
                        CheckedSavedDataFile.readPayload(target, TYPE).orElse(null));
                CheckedSavedDataFile.atomicMove(staged, target);
            });
        }
        if (!mode.equals("ADMISSION") && !mode.equals("ADVANCEMENT") && !mode.equals("LANDED_REMOVAL")) {
            throw new IllegalArgumentException("Unknown journal release-test failure mode");
        }
        return new CheckedSavedDataPolicy(TYPE, (staged, target) -> {
            CompoundTag candidate = CheckedSavedDataFile.readPayload(staged, TYPE).orElseThrow();
            CompoundTag previous = CheckedSavedDataFile.readPayload(target, TYPE).orElse(null);
            if (reject(mode, candidate, previous)
                    && (!mode.equals("LANDED_REMOVAL") || LANDED_REMOVAL_REFUSED.compareAndSet(false, true))) {
                AdvancedRocketryCommunity.LOGGER.info("ARCE_RELEASE_JOURNAL_COMMIT_REFUSED mode={}", mode);
                throw new IOException("Release-test journal committer failure before replacement");
            }
            CheckedSavedDataFile.atomicMove(staged, target);
        });
    }

    static boolean reject(String mode, CompoundTag candidate, CompoundTag previous) {
        var records = candidate.getList("transfers", Tag.TAG_COMPOUND);
        if (mode.equals("ADMISSION")) { return !records.isEmpty(); }
        if (mode.equals("LANDED_REMOVAL")) { return landedRemoval(candidate, previous); }
        if (!mode.equals("ADVANCEMENT")) { throw new IllegalArgumentException("Unknown failure mode"); }
        // Permit only PREPARED authority. Refuse recovery removal as well, so both restarts exercise
        // the failed committer rather than silently deleting the retained old journal generation.
        return records.stream().anyMatch(raw -> !((CompoundTag) raw).getString("phase").equals("PREPARED"))
                || records.isEmpty() && previous != null && !previous.getList("transfers", Tag.TAG_COMPOUND).isEmpty();
    }

    /** The candidate drops a COMMITTED record that the file on disk still holds: a landed reservation's removal. */
    static boolean landedRemoval(CompoundTag candidate, CompoundTag previous) {
        if (previous == null) { return false; }
        var kept = new java.util.HashSet<java.util.UUID>();
        for (Tag raw : candidate.getList("transfers", Tag.TAG_COMPOUND)) {
            if (((CompoundTag) raw).hasUUID("transfer_id")) { kept.add(((CompoundTag) raw).getUUID("transfer_id")); }
        }
        return previous.getList("transfers", Tag.TAG_COMPOUND).stream().map(CompoundTag.class::cast)
                .anyMatch(record -> record.getString("phase").equals("COMMITTED") && record.hasUUID("transfer_id")
                        && !kept.contains(record.getUUID("transfer_id")));
    }

    /** K1: the removal is staged but not moved. The harness kills the process during the fixed wait. */
    private static void pauseBeforeLandedRemoval() throws IOException {
        AdvancedRocketryCommunity.LOGGER.info("ARCE_LANDED_DISASSEMBLY_PAUSED boundary=K1 stage=before_removal_move");
        try {
            java.util.concurrent.TimeUnit.SECONDS.sleep(20L);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
        AdvancedRocketryCommunity.LOGGER.warn("ARCE_LANDED_DISASSEMBLY_PROBE_EXPIRED boundary=K1");
        throw new IOException("External landed disassembly kill did not arrive within the fixed wait");
    }
}
