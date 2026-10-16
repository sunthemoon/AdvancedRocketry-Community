package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.FluidFuelLoaderStatus;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * W82 resources: exactly the loader's and the linker's paths, original textures, a loot table that drops one loader
 * item (also on explosions), the empty-tank loader recipe and the linker recipe, every status member and message in
 * both languages, and the committed DataGen output equal to the provider.
 */
class V180FluidFuelLoaderDataTest {
    private static final String NS = "advancedrocketrycommunity";
    private static final String LOADER = V180FluidFuelLoaderData.LOADER;
    private static final String LINKER = V180FluidFuelLoaderData.LINKER;
    private static final Path GENERATED = Path.of("src/generated/v1.8/resources");

    @BeforeAll
    static void bootstrap() {
        MinecraftBootstrap.initialize();
    }

    @Test
    void theStableIdsOwnExactlyTheirPaths() {
        assertEquals(Set.of("assets/" + NS + "/blockstates/" + LOADER + ".json",
                "assets/" + NS + "/models/block/" + LOADER + ".json",
                "assets/" + NS + "/models/item/" + LOADER + ".json",
                "assets/" + NS + "/models/item/" + LINKER + ".json"), V180FluidFuelLoaderData.clientFiles().keySet());
        assertEquals(Set.of("data/" + NS + "/loot_tables/blocks/" + LOADER + ".json",
                "data/" + NS + "/recipes/" + LOADER + ".json",
                "data/" + NS + "/recipes/" + LINKER + ".json",
                "data/" + NS + "/advancements/recipes/misc/" + LOADER + ".json",
                "data/" + NS + "/advancements/recipes/misc/" + LINKER + ".json"), V180FluidFuelLoaderData.serverFiles().keySet());
        assertEquals(Set.of("assets/" + NS + "/textures/block/" + LOADER + "_side.png",
                "assets/" + NS + "/textures/block/" + LOADER + "_top.png",
                "assets/" + NS + "/textures/block/" + LOADER + "_bottom.png",
                "assets/" + NS + "/textures/item/" + LINKER + ".png"), V180FluidFuelLoaderData.textures().keySet());
    }

    @Test
    void theLootDropsOneLoaderItemEvenFromExplosions() {
        String loot = V180FluidFuelLoaderData.serverFiles().get("data/" + NS + "/loot_tables/blocks/" + LOADER + ".json")
                .toString();
        assertEquals("{\"type\":\"minecraft:block\",\"pools\":[{\"rolls\":1,\"entries\":[{\"type\":\"minecraft:item\","
                + "\"name\":\"" + NS + ":" + LOADER + "\"}]}]}", loot);
        assertFalse(loot.contains("survives_explosion") || loot.contains("explosion_decay"));
    }

    @Test
    void theLoaderTakesAnEmptyTankAndTheLinkerItsThreeParts() {
        JsonObject loader = V180FluidFuelLoaderData.serverFiles().get("data/" + NS + "/recipes/" + LOADER + ".json");
        assertEquals("minecraft:crafting_shapeless", loader.get("type").getAsString());
        JsonArray parts = loader.getAsJsonArray("ingredients");
        assertEquals(4, parts.size());
        // A plain item ingredient would also take a filled tank and delete its Fluid.
        assertEquals("{\"type\":\"" + NS + ":empty_pressurized_tank\"}", parts.get(0).toString());
        assertEquals(List.of(NS + ":liquid_io_board", NS + ":control_board", NS + ":machine_casing"),
                List.of(parts.get(1), parts.get(2), parts.get(3)).stream()
                        .map(part -> part.getAsJsonObject().get("item").getAsString()).toList());
        assertFalse(loader.toString().contains("\"" + NS + ":pressurized_tank\""));
        assertEquals(NS + ":" + LOADER, loader.getAsJsonObject("result").get("item").getAsString());
        assertFalse(loader.getAsJsonObject("result").has("count"));
        JsonObject linker = V180FluidFuelLoaderData.serverFiles().get("data/" + NS + "/recipes/" + LINKER + ".json");
        assertEquals(List.of(NS + ":tracking_circuit", "minecraft:iron_ingot", "minecraft:redstone"),
                linker.getAsJsonArray("ingredients").asList().stream()
                        .map(part -> part.getAsJsonObject().get("item").getAsString()).toList());
        assertEquals(NS + ":" + LINKER, linker.getAsJsonObject("result").get("item").getAsString());
    }

