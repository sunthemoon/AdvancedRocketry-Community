package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketCargoStatus;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class V180ItemCargoStatusLanguageTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void allOrdinalsAndSharedKeysRemainStable() {
        var names = List.of("ADMISSION_DISABLED", "ESCROW_FULL", "LEDGER_UNAVAILABLE", "TRANSFER_JOURNAL_UNPROVED",
                "ESCROW_PENDING", "UNBOUND", "STALE_BINDING", "WAITING_UNLOADED", "OUT_OF_RANGE", "NOT_OWNER",
                "ROCKET_BUSY", "UNSUPPORTED_DATA", "INVALID_DATA", "READY", "PREPARING", "TRANSFERRING",
                "DEVICE_EMPTY", "DEVICE_FULL", "NO_CARGO", "CARGO_FULL", "NO_CARGO_STORAGE", "INCOMPATIBLE_CARGO",
                "CARGO_TOO_LARGE", "UNRESOLVED_LOOT", "OTHER_TRANSFER_PENDING", "RECEIPT_BACKED", "LEGACY_ROCKET");
        assertEquals(names.size(), RocketCargoStatus.values().length);
        for (var status : RocketCargoStatus.values()) {
            assertEquals(names.indexOf(status.name()), status.ordinal());
            assertEquals("message.advancedrocketrycommunity.rocket_cargo." + status.name().toLowerCase(Locale.ROOT),
                    status.translationKey());
        }
    }

    @Test void everyItemStatusResolvesWithoutFluidWordingAndOnlyThreeKeysAreAdded() throws Exception {
        for (boolean chinese : new boolean[] {false, true}) {
            var additions = V180ItemCargoStatusLanguage.language(chinese);
            var shared = V180RocketCargoData.language(chinese);
            assertEquals(3, additions.size());
            var language = com.google.gson.JsonParser.parseString(Files.readString(Path.of(
                    "src/generated/v1.8/resources/assets/advancedrocketrycommunity_v180/lang/"
                            + (chinese ? "zh_cn" : "en_us") + ".json"))).getAsJsonObject();
            for (var status : RocketCargoStatus.values()) {
                String key = status.itemTranslationKey();
                String value = additions.getOrDefault(key, shared.get(key));
                assertNotNull(value, status.name());
                assertFalse(value.isBlank());
                for (String fluidWord : List.of("tank", "fluid", "储罐", "流体")) {
                    assertFalse(value.toLowerCase(Locale.ROOT).contains(fluidWord), status + ": " + value);
                }
                assertEquals(value, language.get(key).getAsString());
                assertEquals(shared.get(status.translationKey()), language.get(status.translationKey()).getAsString());
            }
            for (String key : additions.keySet()) { assertFalse(shared.containsKey(key)); }
        }
    }
}
