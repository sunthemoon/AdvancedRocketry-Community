package io.github.sunthemoon.advancedrocketrycommunity.endgame.laser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.satellite.resource.ResourceAlgorithms;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/** ADR-055 section 2: the strict schema-1 table codec and raw-byte versions. */
final class LaserDrillTableCodecTest {
    private static final ResourceLocation FILE = ResourceLocation.tryBuild("test", "earth");
    private static final Predicate<ResourceLocation> VANILLA = id -> id.getNamespace().equals("minecraft");

    @Test
    void aValidTableDecodesWithItsRawByteVersion() {
        byte[] raw = bytes("""
                {"schema_version": 1, "bodies": ["advancedrocketrycommunity:earth"], "default": false,
                 "entries": [{"item": "minecraft:cobblestone", "count": 5, "weight": 900},
                             {"item": "minecraft:raw_iron", "count": 2, "weight": 100}]}""");
        LaserDrillTable table = LaserDrillTableCodec.decode(FILE, raw, VANILLA);
        assertEquals(FILE, table.id());
        assertEquals(List.of(ResourceLocation.tryBuild("advancedrocketrycommunity", "earth")), table.bodies());
        assertFalse(table.isDefault());
        assertEquals(2, table.entries().size());
        assertEquals(1_000, table.totalWeight());
        assertEquals(ResourceAlgorithms.sha256Hex16(raw), table.version());
        LaserDrillTable reformatted = LaserDrillTableCodec.decode(FILE, bytes(new String(raw, StandardCharsets.UTF_8)
                .replace("\n", " ")), VANILLA);
        assertFalse(table.version().equals(reformatted.version()), "the version hashes the exact bytes");
    }

    @Test
    void aDefaultTableNeedsNoBodies() {
        LaserDrillTable table = LaserDrillTableCodec.decode(FILE, bytes(table("[]", "true", entries(1))), VANILLA);
        assertTrue(table.isDefault());
        assertTrue(table.bodies().isEmpty());
    }

    @Test
    void malformedTablesAreRejected() {
        String good = entries(1);
        List<String> rejected = List.of(
                "[]",
                "{\"schema_version\": 1, \"bodies\": [], \"default\": false}",
                "{\"schema_version\": 1, \"bodies\": [], \"default\": false, \"entries\": " + good + ", \"x\": 1}",
                "{\"schema_version\": 2, \"bodies\": [], \"default\": false, \"entries\": " + good + "}",
                "{\"schema_version\": 1.5, \"bodies\": [], \"default\": false, \"entries\": " + good + "}",
                table("[]", "\"false\"", good),
                table("[]", "0", good),
                table("[\"a:b\", \"a:b\"]", "false", good),
                table("[\"Not Valid\"]", "false", good),
                table("[1]", "false", good),
                table(bodies(65), "false", good),
                table("[]", "false", "[]"),
                table("[]", "false", entries(65)),
                table("[]", "false", "[{\"item\": \"minecraft:stone\", \"count\": 1, \"weight\": 1},"
                        + " {\"item\": \"minecraft:stone\", \"count\": 2, \"weight\": 1}]"),
                table("[]", "false", "[{\"item\": \"minecraft:stone\", \"count\": 0, \"weight\": 1}]"),
                table("[]", "false", "[{\"item\": \"minecraft:stone\", \"count\": 65, \"weight\": 1}]"),
                table("[]", "false", "[{\"item\": \"minecraft:stone\", \"count\": 1, \"weight\": 0}]"),
                table("[]", "false", "[{\"item\": \"minecraft:stone\", \"count\": 1, \"weight\": 10001}]"),
                table("[]", "false", "[{\"item\": \"minecraft:stone\", \"count\": 1.5, \"weight\": 1}]"),
                table("[]", "false", "[{\"item\": \"minecraft:stone\", \"count\": 1}]"),
                table("[]", "false", "[{\"item\": \"minecraft:stone\", \"count\": 1, \"weight\": 1, \"nbt\": {}}]"),
                table("[]", "false", "[{\"item\": \"mod:unknown\", \"count\": 1, \"weight\": 1}]"));
        for (String json : rejected) {
            assertThrows(RuntimeException.class, () -> LaserDrillTableCodec.decode(FILE, bytes(json), VANILLA), json);
        }
    }

    @Test
    void theBoundsAreInclusive() {
        LaserDrillTable table = LaserDrillTableCodec.decode(FILE, bytes(table(bodies(64), "false", entries(64))),
                id -> true);
        assertEquals(64, table.bodies().size());
        assertEquals(64, table.entries().size());
        LaserDrillTableCodec.decode(FILE, bytes(table("[]", "false",
                "[{\"item\": \"minecraft:stone\", \"count\": 64, \"weight\": 10000}]")), VANILLA);
    }

    @Test
    void aFileOverSixteenKibibytesIsRejected() {
        String padded = table("[]", "false", entries(1)) + " ".repeat(LaserDrillTableCodec.MAX_FILE_BYTES);
        assertThrows(IllegalArgumentException.class, () -> LaserDrillTableCodec.decode(FILE, bytes(padded), VANILLA));
    }

    static String table(String bodies, String isDefault, String entries) {
        return "{\"schema_version\": 1, \"bodies\": " + bodies + ", \"default\": " + isDefault + ", \"entries\": "
                + entries + "}";
    }

    static String bodies(int count) {
        StringBuilder out = new StringBuilder("[");
        for (int i = 0; i < count; i++) {
            out.append(i == 0 ? "" : ", ").append("\"test:body_").append(i).append('"');
        }
        return out.append(']').toString();
    }

    static String entries(int count) {
        StringBuilder out = new StringBuilder("[");
        for (int i = 0; i < count; i++) {
            out.append(i == 0 ? "" : ", ").append("{\"item\": \"minecraft:item_").append(i)
                    .append("\", \"count\": 1, \"weight\": 1}");
        }
        return out.append(']').toString();
    }

    static byte[] bytes(String json) {
        return json.getBytes(StandardCharsets.UTF_8);
    }
}
