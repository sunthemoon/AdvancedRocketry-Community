package io.github.sunthemoon.advancedrocketrycommunity.station.persistence;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataPolicy;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;

/**
 * The one station writer and outcome resolver (W44 sections 1.2, 1.3 and 10), extracted from the registry adapter:
 * stage, force, readback and atomic move through the policy's committer, then the data-directory force inside the same
 * try block. A failure after the move was attempted reads the file back: the exact candidate is COMMITTED (R1: the
 * directory force is not retried then), an unreadable file is OUTCOME_UNKNOWN, anything else is a failed write.
 */
final class StationCheckedWriter {
    private static final ManagedSavedDataType TYPE = ManagedSavedDataType.STATIONS;

    /** The resolved outcome; the registry quarantines on UNKNOWN and reasserts RAM after a failed replacement. */
    enum Outcome { COMMITTED, WRITE_FAILED, WRITE_FAILED_REASSERT, OUTCOME_UNKNOWN }

    /** The first-write re-check of a proven-absent root; it throws before the move is attempted. */
    @FunctionalInterface
    interface Guard {
        Guard NONE = target -> { };

        void check(Path target) throws IOException;
    }

    /** Per-operation diagnostics: named operations log every failure, the automatic acknowledgement once per streak. */
    interface Diagnostics {
        void beforeReplace(Throwable failure);

        void unknown(Path file, Throwable failure);

        void replacedDespiteError(Throwable failure);

        void replaceFailed(boolean filePresent, Throwable failure);
    }

    private StationCheckedWriter() {
    }

    static Outcome write(Path file, CompoundTag candidate, CheckedSavedDataPolicy policy,
                         CheckedSavedDataPolicy.DirectoryForce force, Guard guard, Diagnostics log) {
        Objects.requireNonNull(file, "file");
        boolean[] attempted = {false};
        try {
            CheckedSavedDataFile.replace(file, TYPE, candidate::copy, (staged, target) -> {
                guard.check(target);
                attempted[0] = true;
                policy.committer().commit(staged, target);
            });
            force.force(file.toAbsolutePath().normalize().getParent());
        } catch (IOException | RuntimeException failure) {
            if (!attempted[0]) {
                log.beforeReplace(failure);
                return Outcome.WRITE_FAILED;
            }
            return resolve(file, candidate, policy, failure, log);
        }
        return Outcome.COMMITTED;
    }

    private static Outcome resolve(Path file, CompoundTag candidate, CheckedSavedDataPolicy policy, Exception failure,
                                   Diagnostics log) {
        Optional<CompoundTag> onDisk;
        try {
            onDisk = policy.read(file);
        } catch (RuntimeException unreadable) {
            failure.addSuppressed(unreadable);
            log.unknown(file, failure);
            return Outcome.OUTCOME_UNKNOWN;
        }
        if (onDisk.filter(candidate::equals).isPresent()) {
            log.replacedDespiteError(failure);
            return Outcome.COMMITTED;
        }
        // Not the candidate; ordinary saves rewrite the acknowledged authority in case the file is stale.
        log.replaceFailed(onDisk.isPresent(), failure);
        return Outcome.WRITE_FAILED_REASSERT;
    }

    /** Today's diagnostics of a named station operation: one ERROR with its stack trace per failure. */
    static Diagnostics named(UUID stationId) {
        return new Diagnostics() {
            @Override public void beforeReplace(Throwable failure) {
                AdvancedRocketryCommunity.LOGGER.error(
                        "ARCE_STATION_UPDATE_WRITE_FAILED station={} stage=before_replace", stationId, failure);
            }

            @Override public void unknown(Path file, Throwable failure) {
                AdvancedRocketryCommunity.LOGGER.error(
                        "ARCE_STATION_UPDATE_OUTCOME_UNKNOWN station={} file={}; updates disabled until restart",
                        stationId, file, failure);
            }

            @Override public void replacedDespiteError(Throwable failure) {
                AdvancedRocketryCommunity.LOGGER.warn(
                        "ARCE_STATION_UPDATE_REPLACED_DESPITE_ERROR station={}", stationId, failure);
            }

            @Override public void replaceFailed(boolean filePresent, Throwable failure) {
                AdvancedRocketryCommunity.LOGGER.error(
                        "ARCE_STATION_UPDATE_WRITE_FAILED station={} stage=replace file_present={}",
                        stationId, filePresent, failure);
            }
        };
    }
}
