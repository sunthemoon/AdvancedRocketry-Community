package io.github.sunthemoon.advancedrocketrycommunity.travel.migration;

import io.github.sunthemoon.advancedrocketrycommunity.travel.model.TravelTarget;
import java.util.Objects;
import java.util.Optional;

/** Fail-closed migration result; the caller retains the original NBT on failure. */
public record TravelTargetMigrationResult(
        TravelTargetMigrationCode code,
        Optional<TravelTarget> target
) {
    public TravelTargetMigrationResult {
        Objects.requireNonNull(code, "code");
        target = Objects.requireNonNull(target, "target");
        if ((code == TravelTargetMigrationCode.MIGRATED) != target.isPresent()) {
            throw new IllegalArgumentException("Migration status and target disagree");
        }
    }

    public static TravelTargetMigrationResult migrated(TravelTarget target) {
        return new TravelTargetMigrationResult(
                TravelTargetMigrationCode.MIGRATED,
                Optional.of(Objects.requireNonNull(target, "target"))
        );
    }

    public static TravelTargetMigrationResult blocked(TravelTargetMigrationCode code) {
        if (code == TravelTargetMigrationCode.MIGRATED) {
            throw new IllegalArgumentException("A blocked migration cannot use MIGRATED");
        }
        return new TravelTargetMigrationResult(code, Optional.empty());
    }

    public boolean migrated() {
        return code == TravelTargetMigrationCode.MIGRATED;
    }
}
