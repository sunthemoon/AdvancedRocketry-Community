package io.github.sunthemoon.advancedrocketrycommunity.atmosphere.scan;

import java.util.Objects;
import java.util.OptionalLong;

public record ScanScheduleResult(ScanScheduleStatus status, OptionalLong taskId) {
    public ScanScheduleResult {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(taskId, "taskId");
        if (status == ScanScheduleStatus.BUSY && taskId.isPresent()) {
            throw new IllegalArgumentException("A rejected schedule cannot name a task");
        }
        if (status != ScanScheduleStatus.BUSY && taskId.isEmpty()) {
            throw new IllegalArgumentException("An accepted schedule must name a task");
        }
    }
}
