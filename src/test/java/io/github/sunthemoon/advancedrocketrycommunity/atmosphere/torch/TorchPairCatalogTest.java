package io.github.sunthemoon.advancedrocketrycommunity.atmosphere.torch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/** A0 checks of the schema-1 torch_pairs codec and its whole-set bounds, written from ADR-066 section 3.2. */
class TorchPairCatalogTest {
    private static final ResourceLocation FILE = id("example:ember");

    @Test
    void builtInCatalogHoldsOnlyThePermanentVanillaPair() {
        TorchPairCatalog catalog = TorchPairCatalog.BUILT_IN;
        assertEquals(1, catalog.size());
        TorchPair vanilla = catalog.get(id("minecraft:torch")).orElseThrow();
        assertEquals(id("minecraft:torch"), vanilla.standingBlock());
        assertEquals(id("minecraft:wall_torch"), vanilla.wallBlock());
        assertEquals(id("minecraft:torch"), vanilla.item());
        assertEquals(Optional.of(vanilla), catalog.forBlock(id("minecraft:wall_torch")));
        assertEquals(Optional.empty(), catalog.forBlock(id("minecraft:soul_torch")));
    }

    @Test
    void decodesExactlyOneSchemaOnePair() {
        TorchPair pair = TorchPairCatalog.decode(FILE, json(file("example:ember_torch", "example:ember_wall_torch",
                "example:ember_torch")));
        assertEquals(new TorchPair(FILE, id("example:ember_torch"), id("example:ember_wall_torch"),
                id("example:ember_torch")), pair);
        assertEquals(id("example:ember_wall_torch"), pair.block(true));
        assertEquals(id("example:ember_torch"), pair.block(false));
    }

    @Test
    void refusesUnknownMissingFieldsSchemasAndNonCanonicalIds() {
        String good = file("example:a", "example:b", "example:a");
        assertRefused(good.replace("}", ",\"extra\":1}"));
        assertRefused("{\"schema_version\":1,\"standing_block\":\"example:a\",\"wall_block\":\"example:b\"}");
        assertRefused(good.replace("\"schema_version\":1", "\"schema_version\":2"));
        assertRefused(good.replace("\"schema_version\":1", "\"schema_version\":\"1\""));
        assertRefused(file("a", "example:b", "example:a"));
        assertRefused(file("example:A", "example:b", "example:a"));
        assertRefused(file("example:a", "example:a", "example:a"));
        assertRefused(file("example:a", "example:b", "example:" + "c".repeat(121)));
        assertRefused("[]");
        assertRefused("{");
    }

    @Test
    void idsAndFilesAreBoundedInUtf8Bytes() {
        String path128 = "p".repeat(128 - "example:".length());
        TorchPair pair = TorchPairCatalog.decode(FILE, json(file("example:" + path128, "example:b", "example:a")));
        assertEquals(128, pair.standingBlock().toString().getBytes(StandardCharsets.UTF_8).length);
        assertRefused(file("example:" + path128 + "p", "example:b", "example:a"));
        assertThrows(IllegalArgumentException.class, () -> TorchPairCatalog.decode(id("example:" + path128 + "p"),
                json(file("example:a", "example:b", "example:a"))));
        byte[] padded = json(file("example:a", "example:b", "example:a") + " ".repeat(4_096));
        assertTrue(padded.length > TorchPairCatalog.MAX_FILE_BYTES);
        assertThrows(IllegalArgumentException.class, () -> TorchPairCatalog.decode(FILE, padded));
        byte[] exact = json(file("example:a", "example:b", "example:a"));
        byte[] filled = new byte[TorchPairCatalog.MAX_FILE_BYTES];
        java.util.Arrays.fill(filled, (byte) ' ');
        System.arraycopy(exact, 0, filled, 0, exact.length);
        assertEquals(id("example:a"), TorchPairCatalog.decode(FILE, filled).standingBlock());
    }

