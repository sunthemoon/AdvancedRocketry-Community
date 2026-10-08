package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.zip.CRC32;
import java.util.zip.InflaterInputStream;
import net.minecraft.core.Direction;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.PackOutput;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** A0 checks written from the contract's paths and rules, not from the provider's private constants. */
class V180ThermiteDataTest {
    private static final String NS = "advancedrocketrycommunity:";
    private static final String ASSETS = "assets/advancedrocketrycommunity/";
    private static final String DATA = "data/advancedrocketrycommunity/";
    private static final String DUST_PNG = ASSETS + "textures/item/thermite.png";
    private static final String TORCH_PNG = ASSETS + "textures/block/thermite_torch.png";
    private static final Set<String> CLIENT = Set.of(ASSETS + "blockstates/thermite_torch.json",
            ASSETS + "blockstates/thermite_wall_torch.json", ASSETS + "models/block/thermite_torch.json",
            ASSETS + "models/block/thermite_wall_torch.json", ASSETS + "models/item/thermite.json",
            ASSETS + "models/item/thermite_torch.json");
    private static final Set<String> SERVER = Set.of(DATA + "recipes/thermite.json",
            DATA + "recipes/thermite_torch.json", DATA + "advancements/recipes/misc/thermite.json",
            DATA + "advancements/recipes/building_blocks/thermite_torch.json",
            DATA + "loot_tables/blocks/thermite_torch.json", DATA + "loot_tables/blocks/thermite_wall_torch.json");

    @Test void partitionIsExactlyFourteenRelativeLowercasePathsWithoutTagOrLanguageFiles() {
        assertEquals(CLIENT, V180ThermiteData.clientFiles().keySet());
        assertEquals(SERVER, V180ThermiteData.serverFiles().keySet());
        Set<String> all = new HashSet<>(CLIENT);
        all.addAll(SERVER);
        all.addAll(List.of(DUST_PNG, TORCH_PNG));
        assertEquals(14, all.size());
        for (String path : all) {
            assertTrue(path.startsWith(ASSETS) || path.startsWith(DATA), path);
            assertFalse(path.contains("..") || path.contains("/tags/") || path.contains("/lang/"), path);
            assertFalse(Path.of(path).isAbsolute(), path);
            assertEquals(path.toLowerCase(Locale.ROOT), path);
        }
    }

    @Test void shapelessRecipesUseOnlyTagIngredientsAndKeepLiteralResults() {
        var server = V180ThermiteData.serverFiles();
        assertRecipe(server.get(DATA + "recipes/thermite.json"), "misc",
                Set.of("forge:dusts/aluminum", "forge:dusts/iron"), NS + "thermite", 1);
        assertRecipe(server.get(DATA + "recipes/thermite_torch.json"), "building",
                Set.of("forge:rods/wooden", "forge:dusts/thermite"), NS + "thermite_torch", 4);
    }

    @Test void unlocksAreOneOrGroupOverInputTagsWithOnlyTheirOwnRecipeReward() {
        var server = V180ThermiteData.serverFiles();
        assertUnlock(server.get(DATA + "advancements/recipes/misc/thermite.json"), NS + "thermite",
                Set.of("forge:dusts/aluminum", "forge:dusts/iron"));
        assertUnlock(server.get(DATA + "advancements/recipes/building_blocks/thermite_torch.json"),
                NS + "thermite_torch", Set.of("forge:dusts/thermite"));
    }

