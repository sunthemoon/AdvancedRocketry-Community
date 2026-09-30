package io.github.sunthemoon.advancedrocketrycommunity.satellite.mission;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** ADR-051 section 7: every row of the accepted reconciliation vectors, and nothing outside them. */
final class DeliveryReconciliationTest {
    private static final Path EXAMPLES = Path.of("docs/work/v1.6.0-preparation/examples.json");

    @Test
    void everyReconciliationRowMatches() throws Exception {
        JsonObject examples = JsonParser.parseString(Files.readString(EXAMPLES)).getAsJsonObject();
        Set<String> covered = new HashSet<>();
        for (JsonElement element : examples.getAsJsonArray("reconciliation")) {
            JsonObject row = element.getAsJsonObject();
            var registry = DeliveryReconciliation.RegistryView.valueOf(row.get("registry").getAsString());
            var receipt = DeliveryReconciliation.ReceiptView.valueOf(row.get("receipt").getAsString());
            assertEquals(DeliveryReconciliation.Action.valueOf(row.get("action").getAsString()),
                    DeliveryReconciliation.decide(registry, receipt), registry + "/" + receipt);
            covered.add(registry + "/" + receipt);
        }
        assertEquals(36, covered.size());
        for (var registry : EnumSet.allOf(DeliveryReconciliation.RegistryView.class)) {
            for (var receipt : EnumSet.allOf(DeliveryReconciliation.ReceiptView.class)) {
                assertEquals(true, covered.contains(registry + "/" + receipt), "row not in the vectors: " + registry);
            }
        }
    }
}
