package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.validation.RocketValidationCode;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.validation.RocketValidationIssue;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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

    @Test void activeLanguageFilesProvideBothEntityNames() throws IOException {
        Path directory = Path.of("src/generated/v1.8/resources/assets/advancedrocketrycommunity_v180/lang");
        for (var entry : Map.of("en_us", "Assembled Rocket", "zh_cn", "组装火箭").entrySet()) {
            var json = JsonParser.parseString(Files.readString(directory.resolve(entry.getKey() + ".json"))).getAsJsonObject();
            String key = "entity.advancedrocketrycommunity.rocket";
            assertTrue(json.has(key), entry.getKey() + " lacks the entity name");
            assertEquals(entry.getValue(), json.get(key).getAsString());
        }
    }

    @Test void serverDataPackContainsEveryBoundedReasonAndTheEntityName() throws IOException {
        Path root = Path.of("src/generated");
        var server = JsonParser.parseString(Files.readString(root.resolve(
                "v1.8/resources/data/advancedrocketrycommunity_v180/lang/en_us.json"))).getAsJsonObject();
        var legacy = JsonParser.parseString(Files.readString(root.resolve(
                "v0.5/resources/assets/advancedrocketrycommunity_v050/lang/en_us.json"))).getAsJsonObject();
        var active = JsonParser.parseString(Files.readString(root.resolve(
                "v1.8/resources/assets/advancedrocketrycommunity_v180/lang/en_us.json"))).getAsJsonObject();
        assertEquals("Assembled Rocket", server.get("entity.advancedrocketrycommunity.rocket").getAsString());
        for (var code : RocketValidationCode.values()) {
            String key = code.translationKey();
            String expected = (active.has(key) ? active : legacy).get(key).getAsString();
            assertEquals(expected, server.get(key).getAsString(), key);
            assertTrue(expected.length() <= 256 && !expected.isBlank() && !expected.equals(key), key);
        }
    }
}