    @Test void modelsUseOrdinaryParentsCutoutAndResolveOnlyToPartitionResources() {
        var client = V180ThermiteData.clientFiles();
        assertEquals(element("{\"variants\":{\"\":{\"model\":\"" + NS + "block/thermite_torch\"}}}"),
                client.get(ASSETS + "blockstates/thermite_torch.json"));
        for (var model : Map.of("thermite_torch", "minecraft:block/template_torch",
                "thermite_wall_torch", "minecraft:block/template_torch_wall").entrySet()) {
            JsonObject json = client.get(ASSETS + "models/block/" + model.getKey() + ".json");
            assertEquals(Set.of("parent", "textures", "render_type"), json.keySet());
            assertEquals(model.getValue(), json.get("parent").getAsString());
            assertEquals("minecraft:cutout", json.get("render_type").getAsString());
            assertEquals(Set.of("torch"), json.getAsJsonObject("textures").keySet());
            assertEquals(TORCH_PNG, texture(json.getAsJsonObject("textures").get("torch").getAsString()));
        }
        for (var item : Map.of("thermite", DUST_PNG, "thermite_torch", TORCH_PNG).entrySet()) {
            JsonObject json = client.get(ASSETS + "models/item/" + item.getKey() + ".json");
            assertEquals(Set.of("parent", "textures"), json.keySet());
            assertEquals("minecraft:item/generated", json.get("parent").getAsString());
            assertEquals(Set.of("layer0"), json.getAsJsonObject("textures").keySet());
            assertEquals(item.getValue(), texture(json.getAsJsonObject("textures").get("layer0").getAsString()));
        }
        for (String state : List.of("thermite_torch", "thermite_wall_torch")) {
            for (var variant : client.get(ASSETS + "blockstates/" + state + ".json")
                    .getAsJsonObject("variants").entrySet()) {
                String model = variant.getValue().getAsJsonObject().get("model").getAsString();
                assertTrue(model.startsWith(NS + "block/"), model);
                assertTrue(client.containsKey(ASSETS + "models/" + model.substring(NS.length()) + ".json"), model);
            }
        }
    }

