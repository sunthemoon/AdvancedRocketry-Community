package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.validation.RocketValidationCode;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.validation.RocketValidationIssue;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.Map;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class RocketIssueFeedbackTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void failureReportContainsOnlyTheTranslatedReason() {
        for (var code : RocketValidationCode.values()) {
            if (code == RocketValidationCode.SUCCESS) { continue; }
            var issue = new RocketValidationIssue(code, new RocketPosition(1, 2, 3),
                    Map.of("coordinate_space", "absolute", "detail", "internal diagnostic"));
            assertEquals(Component.translatable(code.translationKey()).getString(), RocketInteraction.issueDetail(issue));
        }
    }

    @Test void emptyParametersDoNotLeakTheEnumOrCoordinatesEither() {
        var issue = new RocketValidationIssue(RocketValidationCode.TARGET_OCCUPIED, Map.of());
        assertEquals(Component.translatable(issue.translationKey()).getString(), RocketInteraction.issueDetail(issue));
    }

    @Test void operatorDiagnosticRetainsCodePositionAndMapWithABoundedSingleLine() {
        var issue = new RocketValidationIssue(RocketValidationCode.EXTRACTION_FAILED, new RocketPosition(1, 2, 3),
                Map.of("coordinate_space", "absolute", "detail", "first\nsecond\rthird\tend\u2028last"));
        String detail = RocketInteraction.diagnosticDetail(issue);
        assertTrue(detail.startsWith("EXTRACTION_FAILED at 1,2,3 coordinate_space=absolute detail="));
        assertTrue(detail.endsWith("first second third end last"));
        assertFalse(detail.contains("\n"));
        var large = new RocketValidationIssue(RocketValidationCode.EXTRACTION_FAILED,
                Map.of("detail", "x".repeat(20_000)));
        assertEquals(1_024, RocketInteraction.diagnosticDetail(large).length());
    }
}
