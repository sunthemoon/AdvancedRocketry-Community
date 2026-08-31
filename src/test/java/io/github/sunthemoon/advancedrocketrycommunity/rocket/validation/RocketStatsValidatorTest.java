package io.github.sunthemoon.advancedrocketrycommunity.rocket.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketStats;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class RocketStatsValidatorTest {
    @Test
    void legalRocketProducesNoSyntheticIssue() {
        RocketValidationResult result = RocketStatsValidator.validate(
                new RocketStats(4, 200, 1_000, 500, 1, 1, 1, 0)
        );
        assertTrue(result.valid());
        assertEquals(RocketValidationCode.SUCCESS, result.primaryCode());
        assertEquals(List.of(), result.issues());
    }

    @Test
    void everyIndependentFailureIsReportedInStableOrder() {
        RocketValidationResult result = RocketStatsValidator.validate(
                new RocketStats(2, 100, 10, 0, 0, 0, 0, 0)
        );
        assertFalse(result.valid());
        assertEquals(
                List.of(
                        RocketValidationCode.MISSING_ENGINE,
                        RocketValidationCode.MISSING_SEAT,
                        RocketValidationCode.MISSING_GUIDANCE,
                        RocketValidationCode.INSUFFICIENT_THRUST
                ),
                result.issues().stream().map(RocketValidationIssue::code).toList()
        );
        assertEquals(Map.of("mass", "100", "thrust", "10"), result.issues().get(3).parameters());
        assertEquals(
                "validation.advancedrocketrycommunity.rocket.missing_engine",
                result.issues().get(0).translationKey()
        );
    }

    @Test
    void resultAndIssueParametersAreDefensive() {
        ArrayList<RocketValidationIssue> issues = new ArrayList<>();
        issues.add(new RocketValidationIssue(
                RocketValidationCode.MISSING_ENGINE,
                Map.of("required", "1")
        ));
        RocketValidationResult result = new RocketValidationResult(issues);
        issues.clear();

        assertEquals(1, result.issues().size());
        assertThrows(UnsupportedOperationException.class, () -> result.issues().clear());
        assertThrows(
                UnsupportedOperationException.class,
                () -> result.issues().get(0).parameters().put("required", "0")
        );
    }
}
