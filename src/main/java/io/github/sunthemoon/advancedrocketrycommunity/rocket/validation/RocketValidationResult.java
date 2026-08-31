package io.github.sunthemoon.advancedrocketrycommunity.rocket.validation;

import java.util.List;
import java.util.Objects;

public record RocketValidationResult(List<RocketValidationIssue> issues) {
    public RocketValidationResult {
        Objects.requireNonNull(issues, "issues");
        issues = List.copyOf(issues);
        if (issues.stream().anyMatch(issue -> issue.code() == RocketValidationCode.SUCCESS)) {
            throw new IllegalArgumentException("Validation issue list contains SUCCESS");
        }
    }

    public static RocketValidationResult success() {
        return new RocketValidationResult(List.of());
    }

    public boolean valid() {
        return issues.isEmpty();
    }

    public RocketValidationCode primaryCode() {
        return valid() ? RocketValidationCode.SUCCESS : issues.get(0).code();
    }
}
