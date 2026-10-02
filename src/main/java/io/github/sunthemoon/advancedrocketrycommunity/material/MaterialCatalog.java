package io.github.sunthemoon.advancedrocketrycommunity.material;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * The classic material set of ADR-063 section 1, as plain data. The product lists are the legacy ones (LibVulpes
 * material flags and Advanced Rocketry's two alloys); vanilla supplies the copper, iron and gold ingots and blocks.
 * IDs follow vanilla word order ({@code titanium_ingot}, {@code raw_tin}, {@code deepslate_tin_ore}).
 */
public final class MaterialCatalog {
    private MaterialCatalog() {
    }

    /** A product kind; coils and storage blocks are blocks, every other product is an item. */
    public enum Product {
        INGOT("ingot", "ingots", false),
        NUGGET("nugget", "nuggets", false),
        DUST("dust", "dusts", false),
        PLATE("plate", "plates", false),
        SHEET("sheet", "sheets", false),
        ROD("rod", "rods", false),
        GEAR("gear", "gears", false),
        FAN("fan", "fans", false),
        CRYSTAL("crystal", "gems", false),
        BOULE("boule", "boules", false),
        COIL("coil", "coils", true),
        BLOCK("block", "storage_blocks", true);

        private final String suffix;
        private final String tagFolder;
        private final boolean block;

        Product(String suffix, String tagFolder, boolean block) {
            this.suffix = suffix;
            this.tagFolder = tagFolder;
            this.block = block;
        }

        public String suffix() {
            return suffix;
        }

        /** The tag folder: {@code forge:<folder>/<material>}, or this mod's namespace for {@link #isProjectTag()}. */
        public String tagFolder() {
            return tagFolder;
        }

        public boolean isBlock() {
            return block;
        }

        /** Forge's common tags do not name sheets, coils, fans or boules (ADR-061 section 2). */
        public boolean isProjectTag() {
            return this == SHEET || this == COIL || this == FAN || this == BOULE;
        }
    }

    /** How a material occurs in the world. */
    public enum OreKind {
        /** No ore of this project's own. */
        NONE,
        /** Stone and deepslate ores and a raw item. */
        STONE_AND_DEEPSLATE,
        /** Stone and deepslate ores that drop themselves (dilithium smelts to dust). */
        STONE_AND_DEEPSLATE_SELF,
        /** A stone ore only and a raw item: placed on the Moon and Mars (C15b), never in the Overworld. */
        STONE_ONLY,
        /** Vanilla ores. */
        VANILLA
    }

    public enum Material {
        TITANIUM("titanium", "rutile", 0xCCC8FA, OreKind.STONE_AND_DEEPSLATE, false,
                Product.INGOT, Product.NUGGET, Product.DUST, Product.PLATE, Product.SHEET, Product.ROD, Product.GEAR,
                Product.COIL, Product.BLOCK),
        ALUMINUM("aluminum", "aluminum", 0xB3E4DC, OreKind.STONE_AND_DEEPSLATE, false,
                Product.INGOT, Product.NUGGET, Product.DUST, Product.PLATE, Product.SHEET, Product.COIL, Product.BLOCK),
        TIN("tin", "tin", 0xCDD5D8, OreKind.STONE_AND_DEEPSLATE, false,
                Product.INGOT, Product.NUGGET, Product.DUST, Product.PLATE, Product.BLOCK),
        STEEL("steel", null, 0x55555D, OreKind.NONE, false,
                Product.INGOT, Product.NUGGET, Product.DUST, Product.PLATE, Product.SHEET, Product.ROD, Product.GEAR,
                Product.FAN, Product.BLOCK),
        IRIDIUM("iridium", "iridium", 0xDEDCCE, OreKind.STONE_ONLY, false,
                Product.INGOT, Product.NUGGET, Product.DUST, Product.PLATE, Product.ROD, Product.COIL, Product.BLOCK),
        DILITHIUM("dilithium", "dilithium", 0xDDCECB, OreKind.STONE_AND_DEEPSLATE_SELF, false,
                Product.DUST, Product.CRYSTAL),
        SILICON("silicon", null, 0x2C2C2B, OreKind.NONE, false,
                Product.INGOT, Product.NUGGET, Product.DUST, Product.PLATE, Product.BOULE),
        TITANIUM_ALUMINIDE("titanium_aluminide", null, 0xAEC2DE, OreKind.NONE, false,
                Product.INGOT, Product.NUGGET, Product.DUST, Product.PLATE, Product.SHEET, Product.ROD, Product.GEAR,
                Product.BLOCK),
        TITANIUM_IRIDIUM("titanium_iridium", null, 0xD7DFE4, OreKind.NONE, false,
                Product.INGOT, Product.NUGGET, Product.DUST, Product.PLATE, Product.SHEET, Product.ROD, Product.GEAR,
                Product.BLOCK),
        COPPER("copper", null, 0xD55E28, OreKind.VANILLA, true,
                Product.NUGGET, Product.DUST, Product.PLATE, Product.SHEET, Product.ROD, Product.COIL),
        IRON("iron", null, 0xAFAFAF, OreKind.VANILLA, true,
                Product.DUST, Product.PLATE, Product.SHEET, Product.ROD),
        GOLD("gold", null, 0xFFFF5D, OreKind.VANILLA, true,
                Product.DUST, Product.PLATE, Product.COIL);

        private final String id;
        private final String oreName;
        private final int colour;
        private final OreKind oreKind;
        private final boolean vanillaIngot;
        private final Set<Product> products;

        Material(String id, String oreName, int colour, OreKind oreKind, boolean vanillaIngot, Product... products) {
            this.id = id;
            this.oreName = oreName;
            this.colour = colour;
            this.oreKind = oreKind;
            this.vanillaIngot = vanillaIngot;
            EnumSet<Product> set = EnumSet.noneOf(Product.class);
            Collections.addAll(set, products);
            this.products = Collections.unmodifiableSet(set);
        }

        public String id() {
            return id;
        }

        /** The legacy material colour (LibVulpes table), used to tint the greyscale templates. */
        public int colour() {
            return colour;
        }

        public OreKind oreKind() {
            return oreKind;
        }

        /** Copper, iron and gold: the ingot, the storage block and the ores are vanilla. */
        public boolean vanillaIngot() {
            return vanillaIngot;
        }

        public Set<Product> products() {
            return products;
        }

        public boolean has(Product product) {
            return products.contains(product);
        }

        /** The registry path of one of this project's products, or empty for a vanilla or absent product. */
        public Optional<String> productId(Product product) {
            if (!has(product)) {
                return Optional.empty();
            }
            return Optional.of(id + "_" + product.suffix());
        }

        /** The ore's registry name stem ({@code rutile}, {@code tin}); empty without an ore of this project. */
        public Optional<String> oreName() {
            return Optional.ofNullable(oreName);
        }

        public boolean hasOwnOre() {
            return oreKind == OreKind.STONE_AND_DEEPSLATE || oreKind == OreKind.STONE_AND_DEEPSLATE_SELF
                    || oreKind == OreKind.STONE_ONLY;
        }

        public Optional<String> stoneOreId() {
            return hasOwnOre() ? Optional.of(oreName + "_ore") : Optional.empty();
        }

        public Optional<String> deepslateOreId() {
            return oreKind == OreKind.STONE_AND_DEEPSLATE || oreKind == OreKind.STONE_AND_DEEPSLATE_SELF
                    ? Optional.of("deepslate_" + oreName + "_ore") : Optional.empty();
        }

        public Optional<String> rawId() {
            return oreKind == OreKind.STONE_AND_DEEPSLATE || oreKind == OreKind.STONE_ONLY
                    ? Optional.of("raw_" + oreName) : Optional.empty();
        }
    }

    /** One registered entry: its registry path, material and kind. */
    public record Entry(String id, Material material, Kind kind, Optional<Product> product) {
        public Entry {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(material, "material");
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(product, "product");
        }

        public boolean isBlock() {
            return kind == Kind.STONE_ORE || kind == Kind.DEEPSLATE_ORE || product.map(Product::isBlock).orElse(false);
        }
    }

    public enum Kind {
        PRODUCT,
        STONE_ORE,
        DEEPSLATE_ORE,
        RAW
    }

    /** Every entry this project registers for the material set, in a stable order. */
    public static List<Entry> entries() {
        List<Entry> entries = new ArrayList<>();
        for (Material material : Material.values()) {
            for (Product product : Product.values()) {
                material.productId(product).ifPresent(id -> entries.add(
                        new Entry(id, material, Kind.PRODUCT, Optional.of(product))));
            }
            material.stoneOreId().ifPresent(id -> entries.add(new Entry(id, material, Kind.STONE_ORE, Optional.empty())));
            material.deepslateOreId().ifPresent(id -> entries.add(
                    new Entry(id, material, Kind.DEEPSLATE_ORE, Optional.empty())));
            material.rawId().ifPresent(id -> entries.add(new Entry(id, material, Kind.RAW, Optional.empty())));
        }
        return List.copyOf(entries);
    }
}
