package io.github.sunthemoon.advancedrocketrycommunity.material;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.material.MaterialCatalog.Entry;
import io.github.sunthemoon.advancedrocketrycommunity.material.MaterialCatalog.Kind;
import io.github.sunthemoon.advancedrocketrycommunity.material.MaterialCatalog.Material;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** ADR-063 section 1 (A0): the registered material set is exactly the contract's table. */
class MaterialCatalogTest {
    private static final Pattern ID = Pattern.compile("[a-z][a-z0-9_]{0,63}");

    /** The contract table: products this project registers, then the ore and raw item IDs. */
    private static final Map<String, List<String>> CONTRACT = new LinkedHashMap<>();

    static {
        CONTRACT.put("titanium", List.of("ingot nugget dust plate sheet rod gear coil block",
                "rutile_ore deepslate_rutile_ore raw_rutile"));
        CONTRACT.put("aluminum", List.of("ingot nugget dust plate sheet coil block",
                "aluminum_ore deepslate_aluminum_ore raw_aluminum"));
        CONTRACT.put("tin", List.of("ingot nugget dust plate block", "tin_ore deepslate_tin_ore raw_tin"));
        CONTRACT.put("steel", List.of("ingot nugget dust plate sheet rod gear fan block", ""));
        CONTRACT.put("iridium", List.of("ingot nugget dust plate rod coil block", "iridium_ore raw_iridium"));
        CONTRACT.put("dilithium", List.of("dust crystal", "dilithium_ore deepslate_dilithium_ore"));
        CONTRACT.put("silicon", List.of("ingot nugget dust plate boule", ""));
        CONTRACT.put("titanium_aluminide", List.of("ingot nugget dust plate sheet rod gear block", ""));
        CONTRACT.put("titanium_iridium", List.of("ingot nugget dust plate sheet rod gear block", ""));
        CONTRACT.put("copper", List.of("nugget dust plate sheet rod coil", ""));
        CONTRACT.put("iron", List.of("dust plate sheet rod", ""));
        CONTRACT.put("gold", List.of("dust plate coil", ""));
    }

    @Test
    void theMaterialsAndTheirProductsAreTheContractTable() {
        assertEquals(CONTRACT.keySet(), java.util.Arrays.stream(Material.values()).map(Material::id)
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new)));
        for (Material material : Material.values()) {
            Set<String> expected = new TreeSet<>();
            for (String product : CONTRACT.get(material.id()).get(0).split(" ")) {
                expected.add(material.id() + "_" + product);
            }
            Set<String> ores = new TreeSet<>(List.of(CONTRACT.get(material.id()).get(1).split(" ")));
            ores.remove("");
            expected.addAll(ores);
            Set<String> actual = MaterialCatalog.entries().stream().filter(entry -> entry.material() == material)
                    .map(Entry::id).collect(Collectors.toCollection(TreeSet::new));
            assertEquals(expected, actual, material.id());
        }
    }

    @Test
    void entriesAreUniqueValidAndBlocksAreTheCoilsStorageBlocksAndOres() {
        Set<String> seen = new HashSet<>();
        int blocks = 0;
        for (Entry entry : MaterialCatalog.entries()) {
            assertTrue(seen.add(entry.id()), "duplicate " + entry.id());
            assertTrue(ID.matcher(entry.id()).matches(), entry.id());
            boolean block = entry.kind() == Kind.STONE_ORE || entry.kind() == Kind.DEEPSLATE_ORE
                    || entry.id().endsWith("_coil") || entry.id().endsWith("_block");
            assertEquals(block, entry.isBlock(), entry.id());
            blocks += block ? 1 : 0;
        }
        assertEquals(86, seen.size());
        // Seven storage blocks, five coils, nine ores.
        assertEquals(21, blocks);
        assertEquals(Set.of("copper_coil", "gold_coil", "aluminum_coil", "titanium_coil", "iridium_coil"),
                MaterialCatalog.entries().stream().map(Entry::id).filter(id -> id.endsWith("_coil"))
                        .collect(Collectors.toSet()));
    }

    @Test
    void vanillaMaterialsAddNoIngotBlockOrOre() {
        for (Material material : List.of(Material.COPPER, Material.IRON, Material.GOLD)) {
            assertTrue(material.vanillaIngot(), material.id());
            assertTrue(material.oreName().isEmpty() && !material.hasOwnOre(), material.id());
            assertTrue(material.productId(MaterialCatalog.Product.INGOT).isEmpty(), material.id());
            assertTrue(material.productId(MaterialCatalog.Product.BLOCK).isEmpty(), material.id());
        }
    }
}