    @Test
    void sixtyThreeDataPairsFillTheSixtyFourPairCatalog() {
        TorchPairCatalog.Result result = TorchPairCatalog.build(pairs(63), List.of(), pair -> true);
        assertTrue(result.errors().isEmpty(), result.errors().toString());
        assertEquals(TorchPairCatalog.MAX_PAIRS, result.catalog().orElseThrow().size());
        assertTrue(result.catalog().orElseThrow().forBlock(id("example:wall_62")).isPresent());

        TorchPairCatalog.Result over = TorchPairCatalog.build(pairs(64), List.of(), pair -> true);
        assertTrue(over.catalog().isEmpty());
        assertEquals(1, over.errors().size());
    }

    @Test
    void anyDefectRejectsTheWholeSet() {
        Map<ResourceLocation, byte[]> files = pairs(3);
        files.put(id("example:copy"), json(file("example:standing_1", "example:other", "example:standing_1")));
        assertRejected(files, pair -> true, "already mapped");

        files = pairs(2);
        files.put(id("example:vanilla_copy"), json(file("example:x", "minecraft:wall_torch", "example:x")));
        assertRejected(files, pair -> true, "already mapped");

        files = pairs(2);
        files.put(id("minecraft:torch"), json(file("example:x", "example:y", "example:x")));
        assertRejected(files, pair -> true, "redefined");

        assertRejected(pairs(2), pair -> !pair.id().equals(id("example:pair_1")), "not a registered");

        files = pairs(2);
        files.put(id("example:broken"), "{".getBytes(StandardCharsets.UTF_8));
        assertRejected(files, pair -> true, "example:broken");

        TorchPairCatalog.Result read = TorchPairCatalog.build(pairs(1), List.of("unreadable file"), pair -> true);
        assertTrue(read.catalog().isEmpty());
        assertEquals(List.of("unreadable file"), read.errors());
    }

    @Test
    void errorListIsCappedAtThirtyTwo() {
        Map<ResourceLocation, byte[]> files = new HashMap<>();
        for (int index = 0; index < 40; index++) {
            files.put(id("example:bad_" + index), "{".getBytes(StandardCharsets.UTF_8));
        }
        TorchPairCatalog.Result result = TorchPairCatalog.build(files, List.of(), pair -> true);
        assertTrue(result.catalog().isEmpty());
        assertEquals(32, result.errors().size());
    }

    @Test
    void emptyDataKeepsTheBuiltInPair() {
        TorchPairCatalog.Result result = TorchPairCatalog.build(Map.of(), List.of(), pair -> false);
        assertEquals(List.of(TorchPairCatalog.VANILLA), result.catalog().orElseThrow().pairs());
    }

    @Test
    void canonicalIdsNeedAnExplicitNamespace() {
        assertEquals(id("minecraft:torch"), TorchPairCatalog.canonical("minecraft:torch"));
        assertEquals(null, TorchPairCatalog.canonical("torch"));
        assertEquals(null, TorchPairCatalog.canonical("minecraft:Torch"));
        assertEquals(null, TorchPairCatalog.canonical(null));
        assertFalse(TorchPairCatalog.canonical("a:b") == null);
    }

    private static void assertRefused(String text) {
        assertThrows(RuntimeException.class, () -> TorchPairCatalog.decode(FILE, json(text)), text);
    }

    private static void assertRejected(Map<ResourceLocation, byte[]> files,
                                       java.util.function.Predicate<TorchPair> registered, String fragment) {
        TorchPairCatalog.Result result = TorchPairCatalog.build(files, List.of(), registered);
        assertTrue(result.catalog().isEmpty());
        assertTrue(result.errors().stream().anyMatch(error -> error.contains(fragment)), result.errors().toString());
    }

    private static Map<ResourceLocation, byte[]> pairs(int count) {
        Map<ResourceLocation, byte[]> files = new HashMap<>();
        for (int index = 0; index < count; index++) {
            files.put(id("example:pair_" + index), json(file("example:standing_" + index, "example:wall_" + index,
                    "example:standing_" + index)));
        }
        return files;
    }

    private static String file(String standing, String wall, String item) {
        return "{\"schema_version\":1,\"standing_block\":\"" + standing + "\",\"wall_block\":\"" + wall
                + "\",\"item\":\"" + item + "\"}";
    }

    private static byte[] json(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private static ResourceLocation id(String value) {
        return ResourceLocation.tryParse(value);
    }
}
