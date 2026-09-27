package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class RocketDisassemblyLanguageTest {
    @Test
    void generatedConsentTextIsPackagedWithBothLocalesAndAllArguments() throws Exception {
        String prefix = "message.advancedrocketrycommunity.rocket.";
        Map<String, Integer> placeholders = Map.ofEntries(
                Map.entry(prefix + "disassembly_fuel_warning", 2),
                Map.entry(prefix + "disassembly_discard_action", 1),
                Map.entry(prefix + "disassembly_confirmation_invalid", 0),
                Map.entry(prefix + "disassembly_fuel_discarded", 1),
                Map.entry("validation.advancedrocketrycommunity.rocket.fuel_disposal_required", 0),
                Map.entry("validation.advancedrocketrycommunity.rocket.fuel_capacity_exceeded", 0),
                Map.entry("message.advancedrocketrycommunity.fuel_loader.fuel_inserted", 0),
                Map.entry("message.advancedrocketrycommunity.fuel_loader.fuel_rejected", 0),
                Map.entry("message.advancedrocketrycommunity.fuel_loader.item_returned", 0),
                Map.entry("message.advancedrocketrycommunity.fuel_loader.repair_required", 0),
                Map.entry("status.advancedrocketrycommunity.fuel_loader.unsupported_fuel", 0),
                Map.entry("status.advancedrocketrycommunity.fuel_loader.item_ready", 0));
        try (var runtime = new ZipFile(System.getProperty("arce.runtimeJar"))) {
            for (String locale : new String[] {"en_us", "zh_cn"}) {
                String entryName = "assets/advancedrocketrycommunity_v130/lang/" + locale + ".json";
                var entry = runtime.getEntry(entryName);
                assertNotNull(entry, "Missing packaged consent locale " + locale);
                byte[] packaged = runtime.getInputStream(entry).readAllBytes();
                assertArrayEquals(Files.readAllBytes(Path.of("src/generated/v1.3/resources", entryName)), packaged);
                var json = JsonParser.parseString(new String(packaged, StandardCharsets.UTF_8)).getAsJsonObject();
                assertEquals(placeholders.keySet(), json.keySet());
                for (var expected : placeholders.entrySet()) {
                    String text = json.get(expected.getKey()).getAsString();
                    assertFalse(text.isBlank());
                    assertEquals(expected.getValue().intValue(), text.split("%s", -1).length - 1, expected.getKey());
                    assertEquals(locale.equals("zh_cn"), text.codePoints().anyMatch(c -> c >= 0x4E00 && c <= 0x9FFF));
                }
            }
        }
    }
}
