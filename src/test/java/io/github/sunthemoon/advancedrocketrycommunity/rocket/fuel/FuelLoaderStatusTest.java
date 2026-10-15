package io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FuelLoaderStatusTest {
    /** The generated language root of each version that adds loader status text. */
    private static final Map<String, String> ROOTS = Map.of(
            "v0.6", "advancedrocketrycommunity_v060", "v1.3", "advancedrocketrycommunity_v130",
            "v1.8", "advancedrocketrycommunity_v180");

    /** W72: the existing members keep their ordinals, and the recovery lock is appended. */
    @Test void existingMembersKeepTheirOrderAndTheRecoveryLockIsLast() {
        assertEquals(List.of("UNCLAIMED", "IDLE", "WAITING_FOR_ROCKET", "TRANSFERRING", "OUTPUT_READY",
                        "UNSUPPORTED_FUEL", "UNSUPPORTED_DATA", "INVALID_DATA", "RECOVERY_PENDING"),
                Arrays.stream(FuelLoaderStatus.values()).map(Enum::name).toList());
        assertEquals("recovery_pending", FuelLoaderStatus.RECOVERY_PENDING.diagnosticKey());
    }

    /** Every status the block displays has text in both locales, in exactly one root; v0.6 never gains a key. */
    @Test void everyDisplayedStatusIsTranslatedInExactlyOneGeneratedRoot() throws Exception {
        for (String locale : List.of("en_us", "zh_cn")) {
            for (FuelLoaderStatus status : FuelLoaderStatus.values()) {
                // FuelLoaderBlock shows OUTPUT_READY as item_ready.
                String key = "status.advancedrocketrycommunity.fuel_loader."
                        + (status == FuelLoaderStatus.OUTPUT_READY ? "item_ready" : status.diagnosticKey());
                var found = new java.util.ArrayList<String>();
                for (var root : ROOTS.entrySet()) {
                    var json = JsonParser.parseString(Files.readString(Path.of("src/generated", root.getKey(),
                            "resources/assets", root.getValue(), "lang", locale + ".json"), StandardCharsets.UTF_8))
                            .getAsJsonObject();
                    if (json.has(key)) {
                        String text = json.get(key).getAsString();
                        assertFalse(text.isBlank(), key);
                        assertEquals(locale.equals("zh_cn"), text.codePoints().anyMatch(c -> c >= 0x4E00 && c <= 0x9FFF), key);
                        found.add(root.getKey());
                    }
                }
                assertEquals(1, found.size(), locale + " " + key + " roots " + found);
                if (status == FuelLoaderStatus.RECOVERY_PENDING) { assertEquals(List.of("v1.8"), found); }
            }
        }
    }
}
