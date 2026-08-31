package io.github.sunthemoon.advancedrocketrycommunity.rocket.validation;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketStats;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;

public final class RocketStatsValidator {
    private RocketStatsValidator() {
    }

    public static RocketValidationResult validate(RocketStats stats) {
        Objects.requireNonNull(stats, "stats");
        ArrayList<RocketValidationIssue> issues = new ArrayList<>();
        if (stats.engineCount() == 0) {
            issues.add(new RocketValidationIssue(RocketValidationCode.MISSING_ENGINE, Map.of()));
        }
        if (stats.seatCount() == 0) {
            issues.add(new RocketValidationIssue(RocketValidationCode.MISSING_SEAT, Map.of()));
        }
        if (stats.guidanceCount() == 0) {
            issues.add(new RocketValidationIssue(RocketValidationCode.MISSING_GUIDANCE, Map.of()));
        }
        if (!stats.hasSufficientThrust()) {
            issues.add(new RocketValidationIssue(
                    RocketValidationCode.INSUFFICIENT_THRUST,
                    Map.of(
                            "mass", Long.toString(stats.mass()),
                            "thrust", Long.toString(stats.thrust())
                    )
            ));
        }
        return new RocketValidationResult(issues);
    }
}
