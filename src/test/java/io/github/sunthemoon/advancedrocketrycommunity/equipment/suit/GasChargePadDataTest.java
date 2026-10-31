package io.github.sunthemoon.advancedrocketrycommunity.equipment.suit;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.datagen.V180GasChargePadData;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

class GasChargePadDataTest {
    @Test void originalResourcesRecipeAndBothLanguagesAreDeterministic() {
        var client = V180GasChargePadData.clientFiles(); var server = V180GasChargePadData.serverFiles();
        assertEquals(3, client.size()); assertTrue(client.containsKey("assets/advancedrocketrycommunity/models/item/gas_charge_pad.json"));
        assertEquals("advancedrocketrycommunity:gas_charge_pad", server.get("data/advancedrocketrycommunity/recipes/gas_charge_pad.json")
                .getAsJsonObject("result").get("item").getAsString());
        assertEquals(V180GasChargePadData.language(false).keySet(), V180GasChargePadData.language(true).keySet());
        var textures = V180GasChargePadData.textures(); assertEquals(2, textures.size());
        textures.forEach((path, png) -> {
            assertArrayEquals(new byte[]{(byte)137,80,78,71,13,10,26,10}, Arrays.copyOf(png, 8));
            assertArrayEquals(png, V180GasChargePadData.textures().get(path));
        });
    }

    @Test void texturesMatchGeneratedFilesAndTheOriginalSideIsUnchanged() throws IOException, NoSuchAlgorithmException {
        var textures = V180GasChargePadData.textures();
        for (var texture : textures.entrySet()) {
            assertArrayEquals(texture.getValue(), Files.readAllBytes(Path.of("src/generated/v1.8/resources", texture.getKey())),
                    texture.getKey() + " differs from the generated output");
        }
        assertEquals("77e6a63497d208f32acd5f5c7fb01bfeee3fa80b3af0473e2a665314ef90f17a",
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                        .digest(textures.get("assets/advancedrocketrycommunity/textures/block/gas_charge_pad_side.png"))));
    }
}
