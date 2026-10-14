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
    private static final ManagedSavedDataType TYPE = ManagedSavedDataType.ROCKET_TRANSFERS;
    private RocketJournalFailurePolicy() { }

    static CheckedSavedDataPolicy checked(MinecraftServer server) {
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
        if (server == null || mode.isEmpty() || !Boolean.getBoolean(EscrowTestEndpoints.HOOKS)) {
            return CheckedSavedDataPolicy.atomic(TYPE);
        }
        EscrowTestEndpoints.requireEnabled(server);
        if (!mode.equals("ADMISSION") && !mode.equals("ADVANCEMENT")) {
            throw new IllegalArgumentException("Unknown journal release-test failure mode");
        }
        return new CheckedSavedDataPolicy(TYPE, (staged, target) -> {
            CompoundTag candidate = CheckedSavedDataFile.readPayload(staged, TYPE).orElseThrow();
            CompoundTag previous = CheckedSavedDataFile.readPayload(target, TYPE).orElse(null);
            if (reject(mode, candidate, previous)) {
                AdvancedRocketryCommunity.LOGGER.info("ARCE_RELEASE_JOURNAL_COMMIT_REFUSED mode={}", mode);
                throw new IOException("Release-test journal committer failure before replacement");
            }
            CheckedSavedDataFile.atomicMove(staged, target);
        });
    }

    static boolean reject(String mode, CompoundTag candidate, CompoundTag previous) {
        var records = candidate.getList("transfers", Tag.TAG_COMPOUND);
        if (mode.equals("ADMISSION")) { return !records.isEmpty(); }
        if (!mode.equals("ADVANCEMENT")) { throw new IllegalArgumentException("Unknown failure mode"); }
        // Permit only PREPARED authority. Refuse recovery removal as well, so both restarts exercise
        // the failed committer rather than silently deleting the retained old journal generation.
        return records.stream().anyMatch(raw -> !((CompoundTag) raw).getString("phase").equals("PREPARED"))
                || records.isEmpty() && previous != null && !previous.getList("transfers", Tag.TAG_COMPOUND).isEmpty();
    }
}
