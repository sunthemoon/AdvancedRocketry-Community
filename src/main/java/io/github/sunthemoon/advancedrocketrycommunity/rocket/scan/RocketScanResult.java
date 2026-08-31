package io.github.sunthemoon.advancedrocketrycommunity.rocket.scan;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketStats;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.validation.RocketValidationIssue;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class RocketScanResult {
    public enum Status {
        RUNNING,
        SUCCESS,
        FAILED
    }

    private final Status status;
    private final RocketStructureSnapshot snapshot;
    private final RocketStats stats;
    private final List<RocketValidationIssue> issues;
    private final int inspectionsThisStep;
    private final int totalInspections;
    private final int capturedBlocks;
    private final int queuedPositions;

    private RocketScanResult(
            Status status,
            RocketStructureSnapshot snapshot,
            RocketStats stats,
            List<RocketValidationIssue> issues,
            int inspectionsThisStep,
            int totalInspections,
            int capturedBlocks,
            int queuedPositions
    ) {
        this.status = Objects.requireNonNull(status, "status");
        this.snapshot = snapshot;
        this.stats = stats;
        this.issues = List.copyOf(Objects.requireNonNull(issues, "issues"));
        this.inspectionsThisStep = inspectionsThisStep;
        this.totalInspections = totalInspections;
        this.capturedBlocks = capturedBlocks;
        this.queuedPositions = queuedPositions;
        if (inspectionsThisStep < 0 || totalInspections < 0
                || capturedBlocks < 0 || queuedPositions < 0) {
            throw new IllegalArgumentException("Rocket scan counters must not be negative");
        }
        if (status == Status.SUCCESS && (snapshot == null || stats == null || !issues.isEmpty())) {
            throw new IllegalArgumentException("Successful scan result is incomplete");
        }
        if (status == Status.FAILED && issues.isEmpty()) {
            throw new IllegalArgumentException("Failed scan result requires a diagnostic");
        }
        if (status == Status.RUNNING && (snapshot != null || stats != null || !issues.isEmpty())) {
            throw new IllegalArgumentException("Running scan result contains terminal data");
        }
    }

    static RocketScanResult running(
            int inspectionsThisStep,
            int totalInspections,
            int capturedBlocks,
            int queuedPositions
    ) {
        return new RocketScanResult(
                Status.RUNNING,
                null,
                null,
                List.of(),
                inspectionsThisStep,
                totalInspections,
                capturedBlocks,
                queuedPositions
        );
    }

    static RocketScanResult success(
            RocketStructureSnapshot snapshot,
            int inspectionsThisStep,
            int totalInspections
    ) {
        return new RocketScanResult(
                Status.SUCCESS,
                snapshot,
                snapshot.stats(),
                List.of(),
                inspectionsThisStep,
                totalInspections,
                snapshot.blocks().size(),
                0
        );
    }

    static RocketScanResult failed(
            List<RocketValidationIssue> issues,
            RocketStats stats,
            int inspectionsThisStep,
            int totalInspections,
            int capturedBlocks,
            int queuedPositions
    ) {
        return new RocketScanResult(
                Status.FAILED,
                null,
                stats,
                issues,
                inspectionsThisStep,
                totalInspections,
                capturedBlocks,
                queuedPositions
        );
    }

    public Status status() {
        return status;
    }

    public Optional<RocketStructureSnapshot> snapshot() {
        return Optional.ofNullable(snapshot);
    }

    public Optional<RocketStats> stats() {
        return Optional.ofNullable(stats);
    }

    public List<RocketValidationIssue> issues() {
        return issues;
    }

    public int inspectionsThisStep() {
        return inspectionsThisStep;
    }

    public int totalInspections() {
        return totalInspections;
    }

    public int capturedBlocks() {
        return capturedBlocks;
    }

    public int queuedPositions() {
        return queuedPositions;
    }
}