    @Test void wallVariantsCoverHorizontalFacingsWithYawDerivedFromNativeDirections() {
        JsonObject variants = V180ThermiteData.clientFiles().get(ASSETS + "blockstates/thermite_wall_torch.json")
                .getAsJsonObject("variants");
        Map<String, Integer> yaw = new HashMap<>();
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            // The wall template leans east; blockstate y turns it clockwise seen from above, as toYRot does.
            int expected = Math.floorMod((int) (facing.toYRot() - Direction.EAST.toYRot()), 360);
            JsonObject variant = variants.getAsJsonObject("facing=" + facing.getSerializedName());
            assertNotNull(variant, facing.getSerializedName());
            assertTrue(Set.of("model", "y").containsAll(variant.keySet()), variant.toString());
            assertEquals(NS + "block/thermite_wall_torch", variant.get("model").getAsString());
            int actual = variant.has("y") ? variant.get("y").getAsInt() : 0;
            assertEquals(expected, actual, facing.getSerializedName());
            yaw.put(facing.getSerializedName(), actual);
        }
        assertEquals(4, variants.size());
        // Cross-check against the separately recorded baseline interface (ROTATION-01).
        assertEquals(Map.of("east", 0, "south", 90, "west", 180, "north", 270), yaw);
    }

    @Test void eachTorchBlockHasItsOwnTableDroppingOnePairedItemThatSurvivesExplosion() {
        var server = V180ThermiteData.serverFiles();
        for (String block : List.of("thermite_torch", "thermite_wall_torch")) {
            JsonObject table = server.get(DATA + "loot_tables/blocks/" + block + ".json");
            assertNotNull(table, block);
            assertEquals(Set.of("type", "pools"), table.keySet());
            assertEquals("minecraft:block", table.get("type").getAsString());
            assertEquals(1, table.getAsJsonArray("pools").size());
            JsonObject pool = table.getAsJsonArray("pools").get(0).getAsJsonObject();
            assertEquals(Set.of("rolls", "bonus_rolls", "entries", "conditions"), pool.keySet());
            assertEquals(1.0, pool.get("rolls").getAsDouble());
            assertEquals(0.0, pool.get("bonus_rolls").getAsDouble());
            assertEquals(element("[{\"type\":\"minecraft:item\",\"name\":\"" + NS + "thermite_torch\"}]"),
                    pool.get("entries"));
            assertEquals(element("[{\"condition\":\"minecraft:survives_explosion\"}]"), pool.get("conditions"));
        }
    }

    @Test void everyAccessorReturnsFreshJsonGridsAndBytesWithinBounds() {
        for (boolean client : List.of(true, false)) {
            var original = client ? V180ThermiteData.clientFiles() : V180ThermiteData.serverFiles();
            Map<String, JsonObject> expected = new HashMap<>();
            original.forEach((path, value) -> {
                assertTrue(value.toString().getBytes(StandardCharsets.UTF_8).length <= 16384, path);
                expected.put(path, value.deepCopy());
                value.entrySet().removeIf(entry -> true);
                value.addProperty("changed", true);
            });
            assertEquals(expected, client ? V180ThermiteData.clientFiles() : V180ThermiteData.serverFiles());
        }
        String[] dust = V180ThermiteData.dustGrid();
        String[] torch = V180ThermiteData.torchGrid();
        String[] dustCopy = dust.clone();
        String[] torchCopy = torch.clone();
        Arrays.fill(dust, "x");
        Arrays.fill(torch, "x");
        assertArrayEquals(dustCopy, V180ThermiteData.dustGrid());
        assertArrayEquals(torchCopy, V180ThermiteData.torchGrid());
        for (String[] grid : List.of(dustCopy, torchCopy)) {
            assertEquals(16, grid.length);
            for (String row : grid) { assertTrue(row.matches("[.0-9]{16}"), row); }
        }
        byte[] dustPng = V180ThermiteData.dustTexture();
        byte[] torchPng = V180ThermiteData.torchTexture();
        assertFalse(Arrays.equals(dustPng, torchPng));
        byte[] dustBytes = dustPng.clone();
        byte[] torchBytes = torchPng.clone();
        Arrays.fill(dustPng, (byte) 0);
        Arrays.fill(torchPng, (byte) 0);
        assertArrayEquals(dustBytes, V180ThermiteData.dustTexture());
        assertArrayEquals(torchBytes, V180ThermiteData.torchTexture());
    }

    @Test void languageMapsAreExactImmutableAndShareOneKeySet() {
        Map<String, String> english = V180ThermiteLanguage.translations(false);
        Map<String, String> chinese = V180ThermiteLanguage.translations(true);
        assertEquals(Map.of("item.advancedrocketrycommunity.thermite", "Thermite",
                "block.advancedrocketrycommunity.thermite_torch", "Thermite Torch",
                "block.advancedrocketrycommunity.thermite_wall_torch", "Thermite Wall Torch"), english);
        assertEquals(Map.of("item.advancedrocketrycommunity.thermite", "铝热剂",
                "block.advancedrocketrycommunity.thermite_torch", "铝热火把",
                "block.advancedrocketrycommunity.thermite_wall_torch", "壁挂铝热火把"), chinese);
        assertThrows(UnsupportedOperationException.class, () -> english.put("changed", "changed"));
        assertThrows(UnsupportedOperationException.class, chinese::clear);
    }

    @Test void texturesAreDeterministicBinaryAlphaOneTintWithHandComputedControls() throws Exception {
        Map<Integer, Integer> greenBlueByRed = new HashMap<>();
        for (byte[] png : List.of(V180ThermiteData.dustTexture(), V180ThermiteData.torchTexture())) {
            for (int pixel : decode(png)) {
                int alpha = pixel >>> 24;
                assertTrue(pixel == 0 || alpha == 255, Integer.toHexString(pixel));
                if (alpha == 0) { continue; }
                int r = pixel >> 16 & 0xFF;
                int g = pixel >> 8 & 0xFF;
                int b = pixel & 0xFF;
                assertTrue(r > 0 && r >= g && g >= b, Integer.toHexString(pixel));
                // One tint over one grey scale: equal red always implies equal green and blue.
                assertEquals(greenBlueByRed.computeIfAbsent(r, key -> pixel & 0xFFFF), pixel & 0xFFFF);
            }
        }
        assertTrue(greenBlueByRed.size() <= 10);
        assertArrayEquals(V180ThermiteData.dustTexture(), V180ThermiteData.dustTexture());
        assertArrayEquals(V180ThermiteData.torchTexture(), V180ThermiteData.torchTexture());
        // Fixed controls computed by hand from tint F0B080 and the encoder's greys 238, 72 and 44.
        int[] dust = decode(V180ThermiteData.dustTexture());
        int[] torch = decode(V180ThermiteData.torchTexture());
        assertEquals(0xFFE0A477, torch[6 * 16 + 7]);
        assertEquals(0xFF291E16, torch[15 * 16 + 8]);
        assertEquals(0xFFE0A477, dust[8 * 16 + 7]);
        assertEquals(0xFF433124, dust[14 * 16 + 14]);
        assertEquals(0, dust[0]);
        assertEquals(0, torch[0]);
    }

    @Test void dustIsAMarginedLowHeapAndTorchIsANarrowShaftWithCappedBrightTip() throws Exception {
        int[] dust = decode(V180ThermiteData.dustTexture());
        int opaque = 0;
        int widest = 0;
        for (int y = 0; y < 16; y++) {
            int row = 0;
            for (int x = 0; x < 16; x++) {
                if (dust[y * 16 + x] == 0) { continue; }
                row++;
                assertTrue(y >= 6 && y <= 14 && x >= 1 && x <= 14, "dust outside heap margin at " + x + "," + y);
            }
            if (y >= 8) {
                assertTrue(row >= widest, "heap narrows downward at row " + y);
                widest = row;
            }
            opaque += row;
        }
        assertTrue(widest >= 12 && opaque >= 40 && opaque <= 120, "heap " + widest + "/" + opaque);
        int[] torch = decode(V180ThermiteData.torchTexture());
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                boolean shaft = (x == 7 || x == 8) && y >= 6;
                assertEquals(shaft, torch[y * 16 + x] != 0, "torch pixel " + x + "," + y);
            }
        }
        int tipMin = 255;
        int capMin = 255;
        int capMax = 0;
        int shaftMax = 0;
        for (int x = 7; x <= 8; x++) {
            tipMin = Math.min(tipMin, Math.min(red(torch, x, 6), red(torch, x, 7)));
            capMin = Math.min(capMin, red(torch, x, 8));
            capMax = Math.max(capMax, red(torch, x, 8));
            for (int y = 9; y < 16; y++) { shaftMax = Math.max(shaftMax, red(torch, x, y)); }
        }
        assertTrue(tipMin > capMax && capMin > shaftMax, tipMin + ">" + capMax + ", " + capMin + ">" + shaftMax);
    }

    @Test void providerFlagsWriteOnlyTheirPartitionAndRepeatExactBytes(@TempDir Path directory) throws Exception {
        Map<String, byte[]> png = Map.of(DUST_PNG, V180ThermiteData.dustTexture(),
                TORCH_PNG, V180ThermiteData.torchTexture());
        Map<String, JsonObject> json = new HashMap<>(V180ThermiteData.clientFiles());
        json.putAll(V180ThermiteData.serverFiles());
        for (boolean client : List.of(false, true)) {
            for (boolean server : List.of(false, true)) {
                Path output = directory.resolve(client + "-" + server);
                var provider = new V180ThermiteData(new PackOutput(output), client, server);
                assertFalse(provider.getName().isBlank());
                assertEquals(provider.getName(), new V180ThermiteData(new PackOutput(output), !client, !server).getName());
                provider.run(CachedOutput.NO_CACHE).get(10, TimeUnit.SECONDS);
                Set<String> expected = new HashSet<>();
                if (client) { expected.addAll(CLIENT); expected.addAll(png.keySet()); }
                if (server) { expected.addAll(SERVER); }
                Map<String, byte[]> first = files(output);
                assertEquals(expected, first.keySet());
                first.forEach((path, raw) -> {
                    if (path.endsWith(".png")) {
                        assertArrayEquals(png.get(path), raw, path);
                    } else {
                        assertTrue(raw.length <= 16384, path);
                        assertEquals(json.get(path), JsonParser.parseString(new String(raw, StandardCharsets.UTF_8)));
                    }
                });
                provider.run(CachedOutput.NO_CACHE).get(10, TimeUnit.SECONDS);
                Map<String, byte[]> second = files(output);
                assertEquals(first.keySet(), second.keySet());
                first.forEach((path, raw) -> assertArrayEquals(raw, second.get(path), path));
            }
        }
    }

    private static void assertRecipe(JsonObject recipe, String category, Set<String> tags, String result, int count) {
        assertEquals(Set.of("type", "category", "ingredients", "result"), recipe.keySet());
        assertEquals("minecraft:crafting_shapeless", recipe.get("type").getAsString());
        assertEquals(category, recipe.get("category").getAsString());
        Set<String> seen = new HashSet<>();
        for (JsonElement element : recipe.getAsJsonArray("ingredients")) {
            // Ingredient-only oracle: tag selectors only, never an exact item such as the thermite itself.
            assertEquals(Set.of("tag"), element.getAsJsonObject().keySet(), element.toString());
            assertTrue(seen.add(element.getAsJsonObject().get("tag").getAsString()), element.toString());
        }
        assertEquals(tags, seen);
        JsonObject output = recipe.getAsJsonObject("result");
        assertTrue(Set.of("item", "count").containsAll(output.keySet()), output.toString());
        assertEquals(result, output.get("item").getAsString());
        assertEquals(count, output.has("count") ? output.get("count").getAsInt() : 1);
    }

    private static void assertUnlock(JsonObject advancement, String recipe, Set<String> tags) {
        assertEquals(Set.of("parent", "criteria", "requirements", "rewards"), advancement.keySet());
        assertEquals("minecraft:recipes/root", advancement.get("parent").getAsString());
        JsonObject criteria = advancement.getAsJsonObject("criteria");
        Set<String> unlocked = new HashSet<>();
        Set<String> held = new HashSet<>();
        for (var criterion : criteria.entrySet()) {
            JsonObject body = criterion.getValue().getAsJsonObject();
            JsonObject conditions = body.getAsJsonObject("conditions");
            if (body.get("trigger").getAsString().equals("minecraft:recipe_unlocked")) {
                unlocked.add(conditions.get("recipe").getAsString());
                continue;
            }
            assertEquals("minecraft:inventory_changed", body.get("trigger").getAsString());
            assertEquals(1, conditions.getAsJsonArray("items").size());
            JsonObject predicate = conditions.getAsJsonArray("items").get(0).getAsJsonObject();
            assertEquals(Set.of("tag"), predicate.keySet());
            held.add(predicate.get("tag").getAsString());
        }
        assertEquals(Set.of(recipe), unlocked);
        assertEquals(tags, held);
        assertEquals(tags.size() + 1, criteria.size());
        assertEquals(1, advancement.getAsJsonArray("requirements").size());
        Set<String> group = new HashSet<>();
        advancement.getAsJsonArray("requirements").get(0).getAsJsonArray().forEach(name -> group.add(name.getAsString()));
        assertEquals(criteria.keySet(), group);
        assertEquals(element("{\"recipes\":[\"" + recipe + "\"]}"), advancement.get("rewards"));
    }

    /** Independent bounded PNG reader: exact IHDR/IDAT/IEND, valid CRCs, filter-0 rows; returns 256 ARGB pixels. */
    private static int[] decode(byte[] png) throws Exception {
        assertTrue(png.length <= 4096);
        assertArrayEquals(new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'}, Arrays.copyOf(png, 8));
        ByteBuffer bytes = ByteBuffer.wrap(png);
        bytes.position(8);
        List<String> chunks = new ArrayList<>();
        byte[] compressed = null;
        while (bytes.hasRemaining()) {
            assertTrue(bytes.remaining() >= 12);
            int length = bytes.getInt();
            assertTrue(length >= 0 && length <= 4096 && length <= bytes.remaining() - 8);
            byte[] kind = new byte[4];
            bytes.get(kind);
            byte[] body = new byte[length];
            bytes.get(body);
            CRC32 crc = new CRC32();
            crc.update(kind);
            crc.update(body);
            assertEquals(crc.getValue(), Integer.toUnsignedLong(bytes.getInt()));
            String type = new String(kind, StandardCharsets.US_ASCII);
            chunks.add(type);
            if (type.equals("IHDR")) {
                assertArrayEquals(ByteBuffer.allocate(13).putInt(16).putInt(16).put((byte) 8).put((byte) 6)
                        .put((byte) 0).put((byte) 0).put((byte) 0).array(), body);
            } else if (type.equals("IDAT")) {
                compressed = body;
            } else {
                assertEquals(0, body.length);
            }
        }
        assertEquals(List.of("IHDR", "IDAT", "IEND"), chunks);
        byte[] raw;
        try (var inflated = new InflaterInputStream(new ByteArrayInputStream(compressed))) {
            raw = inflated.readNBytes(1041);
            assertEquals(-1, inflated.read());
        }
        assertEquals(1040, raw.length);
        int[] argb = new int[256];
        for (int y = 0; y < 16; y++) {
            assertEquals(0, raw[y * 65]);
            for (int x = 0; x < 16; x++) {
                int at = y * 65 + 1 + x * 4;
                argb[y * 16 + x] = Byte.toUnsignedInt(raw[at + 3]) << 24 | Byte.toUnsignedInt(raw[at]) << 16
                        | Byte.toUnsignedInt(raw[at + 1]) << 8 | Byte.toUnsignedInt(raw[at + 2]);
            }
        }
        return argb;
    }

    private static Map<String, byte[]> files(Path root) throws Exception {
        Map<String, byte[]> files = new HashMap<>();
        if (!Files.exists(root)) { return files; }
        try (var paths = Files.walk(root)) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                files.put(root.relativize(path).toString().replace('\\', '/'), Files.readAllBytes(path));
            }
        }
        return files;
    }

    private static int red(int[] pixels, int x, int y) { return pixels[y * 16 + x] >> 16 & 0xFF; }

    private static String texture(String id) {
        assertTrue(id.startsWith(NS), id);
        return ASSETS + "textures/" + id.substring(NS.length()) + ".png";
    }

    private static JsonElement element(String text) { return JsonParser.parseString(text); }
}