    @Test
    void texturesAreFourDistinctOriginalGrids() {
        Set<String> faces = new HashSet<>();
        for (String face : List.of("side", "top", "bottom", LINKER)) {
            String[] grid = V180FluidFuelLoaderData.grid(face);
            assertEquals(16, grid.length);
            assertTrue(Arrays.stream(grid).allMatch(row -> row.length() == 16 && row.chars().allMatch(c ->
                    c == '.' || c >= '0' && c <= '9')), face);
            assertEquals(!face.equals(LINKER), Arrays.stream(grid).noneMatch(row -> row.contains(".")),
                    face + ": block faces are opaque, the icon has a transparent margin");
            assertTrue(faces.add(String.join("", grid)), face);
        }
        V180FluidFuelLoaderData.textures().values().forEach(png -> assertTrue(png.length > 8 && png[1] == 'P'));
    }

    @Test
    void everyStatusAndMessageExistsInBothLanguagesAndTheOutputIsCommitted() throws Exception {
        Map<String, String> english = V180FluidFuelLoaderData.language(false);
        Map<String, String> chinese = V180FluidFuelLoaderData.language(true);
        assertEquals(english.keySet(), chinese.keySet());
        for (FluidFuelLoaderStatus status : FluidFuelLoaderStatus.values()) {
            assertFalse(english.getOrDefault(status.translationKey(), "").isBlank(), status.name());
            assertFalse(chinese.getOrDefault(status.translationKey(), "").isBlank(), status.name());
        }
        // ESCROW_PENDING must never claim that nothing moved; PREPARING says nothing has moved yet.
        assertFalse(english.get(FluidFuelLoaderStatus.ESCROW_PENDING.translationKey()).contains("no fuel"));
        assertFalse(chinese.get(FluidFuelLoaderStatus.ESCROW_PENDING.translationKey()).contains("未移动"));
        assertEquals("Escrow capacity full; no fuel moved.", english.get(FluidFuelLoaderStatus.ESCROW_FULL.translationKey()));
        assertEquals("绑定已失效；请重新绑定装载器。", chinese.get(FluidFuelLoaderStatus.STALE_BINDING.translationKey()));
        assertEquals("正在准备托管；尚未移动燃料。", chinese.get(FluidFuelLoaderStatus.PREPARING.translationKey()));
        for (String key : List.of("block." + NS + "." + LOADER, "item." + NS + "." + LINKER)) {
            assertTrue(english.containsKey(key), key);
        }
        for (String locale : List.of("en_us", "zh_cn")) {
            JsonObject lang = JsonParser.parseString(Files.readString(GENERATED.resolve("assets/" + NS
                    + "_v180/lang/" + locale + ".json"), StandardCharsets.UTF_8)).getAsJsonObject();
            Map<String, String> expected = locale.equals("en_us") ? english : chinese;
            expected.forEach((key, value) -> assertEquals(value, lang.has(key) ? lang.get(key).getAsString() : null,
                    locale + " " + key));
        }
        for (Map.Entry<String, JsonObject> file : V180FluidFuelLoaderData.serverFiles().entrySet()) {
            assertEquals(file.getValue(), JsonParser.parseString(Files.readString(GENERATED.resolve(file.getKey()),
                    StandardCharsets.UTF_8)), file.getKey());
        }
        for (Map.Entry<String, JsonObject> file : V180FluidFuelLoaderData.clientFiles().entrySet()) {
            assertEquals(file.getValue(), JsonParser.parseString(Files.readString(GENERATED.resolve(file.getKey()),
                    StandardCharsets.UTF_8)), file.getKey());
        }
        for (Map.Entry<String, byte[]> file : V180FluidFuelLoaderData.textures().entrySet()) {
            assertTrue(Arrays.equals(file.getValue(), Files.readAllBytes(GENERATED.resolve(file.getKey()))), file.getKey());
        }
    }
}
